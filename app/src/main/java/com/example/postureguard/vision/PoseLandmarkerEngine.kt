package com.example.postureguard.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.postureguard.posture.model.Landmark
import com.example.postureguard.posture.model.PoseLandmarks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.core.ErrorListener
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Serial, single-in-flight wrapper around a MediaPipe Pose Landmarker in LIVE_STREAM mode.
 * Frames are submitted only when the previous inference has completed, so the native
 * landmarker never receives concurrent input.
 */
class PoseLandmarkerEngine private constructor(
    private val landmarker: PoseLandmarker,
    private val executor: ExecutorService,
) : Closeable {

    @Volatile
    var onDetection: ((PoseDetection) -> Unit)? = null

    @Volatile
    var onError: ((String) -> Unit)? = null

    private val inFlight = AtomicBoolean(false)
    private val timestampLock = Any()
    private var lastTimestampMs = 0L
    @Volatile
    private var submittedAtElapsedMs = 0L

    fun submit(bitmap: Bitmap, timestampMs: Long): Boolean {
        if (!inFlight.compareAndSet(false, true)) return false
        val safeTimestamp = synchronized(timestampLock) {
            val next = max(timestampMs, lastTimestampMs + 1)
            lastTimestampMs = next
            next
        }
        submittedAtElapsedMs = SystemClock.elapsedRealtime()
        return try {
            val image = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(image, safeTimestamp)
            true
        } catch (error: Exception) {
            inFlight.set(false)
            onError?.invoke(error.message ?: "姿态推理提交失败")
            false
        }
    }

    override fun close() {
        executor.shutdown()
        runCatching { landmarker.close() }
    }

    private fun handleResult(result: PoseLandmarkerResult, width: Int, height: Int) {
        val latency = SystemClock.elapsedRealtime() - submittedAtElapsedMs
        inFlight.set(false)
        val pose = result.landmarks().firstOrNull()?.let { points ->
            PoseLandmarks(
                points.map { landmark ->
                    val visibility = landmark.visibility().orElse(1f)
                    Landmark(
                        x = landmark.x(),
                        y = landmark.y(),
                        z = landmark.z(),
                        visibility = visibility,
                        presence = landmark.presence().orElse(visibility),
                    )
                },
            )
        } ?: PoseLandmarks.EMPTY
        val detection = PoseDetection(
            landmarks = pose,
            timestampMs = result.timestampMs(),
            inferenceLatencyMs = latency,
            inputWidth = width,
            inputHeight = height,
        )
        onDetection?.invoke(detection)
    }

    companion object {
        private const val TAG = "PoseLandmarkerEngine"

        /** Blocking; call on a background thread. */
        fun create(context: Context): Result<PoseLandmarkerEngine> = runCatching {
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "pose-result")
            }
            val engineRef = java.util.concurrent.atomic.AtomicReference<PoseLandmarkerEngine?>()
            val resultCallback: OutputHandler.ResultListener<PoseLandmarkerResult, com.google.mediapipe.framework.image.MPImage> =
                OutputHandler.ResultListener { result, input ->
                    engineRef.get()?.handleResult(result, input.width, input.height)
                }
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(VisionModelInfo.MODEL_ASSET)
                        .setDelegate(Delegate.CPU)
                        .build(),
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(VisionModelInfo.MAX_POSES)
                .setMinPoseDetectionConfidence(VisionModelInfo.MIN_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(VisionModelInfo.MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(VisionModelInfo.MIN_TRACKING_CONFIDENCE)
                .setOutputSegmentationMasks(false)
                .setResultListener(resultCallback)
                .setErrorListener(ErrorListener { error ->
                    Log.e(TAG, "MediaPipe runtime error", error)
                    engineRef.get()?.let { engine ->
                        engine.inFlight.set(false)
                        engine.onError?.invoke(error.message ?: "姿态模型运行失败")
                    }
                })
                .build()
            val landmarker = PoseLandmarker.createFromOptions(context, options)
            val engine = PoseLandmarkerEngine(landmarker, executor)
            engineRef.set(engine)
            engine
        }
    }
}
