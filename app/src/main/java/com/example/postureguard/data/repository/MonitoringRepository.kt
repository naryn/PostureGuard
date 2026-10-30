package com.example.postureguard.data.repository

import androidx.room.withTransaction
import com.example.postureguard.data.db.PostureDatabase
import com.example.postureguard.data.entity.PostureEventEntity
import com.example.postureguard.data.entity.SessionDayEntity
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.data.entity.SessionHourEntity
import com.example.postureguard.monitoring.DayAggregate
import com.example.postureguard.monitoring.HourAggregate
import com.example.postureguard.monitoring.RecordedPostureEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

data class DailySummary(
    val date: LocalDate,
    val monitoredDurationMs: Long,
    val validDurationMs: Long,
    val normalDurationMs: Long,
    val forwardHeadDurationMs: Long,
    val headDownDurationMs: Long,
    val headDownForwardDurationMs: Long,
    val torsoLeanDurationMs: Long,
    val lowConfidenceDurationMs: Long,
    val eventCount: Int,
) {
    val abnormalDurationMs: Long
        get() = forwardHeadDurationMs + headDownDurationMs + headDownForwardDurationMs + torsoLeanDurationMs

    companion object {
        fun empty(date: LocalDate): DailySummary = DailySummary(
            date = date,
            monitoredDurationMs = 0,
            validDurationMs = 0,
            normalDurationMs = 0,
            forwardHeadDurationMs = 0,
            headDownDurationMs = 0,
            headDownForwardDurationMs = 0,
            torsoLeanDurationMs = 0,
            lowConfidenceDurationMs = 0,
            eventCount = 0,
        )
    }
}

data class HourlySummary(
    val date: LocalDate,
    val hour: Int,
    val monitoredDurationMs: Long,
    val normalDurationMs: Long,
    val abnormalDurationMs: Long,
    val lowConfidenceDurationMs: Long,
)

class MonitoringRepository(private val database: PostureDatabase) {

    private val sessionDao get() = database.sessionDao()
    private val splitDao get() = database.sessionSplitDao()
    private val eventDao get() = database.postureEventDao()

    fun observeToday(date: LocalDate): Flow<DailySummary> =
        splitDao.observeDays(date.toString()).map { rows -> rows.toDailySummary(date) }

    fun observeDays(fromDate: LocalDate): Flow<List<DailySummary>> =
        splitDao.observeDaysFrom(fromDate.toString()).map { rows ->
            rows.groupBy { it.date }.map { (date, dayRows) ->
                dayRows.toDailySummary(LocalDate.parse(date))
            }.sortedBy { it.date }
        }

    fun observeHours(date: LocalDate): Flow<List<HourlySummary>> =
        splitDao.observeHours(date.toString()).map { rows ->
            rows.groupBy { it.hour }.map { (hour, hourRows) ->
                HourlySummary(
                    date = date,
                    hour = hour,
                    monitoredDurationMs = hourRows.sumOf { it.monitoredMs },
                    normalDurationMs = hourRows.sumOf { it.normalMs },
                    abnormalDurationMs = hourRows.sumOf {
                        it.forwardHeadMs + it.headDownMs + it.headDownForwardMs + it.torsoLeanMs
                    },
                    lowConfidenceDurationMs = hourRows.sumOf { it.lowConfidenceMs },
                )
            }.sortedBy { it.hour }
        }

    fun observeRecentSessions(limit: Int = 50): Flow<List<SessionEntity>> =
        sessionDao.observeRecent(limit)

    fun observeSession(id: String): Flow<SessionEntity?> = sessionDao.observeById(id)

    fun observeEvents(sessionId: String): Flow<List<PostureEventEntity>> =
        eventDao.observeForSession(sessionId)

    suspend fun checkpointSession(
        session: SessionEntity,
        days: List<DayAggregate>,
        hours: List<HourAggregate>,
        newEvents: List<RecordedPostureEvent>,
    ) {
        database.withTransaction {
            sessionDao.upsert(session)
            splitDao.deleteDaysForSession(session.id)
            splitDao.deleteHoursForSession(session.id)
            if (days.isNotEmpty()) {
                splitDao.insertDays(days.map { it.toEntity(session.id) })
            }
            if (hours.isNotEmpty()) {
                splitDao.insertHours(hours.map { it.toEntity(session.id) })
            }
            if (newEvents.isNotEmpty()) {
                eventDao.insertAll(newEvents.map { it.toEntity(session.id) })
            }
        }
    }

    suspend fun deleteSession(sessionId: String) {
        database.withTransaction {
            sessionDao.delete(sessionId)
            splitDao.deleteDaysForSession(sessionId)
            splitDao.deleteHoursForSession(sessionId)
            eventDao.deleteForSession(sessionId)
        }
    }

    suspend fun clearAll() {
        database.withTransaction {
            sessionDao.deleteAll()
            splitDao.deleteAllDays()
            splitDao.deleteAllHours()
            eventDao.deleteAll()
        }
    }

    suspend fun finalizeAbandonedSessions() {
        sessionDao.finalizeAbandoned()
    }

    private fun List<SessionDayEntity>.toDailySummary(date: LocalDate): DailySummary = DailySummary(
        date = date,
        monitoredDurationMs = sumOf { it.monitoredMs },
        validDurationMs = sumOf { it.validMs },
        normalDurationMs = sumOf { it.normalMs },
        forwardHeadDurationMs = sumOf { it.forwardHeadMs },
        headDownDurationMs = sumOf { it.headDownMs },
        headDownForwardDurationMs = sumOf { it.headDownForwardMs },
        torsoLeanDurationMs = sumOf { it.torsoLeanMs },
        lowConfidenceDurationMs = sumOf { it.lowConfidenceMs },
        eventCount = sumOf { it.eventCount },
    )

    private fun DayAggregate.toEntity(sessionId: String): SessionDayEntity = SessionDayEntity(
        sessionId = sessionId,
        date = date.toString(),
        monitoredMs = monitoredMs,
        validMs = validMs,
        normalMs = durations.normalMs,
        forwardHeadMs = durations.forwardHeadMs,
        headDownMs = durations.headDownMs,
        headDownForwardMs = durations.headDownForwardMs,
        torsoLeanMs = durations.torsoLeanMs,
        lowConfidenceMs = durations.lowConfidenceMs,
        eventCount = eventCount,
    )

    private fun HourAggregate.toEntity(sessionId: String): SessionHourEntity = SessionHourEntity(
        sessionId = sessionId,
        date = key.date.toString(),
        hour = key.hour,
        monitoredMs = monitoredMs,
        normalMs = durations.normalMs,
        forwardHeadMs = durations.forwardHeadMs,
        headDownMs = durations.headDownMs,
        headDownForwardMs = durations.headDownForwardMs,
        torsoLeanMs = durations.torsoLeanMs,
        lowConfidenceMs = durations.lowConfidenceMs,
    )

    private fun RecordedPostureEvent.toEntity(sessionId: String): PostureEventEntity = PostureEventEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        type = condition.name,
        startedAt = startedAtMs,
        endedAt = endedAtMs,
        durationMs = durationMs,
        severity = severity.name,
        peakForwardDelta = peakForwardHead,
        peakHeadDownDelta = peakHeadDown,
        peakTorsoLeanDelta = peakTorsoLean,
    )
}
