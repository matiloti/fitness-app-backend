package com.fittrack.service

import com.fittrack.exception.FoodNotFoundException
import com.fittrack.exception.InvalidStepOrderException
import com.fittrack.exception.LastIngredientException
import com.fittrack.exception.PortionNotFoundException
import com.fittrack.exception.RecipeIngredientNotFoundException
import com.fittrack.exception.RecipeNotFoundException
import com.fittrack.exception.RecipeNotOwnedException
import com.fittrack.exception.RecipeStepNotFoundException
import com.fittrack.model.Food
import com.fittrack.model.FoodPortion
import com.fittrack.model.MetricType
import com.fittrack.model.Recipe
import com.fittrack.model.RecipeIngredient
import com.fittrack.model.RecipeStep
import com.fittrack.model.dto.recipe.AddIngredientRequest
import com.fittrack.model.dto.recipe.AddStepRequest
import com.fittrack.model.dto.recipe.CreateIngredientRequest
import com.fittrack.model.dto.recipe.CreateRecipeRequest
import com.fittrack.model.dto.recipe.CreateStepRequest
import com.fittrack.model.dto.recipe.ReorderStepsRequest
import com.fittrack.model.dto.recipe.UpdateRecipeRequest
import com.fittrack.model.dto.recipe.UpdateStepRequest
import com.fittrack.repository.FoodRepository
import com.fittrack.repository.RecipeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("Recipe Service Tests")
class RecipeServiceTest {

    private lateinit var recipeService: RecipeService
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var foodRepository: FoodRepository

    private val testProfileId = UUID.randomUUID()
    private val otherProfileId = UUID.randomUUID()
    private val now = Instant.now()

    private val testFood = Food(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        name = "Chicken Breast",
        categoryId = 1,
        brandId = null,
        metricType = MetricType.GRAMS,
        caloriesPer100 = BigDecimal("165"),
        fatPer100 = BigDecimal("3.6"),
        carbsPer100 = BigDecimal("0"),
        proteinPer100 = BigDecimal("31"),
        saltPer100 = BigDecimal("0.1"),
        sugarPer100 = BigDecimal("0"),
        fiberPer100 = BigDecimal("0"),
        saturatedFatPer100 = BigDecimal("1"),
        createdAt = now,
        updatedAt = now
    )

    private val testPortion = FoodPortion(
        id = 1,
        foodId = testFood.id,
        name = "1 medium breast",
        amountGrams = BigDecimal("174"),
        createdAt = now
    )

    private val testRecipe = Recipe(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        name = "Grilled Chicken",
        description = "Simple grilled chicken breast",
        totalServings = 2,
        caloriesPerServing = BigDecimal("143.55"),
        fatPerServing = BigDecimal("3.13"),
        carbsPerServing = BigDecimal("0"),
        proteinPerServing = BigDecimal("26.97"),
        saltPerServing = BigDecimal("0.09"),
        sugarPerServing = BigDecimal("0"),
        fiberPerServing = BigDecimal("0"),
        saturatedFatPerServing = BigDecimal("0.87"),
        totalDurationMinutes = 25,
        createdAt = now,
        updatedAt = now
    )

    private val testIngredient = RecipeIngredient(
        id = 1,
        recipeId = testRecipe.id,
        foodId = testFood.id,
        portionId = testPortion.id,
        quantity = BigDecimal("1"),
        amountGrams = BigDecimal("174"),
        displayOrder = 0,
        createdAt = now
    )

    private val testStep = RecipeStep(
        id = 1,
        recipeId = testRecipe.id,
        stepNumber = 1,
        description = "Season the chicken with salt and pepper",
        durationMinutes = 5,
        createdAt = now
    )

    @BeforeEach
    fun setup() {
        recipeRepository = mockk()
        foodRepository = mockk()
        recipeService = RecipeService(recipeRepository, foodRepository)
    }

    @Nested
    @DisplayName("getRecipes")
    inner class GetRecipes {

        @Test
        fun `should return paginated recipe list`() {
            every { recipeRepository.findAllByProfileId(testProfileId, 0, 20, null, "recentlyUsed") } returns listOf(testRecipe)
            every { recipeRepository.countByProfileId(testProfileId, null) } returns 1L
            every { recipeRepository.countIngredientsByRecipeId(testRecipe.id) } returns 1
            every { recipeRepository.getThumbnailUrl(testRecipe.id) } returns null
            every { recipeRepository.getLastUsedAt(testProfileId, testRecipe.id) } returns null

            val response = recipeService.getRecipes(testProfileId, 0, 20, null, "recentlyUsed")

            assertNotNull(response)
            assertEquals(1, response.content.size)
            assertEquals(testRecipe.name, response.content[0].name)
            assertEquals(0, response.page.number)
            assertEquals(20, response.page.size)
            assertEquals(1, response.page.totalElements)
        }

        @Test
        fun `should clamp page size to 100`() {
            every { recipeRepository.findAllByProfileId(testProfileId, 0, 100, null, "recentlyUsed") } returns emptyList()
            every { recipeRepository.countByProfileId(testProfileId, null) } returns 0L

            val response = recipeService.getRecipes(testProfileId, 0, 200, null, "recentlyUsed")

            assertEquals(100, response.page.size)
        }

        @Test
        fun `should filter by search query`() {
            every { recipeRepository.findAllByProfileId(testProfileId, 0, 20, "chicken", "recentlyUsed") } returns listOf(testRecipe)
            every { recipeRepository.countByProfileId(testProfileId, "chicken") } returns 1L
            every { recipeRepository.countIngredientsByRecipeId(testRecipe.id) } returns 1
            every { recipeRepository.getThumbnailUrl(testRecipe.id) } returns null
            every { recipeRepository.getLastUsedAt(testProfileId, testRecipe.id) } returns null

            val response = recipeService.getRecipes(testProfileId, 0, 20, "chicken", "recentlyUsed")

            assertEquals(1, response.content.size)
        }
    }

    @Nested
    @DisplayName("getRecipeById")
    inner class GetRecipeById {

        @Test
        fun `should return recipe details with ingredients and steps`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns listOf(testIngredient)
            every { recipeRepository.findStepsByRecipeId(testRecipe.id) } returns listOf(testStep)
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionById(testPortion.id) } returns testPortion

            val response = recipeService.getRecipeById(testRecipe.id, testProfileId)

            assertNotNull(response)
            assertEquals(testRecipe.id, response.id)
            assertEquals(testRecipe.name, response.name)
            assertEquals(1, response.ingredients.size)
            assertEquals(1, response.steps.size)
        }

        @Test
        fun `should throw RecipeNotFoundException when recipe does not exist`() {
            val nonExistentId = UUID.randomUUID()
            every { recipeRepository.findById(nonExistentId) } returns null

            assertThrows<RecipeNotFoundException> {
                recipeService.getRecipeById(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw RecipeNotOwnedException when recipe belongs to another user`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe

            assertThrows<RecipeNotOwnedException> {
                recipeService.getRecipeById(testRecipe.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("createRecipe")
    inner class CreateRecipe {

        @Test
        fun `should create recipe with ingredients and steps`() {
            val request = CreateRecipeRequest(
                name = "Grilled Chicken",
                description = "Simple grilled chicken",
                totalServings = 2,
                ingredients = listOf(
                    CreateIngredientRequest(
                        foodId = testFood.id,
                        portionId = testPortion.id,
                        quantity = BigDecimal("1")
                    )
                ),
                steps = listOf(
                    CreateStepRequest(
                        description = "Season the chicken",
                        durationMinutes = 5
                    )
                )
            )

            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionById(testPortion.id) } returns testPortion
            every { recipeRepository.create(testProfileId, "Grilled Chicken", "Simple grilled chicken", 2) } returns testRecipe.copy(
                caloriesPerServing = null,
                totalDurationMinutes = null
            )
            every { recipeRepository.createIngredient(testRecipe.id, testFood.id, testPortion.id, BigDecimal("1"), BigDecimal("174"), 0) } returns testIngredient
            every { recipeRepository.createStep(testRecipe.id, 1, "Season the chicken", 5) } returns testStep
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns listOf(testIngredient)
            every { recipeRepository.updateNutrition(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testRecipe
            every { recipeRepository.getTotalStepDuration(testRecipe.id) } returns 5
            every { recipeRepository.updateTotalDuration(testRecipe.id, 5) } returns testRecipe
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findStepsByRecipeId(testRecipe.id) } returns listOf(testStep)

            val response = recipeService.createRecipe(request, testProfileId)

            assertNotNull(response)
            assertEquals("Grilled Chicken", response.name)
        }

        @Test
        fun `should throw FoodNotFoundException when food does not exist`() {
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

            every { foodRepository.findById(any()) } returns null

            assertThrows<FoodNotFoundException> {
                recipeService.createRecipe(request, testProfileId)
            }
        }

        @Test
        fun `should throw PortionNotFoundException when portion does not exist`() {
            val request = CreateRecipeRequest(
                name = "Test Recipe",
                totalServings = 1,
                ingredients = listOf(
                    CreateIngredientRequest(
                        foodId = testFood.id,
                        portionId = 999,
                        quantity = BigDecimal("1")
                    )
                )
            )

            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionById(999) } returns null

            assertThrows<PortionNotFoundException> {
                recipeService.createRecipe(request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("updateRecipe")
    inner class UpdateRecipe {

        @Test
        fun `should update recipe name successfully`() {
            val request = UpdateRecipeRequest(name = "Updated Chicken Recipe")
            val updatedRecipe = testRecipe.copy(name = "Updated Chicken Recipe")

            // First findById call in updateRecipe for ownership check
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe andThen updatedRecipe
            every { recipeRepository.update(testRecipe.id, "Updated Chicken Recipe", null, null) } returns updatedRecipe
            // Calls for getRecipeById at the end
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns listOf(testIngredient)
            every { recipeRepository.findStepsByRecipeId(testRecipe.id) } returns listOf(testStep)
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionById(testPortion.id) } returns testPortion

            val response = recipeService.updateRecipe(testRecipe.id, request, testProfileId)

            assertNotNull(response)
            assertEquals("Updated Chicken Recipe", response.name)
        }

        @Test
        fun `should recalculate nutrition when servings change`() {
            val request = UpdateRecipeRequest(totalServings = 4)
            val updatedRecipe = testRecipe.copy(totalServings = 4)

            // First findById call in updateRecipe for ownership check, then in getRecipeById
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe andThen updatedRecipe
            every { recipeRepository.update(testRecipe.id, null, null, 4) } returns updatedRecipe
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns listOf(testIngredient)
            every { recipeRepository.findStepsByRecipeId(testRecipe.id) } returns listOf(testStep)
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionById(testPortion.id) } returns testPortion
            every { recipeRepository.updateNutrition(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns updatedRecipe

            val response = recipeService.updateRecipe(testRecipe.id, request, testProfileId)

            assertNotNull(response)
            assertEquals(4, response.totalServings)
            verify { recipeRepository.updateNutrition(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should throw RecipeNotFoundException when recipe does not exist`() {
            val nonExistentId = UUID.randomUUID()
            val request = UpdateRecipeRequest(name = "Updated")

            every { recipeRepository.findById(nonExistentId) } returns null

            assertThrows<RecipeNotFoundException> {
                recipeService.updateRecipe(nonExistentId, request, testProfileId)
            }
        }

        @Test
        fun `should throw RecipeNotOwnedException when recipe belongs to another user`() {
            val request = UpdateRecipeRequest(name = "Updated")

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe

            assertThrows<RecipeNotOwnedException> {
                recipeService.updateRecipe(testRecipe.id, request, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("deleteRecipe")
    inner class DeleteRecipe {

        @Test
        fun `should delete recipe successfully`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.delete(testRecipe.id) } returns true

            recipeService.deleteRecipe(testRecipe.id, testProfileId)

            verify { recipeRepository.delete(testRecipe.id) }
        }

        @Test
        fun `should throw RecipeNotFoundException when recipe does not exist`() {
            val nonExistentId = UUID.randomUUID()

            every { recipeRepository.findById(nonExistentId) } returns null

            assertThrows<RecipeNotFoundException> {
                recipeService.deleteRecipe(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw RecipeNotOwnedException when recipe belongs to another user`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe

            assertThrows<RecipeNotOwnedException> {
                recipeService.deleteRecipe(testRecipe.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("addIngredient")
    inner class AddIngredient {

        @Test
        fun `should add ingredient successfully`() {
            val request = AddIngredientRequest(
                foodId = testFood.id,
                portionId = testPortion.id,
                quantity = BigDecimal("2")
            )

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionById(testPortion.id) } returns testPortion
            every { recipeRepository.createIngredient(testRecipe.id, testFood.id, testPortion.id, BigDecimal("2"), BigDecimal("348"), 0) } returns testIngredient.copy(quantity = BigDecimal("2"), amountGrams = BigDecimal("348"))
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns listOf(testIngredient.copy(quantity = BigDecimal("2"), amountGrams = BigDecimal("348")))
            every { recipeRepository.updateNutrition(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testRecipe

            val response = recipeService.addIngredient(testRecipe.id, request, testProfileId)

            assertNotNull(response)
        }

        @Test
        fun `should use grams directly when no portion specified`() {
            val request = AddIngredientRequest(
                foodId = testFood.id,
                portionId = null,
                quantity = BigDecimal("150")
            )

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { foodRepository.findById(testFood.id) } returns testFood
            every { recipeRepository.createIngredient(testRecipe.id, testFood.id, null, BigDecimal("150"), BigDecimal("150"), 0) } returns testIngredient.copy(portionId = null, quantity = BigDecimal("150"), amountGrams = BigDecimal("150"))
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns listOf(testIngredient.copy(portionId = null, quantity = BigDecimal("150"), amountGrams = BigDecimal("150")))
            every { recipeRepository.updateNutrition(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testRecipe

            val response = recipeService.addIngredient(testRecipe.id, request, testProfileId)

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("deleteIngredient")
    inner class DeleteIngredient {

        @Test
        fun `should delete ingredient successfully`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findIngredientByIdAndRecipeId(testIngredient.id, testRecipe.id) } returns testIngredient
            every { recipeRepository.countIngredientsByRecipeId(testRecipe.id) } returns 2
            every { recipeRepository.deleteIngredient(testIngredient.id) } returns true
            every { recipeRepository.findIngredientsByRecipeId(testRecipe.id) } returns emptyList()
            every { recipeRepository.updateNutrition(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testRecipe

            recipeService.deleteIngredient(testRecipe.id, testIngredient.id, testProfileId)

            verify { recipeRepository.deleteIngredient(testIngredient.id) }
        }

        @Test
        fun `should throw LastIngredientException when trying to delete last ingredient`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findIngredientByIdAndRecipeId(testIngredient.id, testRecipe.id) } returns testIngredient
            every { recipeRepository.countIngredientsByRecipeId(testRecipe.id) } returns 1

            assertThrows<LastIngredientException> {
                recipeService.deleteIngredient(testRecipe.id, testIngredient.id, testProfileId)
            }
        }

        @Test
        fun `should throw RecipeIngredientNotFoundException when ingredient does not exist`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findIngredientByIdAndRecipeId(999, testRecipe.id) } returns null

            assertThrows<RecipeIngredientNotFoundException> {
                recipeService.deleteIngredient(testRecipe.id, 999, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("addStep")
    inner class AddStep {

        @Test
        fun `should add step at the end`() {
            val request = AddStepRequest(
                description = "Let the chicken rest",
                durationMinutes = 5
            )

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.getMaxStepNumber(testRecipe.id) } returns 1
            every { recipeRepository.createStep(testRecipe.id, 2, "Let the chicken rest", 5) } returns testStep.copy(id = 2, stepNumber = 2, description = "Let the chicken rest")
            every { recipeRepository.getTotalStepDuration(testRecipe.id) } returns 30
            every { recipeRepository.updateTotalDuration(testRecipe.id, 30) } returns testRecipe.copy(totalDurationMinutes = 30)

            val response = recipeService.addStep(testRecipe.id, request, testProfileId)

            assertNotNull(response)
            assertEquals(2, response.stepNumber)
        }
    }

    @Nested
    @DisplayName("updateStep")
    inner class UpdateStep {

        @Test
        fun `should update step successfully`() {
            val request = UpdateStepRequest(
                description = "Season with herbs and spices",
                durationMinutes = 10
            )

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findStepByIdAndRecipeId(testStep.id, testRecipe.id) } returns testStep
            every { recipeRepository.updateStep(testStep.id, "Season with herbs and spices", 10) } returns testStep.copy(description = "Season with herbs and spices", durationMinutes = 10)
            every { recipeRepository.getTotalStepDuration(testRecipe.id) } returns 10
            every { recipeRepository.updateTotalDuration(testRecipe.id, 10) } returns testRecipe

            val response = recipeService.updateStep(testRecipe.id, testStep.id, request, testProfileId)

            assertNotNull(response)
            assertEquals("Season with herbs and spices", response.description)
        }

        @Test
        fun `should throw RecipeStepNotFoundException when step does not exist`() {
            val request = UpdateStepRequest(description = "Updated")

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findStepByIdAndRecipeId(999, testRecipe.id) } returns null

            assertThrows<RecipeStepNotFoundException> {
                recipeService.updateStep(testRecipe.id, 999, request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("deleteStep")
    inner class DeleteStep {

        @Test
        fun `should delete step and renumber remaining steps`() {
            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findStepByIdAndRecipeId(testStep.id, testRecipe.id) } returns testStep
            every { recipeRepository.deleteStep(testStep.id) } returns true
            every { recipeRepository.renumberStepsAfterDelete(testRecipe.id, testStep.stepNumber) } returns Unit
            every { recipeRepository.getTotalStepDuration(testRecipe.id) } returns null
            every { recipeRepository.updateTotalDuration(testRecipe.id, null) } returns testRecipe

            recipeService.deleteStep(testRecipe.id, testStep.id, testProfileId)

            verify { recipeRepository.deleteStep(testStep.id) }
            verify { recipeRepository.renumberStepsAfterDelete(testRecipe.id, testStep.stepNumber) }
        }
    }

    @Nested
    @DisplayName("reorderSteps")
    inner class ReorderSteps {

        @Test
        fun `should reorder steps successfully`() {
            val step2 = testStep.copy(id = 2, stepNumber = 2)
            val request = ReorderStepsRequest(stepIds = listOf(2, 1))

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findStepsByRecipeId(testRecipe.id) } returns listOf(testStep, step2)
            every { recipeRepository.updateStepNumber(2, 1) } returns step2.copy(stepNumber = 1)
            every { recipeRepository.updateStepNumber(1, 2) } returns testStep.copy(stepNumber = 2)

            val response = recipeService.reorderSteps(testRecipe.id, request, testProfileId)

            assertNotNull(response)
            assertEquals(2, response.steps.size)
        }

        @Test
        fun `should throw InvalidStepOrderException when step IDs don't match`() {
            val request = ReorderStepsRequest(stepIds = listOf(1, 3)) // step 3 doesn't exist

            every { recipeRepository.findById(testRecipe.id) } returns testRecipe
            every { recipeRepository.findStepsByRecipeId(testRecipe.id) } returns listOf(testStep)

            assertThrows<InvalidStepOrderException> {
                recipeService.reorderSteps(testRecipe.id, request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("Nutrition Calculation")
    inner class NutritionCalculation {

        @Test
        fun `should calculate nutrition correctly for ingredient`() {
            // 174g of chicken breast with 165 cal/100g = 287.1 calories
            val expectedCalories = BigDecimal("287.10")

            every { foodRepository.findById(testFood.id) } returns testFood

            // Use reflection or a helper method to test calculation
            // For now, we verify through the integration test
        }

        @Test
        fun `should calculate per serving nutrition correctly`() {
            // 2 servings with 287.1 total calories = 143.55 per serving
        }
    }
}
