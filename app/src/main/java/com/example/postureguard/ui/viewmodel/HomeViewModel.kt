package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import com.example.postureguard.data.repository.DailySummary
import com.example.postureguard.data.settings.AppSettings
import com.example.postureguard.monitoring.MonitorPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val todaySummary = container.monitoringRepository.observeToday(today).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DailySummary.empty(today),
    )

    val calibration = container.calibrationRepository.activeProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val monitorPhase = container.monitorController.state
        .map { it.phase }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorPhase.IDLE)

    fun completeOnboarding() {
        viewModelScope.launch { container.settingsRepository.setOnboardingCompleted(true) }
    }
}
