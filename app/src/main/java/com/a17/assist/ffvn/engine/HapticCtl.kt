package com.a17.assist.ffvn.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.a17.assist.ffvn.data.FingerFeedback

/**
 * HapticCtl:
 * - Head lock feedback: [20, 45, 20] ms
 * - Miss feedback: [32] ms
 * - Throttle: 450ms minimum between pulses
 */
class HapticCtl(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastVibrateMs: Long = 0L

    fun playHeadLock(feedback: FingerFeedback) {
        val pattern = longArrayOf(0L) + feedback.headPatternMs.toLongArray()
        vibrate(pattern, feedback)
    }

    fun playMiss(feedback: FingerFeedback) {
        val pattern = longArrayOf(0L) + feedback.missPatternMs.toLongArray()
        vibrate(pattern, feedback)
    }

    private fun vibrate(waveformWithInitialDelayZero: LongArray, feedback: FingerFeedback) {
        if (!feedback.haptic) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val now = SystemClock.uptimeMillis()
        if (now - lastVibrateMs < feedback.hapticThrottleMs) return
        lastVibrateMs = now

        runCatching {
            val effect = VibrationEffect.createWaveform(waveformWithInitialDelayZero, -1)
            vib.vibrate(effect)
        }
    }
}
