package com.example.postureguard.alert

import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState

data class AlertPolicy(
    val soundEnabled: Boolean = true,
    val speechEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val mildHoldDurationMs: Long = 8_000,
    val severeHoldDurationMs: Long = 3_000,
    val cooldownMs: Long = 120_000,
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
 * already applied hold durations; this planner only handles cooldown, channel selection
 * and the user facing copy.
 */
class AlertPlanner(
    private val policy: AlertPolicy,
    messages: Map<PostureCondition, String> = DEFAULT_MESSAGES,
) {
    private val lessonCopy = messages
    private val lastAlertAtMs = mutableMapOf<PostureCondition, Long>()

    fun onSnapshot(snapshot: PostureSnapshot): AlertRequest? {
        if (snapshot.state == PostureState.NORMAL) {
            lastAlertAtMs.clear()
            return null
        }
        if (!snapshot.metricsValid || snapshot.recovering) return null
        if (!snapshot.state.isConfirmedAbnormal) return null
        val condition = snapshot.condition ?: return null
        val last = lastAlertAtMs[condition]
        if (last != null && snapshot.timestampMs - last < policy.cooldownMs) return null
        lastAlertAtMs[condition] = snapshot.timestampMs
        return AlertRequest(
            condition = condition,
            severity = snapshot.severity,
            message = lessonCopy[condition] ?: DEFAULT_MESSAGES.getValue(condition),
            channels = enabledChannels(),
            timestampMs = snapshot.timestampMs,
        )
    }

    fun reset() = lastAlertAtMs.clear()

    private fun enabledChannels(): Set<AlertChannel> = buildSet {
        if (policy.soundEnabled) add(AlertChannel.SOUND)
        if (policy.speechEnabled) add(AlertChannel.SPEECH)
        if (policy.vibrationEnabled) add(AlertChannel.VIBRATION)
    }

    companion object {
        val DEFAULT_MESSAGES: Map<PostureCondition, String> = mapOf(
            PostureCondition.FORWARD_HEAD to "头部有些前倾，试着回到舒适坐姿。",
            PostureCondition.HEAD_DOWN to "注意抬头，放松颈部。",
            PostureCondition.HEAD_DOWN_FORWARD to "头部前倾并低头，慢慢抬起头来吧。",
            PostureCondition.TORSO_LEAN to "身体持续前倾，可以调整一下坐姿。",
        )
    }
}
