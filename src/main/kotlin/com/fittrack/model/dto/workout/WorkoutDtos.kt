package com.fittrack.model.dto.workout

import com.fittrack.model.WorkoutType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ========== Nested Response Objects ==========

data class WorkoutTypeInfo(
    val type: WorkoutType,
    val metValue: BigDecimal,
    val description: String,
    val estimatedCaloriesPerHour: Int? = null
)

data class CalculationDetails(
    val weightUsed: BigDecimal,
    val metValue: BigDecimal,
    val formula: String = "MET * weight * (duration/60)"
)

data class PageInfo(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class WorkoutSummary(
    val totalWorkouts: Int,
    val totalDuration: Int,
    val totalCalories: Int,
    val avgDuration: Int,
    val avgCalories: Int
)

// ========== Workout List Response ==========

data class WorkoutListItem(
    val id: UUID,
    val date: LocalDate,
    val workoutType: WorkoutType,
    val name: String?,
    val durationMinutes: Int,
    val caloriesBurnedEstimated: BigDecimal?,
    val caloriesBurnedActual: BigDecimal?,
    val caloriesBurned: BigDecimal?,
    val notes: String?
)

data class WorkoutListResponse(
    val content: List<WorkoutListItem>,
    val page: PageInfo,
    val summary: WorkoutSummary
)

// ========== Workout Detail Response ==========

data class WorkoutDetailResponse(
    val id: UUID,
    val date: LocalDate,
    val workoutType: WorkoutType,
    val workoutTypeInfo: WorkoutTypeInfo,
    val name: String?,
    val durationMinutes: Int,
    val caloriesBurnedEstimated: BigDecimal?,
    val caloriesBurnedActual: BigDecimal?,
    val caloriesBurned: BigDecimal?,
    val notes: String?,
    val calculationDetails: CalculationDetails?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// ========== Workout Creation Response ==========

data class WorkoutCreateResponse(
    val id: UUID,
    val date: LocalDate,
    val workoutType: WorkoutType,
    val name: String?,
    val durationMinutes: Int,
    val caloriesBurnedEstimated: BigDecimal?,
    val caloriesBurnedActual: BigDecimal?,
    val caloriesBurned: BigDecimal?,
    val notes: String?,
    val createdAt: Instant
)

// ========== Workout Requests ==========

data class CreateWorkoutRequest(
    @field:NotNull(message = "Date is required")
    val date: LocalDate,

    @field:NotNull(message = "Workout type is required")
    val workoutType: WorkoutType,

    @field:Size(max = 200, message = "Name cannot exceed 200 characters")
    val name: String? = null,

    @field:NotNull(message = "Duration is required")
    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int,

    @field:DecimalMin(value = "0", message = "Calories must be >= 0")
    val caloriesBurnedActual: BigDecimal? = null,

    @field:Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    val notes: String? = null
)

data class UpdateWorkoutRequest(
    val workoutType: WorkoutType? = null,

    @field:Size(max = 200, message = "Name cannot exceed 200 characters")
    val name: String? = null,

    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int? = null,

    @field:DecimalMin(value = "0", message = "Calories must be >= 0")
    val caloriesBurnedActual: BigDecimal? = null,

    @field:Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    val notes: String? = null,

    // Flag to explicitly clear the actual calories (set to estimate)
    val clearActualCalories: Boolean = false
)

// ========== Workout Types Response ==========

data class WorkoutTypesResponse(
    val workoutTypes: List<WorkoutTypeInfo>
)

// ========== Calorie Estimate Response ==========

data class CalorieEstimateResponse(
    val workoutType: WorkoutType,
    val durationMinutes: Int,
    val estimatedCalories: Int,
    val calculation: CalculationDetails
)

// ========== Daily Balance Response ==========

data class CaloriesBurned(
    val fromWorkouts: Int,
    val workoutCount: Int
)

data class BalanceDetails(
    val withoutWorkout: Int,
    val withWorkout: Int
)

data class DailyWorkoutSummary(
    val id: UUID,
    val workoutType: WorkoutType,
    val name: String?,
    val durationMinutes: Int,
    val caloriesBurned: Int
)

data class DailyBalanceResponse(
    val date: LocalDate,
    val calorieGoal: Int,
    val caloriesConsumed: Int,
    val caloriesBurned: CaloriesBurned,
    val netCalories: Int,
    val remainingToGoal: Int,
    val balance: BalanceDetails,
    val workouts: List<DailyWorkoutSummary>
)

// ========== Workout Summary/Analytics Response ==========

data class PeriodInfo(
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class SummaryStats(
    val totalWorkouts: Int,
    val totalDurationMinutes: Int,
    val totalCaloriesBurned: Int,
    val averageWorkoutsPerWeek: Double,
    val averageDurationMinutes: Int,
    val averageCaloriesBurned: Int,
    val longestWorkout: Int,
    val mostCaloriesBurned: Int
)

data class WorkoutByType(
    val workoutType: WorkoutType,
    val count: Int,
    val totalDuration: Int,
    val totalCalories: Int,
    val percentOfTotal: Double
)

data class WeeklyTrend(
    val weekStart: LocalDate,
    val workouts: Int,
    val calories: Int
)

data class WorkoutSummaryResponse(
    val period: PeriodInfo,
    val summary: SummaryStats,
    val byType: List<WorkoutByType>,
    val weeklyTrend: List<WeeklyTrend>
)
