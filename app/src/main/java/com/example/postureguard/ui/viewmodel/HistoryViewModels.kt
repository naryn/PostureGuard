package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(container: AppContainer) : ViewModel() {

    val sessions = container.monitoringRepository.observeRecentSessions(100).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}

class HistoryDetailViewModel(container: AppContainer, sessionId: String) : ViewModel() {

    val session = container.monitoringRepository.observeSession(sessionId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val events = container.monitoringRepository.observeEvents(sessionId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}
