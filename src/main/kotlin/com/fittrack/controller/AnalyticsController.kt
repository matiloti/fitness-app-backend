package com.fittrack.controller

import com.fittrack.model.dto.analytics.*
import com.fittrack.security.UserPrincipal
import com.fittrack.service.AnalyticsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(private val analyticsService: AnalyticsService) {

    /**
     * GET /api/v1/analytics/weight
     * Get weight trend data for charting
     */
    @GetMapping("/weight")
    fun getWeightTrend(
        @RequestParam(defaultValue = "30d") period: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WeightTrendResponse> {
        val response = analyticsService.getWeightTrend(principal.id, period)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/body-composition
     * Get body composition trend data (weight, body fat, muscle)
     */
    @GetMapping("/body-composition")
    fun getBodyCompositionTrend(
        @RequestParam(defaultValue = "30d") period: String,
        @RequestParam(required = false) metrics: String?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BodyCompositionTrendResponse> {
        val metricsList = metrics?.split(",")?.map { it.trim() }
        val response = analyticsService.getBodyCompositionTrend(principal.id, period, metricsList)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/calories
     * Get calorie intake trend data
     */
    @GetMapping("/calories")
    fun getCalorieIntakeTrend(
        @RequestParam(defaultValue = "30d") period: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CalorieIntakeTrendResponse> {
        val response = analyticsService.getCalorieIntakeTrend(principal.id, period)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/macros
     * Get macronutrient distribution (single day or date range)
     */
    @GetMapping("/macros")
    fun getMacroDistribution(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MacroDistributionResponse> {
        val response = analyticsService.getMacroDistribution(principal.id, date, startDate, endDate)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/summary
     * Get dashboard summary (today, week, progress, streaks)
     */
    @GetMapping("/summary")
    fun getDashboardSummary(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DashboardSummaryResponse> {
        val response = analyticsService.getDashboardSummary(principal.id)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/goals
     * Get goal progress and projections
     */
    @GetMapping("/goals")
    fun getGoalProgress(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<GoalProgressResponse> {
        val response = analyticsService.getGoalProgress(principal.id)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/streaks
     * Get logging and workout streaks
     */
    @GetMapping("/streaks")
    fun getStreaks(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<StreaksResponse> {
        val response = analyticsService.getStreaks(principal.id)
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/aggregated
     * Get aggregated metrics with flexible parameters
     */
    @GetMapping("/aggregated")
    fun getAggregatedMetrics(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) metrics: String?,
        @RequestParam(defaultValue = "daily") aggregation: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<AggregatedMetricsResponse> {
        val metricsList = metrics?.split(",")?.map { it.trim() }
        val response = analyticsService.getAggregatedMetrics(
            principal.id, startDate, endDate, metricsList, aggregation
        )
        return ResponseEntity.ok(response)
    }

    /**
     * GET /api/v1/analytics/workouts
     * Get workout summary and analytics
     */
    @GetMapping("/workouts")
    fun getWorkoutSummary(
        @RequestParam(defaultValue = "30d") period: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutSummaryResponse> {
        val response = analyticsService.getWorkoutSummary(principal.id, period)
        return ResponseEntity.ok(response)
    }
}
