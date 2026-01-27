package com.fittrack.model.dto.bodymetrics

import com.fittrack.model.PhotoPosition
import com.fittrack.model.TrendDirection
import com.fittrack.model.dto.food.PageInfo
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ========== Body Metrics Responses ==========

data class BodyMetricsListItem(
    val id: UUID,
    val date: LocalDate,
    val weightKg: BigDecimal?,
    val bodyFatPercentage: BigDecimal?,
    val bodyFatKg: BigDecimal?,
    val muscleMassPercentage: BigDecimal?,
    val muscleMassKg: BigDecimal?,
    val hasPhotos: Boolean,
    val photoCount: Int
)

data class BodyMetricsSummary(
    val latestWeight: BigDecimal?,
    val oldestWeight: BigDecimal?,
    val weightChange: BigDecimal?,
    val latestBodyFat: BigDecimal?,
    val oldestBodyFat: BigDecimal?,
    val bodyFatChange: BigDecimal?
)

data class BodyMetricsListResponse(
    val content: List<BodyMetricsListItem>,
    val page: PageInfo,
    val summary: BodyMetricsSummary?
)

data class PhotoResponse(
    val id: UUID,
    val position: PhotoPosition,
    val imageUrl: String,
    val createdAt: Instant
)

data class BodyMetricsDetailResponse(
    val id: UUID,
    val date: LocalDate,
    val weightKg: BigDecimal?,
    val bodyFatPercentage: BigDecimal?,
    val bodyFatKg: BigDecimal?,
    val muscleMassPercentage: BigDecimal?,
    val muscleMassKg: BigDecimal?,
    val notes: String?,
    val photos: List<PhotoResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class BodyMetricsLatestResponse(
    val id: UUID,
    val date: LocalDate,
    val weightKg: BigDecimal?,
    val bodyFatPercentage: BigDecimal?,
    val bodyFatKg: BigDecimal?,
    val muscleMassPercentage: BigDecimal?,
    val muscleMassKg: BigDecimal?,
    val hasPhotos: Boolean
)

// ========== Trends Response ==========

data class MetricTrend(
    val start: BigDecimal?,
    val end: BigDecimal?,
    val min: BigDecimal?,
    val max: BigDecimal?,
    val average: BigDecimal?,
    val change: BigDecimal?,
    val changePercent: BigDecimal?,
    val trend: TrendDirection
)

data class TrendsResponse(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dataPoints: Int,
    val weight: MetricTrend?,
    val bodyFat: MetricTrend?,
    val muscleMass: MetricTrend?
)

// ========== Body Metrics Requests ==========

data class CreateBodyMetricsRequest(
    @field:NotNull(message = "Date is required")
    val date: LocalDate,

    @field:DecimalMin(value = "0.1", message = "Weight must be greater than 0")
    @field:DecimalMax(value = "500", message = "Weight cannot exceed 500 kg")
    val weightKg: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Body fat percentage must be >= 0")
    @field:DecimalMax(value = "100", message = "Body fat percentage cannot exceed 100")
    val bodyFatPercentage: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Muscle mass percentage must be >= 0")
    @field:DecimalMax(value = "100", message = "Muscle mass percentage cannot exceed 100")
    val muscleMassPercentage: BigDecimal? = null,

    @field:Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    val notes: String? = null
)

data class UpdateBodyMetricsRequest(
    @field:DecimalMin(value = "0.1", message = "Weight must be greater than 0")
    @field:DecimalMax(value = "500", message = "Weight cannot exceed 500 kg")
    val weightKg: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Body fat percentage must be >= 0")
    @field:DecimalMax(value = "100", message = "Body fat percentage cannot exceed 100")
    val bodyFatPercentage: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Muscle mass percentage must be >= 0")
    @field:DecimalMax(value = "100", message = "Muscle mass percentage cannot exceed 100")
    val muscleMassPercentage: BigDecimal? = null,

    @field:Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    val notes: String? = null
)

// ========== Progress Photos Requests ==========

data class CreatePhotoRequest(
    @field:NotNull(message = "Position is required")
    val position: PhotoPosition,

    @field:NotNull(message = "Image URL is required")
    @field:Size(max = 500, message = "Image URL cannot exceed 500 characters")
    val imageUrl: String
)

// ========== Photos Timeline Response ==========

data class PhotoTimelineItem(
    val date: LocalDate,
    val metricsId: UUID,
    val position: PhotoPosition,
    val imageUrl: String,
    val weightKg: BigDecimal?
)

data class PhotoTimelineResponse(
    val photos: List<PhotoTimelineItem>
)
