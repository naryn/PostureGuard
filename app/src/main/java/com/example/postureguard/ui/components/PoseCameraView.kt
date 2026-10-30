package com.example.postureguard.ui.components

import android.os.Handler
import android.os.Looper
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.camera.CameraSession
import com.example.postureguard.camera.CameraStatus
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.SurfaceWhite
import com.example.postureguard.vision.PoseEngineHost
import com.example.postureguard.vision.PoseEngineState

/**
 * Camera preview + real-time pose overlay. While the engine loads or fails, the preview is
 * dimmed and an explicit "detection unavailable" message is shown — never fake landmarks.
 */
@Composable
fun PoseCameraView(
    active: Boolean,
    targetFps: Int,
    modifier: Modifier = Modifier,
    showLandmarks: Boolean = true,
    onFrame: (CameraFrameResult) -> Unit,
    onCameraStatus: (CameraStatus) -> Unit = {},
    onEngineState: (PoseEngineState) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val engineHost = remember(context) {
        PoseEngineHost(context.applicationContext).also { it.load() }
    }
    val engineState by engineHost.state.collectAsState()
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnStatus by rememberUpdatedState(onCameraStatus)
    val currentOnEngineState by rememberUpdatedState(onEngineState)
    var frame by remember { mutableStateOf<CameraFrameResult?>(null) }
    var cameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Initializing) }

    LaunchedEffect(engineState) {
        currentOnEngineState(engineState)
    }
    LaunchedEffect(cameraStatus) {
        currentOnStatus(cameraStatus)
    }
    DisposableEffect(engineHost) {
        onDispose { engineHost.close() }
    }

    val engine = (engineState as? PoseEngineState.Ready)?.engine
    DisposableEffect(engine, active, lifecycleOwner) {
        var session: CameraSession? = null
        if (engine != null && active) {
            session = CameraSession(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                engine = engine,
                targetFps = targetFps,
                onFrame = { result ->
                    mainHandler.post {
                        frame = result
                        currentOnFrame(result)
                    }
                },
                onStatus = { status ->
                    mainHandler.post {
                        cameraStatus = status
                        currentOnStatus(status)
                    }
                },
                onError = { message ->
                    mainHandler.post {
                        val error = CameraStatus.Error(message)
                        cameraStatus = error
                        currentOnStatus(error)
                    }
                },
            )
            session.start()
        } else {
            mainHandler.post { cameraStatus = CameraStatus.Stopped }
        }
        onDispose {
            mainHandler.post { cameraStatus = CameraStatus.Stopped }
            session?.stop()
        }
    }

    Box(modifier = modifier.background(Ink)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        if (showLandmarks) {
            LandmarkOverlay(frame = frame, modifier = Modifier.fillMaxSize())
        }
        when (val state = engineState) {
            PoseEngineState.Loading -> CameraMessage(
                title = "正在加载姿态模型",
                detail = "模型只在本机运行，不会上传画面",
                showProgress = true,
            )
            is PoseEngineState.Unavailable -> CameraMessage(
                title = "检测不可用",
                detail = state.message,
            )
            is PoseEngineState.Ready -> {
                if (cameraStatus is CameraStatus.Error) {
                    CameraMessage(
                        title = "相机不可用",
                        detail = (cameraStatus as CameraStatus.Error).message,
                    )
                }
            }
        }
        if (!active) {
            CameraMessage(title = "相机已暂停", detail = "点击开始后恢复本地姿态检测")
        }
    }
}

@Composable
private fun CameraMessage(
    title: String,
    detail: String,
    showProgress: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.55f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
    }
}
