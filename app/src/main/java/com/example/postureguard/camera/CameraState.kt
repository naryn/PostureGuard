package com.example.postureguard.camera

import com.example.postureguard.posture.model.PoseLandmarks

sealed interface CameraStatus {
    data object Initializing : CameraStatus
    data object Running : CameraStatus
    data class Error(val message: String) : CameraStatus
    data object Stopped : CameraStatus
}

data class CameraFrameResult(
    val pose: PoseLandmarks,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val mirrored: Boolean,
    val inferenceLatencyMs: Long,
    val frameIntervalMs: Long,
    val droppedFrames: Long,
) {
    val imageAspectRatio: Double
        get() = if (imageWidth <= 0 || imageHeight <= 0) 1.0 else imageWidth.toDouble() / imageHeight
}
