package com.a17.assist.ffvn.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log

/**
 * Fallback khi Shizuku chưa cấp. Dùng dispatchGesture + continueStroke.
 * Chỉ giữ điểm đích mới nhất, không xếp hàng dài để tránh trễ dồn.
 */
class A11yInjector(@Suppress("unused") private val context: Context) : Injector {

    companion object {
        private const val TAG = "A11yInjector"
        private const val STROKE_MS = 16L
        private const val FINAL_MS = 10L
    }

    private val thread = HandlerThread("a17-a11y").apply { start() }
    private val h = Handler(thread.looper)

    private var stroke: GestureDescription.StrokeDescription? = null
    private var active = false
    private var busy = false
    private var targetX = 0f
    private var targetY = 0f
    private var lastX = 0f
    private var lastY = 0f

    override val isReady: Boolean
        get() = A17A11yService.instance != null

    private val cb = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(g: GestureDescription?) = pump()
        override fun onCancelled(g: GestureDescription?) { busy = false; pump() }
    }

    override fun down(x: Int, y: Int, eventTime: Long) {
        val svc = A17A11yService.instance ?: return
        if (active) up(x, y, eventTime)
        active = true
        busy = true
        lastX = x.toFloat(); lastY = y.toFloat()
        targetX = lastX; targetY = lastY
        val p = Path().apply { moveTo(lastX, lastY) }
        val s = GestureDescription.StrokeDescription(p, 0, STROKE_MS, true)
        stroke = s
        svc.dispatchGesture(build(s), cb, h)
    }

    override fun move(x: Int, y: Int, eventTime: Long) {
        if (!active) return
        targetX = x.toFloat(); targetY = y.toFloat()
        if (!busy) pump()
    }

    override fun up(x: Int, y: Int, eventTime: Long) {
        if (!active) return
        active = false
        targetX = x.toFloat(); targetY = y.toFloat()
        if (!busy) pump()
    }

    private fun pump() {
        val svc = A17A11yService.instance ?: run { busy = false; return }
        val prev = stroke ?: run { busy = false; return }

        if (targetX == lastX && targetY == lastY && active) { busy = false; return }

        val p = Path().apply {
            moveTo(lastX, lastY)
            lineTo(targetX, targetY)
        }
        lastX = targetX; lastY = targetY

        val cont = runCatching {
            prev.continueStroke(p, 0, if (active) STROKE_MS else FINAL_MS, active)
        }.getOrElse {
            Log.w(TAG, "continueStroke lỗi", it)
            active = false; busy = false
            return
        }
        stroke = cont
        busy = true
        svc.dispatchGesture(build(cont), cb, h)
    }

    private fun build(s: GestureDescription.StrokeDescription) =
        GestureDescription.Builder().addStroke(s).build()

    fun shutdown() {
        active = false
        thread.quitSafely()
    }
}
