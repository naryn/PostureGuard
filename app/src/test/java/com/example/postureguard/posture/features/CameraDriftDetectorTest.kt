package com.example.postureguard.posture.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraDriftDetectorTest {

    @Test
    fun stableScale_doesNotWarn() {
        val detector = CameraDriftDetector()
        detector.reset(0.20)
        var now = 0L
        repeat(100) {
            now += 100
            assertFalse(detector.observe(0.21, now))
        }
        assertFalse(detector.drifting)
    }

    @Test
    fun sustainedScaleChange_warnsAfterThreshold() {
        val detector = CameraDriftDetector(requiredOutOfRangeMs = 8_000)
        detector.reset(0.20)
        var now = 0L
        repeat(80) {
            assertFalse(detector.observe(0.35, now))
            now += 100
        }
        assertTrue(detector.observe(0.35, now))
        assertTrue(detector.drifting)
    }

    @Test
    fun returningToBaseline_beforeThreshold_clearsCandidate() {
        val detector = CameraDriftDetector(requiredOutOfRangeMs = 8_000)
        detector.reset(0.20)
        var now = 0L
        repeat(30) {
            now += 100
            detector.observe(0.35, now)
        }
        repeat(30) {
            now += 100
            detector.observe(0.20, now)
        }
        repeat(60) {
            now += 100
            assertFalse(detector.observe(0.35, now))
        }
        assertFalse(detector.drifting)
    }

    @Test
    fun longGap_restartsAccumulation() {
        val detector = CameraDriftDetector(requiredOutOfRangeMs = 8_000, maxSampleGapMs = 2_000)
        detector.reset(0.20)
        detector.observe(0.35, 0)
        detector.observe(0.35, 5_000)
        detector.observe(0.35, 5_100)
        assertFalse(detector.drifting)
    }

    @Test
    fun missingMetrics_resetCandidate() {
        val detector = CameraDriftDetector(requiredOutOfRangeMs = 8_000)
        detector.reset(0.20)
        detector.observe(0.35, 0)
        detector.observe(null, 100)
        detector.observe(0.35, 200)
        repeat(70) { detector.observe(0.35, 300L + it * 100L) }
        assertFalse(detector.drifting)
    }
}
