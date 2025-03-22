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
    suspend fun sendReport(report: ByteArray, newReportDescriptorFunc : (suspend ()-> HidDescriptor))
}

class AoaBadCommBackend(usbFileDesriptor: ParcelFileDescriptor): BadCommBackend{

    private val outputChannel = Channel<ByteArray>()

    private val aoaInputStream = FileInputStream(usbFileDesriptor.fileDescriptor)
    private val aoaOutputStream = FileOutputStream(usbFileDesriptor.fileDescriptor)

    private var reportDescriptorInvalid: Boolean = true
    override suspend fun start(){
        try {
            outputChannel.receiveAsFlow().collect() {
                withContext(Dispatchers.IO) {
                    aoaOutputStream.write(it)
                }
            }
        }catch (_: CancellationException){
            aoaInputStream.close()
            aoaOutputStream.close()
            currentCoroutineContext().ensureActive()
        }
    }

    override suspend fun invalidReportDescriptor(){
        reportDescriptorInvalid = true
    }

    suspend fun updateReportDescriptor(reportDescriptor: ByteArray){
        val data = mutableListOf<Byte>()
        data.add(0x80u.toByte())
        data.addAll(reportDescriptor.toTypedArray())
        outputChannel.send(data.toByteArray())
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun sendReport(report: ByteArray, newReportDescriptorFunc : (suspend ()-> HidDescriptor)){
        if(reportDescriptorInvalid){
            reportDescriptorInvalid = false
            updateReportDescriptor(newReportDescriptorFunc().descriptorData.toByteArray())
        }
        outputChannel.send(report)
    }
}