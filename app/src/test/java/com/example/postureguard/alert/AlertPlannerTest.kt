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
        cooldownMs = 10_000,
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
    fun confirmedState_alertsOncePerCooldown() {
        val planner = AlertPlanner(policy)
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 0)))
        assertNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 5_000)))
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 10_000)))
    }

    @Test
    fun normalState_resetsCooldown() {
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
        assertEquals("注意抬头，放松颈部。", request.message)
    }

    @Test
    fun separateConditions_haveSeparateCooldowns() {
        val planner = AlertPlanner(policy)
        assertNotNull(planner.onSnapshot(snapshot(PostureState.FORWARD_HEAD, timestampMs = 0)))
        assertNotNull(
            planner.onSnapshot(
                snapshot(
                    PostureState.HEAD_DOWN,
                    condition = PostureCondition.HEAD_DOWN,
                    timestampMs = 1_000,
                ),
            ),
        )
    }
}
