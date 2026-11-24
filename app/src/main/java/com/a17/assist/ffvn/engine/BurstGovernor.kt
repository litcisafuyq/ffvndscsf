package com.a17.assist.ffvn.engine

import com.a17.assist.ffvn.data.AntiCheatStealth

/**
 * BurstGovernor:
 * - Default max burst = 2 (reduced to 1 if RamGuard constrains)
 * - Debounce = 110ms
 * - Merge double-tap within 90ms
 */
class BurstGovernor {

    private var burstCount = 0
    private var windowStartMs = 0L
    private var lastDownMs = 0L
    private var lastUpMs = 0L

    sealed class Decision {
        data object AllowNewBurst : Decision()
        data object MergeWithActive : Decision()
        data object ThrottleBlock : Decision()
    }

    fun evaluateTrigger(
        nowMs: Long,
        stealth: AntiCheatStealth,
        ramMaxBurstOverride: Int?
    ): Decision {
        if (!stealth.burstGovernor) {
            lastDownMs = nowMs
            return Decision.AllowNewBurst
        }

        val maxBurst = ramMaxBurstOverride ?: stealth.burstLimit.coerceAtLeast(1)

        // Merge rapid double-tap within 90ms of previous release
        if (lastUpMs > 0L && (nowMs - lastUpMs) <= stealth.doubleTapMergeMs) {
            lastDownMs = nowMs
            return Decision.MergeWithActive
        }

        // Reset burst counter after cooldown window (debounce * 4)
        if (nowMs - windowStartMs > stealth.burstDebounceMs * 4L) {
            burstCount = 0
            windowStartMs = nowMs
        }

        // Debounce rapid re-triggers < 110ms
        if (lastDownMs > 0L && (nowMs - lastDownMs) < stealth.burstDebounceMs) {
            return Decision.ThrottleBlock
        }

        if (burstCount >= maxBurst) {
            return Decision.ThrottleBlock
        }

        burstCount++
        if (burstCount == 1) {
            windowStartMs = nowMs
        }
        lastDownMs = nowMs
        return Decision.AllowNewBurst
    }

    fun onRelease(nowMs: Long) {
        lastUpMs = nowMs
    }

    fun reset() {
        burstCount = 0
        windowStartMs = 0L
        lastDownMs = 0L
        lastUpMs = 0L
    }
}
