package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Analytics Controller Integration Tests")
class AnalyticsControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    private lateinit var testUserId: UUID
    private lateinit var accessToken: String
    private lateinit var testEmail: String
    private val today = LocalDate.now()

    @BeforeEach
    fun setupTestUser() {
        testUserId = UUID.randomUUID()
        testEmail = "analytics-test-${UUID.randomUUID()}@example.com"
        val passwordHash = passwordEncoder.encode("Test123!@#")

        // Create test user with profile data needed for calorie calculations
        jdbcTemplate.update(
            """
            INSERT INTO profiles (id, email, password_hash, name, date_of_birth, sex, height_cm,
                                  default_activity_level, fitness_goal_type, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'MALE'::sex_type, ?, 'MODERATE'::activity_level, 'LOSE'::fitness_goal_type, NOW(), NOW())
            """,
            testUserId,
            testEmail,
            passwordHash,
            "Test User",
            LocalDate.of(1990, 1, 1),
            BigDecimal("180")
        )

        accessToken = jwtService.generateAccessToken(testUserId, testEmail)

        // Setup test data for analytics
        setupAnalyticsTestData()
    }

    private fun setupAnalyticsTestData() {
        // Create body metrics (weight data) - going back 30 days
        for (i in 0..10) {
            val date = today.minusDays(i.toLong() * 3)
            val weight = BigDecimal("85.0").subtract(BigDecimal(i.toString()).multiply(BigDecimal("0.2")))
            val bodyFat = BigDecimal("20.0").subtract(BigDecimal(i.toString()).multiply(BigDecimal("0.1")))
            val muscleMass = BigDecimal("40.0").add(BigDecimal(i.toString()).multiply(BigDecimal("0.05")))

            jdbcTemplate.update(
                """
                INSERT INTO body_metrics (id, profile_id, date, weight_kg, body_fat_percentage, muscle_mass_percentage, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                UUID.randomUUID(), testUserId, date, weight, bodyFat, muscleMass
            )
        }

        // Create days and meals (calorie data) for the past week
        for (i in 0..7) {
            val date = today.minusDays(i.toLong())
            val dayId = UUID.randomUUID()
            val mealId = UUID.randomUUID()

            // Create day
            jdbcTemplate.update(
                """
                INSERT INTO days (id, profile_id, date, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """,
                dayId, testUserId, date
            )

            // Create meal with varying calories
            val isCheatMeal = i == 3 // One cheat meal
            val calories = BigDecimal("2000").add(BigDecimal(i * 50))

            jdbcTemplate.update(
                """
                INSERT INTO meals (id, day_id, meal_type, is_cheat_meal, total_calories, total_protein, total_carbs, total_fat, created_at, updated_at)
                VALUES (?, ?, 'LUNCH'::meal_type, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                mealId, dayId, isCheatMeal,
                calories,
                BigDecimal("150"),
                BigDecimal("250"),
                BigDecimal("70")
            )

            // Create meal item
            jdbcTemplate.update(
                """
                INSERT INTO meal_items (id, meal_id, is_quick_entry, quick_entry_name, calories, protein, carbs, fat, quantity, created_at, updated_at)
                VALUES (?, ?, true, 'Test meal', ?, ?, ?, ?, 1, NOW(), NOW())
                """,
                UUID.randomUUID(), mealId,
                calories,
                BigDecimal("150"),
                BigDecimal("250"),
                BigDecimal("70")
            )
        }

        // Create workouts
        for (i in 0..4) {
            val date = today.minusDays(i.toLong() * 2)
            jdbcTemplate.update(
                """
                INSERT INTO workouts (id, profile_id, date, workout_type, duration_minutes, calories_burned_estimated, created_at, updated_at)
                VALUES (?, ?, ?, ?::workout_type, ?, ?, NOW(), NOW())
                """,
                UUID.randomUUID(), testUserId, date, "STRENGTH", 60, BigDecimal("350")
            )
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/weight")
    inner class GetWeightTrend {

        @Test
        fun `should return weight trend data`() {
            mockMvc.perform(
                get("/api/v1/analytics/weight")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "30d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.dataPoints").isArray)
                .andExpect(jsonPath("$.dataPoints").isNotEmpty)
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.statistics.trend").exists())
                .andExpect(jsonPath("$.statistics.startWeight").exists())
                .andExpect(jsonPath("$.statistics.endWeight").exists())
        }

        @Test
        fun `should return different periods`() {
            listOf("7d", "30d", "90d").forEach { period ->
                mockMvc.perform(
                    get("/api/v1/analytics/weight")
                        .header("Authorization", "Bearer $accessToken")
                        .param("period", period)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.period").value(period))
            }
        }

        @Test
        fun `should include trend line`() {
            mockMvc.perform(
                get("/api/v1/analytics/weight")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "30d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.trendLine").isArray)
                .andExpect(jsonPath("$.trendLine.length()").value(2))
        }

        @Test
        fun `should require authentication`() {
            mockMvc.perform(get("/api/v1/analytics/weight"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/body-composition")
    inner class GetBodyCompositionTrend {

        @Test
        fun `should return body composition data`() {
            mockMvc.perform(
                get("/api/v1/analytics/body-composition")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "30d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.dataPoints").isArray)
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.statistics.weight").exists())
                .andExpect(jsonPath("$.statistics.bodyFat").exists())
                .andExpect(jsonPath("$.statistics.muscleMass").exists())
        }

        @Test
        fun `should calculate changes correctly`() {
            mockMvc.perform(
                get("/api/v1/analytics/body-composition")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "30d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.statistics.weight.change").exists())
                .andExpect(jsonPath("$.statistics.weight.changePercent").exists())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/calories")
    inner class GetCalorieIntakeTrend {

        @Test
        fun `should return calorie trend with adherence`() {
            mockMvc.perform(
                get("/api/v1/analytics/calories")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "30d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.dailyGoal").isNumber)
                .andExpect(jsonPath("$.dataPoints").isArray)
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.statistics.averageIntake").isNumber)
                .andExpect(jsonPath("$.statistics.daysOnTarget").isNumber)
        }

        @Test
        fun `should include workout calories`() {
            mockMvc.perform(
                get("/api/v1/analytics/calories")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "7d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dataPoints[0].workoutCalories").exists())
                .andExpect(jsonPath("$.dataPoints[0].netCalories").exists())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/macros")
    inner class GetMacroDistribution {

        @Test
        fun `should return single day macro distribution`() {
            mockMvc.perform(
                get("/api/v1/analytics/macros")
                    .header("Authorization", "Bearer $accessToken")
                    .param("date", today.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.distribution").exists())
                .andExpect(jsonPath("$.distribution.protein.grams").isNumber)
                .andExpect(jsonPath("$.distribution.protein.calories").isNumber)
                .andExpect(jsonPath("$.distribution.protein.percent").isNumber)
                .andExpect(jsonPath("$.distribution.carbs").exists())
                .andExpect(jsonPath("$.distribution.fat").exists())
                .andExpect(jsonPath("$.total.calories").isNumber)
        }

        @Test
        fun `should return date range macro averages`() {
            val startDate = today.minusDays(7)

            mockMvc.perform(
                get("/api/v1/analytics/macros")
                    .header("Authorization", "Bearer $accessToken")
                    .param("startDate", startDate.toString())
                    .param("endDate", today.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(today.toString()))
                .andExpect(jsonPath("$.daysWithData").isNumber)
                .andExpect(jsonPath("$.dailyBreakdown").isArray)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/summary")
    inner class GetDashboardSummary {

        @Test
        fun `should return complete dashboard summary`() {
            mockMvc.perform(
                get("/api/v1/analytics/summary")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.today").exists())
                .andExpect(jsonPath("$.today.date").value(today.toString()))
                .andExpect(jsonPath("$.today.caloriesConsumed").isNumber)
                .andExpect(jsonPath("$.today.calorieGoal").isNumber)
                .andExpect(jsonPath("$.today.caloriesRemaining").isNumber)
                .andExpect(jsonPath("$.today.progressPercent").isNumber)
                .andExpect(jsonPath("$.today.macros").exists())
                .andExpect(jsonPath("$.today.macros.protein.consumed").isNumber)
                .andExpect(jsonPath("$.week").exists())
                .andExpect(jsonPath("$.week.workouts").isNumber)
                .andExpect(jsonPath("$.week.cheatMeals").isNumber)
                .andExpect(jsonPath("$.progress").exists())
                .andExpect(jsonPath("$.streaks").exists())
                .andExpect(jsonPath("$.streaks.currentLoggingStreak").isNumber)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/goals")
    inner class GetGoalProgress {

        @Test
        fun `should return goal progress`() {
            mockMvc.perform(
                get("/api/v1/analytics/goals")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.currentWeight").exists())
                .andExpect(jsonPath("$.startWeight").exists())
                .andExpect(jsonPath("$.weightChange").exists())
                .andExpect(jsonPath("$.weeklyRate").exists())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/streaks")
    inner class GetStreaks {

        @Test
        fun `should return streak data`() {
            mockMvc.perform(
                get("/api/v1/analytics/streaks")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.logging").exists())
                .andExpect(jsonPath("$.logging.currentStreak").isNumber)
                .andExpect(jsonPath("$.logging.longestStreak").isNumber)
                .andExpect(jsonPath("$.logging.totalDaysLogged").isNumber)
                .andExpect(jsonPath("$.workout").exists())
                .andExpect(jsonPath("$.workout.currentStreak").isNumber)
                .andExpect(jsonPath("$.workout.longestStreak").isNumber)
                .andExpect(jsonPath("$.cheatMeals").exists())
                .andExpect(jsonPath("$.cheatMeals.totalThisMonth").isNumber)
                .andExpect(jsonPath("$.cheatMeals.totalThisWeek").isNumber)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/aggregated")
    inner class GetAggregatedMetrics {

        @Test
        fun `should return aggregated metrics weekly`() {
            val startDate = today.minusDays(30)

            mockMvc.perform(
                get("/api/v1/analytics/aggregated")
                    .header("Authorization", "Bearer $accessToken")
                    .param("startDate", startDate.toString())
                    .param("endDate", today.toString())
                    .param("aggregation", "weekly")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(today.toString()))
                .andExpect(jsonPath("$.aggregation").value("weekly"))
                .andExpect(jsonPath("$.data").isArray)
                .andExpect(jsonPath("$.totals").exists())
                .andExpect(jsonPath("$.totals.calories").isNumber)
                .andExpect(jsonPath("$.totals.workouts").isNumber)
        }

        @Test
        fun `should return aggregated metrics monthly`() {
            val startDate = today.minusDays(60)

            mockMvc.perform(
                get("/api/v1/analytics/aggregated")
                    .header("Authorization", "Bearer $accessToken")
                    .param("startDate", startDate.toString())
                    .param("endDate", today.toString())
                    .param("aggregation", "monthly")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.aggregation").value("monthly"))
        }

        @Test
        fun `should require date parameters`() {
            mockMvc.perform(
                get("/api/v1/analytics/aggregated")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/workouts")
    inner class GetWorkoutSummary {

        @Test
        fun `should return workout summary`() {
            mockMvc.perform(
                get("/api/v1/analytics/workouts")
                    .header("Authorization", "Bearer $accessToken")
                    .param("period", "30d")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.totalWorkouts").isNumber)
                .andExpect(jsonPath("$.summary.totalDurationMinutes").isNumber)
                .andExpect(jsonPath("$.summary.totalCaloriesBurned").isNumber)
                .andExpect(jsonPath("$.summary.averageWorkoutsPerWeek").isNumber)
                .andExpect(jsonPath("$.summary.consistency").exists())
                .andExpect(jsonPath("$.summary.consistency.daysWithWorkouts").isNumber)
                .andExpect(jsonPath("$.byType").exists())
                .andExpect(jsonPath("$.byType.distribution").isArray)
                .andExpect(jsonPath("$.trend").isArray)
        }
    }

    @Nested
    @DisplayName("Empty Data Scenarios")
    inner class EmptyDataScenarios {

        @Test
        fun `should handle user with no data`() {
            // Create a new user with no data
            val emptyUserId = UUID.randomUUID()
            val emptyEmail = "empty-${UUID.randomUUID()}@example.com"

            jdbcTemplate.update(
                """
                INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                """,
                emptyUserId, emptyEmail, passwordEncoder.encode("Test123!@#"), "Empty User"
            )

            val emptyAccessToken = jwtService.generateAccessToken(emptyUserId, emptyEmail)

            // Weight trend should return empty
            mockMvc.perform(
                get("/api/v1/analytics/weight")
                    .header("Authorization", "Bearer $emptyAccessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dataPoints").isEmpty)
                .andExpect(jsonPath("$.statistics").doesNotExist())

            // Dashboard should still work
            mockMvc.perform(
                get("/api/v1/analytics/summary")
                    .header("Authorization", "Bearer $emptyAccessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.today.caloriesConsumed").value(0))
        }
    }
}
