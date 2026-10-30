package com.example.postureguard.posture.model

/** Geometry of one valid frame, in aspect-corrected normalized units. */
data class RawMetrics(
    val forwardOffset: Double,
    val headAngleDeg: Double,
    val torsoAngleDeg: Double,
    val torsoScale: Double,
)

/** Change relative to the personal baseline. Positive = more of the named problem. */
data class PostureDeltas(
    val forwardHead: Double,
    val headDown: Double,
    val torsoLean: Double,
)

data class ConditionLevels(
    val forwardHead: ConditionSeverity = ConditionSeverity.NONE,
    val headDown: ConditionSeverity = ConditionSeverity.NONE,
    val torsoLean: ConditionSeverity = ConditionSeverity.NONE,
)

enum class ConditionSeverity {
    NONE,
    MILD,
    SEVERE,
    ;

    fun atLeast(other: ConditionSeverity): Boolean = rank >= other.rank

    val rank: Int get() = ordinal
}

enum class PostureCondition(val label: String) {
    FORWARD_HEAD("头部前倾"),
    HEAD_DOWN("低头"),
    HEAD_DOWN_FORWARD("埋头"),
    TORSO_LEAN("躯干前倾"),
}

enum class PostureState(val label: String) {
    NOT_READY("准备中"),
    NORMAL("姿态自然"),
    SUSPECTED_FORWARD_HEAD("注意头部前移"),
    FORWARD_HEAD("头部前倾"),
    SUSPECTED_HEAD_DOWN("注意低头"),
    HEAD_DOWN("低头"),
    SUSPECTED_HEAD_DOWN_FORWARD("注意埋头"),
    HEAD_DOWN_FORWARD("埋头"),
    SUSPECTED_TORSO_LEAN("注意躯干前倾"),
    TORSO_LEAN("躯干前倾"),
    LOW_CONFIDENCE("检测质量不足"),
    PERSON_NOT_FOUND("未检测到人体"),
    PAUSED("已暂停"),
    ;

    val isAbnormal: Boolean
        get() = this in setOf(
            FORWARD_HEAD,
            HEAD_DOWN,
            HEAD_DOWN_FORWARD,
            TORSO_LEAN,
            SUSPECTED_FORWARD_HEAD,
            SUSPECTED_HEAD_DOWN,
            SUSPECTED_HEAD_DOWN_FORWARD,
            SUSPECTED_TORSO_LEAN,
        )

    val isConfirmedAbnormal: Boolean
        get() = this in setOf(FORWARD_HEAD, HEAD_DOWN, HEAD_DOWN_FORWARD, TORSO_LEAN)

    val condition: PostureCondition?
        get() = when (this) {
            SUSPECTED_FORWARD_HEAD, FORWARD_HEAD -> PostureCondition.FORWARD_HEAD
            SUSPECTED_HEAD_DOWN, HEAD_DOWN -> PostureCondition.HEAD_DOWN
            SUSPECTED_HEAD_DOWN_FORWARD, HEAD_DOWN_FORWARD -> PostureCondition.HEAD_DOWN_FORWARD
            SUSPECTED_TORSO_LEAN, TORSO_LEAN -> PostureCondition.TORSO_LEAN
            else -> null
        }
}

enum class QualityIssue(val label: String) {
    PERSON_NOT_FOUND("未检测到人体"),
    LOW_CONFIDENCE("关键点置信度不足"),
    BODY_PARTS_OUT_OF_FRAME("头、肩或髋超出画面"),
    TORSO_SCALE_INVALID("人体过小或距离过远"),
    HEAD_BELOW_HIP("人体方向异常"),
    SIDE_UNSTABLE("左右侧关键点不稳定"),
}

data class LandmarkQuality(
    val valid: Boolean,
    val score: Double,
    val selectedSide: BodySide?,
    val issues: Set<QualityIssue> = emptySet(),
) {
    companion object {
        val NONE = LandmarkQuality(false, 0.0, null, setOf(QualityIssue.PERSON_NOT_FOUND))
    }
}

data class PostureSnapshot(
    val state: PostureState,
    val severity: ConditionSeverity,
    val condition: PostureCondition?,
    val deltas: PostureDeltas?,
    val quality: LandmarkQuality,
    val heldMs: Long,
    val timestampMs: Long,
    val metricsValid: Boolean,
    val recovering: Boolean = false,
    val levels: ConditionLevels = ConditionLevels(),
)
