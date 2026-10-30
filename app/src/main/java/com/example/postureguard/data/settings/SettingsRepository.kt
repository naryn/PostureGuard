package com.example.postureguard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.SensitivityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "posture_guard_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { it.toAppSettings() }

    suspend fun settingsOnce(): AppSettings = settings.first()

    suspend fun setSensitivity(value: SensitivityPreset) = edit(Keys.SENSITIVITY, value.name)

    suspend fun setSoundEnabled(value: Boolean) = edit(Keys.SOUND, value)

    suspend fun setSpeechEnabled(value: Boolean) = edit(Keys.SPEECH, value)

    suspend fun setVibrationEnabled(value: Boolean) = edit(Keys.VIBRATION, value)

    suspend fun setKeepScreenOn(value: Boolean) = edit(Keys.KEEP_SCREEN_ON, value)

    suspend fun setAnalysisFrameRate(value: AnalysisFrameRate) = edit(Keys.FRAME_RATE, value.name)

    suspend fun setFacingDirection(value: FacingDirection) = edit(Keys.FACING, value.name)

    suspend fun setCameraLens(value: CameraLens) = edit(Keys.CAMERA_LENS, value.name)

    suspend fun setAlertRepeatIntervalMs(value: Long) = edit(Keys.REPEAT_INTERVAL_MS, value)

    suspend fun setMildHoldDurationMs(value: Long) = edit(Keys.MILD_HOLD_MS, value)

    suspend fun setOnboardingCompleted(value: Boolean) = edit(Keys.ONBOARDING, value)

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        sensitivity = readEnum(Keys.SENSITIVITY, SensitivityPreset.BALANCED),
        soundEnabled = this[Keys.SOUND] ?: false,
        speechEnabled = this[Keys.SPEECH] ?: true,
        vibrationEnabled = this[Keys.VIBRATION] ?: true,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
        analysisFrameRate = readEnum(Keys.FRAME_RATE, AnalysisFrameRate.AUTO),
        facingDirection = readEnum(Keys.FACING, FacingDirection.FACING_RIGHT),
        cameraLens = readEnum(Keys.CAMERA_LENS, CameraLens.BACK),
        alertRepeatIntervalMs = resolveRepeatIntervalMs(),
        mildHoldDurationMs = (this[Keys.MILD_HOLD_MS] ?: 5_000L).coerceIn(3_000L, 60_000L),
        onboardingCompleted = this[Keys.ONBOARDING] ?: false,
    )

    private fun Preferences.resolveRepeatIntervalMs(): Long {
        // Older builds stored a 1-5 minute cooldown under a different key; those values
        // are no longer meaningful for persistent reminders, so fall back to the default.
        val stored = this[Keys.REPEAT_INTERVAL_MS]
            ?: this[Keys.LEGACY_COOLDOWN_MS]?.takeIf { it in 3_000L..30_000L }
            ?: DEFAULT_REPEAT_INTERVAL_MS
        return stored.coerceIn(3_000L, 60_000L)
    }

    private inline fun <reified T : Enum<T>> Preferences.readEnum(
        key: Preferences.Key<String>,
        fallback: T,
    ): T {
        val raw = this[key] ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private object Keys {
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val SOUND = booleanPreferencesKey("alert_sound")
        val SPEECH = booleanPreferencesKey("alert_speech")
        val VIBRATION = booleanPreferencesKey("alert_vibration")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val FRAME_RATE = stringPreferencesKey("analysis_frame_rate")
        val FACING = stringPreferencesKey("facing_direction")
        val CAMERA_LENS = stringPreferencesKey("camera_lens")
        val REPEAT_INTERVAL_MS = longPreferencesKey("alert_repeat_ms")
        val LEGACY_COOLDOWN_MS = longPreferencesKey("alert_cooldown_ms")
        val MILD_HOLD_MS = longPreferencesKey("mild_hold_ms")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
    }

    private companion object {
        const val DEFAULT_REPEAT_INTERVAL_MS = 10_000L
    }
}
