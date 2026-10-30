package com.example.postureguard.posture.filters

import kotlin.math.pow

/** Frame-rate independent exponential moving average. */
class EmaFilter(
    private val alpha: Double,
    private val referenceIntervalMs: Long = 100,
) {
    private var value: Double? = null

    val current: Double? get() = value

    fun reset() {
        value = null
    }

    fun update(sample: Double, nowMs: Long, previousTimestampMs: Long?): Double {
        if (!sample.isFinite()) return value ?: sample
        val previous = value
        if (previous == null) {
            value = sample
            return sample
        }
        val dt = if (previousTimestampMs == null) referenceIntervalMs else (nowMs - previousTimestampMs).coerceIn(1L, 1_000L)
        val effectiveAlpha = 1.0 - (1.0 - alpha).pow(dt.toDouble() / referenceIntervalMs)
        val next = effectiveAlpha * sample + (1.0 - effectiveAlpha) * previous
        value = next
        return next
    }
}

/** EMA that follows the shortest path around the ±180° wrap. */
class AngleEmaFilter(
    private val alpha: Double,
    private val referenceIntervalMs: Long = 100,
) {
    private var value: Double? = null

    val current: Double? get() = value

    fun reset() {
        value = null
    }

    fun update(sampleDeg: Double, nowMs: Long, previousTimestampMs: Long?): Double {
        if (!sampleDeg.isFinite()) return value ?: sampleDeg
        val previous = value
        if (previous == null) {
            value = sampleDeg
            return sampleDeg
        }
        val dt = if (previousTimestampMs == null) referenceIntervalMs else (nowMs - previousTimestampMs).coerceIn(1L, 1_000L)
        val effectiveAlpha = 1.0 - (1.0 - alpha).pow(dt.toDouble() / referenceIntervalMs)
        val delta = com.example.postureguard.core.math.Geometry.shortestAngleDeltaDegrees(sampleDeg, previous)
        val next = com.example.postureguard.core.math.Geometry.wrapDegrees(previous + effectiveAlpha * delta)
        value = next
        return next
    }
}
