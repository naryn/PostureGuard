package com.example.postureguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.postureguard.data.entity.CalibrationEntity
import com.example.postureguard.data.entity.PostureEventEntity
import com.example.postureguard.data.entity.SessionDayEntity
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.data.entity.SessionHourEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration_profiles WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<CalibrationEntity?>

    @Query("SELECT * FROM calibration_profiles WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeOnce(): CalibrationEntity?

    @Query("SELECT * FROM calibration_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CalibrationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: CalibrationEntity)

    @Query("UPDATE calibration_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("DELETE FROM calibration_profiles")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM monitoring_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT * FROM monitoring_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SessionEntity>

    @Query("SELECT * FROM monitoring_sessions WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    @Query("SELECT * FROM monitoring_sessions WHERE id = :id")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("UPDATE monitoring_sessions SET finalized = 1, endedAt = COALESCE(endedAt, startedAt + totalDurationMs) WHERE finalized = 0")
    suspend fun finalizeAbandoned()

    @Query("DELETE FROM monitoring_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM monitoring_sessions")
    suspend fun deleteAll()
}

@Dao
interface SessionSplitDao {
    @Query("SELECT * FROM session_days WHERE date = :date")
    fun observeDays(date: String): Flow<List<SessionDayEntity>>

    @Query("SELECT * FROM session_days WHERE date >= :fromDate ORDER BY date")
    fun observeDaysFrom(fromDate: String): Flow<List<SessionDayEntity>>

    @Query("SELECT * FROM session_days WHERE sessionId = :sessionId")
    suspend fun daysForSession(sessionId: String): List<SessionDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(days: List<SessionDayEntity>)

    @Query("DELETE FROM session_days WHERE sessionId = :sessionId")
    suspend fun deleteDaysForSession(sessionId: String)

    @Query("SELECT * FROM session_hours WHERE date = :date ORDER BY hour")
    fun observeHours(date: String): Flow<List<SessionHourEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHours(hours: List<SessionHourEntity>)

    @Query("DELETE FROM session_hours WHERE sessionId = :sessionId")
    suspend fun deleteHoursForSession(sessionId: String)

    @Query("DELETE FROM session_days")
    suspend fun deleteAllDays()

    @Query("DELETE FROM session_hours")
    suspend fun deleteAllHours()
}

@Dao
interface PostureEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PostureEventEntity>)

    @Query("SELECT * FROM posture_events WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    fun observeForSession(sessionId: String): Flow<List<PostureEventEntity>>

    @Query("SELECT * FROM posture_events WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    suspend fun forSession(sessionId: String): List<PostureEventEntity>

    @Query("DELETE FROM posture_events WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM posture_events")
    suspend fun deleteAll()
}
