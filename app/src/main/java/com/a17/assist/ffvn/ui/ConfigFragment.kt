package com.a17.assist.ffvn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.data.ConfigSchemaValidator
import com.example.databinding.FragmentConfigBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!

    private lateinit var configRepo: ConfigRepository
    private lateinit var controlsBinder: ConfigControlsBinder
    private var suppressCal = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configRepo = ConfigRepository.getInstance(requireContext())
        controlsBinder = ConfigControlsBinder(binding.configControls, configRepo)

        binding.sliderFireXRatio.addOnChangeListener { _, value, fromUser ->
            if (suppressCal || !fromUser) return@addOnChangeListener
            val pct = value.toInt()
            binding.tvFireXRatio.text = "$pct%"
            configRepo.update {
                it.copy(calibration = it.calibration.copy(fireButtonXRatio = pct / 100f))
            }
        }

        binding.sliderFireYRatio.addOnChangeListener { _, value, fromUser ->
            if (suppressCal || !fromUser) return@addOnChangeListener
            val pct = value.toInt()
            binding.tvFireYRatio.text = "$pct%"
            configRepo.update {
                it.copy(calibration = it.calibration.copy(fireButtonYRatio = pct / 100f))
            }
        }

        binding.btnExportJson.setOnClickListener { showExportDialog() }
        binding.btnImportJson.setOnClickListener { showImportDialog() }
        binding.btnResetJson.setOnClickListener {
            configRepo.resetToDefault()
            Toast.makeText(requireContext(), "Đã khôi phục cấu hình mặc định.", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                configRepo.configFlow.collectLatest { cfg ->
                    controlsBinder.render(cfg)
                    suppressCal = true
                    val xPct = (cfg.calibration.fireButtonXRatio * 100f).toInt().coerceIn(50, 95)
                    val yPct = (cfg.calibration.fireButtonYRatio * 100f).toInt().coerceIn(40, 92)
                    binding.sliderFireXRatio.value = xPct.toFloat()
                    binding.tvFireXRatio.text = "$xPct%"
                    binding.sliderFireYRatio.value = yPct.toFloat()
                    binding.tvFireYRatio.text = "$yPct%"
                    suppressCal = false
                }
            }
        }
    }

    private fun showExportDialog() {
        val exported = configRepo.exportJson()
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("a17_config_json", exported))
        Toast.makeText(requireContext(), "Đã sao chép JSON vào Clipboard.", Toast.LENGTH_SHORT).show()

        val preview = EditText(requireContext()).apply {
            setText(exported)
            isSingleLine = false
            textSize = 11f
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Xuất cấu hình JSON")
            .setView(preview)
            .setPositiveButton("Đóng", null)
            .show()
    }

    private fun showImportDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Dán nội dung config.json tại đây..."
            isSingleLine = false
            minLines = 6
            textSize = 12f
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Nhập cấu hình JSON")
            .setView(input)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Nạp") { _, _ ->
                val raw = input.text?.toString().orEmpty()
                when (val res = configRepo.importJson(raw)) {
                    is ConfigSchemaValidator.ValidationResult.Valid -> {
                        val msg = if (res.warnings.isNotEmpty()) {
                            res.warnings.joinToString("\n")
                        } else {
                            "Đã nạp cấu hình JSON thành công!"
                        }
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                    is ConfigSchemaValidator.ValidationResult.Invalid -> {
                        Toast.makeText(requireContext(), res.errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
