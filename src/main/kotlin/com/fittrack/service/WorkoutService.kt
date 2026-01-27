package com.fittrack.service

import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.exception.MissingWeightException
import com.fittrack.exception.WorkoutNotFoundException
import com.fittrack.exception.WorkoutNotOwnedException
import com.fittrack.model.Workout
import com.fittrack.model.WorkoutType
import com.fittrack.model.WorkoutTypeMet
import com.fittrack.model.dto.workout.CalorieEstimateResponse
import com.fittrack.model.dto.workout.CalculationDetails
import com.fittrack.model.dto.workout.CreateWorkoutRequest
import com.fittrack.model.dto.workout.DayWorkoutIndicator
import com.fittrack.model.dto.workout.MonthlyStats
import com.fittrack.model.dto.workout.PageInfo
import com.fittrack.model.dto.workout.PeriodInfo
import com.fittrack.model.dto.workout.SummaryStats
import com.fittrack.model.dto.workout.UpdateWorkoutRequest
import com.fittrack.model.dto.workout.WeeklyTrend
import com.fittrack.model.dto.workout.WeeklySummaryResponse
import com.fittrack.model.dto.workout.WorkoutByType
import com.fittrack.model.dto.workout.WorkoutCreateResponse
import com.fittrack.model.dto.workout.WorkoutDetailResponse
import com.fittrack.model.dto.workout.WorkoutListItem
import com.fittrack.model.dto.workout.WorkoutListResponse
import com.fittrack.model.dto.workout.WorkoutStatsResponse
import com.fittrack.model.dto.workout.WorkoutStreakResponse
import com.fittrack.model.dto.workout.WorkoutSummary
import com.fittrack.model.dto.workout.WorkoutSummaryResponse
import com.fittrack.model.dto.workout.WorkoutTypeInfo
import com.fittrack.model.dto.workout.WorkoutTypesResponse
import com.fittrack.repository.BodyMetricsRepository
import com.fittrack.repository.WorkoutRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

@Service
class WorkoutService(
    private val workoutRepository: WorkoutRepository,
    private val bodyMetricsRepository: BodyMetricsRepository
) {
    companion object {
        private const val MAX_DATE_RANGE_DAYS = 90L
        private const val DEFAULT_WEIGHT_KG = 70.0
        private val DEFAULT_WEIGHT = BigDecimal("70.0")
    }

    fun getWorkouts(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        workoutType: WorkoutType?,
        page: Int,
        size: Int
    ): WorkoutListResponse {
        // Validate date range
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_DATE_RANGE_DAYS) {
            throw InvalidDateRangeException()
        }

        val validatedSize = size.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)

        val workouts = workoutRepository.findAllByProfileId(
            profileId = profileId,
            startDate = startDate,
            endDate = endDate,
            workoutType = workoutType,
            page = validatedPage,
            size = validatedSize
        )

        val totalElements = workoutRepository.countByProfileId(
            profileId = profileId,
            startDate = startDate,
            endDate = endDate,
            workoutType = workoutType
        )

        val summaryStats = workoutRepository.getSummaryStats(profileId, startDate, endDate)

        val content = workouts.map { toWorkoutListItem(it) }

        return WorkoutListResponse(
            content = content,
            page = PageInfo(
                number = validatedPage,
                size = validatedSize,
                totalElements = totalElements,
                totalPages = ceil(totalElements.toDouble() / validatedSize).toInt()
            ),
            summary = WorkoutSummary(
                totalWorkouts = (summaryStats["total_workouts"] as? Number)?.toInt() ?: 0,
                totalDuration = (summaryStats["total_duration"] as? Number)?.toInt() ?: 0,
                totalCalories = (summaryStats["total_calories"] as? BigDecimal)?.toInt() ?: 0,
                avgDuration = (summaryStats["avg_duration"] as? BigDecimal)?.toInt() ?: 0,
                avgCalories = (summaryStats["avg_calories"] as? BigDecimal)?.toInt() ?: 0
            )
        )
    }

    fun getWorkoutById(id: UUID, profileId: UUID): WorkoutDetailResponse {
        val workout = workoutRepository.findById(id)
            ?: throw WorkoutNotFoundException()

        if (workout.profileId != profileId) {
            throw WorkoutNotOwnedException()
        }

        val met = workoutRepository.findMetByType(workout.workoutType)
        val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
        val weightUsed = latestMetrics?.weightKg ?: DEFAULT_WEIGHT

        return toWorkoutDetailResponse(workout, met, weightUsed)
    }

    @Transactional
    fun createWorkout(request: CreateWorkoutRequest, profileId: UUID): WorkoutCreateResponse {
        // Get user's weight for calorie calculation
        val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
        val weightKg = latestMetrics?.weightKg ?: run {
            // If no metrics at all, use default weight
            // But if metrics exist without weight, still use default
            if (latestMetrics == null) {
                // No body metrics logged at all - we still allow with default weight
                // This is a UX decision: don't block workout logging just because no weight
                DEFAULT_WEIGHT
            } else {
                DEFAULT_WEIGHT
            }
        }

        val met = workoutRepository.findMetByType(request.workoutType)
        val estimatedCalories = calculateCalories(
            metValue = met?.metValue ?: BigDecimal(request.workoutType.metValue.toString()),
            weightKg = weightKg,
            durationMinutes = request.durationMinutes
        )

        val workout = workoutRepository.create(
            profileId = profileId,
            date = request.date,
            workoutType = request.workoutType,
            name = request.name?.trim(),
            durationMinutes = request.durationMinutes,
            caloriesBurnedEstimated = estimatedCalories,
            caloriesBurnedActual = request.caloriesBurnedActual,
            notes = request.notes?.trim()
        )

        return toWorkoutCreateResponse(workout)
    }

    @Transactional
    fun updateWorkout(id: UUID, request: UpdateWorkoutRequest, profileId: UUID): WorkoutDetailResponse {
        val existingWorkout = workoutRepository.findById(id)
            ?: throw WorkoutNotFoundException()

        if (existingWorkout.profileId != profileId) {
            throw WorkoutNotOwnedException()
        }

        // Determine if we need to recalculate estimated calories
        val needsRecalculation = request.durationMinutes != null || request.workoutType != null
        var newEstimatedCalories: BigDecimal? = null

        if (needsRecalculation) {
            val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
            val weightKg = latestMetrics?.weightKg ?: DEFAULT_WEIGHT
            val workoutType = request.workoutType ?: existingWorkout.workoutType
            val duration = request.durationMinutes ?: existingWorkout.durationMinutes

            val met = workoutRepository.findMetByType(workoutType)
            newEstimatedCalories = calculateCalories(
                metValue = met?.metValue ?: BigDecimal(workoutType.metValue.toString()),
                weightKg = weightKg,
                durationMinutes = duration
            )
        }

        val updatedWorkout = workoutRepository.update(
            id = id,
            workoutType = request.workoutType,
            name = request.name?.trim(),
            durationMinutes = request.durationMinutes,
            caloriesBurnedEstimated = newEstimatedCalories,
            caloriesBurnedActual = if (request.clearActualCalories) null else request.caloriesBurnedActual,
            notes = request.notes?.trim(),
            updateWorkoutType = request.workoutType != null,
            updateName = request.name != null,
            updateDuration = request.durationMinutes != null,
            updateEstimated = needsRecalculation,
            updateActual = request.caloriesBurnedActual != null || request.clearActualCalories,
            updateNotes = request.notes != null
        ) ?: throw WorkoutNotFoundException()

        val met = workoutRepository.findMetByType(updatedWorkout.workoutType)
        val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
        val weightUsed = latestMetrics?.weightKg ?: DEFAULT_WEIGHT

        return toWorkoutDetailResponse(updatedWorkout, met, weightUsed)
    }

    @Transactional
    fun deleteWorkout(id: UUID, profileId: UUID) {
        val workout = workoutRepository.findById(id)
            ?: throw WorkoutNotFoundException()

        if (workout.profileId != profileId) {
            throw WorkoutNotOwnedException()
        }

        workoutRepository.delete(id)
    }

    fun getWorkoutTypes(profileId: UUID): WorkoutTypesResponse {
        val mets = workoutRepository.findAllMets()
        val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
        val weightKg = latestMetrics?.weightKg ?: DEFAULT_WEIGHT

        val workoutTypes = mets.map { met ->
            WorkoutTypeInfo(
                type = met.workoutType,
                metValue = met.metValue,
                description = met.description,
                estimatedCaloriesPerHour = calculateCalories(met.metValue, weightKg, 60).toInt()
            )
        }

        return WorkoutTypesResponse(workoutTypes = workoutTypes)
    }

    fun estimateCalories(
        profileId: UUID,
        workoutType: WorkoutType,
        durationMinutes: Int
    ): CalorieEstimateResponse {
        val met = workoutRepository.findMetByType(workoutType)
        val latestMetrics = bodyMetricsRepository.findLatestByProfileId(profileId)
        val weightKg = latestMetrics?.weightKg ?: DEFAULT_WEIGHT

        val metValue = met?.metValue ?: BigDecimal(workoutType.metValue.toString())
        val estimatedCalories = calculateCalories(metValue, weightKg, durationMinutes)

        return CalorieEstimateResponse(
            workoutType = workoutType,
            durationMinutes = durationMinutes,
            estimatedCalories = estimatedCalories.toInt(),
            calculation = CalculationDetails(
                weightUsed = weightKg,
                metValue = metValue
            )
        )
    }

    fun getWorkoutSummary(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): WorkoutSummaryResponse {
        val totalCount = workoutRepository.countByProfileId(profileId, startDate, endDate, null)
        val summaryStats = workoutRepository.getSummaryStats(profileId, startDate, endDate)
        val maxStats = workoutRepository.getMaxStats(profileId, startDate, endDate)
        val byType = workoutRepository.getWorkoutsByType(profileId, startDate, endDate)
        val weeklyTrend = workoutRepository.getWeeklyTrend(profileId, startDate, endDate)

        val totalDuration = (summaryStats["total_duration"] as? Number)?.toInt() ?: 0
        val totalCalories = (summaryStats["total_calories"] as? BigDecimal)?.toInt() ?: 0

        // Calculate average workouts per week
        val weeks = ChronoUnit.WEEKS.between(startDate, endDate).coerceAtLeast(1)
        val avgWorkoutsPerWeek = totalCount.toDouble() / weeks

        val byTypeList = byType.map { row ->
            val workoutTypeStr = row["workout_type"] as String
            val count = (row["count"] as Number).toInt()
            val duration = (row["total_duration"] as Number).toInt()
            val calories = (row["total_calories"] as? BigDecimal)?.toInt() ?: 0
            val percentOfTotal = if (totalCount > 0) (count.toDouble() / totalCount * 100) else 0.0

            WorkoutByType(
                workoutType = WorkoutType.valueOf(workoutTypeStr),
                count = count,
                totalDuration = duration,
                totalCalories = calories,
                percentOfTotal = percentOfTotal.toBigDecimal().setScale(1, RoundingMode.HALF_UP).toDouble()
            )
        }

        val weeklyTrendList = weeklyTrend.map { row ->
            val weekStart = (row["week_start"] as java.sql.Date).toLocalDate()
            val workouts = (row["workouts"] as Number).toInt()
            val calories = (row["calories"] as? BigDecimal)?.toInt() ?: 0

            WeeklyTrend(
                weekStart = weekStart,
                workouts = workouts,
                calories = calories
            )
        }

        return WorkoutSummaryResponse(
            period = PeriodInfo(startDate = startDate, endDate = endDate),
            summary = SummaryStats(
                totalWorkouts = totalCount.toInt(),
                totalDurationMinutes = totalDuration,
                totalCaloriesBurned = totalCalories,
                averageWorkoutsPerWeek = avgWorkoutsPerWeek.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble(),
                averageDurationMinutes = (summaryStats["avg_duration"] as? BigDecimal)?.toInt() ?: 0,
                averageCaloriesBurned = (summaryStats["avg_calories"] as? BigDecimal)?.toInt() ?: 0,
                longestWorkout = (maxStats["longest_workout"] as? Number)?.toInt() ?: 0,
                mostCaloriesBurned = (maxStats["most_calories"] as? BigDecimal)?.toInt() ?: 0
            ),
            byType = byTypeList,
            weeklyTrend = weeklyTrendList
        )
    }

    fun getWorkoutsByDate(date: LocalDate, profileId: UUID): List<Workout> {
        return workoutRepository.findByDateAndProfileId(date, profileId)
    }

    // ========== Streak, Stats, and Weekly Summary ==========

    /**
     * Calculate workout streak for a user.
     * Current streak = consecutive days with workouts starting from today or yesterday.
     * Longest streak = maximum consecutive days with workouts ever.
     */
    fun getWorkoutStreak(profileId: UUID): WorkoutStreakResponse {
        val workoutDates = workoutRepository.getDistinctWorkoutDates(profileId)

        if (workoutDates.isEmpty()) {
            return WorkoutStreakResponse(
                currentStreak = 0,
                longestStreak = 0,
                lastWorkoutDate = null,
                streakStartDate = null,
                isActiveToday = false
            )
        }

        val today = LocalDate.now()
        val lastWorkoutDate = workoutDates.first()
        val isActiveToday = lastWorkoutDate == today

        // Calculate current streak
        val currentStreak = calculateCurrentStreak(workoutDates, today)

        // Calculate longest streak
        val longestStreak = calculateLongestStreak(workoutDates)

        // Calculate streak start date
        val streakStartDate = if (currentStreak > 0) {
            if (isActiveToday) today.minusDays(currentStreak.toLong() - 1)
            else lastWorkoutDate.minusDays(currentStreak.toLong() - 1)
        } else null

        return WorkoutStreakResponse(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastWorkoutDate = lastWorkoutDate,
            streakStartDate = streakStartDate,
            isActiveToday = isActiveToday
        )
    }

    private fun calculateCurrentStreak(dates: List<LocalDate>, today: LocalDate): Int {
        if (dates.isEmpty()) return 0

        val yesterday = today.minusDays(1)
        val mostRecent = dates.first()

        // Current streak only counts if most recent workout is today or yesterday
        if (mostRecent != today && mostRecent != yesterday) {
            return 0
        }

        var streak = 1
        var expectedDate = mostRecent.minusDays(1)

        for (i in 1 until dates.size) {
            val currentDate = dates[i]
            if (currentDate == expectedDate) {
                streak++
                expectedDate = expectedDate.minusDays(1)
            } else {
                break
            }
        }

        return streak
    }

    private fun calculateLongestStreak(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        var longestStreak = 1
        var currentStreak = 1

        // dates are sorted DESC, so we iterate backwards in time
        for (i in 1 until dates.size) {
            val prevDate = dates[i - 1]
            val currentDate = dates[i]

            if (ChronoUnit.DAYS.between(currentDate, prevDate) == 1L) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    /**
     * Get overall workout statistics with monthly breakdown.
     */
    fun getWorkoutStats(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): WorkoutStatsResponse {
        val summaryStats = workoutRepository.getSummaryStats(profileId, startDate, endDate)
        val monthlyStatsRaw = workoutRepository.getMonthlyStats(profileId, startDate, endDate)

        val totalWorkouts = (summaryStats["total_workouts"] as? Number)?.toInt() ?: 0
        val totalDuration = (summaryStats["total_duration"] as? Number)?.toInt() ?: 0
        val totalCalories = (summaryStats["total_calories"] as? BigDecimal)?.toInt() ?: 0
        val avgDuration = (summaryStats["avg_duration"] as? BigDecimal)?.toInt() ?: 0
        val avgCalories = (summaryStats["avg_calories"] as? BigDecimal)?.toInt() ?: 0

        val monthlyStats = monthlyStatsRaw.map { row ->
            MonthlyStats(
                month = row["month"] as String,
                totalWorkouts = (row["total_workouts"] as Number).toInt(),
                totalDurationMinutes = (row["total_duration"] as Number).toInt(),
                totalCaloriesBurned = (row["total_calories"] as? BigDecimal)?.toInt() ?: 0,
                averageDurationMinutes = (row["avg_duration"] as? BigDecimal)?.toInt() ?: 0
            )
        }

        return WorkoutStatsResponse(
            totalWorkouts = totalWorkouts,
            totalDurationMinutes = totalDuration,
            totalCaloriesBurned = totalCalories,
            averageDurationMinutes = avgDuration,
            averageCaloriesPerWorkout = avgCalories,
            monthlyStats = monthlyStats
        )
    }

    /**
     * Get weekly summary showing each day of the week with workout indicators.
     */
    fun getWeeklySummary(profileId: UUID, date: LocalDate): WeeklySummaryResponse {
        // Get Monday of the week containing the provided date
        val weekStart = date.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)

        val workouts = workoutRepository.getWorkoutsForDateRange(profileId, weekStart, weekEnd)

        // Group workouts by date
        val workoutsByDate = workouts.groupBy { it.date }

        // Build day indicators for all 7 days
        val days = (0..6).map { dayOffset ->
            val dayDate = weekStart.plusDays(dayOffset.toLong())
            val dayWorkouts = workoutsByDate[dayDate] ?: emptyList()

            val totalDuration = dayWorkouts.sumOf { it.durationMinutes }
            val totalCalories = dayWorkouts.sumOf {
                it.caloriesBurned?.toInt() ?: 0
            }

            DayWorkoutIndicator(
                date = dayDate,
                dayOfWeek = dayDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                hasWorkout = dayWorkouts.isNotEmpty(),
                workoutCount = dayWorkouts.size,
                totalDurationMinutes = totalDuration,
                totalCaloriesBurned = totalCalories
            )
        }

        val totalWorkouts = workouts.size
        val totalDuration = workouts.sumOf { it.durationMinutes }
        val totalCalories = workouts.sumOf { it.caloriesBurned?.toInt() ?: 0 }

        return WeeklySummaryResponse(
            weekStartDate = weekStart,
            weekEndDate = weekEnd,
            days = days,
            totalWorkouts = totalWorkouts,
            totalDurationMinutes = totalDuration,
            totalCaloriesBurned = totalCalories
        )
    }

    // ========== Helper Methods ==========

    private fun calculateCalories(metValue: BigDecimal, weightKg: BigDecimal, durationMinutes: Int): BigDecimal {
        // Calories = MET * weight(kg) * duration(hours)
        val durationHours = BigDecimal(durationMinutes).divide(BigDecimal(60), 4, RoundingMode.HALF_UP)
        return metValue.multiply(weightKg).multiply(durationHours).setScale(2, RoundingMode.HALF_UP)
    }

    private fun toWorkoutListItem(workout: Workout): WorkoutListItem {
        return WorkoutListItem(
            id = workout.id,
            date = workout.date,
            workoutType = workout.workoutType,
            name = workout.name,
            durationMinutes = workout.durationMinutes,
            caloriesBurnedEstimated = workout.caloriesBurnedEstimated,
            caloriesBurnedActual = workout.caloriesBurnedActual,
            caloriesBurned = workout.caloriesBurned,
            notes = workout.notes
        )
    }

    private fun toWorkoutDetailResponse(
        workout: Workout,
        met: WorkoutTypeMet?,
        weightUsed: BigDecimal
    ): WorkoutDetailResponse {
        val metValue = met?.metValue ?: BigDecimal(workout.workoutType.metValue.toString())

        return WorkoutDetailResponse(
            id = workout.id,
            date = workout.date,
            workoutType = workout.workoutType,
            workoutTypeInfo = WorkoutTypeInfo(
                type = workout.workoutType,
                metValue = metValue,
                description = met?.description ?: workout.workoutType.description
            ),
            name = workout.name,
            durationMinutes = workout.durationMinutes,
            caloriesBurnedEstimated = workout.caloriesBurnedEstimated,
            caloriesBurnedActual = workout.caloriesBurnedActual,
            caloriesBurned = workout.caloriesBurned,
            notes = workout.notes,
            calculationDetails = CalculationDetails(
                weightUsed = weightUsed,
                metValue = metValue
            ),
            createdAt = workout.createdAt,
            updatedAt = workout.updatedAt
        )
    }

    private fun toWorkoutCreateResponse(workout: Workout): WorkoutCreateResponse {
        return WorkoutCreateResponse(
            id = workout.id,
            date = workout.date,
            workoutType = workout.workoutType,
            name = workout.name,
            durationMinutes = workout.durationMinutes,
            caloriesBurnedEstimated = workout.caloriesBurnedEstimated,
            caloriesBurnedActual = workout.caloriesBurnedActual,
            caloriesBurned = workout.caloriesBurned,
            notes = workout.notes,
            createdAt = workout.createdAt
        )
    }
}
