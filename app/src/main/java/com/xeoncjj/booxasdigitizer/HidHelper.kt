package com.xeoncjj.booxasdigitizer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.or

class HidHelper {
    data class PenReport(
        val reportId: UByte,
        val tipSwitch: Boolean,
        val barrelSwitch: Boolean,
        val invert: Boolean,
        val eraserSwitch: Boolean,
        val inRange: Boolean,
        val x: UShort,
        val y: UShort,
        val tipPressure: UShort,
        val xTilt: UByte,
        val yTilt: UByte
    )

    data class TouchpadReport(
        val reportId: UByte,
        val confidence: Boolean,
        val tipSwitch: Boolean,
        val contactId: UByte,
        val x: UShort,
        val y: UShort,
        val scanTime: UShort,
        val contactCount: UByte,
        val button1: Boolean,
    )

    companion object{
        const val PEN_REPORT_SIZE = 10
        const val TOUCHPAD_REPORT_SIZE = 10
        const val MAX_CONTACT_COUNT = 5
        const val MAX_TOUCHPAD_X = 4095
        const val MAX_TOUCHPAD_Y = 4095

        private fun Boolean.asMaskOffset(offset:Int): Byte{
            return if(this){
                (1 shl offset).toByte()
            }else{
                0
            }
        }

        fun PenReport.getReportBuffer(): ByteArray{
            return ByteBuffer.allocate(PEN_REPORT_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(reportId.toByte())
                // construct reportbyte1
                val b1: Byte = tipSwitch.asMaskOffset(0) or
                        barrelSwitch.asMaskOffset(1) or
                        invert.asMaskOffset(2) or
                        eraserSwitch.asMaskOffset(3) or
                        inRange.asMaskOffset(5)
                put(b1)
                putShort(x.toShort())
                putShort(y.toShort())
                putShort(tipPressure.toShort())
                put(xTilt.toByte())
                put(yTilt.toByte())
            }.array()
        }
        fun TouchpadReport.getReportBuffer(): ByteArray{
            return ByteBuffer.allocate(TOUCHPAD_REPORT_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(reportId.toByte())
                val b1: Byte = confidence.asMaskOffset(0) or
                        tipSwitch.asMaskOffset(1) or
                        (contactId.toUInt() shl 2).toByte()
                put(b1)
                putShort(x.toShort())
                putShort(y.toShort())
                putShort(scanTime.toShort())
                put(contactCount.toByte())
                put(button1.asMaskOffset(0))
            }.array()
        }
    }
}