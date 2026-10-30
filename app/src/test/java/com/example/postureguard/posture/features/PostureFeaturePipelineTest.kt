package com.example.postureguard.posture.features

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.posture.model.PoseLandmarks
import com.example.postureguard.posture.model.QualityIssue
import com.example.postureguard.posture.model.RawMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PostureFeaturePipelineTest {

    private val pipeline = PostureFeaturePipeline()
    private val frameSize = 100

    private fun frame(pose: PoseLandmarks) =
        PoseFrame(pose, timestampMs = 0L, imageWidth = frameSize, imageHeight = frameSize)

    @Test
    fun forwardOffset_isSameForMirroredFacingDirections() {
        val right = PoseFixtures.pose(ear = 0.52f to 0.40f, shoulder = 0.47f to 0.52f, hip = 0.47f to 0.70f)
        val left = PoseFixtures.pose(
            ear = 0.52f to 0.40f,
            shoulder = 0.47f to 0.52f,
            hip = 0.47f to 0.70f,
            mirror = true,
        )
        val rightResult = pipeline.process(frame(right), FacingDirection.FACING_RIGHT)
        val leftResult = pipeline.process(frame(left), FacingDirection.FACING_LEFT)
        assertNotNull(rightResult.metrics)
        assertNotNull(leftResult.metrics)
        // Forward offset is the eye/nose/ear weighted offset split by the ear-shoulder length.
        assertEquals(0.0425 / 0.13, rightResult.metrics!!.forwardOffset, 1e-6)
        assertEquals(0.0425 / 0.13, leftResult.metrics!!.forwardOffset, 1e-6)
        assertEquals(BodySide.LEFT, rightResult.side)
        assertEquals(BodySide.LEFT, leftResult.side)
    }

    @Test
    fun forwardHeadMustNotDependOnScreenXDirection() {
        // Facing left: nose to the left of shoulder means the head is behind, not forward.
        val behind = PoseFixtures.pose(
            nose = 0.42f to 0.40f,
            ear = 0.42f to 0.40f,
            shoulder = 0.47f to 0.52f,
            hip = 0.47f to 0.70f,
            mirror = true,
        )
        val result = pipeline.process(frame(behind), FacingDirection.FACING_LEFT)
        assertTrue(result.metrics!!.forwardOffset < 0)
    }

    @Test
    fun forwardOffset_usesEyeNoseAndEarRelativeToShoulder() {
        val neutral = pipeline.process(
            frame(PoseFixtures.pose(nose = 0.52f to 0.40f, ear = 0.47f to 0.40f, shoulder = 0.47f to 0.52f)),
            FacingDirection.FACING_RIGHT,
        )
        val forwardNose = pipeline.process(
            frame(PoseFixtures.pose(nose = 0.55f to 0.40f, ear = 0.47f to 0.40f, shoulder = 0.47f to 0.52f)),
            FacingDirection.FACING_RIGHT,
        )
        assertEquals(0.035 / 0.12, neutral.metrics!!.forwardOffset, 1e-6)
        assertEquals(0.053 / 0.12, forwardNose.metrics!!.forwardOffset, 1e-6)
    }

    @Test
    fun forwardOffset_includesEyeAndEarMovement() {
        val neutral = pipeline.process(frame(PoseFixtures.pose()), FacingDirection.FACING_RIGHT).metrics!!
        val eyeForward = pipeline.process(
            frame(PoseFixtures.pose(eye = 0.52f to 0.39f)),
            FacingDirection.FACING_RIGHT,
        ).metrics!!
        val earForward = pipeline.process(
            frame(PoseFixtures.pose(ear = 0.50f to 0.40f)),
            FacingDirection.FACING_RIGHT,
        ).metrics!!
        assertTrue(eyeForward.forwardOffset > neutral.forwardOffset)
        assertTrue(earForward.forwardOffset > neutral.forwardOffset)
    }

    @Test
    fun missingEye_stillComputesForwardOffsetFromNoseAndEar() {
        val metrics = pipeline.process(
            frame(PoseFixtures.pose(eye = null)),
            FacingDirection.FACING_RIGHT,
        ).metrics!!
        // Nose and ear keep their relative weights after renormalization.
        assertEquals(0.04 / 0.12, metrics.forwardOffset, 1e-6)
    }

    @Test
    fun faceAxis_increasesWhenHeadBows() {
        val neutral = pipeline.process(frame(PoseFixtures.pose()), FacingDirection.FACING_RIGHT)
        val bent = pipeline.process(
            frame(
                PoseFixtures.pose(
                    eye = rotate(0.49f to 0.39f, 0.47f to 0.40f, 20.0),
                    nose = rotate(0.52f to 0.40f, 0.47f to 0.40f, 20.0),
                    mouth = rotate(0.51f to 0.45f, 0.47f to 0.40f, 20.0),
                ),
            ),
            FacingDirection.FACING_RIGHT,
        )
        assertTrue(neutral.metrics!!.faceMeasured)
        assertTrue(bent.metrics!!.faceMeasured)
        assertEquals(neutral.metrics!!.headAngleDeg + 20.0, bent.metrics!!.headAngleDeg, 0.5)
    }

    @Test
    fun faceAxis_isMirroredConsistently() {
        val right = pipeline.process(
            frame(
                PoseFixtures.pose(
                    eye = rotate(0.49f to 0.39f, 0.47f to 0.40f, 20.0),
                    nose = rotate(0.52f to 0.40f, 0.47f to 0.40f, 20.0),
                    mouth = rotate(0.51f to 0.45f, 0.47f to 0.40f, 20.0),
                ),
            ),
            FacingDirection.FACING_RIGHT,
        )
        val left = pipeline.process(
            frame(
                PoseFixtures.pose(
                    eye = rotate(0.49f to 0.39f, 0.47f to 0.40f, 20.0),
                    nose = rotate(0.52f to 0.40f, 0.47f to 0.40f, 20.0),
                    mouth = rotate(0.51f to 0.45f, 0.47f to 0.40f, 20.0),
                    mirror = true,
                ),
            ),
            FacingDirection.FACING_LEFT,
        )
        assertEquals(right.metrics!!.headAngleDeg, left.metrics!!.headAngleDeg, 1e-6)
    }

    @Test
    fun missingEyesOrMouth_keepsUpperBodyMetricsButDisablesFacePitch() {
        listOf(PoseFixtures.pose(eye = null), PoseFixtures.pose(mouth = null)).forEach { pose ->
            val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
            assertTrue(result.quality.valid)
            val metrics = result.metrics!!
            assertFalse(metrics.faceMeasured)
            assertEquals(0.0, metrics.headAngleDeg, 0.0)
        }
    }

    @Test
    fun torsoAngle_positiveWhenShoulderMovesForward() {
        val pose = PoseFixtures.pose(
            shoulder = 0.52f to 0.52f,
            hip = 0.47f to 0.70f,
        )
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertTrue(result.metrics!!.torsoAngleDeg > 10.0)
    }

    @Test
    fun degenerateNeckScale_returnsNoMetrics() {
        val pose = PoseFixtures.pose(
            ear = 0.47f to 0.52f,
            shoulder = 0.47f to 0.52f,
        )
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertNull(result.metrics)
        assertFalse(result.quality.valid)
        assertTrue(result.quality.issues.contains(QualityIssue.TORSO_SCALE_INVALID))
    }

    @Test
    fun missingHip_stillProducesUpperBodyMetrics() {
        val pose = PoseFixtures.pose(
            ear = 0.52f to 0.40f,
            shoulder = 0.47f to 0.52f,
            hip = null,
        )
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertTrue(result.quality.valid)
        val metrics = result.metrics!!
        assertFalse(metrics.torsoMeasured)
        assertEquals(0.0425 / 0.13, metrics.forwardOffset, 1e-6)
        assertEquals(0.13, metrics.torsoScale, 1e-6)
        assertFalse(QualityIssue.TORSO_SCALE_INVALID in result.quality.issues)
    }

    @Test
    fun missingNose_isRejectedAsLowQuality() {
        val pose = PoseFixtures.pose(nose = null)
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertNull(result.metrics)
        assertFalse(result.quality.valid)
        assertTrue(result.quality.issues.contains(QualityIssue.LOW_CONFIDENCE))
    }

    @Test
    fun lowConfidenceLandmarks_areRejected() {
        val pose = PoseFixtures.pose(confidence = 0.2f)
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertFalse(result.quality.valid)
        assertTrue(result.quality.issues.contains(QualityIssue.LOW_CONFIDENCE))
    }

    @Test
    fun validPose_passesQualityGate() {
        val pose = PoseFixtures.pose()
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertTrue(result.quality.valid)
        assertEquals(0, result.quality.issues.size)
        assertNotNull(result.metrics)
    }

    @Test
    fun aspectRatioScalesHorizontalGeometry() {
        val pose = PoseFixtures.pose(ear = 0.52f to 0.40f, shoulder = 0.47f to 0.52f, hip = 0.47f to 0.70f)
        val square = PostureFeaturePipeline.computeMetrics(pose, BodySide.LEFT, FacingDirection.FACING_RIGHT, 1.0)
        val wide = PostureFeaturePipeline.computeMetrics(pose, BodySide.LEFT, FacingDirection.FACING_RIGHT, 2.0)
        assertNotNull(square)
        assertNotNull(wide)
        assertTrue(wide!!.forwardOffset > square!!.forwardOffset)
        // The vertical torso scale is unchanged by the aspect correction.
        assertEquals(square.torsoScale, wide.torsoScale, 1e-9)
        assertTrue(square.torsoMeasured)
        assertTrue(wide.torsoMeasured)
    }

    @Test
    fun upperBodyBaseline_disablesTorsoLean() {
        val metrics = RawMetrics(
            forwardOffset = 0.4,
            headAngleDeg = 0.0,
            torsoAngleDeg = 0.0,
            torsoScale = 0.13,
            torsoMeasured = false,
        )
        val baseline = CalibrationProfile(
            createdAt = 0L,
            facingDirection = FacingDirection.FACING_RIGHT,
            selectedBodySide = BodySide.LEFT,
            forwardOffsetMedian = 0.3,
            headAngleMedianDeg = 0.0,
            torsoAngleMedianDeg = 0.0,
            torsoScaleMedian = 0.13,
            forwardOffsetMad = 0.01,
            headAngleMadDeg = 1.0,
            torsoAngleMadDeg = CalibrationProfile.UNMEASURED_MAD,
            qualityMedian = 0.9,
            validSampleRatio = 0.9,
            modelVersion = "test",
        )
        val deltas = PostureFeaturePipeline.deltas(metrics, baseline)
        assertFalse(deltas.torsoAvailable)
        assertEquals(0.0, deltas.torsoLean, 0.0)
        assertEquals(0.1, deltas.forwardHead, 1e-9)
    }

    @Test
    fun missingFaceBaseline_disablesHeadDown() {
        val metrics = RawMetrics(
            forwardOffset = 0.3,
            headAngleDeg = 60.0,
            torsoAngleDeg = 0.0,
            torsoScale = 0.13,
            faceMeasured = true,
        )
        val baseline = CalibrationProfile(
            createdAt = 0L,
            facingDirection = FacingDirection.FACING_RIGHT,
            selectedBodySide = BodySide.LEFT,
            forwardOffsetMedian = 0.3,
            headAngleMedianDeg = 0.0,
            torsoAngleMedianDeg = 0.0,
            torsoScaleMedian = 0.13,
            forwardOffsetMad = 0.01,
            headAngleMadDeg = CalibrationProfile.UNMEASURED_MAD,
            torsoAngleMadDeg = CalibrationProfile.UNMEASURED_MAD,
            qualityMedian = 0.9,
            validSampleRatio = 0.9,
            modelVersion = "test",
        )
        val deltas = PostureFeaturePipeline.deltas(metrics, baseline)
        assertFalse(deltas.headDownAvailable)
        assertEquals(0.0, deltas.headDown, 0.0)
    }

    @Test
    fun missingFaceInFrame_disablesHeadDown() {
        val pose = PoseFixtures.pose(eye = null)
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        val baseline = CalibrationProfile(
            createdAt = 0L,
            facingDirection = FacingDirection.FACING_RIGHT,
            selectedBodySide = BodySide.LEFT,
            forwardOffsetMedian = 0.0,
            headAngleMedianDeg = 60.0,
            torsoAngleMedianDeg = 0.0,
            torsoScaleMedian = 0.13,
            forwardOffsetMad = 0.01,
            headAngleMadDeg = 1.0,
            torsoAngleMadDeg = CalibrationProfile.UNMEASURED_MAD,
            qualityMedian = 0.9,
            validSampleRatio = 0.9,
            modelVersion = "test",
        )
        val deltas = PostureFeaturePipeline.deltas(result.metrics!!, baseline)
        assertFalse(deltas.headDownAvailable)
    }

    @Test
    fun flippingSideConfidenceMarksQualityUnstable() {
        val flipping = PostureFeaturePipeline()
        repeat(12) { index ->
            val pose = PoseFixtures.pose(
                visibleSide = if (index % 2 == 0) BodySide.LEFT else BodySide.RIGHT,
            )
            flipping.process(frame(pose), FacingDirection.FACING_RIGHT)
        }
        val final = flipping.process(frame(PoseFixtures.pose()), FacingDirection.FACING_RIGHT)
        assertFalse(final.quality.valid)
        assertTrue(final.quality.issues.contains(QualityIssue.SIDE_UNSTABLE))
    }

    /** Rotates a screen point around [center]; positive degrees tilt the face downwards. */
    private fun rotate(
        point: Pair<Float, Float>,
        center: Pair<Float, Float>,
        degrees: Double,
    ): Pair<Float, Float> {
        val rad = Math.toRadians(degrees)
        val dx = (point.first - center.first).toDouble()
        val dy = (point.second - center.second).toDouble()
        val rotatedX = center.first + dx * cos(rad) - dy * sin(rad)
        val rotatedY = center.second + dx * sin(rad) + dy * cos(rad)
        return rotatedX.toFloat() to rotatedY.toFloat()
    }
}
