package com.example.postureguard.ui.util

import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.FacingDirection

fun facingLabel(direction: FacingDirection): String = when (direction) {
    FacingDirection.FACING_RIGHT -> "面向右侧"
    FacingDirection.FACING_LEFT -> "面向左侧"
}

fun severityLabel(severity: ConditionSeverity): String = when (severity) {
    ConditionSeverity.SEVERE -> "明显"
    ConditionSeverity.MILD -> "轻度"
    ConditionSeverity.NONE -> "—"
}
