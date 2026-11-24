package com.a17.assist.ffvn.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.inject.InjectorStatus
import com.example.R
import kotlin.math.abs

/**
 * 48dp draggable floating bubble:
 * - TYPE_APPLICATION_OVERLAY
 * - FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS
 * - Saves (x, y) to DataStore via ConfigRepository
 * - Color reflects status: Green = Shizuku OK, Yellow = A11y fallback, Red = No permission
 */
class OverlayBubbleController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val configRepo: ConfigRepository,
    private val onBubbleTapped: () -> Unit
) {
    private var bubbleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show(initialStatus: InjectorStatus) {
        if (bubbleView != null) {
            updateStatus(initialStatus)
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null)
        val density = context.resources.displayMetrics.density
        val sizePx = (48f * density).toInt()
        val cal = configRepo.current().calibration

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cal.bubbleX
            y = cal.bubbleY
        }

        var startX = 0
        var startY = 0
        var touchDownRawX = 0f
        var touchDownRawY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    isDragging = false
                    v.alpha = 0.96f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownRawX).toInt()
                    val dy = (event.rawY - touchDownRawY).toInt()
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.88f
                    if (isDragging) {
                        configRepo.saveBubblePosition(params.x, params.y)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        onBubbleTapped()
                    }
                    true
                }

                else -> false
            }
        }

        bubbleView = view
        layoutParams = params
        updateStatus(initialStatus)
        runCatching { windowManager.addView(view, params) }
    }

    fun updateStatus(status: InjectorStatus) {
        val view = bubbleView ?: return
        val bgRes = when (status) {
            InjectorStatus.SHIZUKU_OK -> R.drawable.bg_bubble_green
            InjectorStatus.A11Y_FALLBACK -> R.drawable.bg_bubble_yellow
            InjectorStatus.NO_PERMISSION -> R.drawable.bg_bubble_red
        }
        view.setBackgroundResource(bgRes)
    }

    fun hide() {
        val view = bubbleView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        bubbleView = null
        layoutParams = null
    }
}
