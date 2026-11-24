package com.a17.assist.ffvn.engine

import android.os.SystemClock
import com.a17.assist.ffvn.data.AimZone
import com.a17.assist.ffvn.data.AppConfig
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.data.DeviceCalibration
import com.a17.assist.ffvn.data.DisplayScaleMetrics
import com.a17.assist.ffvn.data.DistanceMode
import com.a17.assist.ffvn.data.DistanceProfileSpec
import com.a17.assist.ffvn.inject.Injector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlin.random.Random

data class EngineTelemetry(
    val isAwake: Boolean = false,
    val isPausedByPanel: Boolean = false,
    val isPulling: Boolean = false,
    val activeZone: AimZone = AimZone.HEAD,
    val activeDistance: DistanceMode = DistanceMode.NEAR,
    val lastPullPx: Float = 0f,
    val thermalFactor: Float = 1.0f,
    val ramConstrained: Boolean = false,
    val eventsPerSec: Int = 0
)

/**
 * AimEngine — 17ms tick loop implementing the 10-step non-root touch assist pipeline.
 * Never auto-fires; only moves pointer during an open fire_gate when game is foreground,
 * master is enabled, and overlay panel is closed.
 */
class AimEngine(
    private val configRepo: ConfigRepository,
    private val injectorProvider: () -> Injector,
    private val metricsProvider: () -> DisplayScaleMetrics,
    private val thermalGuard: ThermalGuard,
    private val ramGuard: RamGuard,
    private val hapticCtl: HapticCtl
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val zoneSelector = ZoneSelector()
    private val burstGovernor = BurstGovernor()
    val timingLearner = TimingLearner()

    @Volatile private var gameForeground: Boolean = false
    @Volatile private var panelOpen: Boolean = false
    @Volatile private var fireGateOpen: Boolean = false
    @Volatile private var fireAnchorX: Int = 0
    @Volatile private var fireAnchorY: Int = 0

    private var lastFireCloseMs: Long = 0L
    private var lastDistanceMode: DistanceMode = DistanceMode.NEAR
    private var lastAnchorPxForHysteresis: Float = 13f

    private enum class Phase {
        IDLE,
        REACTION_WAIT,
        EARLY_GUARD,
        RAMPING,
        CREEPING,
        RETURNING,
        SETTLING
    }

    private var phase: Phase = Phase.IDLE
    private var phaseStartMs: Long = 0L
    private var shotStartMs: Long = 0L
    private var reactionWaitMs: Long = 0L

    private var stepWeights: FloatArray = FloatArray(0)
    private var stepIndex: Int = 0
    private var targetTotalDx: Float = 0f
    private var targetTotalDy: Float = 0f
    private var pulledSoFarDx: Float = 0f
    private var pulledSoFarDy: Float = 0f
    private var fracAccX: Float = 0f
    private var fracAccY: Float = 0f
    private var currentTouchX: Int = 0
    private var currentTouchY: Int = 0
    private var pointerActive: Boolean = false
    private var stutterAppliedThisShot: Boolean = false

    private var returnWeights: FloatArray = FloatArray(0)
    private var returnStepIndex: Int = 0
    private var returnTotalDx: Float = 0f
    private var returnTotalDy: Float = 0f

    private var eventTimestamps = ArrayDeque<Long>(64)

    private val _telemetry = MutableStateFlow(EngineTelemetry())
    val telemetry: StateFlow<EngineTelemetry> = _telemetry.asStateFlow()

    fun setGameForeground(foreground: Boolean) {
        gameForeground = foreground
        if (!foreground) {
            forceStopPull()
            thermalGuard.reset()
        }
        updateTelemetry()
    }

    fun setPanelOpen(open: Boolean) {
        panelOpen = open
        if (open) {
            // Pause immediately when overlay panel opens
            forceStopPull()
        }
        updateTelemetry()
    }

    fun onFireGateOpened(x: Int, y: Int) {
        val cfg = SafetyGuard.enforceConfigCaps(configRepo.current())
        if (!cfg.master || !gameForeground || panelOpen) return

        val now = SystemClock.uptimeMillis()
        val ramState = ramGuard.evaluate(cfg.antiCheatStealth.ramGuard, cfg.antiCheatStealth.ramLowThresholdMb)
        when (burstGovernor.evaluateTrigger(now, cfg.antiCheatStealth, ramState.maxBurstOverride)) {
            BurstGovernor.Decision.ThrottleBlock -> return
            BurstGovernor.Decision.MergeWithActive -> {
                fireGateOpen = true
                return
            }
            BurstGovernor.Decision.AllowNewBurst -> Unit
        }

        val metrics = metricsProvider()
        val (defaultX, defaultY) = DeviceCalibration.getFireButtonCenter(metrics, cfg.calibration)
        fireAnchorX = if (x > 0) x else defaultX
        fireAnchorY = if (y > 0) y else defaultY
        fireGateOpen = true
        shotStartMs = now
        stutterAppliedThisShot = false
        pulledSoFarDx = 0f
        pulledSoFarDy = 0f
        fracAccX = 0f
        fracAccY = 0f

        val reacquireDelta = if (lastFireCloseMs > 0L) now - lastFireCloseMs else Long.MAX_VALUE
        if (reacquireDelta < cfg.behaviorSimulation.reacquireSkipMs) {
            // Skip reaction wait when reacquiring < 300ms
            reactionWaitMs = 0L
            phase = Phase.EARLY_GUARD
            phaseStartMs = now
        } else {
            val minR = cfg.behaviorSimulation.reactionMinMs
            val maxR = maxOf(minR, cfg.behaviorSimulation.reactionMaxMs)
            val sampled = if (maxR > minR) Random.nextInt(minR, maxR + 1) else minR
            val learned = timingLearner.getCompensationMs()
            reactionWaitMs = (sampled + learned).coerceIn(
                cfg.safetyCaps.minReactionMs.toLong(),
                cfg.safetyCaps.maxReactionMs.toLong()
            )
            phase = Phase.REACTION_WAIT
            phaseStartMs = now
        }
        ensureLoopRunning()
        updateTelemetry()
    }

    fun onFireGateClosed() {
        if (!fireGateOpen && phase == Phase.IDLE) return
        val now = SystemClock.uptimeMillis()
        fireGateOpen = false
        lastFireCloseMs = now
        burstGovernor.onRelease(now)

        val cfg = SafetyGuard.enforceConfigCaps(configRepo.current())
        val holdDuration = now - shotStartMs
        timingLearner.recordReactionDelta(holdDuration, cfg.assistStrength.rampMs)

        if (pointerActive && (abs(pulledSoFarDy) > 0.5f || abs(pulledSoFarDx) > 0.5f)) {
            // Step 8: return release_return_pct of pulled distance over 55ms
            val returnPct = cfg.behaviorSimulation.releaseReturnPct
            returnTotalDx = -pulledSoFarDx * returnPct
            returnTotalDy = -pulledSoFarDy * returnPct
            val steps = (cfg.behaviorSimulation.releaseReturnMs / 17L).toInt().coerceIn(2, 5)
            returnWeights = CurveGen.generateReturnWeights(steps)
            returnStepIndex = 0
            phase = Phase.RETURNING
            phaseStartMs = now
        } else {
            finishPointer(now)
            phase = Phase.IDLE
        }
        updateTelemetry()
    }

    fun forceStopPull() {
        fireGateOpen = false
        val now = SystemClock.uptimeMillis()
        if (pointerActive) {
            finishPointer(now)
        }
        phase = Phase.IDLE
        pulledSoFarDx = 0f
        pulledSoFarDy = 0f
        fracAccX = 0f
        fracAccY = 0f
        updateTelemetry()
    }

    fun start() {
        ensureLoopRunning()
    }

    fun stop() {
        forceStopPull()
        tickJob?.cancel()
        tickJob = null
    }

    private fun ensureLoopRunning() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            var lastTickMs = SystemClock.uptimeMillis()
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                val dt = (now - lastTickMs).coerceAtLeast(1L)
                lastTickMs = now

                val cfg = SafetyGuard.enforceConfigCaps(configRepo.current())
                if (!cfg.master || !gameForeground || panelOpen) {
                    if (pointerActive || phase != Phase.IDLE) {
                        forceStopPull()
                    }
                    delay(80L)
                    continue
                }

                thermalGuard.onActiveTick(now)
                processTick(now, dt, cfg)
                delay(17L)
            }
        }
    }

    private fun processTick(now: Long, dtMs: Long, cfg: AppConfig) {
        when (phase) {
            Phase.IDLE -> return

            Phase.REACTION_WAIT -> {
                if (!fireGateOpen) {
                    phase = Phase.IDLE
                    return
                }
                if (now - phaseStartMs >= reactionWaitMs) {
                    phase = Phase.EARLY_GUARD
                    phaseStartMs = now
                }
            }

            Phase.EARLY_GUARD -> {
                if (!fireGateOpen) {
                    phase = Phase.IDLE
                    return
                }
                if (now - phaseStartMs >= cfg.behaviorSimulation.earlyGuardMs) {
                    preparePullRamp(now, cfg)
                    phase = Phase.RAMPING
                    phaseStartMs = now
                }
            }

            Phase.RAMPING -> {
                if (!fireGateOpen) {
                    onFireGateClosed()
                    return
                }
                if (stepIndex < stepWeights.size) {
                    val w = stepWeights[stepIndex]
                    stepIndex++
                    var stepDx = targetTotalDx * w
                    var stepDy = targetTotalDy * w

                    // Step 9: Stutter compensation if frame drop > 35ms (max once per shot)
                    if (cfg.behaviorSimulation.stutterCompensation &&
                        !stutterAppliedThisShot &&
                        dtMs > cfg.behaviorSimulation.stutterThresholdMs
                    ) {
                        stutterAppliedThisShot = true
                        val scale = metricsProvider().deviceScale
                        stepDy -= 1.0f * scale
                    }

                    injectIncremental(now, stepDx, stepDy, cfg)
                } else {
                    phase = Phase.CREEPING
                    phaseStartMs = now
                }
            }

            Phase.CREEPING -> {
                if (!fireGateOpen) {
                    onFireGateClosed()
                    return
                }
                if (!cfg.behaviorSimulation.creep ||
                    (now - phaseStartMs) >= cfg.behaviorSimulation.creepMaxMs
                ) {
                    return
                }
                val scale = metricsProvider().deviceScale
                val creepStepDy = -(cfg.behaviorSimulation.creepPxS * scale * (dtMs / 1000f))
                injectIncremental(now, 0f, creepStepDy, cfg)
            }

            Phase.RETURNING -> {
                if (returnStepIndex < returnWeights.size) {
                    val w = returnWeights[returnStepIndex]
                    returnStepIndex++
                    injectIncremental(now, returnTotalDx * w, returnTotalDy * w, cfg)
                } else {
                    finishPointer(now)
                    phase = Phase.SETTLING
                    phaseStartMs = now
                }
            }

            Phase.SETTLING -> {
                if (now - phaseStartMs >= cfg.behaviorSimulation.settleMs) {
                    phase = Phase.IDLE
                }
            }
        }
        updateTelemetry()
    }

    private fun preparePullRamp(now: Long, cfg: AppConfig) {
        val metrics = metricsProvider()
        val deviceScale = metrics.deviceScale

        // Step 3: Zone selection
        val zone = zoneSelector.selectZone(now, cfg.decisionLogic)
        val zoneMultiplier = when (zone) {
            AimZone.HEAD -> 1.0f
            AimZone.NECK -> 0.82f
            AimZone.CHEST -> 0.65f
        }

        // Step 4: Distance profile with 6px hysteresis
        val (distMode, profile) = selectDistanceProfile(cfg)

        // Step 5: Compute total pull
        val thermalFactor = thermalGuard.getThermalFactor(cfg.antiCheatStealth.thermalGuard, now)
        val ramState = ramGuard.evaluate(cfg.antiCheatStealth.ramGuard, cfg.antiCheatStealth.ramLowThresholdMb)
        val sensFactor = computeSensitivityFactor(cfg)

        val anchor = if (distMode == DistanceMode.NEAR) {
            cfg.assistStrength.headAnchorPx
        } else {
            profile.anchorPx
        }

        val rawTotal = anchor *
            profile.capMultiplier *
            zoneMultiplier *
            cfg.assistStrength.pullMultiplier *
            sensFactor *
            deviceScale *
            thermalFactor *
            ramState.pullFactor

        // Step 10: SafetyGuard clamps total pull LAST
        var total = SafetyGuard.capTotalPull(rawTotal, cfg, deviceScale)

        // Undershoot 2% & Overshoot 3% random micro-variation
        val undershoot = 1f - Random.nextFloat() * cfg.behaviorSimulation.undershootPct
        val overshoot = if (cfg.behaviorSimulation.overshoot) {
            1f + Random.nextFloat() * cfg.behaviorSimulation.overshootPct
        } else {
            1f
        }
        total = SafetyGuard.capTotalPull(total * undershoot * overshoot, cfg, deviceScale)

        val dy = -total * (cfg.assistStrength.lockStrength / 100f)
        val randSign = (Random.nextFloat() * 2f) - 1f
        val dx = (tan(0.05) * total * (cfg.assistStrength.aimLightness / 100f) *
            cfg.behaviorSimulation.jitter * randSign).toFloat()

        targetTotalDx = dx
        targetTotalDy = dy

        // Step 6: Nested Bezier weights
        val rampMs = profile.rampMs.coerceIn(cfg.safetyCaps.minRampMs, cfg.safetyCaps.maxRampMs)
        stepWeights = CurveGen.generateStepWeights(rampMs, cfg.behaviorSimulation.curveFriction)
        stepIndex = 0

        // Initialize pointer down at fire anchor
        currentTouchX = fireAnchorX
        currentTouchY = fireAnchorY
        val injector = injectorProvider()
        if (injector.isReady && canEmitEvent(now, cfg)) {
            injector.down(currentTouchX, currentTouchY, now)
            recordEventTimestamp(now)
            pointerActive = true
        }

        if (zone == AimZone.HEAD) {
            hapticCtl.playHeadLock(cfg.fingerFeedback)
        }
    }

    private fun injectIncremental(now: Long, rawDx: Float, rawDy: Float, cfg: AppConfig) {
        val metrics = metricsProvider()
        val ramState = ramGuard.evaluate(cfg.antiCheatStealth.ramGuard, cfg.antiCheatStealth.ramLowThresholdMb)

        // Step 10: SafetyGuard caps per-step delta LAST
        val (cappedDx, cappedDy) = SafetyGuard.capStepDelta(
            rawDx,
            rawDy,
            cfg,
            metrics.deviceScale,
            ramState.stepMultiplier
        )

        // Fractional accumulator so sub-pixel precision is preserved across ticks
        fracAccX += cappedDx
        fracAccY += cappedDy

        val intDx = fracAccX.roundToInt()
        val intDy = fracAccY.roundToInt()
        fracAccX -= intDx
        fracAccY -= intDy

        if (intDx == 0 && intDy == 0) return

        val nextX = (currentTouchX + intDx).coerceIn(1, maxOf(1, metrics.screenWidth - 1))
        val nextY = (currentTouchY + intDy).coerceIn(1, maxOf(1, metrics.screenHeight - 1))

        val injector = injectorProvider()
        if (pointerActive && injector.isReady && canEmitEvent(now, cfg)) {
            injector.move(nextX, nextY, now)
            recordEventTimestamp(now)
            currentTouchX = nextX
            currentTouchY = nextY
            pulledSoFarDx += intDx
            pulledSoFarDy += intDy
        }
    }

    private fun finishPointer(now: Long) {
        if (pointerActive) {
            val injector = injectorProvider()
            if (injector.isReady) {
                injector.up(currentTouchX, currentTouchY, now)
                recordEventTimestamp(now)
            }
            pointerActive = false
        }
    }

    private fun selectDistanceProfile(cfg: AppConfig): Pair<DistanceMode, DistanceProfileSpec> {
        val requested = runCatching {
            DistanceMode.valueOf(cfg.decisionLogic.activeDistanceMode.uppercase())
        }.getOrDefault(DistanceMode.NEAR)

        val specForRequested = when (requested) {
            DistanceMode.NEAR -> cfg.distanceProfiles.near
            DistanceMode.MID -> cfg.distanceProfiles.mid
            DistanceMode.FAR -> cfg.distanceProfiles.far
        }
        val specForLast = when (lastDistanceMode) {
            DistanceMode.NEAR -> cfg.distanceProfiles.near
            DistanceMode.MID -> cfg.distanceProfiles.mid
            DistanceMode.FAR -> cfg.distanceProfiles.far
        }

        // Apply 6px hysteresis if anchor difference < hysteresis_px
        if (requested != lastDistanceMode &&
            abs(specForRequested.anchorPx - lastAnchorPxForHysteresis) < cfg.distanceProfiles.hysteresisPx &&
            requested != DistanceMode.NEAR && lastDistanceMode != DistanceMode.FAR
        ) {
            return lastDistanceMode to specForLast
        }

        lastDistanceMode = requested
        lastAnchorPxForHysteresis = specForRequested.anchorPx
        return requested to specForRequested
    }

    private fun computeSensitivityFactor(cfg: AppConfig): Float {
        // Maps 0..200 user sensitivity smoothly around default (170/160/150) into [0.75, 1.25]
        val weighted = (cfg.controlsConfig.generalSens * 0.45f +
            cfg.controlsConfig.fireSens * 0.40f +
            cfg.controlsConfig.scopeSens * 0.15f)
        val normalized = (weighted / 163.5f).coerceIn(0.65f, 1.30f)
        return normalized
    }

    private fun canEmitEvent(now: Long, cfg: AppConfig): Boolean {
        val minInterval = SafetyGuard.minEventIntervalMs(cfg.safetyCaps)
        val last = eventTimestamps.lastOrNull() ?: 0L
        if (now - last < minInterval) return false

        while (eventTimestamps.isNotEmpty() && now - eventTimestamps.first() > 1000L) {
            eventTimestamps.removeFirst()
        }
        val maxPerSec = minOf(cfg.antiCheatStealth.maxEventsPerSec, cfg.safetyCaps.maxEventsPerSec)
        return eventTimestamps.size < maxPerSec
    }

    private fun recordEventTimestamp(now: Long) {
        eventTimestamps.addLast(now)
    }

    private fun updateTelemetry() {
        val cfg = configRepo.current()
        val now = SystemClock.uptimeMillis()
        while (eventTimestamps.isNotEmpty() && now - eventTimestamps.first() > 1000L) {
            eventTimestamps.removeFirst()
        }
        val ramState = ramGuard.evaluate(cfg.antiCheatStealth.ramGuard, cfg.antiCheatStealth.ramLowThresholdMb)
        _telemetry.value = EngineTelemetry(
            isAwake = cfg.master && gameForeground && !panelOpen,
            isPausedByPanel = panelOpen,
            isPulling = pointerActive,
            activeZone = zoneSelector.selectZone(now, cfg.decisionLogic),
            activeDistance = lastDistanceMode,
            lastPullPx = abs(pulledSoFarDy),
            thermalFactor = thermalGuard.getThermalFactor(cfg.antiCheatStealth.thermalGuard, now),
            ramConstrained = ramState.isConstrained,
            eventsPerSec = eventTimestamps.size
        )
    }
}
