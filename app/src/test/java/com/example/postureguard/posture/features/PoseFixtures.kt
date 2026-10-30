package com.example.postureguard.posture.features

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.Landmark
import com.example.postureguard.posture.model.LandmarkType
import com.example.postureguard.posture.model.PoseLandmarks

/** Builds 33-point pose fixtures for tests; unset points keep zero confidence. */
object PoseFixtures {

    fun pose(
        nose: Pair<Float, Float>? = 0.52f to 0.40f,
        eye: Pair<Float, Float>? = 0.49f to 0.39f,
        ear: Pair<Float, Float>? = 0.47f to 0.40f,
        mouth: Pair<Float, Float>? = 0.51f to 0.45f,
        shoulder: Pair<Float, Float>? = 0.47f to 0.52f,
        hip: Pair<Float, Float>? = 0.47f to 0.70f,
        confidence: Float = 0.9f,
        mirror: Boolean = false,
        knee: Pair<Float, Float>? = 0.47f to 0.88f,
        visibleSide: BodySide = BodySide.LEFT,
    ): PoseLandmarks {
        val points = MutableList(PoseLandmarks.LANDMARK_COUNT) {
            Landmark(0.5f, 0.5f, 0f, 0f, 0f)
        }
        fun put(type: LandmarkType, pair: Pair<Float, Float>?, sideMirror: Boolean = mirror) {
            if (pair == null) return
            val x = if (sideMirror) 1f - pair.first else pair.first
            points[type.index] = Landmark(x, pair.second, 0f, confidence, confidence)
        }
        put(LandmarkType.NOSE, nose)
        put(LandmarkType.LEFT_EYE, eye)
        put(LandmarkType.LEFT_EAR, ear)
        put(LandmarkType.MOUTH_LEFT, mouth)
        put(LandmarkType.LEFT_SHOULDER, shoulder)
        put(LandmarkType.LEFT_HIP, hip)
        put(LandmarkType.LEFT_KNEE, knee)
        put(LandmarkType.RIGHT_EYE, eye, sideMirror = !mirror)
        put(LandmarkType.MOUTH_RIGHT, mouth, sideMirror = !mirror)
        // The opposite side is intentionally low confidence so the tracker picks LEFT.
        put(LandmarkType.RIGHT_EAR, ear, sideMirror = !mirror)
        put(LandmarkType.RIGHT_SHOULDER, shoulder, sideMirror = !mirror)
        put(LandmarkType.RIGHT_HIP, hip, sideMirror = !mirror)
        val hiddenSide = if (visibleSide == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
        listOf(hiddenSide.ear, hiddenSide.shoulder, hiddenSide.hip).forEach { type ->
            points[type.index] = points[type.index].copy(visibility = 0.05f, presence = 0.05f)
        }
        return PoseLandmarks(points)
    }
}
