package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.WorkoutType
import com.fittrack.model.dto.workout.CreateWorkoutRequest
import com.fittrack.model.dto.workout.UpdateWorkoutRequest
import com.fittrack.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Workout Controller Integration Tests")
class WorkoutControllerIntegrationTest : IntegrationTestBase() {

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
        testEmail = "workout-test-${UUID.randomUUID()}@example.com"
        val passwordHash = passwordEncoder.encode("Test123!@#")

        jdbcTemplate.update(
            """
            INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """,
            testUserId,
            testEmail,
            passwordHash,
            "Test User"
        )

        // Create body metrics for calorie calculations
        jdbcTemplate.update(
            """
            INSERT INTO body_metrics (id, profile_id, date, weight_kg, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """,
            UUID.randomUUID(),
            testUserId,
            today.minusDays(1),
            BigDecimal("82.5")
        )

        accessToken = jwtService.generateAccessToken(testUserId, testEmail)
    }

    private fun createWorkout(
        date: LocalDate = today,
        workoutType: WorkoutType = WorkoutType.STRENGTH,
        name: String? = "Test Workout",
        durationMinutes: Int = 60,
        caloriesBurnedEstimated: BigDecimal = BigDecimal("412.50"),
        caloriesBurnedActual: BigDecimal? = null,
        notes: String? = null
    ): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO workouts (id, profile_id, date, workout_type, name, duration_minutes,
                                  calories_burned_estimated, calories_burned_actual, notes, created_at, updated_at)
            VALUES (?, ?, ?, ?::workout_type, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            id, testUserId, date, workoutType.name, name, durationMinutes,
            caloriesBurnedEstimated, caloriesBurnedActual, notes
        )
        return id
    }

    @Nested
    @DisplayName("GET /api/v1/workouts")
    inner class GetWorkouts {

        @Test
        fun `should return paginated workout list with summary`() {
            createWorkout(today, WorkoutType.STRENGTH, "Upper Body", 60)
            createWorkout(today.minusDays(1), WorkoutType.CARDIO_RUNNING, "Morning Run", 30)

            mockMvc.perform(
                get("/api/v1/workouts")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].date").value(today.toString()))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.totalWorkouts").value(2))
        }

        @Test
        fun `should filter by date range`() {
            createWorkout(today)
            createWorkout(today.minusDays(10))
            createWorkout(today.minusDays(60)) // Outside default 30-day range

            mockMvc.perform(
                get("/api/v1/workouts")
                    .param("startDate", today.minusDays(30).toString())
                    .param("endDate", today.toString())
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
        }

        @Test
        fun `should filter by workout type`() {
            createWorkout(today, WorkoutType.STRENGTH)
            createWorkout(today.minusDays(1), WorkoutType.CARDIO_RUNNING)

            mockMvc.perform(
                get("/api/v1/workouts")
                    .param("workoutType", "STRENGTH")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].workoutType").value("STRENGTH"))
        }

        @Test
        fun `should return 401 without authentication`() {
            mockMvc.perform(get("/api/v1/workouts"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workouts/{id}")
    inner class GetWorkoutById {

        @Test
        fun `should return workout details with calculation info`() {
            val workoutId = createWorkout()

            mockMvc.perform(
                get("/api/v1/workouts/$workoutId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(workoutId.toString()))
                .andExpect(jsonPath("$.workoutType").value("STRENGTH"))
                .andExpect(jsonPath("$.workoutTypeInfo").exists())
                .andExpect(jsonPath("$.workoutTypeInfo.metValue").value(5.0))
                .andExpect(jsonPath("$.calculationDetails").exists())
                .andExpect(jsonPath("$.calculationDetails.weightUsed").value(82.5))
        }

        @Test
        fun `should return 404 for non-existent workout`() {
            mockMvc.perform(
                get("/api/v1/workouts/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 for workout belonging to another user`() {
            // Create another user and their workout
            val otherUserId = UUID.randomUUID()
            val otherEmail = "other-${UUID.randomUUID()}@example.com"
            jdbcTemplate.update(
                """
                INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                """,
                otherUserId, otherEmail, passwordEncoder.encode("Test123!@#"), "Other User"
            )

            val otherWorkoutId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO workouts (id, profile_id, date, workout_type, duration_minutes, created_at, updated_at)
                VALUES (?, ?, ?, ?::workout_type, ?, NOW(), NOW())
                """,
                otherWorkoutId, otherUserId, today, "STRENGTH", 60
            )

            mockMvc.perform(
                get("/api/v1/workouts/$otherWorkoutId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workouts")
    inner class CreateWorkout {

        @Test
        fun `should create workout with calculated estimated calories`() {
            val request = CreateWorkoutRequest(
                date = today,
                workoutType = WorkoutType.STRENGTH,
                name = "Upper Body Push",
                durationMinutes = 60,
                notes = "PR on bench press"
            )

            mockMvc.perform(
                post("/api/v1/workouts")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.workoutType").value("STRENGTH"))
                .andExpect(jsonPath("$.name").value("Upper Body Push"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.caloriesBurnedEstimated").exists())
                .andExpect(jsonPath("$.caloriesBurned").exists())
                .andExpect(jsonPath("$.notes").value("PR on bench press"))
        }

        @Test
        fun `should create workout with user-provided actual calories`() {
            val request = CreateWorkoutRequest(
                date = today,
                workoutType = WorkoutType.CARDIO_RUNNING,
                name = "Morning 5K",
                durationMinutes = 28,
                caloriesBurnedActual = BigDecimal("410")
            )

            mockMvc.perform(
                post("/api/v1/workouts")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.caloriesBurnedActual").value(410))
                .andExpect(jsonPath("$.caloriesBurned").value(410))
        }

        @Test
        fun `should return 400 for invalid duration`() {
            val request = mapOf(
                "date" to today.toString(),
                "workoutType" to "STRENGTH",
                "durationMinutes" to 0
            )

            mockMvc.perform(
                post("/api/v1/workouts")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/workouts/{id}")
    inner class UpdateWorkout {

        @Test
        fun `should update workout successfully`() {
            val workoutId = createWorkout()

            val request = UpdateWorkoutRequest(
                name = "Upper Body Push (Heavy)",
                durationMinutes = 75
            )

            mockMvc.perform(
                put("/api/v1/workouts/$workoutId")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Upper Body Push (Heavy)"))
                .andExpect(jsonPath("$.durationMinutes").value(75))
        }

        @Test
        fun `should clear actual calories when clearActualCalories is true`() {
            val workoutId = createWorkout(caloriesBurnedActual = BigDecimal("400"))

            val request = UpdateWorkoutRequest(clearActualCalories = true)

            mockMvc.perform(
                put("/api/v1/workouts/$workoutId")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.caloriesBurnedActual").doesNotExist())
        }

        @Test
        fun `should return 404 for non-existent workout`() {
            val request = UpdateWorkoutRequest(name = "Updated")

            mockMvc.perform(
                put("/api/v1/workouts/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/workouts/{id}")
    inner class DeleteWorkout {

        @Test
        fun `should delete workout successfully`() {
            val workoutId = createWorkout()

            mockMvc.perform(
                delete("/api/v1/workouts/$workoutId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNoContent)

            // Verify deleted
            mockMvc.perform(
                get("/api/v1/workouts/$workoutId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 for non-existent workout`() {
            mockMvc.perform(
                delete("/api/v1/workouts/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workouts/types")
    inner class GetWorkoutTypes {

        @Test
        fun `should return all workout types with MET values`() {
            mockMvc.perform(
                get("/api/v1/workouts/types")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.workoutTypes").isArray)
                .andExpect(jsonPath("$.workoutTypes.length()").value(10)) // All 10 workout types
                .andExpect(jsonPath("$.workoutTypes[0].type").exists())
                .andExpect(jsonPath("$.workoutTypes[0].metValue").exists())
                .andExpect(jsonPath("$.workoutTypes[0].description").exists())
                .andExpect(jsonPath("$.workoutTypes[0].estimatedCaloriesPerHour").exists())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workouts/estimate")
    inner class EstimateCalories {

        @Test
        fun `should estimate calories correctly`() {
            mockMvc.perform(
                get("/api/v1/workouts/estimate")
                    .param("workoutType", "CARDIO_RUNNING")
                    .param("durationMinutes", "30")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.workoutType").value("CARDIO_RUNNING"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.estimatedCalories").exists())
                .andExpect(jsonPath("$.calculation.weightUsed").value(82.5))
                .andExpect(jsonPath("$.calculation.metValue").value(9.8))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workouts/summary")
    inner class GetWorkoutSummary {

        @Test
        fun `should return workout summary for date range`() {
            createWorkout(today, WorkoutType.STRENGTH, "Upper Body", 60)
            createWorkout(today.minusDays(1), WorkoutType.CARDIO_RUNNING, "Morning Run", 30)
            createWorkout(today.minusDays(2), WorkoutType.STRENGTH, "Lower Body", 45)

            mockMvc.perform(
                get("/api/v1/workouts/summary")
                    .param("startDate", today.minusDays(30).toString())
                    .param("endDate", today.toString())
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.period.startDate").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.period.endDate").value(today.toString()))
                .andExpect(jsonPath("$.summary.totalWorkouts").value(3))
                .andExpect(jsonPath("$.summary.totalDurationMinutes").value(135))
                .andExpect(jsonPath("$.byType").isArray)
                .andExpect(jsonPath("$.weeklyTrend").isArray)
        }

        @Test
        fun `should return empty summary when no workouts`() {
            mockMvc.perform(
                get("/api/v1/workouts/summary")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.summary.totalWorkouts").value(0))
        }
    }
}
