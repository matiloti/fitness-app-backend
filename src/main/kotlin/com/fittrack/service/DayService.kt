package com.fittrack.service

import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.exception.MissingProfileDataException
import com.fittrack.exception.ProfileNotFoundException
import com.fittrack.model.ActivityLevel
import com.fittrack.model.Day
import com.fittrack.model.MealType
import com.fittrack.model.User
import com.fittrack.model.dto.day.ActivityLevelInfo
import com.fittrack.model.dto.day.ActivityLevelResponse
import com.fittrack.model.dto.day.AdherenceInfo
import com.fittrack.model.dto.day.AdherenceStatus
import com.fittrack.model.dto.day.BodyMetricSummary
import com.fittrack.model.dto.day.CalculationsInfo
import com.fittrack.model.dto.day.DayNavigationInfo
import com.fittrack.model.dto.day.DayRangeSummaryItem
import com.fittrack.model.dto.day.DaySummaryResponse
import com.fittrack.model.dto.day.DaysRangeResponse
import com.fittrack.model.dto.day.FitnessGoalInfo
import com.fittrack.model.dto.day.GoalsResponse
import com.fittrack.model.dto.day.MacroBreakdown
import com.fittrack.model.dto.day.MacroGoals
import com.fittrack.model.dto.day.MealNutritionTotals
import com.fittrack.model.dto.day.MealSummary
import com.fittrack.model.dto.day.NavigationResponse
import com.fittrack.model.dto.day.NutritionProgress
import com.fittrack.model.dto.day.NutritionTotals
import com.fittrack.model.dto.day.ProfileSummary
import com.fittrack.model.dto.day.RangeSummary
import com.fittrack.model.dto.day.UpdateActivityLevelRequest
import com.fittrack.model.dto.day.WeekDaySummary
import com.fittrack.model.dto.day.WeekOverviewResponse
import com.fittrack.model.dto.day.WeekSummary
import com.fittrack.repository.DayRepository
import com.fittrack.repository.MealRepository
import com.fittrack.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

@Service
class DayService(
    private val dayRepository: DayRepository,
    private val mealRepository: MealRepository,
    private val userRepository: UserRepository,
    private val nutritionCalculator: NutritionCalculatorService
) {

    companion object {
        const val MAX_DATE_RANGE_DAYS = 90
    }

    /**
     * Get day summary for a specific date.
     * Creates day record if it doesn't exist.
     */
    @Transactional
    fun getDaySummary(profileId: UUID, date: LocalDate): DaySummaryResponse {
        val user = userRepository.findById(profileId)
            ?: throw ProfileNotFoundException()

        // Find or create day
        val day = dayRepository.findOrCreate(profileId, date)

        // Get activity level (override or default)
        val activityLevel = day.activityLevelOverride ?: user.defaultActivityLevel
        val isOverride = day.activityLevelOverride != null

        // Calculate goals
        val goals = calculateGoals(user, activityLevel, date)

        // Get consumed totals
        val consumed = getConsumedTotals(day.id)

        // Get meals summary
        val meals = getMealsSummary(day.id)

        // Get body metrics for the day
        val bodyMetrics = getBodyMetricsSummary(profileId, date)

        // Calculate remaining and progress
        val remaining = calculateRemaining(goals, consumed)
        val progress = calculateProgress(consumed, goals)

        return DaySummaryResponse(
            date = date,
            dayId = day.id,
            activityLevel = ActivityLevelInfo(
                level = activityLevel,
                multiplier = activityLevel.multiplier,
                isOverride = isOverride
            ),
            goals = goals,
            consumed = consumed,
            remaining = remaining,
            progress = progress,
            meals = meals,
            bodyMetrics = bodyMetrics
        )
    }

    /**
     * Get today's summary (convenience method).
     */
    @Transactional
    fun getTodaySummary(profileId: UUID): DaySummaryResponse {
        return getDaySummary(profileId, LocalDate.now())
    }

    /**
     * Update activity level override for a day.
     */
    @Transactional
    fun updateActivityLevel(
        profileId: UUID,
        date: LocalDate,
        request: UpdateActivityLevelRequest
    ): ActivityLevelResponse {
        val user = userRepository.findById(profileId)
            ?: throw ProfileNotFoundException()

        // Find or create day
        val day = dayRepository.findOrCreate(profileId, date)

        // Update activity level override
        val updatedDay = dayRepository.updateActivityLevelOverride(day.id, request.activityLevel)
            ?: throw ProfileNotFoundException()

        // Calculate new goals
        val goals = calculateGoals(user, request.activityLevel, date)

        return ActivityLevelResponse(
            date = date,
            activityLevel = ActivityLevelInfo(
                level = request.activityLevel,
                multiplier = request.activityLevel.multiplier,
                isOverride = true
            ),
            goals = goals,
            defaultActivityLevel = ActivityLevelInfo(
                level = user.defaultActivityLevel,
                multiplier = user.defaultActivityLevel.multiplier,
                isOverride = false
            )
        )
    }

    /**
     * Remove activity level override for a day (use profile default).
     */
    @Transactional
    fun removeActivityLevelOverride(profileId: UUID, date: LocalDate): ActivityLevelResponse {
        val user = userRepository.findById(profileId)
            ?: throw ProfileNotFoundException()

        val day = dayRepository.findByProfileIdAndDate(profileId, date)
        if (day != null) {
            dayRepository.updateActivityLevelOverride(day.id, null)
        }

        val goals = calculateGoals(user, user.defaultActivityLevel, date)

        return ActivityLevelResponse(
            date = date,
            activityLevel = ActivityLevelInfo(
                level = user.defaultActivityLevel,
                multiplier = user.defaultActivityLevel.multiplier,
                isOverride = false
            ),
            goals = goals,
            defaultActivityLevel = null
        )
    }

    /**
     * Get calculated nutritional goals for a date with full calculation breakdown.
     */
    fun getGoals(profileId: UUID, date: LocalDate): GoalsResponse {
        val user = userRepository.findById(profileId)
            ?: throw ProfileNotFoundException()

        // Validate required profile data
        if (user.sex == null || user.heightCm == null || user.dateOfBirth == null) {
            throw MissingProfileDataException()
        }

        // Get day if exists to check for activity override
        val day = dayRepository.findByProfileIdAndDate(profileId, date)
        val activityLevel = day?.activityLevelOverride ?: user.defaultActivityLevel
        val isOverride = day?.activityLevelOverride != null

        // Get latest weight for calculations
        val latestMetric = dayRepository.findLatestBodyMetric(profileId, date)
        val weightKg = latestMetric?.weightKg
            ?: throw MissingProfileDataException("No weight data available for calculations.")

        // Calculate age
        val age = Period.between(user.dateOfBirth, date).years

        // Calculate goals
        val calculation = nutritionCalculator.calculateDailyGoals(
            weightKg = weightKg,
            heightCm = user.heightCm,
            age = age,
            sex = user.sex,
            activityLevel = activityLevel,
            goalType = user.fitnessGoalType,
            intensity = user.fitnessGoalIntensity
        )

        return GoalsResponse(
            date = date,
            profile = ProfileSummary(
                sex = user.sex,
                heightCm = user.heightCm,
                age = age
            ),
            latestWeight = weightKg,
            weightDate = latestMetric.date,
            activityLevel = ActivityLevelInfo(
                level = activityLevel,
                multiplier = activityLevel.multiplier,
                isOverride = isOverride
            ),
            fitnessGoal = FitnessGoalInfo(
                type = user.fitnessGoalType,
                intensity = user.fitnessGoalIntensity,
                adjustment = calculation.adjustment
            ),
            calculations = CalculationsInfo(
                bmr = calculation.bmr,
                tdee = calculation.tdee,
                dailyCalorieGoal = calculation.dailyCalorieGoal,
                macros = MacroGoals(
                    protein = MacroBreakdown(
                        grams = calculation.macros.proteinGrams,
                        percent = NutritionCalculatorService.PROTEIN_PERCENT,
                        calories = calculation.macros.proteinCalories
                    ),
                    carbs = MacroBreakdown(
                        grams = calculation.macros.carbsGrams,
                        percent = NutritionCalculatorService.CARBS_PERCENT,
                        calories = calculation.macros.carbsCalories
                    ),
                    fat = MacroBreakdown(
                        grams = calculation.macros.fatGrams,
                        percent = NutritionCalculatorService.FAT_PERCENT,
                        calories = calculation.macros.fatCalories
                    )
                )
            )
        )
    }

    /**
     * Get summary for a date range.
     */
    fun getDaysRange(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): DaysRangeResponse {
        // Validate date range
        val daysBetween = ChronoUnit.DAYS.between(startDate, endDate)
        if (daysBetween > MAX_DATE_RANGE_DAYS || endDate.isBefore(startDate)) {
            throw InvalidDateRangeException()
        }

        val user = userRepository.findById(profileId)
            ?: throw ProfileNotFoundException()

        // Get all days in range
        val days = dayRepository.findByProfileIdAndDateRange(profileId, startDate, endDate)
        val daysMap = days.associateBy { it.date }

        // Get meals for all days
        val meals = mealRepository.findByProfileIdAndDateRange(profileId, startDate, endDate)

        // Build day summaries
        val daySummaries = mutableListOf<DayRangeSummaryItem>()
        var date = startDate
        var totalCaloriesConsumed = 0
        var totalProteinConsumed = 0
        var daysWithMeals = 0
        var totalCheatMeals = 0
        var daysOnTarget = 0

        while (!date.isAfter(endDate)) {
            val day = daysMap[date]
            val dayMeals = if (day != null) mealRepository.findByDayId(day.id) else emptyList()

            val activityLevel = day?.activityLevelOverride ?: user.defaultActivityLevel
            val goals = calculateGoals(user, activityLevel, date)

            val consumed = if (day != null) getConsumedTotals(day.id) else NutritionTotals(0, 0, 0, 0)

            val adherenceResult = nutritionCalculator.calculateAdherence(consumed.calories, goals.calories)
            val adherence = AdherenceInfo(
                calories = toAdherenceStatus(adherenceResult.status),
                caloriesPercent = adherenceResult.percent
            )

            val cheatMealCount = dayMeals.count { it.isCheatMeal }

            if (dayMeals.isNotEmpty()) {
                daysWithMeals++
                totalCaloriesConsumed += consumed.calories
                totalProteinConsumed += consumed.protein
                totalCheatMeals += cheatMealCount
            }

            if (adherenceResult.status == com.fittrack.service.AdherenceStatus.ON_TARGET) {
                daysOnTarget++
            }

            daySummaries.add(
                DayRangeSummaryItem(
                    date = date,
                    activityLevel = activityLevel,
                    isActivityOverride = day?.activityLevelOverride != null,
                    goals = goals,
                    consumed = consumed,
                    adherence = adherence,
                    mealCount = dayMeals.size,
                    cheatMealCount = cheatMealCount,
                    hasBodyMetrics = dayRepository.hasBodyMetricsForDate(profileId, date)
                )
            )

            date = date.plusDays(1)
        }

        val totalDays = daySummaries.size
        val avgCalories = if (daysWithMeals > 0) totalCaloriesConsumed / daysWithMeals else 0
        val avgProtein = if (daysWithMeals > 0) totalProteinConsumed / daysWithMeals else 0
        val adherenceRate = if (totalDays > 0) (daysOnTarget.toDouble() / totalDays) * 100 else 0.0

        return DaysRangeResponse(
            startDate = startDate,
            endDate = endDate,
            days = daySummaries,
            summary = RangeSummary(
                totalDays = totalDays,
                daysWithMeals = daysWithMeals,
                averageCalories = avgCalories,
                averageProtein = avgProtein,
                totalCheatMeals = totalCheatMeals,
                adherenceRate = adherenceRate
            )
        )
    }

    /**
     * Get weekly calendar overview.
     */
    fun getWeekOverview(profileId: UUID, weekOf: LocalDate = LocalDate.now()): WeekOverviewResponse {
        val user = userRepository.findById(profileId)
            ?: throw ProfileNotFoundException()

        // Calculate week start (Monday) and end (Sunday)
        val weekStart = weekOf.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)

        // Get all days in the week
        val days = dayRepository.findByProfileIdAndDateRange(profileId, weekStart, weekEnd)
        val daysMap = days.associateBy { it.date }

        val weekDays = mutableListOf<WeekDaySummary>()
        var totalCalories = 0
        var daysWithData = 0
        var cheatMealCount = 0
        var daysWithBodyMetrics = 0
        var daysOnTarget = 0

        var date = weekStart
        while (!date.isAfter(weekEnd)) {
            val day = daysMap[date]
            val dayMeals = if (day != null) mealRepository.findByDayId(day.id) else emptyList()

            val activityLevel = day?.activityLevelOverride ?: user.defaultActivityLevel
            val goals = calculateGoals(user, activityLevel, date)
            val consumed = if (day != null) getConsumedTotals(day.id) else NutritionTotals(0, 0, 0, 0)

            val hasCheatMeal = dayMeals.any { it.isCheatMeal }
            val hasBodyMetrics = dayRepository.hasBodyMetricsForDate(profileId, date)

            val adherenceResult = if (dayMeals.isNotEmpty()) {
                nutritionCalculator.calculateAdherence(consumed.calories, goals.calories)
            } else null

            if (dayMeals.isNotEmpty()) {
                daysWithData++
                totalCalories += consumed.calories
            }

            if (hasCheatMeal) cheatMealCount++
            if (hasBodyMetrics) daysWithBodyMetrics++
            if (adherenceResult?.status == com.fittrack.service.AdherenceStatus.ON_TARGET) daysOnTarget++

            weekDays.add(
                WeekDaySummary(
                    date = date,
                    dayOfWeek = date.dayOfWeek.name,
                    adherence = adherenceResult?.let { toAdherenceStatus(it.status) },
                    caloriesPercent = adherenceResult?.percent,
                    hasCheatMeal = hasCheatMeal,
                    hasBodyMetrics = hasBodyMetrics
                )
            )

            date = date.plusDays(1)
        }

        val avgCalories = if (daysWithData > 0) totalCalories / daysWithData else 0
        val calorieGoal = calculateGoals(user, user.defaultActivityLevel, weekOf).calories
        val adherenceRate = if (weekDays.isNotEmpty()) (daysOnTarget.toDouble() / weekDays.size) * 100 else 0.0

        return WeekOverviewResponse(
            weekStart = weekStart,
            weekEnd = weekEnd,
            days = weekDays,
            weekSummary = WeekSummary(
                averageCalories = avgCalories,
                calorieGoal = calorieGoal,
                adherenceRate = adherenceRate,
                cheatMealCount = cheatMealCount,
                daysWithBodyMetrics = daysWithBodyMetrics
            )
        )
    }

    /**
     * Get navigation context for day view.
     */
    fun getNavigation(profileId: UUID, date: LocalDate = LocalDate.now()): NavigationResponse {
        val today = LocalDate.now()
        val previousDate = date.minusDays(1)
        val nextDate = date.plusDays(1)

        val hasMealsCurrent = dayRepository.hasMealsForDate(profileId, date)
        val hasMealsPrevious = dayRepository.hasMealsForDate(profileId, previousDate)
        val hasMealsNext = dayRepository.hasMealsForDate(profileId, nextDate)

        val firstDate = dayRepository.findFirstDayWithData(profileId)
        val lastDate = dayRepository.findLastDayWithData(profileId)

        return NavigationResponse(
            current = DayNavigationInfo(
                date = date,
                isToday = date == today,
                hasMeals = hasMealsCurrent
            ),
            previous = DayNavigationInfo(
                date = previousDate,
                isToday = null,
                hasMeals = hasMealsPrevious
            ),
            next = DayNavigationInfo(
                date = nextDate,
                isToday = null,
                hasMeals = hasMealsNext
            ),
            firstDate = firstDate,
            lastDate = lastDate ?: today
        )
    }

    // ========== Helper Methods ==========

    private fun calculateGoals(user: User, activityLevel: ActivityLevel, date: LocalDate): NutritionTotals {
        // Need sex, height, date of birth, and weight for calculations
        if (user.sex == null || user.heightCm == null || user.dateOfBirth == null) {
            // Return default goals if profile incomplete
            return NutritionTotals(calories = 2000, protein = 150, carbs = 225, fat = 55)
        }

        // Get latest weight
        val latestMetric = dayRepository.findLatestBodyMetric(user.id, date)
        val weightKg = latestMetric?.weightKg ?: BigDecimal("70") // Default weight

        val age = Period.between(user.dateOfBirth, date).years

        val calculation = nutritionCalculator.calculateDailyGoals(
            weightKg = weightKg,
            heightCm = user.heightCm,
            age = age,
            sex = user.sex,
            activityLevel = activityLevel,
            goalType = user.fitnessGoalType,
            intensity = user.fitnessGoalIntensity
        )

        return NutritionTotals(
            calories = calculation.dailyCalorieGoal,
            protein = calculation.macros.proteinGrams,
            carbs = calculation.macros.carbsGrams,
            fat = calculation.macros.fatGrams
        )
    }

    private fun getConsumedTotals(dayId: UUID): NutritionTotals {
        val totals = mealRepository.getDayTotals(dayId)
        return NutritionTotals(
            calories = totals["total_calories"]?.toInt() ?: 0,
            protein = totals["total_protein"]?.toInt() ?: 0,
            carbs = totals["total_carbs"]?.toInt() ?: 0,
            fat = totals["total_fat"]?.toInt() ?: 0
        )
    }

    private fun getMealsSummary(dayId: UUID): List<MealSummary> {
        val meals = mealRepository.findByDayId(dayId)
        return meals.map { meal ->
            val itemCount = mealRepository.countItemsByMealId(meal.id)
            MealSummary(
                id = meal.id,
                mealType = meal.mealType,
                isCheatMeal = meal.isCheatMeal,
                totals = MealNutritionTotals(
                    calories = meal.totalCalories.toInt(),
                    protein = meal.totalProtein.toInt()
                ),
                itemCount = itemCount
            )
        }
    }

    private fun getBodyMetricsSummary(profileId: UUID, date: LocalDate): BodyMetricSummary? {
        val metric = dayRepository.findBodyMetricByProfileIdAndDate(profileId, date)
            ?: return null

        val hasPhotos = dayRepository.hasProgressPhotosForMetric(metric.id)

        return BodyMetricSummary(
            id = metric.id,
            weightKg = metric.weightKg,
            bodyFatPercentage = metric.bodyFatPercentage,
            hasPhotos = hasPhotos
        )
    }

    private fun calculateRemaining(goals: NutritionTotals, consumed: NutritionTotals): NutritionTotals {
        return NutritionTotals(
            calories = maxOf(0, goals.calories - consumed.calories),
            protein = maxOf(0, goals.protein - consumed.protein),
            carbs = maxOf(0, goals.carbs - consumed.carbs),
            fat = maxOf(0, goals.fat - consumed.fat)
        )
    }

    private fun calculateProgress(consumed: NutritionTotals, goals: NutritionTotals): NutritionProgress {
        return NutritionProgress(
            caloriesPercent = nutritionCalculator.calculateProgress(consumed.calories, goals.calories),
            proteinPercent = nutritionCalculator.calculateProgress(consumed.protein, goals.protein),
            carbsPercent = nutritionCalculator.calculateProgress(consumed.carbs, goals.carbs),
            fatPercent = nutritionCalculator.calculateProgress(consumed.fat, goals.fat)
        )
    }

    private fun toAdherenceStatus(status: com.fittrack.service.AdherenceStatus): AdherenceStatus {
        return when (status) {
            com.fittrack.service.AdherenceStatus.UNDER -> AdherenceStatus.UNDER
            com.fittrack.service.AdherenceStatus.ON_TARGET -> AdherenceStatus.ON_TARGET
            com.fittrack.service.AdherenceStatus.OVER -> AdherenceStatus.OVER
        }
    }
}
