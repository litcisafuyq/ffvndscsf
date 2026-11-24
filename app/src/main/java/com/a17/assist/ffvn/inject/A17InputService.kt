package com.a17.assist.ffvn.inject

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import androidx.annotation.Keep
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku privileged shell process.
 * Holds InputManager via reflection, builds MotionEvent with pointer id 0,
 * fixed downTime on down(), injects via injectInputEvent, and always recycles event.
 */
@Keep
class A17InputService : IA17Input.Stub() {

    private var inputManager: InputManager? = null
    private var injectMethod: Method? = null
    private var downTimeMs: Long = 0L

    init {
        initReflection()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun initReflection() {
        runCatching {
            val imClass = InputManager::class.java
            val getInstance = imClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            inputManager = getInstance.invoke(null) as? InputManager

            injectMethod = imClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            injectMethod?.isAccessible = true
        }
    }

    override fun down(x: Int, y: Int, eventTime: Long) {
        val ts = if (eventTime > 0L) eventTime else SystemClock.uptimeMillis()
        downTimeMs = ts
        injectMotion(MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), downTimeMs, ts)
    }

    override fun move(x: Int, y: Int, eventTime: Long) {
        val ts = if (eventTime > 0L) eventTime else SystemClock.uptimeMillis()
        val dt = if (downTimeMs > 0L) downTimeMs else ts
        injectMotion(MotionEvent.ACTION_MOVE, x.toFloat(), y.toFloat(), dt, ts)
    }

    override fun up(x: Int, y: Int, eventTime: Long) {
        val ts = if (eventTime > 0L) eventTime else SystemClock.uptimeMillis()
        val dt = if (downTimeMs > 0L) downTimeMs else ts
        injectMotion(MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), dt, ts)
        downTimeMs = 0L
    }

    override fun destroy() {
        downTimeMs = 0L
        exitProcess(0)
    }

    private fun injectMotion(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long
    ) {
        val im = inputManager ?: run {
            initReflection()
            inputManager
        } ?: return
        val method = injectMethod ?: return

        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                size = 0.08f
            }
        )

        var event: MotionEvent? = null
        try {
            event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                props,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            // INJECT_INPUT_EVENT_MODE_ASYNC = 0
            method.invoke(im, event, 0)
        } finally {
            event?.recycle()
        }
    }
}
