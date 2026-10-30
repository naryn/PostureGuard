package com.example.postureguard.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_profiles")
data class CalibrationEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val facingDirection: String,
    val selectedBodySide: String,
    val forwardOffsetMedian: Double,
    val headAngleMedianDeg: Double,
    val torsoAngleMedianDeg: Double,
    val torsoScaleMedian: Double,
    val forwardOffsetMad: Double,
    val headAngleMadDeg: Double,
    val torsoAngleMadDeg: Double,
    val qualityMedian: Double,
    val validSampleRatio: Double,
    val modelVersion: String,
    val isActive: Boolean,
)

@Entity(tableName = "monitoring_sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val totalDurationMs: Long,
    val validDurationMs: Long,
    val normalDurationMs: Long,
    val forwardHeadDurationMs: Long,
    val headDownDurationMs: Long,
    val headDownForwardDurationMs: Long,
    val torsoLeanDurationMs: Long,
    val lowConfidenceDurationMs: Long,
    val pausedDurationMs: Long,
    val calibrationProfileId: String,
    val finalized: Boolean,
)

@Entity(tableName = "session_days", primaryKeys = ["sessionId", "date"])
data class SessionDayEntity(
    val sessionId: String,
    val date: String,
    val monitoredMs: Long,
    val validMs: Long,
    val normalMs: Long,
    val forwardHeadMs: Long,
    val headDownMs: Long,
    val headDownForwardMs: Long,
    val torsoLeanMs: Long,
    val lowConfidenceMs: Long,
    val eventCount: Int,
)

@Entity(tableName = "session_hours", primaryKeys = ["sessionId", "date", "hour"])
data class SessionHourEntity(
    val sessionId: String,
    val date: String,
    val hour: Int,
    val monitoredMs: Long,
    val normalMs: Long,
    val forwardHeadMs: Long,
    val headDownMs: Long,
    val headDownForwardMs: Long,
    val torsoLeanMs: Long,
    val lowConfidenceMs: Long,
)

@Entity(
    tableName = "posture_events",
    indices = [Index("sessionId"), Index("startedAt")],
)
data class PostureEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val severity: String,
    val peakForwardDelta: Double?,
    val peakHeadDownDelta: Double?,
    val peakTorsoLeanDelta: Double?,
)
