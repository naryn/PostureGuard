package com.example.postureguard.monitoring

import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.LandmarkQuality
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.posture.model.PostureDeltas
import com.example.postureguard.posture.model.PostureSnapshot
import com.example.postureguard.posture.model.PostureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MonitoringSessionRecorderTest {

    private fun snapshot(
        state: PostureState,
        timestampMs: Long,
        deltas: PostureDeltas = PostureDeltas(0.0, 0.0, 0.0),
        metricsValid: Boolean = true,
        recovering: Boolean = false,
    ) = PostureSnapshot(
        state = state,
        severity = if (state.isConfirmedAbnormal) ConditionSeverity.MILD else ConditionSeverity.NONE,
        condition = state.condition,
        deltas = deltas,
        quality = LandmarkQuality(metricsValid, 0.9, BodySide.LEFT),
        heldMs = 0,
        timestampMs = timestampMs,
        metricsValid = metricsValid,
        recovering = recovering,
    )

    @Test
    fun tick_splitsSessionsAcrossMidnight() {
        val start = Instant.parse("2026-10-29T23:59:50Z").toEpochMilli()
        val recorder = MonitoringSessionRecorder(start, ZoneOffset.UTC)
        var t = start
        recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        repeat(20) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        }
        val stats = recorder.finish(t)
        assertEquals(20_000, stats.durations.normalMs)
        val day1 = stats.dayAggregates.first { it.date == LocalDate.of(2026, 10, 29) }
        val day2 = stats.dayAggregates.first { it.date == LocalDate.of(2026, 10, 30) }
        assertEquals(10_000, day1.durations.normalMs)
        assertEquals(10_000, day2.durations.normalMs)
        assertEquals(20_000, stats.validMs)
    }

    @Test
    fun categoriesAreMutuallyExclusive() {
        val start = 0L
        val recorder = MonitoringSessionRecorder(start, ZoneOffset.UTC)
        var t = start
        recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        while (t < 3_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        }
        while (t < 6_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.FORWARD_HEAD, t), t)
        }
        while (t < 7_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.LOW_CONFIDENCE, t, metricsValid = false), t)
        }
        val stats = recorder.finish(t)
        assertEquals(3_000, stats.durations.normalMs)
        assertEquals(3_000, stats.durations.forwardHeadMs)
        assertEquals(1_000, stats.durations.lowConfidenceMs)
        assertEquals(6_000, stats.validMs)
        assertEquals(7_000, stats.durations.normalMs + stats.durations.forwardHeadMs +
            stats.durations.lowConfidenceMs)
    }

    @Test
    fun suspectedTimeCountsAsCategoryButNotAsEvent() {
        val recorder = MonitoringSessionRecorder(0, ZoneOffset.UTC)
        recorder.onSnapshot(snapshot(PostureState.NORMAL, 0), 0)
        var t = 0L
        while (t < 2_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.SUSPECTED_HEAD_DOWN, t), t)
        }
        val stats = recorder.finish(t)
        assertEquals(0, stats.durations.normalMs)
        assertEquals(2_000, stats.durations.headDownMs)
        assertTrue(stats.events.isEmpty())
    }

    @Test
    fun confirmedEvent_isRecordedWithPeaks() {
        val recorder = MonitoringSessionRecorder(0, ZoneOffset.UTC)
        var t = 0L
        recorder.onSnapshot(snapshot(PostureState.FORWARD_HEAD, t, PostureDeltas(0.15, 0.0, 0.0)), t)
        while (t < 2_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.FORWARD_HEAD, t, PostureDeltas(0.25, 0.0, 0.0)), t)
        }
        t += 1_000
        recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        val stats = recorder.finish(t + 1_000)
        assertEquals(1, stats.events.size)
        val event = stats.events.first()
        assertEquals(PostureCondition.FORWARD_HEAD, event.condition)
        assertEquals(3_000, event.durationMs)
        assertEquals(0.25, event.peakForwardHead!!, 1e-9)
        assertEquals(1, stats.dayAggregates.first().eventCount)
    }

    @Test
    fun invalidFramesAreNotCountedAsNormal() {
        val recorder = MonitoringSessionRecorder(0, ZoneOffset.UTC)
        recorder.onSnapshot(snapshot(PostureState.NORMAL, 0, metricsValid = false), 0)
        var t = 0L
        while (t < 2_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.NORMAL, t, metricsValid = false), t)
        }
        val stats = recorder.finish(t)
        assertEquals(0, stats.durations.normalMs)
        assertEquals(2_000, stats.durations.lowConfidenceMs)
        assertEquals(0, stats.validMs)
    }

    @Test
    fun pausedTimeIsExcludedFromMonitoredTime() {
        val recorder = MonitoringSessionRecorder(0, ZoneOffset.UTC)
        recorder.onSnapshot(snapshot(PostureState.NORMAL, 0), 0)
        var t = 0L
        while (t < 1_000) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        }
        recorder.onPause(t)
        val stats = recorder.finish(t + 5_000)
        assertEquals(1_000, stats.durations.normalMs)
        assertEquals(5_000, stats.durations.pausedMs)
        assertEquals(1_000, stats.dayAggregates.first().monitoredMs)
    }

    @Test
    fun hourlyBucketsSplitAtTheHour() {
        val start = Instant.parse("2026-10-30T10:59:58Z").toEpochMilli()
        val recorder = MonitoringSessionRecorder(start, ZoneOffset.UTC)
        var t = start
        recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        repeat(6) {
            t += 1_000
            recorder.onSnapshot(snapshot(PostureState.NORMAL, t), t)
        }
        val stats = recorder.finish(t)
        val hours = stats.hourAggregates.associateBy { it.key.hour }
        assertEquals(2_000, hours.getValue(10).durations.normalMs)
        assertEquals(4_000, hours.getValue(11).durations.normalMs)
    }
}
