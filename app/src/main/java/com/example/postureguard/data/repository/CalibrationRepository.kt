package com.example.postureguard.data.repository

import com.example.postureguard.data.dao.CalibrationDao
import com.example.postureguard.data.entity.CalibrationEntity
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.FacingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalibrationRepository(private val dao: CalibrationDao) {

    val activeProfile: Flow<CalibrationProfile?> = dao.observeActive().map { it?.currentOrNull()?.toDomain() }

    suspend fun activeOnce(): CalibrationProfile? = dao.activeOnce()?.currentOrNull()?.toDomain()

    private fun CalibrationEntity.currentOrNull(): CalibrationEntity? =
        takeIf { CalibrationProfile.isCurrent(it.modelVersion) }

    suspend fun save(profile: CalibrationProfile) {
        dao.deactivateAll()
        dao.insert(profile.toEntity())
    }

    suspend fun clear() = dao.deleteAll()

    private fun CalibrationEntity.toDomain(): CalibrationProfile = CalibrationProfile(
        id = id,
        createdAt = createdAt,
        facingDirection = runCatching { FacingDirection.valueOf(facingDirection) }
            .getOrDefault(FacingDirection.FACING_RIGHT),
        selectedBodySide = runCatching { BodySide.valueOf(selectedBodySide) }.getOrDefault(BodySide.LEFT),
        forwardOffsetMedian = forwardOffsetMedian,
        headAngleMedianDeg = headAngleMedianDeg,
        torsoAngleMedianDeg = torsoAngleMedianDeg,
        torsoScaleMedian = torsoScaleMedian,
        forwardOffsetMad = forwardOffsetMad,
        headAngleMadDeg = headAngleMadDeg,
        torsoAngleMadDeg = torsoAngleMadDeg,
        qualityMedian = qualityMedian,
        validSampleRatio = validSampleRatio,
        modelVersion = modelVersion,
        isActive = isActive,
    )

    private fun CalibrationProfile.toEntity(): CalibrationEntity = CalibrationEntity(
        id = id,
        createdAt = createdAt,
        facingDirection = facingDirection.name,
        selectedBodySide = selectedBodySide.name,
        forwardOffsetMedian = forwardOffsetMedian,
        headAngleMedianDeg = headAngleMedianDeg,
        torsoAngleMedianDeg = torsoAngleMedianDeg,
        torsoScaleMedian = torsoScaleMedian,
        forwardOffsetMad = forwardOffsetMad,
        headAngleMadDeg = headAngleMadDeg,
        torsoAngleMadDeg = torsoAngleMadDeg,
        qualityMedian = qualityMedian,
        validSampleRatio = validSampleRatio,
        modelVersion = modelVersion,
        isActive = isActive,
    )
}
