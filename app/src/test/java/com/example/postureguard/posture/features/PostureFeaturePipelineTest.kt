package com.example.postureguard.posture.features

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.posture.model.PoseLandmarks
import com.example.postureguard.posture.model.QualityIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(0.05 / 0.18, rightResult.metrics!!.forwardOffset, 1e-6)
        assertEquals(0.05 / 0.18, leftResult.metrics!!.forwardOffset, 1e-6)
        assertEquals(BodySide.LEFT, rightResult.side)
        assertEquals(BodySide.LEFT, leftResult.side)
    }

    @Test
    fun forwardHeadMustNotDependOnScreenXDirection() {
        // Facing left: ear to the left of shoulder means the head is behind, not forward.
        val behind = PoseFixtures.pose(
            ear = 0.42f to 0.40f,
            shoulder = 0.47f to 0.52f,
            hip = 0.47f to 0.70f,
            mirror = true,
        )
        val result = pipeline.process(frame(behind), FacingDirection.FACING_LEFT)
        assertTrue(result.metrics!!.forwardOffset < 0)
    }

    @Test
    fun headAngle_positiveWhenNoseDropsBelowEar() {
        val pose = PoseFixtures.pose(
            nose = 0.52f to 0.45f,
            ear = 0.47f to 0.40f,
        )
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertEquals(45.0, result.metrics!!.headAngleDeg, 0.5)
    }

    @Test
    fun headAngle_isMirroredConsistently() {
        val pose = PoseFixtures.pose(
            nose = 0.52f to 0.45f,
            ear = 0.47f to 0.40f,
            mirror = true,
        )
        val result = pipeline.process(frame(pose), FacingDirection.FACING_LEFT)
        assertEquals(45.0, result.metrics!!.headAngleDeg, 0.5)
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
    fun degenerateTorsoScale_returnsNoMetrics() {
        val pose = PoseFixtures.pose(
            shoulder = 0.47f to 0.52f,
            hip = 0.47f to 0.52f,
        )
        val result = pipeline.process(frame(pose), FacingDirection.FACING_RIGHT)
        assertNull(result.metrics)
        assertFalse(result.quality.valid)
        assertTrue(result.quality.issues.contains(QualityIssue.TORSO_SCALE_INVALID))
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
        // Horizontal offsets double with a 2:1 aspect; the vertical torso scale is unchanged.
        assertEquals(2.0, wide.forwardOffset / square.forwardOffset, 0.01)
        assertEquals(square.torsoScale, wide.torsoScale, 1e-9)
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
}
