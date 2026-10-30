package com.example.postureguard.posture.engine

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.EffectiveThresholds
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureDeltas
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState
import com.example.postureguard.posture.model.PostureThresholdConfig
import com.example.postureguard.posture.model.QualityIssue
import com.example.postureguard.posture.model.SensitivityPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureStateMachineTest {

    private val config = PostureThresholdConfig(
        mildHoldDurationMs = 1_000,
        severeHoldDurationMs = 300,
        recoveryHoldDurationMs = 500,
        lowConfidenceTimeoutMs = 300,
        maxFrameGapMs = 600,
    )
    private val thresholds = EffectiveThresholds.from(config, SensitivityPreset.BALANCED, null)
    private val valid = LandmarkQuality(true, 0.9, BodySide.LEFT)

    private fun machine() = PostureStateMachine(config, thresholds)

    private fun feed(
        machine: PostureStateMachine,
        fromMs: Long,
        toMs: Long,
        deltas: PostureDeltas?,
        quality: LandmarkQuality = valid,
        step: Long = 100,
    ): PostureSnapshot {
        var t = fromMs
        var snapshot = machine.update(quality, deltas, t)
        while (t < toMs) {
            t += step
            snapshot = machine.update(quality, deltas, t)
        }
        return snapshot
    }

    private val normal = PostureDeltas(0.0, 0.0, 0.0)
    private val forward = PostureDeltas(0.15, 0.0, 0.0)
    private val headDown = PostureDeltas(0.0, 15.0, 0.0)
    private val compound = PostureDeltas(0.15, 15.0, 0.0)
    private val torso = PostureDeltas(0.0, 0.0, 10.0)

    @Test
    fun mildForwardHead_becomesSuspectedThenConfirmed() {
        val machine = machine()
        val suspected = feed(machine, 0, 500, forward)
        assertEquals(PostureState.SUSPECTED_FORWARD_HEAD, suspected.state)
        val confirmed = feed(machine, 600, 1_100, forward)
        assertEquals(PostureState.FORWARD_HEAD, confirmed.state)
        assertEquals(PostureCondition.FORWARD_HEAD, confirmed.condition)
        assertEquals(ConditionSeverity.MILD, confirmed.severity)
    }

    @Test
    fun shortHeadDown_doesNotConfirmAndRecovers() {
        val machine = machine()
        val suspect = feed(machine, 0, 600, headDown)
        assertEquals(PostureState.SUSPECTED_HEAD_DOWN, suspect.state)
        val recovered = feed(machine, 700, 1_300, normal)
        assertEquals(PostureState.NORMAL, recovered.state)
    }

    @Test
    fun recoveryKeepsStateUntilHysteresisExpires() {
        val machine = machine()
        feed(machine, 0, 1_100, forward)
        val recovering = feed(machine, 1_200, 1_400, normal)
        assertEquals(PostureState.FORWARD_HEAD, recovering.state)
        assertTrue(recovering.recovering)
        val normalAgain = feed(machine, 1_500, 2_000, normal)
        assertEquals(PostureState.NORMAL, normalAgain.state)
        assertFalse(normalAgain.recovering)
    }

    @Test
    fun compoundRequiresBothSignals() {
        val machine = machine()
        val forwardOnly = feed(machine, 0, 1_500, forward)
        assertEquals(PostureState.FORWARD_HEAD, forwardOnly.state)
        val compoundSuspected = feed(machine, 1_600, 2_100, compound)
        assertEquals(PostureState.SUSPECTED_HEAD_DOWN_FORWARD, compoundSuspected.state)
        val compoundConfirmed = feed(machine, 2_200, 3_300, compound)
        assertEquals(PostureState.HEAD_DOWN_FORWARD, compoundConfirmed.state)
    }

    @Test
    fun severeChangeUsesShorterHold() {
        val machine = machine()
        val severe = PostureDeltas(0.3, 0.0, 0.0)
        val state = feed(machine, 0, 400, severe)
        assertEquals(PostureState.FORWARD_HEAD, state.state)
        assertEquals(ConditionSeverity.SEVERE, state.severity)
    }

    @Test
    fun lowConfidenceFreezesAccumulation() {
        val machine = machine()
        feed(machine, 0, 900, forward)
        val invalid = LandmarkQuality(false, 0.1, BodySide.LEFT, setOf(QualityIssue.LOW_CONFIDENCE))
        val frozen = feed(machine, 1_000, 1_200, forward, invalid)
        assertFalse(frozen.metricsValid)
        val low = feed(machine, 1_300, 1_700, forward, invalid)
        assertEquals(PostureState.LOW_CONFIDENCE, low.state)
        val resumed = feed(machine, 1_800, 2_100, forward)
        assertEquals(PostureState.FORWARD_HEAD, resumed.state)
        assertTrue(resumed.heldMs >= 1_000)
    }

    @Test
    fun missingPerson_isNeverReportedAsNormal() {
        val machine = machine()
        val missing = LandmarkQuality(false, 0.0, null, setOf(QualityIssue.PERSON_NOT_FOUND))
        val snapshot = feed(machine, 0, 200, null, missing)
        assertEquals(PostureState.PERSON_NOT_FOUND, snapshot.state)
        assertNotEquals(PostureState.NORMAL, snapshot.state)
    }

    @Test
    fun noValidFrame_isNotNormal() {
        val machine = machine()
        val snapshot = machine.update(LandmarkQuality(false, 0.0, null), null, 0)
        assertNotEquals(PostureState.NORMAL, snapshot.state)
    }

    @Test
    fun frameRateDoesNotChangeConfirmationTime() {
        val fast = machine()
        val slow = machine()
        val fastState = feed(fast, 0, 1_000, forward, step = 50)
        val slowState = feed(slow, 0, 1_000, forward, step = 250)
        assertEquals(PostureState.FORWARD_HEAD, fastState.state)
        assertEquals(PostureState.FORWARD_HEAD, slowState.state)
        assertTrue(fastState.heldMs >= 1_000)
        assertTrue(slowState.heldMs >= 1_000)
    }

    @Test
    fun headDownHasPriorityOverTorsoLean() {
        val machine = machine()
        val both = PostureDeltas(0.0, 15.0, 20.0)
        val state = feed(machine, 0, 1_200, both)
        assertEquals(PostureState.HEAD_DOWN, state.state)
    }

    @Test
    fun compoundHasPriorityOverSingleConditions() {
        val machine = machine()
        val state = feed(machine, 0, 1_200, compound)
        assertEquals(PostureState.HEAD_DOWN_FORWARD, state.state)
    }

    @Test
    fun torsoLeanIsDetected() {
        val machine = machine()
        val state = feed(machine, 0, 1_100, torso)
        assertEquals(PostureState.TORSO_LEAN, state.state)
    }

    @Test
    fun briefForwardDip_doesNotResetConfirmedEvent() {
        val machine = machine()
        feed(machine, 0, 1_200, forward)
        val dip = feed(machine, 1_300, 1_500, normal)
        assertEquals(PostureState.FORWARD_HEAD, dip.state)
        val continued = feed(machine, 1_600, 2_000, forward)
        assertEquals(PostureState.FORWARD_HEAD, continued.state)
        assertFalse(continued.recovering)
    }
}
