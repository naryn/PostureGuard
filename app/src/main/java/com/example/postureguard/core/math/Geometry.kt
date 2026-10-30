package com.example.postureguard.core.math

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object Geometry {

    fun distance(dx: Double, dy: Double): Double = hypot(dx, dy)

    fun dot(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * bx + ay * by

    /** Wraps an angle to [-180, 180). */
    fun wrapDegrees(angleDeg: Double): Double {
        if (!angleDeg.isFinite()) return 0.0
        var wrapped = (angleDeg + 180.0) % 360.0
        if (wrapped < 0) wrapped += 360.0
        return wrapped - 180.0
    }

    /** Smallest signed difference between two angles, in degrees. */
    fun shortestAngleDeltaDegrees(current: Double, reference: Double): Double =
        wrapDegrees(current - reference)

    /** Signed angle of a vector measured from +x, in degrees. */
    fun angleFromHorizontalDegrees(dx: Double, downPositiveDy: Double): Double =
        Math.toDegrees(atan2(downPositiveDy, dx))

    /** Signed angle from vertical up (+y is down in image space), in degrees. */
    fun angleFromVerticalDegrees(dx: Double, downPositiveDy: Double): Double =
        Math.toDegrees(atan2(dx, -downPositiveDy))

    fun median(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.copyOf()
        sorted.sort()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** Median absolute deviation around the supplied median. */
    fun medianAbsoluteDeviation(values: DoubleArray, center: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val deviations = DoubleArray(values.size) { abs(values[it] - center) }
        return median(deviations)
    }

    /**
     * Replaces values further than [limitMad] * MAD from the median with the median,
     * so that a few wild frames do not move a percentile baseline.
     */
    fun trimOutliers(values: List<Double>, limitMad: Double = 3.0): List<Double> {
        if (values.size < 4) return values
        val array = values.toDoubleArray()
        val center = median(array)
        if (!center.isFinite()) return values
        val mad = medianAbsoluteDeviation(array, center)
        val tolerance = max(mad * limitMad, 1e-6)
        return values.filter { abs(it - center) <= tolerance }
    }

    fun clamp(value: Double, minValue: Double, maxValue: Double): Double = min(max(value, minValue), maxValue)
}
