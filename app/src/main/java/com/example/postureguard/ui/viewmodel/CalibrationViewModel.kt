package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.data.settings.CameraLens
import com.example.postureguard.posture.calibration.CalibrationAccumulator
import com.example.postureguard.posture.calibration.CalibrationResult
import com.example.postureguard.posture.features.PostureFeaturePipeline
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.vision.VisionModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalibrationUiState(
    val facing: FacingDirection = FacingDirection.FACING_RIGHT,
    val lens: CameraLens = CameraLens.BACK,
    val liveQualityValid: Boolean = false,
    val liveSide: BodySide? = null,
    val running: Boolean = false,
    val progress: Float = 0f,
    val validRatio: Double = 0.0,
    val validSamples: Int = 0,
    val inWarmup: Boolean = false,
    val result: CalibrationResult? = null,
    val saved: Boolean = false,
    val saveError: String? = null,
)

class CalibrationViewModel(private val container: AppContainer) : ViewModel() {

    private val pipeline = PostureFeaturePipeline()
    private val _state = MutableStateFlow(CalibrationUiState())
    val state = _state.asStateFlow()

    private var accumulator: CalibrationAccumulator? = null

    val existingProfile = container.calibrationRepository.activeProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.settingsOnce()
            _state.update {
                it.copy(facing = settings.facingDirection, lens = settings.cameraLens)
            }
        }
    }

    fun onFrame(result: CameraFrameResult) {
        val current = _state.value
        val feature = pipeline.process(
            PoseFrame(result.pose, result.timestampMs, result.imageWidth, result.imageHeight),
            current.facing,
        )
        _state.update {
            it.copy(
                liveQualityValid = feature.quality.valid,
                liveSide = feature.quality.selectedSide,
            )
        }
        val active = accumulator ?: return
        val progress = active.addSample(feature.metrics, feature.quality)
        _state.update {
            it.copy(
                progress = progress.progress,
                validRatio = progress.validRatio,
                validSamples = progress.validSamples,
                inWarmup = progress.inWarmup,
            )
        }
        if (progress.progress >= 1f) {
            finishCalibration()
        }
    }

    fun start() {
        val current = _state.value
        val side = current.liveSide ?: return
        if (!current.liveQualityValid) return
        pipeline.reset()
        accumulator = CalibrationAccumulator(
            facingDirection = current.facing,
            side = side,
            modelVersion = CalibrationProfile.stamp(VisionModelInfo.MODEL_VERSION),
        )
        _state.update {
            it.copy(
                running = true,
                progress = 0f,
                validRatio = 0.0,
                validSamples = 0,
                inWarmup = true,
                result = null,
                saved = false,
                saveError = null,
            )
        }
    }

    fun tick() {
        val active = accumulator ?: return
        if (!active.isFinished) return
        finishCalibration()
    }

    fun retry() {
        accumulator = null
        _state.update {
            it.copy(
                running = false,
                progress = 0f,
                validRatio = 0.0,
                validSamples = 0,
                inWarmup = false,
                result = null,
                saved = false,
                saveError = null,
            )
        }
    }

    fun retrySave() {
        val success = _state.value.result as? CalibrationResult.Success ?: return
        save(success)
    }

    private fun finishCalibration() {
        val active = accumulator ?: return
        accumulator = null
        val result = active.finish()
        _state.update {
            it.copy(
                running = false,
                progress = 1f,
                result = result,
            )
        }
        if (result is CalibrationResult.Success) save(result)
    }

    private fun save(result: CalibrationResult.Success) {
        viewModelScope.launch {
            runCatching { container.calibrationRepository.save(result.profile) }
                .onSuccess { _state.update { it.copy(saved = true, saveError = null) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saved = false,
                            saveError = "保存失败：${error.message ?: "未知错误"}",
                        )
                    }
                }
        }
    }
}
