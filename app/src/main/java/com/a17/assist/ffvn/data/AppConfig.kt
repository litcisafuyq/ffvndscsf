package com.a17.assist.ffvn.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DistanceMode {
    NEAR,
    MID,
    FAR
}

@Serializable
enum class AimZone {
    HEAD,
    NECK,
    CHEST
}

@Serializable
data class AppConfig(
    @SerialName("master") val master: Boolean = true,
    @SerialName("assist_strength") val assistStrength: AssistStrength = AssistStrength(),
    @SerialName("decision_logic") val decisionLogic: DecisionLogic = DecisionLogic(),
    @SerialName("distance_profiles") val distanceProfiles: DistanceProfiles = DistanceProfiles(),
    @SerialName("behavior_simulation") val behaviorSimulation: BehaviorSimulation = BehaviorSimulation(),
    @SerialName("finger_feedback") val fingerFeedback: FingerFeedback = FingerFeedback(),
    @SerialName("controls_config") val controlsConfig: ControlsConfig = ControlsConfig(),
    @SerialName("anti_cheat_stealth") val antiCheatStealth: AntiCheatStealth = AntiCheatStealth(),
    @SerialName("safety_caps") val safetyCaps: SafetyCaps = SafetyCaps(),
    @SerialName("calibration") val calibration: Calibration = Calibration()
)

@Serializable
data class AssistStrength(
    @SerialName("lock_strength") val lockStrength: Int = 72,
    @SerialName("aim_lightness") val aimLightness: Int = 65,
    @SerialName("pull_multiplier") val pullMultiplier: Float = 1.05f,
    @SerialName("head_anchor_px") val headAnchorPx: Float = 13.0f,
    @SerialName("max_total_pull_px") val maxTotalPullPx: Float = 24.0f,
    @SerialName("max_step_px") val maxStepPx: Float = 3.0f,
    @SerialName("ramp_ms") val rampMs: Int = 100
)

@Serializable
data class DecisionLogic(
    @SerialName("head_lock") val headLock: Boolean = true,
    @SerialName("neck_fallback") val neckFallback: Boolean = true,
    @SerialName("ignore_prone") val ignoreProne: Boolean = true,
    @SerialName("zone_priority") val zonePriority: List<String> = listOf("HEAD", "NECK", "CHEST"),
    @SerialName("hysteresis_ms") val hysteresisMs: Long = 100L,
    @SerialName("switch_penalty_ms") val switchPenaltyMs: Long = 60L,
    @SerialName("prefer_neck_when_chest_locked") val preferNeckWhenChestLocked: Boolean = true,
    @SerialName("miss_soft_lock_threshold") val missSoftLockThreshold: Int = 2,
    @SerialName("active_distance_mode") val activeDistanceMode: String = "NEAR"
)

@Serializable
data class DistanceProfileSpec(
    @SerialName("min_m") val minM: Int,
    @SerialName("max_m") val maxM: Int,
    @SerialName("anchor_px") val anchorPx: Float,
    @SerialName("cap_multiplier") val capMultiplier: Float,
    @SerialName("ramp_ms") val rampMs: Int
)

@Serializable
data class DistanceProfiles(
    @SerialName("near") val near: DistanceProfileSpec = DistanceProfileSpec(0, 15, 13.0f, 1.0f, 100),
    @SerialName("mid") val mid: DistanceProfileSpec = DistanceProfileSpec(15, 40, 7.0f, 0.85f, 120),
    @SerialName("far") val far: DistanceProfileSpec = DistanceProfileSpec(40, 99, 3.0f, 0.75f, 150),
    @SerialName("hysteresis_px") val hysteresisPx: Float = 6.0f
)

@Serializable
data class BehaviorSimulation(
    @SerialName("early_guard_ms") val earlyGuardMs: Long = 65L,
    @SerialName("reaction_min_ms") val reactionMinMs: Int = 140,
    @SerialName("reaction_max_ms") val reactionMaxMs: Int = 175,
    @SerialName("reacquire_skip_ms") val reacquireSkipMs: Long = 300L,
    @SerialName("jitter") val jitter: Float = 0.10f,
    @SerialName("creep") val creep: Boolean = true,
    @SerialName("creep_px_s") val creepPxS: Float = 0.9f,
    @SerialName("creep_max_ms") val creepMaxMs: Long = 400L,
    @SerialName("release_return_pct") val releaseReturnPct: Float = 0.55f,
    @SerialName("release_return_ms") val releaseReturnMs: Long = 55L,
    @SerialName("settle_ms") val settleMs: Long = 130L,
    @SerialName("overshoot") val overshoot: Boolean = true,
    @SerialName("undershoot_pct") val undershootPct: Float = 0.02f,
    @SerialName("overshoot_pct") val overshootPct: Float = 0.03f,
    @SerialName("stutter_compensation") val stutterCompensation: Boolean = true,
    @SerialName("stutter_threshold_ms") val stutterThresholdMs: Long = 35L,
    @SerialName("curve_friction") val curveFriction: Float = 0.30f
)

@Serializable
data class FingerFeedback(
    @SerialName("haptic") val haptic: Boolean = true,
    @SerialName("hud") val hud: Boolean = true,
    @SerialName("head_pattern_ms") val headPatternMs: List<Long> = listOf(20L, 45L, 20L),
    @SerialName("miss_pattern_ms") val missPatternMs: List<Long> = listOf(32L),
    @SerialName("haptic_throttle_ms") val hapticThrottleMs: Long = 450L
)

@Serializable
data class ControlsConfig(
    @SerialName("general_sens") val generalSens: Int = 170,
    @SerialName("fire_sens") val fireSens: Int = 160,
    @SerialName("scope_sens") val scopeSens: Int = 150,
    @SerialName("sens_min") val sensMin: Int = 0,
    @SerialName("sens_max") val sensMax: Int = 200
)

@Serializable
data class AntiCheatStealth(
    @SerialName("thermal_guard") val thermalGuard: Boolean = true,
    @SerialName("ram_guard") val ramGuard: Boolean = true,
    @SerialName("burst_governor") val burstGovernor: Boolean = true,
    @SerialName("tap_mode_fallback") val tapModeFallback: Boolean = true,
    @SerialName("max_events_per_sec") val maxEventsPerSec: Int = 60,
    @SerialName("game_exit_sleep_ms") val gameExitSleepMs: Long = 3000L,
    @SerialName("burst_limit") val burstLimit: Int = 2,
    @SerialName("burst_debounce_ms") val burstDebounceMs: Long = 110L,
    @SerialName("double_tap_merge_ms") val doubleTapMergeMs: Long = 90L,
    @SerialName("ram_low_threshold_mb") val ramLowThresholdMb: Long = 400L
)

@Serializable
data class SafetyCaps(
    @SerialName("min_lock_strength") val minLockStrength: Int = 55,
    @SerialName("max_lock_strength") val maxLockStrength: Int = 85,
    @SerialName("min_aim_lightness") val minAimLightness: Int = 52,
    @SerialName("max_aim_lightness") val maxAimLightness: Int = 80,
    @SerialName("min_pull_multiplier") val minPullMultiplier: Float = 0.8f,
    @SerialName("max_pull_multiplier") val maxPullMultiplier: Float = 1.6f,
    @SerialName("min_head_anchor_px") val minHeadAnchorPx: Float = 8.0f,
    @SerialName("max_head_anchor_px") val maxHeadAnchorPx: Float = 18.0f,
    @SerialName("min_total_pull_px") val minTotalPullPx: Float = 12.0f,
    @SerialName("max_total_pull_px") val maxTotalPullPx: Float = 24.0f,
    @SerialName("min_step_px") val minStepPx: Float = 1.0f,
    @SerialName("max_step_px") val maxStepPx: Float = 3.0f,
    @SerialName("min_ramp_ms") val minRampMs: Int = 80,
    @SerialName("max_ramp_ms") val maxRampMs: Int = 150,
    @SerialName("min_creep_px_s") val minCreepPxS: Float = 0.5f,
    @SerialName("max_creep_px_s") val maxCreepPxS: Float = 1.5f,
    @SerialName("min_release_return_pct") val minReleaseReturnPct: Float = 0.4f,
    @SerialName("max_release_return_pct") val maxReleaseReturnPct: Float = 0.7f,
    @SerialName("min_reaction_ms") val minReactionMs: Int = 120,
    @SerialName("max_reaction_ms") val maxReactionMs: Int = 180,
    @SerialName("min_jitter") val minJitter: Float = 0.05f,
    @SerialName("max_jitter") val maxJitter: Float = 0.15f,
    @SerialName("max_events_per_sec") val maxEventsPerSec: Int = 60
)

@Serializable
data class Calibration(
    @SerialName("ref_height") val refHeight: Int = 1200,
    @SerialName("ref_dpi") val refDpi: Int = 384,
    @SerialName("base_factor") val baseFactor: Float = 0.513f,
    @SerialName("fire_button_x_ratio") val fireButtonXRatio: Float = 0.84f,
    @SerialName("fire_button_y_ratio") val fireButtonYRatio: Float = 0.72f,
    @SerialName("fire_button_radius_dp") val fireButtonRadiusDp: Int = 44,
    @SerialName("bubble_x") val bubbleX: Int = 36,
    @SerialName("bubble_y") val bubbleY: Int = 260
)
