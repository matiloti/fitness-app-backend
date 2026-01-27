package com.fittrack.controller

import com.fittrack.model.PhotoPosition
import com.fittrack.model.dto.bodymetrics.BodyMetricsDetailResponse
import com.fittrack.model.dto.bodymetrics.BodyMetricsLatestResponse
import com.fittrack.model.dto.bodymetrics.BodyMetricsListResponse
import com.fittrack.model.dto.bodymetrics.CreateBodyMetricsRequest
import com.fittrack.model.dto.bodymetrics.CreatePhotoRequest
import com.fittrack.model.dto.bodymetrics.PhotoResponse
import com.fittrack.model.dto.bodymetrics.PhotoTimelineResponse
import com.fittrack.model.dto.bodymetrics.TrendsResponse
import com.fittrack.model.dto.bodymetrics.UpdateBodyMetricsRequest
import com.fittrack.security.UserPrincipal
import com.fittrack.service.BodyMetricsService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/body-metrics")
class BodyMetricsController(private val bodyMetricsService: BodyMetricsService) {

    @GetMapping
    fun getBodyMetrics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
        @RequestParam(required = false) hasPhotos: Boolean?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyMetricsListResponse> {
        val effectiveEndDate = endDate ?: LocalDate.now()
        val effectiveStartDate = startDate ?: effectiveEndDate.minusDays(30)

        val response = bodyMetricsService.getBodyMetrics(
            profileId = principal.id,
            startDate = effectiveStartDate,
            endDate = effectiveEndDate,
            page = page,
            size = size,
            hasPhotos = hasPhotos
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getBodyMetricsById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyMetricsDetailResponse> {
        val response = bodyMetricsService.getBodyMetricsById(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/date/{date}")
    fun getBodyMetricsByDate(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyMetricsDetailResponse> {
        val response = bodyMetricsService.getBodyMetricsByDate(date, principal.id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createBodyMetrics(
        @Valid @RequestBody request: CreateBodyMetricsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyMetricsDetailResponse> {
        val result = bodyMetricsService.createBodyMetrics(request, principal.id)
        return if (result.isCreated) {
            ResponseEntity.status(HttpStatus.CREATED).body(result.response)
        } else {
            ResponseEntity.ok(result.response)
        }
    }

    @PutMapping("/{id}")
    fun updateBodyMetrics(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBodyMetricsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyMetricsDetailResponse> {
        val response = bodyMetricsService.updateBodyMetrics(id, request, principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun deleteBodyMetrics(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        bodyMetricsService.deleteBodyMetrics(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/latest")
    fun getLatestBodyMetrics(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyMetricsLatestResponse> {
        val response = bodyMetricsService.getLatestBodyMetrics(principal.id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response)
    }

    @GetMapping("/trends")
    fun getTrends(
        @RequestParam(defaultValue = "30d") period: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<TrendsResponse> {
        val response = bodyMetricsService.getTrends(principal.id, period)
        return ResponseEntity.ok(response)
    }

    // ========== Progress Photos ==========

    @GetMapping("/{id}/photos")
    fun getPhotos(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<PhotoResponse>> {
        val response = bodyMetricsService.getPhotos(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/photos")
    fun addPhoto(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreatePhotoRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PhotoResponse> {
        val response = bodyMetricsService.addPhoto(id, request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    fun deletePhoto(
        @PathVariable id: UUID,
        @PathVariable photoId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        bodyMetricsService.deletePhoto(id, photoId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/photos")
    fun getPhotoTimeline(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) position: PhotoPosition?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PhotoTimelineResponse> {
        val effectiveEndDate = endDate ?: LocalDate.now()
        val effectiveStartDate = startDate ?: effectiveEndDate.minusDays(90)

        val response = bodyMetricsService.getPhotoTimeline(
            profileId = principal.id,
            startDate = effectiveStartDate,
            endDate = effectiveEndDate,
            position = position
        )
        return ResponseEntity.ok(response)
    }
}
