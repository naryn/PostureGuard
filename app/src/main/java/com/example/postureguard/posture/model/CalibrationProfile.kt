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
)

data class ThresholdProposal(
    val forwardHeadMild: Double,
    val forwardHeadSevere: Double,
    val headDownMildDeg: Double,
    val headDownSevereDeg: Double,
    val torsoLeanMildDeg: Double,
    val torsoLeanSevereDeg: Double,
)
