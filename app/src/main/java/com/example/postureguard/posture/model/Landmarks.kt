package com.example.postureguard.posture.model

/** MediaPipe Pose landmark indices, in the official 33-point order. */
enum class LandmarkType(val index: Int) {
    NOSE(0),
    LEFT_EYE_INNER(1),
    LEFT_EYE(2),
    LEFT_EYE_OUTER(3),
    RIGHT_EYE_INNER(4),
    RIGHT_EYE(5),
    RIGHT_EYE_OUTER(6),
    LEFT_EAR(7),
    RIGHT_EAR(8),
    MOUTH_LEFT(9),
    MOUTH_RIGHT(10),
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_PINKY(17),
    RIGHT_PINKY(18),
    LEFT_INDEX(19),
    RIGHT_INDEX(20),
    LEFT_THUMB(21),
    RIGHT_THUMB(22),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
    LEFT_HEEL(29),
    RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31),
    RIGHT_FOOT_INDEX(32),
}

data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f,
    val presence: Float = 1f,
) {
    val confidence: Float get() = minOf(visibility, presence)

    fun isUsable(minConfidence: Double): Boolean =
        confidence >= minConfidence &&
            x.isFinite() && y.isFinite() &&
            x in -0.25f..1.25f && y in -0.25f..1.25f
}

class PoseLandmarks(val points: List<Landmark>) {

    val isEmpty: Boolean get() = points.isEmpty() || points.all { it.confidence <= 0f }

    operator fun get(type: LandmarkType): Landmark? = points.getOrNull(type.index)

    companion object {
        const val LANDMARK_COUNT = 33
        val EMPTY = PoseLandmarks(emptyList())
    }
}

data class PoseFrame(
    val landmarks: PoseLandmarks,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    /** Width / height of the upright analysis image. */
    val aspectRatio: Double
        get() = if (imageWidth <= 0 || imageHeight <= 0) 1.0 else imageWidth.toDouble() / imageHeight

    companion object {
        val EMPTY = PoseFrame(PoseLandmarks.EMPTY, 0L, 0, 0)
    }
}

enum class BodySide {
    LEFT,
    RIGHT,
    ;

    val ear: LandmarkType
        get() = if (this == LEFT) LandmarkType.LEFT_EAR else LandmarkType.RIGHT_EAR

    val eye: LandmarkType
        get() = if (this == LEFT) LandmarkType.LEFT_EYE else LandmarkType.RIGHT_EYE

    val shoulder: LandmarkType
        get() = if (this == LEFT) LandmarkType.LEFT_SHOULDER else LandmarkType.RIGHT_SHOULDER

    val hip: LandmarkType
        get() = if (this == LEFT) LandmarkType.LEFT_HIP else LandmarkType.RIGHT_HIP

    val knee: LandmarkType
        get() = if (this == LEFT) LandmarkType.LEFT_KNEE else LandmarkType.RIGHT_KNEE
}

/**
 * Which way the user faces in the (upright, unmirrored analysis image) frame.
 * FACING_RIGHT means the user's body front points towards increasing x.
 */
enum class FacingDirection(val displayName: String) {
    FACING_LEFT("面向画面左侧"),
    FACING_RIGHT("面向画面右侧"),
    ;

    /** +1 when body forward equals image +x, -1 otherwise. */
    val forwardSign: Double get() = if (this == FACING_RIGHT) 1.0 else -1.0

    val opposite: FacingDirection
        get() = if (this == FACING_LEFT) FACING_RIGHT else FACING_LEFT
}
