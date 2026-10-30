package com.example.postureguard.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.postureguard.posture.features.PostureFeaturePipeline
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.Landmark
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.posture.model.PoseLandmarks
import com.example.postureguard.posture.model.QualityIssue
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the bundled model on a synthetic side-profile fixture. This verifies the model asset,
 * the native runtime and the feature pipeline on-device; it is not a real-camera acceptance test.
 */
@RunWith(AndroidJUnit4::class)
class SideProfileModelInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testAssets = instrumentation.context.assets

    @Test
    fun syntheticSideProfileYieldsUsableMetrics() {
        val bitmap = testAssets.open("side_profile_synthetic.jpg").use(BitmapFactory::decodeStream)
        assertNotNull("fixture must decode", bitmap)
        val landmarks = detect(bitmap!!)
        assertTrue("model must find a person in the fixture", !landmarks.isEmpty)

        val frame = PoseFrame(landmarks, timestampMs = 0L, imageWidth = bitmap.width, imageHeight = bitmap.height)
        val result = PostureFeaturePipeline().process(frame, FacingDirection.FACING_RIGHT)
        val metrics = result.metrics
        assertNotNull("fixture should produce metrics", metrics)
        assertFalse(result.quality.issues.contains(QualityIssue.PERSON_NOT_FOUND))
        assertNotNull("a side should be selected", result.quality.selectedSide)
        assertTrue("torso scale should be plausible, was ${metrics!!.torsoScale}", metrics.torsoScale in 0.02..1.5)

        println(
            "SIDE_PROFILE forward=${metrics.forwardOffset} head=${metrics.headAngleDeg} " +
                "torso=${metrics.torsoAngleDeg} scale=${metrics.torsoScale} " +
                "face=${metrics.faceMeasured} side=${result.quality.selectedSide} " +
                "issues=${result.quality.issues}",
        )
    }

    @Test
    fun blankFrameDoesNotFabricateAPose() {
        val blank = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.LTGRAY)
        }
        val landmarks = detect(blank)
        assertEquals("blank frame must not produce landmarks", 0, landmarks.points.size)
    }

    private fun detect(bitmap: Bitmap): PoseLandmarks {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(VisionModelInfo.MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build(),
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(VisionModelInfo.MAX_POSES)
            .setMinPoseDetectionConfidence(VisionModelInfo.MIN_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(VisionModelInfo.MIN_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(VisionModelInfo.MIN_TRACKING_CONFIDENCE)
            .setOutputSegmentationMasks(false)
            .build()
        val landmarker = PoseLandmarker.createFromOptions(context, options)
        return try {
            val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
            result.landmarks().firstOrNull()?.let { points ->
                PoseLandmarks(
                    points.map { point ->
                        val visibility = point.visibility().orElse(1f)
                        Landmark(
                            x = point.x(),
                            y = point.y(),
                            z = point.z(),
                            visibility = visibility,
                            presence = point.presence().orElse(visibility),
                        )
                    },
                )
            } ?: PoseLandmarks.EMPTY
        } finally {
            landmarker.close()
        }
    }
}
