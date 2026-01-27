package com.fittrack.controller

import com.fittrack.model.dto.day.ActivityLevelResponse
import com.fittrack.model.dto.day.DaySummaryResponse
import com.fittrack.model.dto.day.DaysRangeResponse
import com.fittrack.model.dto.day.GoalsResponse
import com.fittrack.model.dto.day.NavigationResponse
import com.fittrack.model.dto.day.UpdateActivityLevelRequest
import com.fittrack.model.dto.day.WeekOverviewResponse
import com.fittrack.security.UserPrincipal
import com.fittrack.service.DayService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/days")
class DayController(private val dayService: DayService) {

    /**
     * GET /api/v1/days/today - Get today's summary
     */
    @GetMapping("/today")
    fun getToday(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DaySummaryResponse> {
        val response = dayService.getTodaySummary(principal.id)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/days/{date} - Get summary for a specific date
     */
    @GetMapping("/{date}")
    fun getDay(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DaySummaryResponse> {
        val response = dayService.getDaySummary(principal.id, date)
        return ResponseEntity.ok(response)
    }

    /**
     * PUT /api/v1/days/{date}/activity-level - Set activity level override for a day
     */
    @PutMapping("/{date}/activity-level")
    fun updateActivityLevel(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @Valid @RequestBody request: UpdateActivityLevelRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ActivityLevelResponse> {
        val response = dayService.updateActivityLevel(principal.id, date, request)
        return ResponseEntity.ok(response)
    }

    /**
     * DELETE /api/v1/days/{date}/activity-level - Remove activity level override
     */
    @DeleteMapping("/{date}/activity-level")
    fun removeActivityLevelOverride(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ActivityLevelResponse> {
        val response = dayService.removeActivityLevelOverride(principal.id, date)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/days/goals - Get calculated nutritional goals
     */
    @GetMapping("/goals")
    fun getGoals(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<GoalsResponse> {
        val targetDate = date ?: LocalDate.now()
        val response = dayService.getGoals(principal.id, targetDate)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/days/range - Get summary for a date range
     */
    @GetMapping("/range")
    fun getDaysRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DaysRangeResponse> {
        val response = dayService.getDaysRange(principal.id, startDate, endDate)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/days/week - Get weekly calendar overview
     */
    @GetMapping("/week")
    fun getWeekOverview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekOf: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WeekOverviewResponse> {
        val targetDate = weekOf ?: LocalDate.now()
        val response = dayService.getWeekOverview(principal.id, targetDate)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/days/navigate - Get navigation context for day view
     */
    @GetMapping("/navigate")
    fun getNavigation(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<NavigationResponse> {
        val targetDate = date ?: LocalDate.now()
        val response = dayService.getNavigation(principal.id, targetDate)
        return ResponseEntity.ok(response)
    }
}
