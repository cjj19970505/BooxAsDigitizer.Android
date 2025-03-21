package com.xeoncjj.booxasdigitizer

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

interface BadCommBackend {
    suspend fun start()
    suspend fun invalidReportDescriptor()
    suspend fun updateReportDescriptor(reportDescriptor: ByteArray)
    suspend fun sendReport(report: ByteArray, newReportDescriptorFunc : (suspend ()-> ByteArray))
}

class AoaBadCommBackend(usbFileDesriptor: ParcelFileDescriptor): BadCommBackend{

    private val outputChannel = Channel<ByteArray>()

    private val aoaInputStream = FileInputStream(usbFileDesriptor.fileDescriptor)
    private val aoaOutputStream = FileOutputStream(usbFileDesriptor.fileDescriptor)

    private var reportDescriptorInvalid: Boolean = true
    override suspend fun start(){
        try {
            outputChannel.receiveAsFlow().collect() {
                // Use ensureActive to throw cancellation exception since aoaOutputStream.write is not a suspend function, it is not aware of any cancellations.
                // https://developer.android.com/kotlin/coroutines/coroutines-best-practices#coroutine-cancellable
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.IO) {
                    aoaOutputStream.write(it)
                }
                currentCoroutineContext().ensureActive()
            }
        }catch (e: CancellationException){
            aoaInputStream.close()
            aoaOutputStream.close()
            currentCoroutineContext().ensureActive()
        }
    }

    override suspend fun invalidReportDescriptor(){
        reportDescriptorInvalid = true
    }

    override suspend fun updateReportDescriptor(reportDescriptor: ByteArray){
        val data = mutableListOf<Byte>()
        data.add(0x80u.toByte())
        data.addAll(reportDescriptor.toTypedArray())
        outputChannel.send(data.toByteArray())
    }

    override suspend fun sendReport(report: ByteArray, newReportDescriptorFunc : (suspend ()-> ByteArray)){
        if(reportDescriptorInvalid){
            reportDescriptorInvalid = false
            updateReportDescriptor(newReportDescriptorFunc())
        }
        outputChannel.send(report)
    }
}