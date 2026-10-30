package com.example.postureguard.monitoring

import com.example.postureguard.alert.AlertRequest
import com.example.postureguard.posture.model.PostureSnapshot

enum class MonitorPhase {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
}

data class MonitorState(
    val phase: MonitorPhase = MonitorPhase.IDLE,
    val sessionId: String? = null,
    val startedAtMs: Long? = null,
    val elapsedMs: Long = 0L,
    val snapshot: PostureSnapshot? = null,
    val lastAlert: AlertRequest? = null,
    val lastAlertAtMs: Long? = null,
    val cameraMoved: Boolean = false,
    val effectiveFps: Float = 0f,
    val inferenceLatencyMs: Long = 0L,
    val droppedFrames: Long = 0L,
    val calibrationProfileId: String? = null,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = phase == MonitorPhase.RUNNING || phase == MonitorPhase.PAUSED
}
