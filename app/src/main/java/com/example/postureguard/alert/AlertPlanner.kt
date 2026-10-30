package com.example.postureguard.alert

import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState

data class AlertPolicy(
    val soundEnabled: Boolean = false,
    val speechEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val mildHoldDurationMs: Long = 5_000,
    val severeHoldDurationMs: Long = 3_000,
    val repeatIntervalMs: Long = 10_000,
    val recoveryHoldDurationMs: Long = 4_000,
)

enum class AlertChannel {
    SOUND,
    SPEECH,
    VIBRATION,
}

data class AlertRequest(
    val condition: PostureCondition,
    val severity: ConditionSeverity,
    val message: String,
    val channels: Set<AlertChannel>,
    val timestampMs: Long,
)

/**
 * Decides when a confirmed abnormal state deserves an interrupt. The state machine has
 * already applied hold durations; this planner repeats the reminder while the abnormal
 * state persists, throttled by [AlertPolicy.repeatIntervalMs], and resets once posture is
 * back to normal.
 */
class AlertPlanner(
    private val policy: AlertPolicy,
    messages: Map<PostureCondition, String> = DEFAULT_MESSAGES,
) {
    private val lessonCopy = messages
    private var lastCondition: PostureCondition? = null
    private var lastAlertAtMs: Long? = null

    fun onSnapshot(snapshot: PostureSnapshot): AlertRequest? {
        if (snapshot.state == PostureState.NORMAL) {
            reset()
            return null
        }
        if (!snapshot.metricsValid || snapshot.recovering) return null
        if (!snapshot.state.isConfirmedAbnormal) return null
        val condition = snapshot.condition ?: return null
        val last = lastAlertAtMs
        val repeatingSameCondition = condition == lastCondition
        if (last != null && repeatingSameCondition && snapshot.timestampMs - last < policy.repeatIntervalMs) {
            return null
        }
        lastCondition = condition
        lastAlertAtMs = snapshot.timestampMs
        return AlertRequest(
            condition = condition,
            severity = snapshot.severity,
            message = lessonCopy[condition] ?: DEFAULT_MESSAGES.getValue(condition),
            channels = enabledChannels(),
            timestampMs = snapshot.timestampMs,
        )
    }

    fun reset() {
        lastCondition = null
        lastAlertAtMs = null
    }

    private fun enabledChannels(): Set<AlertChannel> = buildSet {
        if (policy.soundEnabled) add(AlertChannel.SOUND)
        if (policy.speechEnabled) add(AlertChannel.SPEECH)
        if (policy.vibrationEnabled) add(AlertChannel.VIBRATION)
    }

    companion object {
        val DEFAULT_MESSAGES: Map<PostureCondition, String> = mapOf(
            PostureCondition.FORWARD_HEAD to "头肩前倾了，请坐直。",
            PostureCondition.HEAD_DOWN to "低头了，请抬头。",
            PostureCondition.HEAD_DOWN_FORWARD to "驼背低头了，请抬头坐直。",
            PostureCondition.TORSO_LEAN to "驼背弯腰了，请坐直。",
        )
    }
}
