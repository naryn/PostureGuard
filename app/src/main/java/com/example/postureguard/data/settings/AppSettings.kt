package com.example.postureguard.data.settings

import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.SensitivityPreset

enum class AnalysisFrameRate(val fps: Int, val label: String) {
    POWER_SAVE(5, "省电"),
    AUTO(10, "自动"),
    SMOOTH(15, "流畅"),
}

data class AppSettings(
    val sensitivity: SensitivityPreset = SensitivityPreset.BALANCED,
    val soundEnabled: Boolean = true,
    val speechEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val analysisFrameRate: AnalysisFrameRate = AnalysisFrameRate.AUTO,
    val facingDirection: FacingDirection = FacingDirection.FACING_RIGHT,
    val alertCooldownMs: Long = 120_000,
    val mildHoldDurationMs: Long = 8_000,
    val onboardingCompleted: Boolean = false,
) {
    val targetFps: Int get() = analysisFrameRate.fps
}
