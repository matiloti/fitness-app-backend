package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import com.fittrack.model.dto.auth.RegisterRequest
import com.fittrack.model.dto.profile.UpdateActivityLevelRequest
import com.fittrack.model.dto.profile.UpdateFitnessGoalRequest
import com.fittrack.model.dto.profile.UpdateProfileMetricsRequest
import com.fittrack.model.dto.profile.UpdateProfileRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("Profile Controller Integration Tests")
class ProfileControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Nested
    @DisplayName("GET /api/v1/profile")
    inner class GetProfile {

        @Test
        fun `should return profile for authenticated user`() {
            val accessToken = registerAndGetAccessToken("profile@example.com")

            mockMvc.perform(
                get("/api/v1/profile")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.email").value("profile@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.activityLevel.level").value("MODERATE"))
                .andExpect(jsonPath("$.fitnessGoal.type").value("MAINTAIN"))
        }

        @Test
        fun `should return 401 for unauthenticated request`() {
            mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/profile")
    inner class UpdateProfile {

        @Test
        fun `should update profile name`() {
            val accessToken = registerAndGetAccessToken("update@example.com")

            val request = UpdateProfileRequest(name = "Updated Name")

            mockMvc.perform(
                patch("/api/v1/profile")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Name"))
        }

        @Test
        fun `should update profile photo url`() {
            val accessToken = registerAndGetAccessToken("photo@example.com")

            val request = UpdateProfileRequest(photoUrl = "https://example.com/photo.jpg")

            mockMvc.perform(
                patch("/api/v1/profile")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.photoUrl").value("https://example.com/photo.jpg"))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/profile/metrics")
    inner class UpdateMetrics {

        @Test
        fun `should update profile metrics`() {
            val accessToken = registerAndGetAccessToken("metrics@example.com")

            val request = UpdateProfileMetricsRequest(
                dateOfBirth = LocalDate.of(1990, 5, 15),
                sex = Sex.MALE,
                heightCm = BigDecimal("180.5")
            )

            mockMvc.perform(
                put("/api/v1/profile/metrics")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"))
                .andExpect(jsonPath("$.sex").value("MALE"))
                .andExpect(jsonPath("$.heightCm").value(180.5))
                .andExpect(jsonPath("$.age").exists())
        }

        @Test
        fun `should return 400 for invalid height`() {
            val accessToken = registerAndGetAccessToken("metrics2@example.com")

            val request = UpdateProfileMetricsRequest(
                dateOfBirth = LocalDate.of(1990, 5, 15),
                sex = Sex.MALE,
                heightCm = BigDecimal("10") // Too short
            )

            mockMvc.perform(
                put("/api/v1/profile/metrics")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/profile/activity-level")
    inner class UpdateActivityLevel {

        @Test
        fun `should update activity level`() {
            val accessToken = registerAndGetAccessToken("activity@example.com")

            val request = UpdateActivityLevelRequest(activityLevel = ActivityLevel.HARD)

            mockMvc.perform(
                put("/api/v1/profile/activity-level")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.activityLevel.level").value("HARD"))
                .andExpect(jsonPath("$.activityLevel.multiplier").value(1.725))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/profile/fitness-goal")
    inner class UpdateFitnessGoal {

        @Test
        fun `should update fitness goal to LOSE`() {
            val accessToken = registerAndGetAccessToken("goal@example.com")

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.LOSE,
                intensity = FitnessGoalIntensity.NORMAL
            )

            mockMvc.perform(
                put("/api/v1/profile/fitness-goal")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fitnessGoal.type").value("LOSE"))
                .andExpect(jsonPath("$.fitnessGoal.intensity").value("NORMAL"))
                .andExpect(jsonPath("$.fitnessGoal.dailyCalorieAdjustment").value(-500))
        }

        @Test
        fun `should update fitness goal to MAINTAIN`() {
            val accessToken = registerAndGetAccessToken("maintain@example.com")

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.MAINTAIN,
                intensity = null
            )

            mockMvc.perform(
                put("/api/v1/profile/fitness-goal")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fitnessGoal.type").value("MAINTAIN"))
                .andExpect(jsonPath("$.fitnessGoal.dailyCalorieAdjustment").value(0))
        }

        @Test
        fun `should return 400 when LOSE goal has no intensity`() {
            val accessToken = registerAndGetAccessToken("nointensity@example.com")

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.LOSE,
                intensity = null
            )

            mockMvc.perform(
                put("/api/v1/profile/fitness-goal")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_FITNESS_GOAL"))
        }

        @Test
        fun `should return 400 when MAINTAIN goal has intensity`() {
            val accessToken = registerAndGetAccessToken("withintensity@example.com")

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.MAINTAIN,
                intensity = FitnessGoalIntensity.NORMAL
            )

            mockMvc.perform(
                put("/api/v1/profile/fitness-goal")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_FITNESS_GOAL"))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/profile/activity-levels")
    inner class GetActivityLevels {

        @Test
        fun `should return all activity levels without authentication`() {
            mockMvc.perform(get("/api/v1/profile/activity-levels"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.activityLevels").isArray)
                .andExpect(jsonPath("$.activityLevels.length()").value(6))
                .andExpect(jsonPath("$.activityLevels[0].level").value("SEDENTARY"))
                .andExpect(jsonPath("$.activityLevels[0].multiplier").value(1.2))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/profile/fitness-goals")
    inner class GetFitnessGoals {

        @Test
        fun `should return all fitness goals without authentication`() {
            mockMvc.perform(get("/api/v1/profile/fitness-goals"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fitnessGoals").isArray)
                .andExpect(jsonPath("$.fitnessGoals.length()").value(9)) // 1 MAINTAIN + 4 LOSE + 4 GAIN
                .andExpect(jsonPath("$.fitnessGoals[0].type").value("MAINTAIN"))
                .andExpect(jsonPath("$.fitnessGoals[0].adjustment").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/profile/countries")
    inner class GetCountries {

        @Test
        fun `should return countries without authentication`() {
            mockMvc.perform(get("/api/v1/profile/countries"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.countries").isArray)
        }

        @Test
        fun `should filter countries by search parameter`() {
            // This will return empty until we seed countries data
            mockMvc.perform(get("/api/v1/profile/countries").param("search", "United"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.countries").isArray)
        }
    }

    // ========== Helper Methods ==========

    private fun registerAndGetAccessToken(email: String): String {
        val request = RegisterRequest(
            email = email,
            password = "Password123!",
            name = "Test User"
        )
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        return response.get("accessToken").asText()
    }
}
