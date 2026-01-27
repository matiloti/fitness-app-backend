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
import com.fittrack.model.Recipe
import com.fittrack.model.RecipeIngredient
import com.fittrack.model.RecipeStep
import com.fittrack.model.dto.food.PageInfo
import com.fittrack.model.dto.food.PortionResponse
import com.fittrack.model.dto.recipe.AddIngredientRequest
import com.fittrack.model.dto.recipe.AddStepRequest
import com.fittrack.model.dto.recipe.CreateRecipeRequest
import com.fittrack.model.dto.recipe.FoodDetailSummary
import com.fittrack.model.dto.recipe.FoodSummary
import com.fittrack.model.dto.recipe.NutritionSummary
import com.fittrack.model.dto.recipe.RecipeDetailResponse
import com.fittrack.model.dto.recipe.RecipeIngredientDetailResponse
import com.fittrack.model.dto.recipe.RecipeIngredientResponse
import com.fittrack.model.dto.recipe.RecipeListItem
import com.fittrack.model.dto.recipe.RecipeListResponse
import com.fittrack.model.dto.recipe.RecipeStepResponse
import com.fittrack.model.dto.recipe.ReorderStepsRequest
import com.fittrack.model.dto.recipe.ReorderStepsResponse
import com.fittrack.model.dto.recipe.UpdateIngredientRequest
import com.fittrack.model.dto.recipe.UpdateRecipeRequest
import com.fittrack.model.dto.recipe.UpdateStepRequest
import com.fittrack.repository.FoodRepository
import com.fittrack.repository.RecipeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.math.ceil

@Service
class RecipeService(
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository
) {

    // ========== Recipe CRUD ==========

    fun getRecipes(
        profileId: UUID,
        page: Int,
        size: Int,
        search: String?,
        sort: String
    ): RecipeListResponse {
        val validatedSize = size.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)

        val recipes = recipeRepository.findAllByProfileId(
            profileId = profileId,
            page = validatedPage,
            size = validatedSize,
            search = search,
            sort = sort
        )

        val totalElements = recipeRepository.countByProfileId(profileId, search)

        val content = recipes.map { recipe ->
            val ingredientCount = recipeRepository.countIngredientsByRecipeId(recipe.id)
            val thumbnailUrl = recipeRepository.getThumbnailUrl(recipe.id)
            val lastUsedAt = recipeRepository.getLastUsedAt(profileId, recipe.id)

            toRecipeListItem(recipe, ingredientCount, thumbnailUrl, lastUsedAt)
        }

        return RecipeListResponse(
            content = content,
            page = PageInfo(
                number = validatedPage,
                size = validatedSize,
                totalElements = totalElements,
                totalPages = ceil(totalElements.toDouble() / validatedSize).toInt()
            )
        )
    }

    fun getRecipeById(id: UUID, profileId: UUID): RecipeDetailResponse {
        val recipe = recipeRepository.findById(id)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val ingredients = recipeRepository.findIngredientsByRecipeId(id)
        val steps = recipeRepository.findStepsByRecipeId(id)

        return toRecipeDetailResponse(recipe, ingredients, steps)
    }

    @Transactional
    fun createRecipe(request: CreateRecipeRequest, profileId: UUID): RecipeDetailResponse {
        // Validate all foods and portions exist
        val ingredientDetails = request.ingredients.map { ingredientReq ->
            val food = foodRepository.findById(ingredientReq.foodId)
                ?: throw FoodNotFoundException()

            val portion = ingredientReq.portionId?.let { portionId ->
                foodRepository.findPortionById(portionId)
                    ?: throw PortionNotFoundException()
            }

            Triple(food, portion, ingredientReq)
        }

        // Create the recipe
        val recipe = recipeRepository.create(
            profileId = profileId,
            name = request.name.trim(),
            description = request.description?.trim(),
            totalServings = request.totalServings
        )

        // Create ingredients
        ingredientDetails.forEachIndexed { index, (food, portion, ingredientReq) ->
            val amountGrams = calculateAmountGrams(portion, ingredientReq.quantity)
            recipeRepository.createIngredient(
                recipeId = recipe.id,
                foodId = ingredientReq.foodId,
                portionId = ingredientReq.portionId,
                quantity = ingredientReq.quantity,
                amountGrams = amountGrams,
                displayOrder = index
            )
        }

        // Create steps
        request.steps.forEachIndexed { index, stepReq ->
            recipeRepository.createStep(
                recipeId = recipe.id,
                stepNumber = index + 1,
                description = stepReq.description.trim(),
                durationMinutes = stepReq.durationMinutes
            )
        }

        // Calculate and update nutrition
        updateRecipeNutrition(recipe.id, request.totalServings)

        // Update total duration
        val totalDuration = recipeRepository.getTotalStepDuration(recipe.id)
        recipeRepository.updateTotalDuration(recipe.id, totalDuration)

        // Return full details
        return getRecipeById(recipe.id, profileId)
    }

    @Transactional
    fun updateRecipe(id: UUID, request: UpdateRecipeRequest, profileId: UUID): RecipeDetailResponse {
        val recipe = recipeRepository.findById(id)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        recipeRepository.update(
            id = id,
            name = request.name?.trim(),
            description = request.description?.trim(),
            totalServings = request.totalServings
        )

        // Recalculate nutrition if servings changed
        if (request.totalServings != null && request.totalServings != recipe.totalServings) {
            updateRecipeNutrition(id, request.totalServings)
        }

        return getRecipeById(id, profileId)
    }

    @Transactional
    fun deleteRecipe(id: UUID, profileId: UUID) {
        val recipe = recipeRepository.findById(id)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        recipeRepository.delete(id)
    }

    // ========== Ingredient Methods ==========

    @Transactional
    fun addIngredient(recipeId: UUID, request: AddIngredientRequest, profileId: UUID): RecipeIngredientResponse {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val food = foodRepository.findById(request.foodId)
            ?: throw FoodNotFoundException()

        val portion = request.portionId?.let { portionId ->
            foodRepository.findPortionById(portionId)
                ?: throw PortionNotFoundException()
        }

        val amountGrams = calculateAmountGrams(portion, request.quantity)

        val ingredient = recipeRepository.createIngredient(
            recipeId = recipeId,
            foodId = request.foodId,
            portionId = request.portionId,
            quantity = request.quantity,
            amountGrams = amountGrams,
            displayOrder = 0
        )

        // Update nutrition
        updateRecipeNutrition(recipeId, recipe.totalServings)

        return toRecipeIngredientResponse(ingredient, food, portion)
    }

    @Transactional
    fun updateIngredient(
        recipeId: UUID,
        ingredientId: Int,
        request: UpdateIngredientRequest,
        profileId: UUID
    ): RecipeIngredientResponse {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val existingIngredient = recipeRepository.findIngredientByIdAndRecipeId(ingredientId, recipeId)
            ?: throw RecipeIngredientNotFoundException()

        val food = foodRepository.findById(existingIngredient.foodId)
            ?: throw FoodNotFoundException()

        val portion = request.portionId?.let { portionId ->
            foodRepository.findPortionById(portionId)
                ?: throw PortionNotFoundException()
        }

        val quantity = request.quantity ?: existingIngredient.quantity
        val amountGrams = calculateAmountGrams(portion, quantity)

        val updatedIngredient = recipeRepository.updateIngredient(
            id = ingredientId,
            portionId = request.portionId,
            quantity = request.quantity,
            amountGrams = amountGrams
        ) ?: throw RecipeIngredientNotFoundException()

        // Update nutrition
        updateRecipeNutrition(recipeId, recipe.totalServings)

        return toRecipeIngredientResponse(updatedIngredient, food, portion)
    }

    @Transactional
    fun deleteIngredient(recipeId: UUID, ingredientId: Int, profileId: UUID) {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val ingredient = recipeRepository.findIngredientByIdAndRecipeId(ingredientId, recipeId)
            ?: throw RecipeIngredientNotFoundException()

        // Check if this is the last ingredient
        val ingredientCount = recipeRepository.countIngredientsByRecipeId(recipeId)
        if (ingredientCount <= 1) {
            throw LastIngredientException()
        }

        recipeRepository.deleteIngredient(ingredientId)

        // Update nutrition
        updateRecipeNutrition(recipeId, recipe.totalServings)
    }

    // ========== Step Methods ==========

    @Transactional
    fun addStep(recipeId: UUID, request: AddStepRequest, profileId: UUID): RecipeStepResponse {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val maxStepNumber = recipeRepository.getMaxStepNumber(recipeId)

        val step = recipeRepository.createStep(
            recipeId = recipeId,
            stepNumber = maxStepNumber + 1,
            description = request.description.trim(),
            durationMinutes = request.durationMinutes
        )

        // Update total duration
        val totalDuration = recipeRepository.getTotalStepDuration(recipeId)
        recipeRepository.updateTotalDuration(recipeId, totalDuration)

        return toRecipeStepResponse(step)
    }

    @Transactional
    fun updateStep(recipeId: UUID, stepId: Int, request: UpdateStepRequest, profileId: UUID): RecipeStepResponse {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val existingStep = recipeRepository.findStepByIdAndRecipeId(stepId, recipeId)
            ?: throw RecipeStepNotFoundException()

        val updatedStep = recipeRepository.updateStep(
            id = stepId,
            description = request.description?.trim(),
            durationMinutes = request.durationMinutes
        ) ?: throw RecipeStepNotFoundException()

        // Update total duration
        val totalDuration = recipeRepository.getTotalStepDuration(recipeId)
        recipeRepository.updateTotalDuration(recipeId, totalDuration)

        return toRecipeStepResponse(updatedStep)
    }

    @Transactional
    fun deleteStep(recipeId: UUID, stepId: Int, profileId: UUID) {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val step = recipeRepository.findStepByIdAndRecipeId(stepId, recipeId)
            ?: throw RecipeStepNotFoundException()

        recipeRepository.deleteStep(stepId)
        recipeRepository.renumberStepsAfterDelete(recipeId, step.stepNumber)

        // Update total duration
        val totalDuration = recipeRepository.getTotalStepDuration(recipeId)
        recipeRepository.updateTotalDuration(recipeId, totalDuration)
    }

    @Transactional
    fun reorderSteps(recipeId: UUID, request: ReorderStepsRequest, profileId: UUID): ReorderStepsResponse {
        val recipe = recipeRepository.findById(recipeId)
            ?: throw RecipeNotFoundException()

        if (recipe.profileId != profileId) {
            throw RecipeNotOwnedException()
        }

        val existingSteps = recipeRepository.findStepsByRecipeId(recipeId)
        val existingStepIds = existingSteps.map { it.id }.toSet()

        // Validate that provided step IDs match existing steps
        if (request.stepIds.toSet() != existingStepIds) {
            throw InvalidStepOrderException()
        }

        // Update step numbers according to new order
        val updatedSteps = request.stepIds.mapIndexed { index, stepId ->
            recipeRepository.updateStepNumber(stepId, index + 1)!!
        }

        return ReorderStepsResponse(steps = updatedSteps.map { toRecipeStepResponse(it) })
    }

    // ========== Nutrition Calculation ==========

    private fun updateRecipeNutrition(recipeId: UUID, totalServings: Int) {
        val ingredients = recipeRepository.findIngredientsByRecipeId(recipeId)

        var totalCalories = BigDecimal.ZERO
        var totalFat = BigDecimal.ZERO
        var totalCarbs = BigDecimal.ZERO
        var totalProtein = BigDecimal.ZERO
        var totalSalt = BigDecimal.ZERO
        var totalSugar = BigDecimal.ZERO
        var totalFiber = BigDecimal.ZERO
        var totalSaturatedFat = BigDecimal.ZERO

        for (ingredient in ingredients) {
            val food = foodRepository.findById(ingredient.foodId) ?: continue

            totalCalories = totalCalories.add(calculateNutritionValue(food.caloriesPer100, ingredient.amountGrams))
            totalFat = totalFat.add(calculateNutritionValue(food.fatPer100, ingredient.amountGrams))
            totalCarbs = totalCarbs.add(calculateNutritionValue(food.carbsPer100, ingredient.amountGrams))
            totalProtein = totalProtein.add(calculateNutritionValue(food.proteinPer100, ingredient.amountGrams))
            food.saltPer100?.let { totalSalt = totalSalt.add(calculateNutritionValue(it, ingredient.amountGrams)) }
            food.sugarPer100?.let { totalSugar = totalSugar.add(calculateNutritionValue(it, ingredient.amountGrams)) }
            food.fiberPer100?.let { totalFiber = totalFiber.add(calculateNutritionValue(it, ingredient.amountGrams)) }
            food.saturatedFatPer100?.let { totalSaturatedFat = totalSaturatedFat.add(calculateNutritionValue(it, ingredient.amountGrams)) }
        }

        val servings = BigDecimal(totalServings)

        recipeRepository.updateNutrition(
            id = recipeId,
            caloriesPerServing = totalCalories.divide(servings, 2, RoundingMode.HALF_UP),
            fatPerServing = totalFat.divide(servings, 2, RoundingMode.HALF_UP),
            carbsPerServing = totalCarbs.divide(servings, 2, RoundingMode.HALF_UP),
            proteinPerServing = totalProtein.divide(servings, 2, RoundingMode.HALF_UP),
            saltPerServing = if (totalSalt > BigDecimal.ZERO) totalSalt.divide(servings, 3, RoundingMode.HALF_UP) else null,
            sugarPerServing = if (totalSugar > BigDecimal.ZERO) totalSugar.divide(servings, 2, RoundingMode.HALF_UP) else null,
            fiberPerServing = if (totalFiber > BigDecimal.ZERO) totalFiber.divide(servings, 2, RoundingMode.HALF_UP) else null,
            saturatedFatPerServing = if (totalSaturatedFat > BigDecimal.ZERO) totalSaturatedFat.divide(servings, 2, RoundingMode.HALF_UP) else null
        )
    }

    private fun calculateNutritionValue(per100Value: BigDecimal, amountGrams: BigDecimal): BigDecimal {
        return per100Value.multiply(amountGrams).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }

    private fun calculateAmountGrams(portion: FoodPortion?, quantity: BigDecimal): BigDecimal {
        return if (portion != null) {
            portion.amountGrams.multiply(quantity)
        } else {
            quantity // Treat quantity as grams directly
        }
    }

    // ========== Response Mappers ==========

    private fun toRecipeListItem(
        recipe: Recipe,
        ingredientCount: Int,
        thumbnailUrl: String?,
        lastUsedAt: java.time.Instant?
    ): RecipeListItem {
        return RecipeListItem(
            id = recipe.id,
            name = recipe.name,
            description = recipe.description,
            totalServings = recipe.totalServings,
            totalDurationMinutes = recipe.totalDurationMinutes,
            nutritionPerServing = recipe.caloriesPerServing?.let {
                NutritionSummary(
                    calories = recipe.caloriesPerServing,
                    fat = recipe.fatPerServing ?: BigDecimal.ZERO,
                    carbs = recipe.carbsPerServing ?: BigDecimal.ZERO,
                    protein = recipe.proteinPerServing ?: BigDecimal.ZERO,
                    salt = recipe.saltPerServing,
                    sugar = recipe.sugarPerServing,
                    fiber = recipe.fiberPerServing,
                    saturatedFat = recipe.saturatedFatPerServing
                )
            },
            thumbnailUrl = thumbnailUrl,
            ingredientCount = ingredientCount,
            lastUsedAt = lastUsedAt
        )
    }

    private fun toRecipeDetailResponse(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
        steps: List<RecipeStep>
    ): RecipeDetailResponse {
        val ingredientResponses = ingredients.map { ingredient ->
            val food = foodRepository.findById(ingredient.foodId)
            val portion = ingredient.portionId?.let { foodRepository.findPortionById(it) }

            toRecipeIngredientDetailResponse(ingredient, food, portion)
        }

        val stepResponses = steps.map { toRecipeStepResponse(it) }

        // Calculate total nutrition
        val totalNutrition = if (recipe.caloriesPerServing != null) {
            NutritionSummary(
                calories = recipe.caloriesPerServing.multiply(BigDecimal(recipe.totalServings)),
                fat = (recipe.fatPerServing ?: BigDecimal.ZERO).multiply(BigDecimal(recipe.totalServings)),
                carbs = (recipe.carbsPerServing ?: BigDecimal.ZERO).multiply(BigDecimal(recipe.totalServings)),
                protein = (recipe.proteinPerServing ?: BigDecimal.ZERO).multiply(BigDecimal(recipe.totalServings)),
                salt = recipe.saltPerServing?.multiply(BigDecimal(recipe.totalServings)),
                sugar = recipe.sugarPerServing?.multiply(BigDecimal(recipe.totalServings)),
                fiber = recipe.fiberPerServing?.multiply(BigDecimal(recipe.totalServings)),
                saturatedFat = recipe.saturatedFatPerServing?.multiply(BigDecimal(recipe.totalServings))
            )
        } else null

        return RecipeDetailResponse(
            id = recipe.id,
            name = recipe.name,
            description = recipe.description,
            totalServings = recipe.totalServings,
            totalDurationMinutes = recipe.totalDurationMinutes,
            ingredients = ingredientResponses,
            steps = stepResponses,
            nutritionPerServing = recipe.caloriesPerServing?.let {
                NutritionSummary(
                    calories = recipe.caloriesPerServing,
                    fat = recipe.fatPerServing ?: BigDecimal.ZERO,
                    carbs = recipe.carbsPerServing ?: BigDecimal.ZERO,
                    protein = recipe.proteinPerServing ?: BigDecimal.ZERO,
                    salt = recipe.saltPerServing,
                    sugar = recipe.sugarPerServing,
                    fiber = recipe.fiberPerServing,
                    saturatedFat = recipe.saturatedFatPerServing
                )
            },
            nutritionTotal = totalNutrition,
            createdAt = recipe.createdAt,
            updatedAt = recipe.updatedAt
        )
    }

    private fun toRecipeIngredientResponse(
        ingredient: RecipeIngredient,
        food: Food,
        portion: FoodPortion?
    ): RecipeIngredientResponse {
        val nutrition = calculateIngredientNutrition(food, ingredient.amountGrams)

        return RecipeIngredientResponse(
            id = ingredient.id,
            food = FoodSummary(
                id = food.id,
                name = food.name,
                metricType = food.metricType
            ),
            portion = portion?.let {
                PortionResponse(
                    id = it.id,
                    name = it.name,
                    amountGrams = it.amountGrams
                )
            },
            quantity = ingredient.quantity,
            amountGrams = ingredient.amountGrams,
            nutrition = nutrition
        )
    }

    private fun toRecipeIngredientDetailResponse(
        ingredient: RecipeIngredient,
        food: Food?,
        portion: FoodPortion?
    ): RecipeIngredientDetailResponse {
        val nutrition = food?.let { calculateIngredientNutrition(it, ingredient.amountGrams) }
            ?: NutritionSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

        val foodNutritionPer100 = food?.let {
            NutritionSummary(
                calories = it.caloriesPer100,
                fat = it.fatPer100,
                carbs = it.carbsPer100,
                protein = it.proteinPer100,
                salt = it.saltPer100,
                sugar = it.sugarPer100,
                fiber = it.fiberPer100,
                saturatedFat = it.saturatedFatPer100
            )
        }

        return RecipeIngredientDetailResponse(
            id = ingredient.id,
            food = FoodDetailSummary(
                id = ingredient.foodId,
                name = food?.name ?: "Unknown Food",
                metricType = food?.metricType ?: com.fittrack.model.MetricType.GRAMS,
                nutritionPer100 = foodNutritionPer100
            ),
            portion = portion?.let {
                PortionResponse(
                    id = it.id,
                    name = it.name,
                    amountGrams = it.amountGrams
                )
            },
            quantity = ingredient.quantity,
            amountGrams = ingredient.amountGrams,
            nutrition = nutrition
        )
    }

    private fun calculateIngredientNutrition(food: Food, amountGrams: BigDecimal): NutritionSummary {
        return NutritionSummary(
            calories = calculateNutritionValue(food.caloriesPer100, amountGrams),
            fat = calculateNutritionValue(food.fatPer100, amountGrams),
            carbs = calculateNutritionValue(food.carbsPer100, amountGrams),
            protein = calculateNutritionValue(food.proteinPer100, amountGrams),
            salt = food.saltPer100?.let { calculateNutritionValue(it, amountGrams) },
            sugar = food.sugarPer100?.let { calculateNutritionValue(it, amountGrams) },
            fiber = food.fiberPer100?.let { calculateNutritionValue(it, amountGrams) },
            saturatedFat = food.saturatedFatPer100?.let { calculateNutritionValue(it, amountGrams) }
        )
    }

    private fun toRecipeStepResponse(step: RecipeStep): RecipeStepResponse {
        return RecipeStepResponse(
            id = step.id,
            stepNumber = step.stepNumber,
            description = step.description,
            durationMinutes = step.durationMinutes
        )
    }
}
