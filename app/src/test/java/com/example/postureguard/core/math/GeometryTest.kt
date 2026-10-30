package com.example.postureguard.core.math

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {

    @Test
    fun wrapDegrees_handlesCrossing() {
        assertEquals(-170.0, Geometry.wrapDegrees(190.0), 1e-9)
        assertEquals(170.0, Geometry.wrapDegrees(-190.0), 1e-9)
        assertEquals(0.0, Geometry.wrapDegrees(360.0), 1e-9)
        assertEquals(45.0, Geometry.wrapDegrees(45.0), 1e-9)
    }

    @Test
    fun shortestAngleDelta_usesSmallestPath() {
        assertEquals(-20.0, Geometry.shortestAngleDeltaDegrees(170.0, -170.0), 1e-9)
        assertEquals(20.0, Geometry.shortestAngleDeltaDegrees(-170.0, 170.0), 1e-9)
        assertEquals(10.0, Geometry.shortestAngleDeltaDegrees(10.0, 0.0), 1e-9)
    }

    @Test
    fun median_handlesOddAndEvenCounts() {
        assertEquals(3.0, Geometry.median(doubleArrayOf(5.0, 1.0, 3.0)), 1e-9)
        assertEquals(2.5, Geometry.median(doubleArrayOf(4.0, 1.0, 3.0, 2.0)), 1e-9)
    }

    @Test
    fun medianAbsoluteDeviation_isRobustToOutlier() {
        val values = doubleArrayOf(0.10, 0.11, 0.09, 0.10, 10.0)
        val median = Geometry.median(values)
        val mad = Geometry.medianAbsoluteDeviation(values, median)
        assertEquals(0.10, median, 1e-9)
        assertEquals(0.01, mad, 1e-9)
    }

    @Test
    fun trimOutliers_removesWildFrames() {
        val values = (0 until 100).map { 0.10 + (it % 5) * 0.001 } + listOf(5.0)
        val trimmed = Geometry.trimOutliers(values)
        assertEquals(100, trimmed.size)
        assertEquals(true, trimmed.none { it > 1.0 })
    }

    @Test
    fun dotAndDistance() {
        assertEquals(0.0, Geometry.dot(1.0, 0.0, 0.0, 1.0), 1e-9)
        assertEquals(5.0, Geometry.distance(3.0, 4.0), 1e-9)
    }

    @Test
    fun angleFromVertical_positiveWhenLeaningForwardInCorrectionSpace() {
        // 0.1 forward, 1.0 down => atan(0.1) => ~5.71 degrees
        assertEquals(5.71, Geometry.angleFromVerticalDegrees(0.1, -1.0), 0.01)
        assertEquals(-5.71, Geometry.angleFromVerticalDegrees(-0.1, -1.0), 0.01)
    }
}
