package com.example.postureguard.monitoring

import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

enum class StatCategory {
    NORMAL,
    FORWARD_HEAD,
    HEAD_DOWN,
    HEAD_DOWN_FORWARD,
    TORSO_LEAN,
    LOW_CONFIDENCE,
    PAUSED,
}

data class CategoryDurations(
    val normalMs: Long = 0,
    val forwardHeadMs: Long = 0,
    val headDownMs: Long = 0,
    val headDownForwardMs: Long = 0,
    val torsoLeanMs: Long = 0,
    val lowConfidenceMs: Long = 0,
    val pausedMs: Long = 0,
) {
    fun durationOf(category: StatCategory): Long = when (category) {
        StatCategory.NORMAL -> normalMs
        StatCategory.FORWARD_HEAD -> forwardHeadMs
        StatCategory.HEAD_DOWN -> headDownMs
        StatCategory.HEAD_DOWN_FORWARD -> headDownForwardMs
        StatCategory.TORSO_LEAN -> torsoLeanMs
        StatCategory.LOW_CONFIDENCE -> lowConfidenceMs
        StatCategory.PAUSED -> pausedMs
    }

    fun plus(category: StatCategory, durationMs: Long): CategoryDurations = when (category) {
        StatCategory.NORMAL -> copy(normalMs = normalMs + durationMs)
        StatCategory.FORWARD_HEAD -> copy(forwardHeadMs = forwardHeadMs + durationMs)
        StatCategory.HEAD_DOWN -> copy(headDownMs = headDownMs + durationMs)
        StatCategory.HEAD_DOWN_FORWARD -> copy(headDownForwardMs = headDownForwardMs + durationMs)
        StatCategory.TORSO_LEAN -> copy(torsoLeanMs = torsoLeanMs + durationMs)
        StatCategory.LOW_CONFIDENCE -> copy(lowConfidenceMs = lowConfidenceMs + durationMs)
        StatCategory.PAUSED -> copy(pausedMs = pausedMs + durationMs)
    }

    fun plus(other: CategoryDurations): CategoryDurations = CategoryDurations(
        normalMs = normalMs + other.normalMs,
        forwardHeadMs = forwardHeadMs + other.forwardHeadMs,
        headDownMs = headDownMs + other.headDownMs,
        headDownForwardMs = headDownForwardMs + other.headDownForwardMs,
        torsoLeanMs = torsoLeanMs + other.torsoLeanMs,
        lowConfidenceMs = lowConfidenceMs + other.lowConfidenceMs,
        pausedMs = pausedMs + other.pausedMs,
    )
}

data class DayAggregate(
    val date: LocalDate,
    val durations: CategoryDurations = CategoryDurations(),
    val validMs: Long = 0,
    val monitoredMs: Long = 0,
    val eventCount: Int = 0,
) {
    val abnormalMs: Long
        get() = durations.forwardHeadMs + durations.headDownMs +
            durations.headDownForwardMs + durations.torsoLeanMs
}

data class HourKey(val date: LocalDate, val hour: Int)

data class HourAggregate(
    val key: HourKey,
    val durations: CategoryDurations = CategoryDurations(),
    val monitoredMs: Long = 0,
)

data class RecordedPostureEvent(
    val condition: PostureCondition,
    val severity: ConditionSeverity,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val peakForwardHead: Double?,
    val peakHeadDown: Double?,
    val peakTorsoLean: Double?,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)
}

data class SessionStats(
    val startMs: Long,
    val endMs: Long,
    val durations: CategoryDurations,
    val validMs: Long,
    val dayAggregates: List<DayAggregate>,
    val hourAggregates: List<HourAggregate>,
    val events: List<RecordedPostureEvent>,
)

/**
 * Aggregates monitoring time into mutually exclusive posture buckets, splitting each
 * tick at hour boundaries so midnight-crossing sessions end up on the right day.
 */
class MonitoringSessionRecorder(
    private val startMs: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val maxTickMs: Long = 2_000,
) {
    private val days = sortedMapOf<LocalDate, DayAggregate>()
    private val hours = linkedMapOf<HourKey, HourAggregate>()
    private var sessionDurations = CategoryDurations()
    private var sessionValidMs = 0L
    private var lastTickMs = startMs
    private var lastCategory = StatCategory.LOW_CONFIDENCE
    private var lastValid = false
    private var paused = false

    private var activeEvent: MutableEvent? = null
    private val completedEvents = mutableListOf<RecordedPostureEvent>()

    val dayAggregates: List<DayAggregate> get() = days.values.toList()
    val hourAggregates: List<HourAggregate> get() = hours.values.toList()
    val sessionTotals: CategoryDurations get() = sessionDurations
    val validDurationMs: Long get() = sessionValidMs

    fun onSnapshot(snapshot: PostureSnapshot, nowMs: Long = snapshot.timestampMs) {
        if (paused) {
            lastTickMs = nowMs
            return
        }
        val category = categoryOf(snapshot)
        val valid = snapshot.metricsValid && snapshot.state != PostureState.PAUSED
        val dt = (nowMs - lastTickMs).coerceIn(0L, maxTickMs)
        if (dt > 0) {
            allocate(lastTickMs, nowMs, category, valid)
        }
        lastTickMs = nowMs
        lastCategory = category
        lastValid = valid
        trackEvent(snapshot, nowMs)
    }

    fun onPause(nowMs: Long) {
        if (paused) return
        closeEvent(nowMs)
        val dt = (nowMs - lastTickMs).coerceIn(0L, maxTickMs)
        if (dt > 0) allocate(lastTickMs, nowMs, StatCategory.PAUSED, valid = false)
        lastTickMs = nowMs
        lastCategory = StatCategory.PAUSED
        lastValid = false
        paused = true
    }

    fun onResume(nowMs: Long) {
        paused = false
        lastTickMs = nowMs
    }

    fun drainCompletedEvents(): List<RecordedPostureEvent> {
        val drained = completedEvents.toList()
        completedEvents.clear()
        return drained
    }

    fun finish(nowMs: Long): SessionStats {
        if (paused) {
            if (nowMs > lastTickMs) allocate(lastTickMs, nowMs, StatCategory.PAUSED, valid = false)
        } else {
            val dt = (nowMs - lastTickMs).coerceIn(0L, maxTickMs)
            if (dt > 0) allocate(lastTickMs, nowMs, lastCategory, lastValid)
        }
        lastTickMs = nowMs
        closeEvent(nowMs)
        return SessionStats(
            startMs = startMs,
            endMs = nowMs,
            durations = sessionDurations,
            validMs = sessionValidMs,
            dayAggregates = days.values.toList(),
            hourAggregates = hours.values.toList(),
            events = completedEvents.toList(),
        )
    }

    private fun allocate(start: Long, end: Long, category: StatCategory, valid: Boolean) {
        if (end <= start) return
        var cursor = start
        while (cursor < end) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
            val nextHour = zoned.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1)
                .toInstant().toEpochMilli()
            val segmentEnd = min(end, nextHour)
            val duration = segmentEnd - cursor
            val date = zoned.toLocalDate()
            val hourKey = HourKey(date, zoned.hour)
            val day = days[date] ?: DayAggregate(date)
            val hour = hours[hourKey] ?: HourAggregate(hourKey)
            val countsMonitored = category != StatCategory.PAUSED
            days[date] = day.copy(
                durations = day.durations.plus(category, duration),
                validMs = day.validMs + if (valid) duration else 0,
                monitoredMs = day.monitoredMs + if (countsMonitored) duration else 0,
            )
            hours[hourKey] = hour.copy(
                durations = hour.durations.plus(category, duration),
                monitoredMs = hour.monitoredMs + if (countsMonitored) duration else 0,
            )
            sessionDurations = sessionDurations.plus(category, duration)
            if (valid) sessionValidMs += duration
            cursor = segmentEnd
        }
    }

    private fun trackEvent(snapshot: PostureSnapshot, nowMs: Long) {
        val confirmed = snapshot.metricsValid && !snapshot.recovering &&
            snapshot.state.isConfirmedAbnormal
        val condition = if (confirmed) snapshot.condition else null
        if (activeEvent != null && activeEvent!!.condition != condition) {
            closeEvent(nowMs)
        }
        if (condition != null) {
            val event = activeEvent ?: MutableEvent(
                condition = condition,
                severity = snapshot.severity,
                startedAtMs = nowMs,
            ).also {
                activeEvent = it
                val date = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
                val day = days[date] ?: DayAggregate(date)
                days[date] = day.copy(eventCount = day.eventCount + 1)
            }
            event.severity = if (snapshot.severity.rank > event.severity.rank) snapshot.severity else event.severity
            val deltas = snapshot.deltas
            if (deltas != null) {
                event.peakForward = maxOfNullable(event.peakForward, deltas.forwardHead)
                event.peakHeadDown = maxOfNullable(event.peakHeadDown, deltas.headDown)
                event.peakTorso = maxOfNullable(event.peakTorso, deltas.torsoLean)
            }
        }
    }

    private fun closeEvent(nowMs: Long) {
        val event = activeEvent ?: return
        activeEvent = null
        if (nowMs <= event.startedAtMs) return
        completedEvents += RecordedPostureEvent(
            condition = event.condition,
            severity = event.severity,
            startedAtMs = event.startedAtMs,
            endedAtMs = nowMs,
            peakForwardHead = event.peakForward,
            peakHeadDown = event.peakHeadDown,
            peakTorsoLean = event.peakTorso,
        )
    }

    private fun maxOfNullable(current: Double?, candidate: Double): Double =
        current?.let { max(it, candidate) } ?: candidate

    companion object {
        fun categoryOf(snapshot: PostureSnapshot): StatCategory = when {
            snapshot.state == PostureState.PAUSED -> StatCategory.PAUSED
            !snapshot.metricsValid -> StatCategory.LOW_CONFIDENCE
            snapshot.state == PostureState.LOW_CONFIDENCE ||
                snapshot.state == PostureState.PERSON_NOT_FOUND ||
                snapshot.state == PostureState.NOT_READY -> StatCategory.LOW_CONFIDENCE
            snapshot.state == PostureState.NORMAL -> StatCategory.NORMAL
            else -> when (snapshot.state.condition) {
                PostureCondition.FORWARD_HEAD -> StatCategory.FORWARD_HEAD
                PostureCondition.HEAD_DOWN -> StatCategory.HEAD_DOWN
                PostureCondition.HEAD_DOWN_FORWARD -> StatCategory.HEAD_DOWN_FORWARD
                PostureCondition.TORSO_LEAN -> StatCategory.TORSO_LEAN
                null -> StatCategory.NORMAL
            }
        }
    }

    private class MutableEvent(
        val condition: PostureCondition,
        var severity: ConditionSeverity,
        val startedAtMs: Long,
    ) {
        var peakForward: Double? = null
        var peakHeadDown: Double? = null
        var peakTorso: Double? = null
    }
}
