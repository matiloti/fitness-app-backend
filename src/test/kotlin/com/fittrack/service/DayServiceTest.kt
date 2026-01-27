package com.fittrack.service

import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.exception.MissingProfileDataException
import com.fittrack.exception.ProfileNotFoundException
import com.fittrack.model.ActivityLevel
import com.fittrack.model.BodyMetric
import com.fittrack.model.Day
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Meal
import com.fittrack.model.MealType
import com.fittrack.model.Sex
import com.fittrack.model.User
import com.fittrack.model.dto.day.UpdateActivityLevelRequest
import com.fittrack.repository.DayRepository
import com.fittrack.repository.MealRepository
import com.fittrack.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Day Service Tests")
class DayServiceTest {

    private lateinit var dayService: DayService
    private lateinit var dayRepository: DayRepository
    private lateinit var mealRepository: MealRepository
    private lateinit var userRepository: UserRepository
    private lateinit var nutritionCalculator: NutritionCalculatorService

    private val testProfileId = UUID.randomUUID()
    private val testDayId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val testUser = User(
        id = testProfileId,
        email = "test@example.com",
        passwordHash = "hash",
        name = "Test User",
        dateOfBirth = LocalDate.of(1994, 1, 1),
        sex = Sex.MALE,
        heightCm = BigDecimal("180"),
        defaultActivityLevel = ActivityLevel.MODERATE,
        fitnessGoalType = FitnessGoalType.LOSE,
        fitnessGoalIntensity = FitnessGoalIntensity.NORMAL,
        createdAt = now,
        updatedAt = now
    )

    private val testDay = Day(
        id = testDayId,
        profileId = testProfileId,
        date = today,
        activityLevelOverride = null,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    private val testBodyMetric = BodyMetric(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        date = today,
        weightKg = BigDecimal("80"),
        bodyFatPercentage = BigDecimal("18"),
        bodyFatKg = null,
        muscleMassPercentage = null,
        muscleMassKg = null,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    private val testMeal = Meal(
        id = UUID.randomUUID(),
        dayId = testDayId,
        mealType = MealType.BREAKFAST,
        isCheatMeal = false,
        displayOrder = 0,
        totalCalories = BigDecimal("500"),
        totalFat = BigDecimal("20"),
        totalCarbs = BigDecimal("60"),
        totalProtein = BigDecimal("30"),
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setup() {
        dayRepository = mockk()
        mealRepository = mockk()
        userRepository = mockk()
        nutritionCalculator = NutritionCalculatorService()

        dayService = DayService(dayRepository, mealRepository, userRepository, nutritionCalculator)
    }

    @Nested
    @DisplayName("getDaySummary")
    inner class GetDaySummary {

        @Test
        fun `should return day summary with calculated goals`() {
            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findOrCreate(testProfileId, today) } returns testDay
            every { dayRepository.findLatestBodyMetric(testProfileId, today) } returns testBodyMetric
            every { mealRepository.getDayTotals(testDayId) } returns mapOf(
                "total_calories" to BigDecimal("1500"),
                "total_fat" to BigDecimal("50"),
                "total_carbs" to BigDecimal("180"),
                "total_protein" to BigDecimal("100")
            )
            every { mealRepository.findByDayId(testDayId) } returns listOf(testMeal)
            every { mealRepository.countItemsByMealId(testMeal.id) } returns 3
            every { dayRepository.findBodyMetricByProfileIdAndDate(testProfileId, today) } returns testBodyMetric
            every { dayRepository.hasProgressPhotosForMetric(testBodyMetric.id) } returns false

            val response = dayService.getDaySummary(testProfileId, today)

            assertNotNull(response)
            assertEquals(today, response.date)
            assertEquals(testDayId, response.dayId)
            assertEquals(ActivityLevel.MODERATE, response.activityLevel.level)
            assertFalse(response.activityLevel.isOverride)
            assertTrue(response.goals.calories > 0)
            assertEquals(1500, response.consumed.calories)
            assertEquals(1, response.meals.size)
        }

        @Test
        fun `should use activity level override when set`() {
            val dayWithOverride = testDay.copy(activityLevelOverride = ActivityLevel.HARD)

            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findOrCreate(testProfileId, today) } returns dayWithOverride
            every { dayRepository.findLatestBodyMetric(testProfileId, today) } returns testBodyMetric
            every { mealRepository.getDayTotals(testDayId) } returns emptyMap<String, BigDecimal>()
            every { mealRepository.findByDayId(testDayId) } returns emptyList()
            every { dayRepository.findBodyMetricByProfileIdAndDate(testProfileId, today) } returns null

            val response = dayService.getDaySummary(testProfileId, today)

            assertEquals(ActivityLevel.HARD, response.activityLevel.level)
            assertTrue(response.activityLevel.isOverride)
        }

        @Test
        fun `should throw ProfileNotFoundException when user does not exist`() {
            every { userRepository.findById(testProfileId) } returns null

            assertThrows<ProfileNotFoundException> {
                dayService.getDaySummary(testProfileId, today)
            }
        }
    }

    @Nested
    @DisplayName("updateActivityLevel")
    inner class UpdateActivityLevel {

        @Test
        fun `should update activity level override`() {
            val request = UpdateActivityLevelRequest(activityLevel = ActivityLevel.HARD)
            val updatedDay = testDay.copy(activityLevelOverride = ActivityLevel.HARD)

            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findOrCreate(testProfileId, today) } returns testDay
            every { dayRepository.updateActivityLevelOverride(testDayId, ActivityLevel.HARD) } returns updatedDay
            every { dayRepository.findLatestBodyMetric(testProfileId, today) } returns testBodyMetric

            val response = dayService.updateActivityLevel(testProfileId, today, request)

            assertEquals(ActivityLevel.HARD, response.activityLevel.level)
            assertTrue(response.activityLevel.isOverride)
            assertNotNull(response.defaultActivityLevel)
            assertEquals(ActivityLevel.MODERATE, response.defaultActivityLevel?.level)
        }
    }

    @Nested
    @DisplayName("removeActivityLevelOverride")
    inner class RemoveActivityLevelOverride {

        @Test
        fun `should remove activity level override`() {
            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findByProfileIdAndDate(testProfileId, today) } returns testDay.copy(activityLevelOverride = ActivityLevel.HARD)
            every { dayRepository.updateActivityLevelOverride(testDayId, null) } returns testDay
            every { dayRepository.findLatestBodyMetric(testProfileId, today) } returns testBodyMetric

            val response = dayService.removeActivityLevelOverride(testProfileId, today)

            assertEquals(ActivityLevel.MODERATE, response.activityLevel.level)
            assertFalse(response.activityLevel.isOverride)
            assertNull(response.defaultActivityLevel)

            verify { dayRepository.updateActivityLevelOverride(testDayId, null) }
        }
    }

    @Nested
    @DisplayName("getGoals")
    inner class GetGoals {

        @Test
        fun `should return calculated goals`() {
            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findByProfileIdAndDate(testProfileId, today) } returns testDay
            every { dayRepository.findLatestBodyMetric(testProfileId, today) } returns testBodyMetric

            val response = dayService.getGoals(testProfileId, today)

            assertNotNull(response)
            assertEquals(today, response.date)
            assertEquals(Sex.MALE, response.profile.sex)
            assertEquals(BigDecimal("80"), response.latestWeight)
            assertTrue(response.calculations.bmr > 0)
            assertTrue(response.calculations.tdee > response.calculations.bmr)
            assertTrue(response.calculations.dailyCalorieGoal > 0)
        }

        @Test
        fun `should throw MissingProfileDataException when profile data incomplete`() {
            val incompleteUser = testUser.copy(sex = null)

            every { userRepository.findById(testProfileId) } returns incompleteUser

            assertThrows<MissingProfileDataException> {
                dayService.getGoals(testProfileId, today)
            }
        }

        @Test
        fun `should throw MissingProfileDataException when no weight data`() {
            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findByProfileIdAndDate(testProfileId, today) } returns testDay
            every { dayRepository.findLatestBodyMetric(testProfileId, today) } returns null

            assertThrows<MissingProfileDataException> {
                dayService.getGoals(testProfileId, today)
            }
        }
    }

    @Nested
    @DisplayName("getDaysRange")
    inner class GetDaysRange {

        @Test
        fun `should return days range summary`() {
            val startDate = today.minusDays(7)
            val endDate = today

            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findByProfileIdAndDateRange(testProfileId, startDate, endDate) } returns listOf(testDay)
            every { dayRepository.findLatestBodyMetric(testProfileId, any()) } returns testBodyMetric
            every { mealRepository.findByDayId(testDayId) } returns listOf(testMeal)
            every { mealRepository.findByProfileIdAndDateRange(testProfileId, startDate, endDate) } returns listOf(testMeal)
            every { dayRepository.hasBodyMetricsForDate(testProfileId, any()) } returns false
            every { mealRepository.getDayTotals(testDayId) } returns mapOf(
                "total_calories" to BigDecimal("500"),
                "total_fat" to BigDecimal("20"),
                "total_carbs" to BigDecimal("60"),
                "total_protein" to BigDecimal("30")
            )

            val response = dayService.getDaysRange(testProfileId, startDate, endDate)

            assertNotNull(response)
            assertEquals(startDate, response.startDate)
            assertEquals(endDate, response.endDate)
            assertEquals(8, response.days.size) // 8 days including start and end
            assertTrue(response.summary.totalDays > 0)
        }

        @Test
        fun `should throw InvalidDateRangeException when range exceeds 90 days`() {
            val startDate = today.minusDays(100)
            val endDate = today

            every { userRepository.findById(testProfileId) } returns testUser

            assertThrows<InvalidDateRangeException> {
                dayService.getDaysRange(testProfileId, startDate, endDate)
            }
        }

        @Test
        fun `should throw InvalidDateRangeException when end date before start date`() {
            val startDate = today
            val endDate = today.minusDays(1)

            every { userRepository.findById(testProfileId) } returns testUser

            assertThrows<InvalidDateRangeException> {
                dayService.getDaysRange(testProfileId, startDate, endDate)
            }
        }
    }

    @Nested
    @DisplayName("getWeekOverview")
    inner class GetWeekOverview {

        @Test
        fun `should return week overview`() {
            every { userRepository.findById(testProfileId) } returns testUser
            every { dayRepository.findByProfileIdAndDateRange(testProfileId, any(), any()) } returns listOf(testDay)
            every { dayRepository.findLatestBodyMetric(testProfileId, any()) } returns testBodyMetric
            every { mealRepository.findByDayId(testDayId) } returns listOf(testMeal)
            every { mealRepository.getDayTotals(testDayId) } returns mapOf(
                "total_calories" to BigDecimal("1500"),
                "total_fat" to BigDecimal("50"),
                "total_carbs" to BigDecimal("180"),
                "total_protein" to BigDecimal("100")
            )
            every { dayRepository.hasBodyMetricsForDate(testProfileId, any()) } returns false

            val response = dayService.getWeekOverview(testProfileId, today)

            assertNotNull(response)
            assertEquals(7, response.days.size)
            assertNotNull(response.weekSummary)
        }
    }

    @Nested
    @DisplayName("getNavigation")
    inner class GetNavigation {

        @Test
        fun `should return navigation context`() {
            every { dayRepository.hasMealsForDate(testProfileId, any()) } returns true
            every { dayRepository.findFirstDayWithData(testProfileId) } returns today.minusDays(30)
            every { dayRepository.findLastDayWithData(testProfileId) } returns today

            val response = dayService.getNavigation(testProfileId, today)

            assertNotNull(response)
            assertEquals(today, response.current.date)
            assertEquals(true, response.current.isToday)
            assertTrue(response.current.hasMeals)
            assertEquals(today.minusDays(1), response.previous?.date)
            assertEquals(today.plusDays(1), response.next?.date)
        }
    }
}
