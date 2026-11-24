package com.a17.assist.ffvn.engine

import android.app.ActivityManager
import android.content.Context

data class RamGuardState(
    val availableMb: Long,
    val isConstrained: Boolean,
    val pullFactor: Float,
    val stepMultiplier: Float,
    val maxBurstOverride: Int?
)

/**
 * RamGuard: When available RAM < 400MB, reduces per-step delta and burst limit.
 */
class RamGuard(context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val memInfo = ActivityManager.MemoryInfo()

    fun evaluate(enabled: Boolean, lowThresholdMb: Long = 400L): RamGuardState {
        val availMb = readAvailableMb()
        if (!enabled) {
            return RamGuardState(
                availableMb = availMb,
                isConstrained = false,
                pullFactor = 1.0f,
                stepMultiplier = 1.0f,
                maxBurstOverride = null
            )
        }
        val constrained = availMb in 1 until lowThresholdMb
        return if (constrained) {
            RamGuardState(
                availableMb = availMb,
                isConstrained = true,
                pullFactor = 0.85f,
                stepMultiplier = 0.75f,
                maxBurstOverride = 1
            )
        } else {
            RamGuardState(
                availableMb = availMb,
                isConstrained = false,
                pullFactor = 1.0f,
                stepMultiplier = 1.0f,
                maxBurstOverride = null
            )
        }
    }

    private fun readAvailableMb(): Long {
        val am = activityManager ?: return 1024L
        return runCatching {
            am.getMemoryInfo(memInfo)
            memInfo.availMem / (1024L * 1024L)
        }.getOrDefault(1024L)
    }
}
