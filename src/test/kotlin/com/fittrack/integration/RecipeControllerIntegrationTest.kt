package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.dto.recipe.AddIngredientRequest
import com.fittrack.model.dto.recipe.AddStepRequest
import com.fittrack.model.dto.recipe.CreateIngredientRequest
import com.fittrack.model.dto.recipe.CreateRecipeRequest
import com.fittrack.model.dto.recipe.CreateStepRequest
import com.fittrack.model.dto.recipe.ReorderStepsRequest
import com.fittrack.model.dto.recipe.UpdateRecipeRequest
import com.fittrack.model.dto.recipe.UpdateStepRequest
import com.fittrack.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("Recipe Controller Integration Tests")
class RecipeControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtService: JwtService

    private val passwordEncoder = BCryptPasswordEncoder()
    private lateinit var testUserId: UUID
    private lateinit var accessToken: String
    private lateinit var testFoodId: UUID

    @BeforeEach
    fun setupTestData() {
        // Create test user
        testUserId = UUID.randomUUID()
        val passwordHash = passwordEncoder.encode("password123")

        jdbcTemplate.update(
            """
            INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            testUserId, "recipe-test@example.com", passwordHash, "Recipe Test User",
            Instant.now(), Instant.now()
        )

        // Generate JWT token
        accessToken = jwtService.generateAccessToken(testUserId, "recipe-test@example.com")

        // Create test food
        testFoodId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO foods (id, profile_id, name, metric_type, calories_per_100, fat_per_100, carbs_per_100, protein_per_100, created_at, updated_at)
            VALUES (?, ?, ?, 'GRAMS'::metric_type, ?, ?, ?, ?, ?, ?)
            """,
            testFoodId, testUserId, "Chicken Breast",
            BigDecimal("165"), BigDecimal("3.6"), BigDecimal("0"), BigDecimal("31"),
            Instant.now(), Instant.now()
        )
    }

    @Nested
    @DisplayName("POST /api/v1/recipes")
    inner class CreateRecipe {

        @Test
        fun `should create recipe with ingredients and steps`() {
            val request = CreateRecipeRequest(
                name = "Grilled Chicken",
                description = "Simple grilled chicken breast",
                totalServings = 2,
                ingredients = listOf(
                    CreateIngredientRequest(
                        foodId = testFoodId,
                        quantity = BigDecimal("200")
                    )
                ),
                steps = listOf(
                    CreateStepRequest(
                        description = "Season the chicken with salt and pepper",
                        durationMinutes = 5
                    ),
                    CreateStepRequest(
                        description = "Grill for 6-8 minutes per side",
                        durationMinutes = 20
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/recipes")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Grilled Chicken"))
                .andExpect(jsonPath("$.description").value("Simple grilled chicken breast"))
                .andExpect(jsonPath("$.totalServings").value(2))
                .andExpect(jsonPath("$.ingredients.length()").value(1))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.totalDurationMinutes").value(25))
                .andExpect(jsonPath("$.nutritionPerServing.calories").isNumber)
        }

        @Test
        fun `should fail without authentication`() {
            val request = CreateRecipeRequest(
                name = "Test Recipe",
                totalServings = 1,
                ingredients = listOf(
                    CreateIngredientRequest(
                        foodId = testFoodId,
                        quantity = BigDecimal("100")
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/recipes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should fail with empty name`() {
            val request = mapOf(
                "name" to "",
                "totalServings" to 1,
                "ingredients" to listOf(
                    mapOf(
                        "foodId" to testFoodId.toString(),
                        "quantity" to 100
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/recipes")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail with no ingredients`() {
            val request = mapOf(
                "name" to "No Ingredients Recipe",
                "totalServings" to 1,
                "ingredients" to emptyList<Any>()
            )

            mockMvc.perform(
                post("/api/v1/recipes")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail with non-existent food`() {
            val request = CreateRecipeRequest(
                name = "Test Recipe",
                totalServings = 1,
                ingredients = listOf(
                    CreateIngredientRequest(
                        foodId = UUID.randomUUID(),
                        quantity = BigDecimal("100")
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/recipes")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("FOOD_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/recipes")
    inner class GetRecipes {

        @Test
        fun `should return paginated recipe list`() {
            // Create a recipe first
            createTestRecipe("Test Recipe 1")
            createTestRecipe("Test Recipe 2")

            mockMvc.perform(
                get("/api/v1/recipes")
                    .header("Authorization", "Bearer $accessToken")
                    .param("page", "0")
                    .param("size", "10")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2))
        }

        @Test
        fun `should filter by search query`() {
            createTestRecipe("Grilled Chicken")
            createTestRecipe("Beef Stew")

            mockMvc.perform(
                get("/api/v1/recipes")
                    .header("Authorization", "Bearer $accessToken")
                    .param("q", "chicken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grilled Chicken"))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/recipes/{id}")
    inner class GetRecipeById {

        @Test
        fun `should return recipe details`() {
            val recipeId = createTestRecipe("Detailed Recipe")

            mockMvc.perform(
                get("/api/v1/recipes/$recipeId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(recipeId.toString()))
                .andExpect(jsonPath("$.name").value("Detailed Recipe"))
                .andExpect(jsonPath("$.ingredients").isArray)
                .andExpect(jsonPath("$.steps").isArray)
        }

        @Test
        fun `should return 404 for non-existent recipe`() {
            mockMvc.perform(
                get("/api/v1/recipes/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/recipes/{id}")
    inner class UpdateRecipe {

        @Test
        fun `should update recipe name`() {
            val recipeId = createTestRecipe("Original Name")

            val request = UpdateRecipeRequest(name = "Updated Name")

            mockMvc.perform(
                put("/api/v1/recipes/$recipeId")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Name"))
        }

        @Test
        fun `should recalculate nutrition when servings change`() {
            val recipeId = createTestRecipe("Test Recipe")

            val request = UpdateRecipeRequest(totalServings = 4)

            mockMvc.perform(
                put("/api/v1/recipes/$recipeId")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalServings").value(4))
                .andExpect(jsonPath("$.nutritionPerServing").exists())
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/recipes/{id}")
    inner class DeleteRecipe {

        @Test
        fun `should delete recipe`() {
            val recipeId = createTestRecipe("To Be Deleted")

            mockMvc.perform(
                delete("/api/v1/recipes/$recipeId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNoContent)

            // Verify it's deleted
            mockMvc.perform(
                get("/api/v1/recipes/$recipeId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("Recipe Ingredients")
    inner class RecipeIngredients {

        @Test
        fun `should add ingredient to recipe`() {
            val recipeId = createTestRecipe("Test Recipe")

            // Create a second food
            val secondFoodId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO foods (id, profile_id, name, metric_type, calories_per_100, fat_per_100, carbs_per_100, protein_per_100, created_at, updated_at)
                VALUES (?, ?, ?, 'GRAMS'::metric_type, ?, ?, ?, ?, ?, ?)
                """,
                secondFoodId, testUserId, "Olive Oil",
                BigDecimal("884"), BigDecimal("100"), BigDecimal("0"), BigDecimal("0"),
                Instant.now(), Instant.now()
            )

            val request = AddIngredientRequest(
                foodId = secondFoodId,
                quantity = BigDecimal("15")
            )

            mockMvc.perform(
                post("/api/v1/recipes/$recipeId/ingredients")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.food.id").value(secondFoodId.toString()))
                .andExpect(jsonPath("$.quantity").value(15))
        }

        @Test
        fun `should fail to delete last ingredient`() {
            val recipeId = createTestRecipe("Single Ingredient Recipe")

            // Get ingredient ID
            val ingredientId = jdbcTemplate.queryForObject(
                "SELECT id FROM recipe_ingredients WHERE recipe_id = ?",
                Int::class.java,
                recipeId
            )

            mockMvc.perform(
                delete("/api/v1/recipes/$recipeId/ingredients/$ingredientId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("LAST_INGREDIENT"))
        }
    }

    @Nested
    @DisplayName("Recipe Steps")
    inner class RecipeSteps {

        @Test
        fun `should add step to recipe`() {
            val recipeId = createTestRecipe("Test Recipe")

            val request = AddStepRequest(
                description = "Let the meat rest for 5 minutes",
                durationMinutes = 5
            )

            mockMvc.perform(
                post("/api/v1/recipes/$recipeId/steps")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.description").value("Let the meat rest for 5 minutes"))
                .andExpect(jsonPath("$.durationMinutes").value(5))
        }

        @Test
        fun `should update step`() {
            val recipeId = createTestRecipeWithSteps()

            // Get step ID
            val stepId = jdbcTemplate.queryForObject(
                "SELECT id FROM recipe_steps WHERE recipe_id = ? ORDER BY step_number LIMIT 1",
                Int::class.java,
                recipeId
            )

            val request = UpdateStepRequest(
                description = "Updated step description",
                durationMinutes = 15
            )

            mockMvc.perform(
                patch("/api/v1/recipes/$recipeId/steps/$stepId")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.description").value("Updated step description"))
                .andExpect(jsonPath("$.durationMinutes").value(15))
        }

        @Test
        fun `should delete step and renumber remaining`() {
            val recipeId = createTestRecipeWithSteps()

            // Get first step ID
            val stepId = jdbcTemplate.queryForObject(
                "SELECT id FROM recipe_steps WHERE recipe_id = ? ORDER BY step_number LIMIT 1",
                Int::class.java,
                recipeId
            )

            mockMvc.perform(
                delete("/api/v1/recipes/$recipeId/steps/$stepId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNoContent)

            // Verify remaining step is renumbered
            val remainingStepNumber = jdbcTemplate.queryForObject(
                "SELECT step_number FROM recipe_steps WHERE recipe_id = ?",
                Int::class.java,
                recipeId
            )
            assert(remainingStepNumber == 1)
        }

        @Test
        fun `should reorder steps`() {
            val recipeId = createTestRecipeWithSteps()

            // Get step IDs in order
            val stepIds = jdbcTemplate.queryForList(
                "SELECT id FROM recipe_steps WHERE recipe_id = ? ORDER BY step_number",
                Int::class.java,
                recipeId
            )

            // Reverse the order
            val request = ReorderStepsRequest(stepIds = stepIds.reversed())

            mockMvc.perform(
                put("/api/v1/recipes/$recipeId/steps/reorder")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].id").value(stepIds[1]))
                .andExpect(jsonPath("$.steps[1].id").value(stepIds[0]))
        }
    }

    // ========== Helper Methods ==========

    private fun createTestRecipe(name: String): UUID {
        val recipeId = UUID.randomUUID()
        val now = Instant.now()

        jdbcTemplate.update(
            """
            INSERT INTO recipes (id, profile_id, name, total_servings, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            recipeId, testUserId, name, 2, now, now
        )

        // Add an ingredient
        jdbcTemplate.update(
            """
            INSERT INTO recipe_ingredients (recipe_id, food_id, quantity, amount_grams, display_order, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            recipeId, testFoodId, BigDecimal("200"), BigDecimal("200"), 0, now
        )

        // Update nutrition
        jdbcTemplate.update(
            """
            UPDATE recipes SET
                calories_per_serving = ?,
                fat_per_serving = ?,
                carbs_per_serving = ?,
                protein_per_serving = ?
            WHERE id = ?
            """,
            BigDecimal("165"), BigDecimal("3.6"), BigDecimal("0"), BigDecimal("31"), recipeId
        )

        return recipeId
    }

    private fun createTestRecipeWithSteps(): UUID {
        val recipeId = createTestRecipe("Recipe With Steps")
        val now = Instant.now()

        // Add two steps
        jdbcTemplate.update(
            """
            INSERT INTO recipe_steps (recipe_id, step_number, description, duration_minutes, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            recipeId, 1, "First step", 10, now
        )

        jdbcTemplate.update(
            """
            INSERT INTO recipe_steps (recipe_id, step_number, description, duration_minutes, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            recipeId, 2, "Second step", 15, now
        )

        jdbcTemplate.update(
            "UPDATE recipes SET total_duration_minutes = ? WHERE id = ?",
            25, recipeId
        )

        return recipeId
    }
}
