package com.example.postureguard.posture.model

import java.util.UUID

/**
 * Personal reference captured during calibration. It is not a medical "correct posture"
 * certificate; it only reflects the user's own comfortable sitting posture.
 */
data class CalibrationProfile(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long,
    val facingDirection: FacingDirection,
    val selectedBodySide: BodySide,
    val forwardOffsetMedian: Double,
    val headAngleMedianDeg: Double,
    val torsoAngleMedianDeg: Double,
    val torsoScaleMedian: Double,
    val forwardOffsetMad: Double,
    val headAngleMadDeg: Double,
    val torsoAngleMadDeg: Double,
    val qualityMedian: Double,
    val validSampleRatio: Double,
    val modelVersion: String,
    val isActive: Boolean = true,
) {
    companion object {
        /**
         * Sentinel stored in a MAD field when calibration never measured that dimension.
         * Such a profile falls back to the dimensions it did measure.
         */
        const val UNMEASURED_MAD = -1.0

        /**
         * Bumped whenever the geometry behind the stored medians changes meaning.
         * Profiles tagged with an older version are treated as absent so the user
         * re-calibrates instead of being judged against a stale baseline.
         */
        const val METRICS_VERSION = 3
        private const val VERSION_PREFIX = "metrics-v$METRICS_VERSION|"

        fun stamp(modelVersion: String): String = VERSION_PREFIX + modelVersion

        fun isCurrent(modelVersion: String): Boolean = modelVersion.startsWith(VERSION_PREFIX)
    }

    val torsoMeasured: Boolean
        get() = torsoAngleMadDeg >= 0.0

    val headDownMeasured: Boolean
        get() = headAngleMadDeg >= 0.0
}

data class ThresholdProposal(
    val forwardHeadMild: Double,
    val forwardHeadSevere: Double,
    val headDownMildDeg: Double,
    val headDownSevereDeg: Double,
    val torsoLeanMildDeg: Double,
    val torsoLeanSevereDeg: Double,
)
