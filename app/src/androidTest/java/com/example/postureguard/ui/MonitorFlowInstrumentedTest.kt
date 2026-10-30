package com.example.postureguard.ui

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.postureguard.MainActivity
import com.example.postureguard.PostureGuardApp
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.vision.VisionModelInfo
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real UI against the emulator's virtual camera, which contains no person.
 * This proves the honest "no detection" path and the pause/resume/stop lifecycle;
 * it is not a real-camera posture acceptance test.
 */
@RunWith(AndroidJUnit4::class)
class MonitorFlowInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun seedCalibration() = runBlocking {
        val container = (context.applicationContext as PostureGuardApp).container
        container.settingsRepository.setOnboardingCompleted(true)
        container.settingsRepository.setSoundEnabled(false)
        container.settingsRepository.setVibrationEnabled(false)
        container.calibrationRepository.save(
            CalibrationProfile(
                id = "ui-test-calibration",
                createdAt = System.currentTimeMillis(),
                facingDirection = FacingDirection.FACING_RIGHT,
                selectedBodySide = BodySide.RIGHT,
                forwardOffsetMedian = 0.0,
                headAngleMedianDeg = 0.0,
                torsoAngleMedianDeg = 0.0,
                torsoScaleMedian = 0.3,
                forwardOffsetMad = 0.02,
                headAngleMadDeg = 3.0,
                torsoAngleMadDeg = 2.0,
                qualityMedian = 0.91,
                validSampleRatio = 0.95,
                modelVersion = CalibrationProfile.stamp(VisionModelInfo.MODEL_VERSION),
                isActive = true,
            ),
        )
    }

    @Test
    fun monitorShowsHonestNoPersonStateAndLifecycle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("开始监测").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("开始监测").performClick()

            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("监测中").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(90_000) {
                compose.onAllNodesWithText("未检测到人体").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("姿态自然").assertCountEquals(0)

            compose.onNodeWithText("暂停").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("继续监测").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("继续监测").performScrollTo().performClick()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("监测中").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("结束监测").performScrollTo().performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("监测结束").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("本次记录已保存在本机").assertExists()
        }
    }
}
