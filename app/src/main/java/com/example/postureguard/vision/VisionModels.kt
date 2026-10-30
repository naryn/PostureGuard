package com.example.postureguard.vision

import com.example.postureguard.posture.model.Landmark
import com.example.postureguard.posture.model.PoseLandmarks

/** Pinned model metadata. The SHA-256 is also checked by scripts/verify_model_assets.sh. */
object VisionModelInfo {
    const val MODEL_ASSET = "pose_landmarker_lite.task"
    const val MODEL_NAME = "MediaPipe Pose Landmarker Lite (float16)"
    const val MODEL_VERSION = "pose_landmarker_lite-float16-1.0"
    const val MODEL_SHA256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
    const val SOURCE_URL =
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
    const val MIN_DETECTION_CONFIDENCE = 0.5f
    const val MIN_PRESENCE_CONFIDENCE = 0.5f
    const val MIN_TRACKING_CONFIDENCE = 0.5f
    const val MAX_POSES = 1
}

data class PoseDetection(
    val landmarks: PoseLandmarks,
    val timestampMs: Long,
    val inferenceLatencyMs: Long,
    val inputWidth: Int,
    val inputHeight: Int,
) {
    val hasPose: Boolean get() = !landmarks.isEmpty
}

data class LandmarkPoint(
    val index: Int,
    val x: Float,
    val y: Float,
    val confidence: Float,
) {
    fun toLandmark(): Landmark = Landmark(x, y, 0f, confidence, confidence)
}
