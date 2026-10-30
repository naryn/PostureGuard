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

    suspend fun setAlertCooldownMs(value: Long) = edit(Keys.COOLDOWN_MS, value)

    suspend fun setMildHoldDurationMs(value: Long) = edit(Keys.MILD_HOLD_MS, value)

    suspend fun setOnboardingCompleted(value: Boolean) = edit(Keys.ONBOARDING, value)

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        sensitivity = readEnum(Keys.SENSITIVITY, SensitivityPreset.BALANCED),
        soundEnabled = this[Keys.SOUND] ?: true,
        speechEnabled = this[Keys.SPEECH] ?: false,
        vibrationEnabled = this[Keys.VIBRATION] ?: true,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
        analysisFrameRate = readEnum(Keys.FRAME_RATE, AnalysisFrameRate.AUTO),
        facingDirection = readEnum(Keys.FACING, FacingDirection.FACING_RIGHT),
        alertCooldownMs = (this[Keys.COOLDOWN_MS] ?: 120_000L).coerceIn(15_000L, 900_000L),
        mildHoldDurationMs = (this[Keys.MILD_HOLD_MS] ?: 8_000L).coerceIn(3_000L, 60_000L),
        onboardingCompleted = this[Keys.ONBOARDING] ?: false,
    )

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
        val COOLDOWN_MS = longPreferencesKey("alert_cooldown_ms")
        val MILD_HOLD_MS = longPreferencesKey("mild_hold_ms")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
    }
}
