package com.fittrack.model.dto.analytics

import com.fittrack.model.TrendDirection
import java.math.BigDecimal
import java.time.LocalDate

// ========== Common Types ==========

/**
 * Time period for analytics queries
 */
enum class AnalyticsPeriod(val days: Int) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    YEAR_1(365),
    ALL(Int.MAX_VALUE);

    companion object {
        fun fromString(value: String): AnalyticsPeriod {
            return when (value.lowercase()) {
                "7d" -> DAYS_7
                "30d" -> DAYS_30
                "90d" -> DAYS_90
                "1y" -> YEAR_1
                "all" -> ALL
                else -> DAYS_30
            }
        }
    }
}

/**
 * Aggregation period for data grouping
 */
enum class AggregationPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    companion object {
        fun fromString(value: String): AggregationPeriod {
            return when (value.lowercase()) {
                "daily" -> DAILY
                "weekly" -> WEEKLY
                "monthly" -> MONTHLY
                else -> DAILY
            }
        }
    }
}

/**
 * Adherence status for calorie tracking
 */
enum class AdherenceStatus {
    UNDER,      // < 90% of goal
    ON_TARGET,  // 90-110% of goal
    OVER        // > 110% of goal
}

/**
 * Data point for time-series charts
 */
data class DataPoint<T>(
    val date: LocalDate,
    val value: T
)

/**
 * Trend line point for regression visualization
 */
data class TrendLinePoint(
    val date: LocalDate,
    val value: BigDecimal
)

// ========== Weight Trend ==========

data class WeightTrendResponse(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dataPoints: List<WeightDataPoint>,
    val trendLine: List<TrendLinePoint>?,
    val statistics: WeightStatistics?
)

data class WeightDataPoint(
    val date: LocalDate,
    val weight: BigDecimal
)

data class WeightStatistics(
    val startWeight: BigDecimal,
    val endWeight: BigDecimal,
    val minWeight: BigDecimal,
    val maxWeight: BigDecimal,
    val averageWeight: BigDecimal,
    val totalChange: BigDecimal,
    val changePercent: BigDecimal,
    val weeklyAvgChange: BigDecimal,
    val trend: TrendDirection,
    val ratePerWeek: BigDecimal
)

// ========== Body Composition Trend ==========

data class BodyCompositionTrendResponse(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dataPoints: List<BodyCompositionDataPoint>,
    val statistics: BodyCompositionStatistics?
)

data class BodyCompositionDataPoint(
    val date: LocalDate,
    val weight: BigDecimal?,
    val bodyFatPercentage: BigDecimal?,
    val bodyFatKg: BigDecimal?,
    val muscleMassPercentage: BigDecimal?,
    val muscleMassKg: BigDecimal?
)

data class BodyCompositionStatistics(
    val weight: MetricChange?,
    val bodyFat: MetricChange?,
    val muscleMass: MetricChange?
)

data class MetricChange(
    val start: BigDecimal,
    val end: BigDecimal,
    val change: BigDecimal,
    val changePercent: BigDecimal
)

// ========== Calorie Intake Trend ==========

data class CalorieIntakeTrendResponse(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyGoal: Int,
    val dataPoints: List<CalorieDataPoint>,
    val statistics: CalorieStatistics
)

data class CalorieDataPoint(
    val date: LocalDate,
    val consumed: Int,
    val goal: Int,
    val difference: Int,
    val adherence: AdherenceStatus,
    val workoutCalories: Int,
    val netCalories: Int
)

data class CalorieStatistics(
    val averageIntake: Int,
    val averageGoal: Int,
    val daysOnTarget: Int,
    val daysUnder: Int,
    val daysOver: Int,
    val adherenceRate: BigDecimal,
    val totalDeficit: Int,
    val averageDeficit: Int,
    val totalWorkoutCalories: Int
)

// ========== Macro Distribution ==========

data class MacroDistributionResponse(
    val date: LocalDate?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val daysWithData: Int?,
    val distribution: MacroDistribution,
    val total: MacroTotal,
    val targets: MacroTargets?,
    val comparison: MacroComparison?,
    val dailyBreakdown: List<DailyMacros>?
)

data class MacroDistribution(
    val protein: MacroDetail,
    val carbs: MacroDetail,
    val fat: MacroDetail
)

data class MacroDetail(
    val grams: Int,
    val calories: Int,
    val percent: BigDecimal
)

data class MacroTotal(
    val calories: Int,
    val grams: Int
)

data class MacroTargets(
    val protein: MacroTarget,
    val carbs: MacroTarget,
    val fat: MacroTarget
)

data class MacroTarget(
    val grams: Int,
    val percent: Int
)

data class MacroComparison(
    val protein: MacroComparisonDetail,
    val carbs: MacroComparisonDetail,
    val fat: MacroComparisonDetail
)

data class MacroComparisonDetail(
    val difference: Int,
    val percentOfTarget: BigDecimal
)

data class DailyMacros(
    val date: LocalDate,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

// ========== Dashboard Summary ==========

data class DashboardSummaryResponse(
    val today: TodaySummary,
    val week: WeekSummary,
    val progress: ProgressSummary?,
    val streaks: StreaksSummary
)

data class TodaySummary(
    val date: LocalDate,
    val caloriesConsumed: Int,
    val calorieGoal: Int,
    val caloriesRemaining: Int,
    val progressPercent: BigDecimal,
    val macros: MacroProgressSummary,
    val workouts: Int,
    val workoutCalories: Int
)

data class MacroProgressSummary(
    val protein: MacroProgress,
    val carbs: MacroProgress,
    val fat: MacroProgress
)

data class MacroProgress(
    val consumed: Int,
    val goal: Int,
    val percent: BigDecimal
)

data class WeekSummary(
    val averageCalories: Int,
    val adherenceRate: BigDecimal,
    val workouts: Int,
    val cheatMeals: Int
)

data class ProgressSummary(
    val currentWeight: BigDecimal,
    val startWeight: BigDecimal,
    val goalWeight: BigDecimal?,
    val weightLost: BigDecimal,
    val percentToGoal: BigDecimal?,
    val weeklyRate: BigDecimal
)

data class StreaksSummary(
    val currentLoggingStreak: Int,
    val longestLoggingStreak: Int,
    val currentWorkoutStreak: Int
)

// ========== Goal Progress ==========

data class GoalProgressResponse(
    val currentWeight: BigDecimal?,
    val targetWeight: BigDecimal?,
    val startWeight: BigDecimal?,
    val startDate: LocalDate?,
    val weightChange: BigDecimal?,
    val percentComplete: BigDecimal?,
    val estimatedCompletionDate: LocalDate?,
    val weeklyRate: BigDecimal?,
    val projectedWeightIn30Days: BigDecimal?,
    val isOnTrack: Boolean?,
    val daysToGoal: Int?
)

// ========== Streaks ==========

data class StreaksResponse(
    val logging: LoggingStreak,
    val workout: WorkoutStreak,
    val cheatMeals: CheatMealStats
)

data class LoggingStreak(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalDaysLogged: Int,
    val lastLoggedDate: LocalDate?
)

data class WorkoutStreak(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalWorkoutDays: Int,
    val lastWorkoutDate: LocalDate?
)

data class CheatMealStats(
    val totalThisMonth: Int,
    val totalThisWeek: Int,
    val averagePerWeek: BigDecimal,
    val lastCheatMealDate: LocalDate?
)

// ========== Aggregated Metrics ==========

data class AggregatedMetricsResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val aggregation: String,
    val data: List<AggregatedPeriodData>,
    val totals: AggregatedTotals
)

data class AggregatedPeriodData(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val weight: BigDecimal?,
    val bodyFat: BigDecimal?,
    val muscle: BigDecimal?,
    val calories: CalorieAggregate?,
    val protein: MacroAggregate?,
    val carbs: MacroAggregate?,
    val fat: MacroAggregate?,
    val cheatMeals: Int?,
    val workouts: Int?,
    val workoutCalories: Int?
)

data class CalorieAggregate(
    val total: Int,
    val average: Int
)

data class MacroAggregate(
    val total: Int,
    val average: Int
)

data class AggregatedTotals(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val cheatMeals: Int,
    val workouts: Int,
    val workoutCalories: Int
)

// ========== Workout Summary ==========

data class WorkoutSummaryResponse(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val summary: WorkoutSummaryStats,
    val byType: WorkoutTypeDistribution,
    val trend: List<WeeklyWorkoutTrend>,
    val comparison: WorkoutComparison?
)

data class WorkoutSummaryStats(
    val totalWorkouts: Int,
    val totalDurationMinutes: Int,
    val totalCaloriesBurned: Int,
    val averageWorkoutsPerWeek: BigDecimal,
    val averageDurationMinutes: Int,
    val averageCaloriesPerWorkout: Int,
    val consistency: WorkoutConsistency
)

data class WorkoutConsistency(
    val daysWithWorkouts: Int,
    val totalDays: Int,
    val consistencyPercent: BigDecimal
)

data class WorkoutTypeDistribution(
    val distribution: List<WorkoutTypeCount>,
    val caloriesByType: List<WorkoutTypeCalories>
)

data class WorkoutTypeCount(
    val type: String,
    val count: Int,
    val percent: BigDecimal
)

data class WorkoutTypeCalories(
    val type: String,
    val calories: Int
)

data class WeeklyWorkoutTrend(
    val week: String,
    val workouts: Int,
    val duration: Int,
    val calories: Int
)

data class WorkoutComparison(
    val previousPeriod: PreviousPeriodStats,
    val change: PeriodChange
)

data class PreviousPeriodStats(
    val workouts: Int,
    val calories: Int
)

data class PeriodChange(
    val workouts: Int,
    val workoutsPercent: BigDecimal,
    val calories: Int,
    val caloriesPercent: BigDecimal
)
