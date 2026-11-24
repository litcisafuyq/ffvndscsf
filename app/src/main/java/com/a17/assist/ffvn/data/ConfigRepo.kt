package com.a17.assist.ffvn.data

import android.content.Context
import android.content.SharedPreferences

data class Cfg(
    val master: Boolean = true,
    val padOn: Boolean = true,
    val lockStrength: Int = 72,
    val aimLightness: Int = 65,
    val pullMultiplier: Double = 1.05,
    val sensGeneral: Int = 170,
    val sensFire: Int = 160,
    val sensScope: Int = 150,
    val headAnchorPx: Int = 13,
    val maxTotalPullPx: Int = 24,
    val maxStepPx: Int = 3,
    val rampMs: Int = 100,
    val creepPxS: Double = 0.9,
    val releaseReturnPct: Double = 0.55,
    val reactionMinMs: Int = 140,
    val reactionMaxMs: Int = 175,
    val jitter: Double = 0.1,
    val tickMs: Int = 17,
    val thermalFactor: Double = 1.0,
    val ramFactor: Double = 1.0,
    val headLock: Boolean = true,
    val neckFallback: Boolean = true,
    val thermalGuard: Boolean = true,
    val ramGuard: Boolean = true,
    val haptic: Boolean = true,
    val hud: Boolean = true,
    val creep: Boolean = true,
    val stutterComp: Boolean = true,
    val overshoot: Boolean = true,
    val burstGovernor: Boolean = true,
    val tapModeFallback: Boolean = false,
    val ignoreProne: Boolean = false
)

class ConfigRepo(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("a17_cfg", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private val dm = context.resources.displayMetrics

    private fun dp(v: Int) = (v * density).toInt()

    fun load(): Cfg = Cfg(
        master = sp.getBoolean("master", true),
        padOn = sp.getBoolean("pad_on", true),
        lockStrength = sp.getInt("lock_strength", 72).coerceIn(55, 85),
        aimLightness = sp.getInt("aim_lightness", 65).coerceIn(52, 80),
        pullMultiplier = sp.getFloat("pull_mult", 1.05f).toDouble().coerceIn(0.8, 1.6),
        sensGeneral = sp.getInt("sens_gen", 170).coerceIn(0, 200),
        sensFire = sp.getInt("sens_fire", 160).coerceIn(0, 200),
        sensScope = sp.getInt("sens_scope", 150).coerceIn(0, 200),
        headAnchorPx = sp.getInt("head_anchor", 13).coerceIn(8, 18),
        maxTotalPullPx = sp.getInt("max_pull", 24).coerceIn(12, 24),
        maxStepPx = sp.getInt("max_step", 3).coerceIn(1, 3),
        rampMs = sp.getInt("ramp", 100).coerceIn(80, 150),
        creepPxS = sp.getFloat("creep", 0.9f).toDouble().coerceIn(0.5, 1.5),
        releaseReturnPct = sp.getFloat("release_pct", 0.55f).toDouble().coerceIn(0.4, 0.7),
        reactionMinMs = sp.getInt("react_min", 140).coerceIn(120, 180),
        reactionMaxMs = sp.getInt("react_max", 175).coerceIn(120, 180),
        jitter = sp.getFloat("jitter", 0.1f).toDouble().coerceIn(0.05, 0.15),
        tickMs = sp.getInt("tick", 17).coerceIn(15, 20),
        thermalFactor = sp.getFloat("thermal", 1.0f).toDouble(),
        ramFactor = sp.getFloat("ram", 1.0f).toDouble(),
        headLock = sp.getBoolean("head_lock", true),
        neckFallback = sp.getBoolean("neck_fallback", true),
        thermalGuard = sp.getBoolean("thermal_guard", true),
        ramGuard = sp.getBoolean("ram_guard", true),
        haptic = sp.getBoolean("haptic", true),
        hud = sp.getBoolean("hud", true),
        creep = sp.getBoolean("creep_on", true),
        stutterComp = sp.getBoolean("stutter", true),
        overshoot = sp.getBoolean("overshoot", true),
        burstGovernor = sp.getBoolean("burst_gov", true),
        tapModeFallback = sp.getBoolean("tap_fallback", false),
        ignoreProne = sp.getBoolean("ignore_prone", false)
    )

    private fun put(k: String, v: Any) {
        sp.edit().apply {
            when (v) {
                is Boolean -> putBoolean(k, v)
                is Int -> putInt(k, v)
                is Float -> putFloat(k, v)
                is Double -> putFloat(k, v.toFloat())
            }
        }.apply()
    }

    // --- getter đọc lẻ cho UI ---
    fun tickMs() = sp.getInt("tick", 17).coerceIn(15, 20)
    fun lockStrength() = sp.getInt("lock_strength", 72).coerceIn(55, 85)
    fun aimLightness() = sp.getInt("aim_lightness", 65).coerceIn(52, 80)
    fun pullMultiplier() = sp.getFloat("pull_mult", 1.05f).toDouble().coerceIn(0.8, 1.6)
    fun sensGeneral() = sp.getInt("sens_gen", 170).coerceIn(0, 200)
    fun sensFire() = sp.getInt("sens_fire", 160).coerceIn(0, 200)
    fun sensScope() = sp.getInt("sens_scope", 150).coerceIn(0, 200)

    // --- setter ---
    fun setMaster(v: Boolean) = put("master", v)
    fun setPad(v: Boolean) = put("pad_on", v)
    fun setLockStrength(v: Int) = put("lock_strength", v.coerceIn(55, 85))
    fun setAimLightness(v: Int) = put("aim_lightness", v.coerceIn(52, 80))
    fun setPullMultiplier(v: Double) = put("pull_mult", v.coerceIn(0.8, 1.6))
    fun setSensGeneral(v: Int) = put("sens_gen", v.coerceIn(0, 200))
    fun setSensFire(v: Int) = put("sens_fire", v.coerceIn(0, 200))
    fun setSensScope(v: Int) = put("sens_scope", v.coerceIn(0, 200))
    fun setThermalFactor(v: Double) = put("thermal", v)
    fun setRamFactor(v: Double) = put("ram", v)

    // --- vị trí overlay ---
    fun bubbleX() = sp.getInt("bubble_x", dm.widthPixels - dp(64))
    fun bubbleY() = sp.getInt("bubble_y", dp(200))
    fun saveBubble(x: Int, y: Int) {
        sp.edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
    }

    fun padX(screenW: Int) = sp.getInt("pad_x", screenW - dp(150))
    fun padY(screenH: Int) = sp.getInt("pad_y", screenH - dp(300))
    fun savePad(x: Int, y: Int) {
        sp.edit().putInt("pad_x", x).putInt("pad_y", y).apply()
    }
}
