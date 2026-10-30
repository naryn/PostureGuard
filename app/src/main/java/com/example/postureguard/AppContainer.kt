package com.example.postureguard

import android.content.Context
import com.example.postureguard.alert.AlertDispatcher
import com.example.postureguard.data.db.PostureDatabase
import com.example.postureguard.data.repository.CalibrationRepository
import com.example.postureguard.data.repository.MonitoringRepository
import com.example.postureguard.data.settings.SettingsRepository
import com.example.postureguard.monitoring.MonitorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency graph for the single-process app. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: PostureDatabase = PostureDatabase.get(appContext)
    val monitoringRepository = MonitoringRepository(database)
    val calibrationRepository = CalibrationRepository(database.calibrationDao())
    val settingsRepository = SettingsRepository(appContext)
    private val alertDispatcher = AlertDispatcher(appContext)
    val monitorController = MonitorController(
        context = appContext,
        settingsRepository = settingsRepository,
        monitoringRepository = monitoringRepository,
        alertDispatcher = alertDispatcher,
    )

    init {
        appScope.launch {
            runCatching { monitoringRepository.finalizeAbandonedSessions() }
        }
    }

    fun shutdown() {
        monitorController.close()
        alertDispatcher.close()
    }
}
