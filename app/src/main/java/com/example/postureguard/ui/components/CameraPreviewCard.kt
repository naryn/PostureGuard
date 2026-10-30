package com.example.postureguard.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.camera.CameraStatus
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.vision.PoseEngineState

@Composable
fun CameraPreviewCard(
    active: Boolean,
    targetFps: Int,
    modifier: Modifier = Modifier,
    showLandmarks: Boolean = true,
    onFrame: (CameraFrameResult) -> Unit,
    onCameraStatus: (CameraStatus) -> Unit = {},
    onEngineState: (PoseEngineState) -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Ink,
    ) {
        PoseCameraView(
            active = active,
            targetFps = targetFps,
            modifier = Modifier.fillMaxSize(),
            showLandmarks = showLandmarks,
            onFrame = onFrame,
            onCameraStatus = onCameraStatus,
            onEngineState = onEngineState,
        )
    }
}
