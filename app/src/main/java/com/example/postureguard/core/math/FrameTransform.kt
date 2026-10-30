package com.example.postureguard.core.math

/**
 * Coordinate helpers shared by camera analysis and the landmark overlay.
 * Normalized coordinates are always relative to the upright analysis image
 * (0,0 top-left; 1,1 bottom-right).
 */
object FrameTransform {

    data class NormalizedPoint(val x: Double, val y: Double)

    data class ViewPoint(val x: Float, val y: Float)

    /** Maps a sensor-oriented normalized point into the clockwise-rotated upright image. */
    fun rotateNormalized(x: Double, y: Double, rotationDegrees: Int): NormalizedPoint =
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> NormalizedPoint(1.0 - y, x)
            180 -> NormalizedPoint(1.0 - x, 1.0 - y)
            270 -> NormalizedPoint(y, 1.0 - x)
            else -> NormalizedPoint(x, y)
        }

    /** Dimensions of the image after applying rotationDegrees clockwise. */
    fun rotatedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return if (normalized == 90 || normalized == 270) height to width else width to height
    }

    /**
     * Center-crop ("FILL_CENTER") mapping from normalized image coordinates to view pixels.
     * Keeps the aspect ratio and crops the overflowing dimension symmetrically.
     */
    fun centerCropMap(
        nx: Double,
        ny: Double,
        imageAspectRatio: Double,
        viewWidth: Float,
        viewHeight: Float,
    ): ViewPoint {
        if (viewWidth <= 0f || viewHeight <= 0f || imageAspectRatio <= 0.0) {
            return ViewPoint(0f, 0f)
        }
        val scale = maxOf(viewWidth / imageAspectRatio.toFloat(), viewHeight)
        val displayedWidth = imageAspectRatio.toFloat() * scale
        val displayedHeight = scale
        val offsetX = (viewWidth - displayedWidth) / 2f
        val offsetY = (viewHeight - displayedHeight) / 2f
        return ViewPoint(
            x = offsetX + nx.toFloat() * displayedWidth,
            y = offsetY + ny.toFloat() * displayedHeight,
        )
    }
}
