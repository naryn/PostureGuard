package com.example.postureguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.postureguard.AppContainer
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.data.settings.AppSettings
import com.example.postureguard.data.settings.CameraLens
import com.example.postureguard.posture.features.PostureFeaturePipeline
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.posture.model.QualityIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraProbeState(
    val hasPerson: Boolean = false,
    val qualityValid: Boolean = false,
    val faceVisible: Boolean = false,
    val selectedSide: BodySide? = null,
    val issues: Set<QualityIssue> = emptySet(),
    val mirrored: Boolean = false,
)

class CameraSetupViewModel(private val container: AppContainer) : ViewModel() {

    private val pipeline = PostureFeaturePipeline()
    private val _probe = MutableStateFlow(CameraProbeState())
    val probe = _probe.asStateFlow()

    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun onFrame(result: CameraFrameResult) {
        val facing = settings.value.facingDirection
        val feature = pipeline.process(
            PoseFrame(result.pose, result.timestampMs, result.imageWidth, result.imageHeight),
            facing,
        )
        _probe.update {
            CameraProbeState(
                hasPerson = feature.side != null,
                qualityValid = feature.quality.valid,
                faceVisible = feature.metrics?.faceMeasured == true,
                selectedSide = feature.quality.selectedSide,
                issues = feature.quality.issues,
                mirrored = result.mirrored,
            )
        }
    }

    fun setFacing(direction: FacingDirection) {
        pipeline.reset()
        viewModelScope.launch { container.settingsRepository.setFacingDirection(direction) }
    }

    fun setLens(lens: CameraLens) {
        pipeline.reset()
        _probe.value = CameraProbeState()
        viewModelScope.launch { container.settingsRepository.setCameraLens(lens) }
    }
}
