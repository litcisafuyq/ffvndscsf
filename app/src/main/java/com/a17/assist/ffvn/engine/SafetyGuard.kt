package com.a17.assist.ffvn.engine

import com.a17.assist.ffvn.data.AppConfig
import com.a17.assist.ffvn.data.SafetyCaps
import kotlin.math.hypot
import kotlin.math.min

/**
 * SafetyGuard always runs LAST and overrides any user slider or imported JSON value
 * that attempts to exceed safety_caps.
 */
object SafetyGuard {

    fun enforceConfigCaps(config: AppConfig): AppConfig {
        val caps = config.safetyCaps
        val fixedCaps = caps.copy(
            maxLockStrength = caps.maxLockStrength.coerceIn(55, 85),
            minLockStrength = caps.minLockStrength.coerceIn(55, 85),
            maxAimLightness = caps.maxAimLightness.coerceIn(52, 80),
            minAimLightness = caps.minAimLightness.coerceIn(52, 80),
            maxPullMultiplier = caps.maxPullMultiplier.coerceIn(0.8f, 1.6f),
            minPullMultiplier = caps.minPullMultiplier.coerceIn(0.8f, 1.6f),
            maxHeadAnchorPx = caps.maxHeadAnchorPx.coerceIn(8f, 18f),
            minHeadAnchorPx = caps.minHeadAnchorPx.coerceIn(8f, 18f),
            maxTotalPullPx = caps.maxTotalPullPx.coerceIn(12f, 24f),
            minTotalPullPx = caps.minTotalPullPx.coerceIn(12f, 24f),
            maxStepPx = caps.maxStepPx.coerceIn(1f, 3f),
            minStepPx = caps.minStepPx.coerceIn(1f, 3f),
            minRampMs = caps.minRampMs.coerceIn(80, 150),
            maxRampMs = caps.maxRampMs.coerceIn(80, 150),
            minCreepPxS = caps.minCreepPxS.coerceIn(0.5f, 1.5f),
            maxCreepPxS = caps.maxCreepPxS.coerceIn(0.5f, 1.5f),
            minReleaseReturnPct = caps.minReleaseReturnPct.coerceIn(0.4f, 0.7f),
            maxReleaseReturnPct = caps.maxReleaseReturnPct.coerceIn(0.4f, 0.7f),
            minReactionMs = caps.minReactionMs.coerceIn(120, 180),
            maxReactionMs = caps.maxReactionMs.coerceIn(120, 180),
            minJitter = caps.minJitter.coerceIn(0.05f, 0.15f),
            maxJitter = caps.maxJitter.coerceIn(0.05f, 0.15f),
            maxEventsPerSec = caps.maxEventsPerSec.coerceIn(1, 60)
        )

        val assist = config.assistStrength.copy(
            lockStrength = config.assistStrength.lockStrength.coerceIn(fixedCaps.minLockStrength, fixedCaps.maxLockStrength),
            aimLightness = config.assistStrength.aimLightness.coerceIn(fixedCaps.minAimLightness, fixedCaps.maxAimLightness),
            pullMultiplier = config.assistStrength.pullMultiplier.coerceIn(fixedCaps.minPullMultiplier, fixedCaps.maxPullMultiplier),
            headAnchorPx = config.assistStrength.headAnchorPx.coerceIn(fixedCaps.minHeadAnchorPx, fixedCaps.maxHeadAnchorPx),
            maxTotalPullPx = config.assistStrength.maxTotalPullPx.coerceIn(fixedCaps.minTotalPullPx, fixedCaps.maxTotalPullPx),
            maxStepPx = config.assistStrength.maxStepPx.coerceIn(fixedCaps.minStepPx, fixedCaps.maxStepPx),
            rampMs = config.assistStrength.rampMs.coerceIn(fixedCaps.minRampMs, fixedCaps.maxRampMs)
        )

        val rawMinReact = config.behaviorSimulation.reactionMinMs.coerceIn(fixedCaps.minReactionMs, fixedCaps.maxReactionMs)
        val rawMaxReact = config.behaviorSimulation.reactionMaxMs.coerceIn(rawMinReact, fixedCaps.maxReactionMs)
        val behavior = config.behaviorSimulation.copy(
            reactionMinMs = rawMinReact,
            reactionMaxMs = rawMaxReact,
            jitter = config.behaviorSimulation.jitter.coerceIn(fixedCaps.minJitter, fixedCaps.maxJitter),
            creepPxS = config.behaviorSimulation.creepPxS.coerceIn(fixedCaps.minCreepPxS, fixedCaps.maxCreepPxS),
            releaseReturnPct = config.behaviorSimulation.releaseReturnPct.coerceIn(fixedCaps.minReleaseReturnPct, fixedCaps.maxReleaseReturnPct)
        )

        val controls = config.controlsConfig.copy(
            generalSens = config.controlsConfig.generalSens.coerceIn(0, 200),
            fireSens = config.controlsConfig.fireSens.coerceIn(0, 200),
            scopeSens = config.controlsConfig.scopeSens.coerceIn(0, 200),
            sensMin = 0,
            sensMax = 200
        )

        val stealth = config.antiCheatStealth.copy(
            maxEventsPerSec = config.antiCheatStealth.maxEventsPerSec.coerceIn(1, fixedCaps.maxEventsPerSec)
        )

        return config.copy(
            assistStrength = assist,
            behaviorSimulation = behavior,
            controlsConfig = controls,
            antiCheatStealth = stealth,
            safetyCaps = fixedCaps
        )
    }

    fun capTotalPull(rawTotalPx: Float, config: AppConfig, deviceScale: Float): Float {
        val hardMax = min(config.assistStrength.maxTotalPullPx, config.safetyCaps.maxTotalPullPx) * deviceScale
        val hardMin = 3.0f
        return rawTotalPx.coerceIn(hardMin, maxOf(hardMin, hardMax))
    }

    fun capStepDelta(
        dx: Float,
        dy: Float,
        config: AppConfig,
        deviceScale: Float,
        ramStepMultiplier: Float
    ): Pair<Float, Float> {
        val maxStep = min(config.assistStrength.maxStepPx, config.safetyCaps.maxStepPx) *
            deviceScale * ramStepMultiplier.coerceIn(0.4f, 1.0f)
        val safeMax = maxOf(0.5f, maxStep)
        val mag = hypot(dx, dy)
        return if (mag > safeMax && mag > 0f) {
            val scale = safeMax / mag
            (dx * scale) to (dy * scale)
        } else {
            dx to dy
        }
    }

    fun minEventIntervalMs(caps: SafetyCaps): Long {
        val hz = caps.maxEventsPerSec.coerceIn(1, 60)
        return (1000L / hz).coerceAtLeast(16L)
    }
}
