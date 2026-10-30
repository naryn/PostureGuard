package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import com.example.postureguard.data.repository.DailySummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class ReportsViewModel(container: AppContainer) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val todaySummary = container.monitoringRepository.observeToday(today).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DailySummary.empty(today),
    )

    val todayHours = container.monitoringRepository.observeHours(today).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val lastSevenDays = container.monitoringRepository.observeDays(today.minusDays(6)).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val todayDate: LocalDate get() = today
}
