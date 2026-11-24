package com.a17.assist.ffvn.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.a17.assist.ffvn.data.AppConfig
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.ui.ConfigControlsBinder
import com.example.R
import com.example.databinding.OverlayPanelBinding

/**
 * Slide-in right panel (300dp, 90% dark background):
 * - TYPE_APPLICATION_OVERLAY + FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS
 * - Only attached and touch-receiving while open
 * - Tapping outside panel (on scrim) closes immediately
 * - Opening pauses AimEngine; closing resumes
 * - Includes X button and "Tắt khẩn cấp" (Emergency Kill) button
 */
class OverlayPanelController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val configRepo: ConfigRepository,
    private val onPanelVisibilityChanged: (Boolean) -> Unit,
    private val onEmergencyKill: () -> Unit
) {
    private var rootView: View? = null
    private var binding: OverlayPanelBinding? = null
    private var controlsBinder: ConfigControlsBinder? = null
    private var suppressMaster = false

    val isOpen: Boolean
        get() = rootView != null

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun open() {
        if (rootView != null) return
        onPanelVisibilityChanged(true)

        val themedContext = ContextThemeWrapper(context, R.style.Theme_A17Assist)
        val panelBinding = OverlayPanelBinding.inflate(LayoutInflater.from(themedContext))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
        }

        // Tapping outside the 300dp drawer closes panel immediately
        panelBinding.panelScrim.setOnClickListener { close() }
        panelBinding.panelDrawer.setOnClickListener { /* Consume clicks inside drawer */ }
        panelBinding.btnPanelClose.setOnClickListener { close() }
        panelBinding.btnEmergencyKill.setOnClickListener {
            onEmergencyKill()
            close()
        }

        val cfg = configRepo.current()
        suppressMaster = true
        panelBinding.swPanelMaster.isChecked = cfg.master
        suppressMaster = false
        panelBinding.swPanelMaster.setOnCheckedChangeListener { _, checked ->
            if (!suppressMaster) {
                configRepo.setMaster(checked)
            }
        }

        val binder = ConfigControlsBinder(panelBinding.panelControls, configRepo)
        controlsBinder = binder
        binding = panelBinding
        rootView = panelBinding.root

        val drawerWidthPx = 300f * context.resources.displayMetrics.density
        panelBinding.panelDrawer.translationX = drawerWidthPx

        runCatching {
            windowManager.addView(panelBinding.root, params)
            panelBinding.panelDrawer.animate()
                .translationX(0f)
                .setDuration(180L)
                .start()
        }
    }

    fun renderConfig(config: AppConfig) {
        val b = binding ?: return
        suppressMaster = true
        b.swPanelMaster.isChecked = config.master
        suppressMaster = false
        controlsBinder?.render(config)
    }

    fun close() {
        val view = rootView ?: return
        rootView = null
        binding = null
        controlsBinder = null
        runCatching { windowManager.removeViewImmediate(view) }
        onPanelVisibilityChanged(false)
    }
}
