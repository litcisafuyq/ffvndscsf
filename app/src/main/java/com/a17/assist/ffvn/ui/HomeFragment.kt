package com.a17.assist.ffvn.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.data.DeviceCalibration
import com.a17.assist.ffvn.detect.GameWatcher
import com.a17.assist.ffvn.inject.A17A11yService
import com.a17.assist.ffvn.inject.InjectorStatus
import com.a17.assist.ffvn.overlay.A17RuntimeBus
import com.a17.assist.ffvn.overlay.OverlayService
import com.example.MainActivity
import com.example.R
import com.example.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var configRepo: ConfigRepository
    private var suppressMaster = false
    private var weaponToggleIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configRepo = ConfigRepository.getInstance(requireContext())

        binding.btnLaunchFreeFire.setOnClickListener {
            val ctx = requireContext()
            if (!Settings.canDrawOverlays(ctx)) {
                Toast.makeText(
                    ctx,
                    "Vui lòng cấp quyền Hiển thị trên ứng dụng khác ở tab Thiết lập.",
                    Toast.LENGTH_LONG
                ).show()
                (activity as? MainActivity)?.navigateToTab(1)
                return@setOnClickListener
            }
            OverlayService.start(ctx, gameLaunched = true)
            val launched = GameWatcher.launchFreeFireOrStore(ctx)
            if (!launched) {
                Toast.makeText(
                    ctx,
                    "Chưa cài Free Fire, đang mở Play Store…",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnToggleOverlay.setOnClickListener {
            val ctx = requireContext()
            if (A17RuntimeBus.overlayRunning.value) {
                OverlayService.stop(ctx)
            } else {
                if (!Settings.canDrawOverlays(ctx)) {
                    Toast.makeText(
                        ctx,
                        "Cần cấp quyền Overlay trước khi bật bong bóng.",
                        Toast.LENGTH_SHORT
                    ).show()
                    (activity as? MainActivity)?.navigateToTab(1)
                } else {
                    OverlayService.start(ctx)
                }
            }
        }

        binding.btnHomeEmergencyKill.setOnClickListener {
            A17RuntimeBus.activeService?.emergencyKill() ?: configRepo.setMaster(false)
            Toast.makeText(requireContext(), "Đã tắt khẩn cấp toàn bộ inject.", Toast.LENGTH_SHORT).show()
        }

        binding.swHomeMaster.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressMaster) {
                configRepo.setMaster(isChecked)
            }
        }

        binding.btnWeaponReset.setOnClickListener {
            weaponToggleIndex++
            val tag = "WEAPON_$weaponToggleIndex"
            A17RuntimeBus.activeService?.resetWeaponTiming(tag)
            Toast.makeText(requireContext(), "Đã reset TimingLearner ($tag).", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestFireGate.setOnTouchListener { _, event ->
            val svc = A17RuntimeBus.activeService
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (svc == null) {
                        Toast.makeText(
                            requireContext(),
                            "Hãy bật Overlay trước để thử fire_gate.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        svc.triggerTestGate(true)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    svc?.triggerTestGate(false)
                    true
                }
                else -> false
            }
        }

        observeState()
        refreshStaticInfo()
    }

    override fun onResume() {
        super.onResume()
        refreshStaticInfo()
    }

    private fun refreshStaticInfo() {
        val ctx = requireContext()
        val cfg = configRepo.current()
        val metrics = DeviceCalibration.computeMetrics(ctx, cfg.calibration)
        binding.tvHomeDeviceScale.text = String.format(
            Locale.US,
            "Màn hình: %dx%d @ %ddpi | deviceScale = %.4f",
            metrics.screenWidth,
            metrics.screenHeight,
            metrics.densityDpi,
            metrics.deviceScale
        )

        val installed = GameWatcher.resolveInstalledPackage(ctx)
        binding.tvHomeGameInstalled.text = if (installed != null) {
            "Đã phát hiện: $installed"
        } else {
            "Chưa cài Free Fire (sẽ mở Play Store khi bấm Vào Free Fire)"
        }

        if (!A17RuntimeBus.overlayRunning.value) {
            val status = computeLocalStatus()
            renderInjectorStatus(status)
        }
    }

    private fun computeLocalStatus(): InjectorStatus {
        val shizukuOk = runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val a11yOk = A17A11yService.instance != null
        return when {
            shizukuOk -> InjectorStatus.SHIZUKU_OK
            a11yOk -> InjectorStatus.A11Y_FALLBACK
            else -> InjectorStatus.NO_PERMISSION
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    configRepo.configFlow.collectLatest { cfg ->
                        suppressMaster = true
                        binding.swHomeMaster.isChecked = cfg.master
                        suppressMaster = false
                    }
                }
                launch {
                    A17RuntimeBus.overlayRunning.collectLatest { running ->
                        binding.btnToggleOverlay.text = getString(
                            if (running) R.string.btn_stop_overlay else R.string.btn_start_overlay
                        )
                    }
                }
                launch {
                    A17RuntimeBus.injectorStatus.collectLatest { status ->
                        if (A17RuntimeBus.overlayRunning.value) {
                            renderInjectorStatus(status)
                        }
                    }
                }
                launch {
                    A17RuntimeBus.telemetry.collectLatest { t ->
                        val state = when {
                            t.isPausedByPanel -> "PAUSE (Panel mở)"
                            t.isPulling -> "INJECTING"
                            t.isAwake -> "ACTIVE"
                            else -> "SLEEP"
                        }
                        binding.tvHomeEngineTelemetry.text = String.format(
                            Locale.US,
                            "Engine: %s | %s/%s | %d evt/s | Thermal %.2fx%s",
                            state,
                            t.activeDistance.name,
                            t.activeZone.name,
                            t.eventsPerSec,
                            t.thermalFactor,
                            if (t.ramConstrained) " | RAM LOW" else ""
                        )
                    }
                }
            }
        }
    }

    private fun renderInjectorStatus(status: InjectorStatus) {
        when (status) {
            InjectorStatus.SHIZUKU_OK -> {
                binding.viewHomeStatusDot.setBackgroundResource(R.drawable.bg_bubble_green)
                binding.tvHomeInjectorStatus.text = getString(R.string.status_shizuku_ok)
            }
            InjectorStatus.A11Y_FALLBACK -> {
                binding.viewHomeStatusDot.setBackgroundResource(R.drawable.bg_bubble_yellow)
                binding.tvHomeInjectorStatus.text = getString(R.string.status_a11y_fallback)
            }
            InjectorStatus.NO_PERMISSION -> {
                binding.viewHomeStatusDot.setBackgroundResource(R.drawable.bg_bubble_red)
                binding.tvHomeInjectorStatus.text = getString(R.string.status_no_permission)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
