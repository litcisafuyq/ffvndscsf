package com.example

import com.a17.assist.ffvn.data.AimZone
import com.a17.assist.ffvn.data.AppConfig
import com.a17.assist.ffvn.data.ConfigSchemaValidator
import com.a17.assist.ffvn.data.DecisionLogic
import com.a17.assist.ffvn.engine.CurveGen
import com.a17.assist.ffvn.engine.SafetyGuard
import com.a17.assist.ffvn.engine.TimingLearner
import com.a17.assist.ffvn.engine.ZoneSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A17EngineTest {

    @Test
    fun `curve weights normalize to 1 and respect max 16 steps`() {
        val weights = CurveGen.generateStepWeights(rampMs = 150, friction = 0.3f)
        assertTrue(weights.size in 1..16)
        assertEquals(1.0f, weights.sum(), 0.001f)
    }

    @Test
    fun `safety guard clamps out-of-range values`() {
        val unsafe = AppConfig(
            assistStrength = AppConfig().assistStrength.copy(
                lockStrength = 100,
                maxTotalPullPx = 99f,
                maxStepPx = 15f
            ),
            controlsConfig = AppConfig().controlsConfig.copy(
                generalSens = 250
            )
        )
        val safe = SafetyGuard.enforceConfigCaps(unsafe)
        assertEquals(85, safe.assistStrength.lockStrength)
        assertEquals(24f, safe.assistStrength.maxTotalPullPx, 0.001f)
        assertEquals(3f, safe.assistStrength.maxStepPx, 0.001f)
        assertEquals(200, safe.controlsConfig.generalSens)
    }

    @Test
    fun `zone selector soft locks neck after 2 misses`() {
        val selector = ZoneSelector()
        val logic = DecisionLogic()
        assertEquals(AimZone.HEAD, selector.selectZone(1000L, logic))
        selector.recordShotOutcome(hitHead = false)
        selector.recordShotOutcome(hitHead = false)
        assertEquals(AimZone.NECK, selector.selectZone(1200L, logic))
    }

    @Test
    fun `timing learner caps compensation at 50ms and resets on weapon change`() {
        val learner = TimingLearner()
        repeat(8) { learner.recordReactionDelta(300L, 100) }
        assertTrue(learner.getCompensationMs() <= 50L)
        learner.onWeaponChanged("MP40")
        assertEquals(0L, learner.getCompensationMs())
    }

    @Test
    fun `schema validator rejects missing root keys`() {
        val result = ConfigSchemaValidator.validateAndParse("""{"master": true}""")
        assertTrue(result is ConfigSchemaValidator.ValidationResult.Invalid)
    }
}
