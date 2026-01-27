package com.fittrack.service

import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.model.TrendDirection
import com.fittrack.model.dto.analytics.*
import com.fittrack.repository.AnalyticsRepository
import com.fittrack.repository.BodyMetricsRepository
import com.fittrack.repository.DailyCalorieData
import com.fittrack.repository.DailyWeightData
import com.fittrack.repository.UserRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.*

@Service
class AnalyticsService(
    private val analyticsRepository: AnalyticsRepository,
    private val userRepository: UserRepository,
    private val nutritionCalculatorService: NutritionCalculatorService,
    private val bodyMetricsRepository: BodyMetricsRepository
) {

    companion object {
        private val ONE_HUNDRED = BigDecimal("100")
        private val SEVEN = BigDecimal("7")
        private const val MAX_DATE_RANGE_DAYS = 365L
        private const val DEFAULT_CALORIE_GOAL = 2000
    }

    // ========== Weight Trend ==========

    fun getWeightTrend(profileId: UUID, period: String): WeightTrendResponse {
        val analyticsPeriod = AnalyticsPeriod.fromString(period)
        val today = LocalDate.now()
        val startDate = calculateStartDate(today, analyticsPeriod, profileId)

        val weightData = analyticsRepository.getWeightData(profileId, startDate, today)

        if (weightData.isEmpty()) {
            return WeightTrendResponse(
                period = period,
                startDate = startDate,
                endDate = today,
                dataPoints = emptyList(),
                trendLine = null,
                statistics = null
            )
        }

        val dataPoints = weightData.map { WeightDataPoint(it.date, it.weightKg) }
        val statistics = calculateWeightStatistics(weightData, startDate, today)
        val trendLine = calculateTrendLine(weightData)

        return WeightTrendResponse(
            period = period,
            startDate = weightData.first().date,
            endDate = weightData.last().date,
            dataPoints = dataPoints,
            trendLine = trendLine,
            statistics = statistics
        )
    }

    // ========== Body Composition Trend ==========

    fun getBodyCompositionTrend(profileId: UUID, period: String, metrics: List<String>?): BodyCompositionTrendResponse {
        val analyticsPeriod = AnalyticsPeriod.fromString(period)
        val today = LocalDate.now()
        val startDate = calculateStartDate(today, analyticsPeriod, profileId)

        val bodyData = analyticsRepository.getBodyCompositionData(profileId, startDate, today)

        if (bodyData.isEmpty()) {
            return BodyCompositionTrendResponse(
                period = period,
                startDate = startDate,
                endDate = today,
                dataPoints = emptyList(),
                statistics = null
            )
        }

        val dataPoints = bodyData.map { data ->
            BodyCompositionDataPoint(
                date = data.date,
                weight = data.weightKg,
                bodyFatPercentage = data.bodyFatPercentage,
                bodyFatKg = data.bodyFatKg,
                muscleMassPercentage = data.muscleMassPercentage,
                muscleMassKg = data.muscleMassKg
            )
        }

        val statistics = calculateBodyCompositionStatistics(bodyData)

        return BodyCompositionTrendResponse(
            period = period,
            startDate = bodyData.first().date,
            endDate = bodyData.last().date,
            dataPoints = dataPoints,
            statistics = statistics
        )
    }

    // ========== Calorie Intake Trend ==========

    fun getCalorieIntakeTrend(profileId: UUID, period: String): CalorieIntakeTrendResponse {
        val analyticsPeriod = AnalyticsPeriod.fromString(period)
        val today = LocalDate.now()
        val startDate = calculateStartDate(today, analyticsPeriod, profileId)

        // Get user's daily calorie goal
        val calorieGoal = calculateCalorieGoalForProfile(profileId)

        val calorieData = analyticsRepository.getDailyCalorieData(profileId, startDate, today)

        if (calorieData.isEmpty()) {
            return CalorieIntakeTrendResponse(
                period = period,
                startDate = startDate,
                endDate = today,
                dailyGoal = calorieGoal,
                dataPoints = emptyList(),
                statistics = CalorieStatistics(
                    averageIntake = 0,
                    averageGoal = calorieGoal,
                    daysOnTarget = 0,
                    daysUnder = 0,
                    daysOver = 0,
                    adherenceRate = BigDecimal.ZERO,
                    totalDeficit = 0,
                    averageDeficit = 0,
                    totalWorkoutCalories = 0
                )
            )
        }

        // Get workout data for same period
        val workoutData = analyticsRepository.getWorkoutData(profileId, startDate, today)
        val workoutCaloriesByDate = workoutData.associate { it.date to it.totalCalories.toInt() }

        val dataPoints = calorieData.map { data ->
            val consumed = data.totalCalories.toInt()
            val workoutCals = workoutCaloriesByDate[data.date] ?: 0
            val difference = consumed - calorieGoal
            val adherence = calculateAdherence(consumed, calorieGoal)

            CalorieDataPoint(
                date = data.date,
                consumed = consumed,
                goal = calorieGoal,
                difference = difference,
                adherence = adherence,
                workoutCalories = workoutCals,
                netCalories = consumed - workoutCals
            )
        }

        val statistics = calculateCalorieStatistics(dataPoints, calorieGoal)

        return CalorieIntakeTrendResponse(
            period = period,
            startDate = calorieData.first().date,
            endDate = calorieData.last().date,
            dailyGoal = calorieGoal,
            dataPoints = dataPoints,
            statistics = statistics
        )
    }

    // ========== Macro Distribution ==========

    fun getMacroDistribution(
        profileId: UUID,
        date: LocalDate?,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): MacroDistributionResponse {
        // Single day mode
        if (date != null) {
            return getSingleDayMacros(profileId, date)
        }

        // Date range mode
        val effectiveStartDate = startDate ?: LocalDate.now().minusDays(7)
        val effectiveEndDate = endDate ?: LocalDate.now()

        validateDateRange(effectiveStartDate, effectiveEndDate)

        val calorieData = analyticsRepository.getDailyCalorieData(profileId, effectiveStartDate, effectiveEndDate)

        if (calorieData.isEmpty()) {
            return MacroDistributionResponse(
                date = null,
                startDate = effectiveStartDate,
                endDate = effectiveEndDate,
                daysWithData = 0,
                distribution = MacroDistribution(
                    protein = MacroDetail(0, 0, BigDecimal.ZERO),
                    carbs = MacroDetail(0, 0, BigDecimal.ZERO),
                    fat = MacroDetail(0, 0, BigDecimal.ZERO)
                ),
                total = MacroTotal(0, 0),
                targets = null,
                comparison = null,
                dailyBreakdown = emptyList()
            )
        }

        return getDateRangeMacros(profileId, effectiveStartDate, effectiveEndDate, calorieData)
    }

    // ========== Dashboard Summary ==========

    fun getDashboardSummary(profileId: UUID): DashboardSummaryResponse {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Today's summary
        val todaySummary = getTodaySummary(profileId, today)

        // Week summary
        val weekSummary = getWeekSummary(profileId, weekStart, today)

        // Progress summary
        val progressSummary = getProgressSummary(profileId)

        // Streaks summary
        val streaksSummary = getStreaksSummary(profileId)

        return DashboardSummaryResponse(
            today = todaySummary,
            week = weekSummary,
            progress = progressSummary,
            streaks = streaksSummary
        )
    }

    // ========== Goal Progress ==========

    fun getGoalProgress(profileId: UUID): GoalProgressResponse {
        val user = userRepository.findById(profileId) ?: return GoalProgressResponse(
            currentWeight = null,
            targetWeight = null,
            startWeight = null,
            startDate = null,
            weightChange = null,
            percentComplete = null,
            estimatedCompletionDate = null,
            weeklyRate = null,
            projectedWeightIn30Days = null,
            isOnTrack = null,
            daysToGoal = null
        )

        val firstWeight = analyticsRepository.getFirstWeight(profileId)
        val latestWeight = analyticsRepository.getLatestWeightBefore(profileId, LocalDate.now())

        if (firstWeight == null || latestWeight == null) {
            return GoalProgressResponse(
                currentWeight = latestWeight,
                targetWeight = null,
                startWeight = firstWeight?.second,
                startDate = firstWeight?.first,
                weightChange = null,
                percentComplete = null,
                estimatedCompletionDate = null,
                weeklyRate = null,
                projectedWeightIn30Days = null,
                isOnTrack = null,
                daysToGoal = null
            )
        }

        val weightChange = latestWeight.subtract(firstWeight.second)
        val daysSinceStart = ChronoUnit.DAYS.between(firstWeight.first, LocalDate.now()).toInt()
        val weeklyRate = if (daysSinceStart >= 7) {
            weightChange.multiply(SEVEN).divide(BigDecimal(daysSinceStart), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val projectedIn30Days = latestWeight.add(
            weeklyRate.multiply(BigDecimal("4.29"))  // ~30 days / 7
        ).setScale(1, RoundingMode.HALF_UP)

        return GoalProgressResponse(
            currentWeight = latestWeight,
            targetWeight = null, // Would need target weight in user profile
            startWeight = firstWeight.second,
            startDate = firstWeight.first,
            weightChange = weightChange,
            percentComplete = null,
            estimatedCompletionDate = null,
            weeklyRate = weeklyRate,
            projectedWeightIn30Days = projectedIn30Days,
            isOnTrack = null,
            daysToGoal = null
        )
    }

    // ========== Streaks ==========

    fun getStreaks(profileId: UUID): StreaksResponse {
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)
        val monthStart = today.withDayOfMonth(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Logging streak
        val loggingDates = analyticsRepository.getDatesWithMeals(profileId, oneYearAgo, today)
        val loggingStreak = calculateStreak(loggingDates, today)
        val longestLoggingStreak = calculateLongestStreak(loggingDates)

        // Workout streak
        val workoutDates = analyticsRepository.getDatesWithWorkouts(profileId, oneYearAgo, today)
        val workoutStreak = calculateStreak(workoutDates, today)
        val longestWorkoutStreak = calculateLongestStreak(workoutDates)

        // Cheat meal stats
        val cheatMealsThisMonth = analyticsRepository.countCheatMeals(profileId, monthStart, today)
        val cheatMealsThisWeek = analyticsRepository.countCheatMeals(profileId, weekStart, today)
        val totalCheatMeals = analyticsRepository.countCheatMeals(profileId, oneYearAgo, today)
        val weeksInPeriod = ChronoUnit.WEEKS.between(oneYearAgo, today).toInt().coerceAtLeast(1)
        val avgCheatMealsPerWeek = BigDecimal(totalCheatMeals)
            .divide(BigDecimal(weeksInPeriod), 2, RoundingMode.HALF_UP)
        val lastCheatMealDate = analyticsRepository.getLastCheatMealDate(profileId)

        return StreaksResponse(
            logging = LoggingStreak(
                currentStreak = loggingStreak,
                longestStreak = longestLoggingStreak,
                totalDaysLogged = loggingDates.size,
                lastLoggedDate = loggingDates.lastOrNull()
            ),
            workout = WorkoutStreak(
                currentStreak = workoutStreak,
                longestStreak = longestWorkoutStreak,
                totalWorkoutDays = workoutDates.size,
                lastWorkoutDate = workoutDates.lastOrNull()
            ),
            cheatMeals = CheatMealStats(
                totalThisMonth = cheatMealsThisMonth,
                totalThisWeek = cheatMealsThisWeek,
                averagePerWeek = avgCheatMealsPerWeek,
                lastCheatMealDate = lastCheatMealDate
            )
        )
    }

    // ========== Aggregated Metrics ==========

    fun getAggregatedMetrics(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        metrics: List<String>?,
        aggregation: String
    ): AggregatedMetricsResponse {
        validateDateRange(startDate, endDate)

        val period = AggregationPeriod.fromString(aggregation)

        // Get aggregated data based on period
        val weightData = analyticsRepository.getAggregatedWeightData(profileId, startDate, endDate, period)
        val calorieData = analyticsRepository.getAggregatedCalorieData(profileId, startDate, endDate, period)

        // Group data by period
        val allPeriods = (weightData.map { it.first } + calorieData.map { it.first }).distinct().sorted()

        val weightByPeriod = weightData.associate { it.first to it.second }
        val calorieByPeriod = calorieData.associate { it.first to Pair(it.second, it.third) }

        val data = allPeriods.map { periodStart ->
            val periodEnd = calculatePeriodEnd(periodStart, period)
            val weight = weightByPeriod[periodStart]
            val (calories, daysCount) = calorieByPeriod[periodStart] ?: Pair(BigDecimal.ZERO, 0)

            AggregatedPeriodData(
                periodStart = periodStart,
                periodEnd = periodEnd,
                weight = weight,
                bodyFat = null,
                muscle = null,
                calories = if (daysCount > 0) CalorieAggregate(
                    total = calories.toInt(),
                    average = calories.divide(BigDecimal(daysCount), 0, RoundingMode.HALF_UP).toInt()
                ) else null,
                protein = null,
                carbs = null,
                fat = null,
                cheatMeals = null,
                workouts = null,
                workoutCalories = null
            )
        }

        val totalCalories = calorieData.sumOf { it.second.toInt() }
        val totalWorkouts = analyticsRepository.countWorkouts(profileId, startDate, endDate)
        val totalCheatMeals = analyticsRepository.countCheatMeals(profileId, startDate, endDate)

        return AggregatedMetricsResponse(
            startDate = startDate,
            endDate = endDate,
            aggregation = aggregation,
            data = data,
            totals = AggregatedTotals(
                calories = totalCalories,
                protein = 0,
                carbs = 0,
                fat = 0,
                cheatMeals = totalCheatMeals,
                workouts = totalWorkouts,
                workoutCalories = 0
            )
        )
    }

    // ========== Workout Summary ==========

    fun getWorkoutSummary(profileId: UUID, period: String): WorkoutSummaryResponse {
        val analyticsPeriod = AnalyticsPeriod.fromString(period)
        val today = LocalDate.now()
        val startDate = calculateStartDate(today, analyticsPeriod, profileId)
        val totalDays = ChronoUnit.DAYS.between(startDate, today).toInt() + 1

        val workoutData = analyticsRepository.getWorkoutData(profileId, startDate, today)
        val typeDistribution = analyticsRepository.getWorkoutTypeDistribution(profileId, startDate, today)
        val caloriesByType = analyticsRepository.getWorkoutCaloriesByType(profileId, startDate, today)

        val totalWorkouts = workoutData.sumOf { it.workoutCount }
        val totalDuration = workoutData.sumOf { it.totalDuration }
        val totalCalories = workoutData.sumOf { it.totalCalories.toInt() }
        val daysWithWorkouts = workoutData.size
        val weeks = (totalDays / 7.0).coerceAtLeast(1.0)

        val summary = WorkoutSummaryStats(
            totalWorkouts = totalWorkouts,
            totalDurationMinutes = totalDuration,
            totalCaloriesBurned = totalCalories,
            averageWorkoutsPerWeek = BigDecimal(totalWorkouts / weeks).setScale(2, RoundingMode.HALF_UP),
            averageDurationMinutes = if (totalWorkouts > 0) totalDuration / totalWorkouts else 0,
            averageCaloriesPerWorkout = if (totalWorkouts > 0) totalCalories / totalWorkouts else 0,
            consistency = WorkoutConsistency(
                daysWithWorkouts = daysWithWorkouts,
                totalDays = totalDays,
                consistencyPercent = BigDecimal(daysWithWorkouts * 100.0 / totalDays).setScale(1, RoundingMode.HALF_UP)
            )
        )

        val typeDistributionResponse = WorkoutTypeDistribution(
            distribution = typeDistribution.map { (type, count) ->
                WorkoutTypeCount(
                    type = type,
                    count = count,
                    percent = BigDecimal(count * 100.0 / totalWorkouts.coerceAtLeast(1))
                        .setScale(1, RoundingMode.HALF_UP)
                )
            },
            caloriesByType = caloriesByType.map { (type, calories) ->
                WorkoutTypeCalories(type = type, calories = calories)
            }
        )

        // Calculate weekly trends
        val weeklyTrends = calculateWeeklyWorkoutTrends(workoutData, startDate, today)

        return WorkoutSummaryResponse(
            period = period,
            startDate = startDate,
            endDate = today,
            summary = summary,
            byType = typeDistributionResponse,
            trend = weeklyTrends,
            comparison = null // Would need previous period data
        )
    }

    // ========== Private Helper Methods ==========

    private fun calculateStartDate(today: LocalDate, period: AnalyticsPeriod, profileId: UUID): LocalDate {
        return when (period) {
            AnalyticsPeriod.ALL -> {
                val firstWeight = analyticsRepository.getFirstWeight(profileId)
                firstWeight?.first ?: today.minusYears(1)
            }
            else -> today.minusDays(period.days.toLong())
        }
    }

    private fun validateDateRange(startDate: LocalDate, endDate: LocalDate) {
        if (startDate.isAfter(endDate)) {
            throw InvalidDateRangeException("Start date must be before end date")
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_DATE_RANGE_DAYS) {
            throw InvalidDateRangeException("Date range cannot exceed $MAX_DATE_RANGE_DAYS days")
        }
    }

    private fun calculateWeightStatistics(
        data: List<DailyWeightData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): WeightStatistics {
        val weights = data.map { it.weightKg }
        val startWeight = weights.first()
        val endWeight = weights.last()
        val minWeight = weights.minOrNull() ?: startWeight
        val maxWeight = weights.maxOrNull() ?: startWeight
        val averageWeight = weights.fold(BigDecimal.ZERO) { acc, w -> acc.add(w) }
            .divide(BigDecimal(weights.size), 2, RoundingMode.HALF_UP)
        val totalChange = endWeight.subtract(startWeight)
        val changePercent = if (startWeight != BigDecimal.ZERO) {
            totalChange.multiply(ONE_HUNDRED).divide(startWeight, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val daysBetween = ChronoUnit.DAYS.between(data.first().date, data.last().date).toInt()
        val weeks = (daysBetween / 7.0).coerceAtLeast(1.0)
        val weeklyAvgChange = totalChange.divide(BigDecimal(weeks), 2, RoundingMode.HALF_UP)

        val trend = when {
            changePercent > BigDecimal("1") -> TrendDirection.INCREASING
            changePercent < BigDecimal("-1") -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }

        return WeightStatistics(
            startWeight = startWeight,
            endWeight = endWeight,
            minWeight = minWeight,
            maxWeight = maxWeight,
            averageWeight = averageWeight,
            totalChange = totalChange,
            changePercent = changePercent,
            weeklyAvgChange = weeklyAvgChange,
            trend = trend,
            ratePerWeek = weeklyAvgChange
        )
    }

    private fun calculateTrendLine(data: List<DailyWeightData>): List<TrendLinePoint>? {
        if (data.size < 2) return null

        // Simple linear regression
        val n = data.size
        val xMean = (n - 1) / 2.0
        val yMean = data.map { it.weightKg.toDouble() }.average()

        var numerator = 0.0
        var denominator = 0.0

        data.forEachIndexed { index, point ->
            val x = index.toDouble()
            val y = point.weightKg.toDouble()
            numerator += (x - xMean) * (y - yMean)
            denominator += (x - xMean) * (x - xMean)
        }

        if (denominator == 0.0) return null

        val slope = numerator / denominator
        val intercept = yMean - slope * xMean

        return listOf(
            TrendLinePoint(
                date = data.first().date,
                value = BigDecimal(intercept).setScale(1, RoundingMode.HALF_UP)
            ),
            TrendLinePoint(
                date = data.last().date,
                value = BigDecimal(intercept + slope * (n - 1)).setScale(1, RoundingMode.HALF_UP)
            )
        )
    }

    private fun calculateBodyCompositionStatistics(data: List<DailyWeightData>): BodyCompositionStatistics? {
        if (data.isEmpty()) return null

        val first = data.first()
        val last = data.last()

        return BodyCompositionStatistics(
            weight = calculateMetricChange(first.weightKg, last.weightKg),
            bodyFat = calculateMetricChange(first.bodyFatPercentage, last.bodyFatPercentage),
            muscleMass = calculateMetricChange(first.muscleMassPercentage, last.muscleMassPercentage)
        )
    }

    private fun calculateMetricChange(start: BigDecimal?, end: BigDecimal?): MetricChange? {
        if (start == null || end == null) return null

        val change = end.subtract(start)
        val changePercent = if (start != BigDecimal.ZERO) {
            change.multiply(ONE_HUNDRED).divide(start, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return MetricChange(
            start = start,
            end = end,
            change = change,
            changePercent = changePercent
        )
    }

    private fun calculateAdherence(consumed: Int, goal: Int): com.fittrack.model.dto.analytics.AdherenceStatus {
        val ratio = consumed.toDouble() / goal.coerceAtLeast(1)
        return when {
            ratio < 0.9 -> com.fittrack.model.dto.analytics.AdherenceStatus.UNDER
            ratio > 1.1 -> com.fittrack.model.dto.analytics.AdherenceStatus.OVER
            else -> com.fittrack.model.dto.analytics.AdherenceStatus.ON_TARGET
        }
    }

    private fun calculateCalorieStatistics(
        dataPoints: List<CalorieDataPoint>,
        calorieGoal: Int
    ): CalorieStatistics {
        val daysOnTarget = dataPoints.count { it.adherence == com.fittrack.model.dto.analytics.AdherenceStatus.ON_TARGET }
        val daysUnder = dataPoints.count { it.adherence == com.fittrack.model.dto.analytics.AdherenceStatus.UNDER }
        val daysOver = dataPoints.count { it.adherence == com.fittrack.model.dto.analytics.AdherenceStatus.OVER }
        val totalDays = dataPoints.size.coerceAtLeast(1)

        val avgIntake = dataPoints.map { it.consumed }.average().toInt()
        val totalDeficit = dataPoints.sumOf { it.difference }
        val totalWorkoutCalories = dataPoints.sumOf { it.workoutCalories }

        return CalorieStatistics(
            averageIntake = avgIntake,
            averageGoal = calorieGoal,
            daysOnTarget = daysOnTarget,
            daysUnder = daysUnder,
            daysOver = daysOver,
            adherenceRate = BigDecimal(daysOnTarget * 100.0 / totalDays).setScale(1, RoundingMode.HALF_UP),
            totalDeficit = totalDeficit,
            averageDeficit = totalDeficit / totalDays,
            totalWorkoutCalories = totalWorkoutCalories
        )
    }

    private fun getSingleDayMacros(profileId: UUID, date: LocalDate): MacroDistributionResponse {
        val macros = analyticsRepository.getDayMacros(profileId, date)
            ?: return MacroDistributionResponse(
                date = date,
                startDate = null,
                endDate = null,
                daysWithData = null,
                distribution = MacroDistribution(
                    protein = MacroDetail(0, 0, BigDecimal.ZERO),
                    carbs = MacroDetail(0, 0, BigDecimal.ZERO),
                    fat = MacroDetail(0, 0, BigDecimal.ZERO)
                ),
                total = MacroTotal(0, 0),
                targets = null,
                comparison = null,
                dailyBreakdown = null
            )

        val (protein, carbs, fat) = macros
        val proteinCals = protein.multiply(BigDecimal("4")).toInt()
        val carbsCals = carbs.multiply(BigDecimal("4")).toInt()
        val fatCals = fat.multiply(BigDecimal("9")).toInt()
        val totalCals = proteinCals + carbsCals + fatCals
        val totalGrams = protein.toInt() + carbs.toInt() + fat.toInt()

        val distribution = MacroDistribution(
            protein = MacroDetail(
                grams = protein.toInt(),
                calories = proteinCals,
                percent = if (totalCals > 0) BigDecimal(proteinCals * 100.0 / totalCals)
                    .setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
            ),
            carbs = MacroDetail(
                grams = carbs.toInt(),
                calories = carbsCals,
                percent = if (totalCals > 0) BigDecimal(carbsCals * 100.0 / totalCals)
                    .setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
            ),
            fat = MacroDetail(
                grams = fat.toInt(),
                calories = fatCals,
                percent = if (totalCals > 0) BigDecimal(fatCals * 100.0 / totalCals)
                    .setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
            )
        )

        return MacroDistributionResponse(
            date = date,
            startDate = null,
            endDate = null,
            daysWithData = null,
            distribution = distribution,
            total = MacroTotal(calories = totalCals, grams = totalGrams),
            targets = null,
            comparison = null,
            dailyBreakdown = null
        )
    }

    private fun getDateRangeMacros(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        calorieData: List<DailyCalorieData>
    ): MacroDistributionResponse {
        val avgProtein = calorieData.map { it.totalProtein }.fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(calorieData.size), 0, RoundingMode.HALF_UP).toInt()
        val avgCarbs = calorieData.map { it.totalCarbs }.fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(calorieData.size), 0, RoundingMode.HALF_UP).toInt()
        val avgFat = calorieData.map { it.totalFat }.fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(calorieData.size), 0, RoundingMode.HALF_UP).toInt()

        val proteinCals = avgProtein * 4
        val carbsCals = avgCarbs * 4
        val fatCals = avgFat * 9
        val totalCals = proteinCals + carbsCals + fatCals
        val totalGrams = avgProtein + avgCarbs + avgFat

        val distribution = MacroDistribution(
            protein = MacroDetail(
                grams = avgProtein,
                calories = proteinCals,
                percent = if (totalCals > 0) BigDecimal(proteinCals * 100.0 / totalCals)
                    .setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
            ),
            carbs = MacroDetail(
                grams = avgCarbs,
                calories = carbsCals,
                percent = if (totalCals > 0) BigDecimal(carbsCals * 100.0 / totalCals)
                    .setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
            ),
            fat = MacroDetail(
                grams = avgFat,
                calories = fatCals,
                percent = if (totalCals > 0) BigDecimal(fatCals * 100.0 / totalCals)
                    .setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
            )
        )

        val dailyBreakdown = calorieData.map { data ->
            DailyMacros(
                date = data.date,
                protein = data.totalProtein.toInt(),
                carbs = data.totalCarbs.toInt(),
                fat = data.totalFat.toInt()
            )
        }

        return MacroDistributionResponse(
            date = null,
            startDate = startDate,
            endDate = endDate,
            daysWithData = calorieData.size,
            distribution = distribution,
            total = MacroTotal(calories = totalCals, grams = totalGrams),
            targets = null,
            comparison = null,
            dailyBreakdown = dailyBreakdown
        )
    }

    private fun getTodaySummary(profileId: UUID, today: LocalDate): TodaySummary {
        val calorieGoal = calculateCalorieGoalForProfile(profileId)
        val macros = analyticsRepository.getDayMacros(profileId, today)
        val workoutCalories = analyticsRepository.getWorkoutCaloriesForDate(profileId, today)
        val workoutData = analyticsRepository.getWorkoutData(profileId, today, today)
        val workoutCount = workoutData.sumOf { it.workoutCount }

        val (protein, carbs, fat) = macros ?: Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        val caloriesConsumed = (protein.multiply(BigDecimal("4")) +
                carbs.multiply(BigDecimal("4")) +
                fat.multiply(BigDecimal("9"))).toInt()

        val caloriesRemaining = (calorieGoal - caloriesConsumed).coerceAtLeast(0)
        val progressPercent = BigDecimal(caloriesConsumed * 100.0 / calorieGoal.coerceAtLeast(1))
            .setScale(1, RoundingMode.HALF_UP)

        // Calculate macro targets (rough estimate: 30/45/25 split)
        val proteinGoal = (calorieGoal * 0.30 / 4).toInt()
        val carbsGoal = (calorieGoal * 0.45 / 4).toInt()
        val fatGoal = (calorieGoal * 0.25 / 9).toInt()

        return TodaySummary(
            date = today,
            caloriesConsumed = caloriesConsumed,
            calorieGoal = calorieGoal,
            caloriesRemaining = caloriesRemaining,
            progressPercent = progressPercent,
            macros = MacroProgressSummary(
                protein = MacroProgress(
                    consumed = protein.toInt(),
                    goal = proteinGoal,
                    percent = BigDecimal(protein.toInt() * 100.0 / proteinGoal.coerceAtLeast(1))
                        .setScale(1, RoundingMode.HALF_UP)
                ),
                carbs = MacroProgress(
                    consumed = carbs.toInt(),
                    goal = carbsGoal,
                    percent = BigDecimal(carbs.toInt() * 100.0 / carbsGoal.coerceAtLeast(1))
                        .setScale(1, RoundingMode.HALF_UP)
                ),
                fat = MacroProgress(
                    consumed = fat.toInt(),
                    goal = fatGoal,
                    percent = BigDecimal(fat.toInt() * 100.0 / fatGoal.coerceAtLeast(1))
                        .setScale(1, RoundingMode.HALF_UP)
                )
            ),
            workouts = workoutCount,
            workoutCalories = workoutCalories
        )
    }

    private fun getWeekSummary(profileId: UUID, weekStart: LocalDate, today: LocalDate): WeekSummary {
        val calorieGoal = calculateCalorieGoalForProfile(profileId)
        val weekData = analyticsRepository.getWeekSummary(profileId, weekStart, today)

        val avgCalories = weekData["avgCalories"] as? Int ?: 0
        val daysWithData = weekData["daysWithData"] as? Int ?: 0
        val workouts = weekData["workouts"] as? Int ?: 0
        val cheatMeals = weekData["cheatMeals"] as? Int ?: 0

        // Calculate adherence rate based on days within 10% of goal
        val calorieData = analyticsRepository.getDailyCalorieData(profileId, weekStart, today)
        val daysOnTarget = calorieData.count { data ->
            val ratio = data.totalCalories.toDouble() / calorieGoal
            ratio in 0.9..1.1
        }
        val adherenceRate = if (daysWithData > 0) {
            BigDecimal(daysOnTarget * 100.0 / daysWithData).setScale(1, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return WeekSummary(
            averageCalories = avgCalories,
            adherenceRate = adherenceRate,
            workouts = workouts,
            cheatMeals = cheatMeals
        )
    }

    private fun getProgressSummary(profileId: UUID): ProgressSummary? {
        val firstWeight = analyticsRepository.getFirstWeight(profileId) ?: return null
        val latestWeight = analyticsRepository.getLatestWeightBefore(profileId, LocalDate.now()) ?: return null

        val weightLost = firstWeight.second.subtract(latestWeight)
        val daysSinceStart = ChronoUnit.DAYS.between(firstWeight.first, LocalDate.now()).toInt()
        val weeklyRate = if (daysSinceStart >= 7) {
            weightLost.negate().multiply(SEVEN).divide(BigDecimal(daysSinceStart), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return ProgressSummary(
            currentWeight = latestWeight,
            startWeight = firstWeight.second,
            goalWeight = null,
            weightLost = weightLost,
            percentToGoal = null,
            weeklyRate = weeklyRate
        )
    }

    private fun getStreaksSummary(profileId: UUID): StreaksSummary {
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)

        val loggingDates = analyticsRepository.getDatesWithMeals(profileId, oneYearAgo, today)
        val workoutDates = analyticsRepository.getDatesWithWorkouts(profileId, oneYearAgo, today)

        val currentLoggingStreak = calculateStreak(loggingDates, today)
        val longestLoggingStreak = calculateLongestStreak(loggingDates)
        val currentWorkoutStreak = calculateStreak(workoutDates, today)

        return StreaksSummary(
            currentLoggingStreak = currentLoggingStreak,
            longestLoggingStreak = longestLoggingStreak,
            currentWorkoutStreak = currentWorkoutStreak
        )
    }

    private fun calculateStreak(dates: List<LocalDate>, today: LocalDate): Int {
        if (dates.isEmpty()) return 0

        var streak = 0
        var checkDate = today

        // Check if today has data, otherwise start from yesterday
        if (!dates.contains(today)) {
            checkDate = today.minusDays(1)
            if (!dates.contains(checkDate)) {
                return 0
            }
        }

        while (dates.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return streak
    }

    private fun calculateLongestStreak(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        var longestStreak = 1
        var currentStreak = 1

        for (i in 1 until dates.size) {
            if (dates[i].minusDays(1) == dates[i - 1]) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    private fun calculatePeriodEnd(periodStart: LocalDate, period: AggregationPeriod): LocalDate {
        return when (period) {
            AggregationPeriod.WEEKLY -> periodStart.plusDays(6)
            AggregationPeriod.MONTHLY -> periodStart.plusMonths(1).minusDays(1)
            AggregationPeriod.DAILY -> periodStart
        }
    }

    private fun calculateWeeklyWorkoutTrends(
        workoutData: List<com.fittrack.repository.DailyWorkoutData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WeeklyWorkoutTrend> {
        val weekFields = WeekFields.of(Locale.getDefault())
        val byWeek = workoutData.groupBy { data ->
            val weekOfYear = data.date.get(weekFields.weekOfWeekBasedYear())
            val year = data.date.get(weekFields.weekBasedYear())
            "$year-W${weekOfYear.toString().padStart(2, '0')}"
        }

        return byWeek.map { (week, data) ->
            WeeklyWorkoutTrend(
                week = week,
                workouts = data.sumOf { it.workoutCount },
                duration = data.sumOf { it.totalDuration },
                calories = data.sumOf { it.totalCalories.toInt() }
            )
        }.sortedBy { it.week }
    }

    /**
     * Calculate calorie goal for a profile by looking up user data and computing TDEE.
     */
    private fun calculateCalorieGoalForProfile(profileId: UUID): Int {
        val user = userRepository.findById(profileId) ?: return DEFAULT_CALORIE_GOAL

        // Need weight, height, dob, and sex to calculate
        val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
        val weightKg = latestMetrics?.weightKg ?: return DEFAULT_CALORIE_GOAL
        val heightCm = user.heightCm ?: return DEFAULT_CALORIE_GOAL
        val dob = user.dateOfBirth ?: return DEFAULT_CALORIE_GOAL
        val sex = user.sex ?: return DEFAULT_CALORIE_GOAL

        val age = Period.between(dob, LocalDate.now()).years

        val bmr = nutritionCalculatorService.calculateBmr(weightKg, heightCm, age, sex)
        val tdee = nutritionCalculatorService.calculateTdee(bmr, user.defaultActivityLevel)

        return nutritionCalculatorService.calculateDailyCalorieGoal(
            tdee,
            user.fitnessGoalType,
            user.fitnessGoalIntensity
        )
    }
}
