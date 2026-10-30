package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.data.settings.AppSettings
import com.example.postureguard.monitoring.MonitorPhase
import com.example.postureguard.posture.model.CalibrationProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalibrationSnapshot(
    val loaded: Boolean = false,
    val profile: CalibrationProfile? = null,
)

class MonitorViewModel(private val container: AppContainer) : ViewModel() {

    private val controller = container.monitorController
    private val settingsRepository = container.settingsRepository

    val state = controller.state

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val calibration = container.calibrationRepository.activeProfile
        .map { CalibrationSnapshot(loaded = true, profile = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalibrationSnapshot(),
        )

    fun ensureStarted(profile: CalibrationProfile, settings: AppSettings) {
        if (controller.state.value.phase == MonitorPhase.FINISHED) controller.reset()
        if (controller.state.value.phase == MonitorPhase.IDLE) controller.start(profile, settings)
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun stop() = controller.stop()

    fun reset() = controller.reset()

    fun onFrame(result: CameraFrameResult) = controller.onFrame(result)

    fun onCameraError(message: String) {
        if (controller.state.value.phase == MonitorPhase.RUNNING) {
            controller.onCameraUnavailable(message)
        }
    }

    fun updateSoundEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(value) }
    }

    fun updateSpeechEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSpeechEnabled(value) }
    }

    fun updateVibrationEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(value) }
    }

    override fun onCleared() {
        if (controller.state.value.isActive) controller.pause()
    }
}
