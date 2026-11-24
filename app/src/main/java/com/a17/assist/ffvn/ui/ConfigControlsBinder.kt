package com.a17.assist.ffvn.ui

import com.a17.assist.ffvn.data.AppConfig
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.data.DistanceMode
import com.example.R
import com.example.databinding.IncludeConfigControlsBinding
import java.util.Locale

class ConfigControlsBinder(
    private val binding: IncludeConfigControlsBinding,
    private val repo: ConfigRepository
) {
    private var suppress = false

    init {
        setupListeners()
        render(repo.current())
    }

    fun render(config: AppConfig) {
        suppress = true
        try {
            val caps = config.safetyCaps
            val assist = config.assistStrength
            val ctrl = config.controlsConfig
            val beh = config.behaviorSimulation
            val dec = config.decisionLogic
            val feed = config.fingerFeedback
            val stealth = config.antiCheatStealth

            when (runCatching { DistanceMode.valueOf(dec.activeDistanceMode.uppercase()) }.getOrDefault(DistanceMode.NEAR)) {
                DistanceMode.NEAR -> binding.toggleDistanceMode.check(R.id.btn_dist_near)
                DistanceMode.MID -> binding.toggleDistanceMode.check(R.id.btn_dist_mid)
                DistanceMode.FAR -> binding.toggleDistanceMode.check(R.id.btn_dist_far)
            }

            binding.sliderLockStrength.apply {
                valueFrom = caps.minLockStrength.toFloat()
                valueTo = caps.maxLockStrength.toFloat()
                value = assist.lockStrength.toFloat().coerceIn(valueFrom, valueTo)
            }
            binding.tvLockStrength.text = "${assist.lockStrength}%"

            binding.sliderAimLightness.apply {
                valueFrom = caps.minAimLightness.toFloat()
                valueTo = caps.maxAimLightness.toFloat()
                value = assist.aimLightness.toFloat().coerceIn(valueFrom, valueTo)
            }
            binding.tvAimLightness.text = "${assist.aimLightness}%"

            binding.sliderPullMultiplier.apply {
                valueFrom = caps.minPullMultiplier
                valueTo = caps.maxPullMultiplier
                value = snapStep(assist.pullMultiplier.coerceIn(valueFrom, valueTo), 0.05f)
            }
            binding.tvPullMultiplier.text = String.format(Locale.US, "%.2fx", assist.pullMultiplier)

            binding.sliderGeneralSens.value = ctrl.generalSens.toFloat().coerceIn(0f, 200f)
            binding.tvGeneralSens.text = "${ctrl.generalSens}"

            binding.sliderFireSens.value = ctrl.fireSens.toFloat().coerceIn(0f, 200f)
            binding.tvFireSens.text = "${ctrl.fireSens}"

            binding.sliderScopeSens.value = ctrl.scopeSens.toFloat().coerceIn(0f, 200f)
            binding.tvScopeSens.text = "${ctrl.scopeSens}"

            binding.sliderHeadAnchorPx.apply {
                valueFrom = caps.minHeadAnchorPx
                valueTo = caps.maxHeadAnchorPx
                value = snapStep(assist.headAnchorPx.coerceIn(valueFrom, valueTo), 0.5f)
            }
            binding.tvHeadAnchorPx.text = String.format(Locale.US, "%.1f px", assist.headAnchorPx)

            binding.sliderMaxTotalPullPx.apply {
                valueFrom = caps.minTotalPullPx
                valueTo = caps.maxTotalPullPx
                value = snapStep(assist.maxTotalPullPx.coerceIn(valueFrom, valueTo), 0.5f)
            }
            binding.tvMaxTotalPullPx.text = String.format(Locale.US, "%.1f px", assist.maxTotalPullPx)

            binding.sliderMaxStepPx.apply {
                valueFrom = caps.minStepPx
                valueTo = caps.maxStepPx
                value = snapStep(assist.maxStepPx.coerceIn(valueFrom, valueTo), 0.25f)
            }
            binding.tvMaxStepPx.text = String.format(Locale.US, "%.2f px", assist.maxStepPx)

            binding.sliderRampMs.apply {
                valueFrom = caps.minRampMs.toFloat()
                valueTo = caps.maxRampMs.toFloat()
                value = snapStep(assist.rampMs.toFloat().coerceIn(valueFrom, valueTo), 5f)
            }
            binding.tvRampMs.text = "${assist.rampMs} ms"

            binding.sliderCreepPxS.apply {
                valueFrom = caps.minCreepPxS
                valueTo = caps.maxCreepPxS
                value = snapStep(beh.creepPxS.coerceIn(valueFrom, valueTo), 0.05f)
            }
            binding.tvCreepPxS.text = String.format(Locale.US, "%.2f px/s", beh.creepPxS)

            binding.sliderReleaseReturnPct.apply {
                valueFrom = caps.minReleaseReturnPct
                valueTo = caps.maxReleaseReturnPct
                value = snapStep(beh.releaseReturnPct.coerceIn(valueFrom, valueTo), 0.05f)
            }
            binding.tvReleaseReturnPct.text = "${(beh.releaseReturnPct * 100f).toInt()}%"

            binding.sliderReactionMs.apply {
                valueFrom = caps.minReactionMs.toFloat()
                valueTo = caps.maxReactionMs.toFloat()
                val minV = snapStep(beh.reactionMinMs.toFloat().coerceIn(valueFrom, valueTo), 5f)
                val maxV = snapStep(beh.reactionMaxMs.toFloat().coerceIn(minV, valueTo), 5f)
                values = listOf(minV, maxV)
            }
            binding.tvReactionMs.text = "${beh.reactionMinMs}–${beh.reactionMaxMs} ms"

            binding.sliderJitter.apply {
                valueFrom = caps.minJitter
                valueTo = caps.maxJitter
                value = snapStep(beh.jitter.coerceIn(valueFrom, valueTo), 0.01f)
            }
            binding.tvJitter.text = String.format(Locale.US, "%.2f", beh.jitter)

            binding.swHeadLock.isChecked = dec.headLock
            binding.swNeckFallback.isChecked = dec.neckFallback
            binding.swThermalGuard.isChecked = stealth.thermalGuard
            binding.swRamGuard.isChecked = stealth.ramGuard
            binding.swHaptic.isChecked = feed.haptic
            binding.swHud.isChecked = feed.hud
            binding.swCreep.isChecked = beh.creep
            binding.swStutterCompensation.isChecked = beh.stutterCompensation
            binding.swOvershoot.isChecked = beh.overshoot
            binding.swBurstGovernor.isChecked = stealth.burstGovernor
            binding.swTapModeFallback.isChecked = stealth.tapModeFallback
            binding.swIgnoreProne.isChecked = dec.ignoreProne
        } finally {
            suppress = false
        }
    }

    private fun setupListeners() {
        binding.toggleDistanceMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppress || !isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btn_dist_mid -> "MID"
                R.id.btn_dist_far -> "FAR"
                else -> "NEAR"
            }
            repo.update { it.copy(decisionLogic = it.decisionLogic.copy(activeDistanceMode = mode)) }
        }

        binding.sliderLockStrength.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val iv = v.toInt()
            binding.tvLockStrength.text = "$iv%"
            repo.update { it.copy(assistStrength = it.assistStrength.copy(lockStrength = iv)) }
        }

        binding.sliderAimLightness.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val iv = v.toInt()
            binding.tvAimLightness.text = "$iv%"
            repo.update { it.copy(assistStrength = it.assistStrength.copy(aimLightness = iv)) }
        }

        binding.sliderPullMultiplier.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvPullMultiplier.text = String.format(Locale.US, "%.2fx", v)
            repo.update { it.copy(assistStrength = it.assistStrength.copy(pullMultiplier = v)) }
        }

        binding.sliderGeneralSens.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val iv = v.toInt()
            binding.tvGeneralSens.text = "$iv"
            repo.update { it.copy(controlsConfig = it.controlsConfig.copy(generalSens = iv)) }
        }

        binding.sliderFireSens.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val iv = v.toInt()
            binding.tvFireSens.text = "$iv"
            repo.update { it.copy(controlsConfig = it.controlsConfig.copy(fireSens = iv)) }
        }

        binding.sliderScopeSens.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val iv = v.toInt()
            binding.tvScopeSens.text = "$iv"
            repo.update { it.copy(controlsConfig = it.controlsConfig.copy(scopeSens = iv)) }
        }

        binding.sliderHeadAnchorPx.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvHeadAnchorPx.text = String.format(Locale.US, "%.1f px", v)
            repo.update { it.copy(assistStrength = it.assistStrength.copy(headAnchorPx = v)) }
        }

        binding.sliderMaxTotalPullPx.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvMaxTotalPullPx.text = String.format(Locale.US, "%.1f px", v)
            repo.update { it.copy(assistStrength = it.assistStrength.copy(maxTotalPullPx = v)) }
        }

        binding.sliderMaxStepPx.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvMaxStepPx.text = String.format(Locale.US, "%.2f px", v)
            repo.update { it.copy(assistStrength = it.assistStrength.copy(maxStepPx = v)) }
        }

        binding.sliderRampMs.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val iv = v.toInt()
            binding.tvRampMs.text = "$iv ms"
            repo.update { it.copy(assistStrength = it.assistStrength.copy(rampMs = iv)) }
        }

        binding.sliderCreepPxS.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvCreepPxS.text = String.format(Locale.US, "%.2f px/s", v)
            repo.update { it.copy(behaviorSimulation = it.behaviorSimulation.copy(creepPxS = v)) }
        }

        binding.sliderReleaseReturnPct.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvReleaseReturnPct.text = "${(v * 100f).toInt()}%"
            repo.update { it.copy(behaviorSimulation = it.behaviorSimulation.copy(releaseReturnPct = v)) }
        }

        binding.sliderReactionMs.addOnChangeListener { slider, _, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            val vals = slider.values
            if (vals.size >= 2) {
                val minR = vals[0].toInt()
                val maxR = vals[1].toInt()
                binding.tvReactionMs.text = "$minR–$maxR ms"
                repo.update {
                    it.copy(
                        behaviorSimulation = it.behaviorSimulation.copy(
                            reactionMinMs = minR,
                            reactionMaxMs = maxR
                        )
                    )
                }
            }
        }

        binding.sliderJitter.addOnChangeListener { _, v, fromUser ->
            if (suppress || !fromUser) return@addOnChangeListener
            binding.tvJitter.text = String.format(Locale.US, "%.2f", v)
            repo.update { it.copy(behaviorSimulation = it.behaviorSimulation.copy(jitter = v)) }
        }

        binding.swHeadLock.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(decisionLogic = it.decisionLogic.copy(headLock = checked)) }
        }
        binding.swNeckFallback.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(decisionLogic = it.decisionLogic.copy(neckFallback = checked)) }
        }
        binding.swThermalGuard.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(antiCheatStealth = it.antiCheatStealth.copy(thermalGuard = checked)) }
        }
        binding.swRamGuard.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(antiCheatStealth = it.antiCheatStealth.copy(ramGuard = checked)) }
        }
        binding.swHaptic.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(fingerFeedback = it.fingerFeedback.copy(haptic = checked)) }
        }
        binding.swHud.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(fingerFeedback = it.fingerFeedback.copy(hud = checked)) }
        }
        binding.swCreep.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(behaviorSimulation = it.behaviorSimulation.copy(creep = checked)) }
        }
        binding.swStutterCompensation.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(behaviorSimulation = it.behaviorSimulation.copy(stutterCompensation = checked)) }
        }
        binding.swOvershoot.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(behaviorSimulation = it.behaviorSimulation.copy(overshoot = checked)) }
        }
        binding.swBurstGovernor.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(antiCheatStealth = it.antiCheatStealth.copy(burstGovernor = checked)) }
        }
        binding.swTapModeFallback.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(antiCheatStealth = it.antiCheatStealth.copy(tapModeFallback = checked)) }
        }
        binding.swIgnoreProne.setOnCheckedChangeListener { _, checked ->
            if (!suppress) repo.update { it.copy(decisionLogic = it.decisionLogic.copy(ignoreProne = checked)) }
        }
    }

    private fun snapStep(value: Float, step: Float): Float {
        if (step <= 0f) return value
        val steps = kotlin.math.round(value / step)
        return steps * step
    }
}
