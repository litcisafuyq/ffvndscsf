package com.a17.assist.ffvn.detect

import com.a17.assist.ffvn.data.Calibration
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.data.DeviceCalibration
import com.a17.assist.ffvn.data.DisplayScaleMetrics
import com.a17.assist.ffvn.engine.AimEngine
import kotlin.math.hypot

/**
 * Coordinates fire_gate open/close triggers without ever intercepting game touches
 * or auto-firing. Supports calibrated fire zone and engine gating.
 */
class FireGateDetector(
    private val configRepo: ConfigRepository,
    private val metricsProvider: () -> DisplayScaleMetrics,
    private val aimEngine: AimEngine
) {
    @Volatile
    private var isGateOpen = false

    fun attach() = Unit

    fun detach() {
        closeGate()
    }

    fun openGate(x: Int, y: Int) {
        if (isGateOpen) return
        isGateOpen = true
        aimEngine.onFireGateOpened(x, y)
    }

    fun closeGate() {
        if (!isGateOpen) return
        isGateOpen = false
        aimEngine.onFireGateClosed()
    }

    fun isInsideCalibratedFireZone(x: Int, y: Int, calibration: Calibration): Boolean {
        val metrics = metricsProvider()
        val (cx, cy) = DeviceCalibration.getFireButtonCenter(metrics, calibration)
        val radiusPx = calibration.fireButtonRadiusDp * metrics.density
        return hypot((x - cx).toFloat(), (y - cy).toFloat()) <= radiusPx
    }
}
