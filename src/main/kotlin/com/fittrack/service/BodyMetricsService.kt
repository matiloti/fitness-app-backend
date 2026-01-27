package com.fittrack.service

import com.fittrack.exception.BodyMetricsNotFoundException
import com.fittrack.exception.BodyMetricsNotOwnedException
import com.fittrack.exception.NoMeasurementsProvidedException
import com.fittrack.exception.PhotoNotFoundException
import com.fittrack.model.BodyMetrics
import com.fittrack.model.PhotoPosition
import com.fittrack.model.ProgressPhoto
import com.fittrack.model.TrendDirection
import com.fittrack.model.dto.bodymetrics.BodyMetricsDetailResponse
import com.fittrack.model.dto.bodymetrics.BodyMetricsLatestResponse
import com.fittrack.model.dto.bodymetrics.BodyMetricsListItem
import com.fittrack.model.dto.bodymetrics.BodyMetricsListResponse
import com.fittrack.model.dto.bodymetrics.BodyMetricsSummary
import com.fittrack.model.dto.bodymetrics.CreateBodyMetricsRequest
import com.fittrack.model.dto.bodymetrics.CreatePhotoRequest
import com.fittrack.model.dto.bodymetrics.MetricTrend
import com.fittrack.model.dto.bodymetrics.PhotoResponse
import com.fittrack.model.dto.bodymetrics.PhotoTimelineItem
import com.fittrack.model.dto.bodymetrics.PhotoTimelineResponse
import com.fittrack.model.dto.bodymetrics.TrendsResponse
import com.fittrack.model.dto.bodymetrics.UpdateBodyMetricsRequest
import com.fittrack.model.dto.food.PageInfo
import com.fittrack.repository.BodyMetricsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

/**
 * Result of create operation indicating if new entry was created or existing updated
 */
data class CreateResult(
    val response: BodyMetricsDetailResponse,
    val isCreated: Boolean
)

@Service
class BodyMetricsService(
    private val bodyMetricsRepository: BodyMetricsRepository
) {

    fun getBodyMetrics(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        page: Int,
        size: Int,
        hasPhotos: Boolean?
    ): BodyMetricsListResponse {
        val validatedSize = size.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)

        val entries = bodyMetricsRepository.findAllByProfileId(
            profileId = profileId,
            startDate = startDate,
            endDate = endDate,
            page = validatedPage,
            size = validatedSize,
            hasPhotos = hasPhotos
        )

        val totalElements = bodyMetricsRepository.countByProfileId(
            profileId = profileId,
            startDate = startDate,
            endDate = endDate,
            hasPhotos = hasPhotos
        )

        val content = entries.map { metrics ->
            val photoCount = bodyMetricsRepository.countPhotosByMetricsId(metrics.id)
            toListItem(metrics, photoCount)
        }

        // Calculate summary from returned entries
        val summary = if (content.isNotEmpty()) {
            calculateSummary(content)
        } else {
            null
        }

        return BodyMetricsListResponse(
            content = content,
            page = PageInfo(
                number = validatedPage,
                size = validatedSize,
                totalElements = totalElements,
                totalPages = ceil(totalElements.toDouble() / validatedSize).toInt()
            ),
            summary = summary
        )
    }

    fun getBodyMetricsById(id: UUID, profileId: UUID): BodyMetricsDetailResponse {
        val metrics = bodyMetricsRepository.findById(id)
            ?: throw BodyMetricsNotFoundException()

        if (metrics.profileId != profileId) {
            throw BodyMetricsNotOwnedException()
        }

        val photos = bodyMetricsRepository.findPhotosByMetricsId(id)
        return toDetailResponse(metrics, photos)
    }

    fun getBodyMetricsByDate(date: LocalDate, profileId: UUID): BodyMetricsDetailResponse? {
        val metrics = bodyMetricsRepository.findByDateAndProfileId(date, profileId)
            ?: return null

        val photos = bodyMetricsRepository.findPhotosByMetricsId(metrics.id)
        return toDetailResponse(metrics, photos)
    }

    @Transactional
    fun createBodyMetrics(request: CreateBodyMetricsRequest, profileId: UUID): CreateResult {
        // Validate at least one measurement is provided
        if (request.weightKg == null && request.bodyFatPercentage == null && request.muscleMassPercentage == null) {
            throw NoMeasurementsProvidedException()
        }

        // Calculate kg values from percentages if weight is provided
        val bodyFatKg = calculateKgFromPercentage(request.weightKg, request.bodyFatPercentage)
        val muscleMassKg = calculateKgFromPercentage(request.weightKg, request.muscleMassPercentage)

        // Check if entry for date already exists (upsert behavior)
        val existingMetrics = bodyMetricsRepository.findByDateAndProfileId(request.date, profileId)

        return if (existingMetrics != null) {
            // Update existing entry
            val updatedMetrics = bodyMetricsRepository.update(
                id = existingMetrics.id,
                weightKg = request.weightKg,
                bodyFatPercentage = request.bodyFatPercentage,
                bodyFatKg = bodyFatKg,
                muscleMassPercentage = request.muscleMassPercentage,
                muscleMassKg = muscleMassKg,
                notes = request.notes,
                updateWeight = request.weightKg != null,
                updateBodyFat = request.bodyFatPercentage != null,
                updateMuscleMass = request.muscleMassPercentage != null,
                updateNotes = false // Don't update notes on upsert unless explicitly provided
            ) ?: throw BodyMetricsNotFoundException()

            val photos = bodyMetricsRepository.findPhotosByMetricsId(existingMetrics.id)
            CreateResult(toDetailResponse(updatedMetrics, photos), isCreated = false)
        } else {
            // Create new entry
            val newMetrics = bodyMetricsRepository.create(
                profileId = profileId,
                date = request.date,
                weightKg = request.weightKg,
                bodyFatPercentage = request.bodyFatPercentage,
                bodyFatKg = bodyFatKg,
                muscleMassPercentage = request.muscleMassPercentage,
                muscleMassKg = muscleMassKg,
                notes = request.notes
            )

            val photos = bodyMetricsRepository.findPhotosByMetricsId(newMetrics.id)
            CreateResult(toDetailResponse(newMetrics, photos), isCreated = true)
        }
    }

    @Transactional
    fun updateBodyMetrics(id: UUID, request: UpdateBodyMetricsRequest, profileId: UUID): BodyMetricsDetailResponse {
        val existingMetrics = bodyMetricsRepository.findById(id)
            ?: throw BodyMetricsNotFoundException()

        if (existingMetrics.profileId != profileId) {
            throw BodyMetricsNotOwnedException()
        }

        // Use existing weight if not provided in update for kg calculations
        val weightForCalc = request.weightKg ?: existingMetrics.weightKg

        // Calculate kg values
        val bodyFatKg = if (request.bodyFatPercentage != null) {
            calculateKgFromPercentage(weightForCalc, request.bodyFatPercentage)
        } else if (request.weightKg != null && existingMetrics.bodyFatPercentage != null) {
            // Recalculate with new weight
            calculateKgFromPercentage(request.weightKg, existingMetrics.bodyFatPercentage)
        } else {
            existingMetrics.bodyFatKg
        }

        val muscleMassKg = if (request.muscleMassPercentage != null) {
            calculateKgFromPercentage(weightForCalc, request.muscleMassPercentage)
        } else if (request.weightKg != null && existingMetrics.muscleMassPercentage != null) {
            // Recalculate with new weight
            calculateKgFromPercentage(request.weightKg, existingMetrics.muscleMassPercentage)
        } else {
            existingMetrics.muscleMassKg
        }

        val updatedMetrics = bodyMetricsRepository.update(
            id = id,
            weightKg = request.weightKg ?: existingMetrics.weightKg,
            bodyFatPercentage = request.bodyFatPercentage ?: existingMetrics.bodyFatPercentage,
            bodyFatKg = bodyFatKg,
            muscleMassPercentage = request.muscleMassPercentage ?: existingMetrics.muscleMassPercentage,
            muscleMassKg = muscleMassKg,
            notes = request.notes ?: existingMetrics.notes,
            updateWeight = request.weightKg != null,
            updateBodyFat = request.bodyFatPercentage != null,
            updateMuscleMass = request.muscleMassPercentage != null,
            updateNotes = request.notes != null
        ) ?: throw BodyMetricsNotFoundException()

        val photos = bodyMetricsRepository.findPhotosByMetricsId(id)
        return toDetailResponse(updatedMetrics, photos)
    }

    @Transactional
    fun deleteBodyMetrics(id: UUID, profileId: UUID) {
        val metrics = bodyMetricsRepository.findById(id)
            ?: throw BodyMetricsNotFoundException()

        if (metrics.profileId != profileId) {
            throw BodyMetricsNotOwnedException()
        }

        // Delete associated photos first
        bodyMetricsRepository.deletePhotosByMetricsId(id)
        bodyMetricsRepository.delete(id)
    }

    fun getLatestBodyMetrics(profileId: UUID): BodyMetricsLatestResponse? {
        val metrics = bodyMetricsRepository.findLatestByProfileId(profileId)
            ?: return null

        val photoCount = bodyMetricsRepository.countPhotosByMetricsId(metrics.id)

        return BodyMetricsLatestResponse(
            id = metrics.id,
            date = metrics.date,
            weightKg = metrics.weightKg,
            bodyFatPercentage = metrics.bodyFatPercentage,
            bodyFatKg = metrics.bodyFatKg,
            muscleMassPercentage = metrics.muscleMassPercentage,
            muscleMassKg = metrics.muscleMassKg,
            hasPhotos = photoCount > 0
        )
    }

    fun getTrends(profileId: UUID, period: String): TrendsResponse {
        val today = LocalDate.now()
        val startDate = when (period) {
            "7d" -> today.minusDays(7)
            "30d" -> today.minusDays(30)
            "90d" -> today.minusDays(90)
            "1y" -> today.minusYears(1)
            "all" -> LocalDate.of(2000, 1, 1) // Effectively all data
            else -> today.minusDays(30)
        }

        val entries = bodyMetricsRepository.findForTrends(profileId, startDate, today)

        if (entries.isEmpty()) {
            return TrendsResponse(
                period = period,
                startDate = startDate,
                endDate = today,
                dataPoints = 0,
                weight = null,
                bodyFat = null,
                muscleMass = null
            )
        }

        val actualStartDate = entries.first().date
        val actualEndDate = entries.last().date

        return TrendsResponse(
            period = period,
            startDate = actualStartDate,
            endDate = actualEndDate,
            dataPoints = entries.size,
            weight = calculateMetricTrend(entries.mapNotNull { it.weightKg }),
            bodyFat = calculateMetricTrend(entries.mapNotNull { it.bodyFatPercentage }),
            muscleMass = calculateMetricTrend(entries.mapNotNull { it.muscleMassPercentage })
        )
    }

    // ========== Progress Photos ==========

    fun getPhotos(metricsId: UUID, profileId: UUID): List<PhotoResponse> {
        val metrics = bodyMetricsRepository.findById(metricsId)
            ?: throw BodyMetricsNotFoundException()

        if (metrics.profileId != profileId) {
            throw BodyMetricsNotOwnedException()
        }

        val photos = bodyMetricsRepository.findPhotosByMetricsId(metricsId)
        return photos.map { toPhotoResponse(it) }
    }

    @Transactional
    fun addPhoto(metricsId: UUID, request: CreatePhotoRequest, profileId: UUID): PhotoResponse {
        val metrics = bodyMetricsRepository.findById(metricsId)
            ?: throw BodyMetricsNotFoundException()

        if (metrics.profileId != profileId) {
            throw BodyMetricsNotOwnedException()
        }

        // Check if photo already exists for this position
        val existingPhoto = bodyMetricsRepository.findPhotoByMetricsIdAndPosition(metricsId, request.position)

        val photo = if (existingPhoto != null) {
            // Update existing photo
            bodyMetricsRepository.updatePhoto(existingPhoto.id, request.imageUrl)
                ?: throw PhotoNotFoundException()
        } else {
            // Create new photo
            bodyMetricsRepository.createPhoto(metricsId, request.position, request.imageUrl)
        }

        return toPhotoResponse(photo)
    }

    @Transactional
    fun deletePhoto(metricsId: UUID, photoId: UUID, profileId: UUID) {
        val metrics = bodyMetricsRepository.findById(metricsId)
            ?: throw BodyMetricsNotFoundException()

        if (metrics.profileId != profileId) {
            throw BodyMetricsNotOwnedException()
        }

        val photo = bodyMetricsRepository.findPhotoByIdAndMetricsId(photoId, metricsId)
            ?: throw PhotoNotFoundException()

        bodyMetricsRepository.deletePhoto(photoId)
    }

    fun getPhotoTimeline(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        position: PhotoPosition?
    ): PhotoTimelineResponse {
        val photos = bodyMetricsRepository.findPhotosForTimeline(profileId, startDate, endDate, position)

        val items = photos.map { (photo, date, weightKg) ->
            PhotoTimelineItem(
                date = date,
                metricsId = photo.bodyMetricsId,
                position = photo.position,
                imageUrl = photo.imageUrl,
                weightKg = weightKg
            )
        }

        return PhotoTimelineResponse(photos = items)
    }

    // ========== Helper Methods ==========

    private fun calculateKgFromPercentage(weightKg: BigDecimal?, percentage: BigDecimal?): BigDecimal? {
        if (weightKg == null || percentage == null) return null
        return weightKg.multiply(percentage).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
    }

    private fun calculateSummary(entries: List<BodyMetricsListItem>): BodyMetricsSummary {
        // Entries are ordered by date DESC, so first is latest, last is oldest
        val latest = entries.first()
        val oldest = entries.last()

        val weightChange = if (latest.weightKg != null && oldest.weightKg != null) {
            latest.weightKg.subtract(oldest.weightKg)
        } else null

        val bodyFatChange = if (latest.bodyFatPercentage != null && oldest.bodyFatPercentage != null) {
            latest.bodyFatPercentage.subtract(oldest.bodyFatPercentage)
        } else null

        return BodyMetricsSummary(
            latestWeight = latest.weightKg,
            oldestWeight = oldest.weightKg,
            weightChange = weightChange,
            latestBodyFat = latest.bodyFatPercentage,
            oldestBodyFat = oldest.bodyFatPercentage,
            bodyFatChange = bodyFatChange
        )
    }

    private fun calculateMetricTrend(values: List<BigDecimal>): MetricTrend? {
        if (values.isEmpty()) return null

        val start = values.first()
        val end = values.last()
        val min = values.minOrNull() ?: start
        val max = values.maxOrNull() ?: start
        val average = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
            .divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)
        val change = end.subtract(start)
        val changePercent = if (start != BigDecimal.ZERO) {
            change.multiply(BigDecimal("100")).divide(start, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val trend = when {
            changePercent > BigDecimal("1") -> TrendDirection.INCREASING
            changePercent < BigDecimal("-1") -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }

        return MetricTrend(
            start = start,
            end = end,
            min = min,
            max = max,
            average = average,
            change = change,
            changePercent = changePercent,
            trend = trend
        )
    }

    private fun toListItem(metrics: BodyMetrics, photoCount: Int): BodyMetricsListItem {
        return BodyMetricsListItem(
            id = metrics.id,
            date = metrics.date,
            weightKg = metrics.weightKg,
            bodyFatPercentage = metrics.bodyFatPercentage,
            bodyFatKg = metrics.bodyFatKg,
            muscleMassPercentage = metrics.muscleMassPercentage,
            muscleMassKg = metrics.muscleMassKg,
            hasPhotos = photoCount > 0,
            photoCount = photoCount
        )
    }

    private fun toDetailResponse(metrics: BodyMetrics, photos: List<ProgressPhoto>): BodyMetricsDetailResponse {
        return BodyMetricsDetailResponse(
            id = metrics.id,
            date = metrics.date,
            weightKg = metrics.weightKg,
            bodyFatPercentage = metrics.bodyFatPercentage,
            bodyFatKg = metrics.bodyFatKg,
            muscleMassPercentage = metrics.muscleMassPercentage,
            muscleMassKg = metrics.muscleMassKg,
            notes = metrics.notes,
            photos = photos.map { toPhotoResponse(it) },
            createdAt = metrics.createdAt,
            updatedAt = metrics.updatedAt
        )
    }

    private fun toPhotoResponse(photo: ProgressPhoto): PhotoResponse {
        return PhotoResponse(
            id = photo.id,
            position = photo.position,
            imageUrl = photo.imageUrl,
            createdAt = photo.createdAt
        )
    }
}
