package com.xeoncjj.booxasdigitizer

import android.content.Context
import android.graphics.Rect
import com.onyx.android.sdk.api.device.epd.EpdController

class TouchUtils {
    companion object{
        fun disableFingerTouch(context: Context){
            val width = context.resources.displayMetrics.widthPixels
            val height = context.resources.displayMetrics.heightPixels
            val rect = Rect(0, 0, width, height)
            EpdController.setAppCTPDisableRegion(context, arrayOf(rect))
        }

        fun enableFingerTouch(context: Context){
            EpdController.appResetCTPDisableRegion(context)
        }
    }
}