package com.fittrack.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Photo position for progress photos - maps to PostgreSQL photo_position enum
 */
enum class PhotoPosition {
    FRONT,
    BACK,
    LEFT,
    RIGHT
}

/**
 * Trend direction for body metrics
 */
enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE
}

/**
 * Body metrics entry domain model - maps to body_metrics table
 */
data class BodyMetrics(
    val id: UUID,
    val profileId: UUID,
    val date: LocalDate,
    val weightKg: BigDecimal?,
    val bodyFatPercentage: BigDecimal?,
    val bodyFatKg: BigDecimal?,
    val muscleMassPercentage: BigDecimal?,
    val muscleMassKg: BigDecimal?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Progress photo domain model - maps to progress_photos table
 */
data class ProgressPhoto(
    val id: UUID,
    val bodyMetricsId: UUID,
    val position: PhotoPosition,
    val imageUrl: String,
    val createdAt: Instant
)
