package com.a17.assist.ffvn.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.a17.assist.ffvn.engine.EngineTelemetry
import com.a17.assist.ffvn.inject.InjectorStatus
import com.example.R

/**
 * Pass-through HUD overlay:
 * MUST include FLAG_NOT_TOUCHABLE so the HUD area never intercepts game touches.
 */
class HudOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var hudView: View? = null
    private var tvStatus: TextView? = null
    private var dotView: View? = null

    @SuppressLint("InflateParams")
    fun update(
        enabled: Boolean,
        gameForeground: Boolean,
        telemetry: EngineTelemetry,
        injectorStatus: InjectorStatus
    ) {
        if (!enabled || !gameForeground) {
            hide()
            return
        }

        if (hudView == null) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_hud, null)
            tvStatus = view.findViewById(R.id.tv_hud_status)
            dotView = view.findViewById(R.id.view_hud_dot)

            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (14f * density).toInt()
            }

            hudView = view
            runCatching { windowManager.addView(view, params) }
        }

        val stateLabel = when {
            telemetry.isPausedByPanel -> "PAUSE"
            telemetry.isPulling -> "PULL"
            telemetry.isAwake -> "ON"
            else -> "SLEEP"
        }
        tvStatus?.text = "A17 • $stateLabel • ${telemetry.activeDistance.name} • ${telemetry.activeZone.name}"
        dotView?.setBackgroundResource(
            when (injectorStatus) {
                InjectorStatus.SHIZUKU_OK -> R.drawable.bg_bubble_green
                InjectorStatus.A11Y_FALLBACK -> R.drawable.bg_bubble_yellow
                InjectorStatus.NO_PERMISSION -> R.drawable.bg_bubble_red
            }
        )
    }

    fun hide() {
        val view = hudView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        hudView = null
        tvStatus = null
        dotView = null
    }
}
