package com.example.postureguard.posture.engine

import com.example.postureguard.posture.features.PostureFeaturePipeline
import com.example.postureguard.posture.model.ConditionLevels
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.EffectiveThresholds
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureDeltas
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState
import com.example.postureguard.posture.model.PostureThresholdConfig
import com.example.postureguard.posture.model.QualityIssue
import kotlin.math.max

/**
 * Time based posture state machine with hysteresis.
 *
 * Only frames with valid landmark quality accumulate hold time. Missing landmarks
 * freeze every timer so that "no data" is never counted as bad posture. A condition
 * must stay abnormal for the mild/severe hold duration before it is confirmed.
 */
class PostureStateMachine(
    private val config: PostureThresholdConfig,
    initialThresholds: EffectiveThresholds,
) {
    var thresholds: EffectiveThresholds = initialThresholds

    private val forward = ConditionTracker()
    private val headDown = ConditionTracker()
    private val torso = ConditionTracker()
    private val compound = ConditionTracker()

    private var lastTimestampMs: Long? = null
    private var lastQuality: LandmarkQuality = LandmarkQuality.NONE
    private var lastDeltas: PostureDeltas? = null
    private var invalidSinceMs: Long? = null
    private var missingSinceMs: Long? = null
    private var lastAbnormalState: PostureState? = null
    private var lastAbnormalCondition: PostureCondition? = null
    private var lastAbnormalSeverity: ConditionSeverity = ConditionSeverity.NONE
    private var lastSnapshot: PostureSnapshot? = null

    fun reset() {
        forward.reset()
        headDown.reset()
        torso.reset()
        compound.reset()
        lastTimestampMs = null
        lastQuality = LandmarkQuality.NONE
        lastDeltas = null
        invalidSinceMs = null
        missingSinceMs = null
        lastAbnormalState = null
        lastAbnormalCondition = null
        lastAbnormalSeverity = ConditionSeverity.NONE
        lastSnapshot = null
    }

    fun pause(nowMs: Long): PostureSnapshot = PostureSnapshot(
        state = PostureState.PAUSED,
        severity = ConditionSeverity.NONE,
        condition = null,
        deltas = lastDeltas,
        quality = lastQuality,
        heldMs = 0,
        timestampMs = nowMs,
        metricsValid = false,
    ).also { lastSnapshot = it }

    fun update(quality: LandmarkQuality, deltas: PostureDeltas?, nowMs: Long): PostureSnapshot {
        val previousTimestamp = lastTimestampMs
        val dt = if (previousTimestamp == null) 0L else (nowMs - previousTimestamp).coerceIn(0L, config.maxFrameGapMs)
        lastTimestampMs = nowMs
        lastQuality = quality

        if (quality.issues.contains(QualityIssue.PERSON_NOT_FOUND)) {
            invalidSinceMs = null
            val since = missingSinceMs ?: nowMs.also { missingSinceMs = it }
            if (nowMs - since >= max(3_000L, config.recoveryHoldDurationMs)) {
                resetTrackersOnly()
            }
            return publish(
                state = PostureState.PERSON_NOT_FOUND,
                quality = quality,
                deltas = deltas,
                metricsValid = false,
            )
        }
        missingSinceMs = null

        if (!quality.valid || deltas == null) {
            val since = invalidSinceMs ?: nowMs.also { invalidSinceMs = it }
            val graceExpired = nowMs - since >= config.lowConfidenceTimeoutMs
            val state = when {
                graceExpired -> PostureState.LOW_CONFIDENCE
                lastSnapshot != null -> lastSnapshot!!.state
                else -> PostureState.LOW_CONFIDENCE
            }
            return publish(
                state = state,
                quality = quality,
                deltas = deltas ?: lastDeltas,
                metricsValid = false,
                heldMs = lastSnapshot?.heldMs ?: 0L,
            )
        }
        invalidSinceMs = null
        lastDeltas = deltas

        val levels = PostureFeaturePipeline.levels(deltas, thresholds)
        forward.update(levels.forwardHead != ConditionSeverity.NONE, dt)
        headDown.update(levels.headDown != ConditionSeverity.NONE, dt)
        torso.update(levels.torsoLean != ConditionSeverity.NONE, dt)

        val compoundLevel = minSeverity(levels.forwardHead, levels.headDown)
        compound.update(compoundLevel != ConditionSeverity.NONE, dt)

        val candidate: Candidate? = when {
            compound.active -> Candidate(
                condition = PostureCondition.HEAD_DOWN_FORWARD,
                tracker = compound,
                severity = compoundLevel,
            )
            headDown.active -> Candidate(PostureCondition.HEAD_DOWN, headDown, levels.headDown)
            forward.active -> Candidate(PostureCondition.FORWARD_HEAD, forward, levels.forwardHead)
            torso.active -> Candidate(PostureCondition.TORSO_LEAN, torso, levels.torsoLean)
            else -> null
        }

        if (candidate != null) {
            val requiredHold = if (candidate.severity == ConditionSeverity.SEVERE) {
                config.severeHoldDurationMs
            } else {
                config.mildHoldDurationMs
            }
            val confirmed = candidate.tracker.heldMs >= requiredHold
            val state = if (confirmed) confirmedState(candidate.condition) else suspectedState(candidate.condition)
            if (confirmed) {
                lastAbnormalState = state
                lastAbnormalCondition = candidate.condition
                lastAbnormalSeverity = candidate.severity
            }
            return publish(
                state = state,
                quality = quality,
                deltas = deltas,
                metricsValid = true,
                severity = candidate.severity,
                condition = candidate.condition,
                heldMs = candidate.tracker.heldMs,
                levels = levels,
                recovering = false,
            )
        }

        val recoveringCandidate = recoveringCondition()
        if (recoveringCandidate != null && lastAbnormalState != null && lastAbnormalCondition != null) {
            val tracker = trackerFor(lastAbnormalCondition!!)
            return publish(
                state = lastAbnormalState!!,
                quality = quality,
                deltas = deltas,
                metricsValid = true,
                severity = lastAbnormalSeverity,
                condition = lastAbnormalCondition,
                heldMs = tracker.heldMs,
                levels = levels,
                recovering = true,
            )
        }

        lastAbnormalState = null
        lastAbnormalCondition = null
        lastAbnormalSeverity = ConditionSeverity.NONE
        return publish(
            state = PostureState.NORMAL,
            quality = quality,
            deltas = deltas,
            metricsValid = true,
            severity = ConditionSeverity.NONE,
            condition = null,
            heldMs = 0L,
            levels = levels,
            recovering = false,
        )
    }

    private fun recoveringCondition(): PostureCondition? {
        val condition = lastAbnormalCondition ?: return null
        return if (trackerFor(condition).recovering) condition else null
    }

    private fun trackerFor(condition: PostureCondition): ConditionTracker = when (condition) {
        PostureCondition.FORWARD_HEAD -> forward
        PostureCondition.HEAD_DOWN -> headDown
        PostureCondition.HEAD_DOWN_FORWARD -> compound
        PostureCondition.TORSO_LEAN -> torso
    }

    private fun resetTrackersOnly() {
        forward.reset()
        headDown.reset()
        torso.reset()
        compound.reset()
        lastAbnormalState = null
        lastAbnormalCondition = null
        lastAbnormalSeverity = ConditionSeverity.NONE
    }

    private fun publish(
        state: PostureState,
        quality: LandmarkQuality,
        deltas: PostureDeltas?,
        metricsValid: Boolean,
        severity: ConditionSeverity = ConditionSeverity.NONE,
        condition: PostureCondition? = null,
        heldMs: Long = 0L,
        levels: ConditionLevels = ConditionLevels(),
        recovering: Boolean = false,
    ): PostureSnapshot = PostureSnapshot(
        state = state,
        severity = severity,
        condition = condition,
        deltas = deltas,
        quality = quality,
        heldMs = heldMs,
        timestampMs = lastTimestampMs ?: 0L,
        metricsValid = metricsValid,
        recovering = recovering,
        levels = levels,
    ).also { lastSnapshot = it }

    private fun confirmedState(condition: PostureCondition): PostureState = when (condition) {
        PostureCondition.FORWARD_HEAD -> PostureState.FORWARD_HEAD
        PostureCondition.HEAD_DOWN -> PostureState.HEAD_DOWN
        PostureCondition.HEAD_DOWN_FORWARD -> PostureState.HEAD_DOWN_FORWARD
        PostureCondition.TORSO_LEAN -> PostureState.TORSO_LEAN
    }

    private fun suspectedState(condition: PostureCondition): PostureState = when (condition) {
        PostureCondition.FORWARD_HEAD -> PostureState.SUSPECTED_FORWARD_HEAD
        PostureCondition.HEAD_DOWN -> PostureState.SUSPECTED_HEAD_DOWN
        PostureCondition.HEAD_DOWN_FORWARD -> PostureState.SUSPECTED_HEAD_DOWN_FORWARD
        PostureCondition.TORSO_LEAN -> PostureState.SUSPECTED_TORSO_LEAN
    }

    private fun minSeverity(a: ConditionSeverity, b: ConditionSeverity): ConditionSeverity =
        if (a.rank <= b.rank) a else b

    private data class Candidate(
        val condition: PostureCondition,
        val tracker: ConditionTracker,
        val severity: ConditionSeverity,
    )

    private inner class ConditionTracker {
        var heldMs: Long = 0L
            private set
        var recoveryMs: Long = 0L
            private set
        var active: Boolean = false
            private set

        val recovering: Boolean
            get() = !active && recoveryMs in 1 until config.recoveryHoldDurationMs

        fun update(isActive: Boolean, dt: Long) {
            if (isActive) {
                active = true
                recoveryMs = 0L
                heldMs += dt
            } else {
                if (active) {
                    active = false
                    recoveryMs = 0L
                }
                recoveryMs += dt
                if (recoveryMs >= config.recoveryHoldDurationMs) {
                    recoveryMs = 0L
                    heldMs = 0L
                }
            }
        }

        fun reset() {
            heldMs = 0L
            recoveryMs = 0L
            active = false
        }
    }
}
