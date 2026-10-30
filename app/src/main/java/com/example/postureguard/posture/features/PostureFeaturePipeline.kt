package com.example.postureguard.posture.features

import com.example.postureguard.core.math.Geometry
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.ConditionLevels
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.LandmarkType
import com.example.postureguard.posture.model.PostureDeltas
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.posture.model.PoseLandmarks
import com.example.postureguard.posture.model.QualityIssue
import com.example.postureguard.posture.model.RawMetrics
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.EffectiveThresholds
import com.example.postureguard.posture.model.PostureThresholdConfig

data class FeatureResult(
    val metrics: RawMetrics?,
    val quality: LandmarkQuality,
    val side: BodySide?,
    val facingDirection: FacingDirection,
    val aspectRatio: Double,
)

/**
 * Turns normalized landmarks into the geometry used by the posture engine.
 * All forward quantities are expressed in "towards the body front" units, so the
 * camera side (facing left/right) never leaks into the state machine.
 */
class PostureFeaturePipeline(
    private val config: PostureThresholdConfig = PostureThresholdConfig(),
) {
    private val sideTracker = SideTracker()

    fun reset() = sideTracker.reset()

    fun process(frame: PoseFrame, facingDirection: FacingDirection): FeatureResult {
        val pose = frame.landmarks
        val side = selectSide(pose)
        if (side == null) {
            return FeatureResult(
                metrics = null,
                quality = LandmarkQuality(false, 0.0, null, setOf(QualityIssue.PERSON_NOT_FOUND)),
                side = null,
                facingDirection = facingDirection,
                aspectRatio = frame.aspectRatio,
            )
        }
        val stable = sideTracker.observe(side)
        val metrics = computeMetrics(
            pose,
            side,
            facingDirection,
            frame.aspectRatio,
            minFaceConfidence = config.minLandmarkConfidence,
        )
        val issues = mutableSetOf<QualityIssue>()

        val ear = pose[side.ear]
        val shoulder = pose[side.shoulder]
        val hip = pose[side.hip]
        val nose = pose[LandmarkType.NOSE]
        val minConfidence = config.minLandmarkConfidence
        // Only head and shoulder are mandatory; the hip is optional so the phone can be
        // framed on the upper body alone.
        val required = listOfNotNull(nose, ear, shoulder)
        val scores = required.map { it.confidence.toDouble() }
        val qualityScore = if (scores.isEmpty()) 0.0 else scores.average()

        if (pose.points.isEmpty() || nose == null) {
            return FeatureResult(
                metrics = null,
                quality = LandmarkQuality(false, 0.0, side, setOf(QualityIssue.PERSON_NOT_FOUND)),
                side = side,
                facingDirection = facingDirection,
                aspectRatio = frame.aspectRatio,
            )
        }

        if (!stable) issues += QualityIssue.SIDE_UNSTABLE
        if (required.size < 3 || scores.any { it < minConfidence }) {
            issues += QualityIssue.LOW_CONFIDENCE
        }
        if (required.any { !it.isInFrame() }) {
            issues += QualityIssue.BODY_PARTS_OUT_OF_FRAME
        }
        if (metrics == null || metrics.torsoScale < config.minTorsoScale || metrics.torsoScale > config.maxTorsoScale) {
            issues += QualityIssue.TORSO_SCALE_INVALID
        }
        if (metrics?.torsoMeasured == true && ear != null && hip != null && ear.y >= hip.y) {
            issues += QualityIssue.HEAD_BELOW_HIP
        }

        return FeatureResult(
            metrics = metrics,
            quality = LandmarkQuality(
                valid = issues.isEmpty(),
                score = qualityScore,
                selectedSide = side,
                issues = issues,
            ),
            side = side,
            facingDirection = facingDirection,
            aspectRatio = frame.aspectRatio,
        )
    }

    private fun selectSide(pose: PoseLandmarks): BodySide? {
        if (pose.isEmpty) return null
        fun sideScore(side: BodySide): Double {
            val ear = pose[side.ear] ?: return -1.0
            val shoulder = pose[side.shoulder] ?: return -1.0
            if (ear.confidence <= 0f || shoulder.confidence <= 0f) return -1.0
            val hipScore = pose[side.hip]?.confidence?.toDouble()?.takeIf { it > 0.0 } ?: shoulder.confidence.toDouble()
            return (ear.confidence + shoulder.confidence + hipScore) / 3.0
        }
        val left = sideScore(BodySide.LEFT)
        val right = sideScore(BodySide.RIGHT)
        return when {
            left < 0 && right < 0 -> null
            left >= right -> BodySide.LEFT
            else -> BodySide.RIGHT
        }
    }

    companion object {
        /**
         * Computes aspect-corrected geometry. The hip is optional: without a usable hip the
         * metric falls back to the ear-to-shoulder scale and torso lean stays unavailable.
         * Forward lean is the weighted horizontal offset of the eye, nose and ear from the
         * shoulder, normalized by the ear-to-shoulder length (a head-shoulder slope).
         * Head-down uses the face axis formed by eyes, nose and mouth, and stays unavailable
         * when those points are not visible.
         * Returns null when the head/shoulder points are missing or the head scale is degenerate.
         */
        fun computeMetrics(
            pose: PoseLandmarks,
            side: BodySide,
            facingDirection: FacingDirection,
            aspectRatio: Double,
            minFaceConfidence: Double = 0.5,
            minTorsoScale: Double = 1e-4,
        ): RawMetrics? {
            val nose = pose[LandmarkType.NOSE] ?: return null
            val ear = pose[side.ear] ?: return null
            val shoulder = pose[side.shoulder] ?: return null
            if (nose.confidence <= 0f || ear.confidence <= 0f || shoulder.confidence <= 0f) {
                return null
            }
            if (!nose.x.isFinite() || !nose.y.isFinite() || !ear.y.isFinite() || !shoulder.y.isFinite()) {
                return null
            }

            val aspect = aspectRatio.takeIf { it.isFinite() && it > 0.05 } ?: 1.0
            val fwd = facingDirection.forwardSign
            val sx = shoulder.x.toDouble() * aspect
            val nx = nose.x.toDouble() * aspect
            val ex = ear.x.toDouble() * aspect
            val headScale = Geometry.distance(ex - sx, ear.y.toDouble() - shoulder.y.toDouble())
            if (!headScale.isFinite() || headScale < minTorsoScale) return null

            val eye = facePoint(pose, side.eye, side.opposite.eye, minFaceConfidence, aspect)
            val mouth = facePoint(pose, side.mouth, side.opposite.mouth, minFaceConfidence, aspect)

            // A head-shoulder slope built from all visible face points, with the nose weighted
            // highest because it moves most when the neck travels forward.
            var weightedOffset = NOSE_FORWARD_WEIGHT * (nx - sx) + EAR_FORWARD_WEIGHT * (ex - sx)
            var weightSum = NOSE_FORWARD_WEIGHT + EAR_FORWARD_WEIGHT
            if (eye != null) {
                weightedOffset += EYE_FORWARD_WEIGHT * (eye.x - sx)
                weightSum += EYE_FORWARD_WEIGHT
            }
            val forwardOffset = fwd * (weightedOffset / weightSum) / headScale

            var faceMeasured = false
            var headDownAngle = 0.0
            if (eye != null && mouth != null) {
                val eyeToNose = unitVector(fwd * (nx - eye.x), nose.y.toDouble() - eye.y)
                val noseToMouth = unitVector(fwd * (mouth.x - nx), mouth.y - nose.y.toDouble())
                val bisector = if (eyeToNose != null && noseToMouth != null) {
                    unitVector(eyeToNose.x + noseToMouth.x, eyeToNose.y + noseToMouth.y)
                } else {
                    null
                }
                val axis = bisector ?: unitVector(fwd * (mouth.x - eye.x), mouth.y - eye.y)
                if (axis != null) {
                    faceMeasured = true
                    headDownAngle = Geometry.angleFromHorizontalDegrees(axis.x, axis.y)
                }
            }

            var torsoScale = headScale
            var torsoAngle = 0.0
            var torsoMeasured = false
            val hip = pose[side.hip]
            if (hip != null && hip.confidence > 0f && hip.x.isFinite() && hip.y.isFinite() && hip.isInFrame()) {
                val hx = hip.x.toDouble() * aspect
                val scale = Geometry.distance(sx - hx, shoulder.y.toDouble() - hip.y.toDouble())
                if (scale.isFinite() && scale >= minTorsoScale) {
                    torsoScale = scale
                    torsoMeasured = true
                    torsoAngle = Geometry.angleFromVerticalDegrees(
                        dx = fwd * (sx - hx),
                        downPositiveDy = shoulder.y.toDouble() - hip.y.toDouble(),
                    )
                }
            }

            return RawMetrics(
                forwardOffset = forwardOffset,
                headAngleDeg = Geometry.wrapDegrees(headDownAngle),
                torsoAngleDeg = Geometry.wrapDegrees(torsoAngle),
                torsoScale = torsoScale,
                torsoMeasured = torsoMeasured,
                faceMeasured = faceMeasured,
            )
        }

        private const val NOSE_FORWARD_WEIGHT = 0.6
        private const val EYE_FORWARD_WEIGHT = 0.25
        private const val EAR_FORWARD_WEIGHT = 0.15

        /** Prefers the selected side, then the other side, so a profile view still gets a point. */
        private fun facePoint(
            pose: PoseLandmarks,
            preferred: LandmarkType,
            fallback: LandmarkType,
            minConfidence: Double,
            aspect: Double,
        ): Vec2? {
            val landmark = pose[preferred]?.takeIf { it.isUsable(minConfidence) }
                ?: pose[fallback]?.takeIf { it.isUsable(minConfidence) }
                ?: return null
            return Vec2(landmark.x.toDouble() * aspect, landmark.y.toDouble())
        }

        private fun unitVector(dx: Double, dy: Double): Vec2? {
            val length = Geometry.distance(dx, dy)
            if (!length.isFinite() || length < 1e-6) return null
            return Vec2(dx / length, dy / length)
        }

        private data class Vec2(val x: Double, val y: Double)

        fun deltas(metrics: RawMetrics, baseline: CalibrationProfile): PostureDeltas {
            val torsoAvailable = metrics.torsoMeasured && baseline.torsoMeasured
            val headDownAvailable = metrics.faceMeasured && baseline.headDownMeasured
            return PostureDeltas(
                forwardHead = metrics.forwardOffset - baseline.forwardOffsetMedian,
                headDown = if (headDownAvailable) {
                    Geometry.shortestAngleDeltaDegrees(metrics.headAngleDeg, baseline.headAngleMedianDeg)
                } else {
                    0.0
                },
                torsoLean = if (torsoAvailable) {
                    Geometry.shortestAngleDeltaDegrees(metrics.torsoAngleDeg, baseline.torsoAngleMedianDeg)
                } else {
                    0.0
                },
                torsoAvailable = torsoAvailable,
                headDownAvailable = headDownAvailable,
            )
        }

        fun levels(deltas: PostureDeltas, thresholds: EffectiveThresholds): ConditionLevels =
            ConditionLevels(
                forwardHead = thresholds.levelForward(deltas.forwardHead),
                headDown = if (deltas.headDownAvailable) thresholds.levelHeadDown(deltas.headDown) else ConditionSeverity.NONE,
                torsoLean = if (deltas.torsoAvailable) thresholds.levelTorso(deltas.torsoLean) else ConditionSeverity.NONE,
            )
    }
}

internal fun com.example.postureguard.posture.model.Landmark.isInFrame(margin: Float = 0.02f): Boolean =
    x in (-margin)..(1f + margin) && y in (-margin)..(1f + margin)

/** Remembers recent side picks to detect a camera view where left/right keep swapping. */
class SideTracker(
    private val windowSize: Int = 15,
    private val minSamples: Int = 8,
    private val maxFlipRatio: Double = 0.35,
) {
    private val recent = ArrayDeque<BodySide>()

    fun reset() = recent.clear()

    fun observe(side: BodySide): Boolean {
        recent.addLast(side)
        while (recent.size > windowSize) recent.removeFirst()
        if (recent.size < minSamples) return true
        var flips = 0
        val iterator = recent.iterator()
        var previous: BodySide? = null
        while (iterator.hasNext()) {
            val current = iterator.next()
            if (previous != null && current != previous) flips++
            previous = current
        }
        val ratio = flips.toDouble() / (recent.size - 1)
        return ratio <= maxFlipRatio
    }
}
