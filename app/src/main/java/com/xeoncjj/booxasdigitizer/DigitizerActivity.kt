package com.xeoncjj.booxasdigitizer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.xeoncjj.booxasdigitizer.HidHelper.Companion.getReportBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt


class DigitizerActivity : AppCompatActivity() {
    companion object {
        private const val STROKE_WIDTH = 3.0f
        private const val LOG_TAG = "DigitizerActivity"
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        private const val REPORTID_TOUCHPAD: UByte = 0x01u
        private const val REPORTID_PEN: UByte = 0x07u
        private const val BOOX_MAX_PRESURE = 4095
        private const val HOST_MAX_PRESURE = 255
    }
    lateinit var touchHelper: TouchHelper
    lateinit var surfaceView: SurfaceView
    lateinit var usbManager: UsbManager
    var accessory: UsbAccessory? = null

    private var _drawing: Boolean = false
    val usbOutputChannel = Channel<ByteArray>()

    val digitizerRect: Rect = Rect(0,0,0,0)
    val touchScheduler = TouchScheduler(2)

    private val usbReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent != null && intent.action == ACTION_USB_PERMISSION){
                synchronized(this){
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(LOG_TAG, "permission granted")
                    } else {
                        Log.d(LOG_TAG, "permission denied for accessory")
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "UnspecifiedRegisterReceiverFlag",
        "UnspecifiedImmutableFlag"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // https://stackoverflow.com/a/66076995/11879605
        window.decorView.windowInsetsController!!.hide(
            android.view.WindowInsets.Type.statusBars()
        )

        setContentView(R.layout.activity_digitizer)
        surfaceView = findViewById(R.id.surface_view)

        touchHelper = TouchHelper.create(surfaceView, object: RawInputCallback() {
            override fun onBeginRawDrawing(p0: Boolean, touchPoint: TouchPoint?) {
                _drawing = true
                Log.d(LOG_TAG, "Begin Draw")
                TouchUtils.disableFingerTouch(applicationContext)

                if (touchPoint != null) {
                    if (touchPoint.x >= 0 && touchPoint.x <= digitizerRect.width() && touchPoint.y >= 0 && touchPoint.y <= digitizerRect.height()) {
                        val x =
                            (touchPoint.x.toDouble() / digitizerRect.width() * 21240).toUInt().toUShort()
                        val y =
                            (touchPoint.y.toDouble() / digitizerRect.height() * 15980).toUInt().toUShort()
                        val pressure = ((touchPoint.pressure / BOOX_MAX_PRESURE) * HOST_MAX_PRESURE).toUInt().toUShort()
                        val reportByteArray = HidHelper.PenReport(
                            REPORTID_PEN,
                            true,
                            false,
                            false,
                            false,
                            true,
                            x,
                            y,
                            pressure,
                            0u,
                            0u
                        ).getReportBuffer()
                        lifecycleScope.launch {
                            usbOutputChannel.send(reportByteArray)
                        }
                    }
                }
            }

            override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
                _drawing = false
                Log.d(LOG_TAG, "End Draw")
                TouchUtils.enableFingerTouch(applicationContext)
            }

            override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
                if (p0 != null) {
                    if (p0.x >= 0 && p0.x <= digitizerRect.width() && p0.y >= 0 && p0.y <= digitizerRect.height()) {
                        val x =
                            (p0.x.toDouble() / digitizerRect.width() * 21240).toUInt().toUShort()
                        val y =
                            (p0.y.toDouble() / digitizerRect.height() * 15980).toUInt().toUShort()
                        val pressure = ((p0.pressure / 4095) * 255).toUInt().toUShort()
                        val reportByteArray = HidHelper.PenReport(
                            REPORTID_PEN,
                            true,
                            false,
                            false,
                            false,
                            true,
                            x,
                            y,
                            pressure,
                            0u,
                            0u
                        ).getReportBuffer()
                        lifecycleScope.launch {
                            usbOutputChannel.send(reportByteArray)
                        }
                    }
                }
            }

            override fun onRawDrawingTouchPointListReceived(p0: TouchPointList?) {
            }

            override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint?) {
            }

            override fun onEndRawErasing(p0: Boolean, p1: TouchPoint?) {
            }

            override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {
                if (p0 != null) {
                    if (p0.x >= 0 && p0.x <= digitizerRect.width() && p0.y >= 0 && p0.y <= digitizerRect.height()) {
                        val x =
                            (p0.x.toDouble() / digitizerRect.width() * 21240).toUInt().toUShort()
                        val y =
                            (p0.y.toDouble() / digitizerRect.height() * 15980).toUInt().toUShort()
                        val pressure = ((p0.pressure / 4095) * 255).toUInt().toUShort()
                        val reportByteArray = HidHelper.PenReport(
                            REPORTID_PEN,
                            false,
                            false,
                            true,
                            true,
                            true,
                            x,
                            y,
                            pressure,
                            0u,
                            0u
                        ).getReportBuffer()
                        lifecycleScope.launch {
                            usbOutputChannel.send(reportByteArray)
                        }
                    }
                }
            }

            override fun onRawErasingTouchPointListReceived(p0: TouchPointList?) {
            }

        })

        surfaceView.addOnLayoutChangeListener(object: View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                digitizerRect.left = left
                digitizerRect.top = top
                digitizerRect.right = right
                digitizerRect.bottom = bottom
                surfaceView.clean()
                val limit = Rect()
                surfaceView.getLocalVisibleRect(limit)
                touchHelper.apply {
                    setLimitRect(limit, listOf(Rect()))
                    setStrokeWidth(STROKE_WIDTH)
                    openRawDrawing()
                    // strokestyle must be set after openrawdrawing was called
                    setStrokeStyle(TouchHelper.STROKE_STYLE_BRUSH)
                }
                touchHelper.setRawInputReaderEnable(true)
            }
        })

        surfaceView.setOnTouchListener(object:View.OnTouchListener{
            var startTouchDateTime:OffsetDateTime = OffsetDateTime.MIN
            var preTouchDateTime: OffsetDateTime = OffsetDateTime.MIN
            var noTouch = true
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if(event == null){
                    return true
                }

                if(event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_FINGER){
                    lifecycleScope.launch {

                        // actionIndex is only used for ACTION_POINTER_DOWN or ACTION_POINTER_UP
                        // https://developer.android.com/reference/android/view/MotionEvent#getActionIndex()
                        val updatedTouchData = mutableListOf<TouchData>()
                        if(event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP){
                            updatedTouchData.add(TouchData(
                                event.getPointerId(event.actionIndex),
                                event.actionMasked,
                                event.getX(event.actionIndex),
                                event.getY(event.actionIndex),
                                event.getSize(event.actionIndex),
                                event.getPressure(event.actionIndex)
                            ))
                        }else{
                            for(pointerIndex in 0..<event.pointerCount){
                                updatedTouchData.add(TouchData(
                                    event.getPointerId(pointerIndex),
                                    event.actionMasked,
                                    event.getX(pointerIndex),
                                    event.getY(pointerIndex),
                                    event.getSize(pointerIndex),
                                    event.getPressure(pointerIndex)
                                ))
                            }

                        }
                        touchScheduler.sendChannel.send(updatedTouchData.toTypedArray())
                    }
                }
                return true

                // Old way
                // Bypass
//                if(event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_FINGER){
//                    val now = OffsetDateTime.now()
//                    var scanTime: UShort = 0u
//                    val duration = Duration.between(startTouchDateTime, now)
//                    val durationBetweenFrame = Duration.between(preTouchDateTime, now)
//                    if(noTouch && durationBetweenFrame.seconds > 3){
//                        // reset scan time
//                        startTouchDateTime = now
//                    }else{
//                        scanTime = (duration.toMillis() * 10).toUShort()
//                    }
//
//                    preTouchDateTime = now
//                    noTouch = event.pointerCount == 1 && event.actionMasked == MotionEvent.ACTION_UP
//                    // From experiment
//                    // first finger triggers ACTION_DOWN, the rest fingers trigger ACTION_POINTER_DOWN
//                    var contactCountAssigned = false
//                    for(pointerIndex in 0..<event.pointerCount){
//                        val pointerId = event.getPointerId(pointerIndex)
//                        if(pointerId > HidHelper.MAX_CONTACT_COUNT - 1)
//                        {
//                            continue
//                        }
//                        val x = ((event.getX(pointerIndex) / digitizerRect.width()) * HidHelper.MAX_TOUCHPAD_X).roundToInt().toUInt().toUShort()
//                        val y = ((event.getY(pointerIndex) / digitizerRect.height()) * HidHelper.MAX_TOUCHPAD_Y).roundToInt().toUInt().toUShort()
//                        var contactCount = 0
//                        if(!contactCountAssigned)
//                        {
//                            contactCount = kotlin.math.min(event.pointerCount, HidHelper.MAX_CONTACT_COUNT)
//                            contactCountAssigned = true
//                        }
//                        val tipSwitch = !(event.actionIndex == pointerIndex && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP))
//                        val touchSize = event.getSize(pointerIndex)
//                        val confidence = touchSize < 0.16
//
//                        val btn1 = event.pressure > 0.48
//                        if(btn1){
//                            Log.d(LOG_TAG, "Pressure: ${event.pressure}")
//                        }
//
//                        val report = HidHelper.TouchpadReport(
//                            REPORTID_TOUCHPAD,
//                            confidence,
//                            tipSwitch,
//                            pointerId.toUByte(),
//                            x,
//                            y,
//                            scanTime,
//                            contactCount.toUByte(),
//                            btn1
//                        )
//                        Log.d(LOG_TAG, "Contact${pointerId} x: ${x} y: ${y} tipSwitch: ${tipSwitch} contactCount: $contactCount scanTime: $scanTime confidence: $confidence")
//                        lifecycleScope.launch {
//                            usbOutputChannel.send(report.getReportBuffer())
//                        }
//                    }
//                }
//                return true
            }
        })

        surfaceView.setOnGenericMotionListener(object: View.OnGenericMotionListener{
            override fun onGenericMotion(v: View?, event: MotionEvent?): Boolean {
                if(event == null){
                    return true
                }
                if(event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_ERASER){
                    when(event.action){
                        MotionEvent.ACTION_HOVER_ENTER->{
                            touchHelper.isRawDrawingRenderEnabled = true
                        }
                        MotionEvent.ACTION_HOVER_EXIT->{
                            lifecycleScope.launch {
                                delay(50)
                                if(!_drawing){
                                    touchHelper.isRawDrawingRenderEnabled = false
                                }
                            }
                        }
                        MotionEvent.ACTION_HOVER_MOVE -> {
                            // SurfaceView's motion event might not be sync with TouchHelper's drawing events.
                            // We need make sure no hover_move is sent to client after TouchHeler.onBeginRawDrawing is received.
                            // and make sure no hover_move before TouchHeler.onEndRawDrawing
                            // I remembered I encountered a bug on this. But I don't remember what exactly.
                            if(!_drawing){
                                if (event.x >= 0 && event.x <= digitizerRect.width() && event.y >= 0 && event.y <= digitizerRect.height()) {
                                    val x =
                                        (event.x.toDouble() / digitizerRect.width() * 21240).toUInt()
                                            .toUShort()
                                    val y =
                                        (event.y.toDouble() / digitizerRect.height() * 15980).toUInt()
                                            .toUShort()
                                    val invert = event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                                    val reportByteArray = HidHelper.PenReport(
                                        REPORTID_PEN,
                                        false,
                                        false,
                                        invert,
                                        false,
                                        true,
                                        x,
                                        y,
                                        0u,
                                        0u,
                                        0u
                                    ).getReportBuffer()
                                    lifecycleScope.launch {
                                        usbOutputChannel.send(reportByteArray)
                                    }
                                }
                            }
                        }
                    }
                }
                return true
            }
        })
        surfaceView.holder.addCallback(object:SurfaceHolder.Callback{
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceView.clean()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                holder.removeCallback(this)
            }
        })
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        @Suppress("DEPRECATION")
        accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
        if(accessory == null){
            if(usbManager.accessoryList != null){
                accessory = usbManager.accessoryList.filter {
                    it.model == "BooxAsDigitizer"
                }.let {
                    if(it.isEmpty()){
                        null
                    }else{
                        it[0]
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED){
                val touchUpdaterDeferred = async { touchScheduler.Start() }
                val touchDeferred = async {
                    var startTouchDateTime:OffsetDateTime = OffsetDateTime.MIN
                    var preTouchDateTime: OffsetDateTime = OffsetDateTime.MIN
                    var noTouch = true
                    touchScheduler.scheduledTouchDataReceiveChannel.receiveAsFlow().collect(){
                        var scanTime: UShort = 0u
                        val now = OffsetDateTime.now()
                        val duration = Duration.between(startTouchDateTime, now)
                        val durationBetweenFrame = Duration.between(preTouchDateTime, now)
                        if(noTouch && durationBetweenFrame.seconds > 3){
                            startTouchDateTime = now
                        }else{
                            scanTime = (duration.toMillis() * 10).toUShort()
                        }

                        preTouchDateTime = now
                        noTouch = it.count() == 1 && (it[0].actionMasked == MotionEvent.ACTION_UP || it[0].actionMasked == MotionEvent.ACTION_UP)

                        var contactCountAssigned = false
                        for(touchData in it){
                            val x = (touchData.x / digitizerRect.width() * HidHelper.MAX_TOUCHPAD_X).roundToInt().toUInt().toUShort()
                            val y = (touchData.y / digitizerRect.height() * HidHelper.MAX_TOUCHPAD_Y).roundToInt().toUInt().toUShort()

                            // val tipSwitch = touchData.actionMasked != MotionEvent.ACTION_UP
                            val tipSwitch = touchData.actionMasked != MotionEvent.ACTION_UP && touchData.actionMasked != MotionEvent.ACTION_POINTER_UP
                            val confidence = touchData.touchSize < 0.16
                            var btn1 = touchData.pressure > 0.48
                            btn1 = false
                            // From MSDN
                            // For multi-touch. Send first packet with contactCount, and the rest packet with a contactCount of 0

                            var contactCount:UByte = 0u
                            if(!contactCountAssigned)
                            {
                                contactCount = it.count().toUByte()
                                contactCountAssigned = true
                            }

                            val report = HidHelper.TouchpadReport(
                                REPORTID_TOUCHPAD,
                                confidence,
                                tipSwitch,
                                touchData.pointerId.toUByte(),
                                x,
                                y,
                                scanTime,
                                contactCount,
                                btn1
                            )

                            async { usbOutputChannel.send(report.getReportBuffer()) }


                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED){
                val permissionIntent = PendingIntent.getBroadcast(this@DigitizerActivity, 0, Intent(ACTION_USB_PERMISSION), 0)
                val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
                    addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                }
                registerReceiver(usbReceiver, filter)
                if(accessory != null){
                    usbManager.requestPermission(accessory, permissionIntent)
                    usbManager.openAccessory(accessory).use { parcelFileDescriptor ->
                        val fileDescriptor = parcelFileDescriptor.fileDescriptor
                        val inputStream = FileInputStream(fileDescriptor)
                        val outputStream = FileOutputStream(fileDescriptor)
                        val outputDeferred = async {
                            usbOutputChannel.receiveAsFlow().collect() {
                                withContext(Dispatchers.IO){
                                    outputStream.write(it)
                                }
                            }
                        }
                        try {
                            awaitAll(outputDeferred)
                        }finally {
                            inputStream.close()
                            outputStream.close()
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.DESTROYED){
                unregisterReceiver(usbReceiver)
            }
        }
    }

    fun getRelativeRect(parentView: View, childView: View): Rect{
        val parent = IntArray(2)
        val child = IntArray(2)
        parentView.getLocationOnScreen(parent)
        childView.getLocationOnScreen(child)
        val rect = Rect()
        childView.getLocalVisibleRect(rect)
        rect.offset(child[0] - parent[0], child[1] - parent[1])
        return rect
    }

    fun SurfaceView.clean(): Boolean{
        if(holder == null){
            return false
        }
        val canvas = holder.lockCanvas()
        if(canvas == null){
            return false
        }
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }


}