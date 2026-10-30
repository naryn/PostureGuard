package com.example.postureguard.alert

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureDeltas
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPlannerTest {

    private val policy = AlertPolicy(
        soundEnabled = true,
        speechEnabled = false,
        vibrationEnabled = true,
        repeatIntervalMs = 10_000,
    )

    private fun snapshot(
        state: PostureState,
        condition: PostureCondition? = PostureCondition.FORWARD_HEAD,
        timestampMs: Long = 0,
        metricsValid: Boolean = true,
        recovering: Boolean = false,
    ) = PostureSnapshot(
        state = state,
        severity = ConditionSeverity.MILD,
        condition = condition,
        deltas = PostureDeltas(0.2, 0.0, 0.0),
        quality = LandmarkQuality(metricsValid, 0.9, BodySide.LEFT),
        heldMs = 8_000,
        timestampMs = timestampMs,
        metricsValid = metricsValid,
        recovering = recovering,
    )

    @Test
    fun suspectedState_doesNotAlert() {
        val planner = AlertPlanner(policy)
        assertNull(planner.onSnapshot(snapshot(PostureState.SUSPECTED_FORWARD_HEAD)))
    }

    @Test
    fun confirmedState_repeatsAtInterval() {
        val planner = AlertPlanner(policy)
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 0)))
        assertNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 5_000)))
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 10_000)))
        assertNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 15_000)))
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 20_000)))
    }

    @Test
    fun normalState_resetsRepeatGate() {
        val planner = AlertPlanner(policy)
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 0)))
        planner.onSnapshot(snapshot(PostureState.NORMAL, condition = null, timestampMs = 1_000))
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 2_000)))
    }

    @Test
    fun recoveringOrInvalidFrames_doNotAlert() {
        val planner = AlertPlanner(policy)
        assertNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, recovering = true)))
        assertNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, metricsValid = false)))
        assertNull(planner.onSnapshot(snapshot(PostureState.LOW_CONFIDENCE, condition = null, metricsValid = false)))
    }

    @Test
    fun disabledChannels_areNotRequested() {
        val planner = AlertPlanner(policy.copy(soundEnabled = false, speechEnabled = false, vibrationEnabled = false))
        val request = planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD))
        assertNotNull(request)
        assertTrue(request!!.channels.isEmpty())
    }

    @Test
    fun enabledChannels_areRequested() {
        val planner = AlertPlanner(policy.copy(speechEnabled = true))
        val request = planner.onSnapshot(snapshot(PostureState.HEAD_DOWN, condition = PostureCondition.HEAD_DOWN))
        assertNotNull(request)
        assertEquals(setOf(AlertChannel.SOUND, AlertChannel.SPEECH, AlertChannel.VIBRATION), request!!.channels)
        assertEquals("低头了，请抬头。", request.message)
    }

    @Test
    fun defaultPolicy_usesSpeechAndVibration() {
        val planner = AlertPlanner(AlertPolicy())
        val request = planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD))
        assertNotNull(request)
        assertEquals(setOf(AlertChannel.SPEECH, AlertChannel.VIBRATION), request!!.channels)
        assertEquals("头肩前倾了，请坐直。", request.message)
    }

    @Test
    fun conditionChange_alertsWithoutWaitingForInterval() {
        val planner = AlertPlanner(policy)
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 0)))
        val headDown = planner.onSnapshot(
            snapshot(
                PostureState.HEAD_DOWN,
                condition = PostureCondition.HEAD_DOWN,
                timestampMs = 1_000,
            ),
        )
        assertNotNull(headDown)
        assertEquals(PostureCondition.HEAD_DOWN, headDown!!.condition)
    }
}
