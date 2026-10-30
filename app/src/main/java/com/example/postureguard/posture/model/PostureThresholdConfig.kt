package com.example.postureguard.posture.model

import kotlin.math.max

/**
 * All posture thresholds live here. The defaults are engineering starting values only;
 * they are not medical standards and must be tuned with real recordings.
 */
data class PostureThresholdConfig(
    // Forward-head deltas are in ear-to-shoulder length units (1.0 ≈ 90° neck lean).
    val forwardHeadMild: Double = 0.15,
    val forwardHeadSevere: Double = 0.33,
    val headDownMildDeg: Double = 10.0,
    val headDownSevereDeg: Double = 22.0,
    val torsoLeanMildDeg: Double = 8.0,
    val torsoLeanSevereDeg: Double = 18.0,
    val mildHoldDurationMs: Long = 5_000,
    val severeHoldDurationMs: Long = 3_000,
    val recoveryHoldDurationMs: Long = 4_000,
    val alertRepeatIntervalMs: Long = 10_000,
    val lowConfidenceTimeoutMs: Long = 1_200,
    val minTorsoScale: Double = 0.02,
    val maxTorsoScale: Double = 1.5,
    val minLandmarkConfidence: Double = 0.5,
    val maxFrameGapMs: Long = 600,
    val emaAlpha: Double = 0.3,
    val emaReferenceIntervalMs: Long = 100,
) {
    companion object {
        /** Baseline MAD inflation: noisy calibration must not make detection over-sensitive. */
        const val MAD_MULTIPLIER = 3.0
        const val FORWARD_FLOOR = 0.06
        const val ANGLE_FLOOR_DEG = 4.0
    }
}

enum class SensitivityPreset(val displayName: String, val scale: Double) {
    RELAXED("宽松", 1.30),
    BALANCED("均衡", 1.0),
    SENSITIVE("灵敏", 0.78),
}

/** Thresholds after applying the sensitivity preset and the personal baseline noise. */
data class EffectiveThresholds(
    val forwardHeadMild: Double,
    val forwardHeadSevere: Double,
    val headDownMildDeg: Double,
    val headDownSevereDeg: Double,
    val torsoLeanMildDeg: Double,
    val torsoLeanSevereDeg: Double,
) {
    companion object {
        fun from(
            config: PostureThresholdConfig,
            preset: SensitivityPreset,
            baseline: CalibrationProfile?,
        ): EffectiveThresholds {
            fun scaled(value: Double): Double = value * preset.scale
            val upperBodyOnly = baseline != null && !baseline.torsoMeasured
            val faceUnavailable = baseline != null && !baseline.headDownMeasured
            val forwardNoise = baseline?.let { it.forwardOffsetMad * PostureThresholdConfig.MAD_MULTIPLIER + PostureThresholdConfig.FORWARD_FLOOR } ?: 0.0
            val headNoise = baseline
                ?.takeUnless { faceUnavailable }
                ?.let { it.headAngleMadDeg * PostureThresholdConfig.MAD_MULTIPLIER + PostureThresholdConfig.ANGLE_FLOOR_DEG }
                ?: 0.0
            val torsoNoise = baseline?.takeUnless { upperBodyOnly }?.let { it.torsoAngleMadDeg * PostureThresholdConfig.MAD_MULTIPLIER + PostureThresholdConfig.ANGLE_FLOOR_DEG } ?: 0.0
            val mildForward = max(scaled(config.forwardHeadMild), forwardNoise)
            val headMild = if (faceUnavailable) {
                Double.POSITIVE_INFINITY
            } else {
                max(scaled(config.headDownMildDeg), headNoise)
            }
            val torsoMild = if (upperBodyOnly) {
                Double.POSITIVE_INFINITY
            } else {
                max(scaled(config.torsoLeanMildDeg), torsoNoise)
            }
            return EffectiveThresholds(
                forwardHeadMild = mildForward,
                forwardHeadSevere = max(scaled(config.forwardHeadSevere), mildForward * 1.8),
                headDownMildDeg = headMild,
                headDownSevereDeg = if (faceUnavailable) {
                    Double.POSITIVE_INFINITY
                } else {
                    max(scaled(config.headDownSevereDeg), headMild * 1.8)
                },
                torsoLeanMildDeg = torsoMild,
                torsoLeanSevereDeg = if (upperBodyOnly) {
                    Double.POSITIVE_INFINITY
                } else {
                    max(scaled(config.torsoLeanSevereDeg), torsoMild * 1.8)
                },
            )
        }
    }

    fun levelForward(delta: Double): ConditionSeverity = when {
        delta >= forwardHeadSevere -> ConditionSeverity.SEVERE
        delta >= forwardHeadMild -> ConditionSeverity.MILD
        else -> ConditionSeverity.NONE
    }

    fun levelHeadDown(delta: Double): ConditionSeverity = when {
        delta >= headDownSevereDeg -> ConditionSeverity.SEVERE
        delta >= headDownMildDeg -> ConditionSeverity.MILD
        else -> ConditionSeverity.NONE
    }

    fun levelTorso(delta: Double): ConditionSeverity = when {
        delta >= torsoLeanSevereDeg -> ConditionSeverity.SEVERE
        delta >= torsoLeanMildDeg -> ConditionSeverity.MILD
        else -> ConditionSeverity.NONE
    }
}
