package com.fittrack.model.dto.day

import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.MealType
import com.fittrack.model.Sex
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ========== Nested Response Objects ==========

data class ActivityLevelInfo(
    val level: ActivityLevel,
    val multiplier: Double,
    val isOverride: Boolean
)

data class NutritionTotals(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

data class NutritionProgress(
    val caloriesPercent: Double,
    val proteinPercent: Double,
    val carbsPercent: Double,
    val fatPercent: Double
)

data class MealSummary(
    val id: UUID,
    val mealType: MealType,
    val isCheatMeal: Boolean,
    val totals: MealNutritionTotals,
    val itemCount: Int
)

data class MealNutritionTotals(
    val calories: Int,
    val protein: Int
)

data class BodyMetricSummary(
    val id: UUID,
    val weightKg: BigDecimal?,
    val bodyFatPercentage: BigDecimal?,
    val hasPhotos: Boolean
)

// ========== Day Summary Response (GET /today, GET /{date}) ==========

data class DaySummaryResponse(
    val date: LocalDate,
    val dayId: UUID,
    val activityLevel: ActivityLevelInfo,
    val goals: NutritionTotals,
    val consumed: NutritionTotals,
    val remaining: NutritionTotals,
    val progress: NutritionProgress,
    val meals: List<MealSummary>,
    val bodyMetrics: BodyMetricSummary?
)

// ========== Activity Level Update ==========

data class UpdateActivityLevelRequest(
    @field:NotNull(message = "Activity level is required")
    val activityLevel: ActivityLevel
)

data class ActivityLevelResponse(
    val date: LocalDate,
    val activityLevel: ActivityLevelInfo,
    val goals: NutritionTotals,
    val defaultActivityLevel: ActivityLevelInfo?
)

// ========== Goals Response (GET /goals) ==========

data class ProfileSummary(
    val sex: Sex?,
    val heightCm: BigDecimal?,
    val age: Int?
)

data class FitnessGoalInfo(
    val type: FitnessGoalType,
    val intensity: FitnessGoalIntensity?,
    val adjustment: Int
)

data class MacroBreakdown(
    val grams: Int,
    val percent: Int,
    val calories: Int
)

data class MacroGoals(
    val protein: MacroBreakdown,
    val carbs: MacroBreakdown,
    val fat: MacroBreakdown
)

data class CalculationsInfo(
    val bmr: Int,
    val tdee: Int,
    val dailyCalorieGoal: Int,
    val macros: MacroGoals
)

data class GoalsResponse(
    val date: LocalDate,
    val profile: ProfileSummary,
    val latestWeight: BigDecimal?,
    val weightDate: LocalDate?,
    val activityLevel: ActivityLevelInfo,
    val fitnessGoal: FitnessGoalInfo,
    val calculations: CalculationsInfo
)

// ========== Date Range Summary (GET /range) ==========

enum class AdherenceStatus {
    UNDER,
    ON_TARGET,
    OVER
}

data class DayRangeSummaryItem(
    val date: LocalDate,
    val activityLevel: ActivityLevel,
    val isActivityOverride: Boolean,
    val goals: NutritionTotals,
    val consumed: NutritionTotals,
    val adherence: AdherenceInfo,
    val mealCount: Int,
    val cheatMealCount: Int,
    val hasBodyMetrics: Boolean
)

data class AdherenceInfo(
    val calories: AdherenceStatus,
    val caloriesPercent: Double
)

data class RangeSummary(
    val totalDays: Int,
    val daysWithMeals: Int,
    val averageCalories: Int,
    val averageProtein: Int,
    val totalCheatMeals: Int,
    val adherenceRate: Double
)

data class DaysRangeResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<DayRangeSummaryItem>,
    val summary: RangeSummary
)

// ========== Weekly Calendar Overview (GET /week) ==========

data class WeekDaySummary(
    val date: LocalDate,
    val dayOfWeek: String,
    val adherence: AdherenceStatus?,
    val caloriesPercent: Double?,
    val hasCheatMeal: Boolean,
    val hasBodyMetrics: Boolean
)

data class WeekSummary(
    val averageCalories: Int,
    val calorieGoal: Int,
    val adherenceRate: Double,
    val cheatMealCount: Int,
    val daysWithBodyMetrics: Int
)

data class WeekOverviewResponse(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val days: List<WeekDaySummary>,
    val weekSummary: WeekSummary
)

// ========== Navigation Context (GET /navigate) ==========

data class DayNavigationInfo(
    val date: LocalDate,
    val isToday: Boolean?,
    val hasMeals: Boolean
)

data class NavigationResponse(
    val current: DayNavigationInfo,
    val previous: DayNavigationInfo?,
    val next: DayNavigationInfo?,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?
)
