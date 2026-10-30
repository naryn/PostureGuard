package com.example.postureguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.postureguard.ui.screens.AboutScreen
import com.example.postureguard.ui.screens.CalibrationScreen
import com.example.postureguard.ui.screens.CameraSetupScreen
import com.example.postureguard.ui.screens.HistoryDetailScreen
import com.example.postureguard.ui.screens.HistoryScreen
import com.example.postureguard.ui.screens.HomeScreen
import com.example.postureguard.ui.screens.MonitorScreen
import com.example.postureguard.ui.screens.PrivacyScreen
import com.example.postureguard.ui.screens.ReportsScreen
import com.example.postureguard.ui.screens.SettingsScreen
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.viewmodel.CalibrationViewModel
import com.example.postureguard.ui.viewmodel.CameraSetupViewModel
import com.example.postureguard.ui.viewmodel.HistoryDetailViewModel
import com.example.postureguard.ui.viewmodel.HistoryViewModel
import com.example.postureguard.ui.viewmodel.HomeViewModel
import com.example.postureguard.ui.viewmodel.MonitorViewModel
import com.example.postureguard.ui.viewmodel.ReportsViewModel
import com.example.postureguard.ui.viewmodel.SettingsViewModel
import com.example.postureguard.ui.viewmodel.rememberAppContainer
import com.example.postureguard.ui.viewmodel.simpleViewModelFactory

object Routes {
    const val HOME = "home"
    const val CAMERA_SETUP = "camera-setup"
    const val CALIBRATION = "calibration"
    const val MONITOR = "monitor"
    const val SETTINGS = "settings"
    const val REPORTS = "reports"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{sessionId}"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"

    fun historyDetail(sessionId: String): String = "history/$sessionId"
}

@Composable
fun PostureGuardRoot() {
    val navController = rememberNavController()
    val container = rememberAppContainer()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier
            .fillMaxSize()
            .background(Mist),
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { HomeViewModel(container) }
                },
            )
            HomeScreen(
                viewModel = viewModel,
                onStartMonitoring = { navController.navigate(Routes.MONITOR) },
                onOpenMonitor = {
                    navController.navigate(Routes.MONITOR) { launchSingleTop = true }
                },
                onCameraSetup = { navController.navigate(Routes.CAMERA_SETUP) },
                onCalibration = { navController.navigate(Routes.CALIBRATION) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onReports = { navController.navigate(Routes.REPORTS) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }

        composable(Routes.CAMERA_SETUP) {
            val viewModel: CameraSetupViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { CameraSetupViewModel(container) }
                },
            )
            CameraSetupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.CALIBRATION) {
            val viewModel: CalibrationViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { CalibrationViewModel(container) }
                },
            )
            CalibrationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.MONITOR) {
            val viewModel: MonitorViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { MonitorViewModel(container) }
                },
            )
            MonitorScreen(
                viewModel = viewModel,
                onExit = { navController.popBackStack() },
                onRecalibrate = {
                    navController.navigate(Routes.CALIBRATION) {
                        popUpTo(Routes.MONITOR) { inclusive = true }
                    }
                },
                onOpenReports = {
                    navController.navigate(Routes.REPORTS) { launchSingleTop = true }
                },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.historyDetail(sessionId))
                },
                onNeedCalibration = { navController.navigate(Routes.CALIBRATION) },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { SettingsViewModel(container) }
                },
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.REPORTS) {
            val viewModel: ReportsViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { ReportsViewModel(container) }
                },
            )
            ReportsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            val viewModel: HistoryViewModel = viewModel(
                factory = remember(container) {
                    simpleViewModelFactory { HistoryViewModel(container) }
                },
            )
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.historyDetail(sessionId))
                },
            )
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val viewModel: HistoryDetailViewModel = viewModel(
                factory = remember(container, sessionId) {
                    simpleViewModelFactory { HistoryDetailViewModel(container, sessionId) }
                },
            )
            HistoryDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
