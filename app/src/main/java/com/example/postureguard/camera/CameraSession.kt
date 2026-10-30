package com.example.postureguard.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.postureguard.vision.PoseLandmarkerEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns CameraX binding for one preview surface. The back camera is preferred; a front
 * camera fallback is mirrored only in the overlay, never in the analyzed image.
 */
class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val engine: PoseLandmarkerEngine,
    private val targetFps: Int,
    private val onFrame: (CameraFrameResult) -> Unit,
    private val onStatus: (CameraStatus) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camera-analysis")
    }
    private val released = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var analyzer: PoseAnalyzer? = null

    fun start() {
        onStatus(CameraStatus.Initializing)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (released.get()) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    bind(cameraProvider)
                } catch (error: Exception) {
                    Log.e(TAG, "Camera provider unavailable", error)
                    onStatus(CameraStatus.Error(error.message ?: "相机不可用"))
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val useFront = runCatching { !cameraProvider.hasCamera(backSelector) }.getOrDefault(false)
        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else backSelector

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        val frameAnalyzer = PoseAnalyzer(
            engine = engine,
            targetFps = targetFps,
            mirrored = useFront,
            onFrame = onFrame,
            onError = onError,
        )
        analyzer = frameAnalyzer
        analysis.setAnalyzer(analysisExecutor, frameAnalyzer)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        onStatus(CameraStatus.Running)
    }

    fun stop() {
        if (!released.compareAndSet(false, true)) return
        analyzer?.paused = true
        analyzer = null
        runCatching {
            ContextCompat.getMainExecutor(context).execute {
                provider?.unbindAll()
                provider = null
            }
        }
        analysisExecutor.shutdown()
        onStatus(CameraStatus.Stopped)
    }

    companion object {
        private const val TAG = "CameraSession"
    }
}
