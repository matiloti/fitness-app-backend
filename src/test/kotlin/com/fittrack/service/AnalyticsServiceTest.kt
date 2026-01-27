package com.fittrack.service

import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.model.BodyMetrics
import com.fittrack.model.TrendDirection
import com.fittrack.model.User
import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.Sex
import com.fittrack.model.dto.analytics.AdherenceStatus
import com.fittrack.repository.AnalyticsRepository
import com.fittrack.repository.BodyMetricsRepository
import com.fittrack.repository.DailyCalorieData
import com.fittrack.repository.DailyWeightData
import com.fittrack.repository.DailyWorkoutData
import com.fittrack.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Analytics Service Tests")
class AnalyticsServiceTest {

    private lateinit var analyticsService: AnalyticsService
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var nutritionCalculatorService: NutritionCalculatorService
    private lateinit var bodyMetricsRepository: BodyMetricsRepository

    private val testProfileId = UUID.randomUUID()
    private val today = LocalDate.now()
    private val now = Instant.now()

    private val testUser = User(
        id = testProfileId,
        email = "test@example.com",
        passwordHash = "hash",
        name = "Test User",
        dateOfBirth = LocalDate.of(1990, 1, 1),
        sex = Sex.MALE,
        heightCm = BigDecimal("180"),
        defaultActivityLevel = ActivityLevel.MODERATE,
        fitnessGoalType = FitnessGoalType.LOSE,
        fitnessGoalIntensity = FitnessGoalIntensity.NORMAL,
        createdAt = now,
        updatedAt = now
    )

    private val testBodyMetrics = BodyMetrics(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        date = today,
        weightKg = BigDecimal("80.0"),
        bodyFatPercentage = null,
        bodyFatKg = null,
        muscleMassPercentage = null,
        muscleMassKg = null,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setup() {
        analyticsRepository = mockk()
        userRepository = mockk()
        nutritionCalculatorService = mockk()
        bodyMetricsRepository = mockk()
        analyticsService = AnalyticsService(analyticsRepository, userRepository, nutritionCalculatorService, bodyMetricsRepository)

        // Default mocks for calorie goal calculation
        every { userRepository.findById(testProfileId) } returns testUser
        every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics
        every { nutritionCalculatorService.calculateBmr(any(), any(), any(), any()) } returns 1800
        every { nutritionCalculatorService.calculateTdee(any(), any()) } returns 2500
        every { nutritionCalculatorService.calculateDailyCalorieGoal(any<Int>(), any(), any()) } returns 2000
    }

    @Nested
    @DisplayName("getWeightTrend")
    inner class GetWeightTrend {

        @Test
        fun `should return weight trend for 30 day period`() {
            val startDate = today.minusDays(30)
            val weightData = listOf(
                DailyWeightData(startDate, BigDecimal("85.0"), null, null, null, null),
                DailyWeightData(today.minusDays(15), BigDecimal("84.0"), null, null, null, null),
                DailyWeightData(today, BigDecimal("83.0"), null, null, null, null)
            )

            every { analyticsRepository.getWeightData(testProfileId, any(), today) } returns weightData
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(startDate, BigDecimal("85.0"))

            val response = analyticsService.getWeightTrend(testProfileId, "30d")

            assertEquals("30d", response.period)
            assertEquals(3, response.dataPoints.size)
            assertNotNull(response.statistics)
            assertEquals(BigDecimal("85.0"), response.statistics?.startWeight)
            assertEquals(BigDecimal("83.0"), response.statistics?.endWeight)
            assertEquals(TrendDirection.DECREASING, response.statistics?.trend)
        }

        @Test
        fun `should return empty response when no weight data`() {
            every { analyticsRepository.getWeightData(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.getFirstWeight(testProfileId) } returns null

            val response = analyticsService.getWeightTrend(testProfileId, "30d")

            assertEquals("30d", response.period)
            assertTrue(response.dataPoints.isEmpty())
            assertNull(response.statistics)
            assertNull(response.trendLine)
        }

        @Test
        fun `should calculate trend line with linear regression`() {
            val weightData = listOf(
                DailyWeightData(today.minusDays(10), BigDecimal("85.0"), null, null, null, null),
                DailyWeightData(today.minusDays(5), BigDecimal("84.0"), null, null, null, null),
                DailyWeightData(today, BigDecimal("83.0"), null, null, null, null)
            )

            every { analyticsRepository.getWeightData(testProfileId, any(), today) } returns weightData
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(today.minusDays(10), BigDecimal("85.0"))

            val response = analyticsService.getWeightTrend(testProfileId, "30d")

            assertNotNull(response.trendLine)
            assertEquals(2, response.trendLine?.size)
            // First point should be close to 85, last point close to 83
            assertTrue(response.trendLine!![0].value >= BigDecimal("84"))
            assertTrue(response.trendLine!![1].value <= BigDecimal("84"))
        }

        @Test
        fun `should detect stable trend when change is minimal`() {
            val weightData = listOf(
                DailyWeightData(today.minusDays(10), BigDecimal("85.0"), null, null, null, null),
                DailyWeightData(today, BigDecimal("85.1"), null, null, null, null)
            )

            every { analyticsRepository.getWeightData(testProfileId, any(), today) } returns weightData
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(today.minusDays(10), BigDecimal("85.0"))

            val response = analyticsService.getWeightTrend(testProfileId, "30d")

            assertEquals(TrendDirection.STABLE, response.statistics?.trend)
        }
    }

    @Nested
    @DisplayName("getBodyCompositionTrend")
    inner class GetBodyCompositionTrend {

        @Test
        fun `should return body composition data`() {
            val bodyData = listOf(
                DailyWeightData(
                    today.minusDays(30), BigDecimal("85.0"),
                    BigDecimal("20.0"), BigDecimal("17.0"),
                    BigDecimal("40.0"), BigDecimal("34.0")
                ),
                DailyWeightData(
                    today, BigDecimal("83.0"),
                    BigDecimal("18.0"), BigDecimal("14.94"),
                    BigDecimal("42.0"), BigDecimal("34.86")
                )
            )

            every { analyticsRepository.getBodyCompositionData(testProfileId, any(), today) } returns bodyData
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(today.minusDays(30), BigDecimal("85.0"))

            val response = analyticsService.getBodyCompositionTrend(testProfileId, "30d", null)

            assertEquals("30d", response.period)
            assertEquals(2, response.dataPoints.size)
            assertNotNull(response.statistics)
            assertNotNull(response.statistics?.weight)
            assertNotNull(response.statistics?.bodyFat)
            assertNotNull(response.statistics?.muscleMass)
        }

        @Test
        fun `should calculate metric changes correctly`() {
            val bodyData = listOf(
                DailyWeightData(
                    today.minusDays(30), BigDecimal("100.0"),
                    BigDecimal("25.0"), BigDecimal("25.0"),
                    BigDecimal("35.0"), BigDecimal("35.0")
                ),
                DailyWeightData(
                    today, BigDecimal("95.0"),
                    BigDecimal("20.0"), BigDecimal("19.0"),
                    BigDecimal("38.0"), BigDecimal("36.1")
                )
            )

            every { analyticsRepository.getBodyCompositionData(testProfileId, any(), today) } returns bodyData
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(today.minusDays(30), BigDecimal("100.0"))

            val response = analyticsService.getBodyCompositionTrend(testProfileId, "30d", null)

            // Weight: 100 -> 95 = -5 (-5%)
            assertEquals(BigDecimal("-5.0"), response.statistics?.weight?.change)
            assertEquals(BigDecimal("-5.00"), response.statistics?.weight?.changePercent)

            // Body fat: 25 -> 20 = -5 (-20%)
            assertEquals(BigDecimal("-5.0"), response.statistics?.bodyFat?.change)
            assertEquals(BigDecimal("-20.00"), response.statistics?.bodyFat?.changePercent)

            // Muscle: 35 -> 38 = +3 (~8.57%)
            assertEquals(BigDecimal("3.0"), response.statistics?.muscleMass?.change)
        }
    }

    @Nested
    @DisplayName("getCalorieIntakeTrend")
    inner class GetCalorieIntakeTrend {

        @Test
        fun `should return calorie trend with adherence status`() {
            val calorieData = listOf(
                DailyCalorieData(today.minusDays(2), BigDecimal("2100"), BigDecimal("150"), BigDecimal("250"), BigDecimal("70"), 0),
                DailyCalorieData(today.minusDays(1), BigDecimal("1700"), BigDecimal("140"), BigDecimal("220"), BigDecimal("60"), 0),
                DailyCalorieData(today, BigDecimal("2500"), BigDecimal("180"), BigDecimal("300"), BigDecimal("90"), 1)
            )

            every { analyticsRepository.getDailyCalorieData(testProfileId, any(), today) } returns calorieData
            every { analyticsRepository.getWorkoutData(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.getFirstWeight(testProfileId) } returns null

            val response = analyticsService.getCalorieIntakeTrend(testProfileId, "30d")

            assertEquals("30d", response.period)
            assertEquals(2000, response.dailyGoal)
            assertEquals(3, response.dataPoints.size)

            // Day 1: 2100 cal, within 90-110% of 2000 = ON_TARGET
            assertEquals(AdherenceStatus.ON_TARGET, response.dataPoints[0].adherence)

            // Day 2: 1700 cal, <90% of 2000 (1800) = UNDER
            assertEquals(AdherenceStatus.UNDER, response.dataPoints[1].adherence)

            // Day 3: 2500 cal, >110% of 2000 = OVER
            assertEquals(AdherenceStatus.OVER, response.dataPoints[2].adherence)
        }

        @Test
        fun `should calculate calorie statistics`() {
            val calorieData = listOf(
                DailyCalorieData(today.minusDays(2), BigDecimal("2000"), BigDecimal("150"), BigDecimal("250"), BigDecimal("70"), 0),
                DailyCalorieData(today.minusDays(1), BigDecimal("1900"), BigDecimal("140"), BigDecimal("220"), BigDecimal("60"), 0),
                DailyCalorieData(today, BigDecimal("2100"), BigDecimal("160"), BigDecimal("260"), BigDecimal("75"), 0)
            )

            every { analyticsRepository.getDailyCalorieData(testProfileId, any(), today) } returns calorieData
            every { analyticsRepository.getWorkoutData(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.getFirstWeight(testProfileId) } returns null

            val response = analyticsService.getCalorieIntakeTrend(testProfileId, "30d")

            // Average: (2000 + 1900 + 2100) / 3 = 2000
            assertEquals(2000, response.statistics.averageIntake)
            // All within 90-110% of 2000
            assertEquals(3, response.statistics.daysOnTarget)
            assertEquals(0, response.statistics.daysUnder)
            assertEquals(0, response.statistics.daysOver)
        }

        @Test
        fun `should include workout calories`() {
            val calorieData = listOf(
                DailyCalorieData(today, BigDecimal("2500"), BigDecimal("180"), BigDecimal("300"), BigDecimal("90"), 0)
            )
            val workoutData = listOf(
                DailyWorkoutData(today, 1, 60, BigDecimal("400"), listOf("STRENGTH"))
            )

            every { analyticsRepository.getDailyCalorieData(testProfileId, any(), today) } returns calorieData
            every { analyticsRepository.getWorkoutData(testProfileId, any(), today) } returns workoutData
            every { analyticsRepository.getFirstWeight(testProfileId) } returns null

            val response = analyticsService.getCalorieIntakeTrend(testProfileId, "30d")

            assertEquals(400, response.dataPoints[0].workoutCalories)
            assertEquals(2100, response.dataPoints[0].netCalories) // 2500 - 400
        }
    }

    @Nested
    @DisplayName("getMacroDistribution")
    inner class GetMacroDistribution {

        @Test
        fun `should return single day macro distribution`() {
            every { analyticsRepository.getDayMacros(testProfileId, today) } returns Triple(
                BigDecimal("150"),  // protein
                BigDecimal("250"),  // carbs
                BigDecimal("70")    // fat
            )

            val response = analyticsService.getMacroDistribution(testProfileId, today, null, null)

            assertEquals(today, response.date)
            assertNull(response.startDate)
            assertNull(response.endDate)

            // Protein: 150g * 4 = 600 cal
            assertEquals(150, response.distribution.protein.grams)
            assertEquals(600, response.distribution.protein.calories)

            // Carbs: 250g * 4 = 1000 cal
            assertEquals(250, response.distribution.carbs.grams)
            assertEquals(1000, response.distribution.carbs.calories)

            // Fat: 70g * 9 = 630 cal
            assertEquals(70, response.distribution.fat.grams)
            assertEquals(630, response.distribution.fat.calories)

            // Total: 600 + 1000 + 630 = 2230 cal
            assertEquals(2230, response.total.calories)
        }

        @Test
        fun `should return date range macro averages`() {
            val startDate = today.minusDays(7)
            val calorieData = listOf(
                DailyCalorieData(startDate, BigDecimal("2000"), BigDecimal("140"), BigDecimal("240"), BigDecimal("65"), 0),
                DailyCalorieData(today.minusDays(3), BigDecimal("2100"), BigDecimal("150"), BigDecimal("250"), BigDecimal("70"), 0),
                DailyCalorieData(today, BigDecimal("2200"), BigDecimal("160"), BigDecimal("260"), BigDecimal("75"), 0)
            )

            every { analyticsRepository.getDailyCalorieData(testProfileId, startDate, today) } returns calorieData

            val response = analyticsService.getMacroDistribution(testProfileId, null, startDate, today)

            assertNull(response.date)
            assertEquals(startDate, response.startDate)
            assertEquals(today, response.endDate)
            assertEquals(3, response.daysWithData)

            // Average protein: (140 + 150 + 160) / 3 = 150
            assertEquals(150, response.distribution.protein.grams)

            // Should have daily breakdown
            assertNotNull(response.dailyBreakdown)
            assertEquals(3, response.dailyBreakdown?.size)
        }

        @Test
        fun `should throw exception for invalid date range`() {
            assertThrows<InvalidDateRangeException> {
                analyticsService.getMacroDistribution(testProfileId, null, today, today.minusDays(7))
            }
        }
    }

    @Nested
    @DisplayName("getDashboardSummary")
    inner class GetDashboardSummary {

        @Test
        fun `should return complete dashboard summary`() {
            val weekStart = today.minusDays(today.dayOfWeek.value - 1L)

            every { analyticsRepository.getDayMacros(testProfileId, today) } returns Triple(
                BigDecimal("120"), BigDecimal("200"), BigDecimal("55")
            )
            every { analyticsRepository.getWorkoutCaloriesForDate(testProfileId, today) } returns 350
            every { analyticsRepository.getWorkoutData(testProfileId, today, today) } returns listOf(
                DailyWorkoutData(today, 1, 60, BigDecimal("350"), listOf("STRENGTH"))
            )
            every { analyticsRepository.getWeekSummary(testProfileId, any(), any()) } returns mapOf(
                "avgCalories" to 2100,
                "daysWithData" to 5,
                "workouts" to 3,
                "cheatMeals" to 1
            )
            every { analyticsRepository.getDailyCalorieData(testProfileId, any(), any()) } returns emptyList()
            every { analyticsRepository.countWorkouts(testProfileId, any(), any()) } returns 3
            every { analyticsRepository.countCheatMeals(testProfileId, any(), any()) } returns 1
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(today.minusDays(30), BigDecimal("85.0"))
            every { analyticsRepository.getLatestWeightBefore(testProfileId, today) } returns BigDecimal("83.0")
            every { analyticsRepository.getDatesWithMeals(testProfileId, any(), any()) } returns listOf(
                today.minusDays(2), today.minusDays(1), today
            )
            every { analyticsRepository.getDatesWithWorkouts(testProfileId, any(), any()) } returns listOf(
                today.minusDays(1), today
            )

            val response = analyticsService.getDashboardSummary(testProfileId)

            // Today summary
            assertEquals(today, response.today.date)
            assertEquals(2000, response.today.calorieGoal)
            assertEquals(1, response.today.workouts)
            assertEquals(350, response.today.workoutCalories)

            // Week summary
            assertEquals(3, response.week.workouts)
            assertEquals(1, response.week.cheatMeals)

            // Progress summary
            assertNotNull(response.progress)
            assertEquals(BigDecimal("83.0"), response.progress?.currentWeight)
            assertEquals(BigDecimal("85.0"), response.progress?.startWeight)
            assertEquals(BigDecimal("2.0"), response.progress?.weightLost)

            // Streaks
            assertEquals(3, response.streaks.currentLoggingStreak)
            assertEquals(2, response.streaks.currentWorkoutStreak)
        }
    }

    @Nested
    @DisplayName("getStreaks")
    inner class GetStreaks {

        @Test
        fun `should calculate logging streak correctly`() {
            val loggingDates = listOf(
                today.minusDays(4),
                today.minusDays(3),
                today.minusDays(2),
                today.minusDays(1),
                today
            )

            every { analyticsRepository.getDatesWithMeals(testProfileId, any(), today) } returns loggingDates
            every { analyticsRepository.getDatesWithWorkouts(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.countCheatMeals(testProfileId, any(), any()) } returns 0
            every { analyticsRepository.getLastCheatMealDate(testProfileId) } returns null

            val response = analyticsService.getStreaks(testProfileId)

            assertEquals(5, response.logging.currentStreak)
            assertEquals(5, response.logging.longestStreak)
            assertEquals(5, response.logging.totalDaysLogged)
            assertEquals(today, response.logging.lastLoggedDate)
        }

        @Test
        fun `should handle broken streak`() {
            val loggingDates = listOf(
                today.minusDays(7),
                today.minusDays(6),
                today.minusDays(5),
                // Gap of 2 days
                today.minusDays(2),
                today.minusDays(1),
                today
            )

            every { analyticsRepository.getDatesWithMeals(testProfileId, any(), today) } returns loggingDates
            every { analyticsRepository.getDatesWithWorkouts(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.countCheatMeals(testProfileId, any(), any()) } returns 0
            every { analyticsRepository.getLastCheatMealDate(testProfileId) } returns null

            val response = analyticsService.getStreaks(testProfileId)

            assertEquals(3, response.logging.currentStreak) // Only last 3 days
            assertEquals(3, response.logging.longestStreak) // Both streaks are 3
        }

        @Test
        fun `should calculate cheat meal stats`() {
            every { analyticsRepository.getDatesWithMeals(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.getDatesWithWorkouts(testProfileId, any(), today) } returns emptyList()
            every { analyticsRepository.countCheatMeals(testProfileId, any(), any()) } answers {
                // Different counts based on date range
                val startDate = secondArg<LocalDate>()
                val endDate = thirdArg<LocalDate>()
                when {
                    startDate.month == today.month && startDate.dayOfMonth == 1 -> 4 // This month
                    startDate.dayOfWeek.value == 1 -> 1 // This week
                    else -> 12 // Year total
                }
            }
            every { analyticsRepository.getLastCheatMealDate(testProfileId) } returns today.minusDays(2)

            val response = analyticsService.getStreaks(testProfileId)

            assertEquals(today.minusDays(2), response.cheatMeals.lastCheatMealDate)
        }
    }

    @Nested
    @DisplayName("getGoalProgress")
    inner class GetGoalProgress {

        @Test
        fun `should calculate goal progress`() {
            val startDate = today.minusDays(30)

            every { userRepository.findById(testProfileId) } returns testUser
            every { analyticsRepository.getFirstWeight(testProfileId) } returns Pair(startDate, BigDecimal("85.0"))
            every { analyticsRepository.getLatestWeightBefore(testProfileId, today) } returns BigDecimal("82.0")

            val response = analyticsService.getGoalProgress(testProfileId)

            assertEquals(BigDecimal("82.0"), response.currentWeight)
            assertEquals(BigDecimal("85.0"), response.startWeight)
            assertEquals(startDate, response.startDate)
            assertEquals(BigDecimal("-3.0"), response.weightChange)
            assertNotNull(response.weeklyRate)
            assertNotNull(response.projectedWeightIn30Days)
        }

        @Test
        fun `should handle missing data`() {
            every { userRepository.findById(testProfileId) } returns testUser
            every { analyticsRepository.getFirstWeight(testProfileId) } returns null
            every { analyticsRepository.getLatestWeightBefore(testProfileId, today) } returns null

            val response = analyticsService.getGoalProgress(testProfileId)

            assertNull(response.currentWeight)
            assertNull(response.startWeight)
            assertNull(response.weightChange)
        }
    }

    @Nested
    @DisplayName("getAggregatedMetrics")
    inner class GetAggregatedMetrics {

        @Test
        fun `should aggregate data weekly`() {
            val startDate = today.minusDays(30)
            val weightData = listOf(
                Pair(today.minusDays(21), BigDecimal("85.0")),
                Pair(today.minusDays(14), BigDecimal("84.0")),
                Pair(today.minusDays(7), BigDecimal("83.5")),
                Pair(today, BigDecimal("83.0"))
            )
            val calorieData = listOf(
                Triple(today.minusDays(21), BigDecimal("14000"), 7),
                Triple(today.minusDays(14), BigDecimal("13500"), 7),
                Triple(today.minusDays(7), BigDecimal("14500"), 7),
                Triple(today, BigDecimal("7000"), 4) // Partial week
            )

            every { analyticsRepository.getAggregatedWeightData(testProfileId, startDate, today, any()) } returns weightData
            every { analyticsRepository.getAggregatedCalorieData(testProfileId, startDate, today, any()) } returns calorieData
            every { analyticsRepository.countWorkouts(testProfileId, startDate, today) } returns 12
            every { analyticsRepository.countCheatMeals(testProfileId, startDate, today) } returns 3

            val response = analyticsService.getAggregatedMetrics(
                testProfileId, startDate, today, null, "weekly"
            )

            assertEquals("weekly", response.aggregation)
            assertEquals(startDate, response.startDate)
            assertEquals(today, response.endDate)
            assertTrue(response.data.isNotEmpty())
            assertEquals(12, response.totals.workouts)
            assertEquals(3, response.totals.cheatMeals)
        }

        @Test
        fun `should throw exception for date range exceeding max`() {
            val startDate = today.minusYears(2)

            assertThrows<InvalidDateRangeException> {
                analyticsService.getAggregatedMetrics(
                    testProfileId, startDate, today, null, "monthly"
                )
            }
        }
    }

    @Nested
    @DisplayName("getWorkoutSummary")
    inner class GetWorkoutSummary {

        @Test
        fun `should return workout summary`() {
            val workoutData = listOf(
                DailyWorkoutData(today.minusDays(7), 1, 60, BigDecimal("400"), listOf("STRENGTH")),
                DailyWorkoutData(today.minusDays(5), 1, 45, BigDecimal("300"), listOf("CARDIO_RUNNING")),
                DailyWorkoutData(today.minusDays(3), 1, 60, BigDecimal("350"), listOf("STRENGTH")),
                DailyWorkoutData(today, 1, 30, BigDecimal("200"), listOf("YOGA"))
            )
            val typeDistribution = listOf(
                Pair("STRENGTH", 2),
                Pair("CARDIO_RUNNING", 1),
                Pair("YOGA", 1)
            )
            val caloriesByType = listOf(
                Pair("STRENGTH", 750),
                Pair("CARDIO_RUNNING", 300),
                Pair("YOGA", 200)
            )

            every { analyticsRepository.getWorkoutData(testProfileId, any(), today) } returns workoutData
            every { analyticsRepository.getWorkoutTypeDistribution(testProfileId, any(), today) } returns typeDistribution
            every { analyticsRepository.getWorkoutCaloriesByType(testProfileId, any(), today) } returns caloriesByType
            every { analyticsRepository.getFirstWeight(testProfileId) } returns null

            val response = analyticsService.getWorkoutSummary(testProfileId, "30d")

            assertEquals("30d", response.period)
            assertEquals(4, response.summary.totalWorkouts)
            assertEquals(195, response.summary.totalDurationMinutes) // 60+45+60+30
            assertEquals(1250, response.summary.totalCaloriesBurned)
            assertEquals(4, response.summary.consistency.daysWithWorkouts)

            // Type distribution
            assertEquals(3, response.byType.distribution.size)
            assertEquals("STRENGTH", response.byType.distribution[0].type)
            assertEquals(2, response.byType.distribution[0].count)
        }
    }
}
