package com.example.postureguard.data.settings

import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.SensitivityPreset

enum class AnalysisFrameRate(val fps: Int, val label: String) {
    POWER_SAVE(5, "省电"),
    AUTO(10, "自动"),
    SMOOTH(15, "流畅"),
}

enum class CameraLens(val label: String) {
    BACK("后置摄像头"),
    FRONT("前置摄像头"),
}

data class AppSettings(
    val sensitivity: SensitivityPreset = SensitivityPreset.BALANCED,
    val soundEnabled: Boolean = false,
    val speechEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val analysisFrameRate: AnalysisFrameRate = AnalysisFrameRate.AUTO,
    val facingDirection: FacingDirection = FacingDirection.FACING_RIGHT,
    val cameraLens: CameraLens = CameraLens.BACK,
    val alertRepeatIntervalMs: Long = 10_000,
    val mildHoldDurationMs: Long = 5_000,
    val onboardingCompleted: Boolean = false,
) {
    val targetFps: Int get() = analysisFrameRate.fps
}
