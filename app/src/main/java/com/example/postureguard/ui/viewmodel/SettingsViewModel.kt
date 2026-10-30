package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import com.example.postureguard.data.settings.AnalysisFrameRate
import com.example.postureguard.data.settings.AppSettings
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.SensitivityPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val hasCalibration = container.calibrationRepository.activeProfile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun updateSensitivity(value: SensitivityPreset) =
        launchSetting { setSensitivity(value) }

    fun updateSoundEnabled(value: Boolean) = launchSetting { setSoundEnabled(value) }

    fun updateSpeechEnabled(value: Boolean) = launchSetting { setSpeechEnabled(value) }

    fun updateVibrationEnabled(value: Boolean) = launchSetting { setVibrationEnabled(value) }

    fun updateKeepScreenOn(value: Boolean) = launchSetting { setKeepScreenOn(value) }

    fun updateFrameRate(value: AnalysisFrameRate) = launchSetting { setAnalysisFrameRate(value) }

    fun updateFacingDirection(value: FacingDirection) = launchSetting { setFacingDirection(value) }

    fun updateAlertCooldownMs(value: Long) = launchSetting { setAlertCooldownMs(value) }

    fun updateMildHoldDurationMs(value: Long) = launchSetting { setMildHoldDurationMs(value) }

    fun deleteHistory() {
        viewModelScope.launch {
            runCatching { container.monitoringRepository.clearAll() }
                .onSuccess { _message.value = "历史统计已删除" }
                .onFailure { _message.value = "删除失败：${it.message ?: "未知错误"}" }
        }
    }

    fun deleteCalibration() {
        viewModelScope.launch {
            runCatching { container.calibrationRepository.clear() }
                .onSuccess { _message.value = "校准数据已删除" }
                .onFailure { _message.value = "删除失败：${it.message ?: "未知错误"}" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun launchSetting(block: suspend com.example.postureguard.data.settings.SettingsRepository.() -> Unit) {
        viewModelScope.launch { container.settingsRepository.block() }
    }
}
