package com.a17.assist.ffvn.engine

import kotlin.math.roundToLong

/**
 * TimingLearner:
 * - Sliding window of 8 shots
 * - Learning rate = 0.25
 * - Max compensation cap = 50ms
 * - Resets immediately when weapon changes
 */
class TimingLearner(
    private val windowSize: Int = 8,
    private val learningRate: Float = 0.25f,
    private val maxCompCapMs: Long = 50L
) {
    private val samples = ArrayDeque<Long>(windowSize)
    private var currentWeaponId: String = "DEFAULT"
    private var learnedOffsetMs: Long = 0L

    fun onWeaponChanged(weaponId: String) {
        if (weaponId != currentWeaponId) {
            currentWeaponId = weaponId
            reset()
        }
    }

    fun recordReactionDelta(actualHoldDurationMs: Long, targetRampMs: Int) {
        val delta = (actualHoldDurationMs - targetRampMs).coerceIn(-maxCompCapMs, maxCompCapMs)
        if (samples.size >= windowSize) {
            samples.removeFirst()
        }
        samples.addLast(delta)

        val avg = if (samples.isNotEmpty()) samples.average().toFloat() else 0f
        val updated = learnedOffsetMs + learningRate * (avg - learnedOffsetMs)
        learnedOffsetMs = updated.roundToLong().coerceIn(-maxCompCapMs, maxCompCapMs)
    }

    fun getCompensationMs(): Long = learnedOffsetMs.coerceIn(-maxCompCapMs, maxCompCapMs)

    fun reset() {
        samples.clear()
        learnedOffsetMs = 0L
    }
}
