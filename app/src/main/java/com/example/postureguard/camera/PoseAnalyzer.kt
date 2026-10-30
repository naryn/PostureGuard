package com.example.postureguard.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.postureguard.posture.model.PoseLandmarks
import com.example.postureguard.vision.PoseDetection
import com.example.postureguard.vision.PoseLandmarkerEngine
import kotlin.math.max

/**
 * CameraX analyzer that converts a YUV frame to an upright bitmap, throttles inference to
 * the configured FPS, and drops frames while MediaPipe is still busy.
 */
class PoseAnalyzer(
    private val engine: PoseLandmarkerEngine,
    targetFps: Int,
    private val mirrored: Boolean,
    private val onFrame: (CameraFrameResult) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = (1_000L / targetFps.coerceIn(1, 30))
    private var lastSubmitAtMs = 0L
    private var lastFrameTimestampMs = 0L
    private var droppedFrames = 0L

    @Volatile
    var paused: Boolean = false

    private var pending: PendingFrame? = null

    init {
        engine.onDetection = ::handleDetection
        engine.onError = onError
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (paused) return
            val now = SystemClock.elapsedRealtime()
            if (lastSubmitAtMs != 0L && now - lastSubmitAtMs < minIntervalMs) {
                droppedFrames++
                return
            }
            val rotation = image.imageInfo.rotationDegrees
            val source = image.toBitmap()
            val upright = rotate(source, rotation)
            val timestampMs = image.imageInfo.timestamp / 1_000_000L
            val safeTimestamp = if (timestampMs > 0) timestampMs else now
            pending = PendingFrame(
                imageWidth = upright.width,
                imageHeight = upright.height,
            )
            if (engine.submit(upright, safeTimestamp)) {
                lastSubmitAtMs = now
            } else {
                droppedFrames++
                pending = null
            }
        } catch (error: Exception) {
            Log.e(TAG, "Frame conversion failed", error)
            onError(error.message ?: "相机帧处理失败")
        } finally {
            image.close()
        }
    }

    private fun handleDetection(detection: PoseDetection) {
        val pendingFrame = pending ?: return
        pending = null
        val interval = if (lastFrameTimestampMs == 0L) 0L else {
            max(0L, detection.timestampMs - lastFrameTimestampMs)
        }
        lastFrameTimestampMs = detection.timestampMs
        val pose = if (detection.hasPose) detection.landmarks else PoseLandmarks.EMPTY
        onFrame(
            CameraFrameResult(
                pose = pose,
                timestampMs = detection.timestampMs,
                imageWidth = pendingFrame.imageWidth,
                imageHeight = pendingFrame.imageHeight,
                mirrored = mirrored,
                inferenceLatencyMs = detection.inferenceLatencyMs,
                frameIntervalMs = interval,
                droppedFrames = droppedFrames,
            ),
        )
    }

    private fun rotate(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private data class PendingFrame(
        val imageWidth: Int,
        val imageHeight: Int,
    )

    companion object {
        private const val TAG = "PoseAnalyzer"
    }
}
