package com.xeoncjj.booxasdigitizer

import android.view.MotionEvent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class TouchData(
    val pointerId: Int,
    val actionMasked: Int,
    val x: Float,
    val y: Float,
    val touchSize: Float,
    val pressure: Float
)
// Regulate touch event update.
// Why we need this:
// TouchEvent in activity is dispatched only when event occur
// when you put your finger stand still and suddently move. A new tipSwitch=1 report will be sent to windows who reconize it as a new pointer down input (instead of moving)
// And trigger a click event in windows
// set if(currentTouchState.isNotEmpty()) -> if(false) and you will know
class TouchScheduler(private val maxContactCount: Int) {
    companion object{
        private const val LOG_TAG = "TouchUpdater"
    }
    private val _inChannel = Channel<Array<TouchData>>()
    private val _touchDataChannel = Channel<Array<TouchData>>()
    private val _outChannel = Channel<Array<TouchData>>()
    private val _updateInterval = 10.toDuration(DurationUnit.MILLISECONDS)
    private val _touchState = mutableMapOf<Int, TouchData>()
    private val _touchStateMutex = Mutex()

    val sendChannel:SendChannel<Array<TouchData>> = _inChannel
    val scheduledTouchDataReceiveChannel: ReceiveChannel<Array<TouchData>> = _outChannel

    @OptIn(FlowPreview::class)
    suspend fun start() = coroutineScope {
        val receiveDeferred = async() {
            _inChannel.receiveAsFlow().collect() {
                var ignore = true
                val allTouchData = mutableListOf<TouchData>()
                _touchStateMutex.withLock {
                    // update state
                    for(touchData in it){
                        if(touchData.actionMasked == MotionEvent.ACTION_UP || touchData.actionMasked == MotionEvent.ACTION_POINTER_UP || touchData.actionMasked == MotionEvent.ACTION_CANCEL){
                            _touchState.remove(touchData.pointerId)
                            if(touchData.actionMasked != MotionEvent.ACTION_CANCEL){
                                allTouchData.add(touchData)
                            }
                            ignore = false
                        }else{
                            if(_touchState.containsKey(touchData.pointerId) || _touchState.count() < maxContactCount) {
                                _touchState[touchData.pointerId] = touchData
                                ignore = false
                            }
                        }
                    }
                    if(!ignore){
                        allTouchData.addAll(_touchState.values)
                    }
                }
                if(!ignore && allTouchData.isNotEmpty()){
                    _touchDataChannel.send(allTouchData.toTypedArray())
                }
            }
        }
        val scheduleDeferred = async() {
            while(true){
                val currentTouchState = _touchStateMutex.withLock{_touchState.values.toTypedArray()}
                if(currentTouchState.isNotEmpty()){
                    val getNextIdleTouchData = async() {
                        delay(_updateInterval)
                        currentTouchState
                    }

                    // wait for any of _touchDataChannel.receiveAsFlow() or getNextIdleTouchData::await.asFlow() to finish
                    // https://stackoverflow.com/a/67724851/11879605
                    val getNextScheduleTouchDataFlow = listOf(_touchDataChannel.receiveAsFlow(), getNextIdleTouchData::await.asFlow()).merge()
                    val data = getNextScheduleTouchDataFlow.first()

                    if(data.isNotEmpty()){
                        _outChannel.send(data)
                    }
                }else{
                    val data = _touchDataChannel.receiveAsFlow().first()
                    _outChannel.send(data)
                }
            }
        }
        awaitAll(receiveDeferred, scheduleDeferred)
    }

}