package com.a17.assist.ffvn.engine

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

/**
 * ThermalGuard:
 * - 10 minutes continuous active play -> reduce pull by 20% (0.80f)
 * - 20 minutes continuous active play -> reduce pull by 30% (0.70f)
 * Also respects Android PowerManager thermal status on API 29+.
 */
class ThermalGuard(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var sessionStartMs: Long = 0L
    private var lastActiveMs: Long = 0L

    fun onActiveTick(nowMs: Long = SystemClock.uptimeMillis()) {
        if (sessionStartMs == 0L || (nowMs - lastActiveMs) > 60_000L) {
            sessionStartMs = nowMs
        }
        lastActiveMs = nowMs
    }

    fun getThermalFactor(enabled: Boolean, nowMs: Long = SystemClock.uptimeMillis()): Float {
        if (!enabled) return 1.0f

        var factor = 1.0f
        if (sessionStartMs > 0L) {
            val elapsed = nowMs - sessionStartMs
            factor = when {
                elapsed >= 20L * 60_000L -> 0.70f
                elapsed >= 10L * 60_000L -> 0.80f
                else -> 1.0f
            }
        }

        val pm = powerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            val status = runCatching { pm.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
            val hwFactor = when {
                status >= PowerManager.THERMAL_STATUS_SEVERE -> 0.70f
                status >= PowerManager.THERMAL_STATUS_MODERATE -> 0.80f
                else -> 1.0f
            }
            factor = minOf(factor, hwFactor)
        }
        return factor
    }

    fun getActiveMinutes(nowMs: Long = SystemClock.uptimeMillis()): Long {
        if (sessionStartMs == 0L) return 0L
        return ((nowMs - sessionStartMs).coerceAtLeast(0L)) / 60_000L
    }

    fun reset() {
        sessionStartMs = 0L
        lastActiveMs = 0L
    }
}
