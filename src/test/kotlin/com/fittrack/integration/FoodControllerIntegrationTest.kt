package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.MetricType
import com.fittrack.model.dto.food.CreateFoodRequest
import com.fittrack.model.dto.food.CreatePortionRequest
import com.fittrack.model.dto.food.NutritionRequest
import com.fittrack.model.dto.food.PortionRequest
import com.fittrack.model.dto.food.UpdateFoodRequest
import com.fittrack.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@DisplayName("Food Controller Integration Tests")
class FoodControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var testUserId: UUID
    private lateinit var authToken: String

    @BeforeEach
    fun setupUser() {
        // Create a test user
        testUserId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, 'test@example.com', 'hash', 'Test User', NOW(), NOW())
            """.trimIndent(),
            testUserId
        )

        // Generate auth token
        authToken = jwtService.generateAccessToken(testUserId, "test@example.com")
    }

    @Nested
    @DisplayName("GET /api/v1/foods")
    inner class GetFoods {

        @Test
        fun `should return empty list when no foods exist`() {
            mockMvc.perform(
                get("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content").isEmpty)
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
        }

        @Test
        fun `should return foods with pagination`() {
            // Create test foods
            createTestFood("Chicken Breast")
            createTestFood("Salmon Fillet")

            mockMvc.perform(
                get("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .param("page", "0")
                    .param("size", "10")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
        }

        @Test
        fun `should search foods by name`() {
            createTestFood("Chicken Breast")
            createTestFood("Salmon Fillet")

            mockMvc.perform(
                get("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .param("q", "chicken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Chicken Breast"))
        }

        @Test
        fun `should filter by category`() {
            val foodId = createTestFood("Chicken Breast")
            jdbcTemplate.update("UPDATE foods SET category_id = 4 WHERE id = ?", foodId)

            mockMvc.perform(
                get("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .param("categoryId", "4")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should require authentication`() {
            mockMvc.perform(get("/api/v1/foods"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/foods/{id}")
    inner class GetFoodById {

        @Test
        fun `should return food details`() {
            val foodId = createTestFood("Chicken Breast")

            mockMvc.perform(
                get("/api/v1/foods/$foodId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(foodId.toString()))
                .andExpect(jsonPath("$.name").value("Chicken Breast"))
                .andExpect(jsonPath("$.nutrition.caloriesPer100").isNumber)
                .andExpect(jsonPath("$.portions").isArray)
        }

        @Test
        fun `should return 404 for non-existent food`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                get("/api/v1/foods/$nonExistentId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("FOOD_NOT_FOUND"))
        }

        @Test
        fun `should return 403 for food owned by another user`() {
            val otherUserId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
                VALUES (?, 'other@example.com', 'hash', 'Other User', NOW(), NOW())
                """.trimIndent(),
                otherUserId
            )

            val foodId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO foods (id, profile_id, name, metric_type, calories_per_100, fat_per_100, carbs_per_100, protein_per_100, created_at, updated_at)
                VALUES (?, ?, 'Other Food', 'GRAMS', 100, 5, 10, 20, NOW(), NOW())
                """.trimIndent(),
                foodId,
                otherUserId
            )

            mockMvc.perform(
                get("/api/v1/foods/$foodId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FOOD_NOT_OWNED"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/foods")
    inner class CreateFood {

        @Test
        fun `should create food successfully`() {
            val request = CreateFoodRequest(
                name = "Chicken Breast",
                metricType = MetricType.GRAMS,
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("165"),
                    fatPer100 = BigDecimal("3.6"),
                    carbsPer100 = BigDecimal("0"),
                    proteinPer100 = BigDecimal("31")
                ),
                portions = listOf(
                    PortionRequest("1 medium breast", BigDecimal("174"))
                )
            )

            mockMvc.perform(
                post("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.name").value("Chicken Breast"))
                .andExpect(jsonPath("$.portions.length()").value(1))
                .andExpect(jsonPath("$.portions[0].name").value("1 medium breast"))
        }

        @Test
        fun `should create food with category`() {
            val request = CreateFoodRequest(
                name = "Chicken Breast",
                categoryId = 4, // Protein category
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("165"),
                    fatPer100 = BigDecimal("3.6"),
                    carbsPer100 = BigDecimal("0"),
                    proteinPer100 = BigDecimal("31")
                )
            )

            mockMvc.perform(
                post("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.category.id").value(4))
                .andExpect(jsonPath("$.category.name").value("Protein"))
        }

        @Test
        fun `should return 400 for invalid nutrition values`() {
            val request = mapOf(
                "name" to "Test Food",
                "nutrition" to mapOf(
                    "caloriesPer100" to -10,
                    "fatPer100" to 5,
                    "carbsPer100" to 10,
                    "proteinPer100" to 20
                )
            )

            mockMvc.perform(
                post("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 for invalid category`() {
            val request = CreateFoodRequest(
                name = "Test Food",
                categoryId = 999,
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("100"),
                    fatPer100 = BigDecimal("5"),
                    carbsPer100 = BigDecimal("10"),
                    proteinPer100 = BigDecimal("20")
                )
            )

            mockMvc.perform(
                post("/api/v1/foods")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/foods/{id}")
    inner class UpdateFood {

        @Test
        fun `should update food name`() {
            val foodId = createTestFood("Chicken Breast")

            val request = UpdateFoodRequest(name = "Grilled Chicken Breast")

            mockMvc.perform(
                put("/api/v1/foods/$foodId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Grilled Chicken Breast"))
        }

        @Test
        fun `should update nutrition values`() {
            val foodId = createTestFood("Chicken Breast")

            val request = UpdateFoodRequest(
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("180"),
                    fatPer100 = BigDecimal("4.0"),
                    carbsPer100 = BigDecimal("0"),
                    proteinPer100 = BigDecimal("32")
                )
            )

            mockMvc.perform(
                put("/api/v1/foods/$foodId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nutrition.caloriesPer100").value(180))
        }

        @Test
        fun `should return 404 for non-existent food`() {
            val request = UpdateFoodRequest(name = "Updated")

            mockMvc.perform(
                put("/api/v1/foods/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/foods/{id}")
    inner class DeleteFood {

        @Test
        fun `should delete food successfully`() {
            val foodId = createTestFood("Chicken Breast")

            mockMvc.perform(
                delete("/api/v1/foods/$foodId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            // Verify deletion
            mockMvc.perform(
                get("/api/v1/foods/$foodId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 for non-existent food`() {
            mockMvc.perform(
                delete("/api/v1/foods/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/foods/{id}/portions")
    inner class GetPortions {

        @Test
        fun `should return portions for food`() {
            val foodId = createTestFood("Chicken Breast")

            // Add portions
            jdbcTemplate.update(
                "INSERT INTO food_portions (food_id, name, amount_grams, created_at) VALUES (?, '1 medium breast', 174, NOW())",
                foodId
            )
            jdbcTemplate.update(
                "INSERT INTO food_portions (food_id, name, amount_grams, created_at) VALUES (?, '1 cup shredded', 140, NOW())",
                foodId
            )

            mockMvc.perform(
                get("/api/v1/foods/$foodId/portions")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("1 medium breast"))
        }

        @Test
        fun `should return empty list when no portions exist`() {
            val foodId = createTestFood("Chicken Breast")

            mockMvc.perform(
                get("/api/v1/foods/$foodId/portions")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$").isEmpty)
        }

        @Test
        fun `should return 404 for non-existent food`() {
            mockMvc.perform(
                get("/api/v1/foods/${UUID.randomUUID()}/portions")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("FOOD_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/foods/{id}/portions")
    inner class AddPortion {

        @Test
        fun `should add portion successfully`() {
            val foodId = createTestFood("Chicken Breast")

            val request = CreatePortionRequest(
                name = "1 cup shredded",
                amountGrams = BigDecimal("140")
            )

            mockMvc.perform(
                post("/api/v1/foods/$foodId/portions")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("1 cup shredded"))
                .andExpect(jsonPath("$.amountGrams").value(140))
        }

        @Test
        fun `should return 409 for duplicate portion name`() {
            val foodId = createTestFood("Chicken Breast")

            // Add first portion
            jdbcTemplate.update(
                "INSERT INTO food_portions (food_id, name, amount_grams, created_at) VALUES (?, '1 cup', 140, NOW())",
                foodId
            )

            val request = CreatePortionRequest(
                name = "1 cup",
                amountGrams = BigDecimal("140")
            )

            mockMvc.perform(
                post("/api/v1/foods/$foodId/portions")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("PORTION_ALREADY_EXISTS"))
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/foods/{id}/portions/{portionId}")
    inner class DeletePortion {

        @Test
        fun `should delete portion successfully`() {
            val foodId = createTestFood("Chicken Breast")

            // Add a portion
            val portionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO food_portions (food_id, name, amount_grams, created_at)
                VALUES (?, '1 cup', 140, NOW())
                RETURNING id
                """.trimIndent(),
                Int::class.java,
                foodId
            )

            mockMvc.perform(
                delete("/api/v1/foods/$foodId/portions/$portionId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `should return 404 for non-existent portion`() {
            val foodId = createTestFood("Chicken Breast")

            mockMvc.perform(
                delete("/api/v1/foods/$foodId/portions/999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/foods/recent")
    inner class GetRecentFoods {

        @Test
        fun `should return empty list when no recent foods`() {
            mockMvc.perform(
                get("/api/v1/foods/recent")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.foods").isArray)
                .andExpect(jsonPath("$.foods").isEmpty)
        }

        @Test
        fun `should return recent foods with use count`() {
            val foodId = createTestFood("Chicken Breast")

            // Add to recent foods
            jdbcTemplate.update(
                "INSERT INTO recent_foods (profile_id, food_id, last_used_at, use_count) VALUES (?, ?, NOW(), 5)",
                testUserId,
                foodId
            )

            mockMvc.perform(
                get("/api/v1/foods/recent")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.foods.length()").value(1))
                .andExpect(jsonPath("$.foods[0].name").value("Chicken Breast"))
                .andExpect(jsonPath("$.foods[0].useCount").value(5))
        }
    }

    private fun createTestFood(name: String): UUID {
        val foodId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO foods (id, profile_id, name, metric_type, calories_per_100, fat_per_100, carbs_per_100, protein_per_100, created_at, updated_at)
            VALUES (?, ?, ?, 'GRAMS', 165, 3.6, 0, 31, NOW(), NOW())
            """.trimIndent(),
            foodId,
            testUserId,
            name
        )
        return foodId
    }
}
