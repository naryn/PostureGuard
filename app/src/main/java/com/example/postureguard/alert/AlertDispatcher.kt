package com.example.postureguard.alert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Plays the local alert channels selected by [AlertPlanner]. The latest speech request is
 * queued until the TTS engine finishes initializing; an unavailable engine never blocks monitoring.
 */
class AlertDispatcher(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
    }.getOrNull()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile
    private var ttsReady = false

    private var tts: TextToSpeech? = null
    private val speechLock = Any()
    private var pendingSpeech: String? = null

    init {
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val language = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    ttsReady = language != null &&
                        language != TextToSpeech.LANG_MISSING_DATA &&
                        language != TextToSpeech.LANG_NOT_SUPPORTED
                    if (ttsReady) {
                        val queued = synchronized(speechLock) {
                            pendingSpeech.also { pendingSpeech = null }
                        }
                        queued?.let(::speak)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "TTS unavailable", it) }
    }

    fun dispatch(request: AlertRequest) {
        if (AlertChannel.SOUND in request.channels) playTone()
        if (AlertChannel.VIBRATION in request.channels) vibrate()
        if (AlertChannel.SPEECH in request.channels) speak(request.message)
    }

    private fun playTone() {
        runCatching {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_DURATION_MS)
        }.onFailure { Log.w(TAG, "Tone playback failed", it) }
    }

    private fun vibrate() {
        val target = vibrator ?: return
        if (!target.hasVibrator()) return
        runCatching {
            target.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, -1),
            )
        }.onFailure { Log.w(TAG, "Vibration failed", it) }
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            // The engine often finishes initializing moments after monitoring starts.
            synchronized(speechLock) { pendingSpeech = message }
            return
        }
        runCatching {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }.onFailure { Log.w(TAG, "Speech failed", it) }
    }

    override fun close() {
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { toneGenerator?.release() }
    }

    companion object {
        private const val TAG = "AlertDispatcher"
        private const val TONE_VOLUME = 70
        private const val TONE_DURATION_MS = 220
        private val VIBRATION_PATTERN = longArrayOf(0, 220, 140, 220)
    }
}
