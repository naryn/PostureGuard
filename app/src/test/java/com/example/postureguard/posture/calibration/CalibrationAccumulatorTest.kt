package com.example.postureguard.posture.calibration

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.RawMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationAccumulatorTest {

    private var now = 0L

    private fun accumulator(side: BodySide = BodySide.LEFT) = CalibrationAccumulator(
        facingDirection = if (side == BodySide.LEFT) FacingDirection.FACING_RIGHT else FacingDirection.FACING_LEFT,
        side = side,
        modelVersion = "test-model",
        config = CalibrationConfig(durationMs = 15_000, warmupMs = 2_000, minSamples = 40),
        clock = { now },
    )

    private val quality = LandmarkQuality(true, 0.9, BodySide.LEFT)

    private fun sample(forward: Double, head: Double, torso: Double, scale: Double = 0.20): RawMetrics =
        RawMetrics(forward, head, torso, scale)

    @Test
    fun stableSamples_produceBaseline() {
        val acc = accumulator()
        now = 0
        var timestamp = 0L
        repeat(151) {
            val jitter = (it % 3 - 1) * 0.001
            acc.addSample(sample(0.20 + jitter, 4.0 + jitter * 10, 2.0 + jitter * 10), quality, timestamp)
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(15_000)
        assertTrue(result is CalibrationResult.Success)
        result as CalibrationResult.Success
        assertEquals(0.20, result.profile.forwardOffsetMedian, 0.005)
        assertEquals(4.0, result.profile.headAngleMedianDeg, 0.5)
        assertEquals(2.0, result.profile.torsoAngleMedianDeg, 0.5)
        assertTrue(result.profile.forwardOffsetMad < 0.01)
        assertTrue(result.proposal.forwardHeadMild >= 0.10)
    }

    @Test
    fun upperBodyCalibration_disablesTorsoLean() {
        val acc = accumulator()
        var timestamp = 0L
        repeat(130) { index ->
            val jitter = (index % 3 - 1) * 0.001
            acc.addSample(
                RawMetrics(0.30 + jitter, 4.0, 0.0, 0.13, torsoMeasured = false, faceMeasured = true),
                quality,
                timestamp,
            )
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(timestamp)
        assertTrue(result is CalibrationResult.Success)
        result as CalibrationResult.Success
        assertFalse(result.profile.torsoMeasured)
        assertEquals(0.30, result.profile.forwardOffsetMedian, 0.005)
        assertEquals(0.13, result.profile.torsoScaleMedian, 1e-6)
        assertEquals(Double.POSITIVE_INFINITY, result.proposal.torsoLeanMildDeg, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, result.proposal.torsoLeanSevereDeg, 0.0)
    }

    @Test
    fun missingFace_disablesHeadDown() {
        val acc = accumulator()
        var timestamp = 0L
        repeat(130) { index ->
            val jitter = (index % 3 - 1) * 0.001
            acc.addSample(
                RawMetrics(0.30 + jitter, 0.0, 0.0, 0.13, faceMeasured = false),
                quality,
                timestamp,
            )
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(timestamp)
        assertTrue(result is CalibrationResult.Success)
        result as CalibrationResult.Success
        assertFalse(result.profile.headDownMeasured)
        assertEquals(Double.POSITIVE_INFINITY, result.proposal.headDownMildDeg, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, result.proposal.headDownSevereDeg, 0.0)
    }

    @Test
    fun outliers_doNotMoveTheBaseline() {
        val acc = accumulator()
        var timestamp = 0L
        repeat(130) { index ->
            val forward = if (index % 10 == 0) 2.0 else 0.20
            acc.addSample(sample(forward, 4.0, 2.0), quality, timestamp)
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(timestamp)
        assertTrue(result is CalibrationResult.Success)
        result as CalibrationResult.Success
        assertEquals(0.20, result.profile.forwardOffsetMedian, 0.01)
    }

    @Test
    fun insufficientValidSamples_fails() {
        val acc = accumulator()
        var timestamp = 0L
        repeat(130) {
            acc.addSample(null, LandmarkQuality(false, 0.0, BodySide.LEFT), timestamp)
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(timestamp)
        assertTrue(result is CalibrationResult.Failure)
        assertEquals(
            CalibrationFailureReason.NOT_ENOUGH_SAMPLES,
            (result as CalibrationResult.Failure).reason,
        )
    }

    @Test
    fun unstablePosture_failsWithRetryHint() {
        val acc = accumulator()
        var timestamp = 0L
        repeat(130) { index ->
            val forward = if (index % 2 == 0) 0.05 else 0.45
            acc.addSample(sample(forward, if (index % 2 == 0) -10.0 else 20.0, 2.0), quality, timestamp)
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(timestamp)
        assertTrue(result is CalibrationResult.Failure)
        assertEquals(
            CalibrationFailureReason.UNSTABLE_POSTURE,
            (result as CalibrationResult.Failure).reason,
        )
    }

    @Test
    fun wrongSideSamples_areDiscarded() {
        val acc = accumulator(BodySide.LEFT)
        var timestamp = 0L
        repeat(130) {
            acc.addSample(
                sample(0.2, 4.0, 2.0),
                LandmarkQuality(true, 0.9, BodySide.RIGHT),
                timestamp,
            )
            timestamp += 100
        }
        now = 15_000
        val result = acc.finish(timestamp)
        assertTrue(result is CalibrationResult.Failure)
    }

    @Test
    fun progressReportsWarmupAndRatio() {
        val acc = accumulator()
        acc.addSample(sample(0.2, 4.0, 2.0), quality, 500)
        val warmup = acc.progress(500)
        assertTrue(warmup.inWarmup)
        assertEquals(0, warmup.validSamples)
        var timestamp = 2_000L
        repeat(20) {
            acc.addSample(sample(0.2, 4.0, 2.0), quality, timestamp)
            timestamp += 100
        }
        val after = acc.progress(timestamp)
        assertEquals(20, after.validSamples)
        assertTrue(after.validRatio > 0.9)
    }
}
