package com.a17.assist.ffvn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.a17.assist.ffvn.detect.GameWatcher
import com.a17.assist.ffvn.inject.A17A11yService
import com.a17.assist.ffvn.overlay.A17RuntimeBus
import com.example.R
import com.example.databinding.FragmentSetupBinding
import rikka.shizuku.Shizuku

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatuses() }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == REQ_SHIZUKU) {
                refreshStatuses()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            runCatching { startActivity(intent) }
                .onFailure {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
        }

        binding.btnGrantNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotifSettings()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Android dưới 13 đã tự động cấp quyền thông báo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnGrantUsage.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            runCatching { startActivity(intent) }
        }

        binding.btnGrantShizuku.setOnClickListener {
            requestShizukuPermission()
        }

        binding.btnGrantA11y.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            runCatching { startActivity(intent) }
        }

        binding.btnRecheckPermissions.setOnClickListener {
            refreshStatuses()
            Toast.makeText(requireContext(), "Đã kiểm tra lại toàn bộ quyền.", Toast.LENGTH_SHORT).show()
        }

        refreshStatuses()
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    override fun onDestroyView() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroyView()
        _binding = null
    }

    private fun openAppNotifSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        runCatching { startActivity(intent) }
    }

    private fun requestShizukuPermission() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            Toast.makeText(
                requireContext(),
                "Shizuku chưa chạy. Hãy khởi động app Shizuku trước, hoặc bật Trợ năng (A11y) làm dự phòng.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (granted) {
            Toast.makeText(requireContext(), "Shizuku đã được cấp quyền!", Toast.LENGTH_SHORT).show()
            refreshStatuses()
        } else {
            runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
        }
    }

    private fun refreshStatuses() {
        val ctx = context ?: return
        val overlayOk = Settings.canDrawOverlays(ctx)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (overlayOk && notifOk) {
            binding.tvStep1State.text = "ĐẠT"
            binding.tvStep1State.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
        } else if (overlayOk) {
            binding.tvStep1State.text = "THIẾU THÔNG BÁO"
            binding.tvStep1State.setTextColor(ContextCompat.getColor(ctx, R.color.status_yellow))
        } else {
            binding.tvStep1State.text = "CHƯA CẤP"
            binding.tvStep1State.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
        }

        val usageOk = GameWatcher.hasUsageStatsPermission(ctx)
        if (usageOk) {
            binding.tvStep2State.text = "ĐẠT"
            binding.tvStep2State.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
        } else {
            binding.tvStep2State.text = "CHƯA CẤP"
            binding.tvStep2State.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
        }

        val shizukuOk = runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val a11yOk = A17A11yService.instance != null

        when {
            shizukuOk -> {
                binding.tvStep3State.text = "SHIZUKU OK"
                binding.tvStep3State.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
            }
            a11yOk -> {
                binding.tvStep3State.text = "A11Y DỰ PHÒNG"
                binding.tvStep3State.setTextColor(ContextCompat.getColor(ctx, R.color.status_yellow))
            }
            else -> {
                binding.tvStep3State.text = "CHƯA CẤP"
                binding.tvStep3State.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
            }
        }

        A17RuntimeBus.activeService?.let {
            // Re-sync injector status if service already running
        }
    }

    companion object {
        private const val REQ_SHIZUKU = 1717
    }
}
