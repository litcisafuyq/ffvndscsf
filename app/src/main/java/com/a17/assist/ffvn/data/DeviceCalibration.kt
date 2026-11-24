package com.a17.assist.ffvn.data

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

data class DisplayScaleMetrics(
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val density: Float,
    val deviceScale: Float
)

object DeviceCalibration {

    /**
     * Exact formula:
     * deviceScale = (screenHeight / 1200) * (densityDpi / 384) * 0.513
     * Preserves floating-point precision so fractional px are never lost.
     */
    fun computeMetrics(context: Context, calibration: Calibration = Calibration()): DisplayScaleMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(dm)

        val rawW: Int
        val rawH: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            rawW = bounds.width()
            rawH = bounds.height()
        } else {
            rawW = dm.widthPixels
            rawH = dm.heightPixels
        }

        val densityDpi = context.resources.displayMetrics.densityDpi.takeIf { it > 0 } ?: 384
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 2.4f

        val heightForScale = maxOf(rawH, 1)
        val refH = calibration.refHeight.takeIf { it > 0 } ?: 1200
        val refDpi = calibration.refDpi.takeIf { it > 0 } ?: 384
        val baseFactor = calibration.baseFactor.takeIf { it > 0f } ?: 0.513f

        val deviceScale = (heightForScale.toFloat() / refH.toFloat()) *
            (densityDpi.toFloat() / refDpi.toFloat()) *
            baseFactor

        return DisplayScaleMetrics(
            screenWidth = rawW,
            screenHeight = rawH,
            densityDpi = densityDpi,
            density = density,
            deviceScale = deviceScale.coerceAtLeast(0.1f)
        )
    }

    fun getFireButtonCenter(metrics: DisplayScaleMetrics, calibration: Calibration): Pair<Int, Int> {
        val x = (metrics.screenWidth * calibration.fireButtonXRatio).toInt().coerceIn(0, metrics.screenWidth)
        val y = (metrics.screenHeight * calibration.fireButtonYRatio).toInt().coerceIn(0, metrics.screenHeight)
        return x to y
    }
}
