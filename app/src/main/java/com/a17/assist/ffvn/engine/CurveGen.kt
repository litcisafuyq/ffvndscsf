package com.a17.assist.ffvn.engine

import kotlin.math.PI
import kotlin.math.cos

/**
 * Generates nested Bezier progression weights blending ease_out_quad and ease_in_sine
 * with friction = 0.3, normalized so sum == 1.0f.
 */
object CurveGen {

    fun easeOutQuad(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - (1f - c) * (1f - c)
    }

    fun easeInSine(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - cos((c * PI) / 2.0).toFloat()
    }

    fun nestedBlend(t: Float, friction: Float = 0.3f): Float {
        val f = friction.coerceIn(0f, 1f)
        return (1f - f) * easeOutQuad(t) + f * easeInSine(t)
    }

    fun generateStepWeights(rampMs: Int, friction: Float = 0.3f): FloatArray {
        val n = (rampMs / 17).coerceIn(1, 16)
        val raw = FloatArray(n)
        var sum = 0f
        var prev = 0f
        for (i in 0 until n) {
            val tNext = (i + 1).toFloat() / n.toFloat()
            val cur = nestedBlend(tNext, friction)
            val delta = (cur - prev).coerceAtLeast(0.0001f)
            raw[i] = delta
            sum += delta
            prev = cur
        }
        if (sum > 0f) {
            for (i in 0 until n) {
                raw[i] /= sum
            }
        }
        return raw
    }

    fun generateReturnWeights(steps: Int = 3): FloatArray {
        val n = steps.coerceAtLeast(1)
        val raw = FloatArray(n)
        var sum = 0f
        var prev = 0f
        for (i in 0 until n) {
            val tNext = (i + 1).toFloat() / n.toFloat()
            val cur = easeOutQuad(tNext)
            val delta = (cur - prev).coerceAtLeast(0.0001f)
            raw[i] = delta
            sum += delta
            prev = cur
        }
        if (sum > 0f) {
            for (i in 0 until n) {
                raw[i] /= sum
            }
        }
        return raw
    }
}
