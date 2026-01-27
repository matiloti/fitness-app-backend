package com.fittrack.controller

import com.fittrack.model.WorkoutType
import com.fittrack.model.dto.workout.CalorieEstimateResponse
import com.fittrack.model.dto.workout.CreateWorkoutRequest
import com.fittrack.model.dto.workout.UpdateWorkoutRequest
import com.fittrack.model.dto.workout.WeeklySummaryResponse
import com.fittrack.model.dto.workout.WorkoutCreateResponse
import com.fittrack.model.dto.workout.WorkoutDetailResponse
import com.fittrack.model.dto.workout.WorkoutListResponse
import com.fittrack.model.dto.workout.WorkoutStatsResponse
import com.fittrack.model.dto.workout.WorkoutStreakResponse
import com.fittrack.model.dto.workout.WorkoutSummaryResponse
import com.fittrack.model.dto.workout.WorkoutTypesResponse
import com.fittrack.security.UserPrincipal
import com.fittrack.service.WorkoutService
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
@RequestMapping("/api/v1/workouts")
class WorkoutController(private val workoutService: WorkoutService) {

    @GetMapping
    fun getWorkouts(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) workoutType: WorkoutType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutListResponse> {
        val effectiveEndDate = endDate ?: LocalDate.now()
        val effectiveStartDate = startDate ?: effectiveEndDate.minusDays(30)

        val response = workoutService.getWorkouts(
            profileId = principal.id,
            startDate = effectiveStartDate,
            endDate = effectiveEndDate,
            workoutType = workoutType,
            page = page,
            size = size
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getWorkoutById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutDetailResponse> {
        val response = workoutService.getWorkoutById(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createWorkout(
        @Valid @RequestBody request: CreateWorkoutRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutCreateResponse> {
        val response = workoutService.createWorkout(request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    fun updateWorkout(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWorkoutRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutDetailResponse> {
        val response = workoutService.updateWorkout(id, request, principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun deleteWorkout(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        workoutService.deleteWorkout(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/types")
    fun getWorkoutTypes(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutTypesResponse> {
        val response = workoutService.getWorkoutTypes(principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/estimate")
    fun estimateCalories(
        @RequestParam workoutType: WorkoutType,
        @RequestParam durationMinutes: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CalorieEstimateResponse> {
        val response = workoutService.estimateCalories(principal.id, workoutType, durationMinutes)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/summary")
    fun getWorkoutSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutSummaryResponse> {
        val effectiveEndDate = endDate ?: LocalDate.now()
        val effectiveStartDate = startDate ?: effectiveEndDate.minusDays(30)

        val response = workoutService.getWorkoutSummary(
            profileId = principal.id,
            startDate = effectiveStartDate,
            endDate = effectiveEndDate
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/streak")
    fun getWorkoutStreak(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutStreakResponse> {
        val response = workoutService.getWorkoutStreak(principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/stats")
    fun getWorkoutStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WorkoutStatsResponse> {
        val effectiveEndDate = endDate ?: LocalDate.now()
        val effectiveStartDate = startDate ?: effectiveEndDate.minusDays(90)

        val response = workoutService.getWorkoutStats(
            profileId = principal.id,
            startDate = effectiveStartDate,
            endDate = effectiveEndDate
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/weekly")
    fun getWeeklySummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<WeeklySummaryResponse> {
        val effectiveDate = date ?: LocalDate.now()
        val response = workoutService.getWeeklySummary(principal.id, effectiveDate)
        return ResponseEntity.ok(response)
    }
}
