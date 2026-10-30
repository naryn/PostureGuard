package com.example.postureguard.posture.calibration

import com.example.postureguard.core.math.Geometry
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.EffectiveThresholds
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.PostureThresholdConfig
import com.example.postureguard.posture.model.RawMetrics
import com.example.postureguard.posture.model.SensitivityPreset
import com.example.postureguard.posture.model.ThresholdProposal

data class CalibrationConfig(
    val durationMs: Long = 15_000,
    val warmupMs: Long = 2_000,
    val minValidRatio: Double = 0.60,
    val minSamples: Int = 40,
    val maxForwardOffsetMad: Double = 0.18,
    val maxHeadAngleMadDeg: Double = 15.0,
    val maxTorsoAngleMadDeg: Double = 12.0,
)

data class CalibrationProgress(
    val elapsedMs: Long,
    val progress: Float,
    val validSamples: Int,
    val totalSamples: Int,
    val validRatio: Double,
    val inWarmup: Boolean,
)

enum class CalibrationFailureReason(val message: String) {
    NOT_ENOUGH_SAMPLES("有效样本不足，请保持头、肩和面部（眼睛、鼻子、耳朵、嘴）在画面内后重试"),
    UNSTABLE_POSTURE("校准期间姿势波动较大，请保持自然坐姿后重试"),
    SIDE_UNSTABLE("左右侧关键点反复切换，请调整手机角度"),
    TIMEOUT("校准时间不足，请重试"),
}

sealed interface CalibrationResult {
    data class Success(
        val profile: CalibrationProfile,
        val proposal: ThresholdProposal,
        val validRatio: Double,
    ) : CalibrationResult

    data class Failure(
        val reason: CalibrationFailureReason,
        val validRatio: Double,
    ) : CalibrationResult
}

/**
 * Collects valid, settled frames during a calibration window, then derives a robust
 * personal baseline with median / MAD. Outliers are trimmed once before the final median.
 */
class CalibrationAccumulator(
    private val facingDirection: FacingDirection,
    private val side: BodySide,
    private val modelVersion: String,
    private val config: CalibrationConfig = CalibrationConfig(),
    private val thresholdConfig: PostureThresholdConfig = PostureThresholdConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val startedAt: Long = clock()
    private var validSamples = 0
    private var totalSamples = 0
    private val forwardValues = mutableListOf<Double>()
    private val headValues = mutableListOf<Double>()
    private val torsoValues = mutableListOf<Double>()
    private val torsoScaleValues = mutableListOf<Double>()
    private val headScaleValues = mutableListOf<Double>()
    private val qualityValues = mutableListOf<Double>()

    val isFinished: Boolean
        get() = clock() - startedAt >= config.durationMs

    fun addSample(metrics: RawMetrics?, quality: LandmarkQuality, nowMs: Long = clock()): CalibrationProgress {
        val elapsed = (nowMs - startedAt).coerceAtLeast(0)
        val inWarmup = elapsed < config.warmupMs
        if (!isFinished && elapsed <= config.durationMs) {
            totalSamples++
            val sample = metrics
            if (!inWarmup &&
                sample != null &&
                quality.valid &&
                quality.selectedSide == side &&
                sample.forwardOffset.isFinite() &&
                sample.headAngleDeg.isFinite() &&
                sample.torsoScale > 0
            ) {
                validSamples++
                forwardValues += sample.forwardOffset
                if (sample.faceMeasured) headValues += sample.headAngleDeg
                if (sample.torsoMeasured) {
                    torsoValues += sample.torsoAngleDeg
                    torsoScaleValues += sample.torsoScale
                } else {
                    headScaleValues += sample.torsoScale
                }
                qualityValues += quality.score
            }
        }
        return progress(nowMs)
    }

    fun progress(nowMs: Long = clock()): CalibrationProgress {
        val elapsed = (nowMs - startedAt).coerceIn(0, config.durationMs)
        val samples = totalSamples.coerceAtLeast(1)
        return CalibrationProgress(
            elapsedMs = elapsed,
            progress = (elapsed.toFloat() / config.durationMs).coerceIn(0f, 1f),
            validSamples = validSamples,
            totalSamples = totalSamples,
            validRatio = validSamples.toDouble() / samples,
            inWarmup = elapsed < config.warmupMs,
        )
    }

    fun finish(nowMs: Long = clock()): CalibrationResult {
        val validRatio = if (totalSamples == 0) 0.0 else validSamples.toDouble() / totalSamples
        if (nowMs - startedAt < config.warmupMs + 2_000) {
            return CalibrationResult.Failure(CalibrationFailureReason.TIMEOUT, validRatio)
        }
        if (validSamples < config.minSamples || validRatio < config.minValidRatio) {
            return CalibrationResult.Failure(CalibrationFailureReason.NOT_ENOUGH_SAMPLES, validRatio)
        }

        val forward = stableValues(forwardValues)
        val head = stableValues(headValues)
        val faceMode = headValues.size >= maxOf(config.minSamples / 2, 1)
        val torsoMode = torsoValues.size >= maxOf(config.minSamples / 2, 1)
        val torso = if (torsoMode) stableValues(torsoValues) else emptyList()
        if (forward.size < config.minSamples / 2 || (faceMode && head.size < config.minSamples / 2)) {
            return CalibrationResult.Failure(CalibrationFailureReason.NOT_ENOUGH_SAMPLES, validRatio)
        }

        val forwardMedian = Geometry.median(forward.toDoubleArray())
        val headMedian = if (faceMode) Geometry.median(head.toDoubleArray()) else 0.0
        val torsoMedian = if (torsoMode) Geometry.median(torso.toDoubleArray()) else 0.0
        val forwardMad = Geometry.medianAbsoluteDeviation(forward.toDoubleArray(), forwardMedian)
        val headMad = if (faceMode) {
            Geometry.medianAbsoluteDeviation(head.toDoubleArray(), headMedian)
        } else {
            CalibrationProfile.UNMEASURED_MAD
        }
        val torsoMad = if (torsoMode) {
            Geometry.medianAbsoluteDeviation(torso.toDoubleArray(), torsoMedian)
        } else {
            CalibrationProfile.UNMEASURED_MAD
        }

        if (forwardMad > config.maxForwardOffsetMad ||
            (faceMode && headMad > config.maxHeadAngleMadDeg) ||
            (torsoMode && torsoMad > config.maxTorsoAngleMadDeg)
        ) {
            return CalibrationResult.Failure(CalibrationFailureReason.UNSTABLE_POSTURE, validRatio)
        }

        val scaleValues = if (torsoMode) torsoScaleValues else headScaleValues
        if (scaleValues.isEmpty()) {
            return CalibrationResult.Failure(CalibrationFailureReason.NOT_ENOUGH_SAMPLES, validRatio)
        }

        val profile = CalibrationProfile(
            createdAt = clock(),
            facingDirection = facingDirection,
            selectedBodySide = side,
            forwardOffsetMedian = forwardMedian,
            headAngleMedianDeg = headMedian,
            torsoAngleMedianDeg = torsoMedian,
            torsoScaleMedian = Geometry.median(scaleValues.toDoubleArray()),
            forwardOffsetMad = forwardMad,
            headAngleMadDeg = headMad,
            torsoAngleMadDeg = torsoMad,
            qualityMedian = Geometry.median(qualityValues.toDoubleArray()),
            validSampleRatio = validRatio,
            modelVersion = modelVersion,
            isActive = true,
        )
        val thresholds = EffectiveThresholds.from(thresholdConfig, SensitivityPreset.BALANCED, profile)
        val proposal = ThresholdProposal(
            forwardHeadMild = thresholds.forwardHeadMild,
            forwardHeadSevere = thresholds.forwardHeadSevere,
            headDownMildDeg = thresholds.headDownMildDeg,
            headDownSevereDeg = thresholds.headDownSevereDeg,
            torsoLeanMildDeg = thresholds.torsoLeanMildDeg,
            torsoLeanSevereDeg = thresholds.torsoLeanSevereDeg,
        )
        return CalibrationResult.Success(profile, proposal, validRatio)
    }

    private fun stableValues(values: List<Double>): List<Double> = Geometry.trimOutliers(values)
}
