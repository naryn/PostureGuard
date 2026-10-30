package com.example.postureguard.posture.features

/**
 * Heuristic for "the phone probably moved". The shoulder-to-hip pixel scale is the most
 * position-invariant quantity we have, so a sustained change against the calibration
 * baseline suggests the camera distance or angle changed. It only raises a warning; it
 * never changes posture classification.
 */
class CameraDriftDetector(
    private val lowRatio: Double = 0.72,
    private val highRatio: Double = 1.38,
    private val requiredOutOfRangeMs: Long = 8_000,
    private val maxSampleGapMs: Long = 2_000,
) {
    private var baselineScale: Double = 0.0
    private var outOfRangeSinceMs: Long? = null
    private var lastSampleMs: Long? = null

    var drifting: Boolean = false
        private set

    fun reset(baselineTorsoScale: Double) {
        baselineScale = baselineTorsoScale
        outOfRangeSinceMs = null
        lastSampleMs = null
        drifting = false
    }

    fun observe(torsoScale: Double?, nowMs: Long): Boolean {
        if (drifting) return true
        if (baselineScale <= 0 || torsoScale == null || !torsoScale.isFinite() || torsoScale <= 0) {
            outOfRangeSinceMs = null
            lastSampleMs = null
            return false
        }
        val previous = lastSampleMs
        lastSampleMs = nowMs
        if (previous != null && nowMs - previous > maxSampleGapMs) {
            outOfRangeSinceMs = null
        }
        val ratio = torsoScale / baselineScale
        if (ratio in lowRatio..highRatio) {
            outOfRangeSinceMs = null
            return false
        }
        val since = outOfRangeSinceMs ?: nowMs.also { outOfRangeSinceMs = it }
        if (nowMs - since >= requiredOutOfRangeMs) drifting = true
        return drifting
    }
}
