package com.example.postureguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.core.math.FrameTransform
import com.example.postureguard.posture.model.LandmarkType
import com.example.postureguard.ui.theme.Mint
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.Warm

private val connections: List<Pair<LandmarkType, LandmarkType>> = listOf(
    LandmarkType.NOSE to LandmarkType.LEFT_EAR,
    LandmarkType.NOSE to LandmarkType.RIGHT_EAR,
    LandmarkType.LEFT_EAR to LandmarkType.LEFT_SHOULDER,
    LandmarkType.RIGHT_EAR to LandmarkType.RIGHT_SHOULDER,
    LandmarkType.LEFT_SHOULDER to LandmarkType.RIGHT_SHOULDER,
    LandmarkType.LEFT_SHOULDER to LandmarkType.LEFT_ELBOW,
    LandmarkType.RIGHT_SHOULDER to LandmarkType.RIGHT_ELBOW,
    LandmarkType.LEFT_ELBOW to LandmarkType.LEFT_WRIST,
    LandmarkType.RIGHT_ELBOW to LandmarkType.RIGHT_WRIST,
    LandmarkType.LEFT_SHOULDER to LandmarkType.LEFT_HIP,
    LandmarkType.RIGHT_SHOULDER to LandmarkType.RIGHT_HIP,
    LandmarkType.LEFT_HIP to LandmarkType.RIGHT_HIP,
    LandmarkType.LEFT_HIP to LandmarkType.LEFT_KNEE,
    LandmarkType.RIGHT_HIP to LandmarkType.RIGHT_KNEE,
    LandmarkType.LEFT_KNEE to LandmarkType.LEFT_ANKLE,
    LandmarkType.RIGHT_KNEE to LandmarkType.RIGHT_ANKLE,
)

private val keyPoints = listOf(
    LandmarkType.NOSE,
    LandmarkType.LEFT_EAR,
    LandmarkType.RIGHT_EAR,
    LandmarkType.LEFT_SHOULDER,
    LandmarkType.RIGHT_SHOULDER,
    LandmarkType.LEFT_HIP,
    LandmarkType.RIGHT_HIP,
    LandmarkType.LEFT_KNEE,
    LandmarkType.RIGHT_KNEE,
)

private const val MIN_DRAW_CONFIDENCE = 0.3f

/** Draws the pose skeleton aligned with a FILL_CENTER preview. */
@Composable
fun LandmarkOverlay(
    frame: CameraFrameResult?,
    modifier: Modifier = Modifier,
    lineColor: Color = Teal,
) {
    Canvas(modifier = modifier) {
        if (frame == null || frame.pose.isEmpty) return@Canvas
        val aspect = frame.imageAspectRatio
        fun projected(type: LandmarkType): Offset? {
            val landmark = frame.pose[type] ?: return null
            if (landmark.confidence < MIN_DRAW_CONFIDENCE) return null
            val normalizedX = if (frame.mirrored) 1.0 - landmark.x else landmark.x.toDouble()
            val point = FrameTransform.centerCropMap(
                nx = normalizedX,
                ny = landmark.y.toDouble(),
                imageAspectRatio = aspect,
                viewWidth = size.width,
                viewHeight = size.height,
            )
            return Offset(point.x, point.y)
        }

        connections.forEach { (from, to) ->
            val a = projected(from) ?: return@forEach
            val b = projected(to) ?: return@forEach
            drawLine(
                color = lineColor.copy(alpha = 0.72f),
                start = a,
                end = b,
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
        keyPoints.forEach { type ->
            val point = projected(type) ?: return@forEach
            drawKeyPoint(point)
        }
    }
}

private fun DrawScope.drawKeyPoint(point: Offset) {
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 8f, center = point)
    drawCircle(color = Warm, radius = 6f, center = point)
    drawCircle(color = Mint.copy(alpha = 0.35f), radius = 12f, center = point)
}
