package com.example.postureguard.posture.features

import com.example.postureguard.core.math.Geometry
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.ConditionLevels
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
        val metrics = computeMetrics(pose, side, facingDirection, frame.aspectRatio)
        val issues = mutableSetOf<QualityIssue>()

        val ear = pose[side.ear]
        val shoulder = pose[side.shoulder]
        val hip = pose[side.hip]
        val nose = pose[LandmarkType.NOSE]
        val minConfidence = config.minLandmarkConfidence
        val required = listOfNotNull(nose, ear, shoulder, hip)
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
        if (required.size < 4 || scores.any { it < minConfidence }) {
            issues += QualityIssue.LOW_CONFIDENCE
        }
        if (listOfNotNull(nose, ear, shoulder, hip).any { !it.isInFrame() }) {
            issues += QualityIssue.BODY_PARTS_OUT_OF_FRAME
        }
        if (metrics == null || metrics.torsoScale < config.minTorsoScale || metrics.torsoScale > config.maxTorsoScale) {
            issues += QualityIssue.TORSO_SCALE_INVALID
        }
        if (ear != null && hip != null && ear.y >= hip.y) {
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
            val parts = listOfNotNull(pose[side.ear], pose[side.shoulder], pose[side.hip])
            if (parts.size < 2) return -1.0
            return parts.map { it.confidence.toDouble() }.average()
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
         * Computes aspect-corrected geometry. Returns null when the required points are
         * missing or the torso scale is degenerate.
         */
        fun computeMetrics(
            pose: PoseLandmarks,
            side: BodySide,
            facingDirection: FacingDirection,
            aspectRatio: Double,
            minTorsoScale: Double = 1e-4,
        ): RawMetrics? {
            val nose = pose[LandmarkType.NOSE] ?: return null
            val ear = pose[side.ear] ?: return null
            val shoulder = pose[side.shoulder] ?: return null
            val hip = pose[side.hip] ?: return null
            if (nose.confidence <= 0f || ear.confidence <= 0f ||
                shoulder.confidence <= 0f || hip.confidence <= 0f
            ) {
                return null
            }
            if (!nose.y.isFinite() || !ear.y.isFinite()) return null

            val aspect = aspectRatio.takeIf { it.isFinite() && it > 0.05 } ?: 1.0
            val fwd = facingDirection.forwardSign
            val sx = shoulder.x.toDouble() * aspect
            val hx = hip.x.toDouble() * aspect
            val torsoScale = Geometry.distance(sx - hx, shoulder.y.toDouble() - hip.y.toDouble())
            if (!torsoScale.isFinite() || torsoScale < minTorsoScale) return null

            val forwardOffset = fwd * (ear.x.toDouble() * aspect - sx) / torsoScale
            val headDownAngle = Geometry.angleFromHorizontalDegrees(
                dx = fwd * (nose.x.toDouble() * aspect - ear.x.toDouble() * aspect),
                downPositiveDy = nose.y.toDouble() - ear.y.toDouble(),
            )
            val torsoAngle = Geometry.angleFromVerticalDegrees(
                dx = fwd * (sx - hx),
                downPositiveDy = shoulder.y.toDouble() - hip.y.toDouble(),
            )
            return RawMetrics(
                forwardOffset = forwardOffset,
                headAngleDeg = Geometry.wrapDegrees(headDownAngle),
                torsoAngleDeg = Geometry.wrapDegrees(torsoAngle),
                torsoScale = torsoScale,
            )
        }

        fun deltas(metrics: RawMetrics, baseline: CalibrationProfile): PostureDeltas =
            PostureDeltas(
                forwardHead = metrics.forwardOffset - baseline.forwardOffsetMedian,
                headDown = Geometry.shortestAngleDeltaDegrees(metrics.headAngleDeg, baseline.headAngleMedianDeg),
                torsoLean = Geometry.shortestAngleDeltaDegrees(metrics.torsoAngleDeg, baseline.torsoAngleMedianDeg),
            )

        fun levels(deltas: PostureDeltas, thresholds: EffectiveThresholds): ConditionLevels =
            ConditionLevels(
                forwardHead = thresholds.levelForward(deltas.forwardHead),
                headDown = thresholds.levelHeadDown(deltas.headDown),
                torsoLean = thresholds.levelTorso(deltas.torsoLean),
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
