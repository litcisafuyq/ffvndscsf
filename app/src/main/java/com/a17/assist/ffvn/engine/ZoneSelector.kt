package com.a17.assist.ffvn.engine

import com.a17.assist.ffvn.data.AimZone
import com.a17.assist.ffvn.data.DecisionLogic

class ZoneSelector {

    private var currentZone: AimZone = AimZone.HEAD
    private var lastSwitchTimeMs: Long = 0L
    private var penaltyUntilMs: Long = 0L
    private var consecutiveMisses: Int = 0
    private var softLockNeckCycles: Int = 0

    fun recordShotOutcome(hitHead: Boolean) {
        if (hitHead) {
            consecutiveMisses = 0
        } else {
            consecutiveMisses++
            if (consecutiveMisses >= 2) {
                softLockNeckCycles = 1
                consecutiveMisses = 0
            }
        }
    }

    fun selectZone(
        nowMs: Long,
        logic: DecisionLogic,
        chestLocked: Boolean = false,
        targetProne: Boolean = false
    ): AimZone {
        if (logic.ignoreProne && targetProne) {
            return AimZone.CHEST
        }

        // 2 consecutive misses -> soft lock NECK for 1 cycle
        if (softLockNeckCycles > 0 && logic.neckFallback) {
            softLockNeckCycles--
            currentZone = AimZone.NECK
            lastSwitchTimeMs = nowMs
            return AimZone.NECK
        }

        val desired = resolveDesiredZone(logic, chestLocked)
        if (desired == currentZone) {
            return currentZone
        }

        // Hysteresis 100ms + switch_penalty 60ms
        val elapsedSinceSwitch = nowMs - lastSwitchTimeMs
        if (elapsedSinceSwitch < logic.hysteresisMs || nowMs < penaltyUntilMs) {
            return currentZone
        }

        currentZone = desired
        lastSwitchTimeMs = nowMs
        penaltyUntilMs = nowMs + logic.switchPenaltyMs
        return currentZone
    }

    private fun resolveDesiredZone(logic: DecisionLogic, chestLocked: Boolean): AimZone {
        if (chestLocked && logic.preferNeckWhenChestLocked && logic.neckFallback) {
            return AimZone.NECK
        }

        for (zoneName in logic.zonePriority) {
            val z = runCatching { AimZone.valueOf(zoneName.uppercase()) }.getOrNull() ?: continue
            when (z) {
                AimZone.HEAD -> if (logic.headLock) return AimZone.HEAD
                AimZone.NECK -> if (logic.neckFallback) return AimZone.NECK
                AimZone.CHEST -> return AimZone.CHEST
            }
        }
        return if (logic.neckFallback) AimZone.NECK else AimZone.CHEST
    }

    fun reset() {
        currentZone = AimZone.HEAD
        lastSwitchTimeMs = 0L
        penaltyUntilMs = 0L
        consecutiveMisses = 0
        softLockNeckCycles = 0
    }
}
