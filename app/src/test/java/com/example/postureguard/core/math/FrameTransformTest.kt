package com.example.postureguard.core.math

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTransformTest {

    @Test
    fun rotateNormalized_handlesAllFourRotations() {
        val x = 0.1
        val y = 0.2
        FrameTransform.rotateNormalized(x, y, 0).let {
            assertEquals(x, it.x, 1e-9)
            assertEquals(y, it.y, 1e-9)
        }
        FrameTransform.rotateNormalized(x, y, 90).let {
            assertEquals(0.8, it.x, 1e-9)
            assertEquals(0.1, it.y, 1e-9)
        }
        FrameTransform.rotateNormalized(x, y, 180).let {
            assertEquals(0.9, it.x, 1e-9)
            assertEquals(0.8, it.y, 1e-9)
        }
        FrameTransform.rotateNormalized(x, y, 270).let {
            assertEquals(0.2, it.x, 1e-9)
            assertEquals(0.9, it.y, 1e-9)
        }
    }

    @Test
    fun rotateNormalized_fourNinetyRotationsReturnIdentity() {
        var point = FrameTransform.NormalizedPoint(0.33, 0.66)
        repeat(4) {
            point = FrameTransform.rotateNormalized(point.x, point.y, 90)
        }
        assertEquals(0.33, point.x, 1e-9)
        assertEquals(0.66, point.y, 1e-9)
    }

    @Test
    fun rotatedSize_swapsOnQuarterTurns() {
        assertEquals(480, FrameTransform.rotatedSize(640, 480, 90).first)
        assertEquals(640, FrameTransform.rotatedSize(640, 480, 90).second)
        assertEquals(640, FrameTransform.rotatedSize(640, 480, 180).first)
        assertEquals(480, FrameTransform.rotatedSize(640, 480, 180).second)
        assertEquals(480, FrameTransform.rotatedSize(640, 480, 270).first)
        assertEquals(640, FrameTransform.rotatedSize(640, 480, 270).second)
    }

    @Test
    fun centerCropMap_portraitViewCropsSidesOfFourThreeImage() {
        // 4:3 image in a 9:16 view: height fills, width is cropped.
        val viewWidth = 1080f
        val viewHeight = 2400f
        val aspect = 4.0 / 3.0
        val center = FrameTransform.centerCropMap(0.5, 0.5, aspect, viewWidth, viewHeight)
        assertEquals(540f, center.x, 0.5f)
        assertEquals(1200f, center.y, 0.5f)
        val topLeft = FrameTransform.centerCropMap(0.0, 0.0, aspect, viewWidth, viewHeight)
        assertEquals(-1060f, topLeft.x, 0.5f)
        assertEquals(0f, topLeft.y, 0.5f)
    }

    @Test
    fun centerCropMap_landscapeViewCropsTopAndBottom() {
        val viewWidth = 2000f
        val viewHeight = 1000f
        val aspect = 1.0
        val top = FrameTransform.centerCropMap(0.0, 0.0, aspect, viewWidth, viewHeight)
        assertEquals(0f, top.x, 0.5f)
        assertEquals(-500f, top.y, 0.5f)
    }
}
