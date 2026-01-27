package com.fittrack.service

import com.fittrack.exception.FoodNotFoundException
import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.exception.InvalidItemTypeException
import com.fittrack.exception.InvalidQuantityException
import com.fittrack.exception.MealItemNotFoundException
import com.fittrack.exception.MealNotFoundException
import com.fittrack.exception.MealNotOwnedException
import com.fittrack.exception.PortionNotFoundException
import com.fittrack.exception.RecipeNotFoundException
import com.fittrack.model.Food
import com.fittrack.model.FoodPortion
import com.fittrack.model.Meal
import com.fittrack.model.MealItem
import com.fittrack.model.MealType
import com.fittrack.model.dto.food.PortionResponse
import com.fittrack.model.dto.meal.AddMealItemRequest
import com.fittrack.model.dto.meal.CopyMealRequest
import com.fittrack.model.dto.meal.CreateMealRequest
import com.fittrack.model.dto.meal.FoodSummary
import com.fittrack.model.dto.meal.MealItemNutrition
import com.fittrack.model.dto.meal.MealItemResponse
import com.fittrack.model.dto.meal.MealItemType
import com.fittrack.model.dto.meal.MealListResponse
import com.fittrack.model.dto.meal.MealListSummary
import com.fittrack.model.dto.meal.MealResponse
import com.fittrack.model.dto.meal.MealSummaryResponse
import com.fittrack.model.dto.meal.MealTotals
import com.fittrack.model.dto.meal.MealTypeResponse
import com.fittrack.model.dto.meal.MealTypesResponse
import com.fittrack.model.dto.meal.QuickAddFoodRequest
import com.fittrack.model.dto.meal.RecipeSummary
import com.fittrack.model.dto.meal.UpdateMealItemRequest
import com.fittrack.model.dto.meal.UpdateMealRequest
import com.fittrack.repository.DayRepository
import com.fittrack.repository.FoodRepository
import com.fittrack.repository.MealRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class MealService(
    private val mealRepository: MealRepository,
    private val dayRepository: DayRepository,
    private val foodRepository: FoodRepository,
    private val nutritionCalculator: NutritionCalculatorService
) {

    companion object {
        const val MAX_DATE_RANGE_DAYS = 90
    }

    // ========== Meal Operations ==========

    /**
     * Create a new meal for a day.
     * Day is created automatically if it doesn't exist.
     */
    @Transactional
    fun createMeal(profileId: UUID, request: CreateMealRequest): MealResponse {
        // Find or create day
        val day = dayRepository.findOrCreate(profileId, request.date)

        // Create meal
        val meal = mealRepository.create(
            dayId = day.id,
            mealType = request.mealType,
            isCheatMeal = request.isCheatMeal
        )

        return toMealResponse(meal, request.date, emptyList())
    }

    /**
     * Get meal details by ID.
     */
    fun getMeal(mealId: UUID, profileId: UUID): MealResponse {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        val mealDate = mealRepository.getMealDate(mealId)
            ?: throw MealNotFoundException()

        val items = mealRepository.findItemsByMealId(mealId)
        val itemResponses = items.map { toMealItemResponse(it) }

        return toMealResponse(meal, mealDate, itemResponses)
    }

    /**
     * Update meal properties (mealType, isCheatMeal).
     */
    @Transactional
    fun updateMeal(mealId: UUID, profileId: UUID, request: UpdateMealRequest): MealResponse {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        val updatedMeal = mealRepository.update(
            id = mealId,
            mealType = request.mealType,
            isCheatMeal = request.isCheatMeal
        ) ?: throw MealNotFoundException()

        val mealDate = mealRepository.getMealDate(mealId)
            ?: throw MealNotFoundException()

        val items = mealRepository.findItemsByMealId(mealId)
        val itemResponses = items.map { toMealItemResponse(it) }

        return toMealResponse(updatedMeal, mealDate, itemResponses)
    }

    /**
     * Delete a meal and all its items.
     */
    @Transactional
    fun deleteMeal(mealId: UUID, profileId: UUID) {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        mealRepository.delete(mealId)
    }

    /**
     * Copy a meal to another day.
     */
    @Transactional
    fun copyMeal(mealId: UUID, profileId: UUID, request: CopyMealRequest): MealResponse {
        val sourceMeal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        // Find or create target day
        val targetDay = dayRepository.findOrCreate(profileId, request.targetDate)

        // Create new meal (cheat meal flag is NOT copied)
        val newMeal = mealRepository.create(
            dayId = targetDay.id,
            mealType = request.targetMealType ?: sourceMeal.mealType,
            isCheatMeal = false
        )

        // Copy all items
        val sourceItems = mealRepository.findItemsByMealId(mealId)
        val newItems = sourceItems.map { item ->
            mealRepository.createItem(
                mealId = newMeal.id,
                foodId = item.foodId,
                recipeId = item.recipeId,
                portionId = item.portionId,
                quantity = item.quantity,
                amountGrams = item.amountGrams,
                quickEntryName = item.quickEntryName,
                isQuickEntry = item.isQuickEntry,
                calories = item.calories,
                fat = item.fat,
                carbs = item.carbs,
                protein = item.protein,
                salt = item.salt,
                sugar = item.sugar,
                fiber = item.fiber,
                saturatedFat = item.saturatedFat,
                displayOrder = item.displayOrder
            )
        }

        // Update meal totals
        val updatedMeal = mealRepository.updateTotals(newMeal.id)
            ?: throw MealNotFoundException()

        val itemResponses = newItems.map { toMealItemResponse(it) }
        return toMealResponse(updatedMeal, request.targetDate, itemResponses)
    }

    /**
     * List meals for a date range.
     */
    fun getMeals(
        profileId: UUID,
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate = LocalDate.now(),
        mealType: MealType? = null
    ): MealListResponse {
        // Validate date range
        val daysBetween = ChronoUnit.DAYS.between(startDate, endDate)
        if (daysBetween > MAX_DATE_RANGE_DAYS || endDate.isBefore(startDate)) {
            throw InvalidDateRangeException()
        }

        var meals = mealRepository.findByProfileIdAndDateRange(profileId, startDate, endDate)

        // Filter by meal type if specified
        if (mealType != null) {
            meals = meals.filter { it.mealType == mealType }
        }

        val mealSummaries = meals.map { meal ->
            val mealDate = mealRepository.getMealDate(meal.id)
                ?: throw MealNotFoundException()
            val itemCount = mealRepository.countItemsByMealId(meal.id)

            MealSummaryResponse(
                id = meal.id,
                date = mealDate,
                mealType = meal.mealType,
                isCheatMeal = meal.isCheatMeal,
                itemCount = itemCount,
                totals = MealTotals(
                    calories = meal.totalCalories,
                    fat = meal.totalFat,
                    carbs = meal.totalCarbs,
                    protein = meal.totalProtein
                )
            )
        }

        val totalCalories = mealSummaries.sumOf { it.totals.calories }
        val cheatMealCount = mealSummaries.count { it.isCheatMeal }

        return MealListResponse(
            meals = mealSummaries,
            summary = MealListSummary(
                totalMeals = mealSummaries.size,
                totalCalories = totalCalories,
                cheatMealCount = cheatMealCount
            )
        )
    }

    // ========== Meal Item Operations ==========

    /**
     * Add an item to a meal.
     */
    @Transactional
    fun addMealItem(mealId: UUID, profileId: UUID, request: AddMealItemRequest): MealItemResponse {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        val item = when (request.type) {
            MealItemType.FOOD -> createFoodItem(mealId, profileId, request)
            MealItemType.RECIPE -> createRecipeItem(mealId, profileId, request)
            MealItemType.QUICK_ENTRY -> createQuickEntryItem(mealId, request)
        }

        // Update meal totals
        mealRepository.updateTotals(mealId)

        // Update recent foods if it's a food item
        if (request.type == MealItemType.FOOD && request.foodId != null) {
            foodRepository.updateRecentFood(profileId, request.foodId)
        }

        return toMealItemResponse(item)
    }

    /**
     * Quick add a food with default portion.
     */
    @Transactional
    fun quickAddFood(mealId: UUID, profileId: UUID, request: QuickAddFoodRequest): MealItemResponse {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        // Get food
        val food = foodRepository.findById(request.foodId)
            ?: throw FoodNotFoundException()

        // Get default portion (most recently used or first available)
        val portions = foodRepository.findPortionsByFoodId(request.foodId)
        val defaultPortion = portions.firstOrNull()

        // Calculate amount and nutrition
        val amountGrams = if (defaultPortion != null) {
            defaultPortion.amountGrams.multiply(request.quantity)
        } else {
            request.quantity.multiply(BigDecimal(100)) // Use 100g as default
        }

        val nutrition = calculateFoodNutrition(food, amountGrams)

        val item = mealRepository.createItem(
            mealId = mealId,
            foodId = request.foodId,
            portionId = defaultPortion?.id,
            quantity = request.quantity,
            amountGrams = amountGrams,
            isQuickEntry = false,
            calories = nutrition.calories,
            fat = nutrition.fat,
            carbs = nutrition.carbs,
            protein = nutrition.protein,
            salt = nutrition.salt,
            sugar = nutrition.sugar,
            fiber = nutrition.fiber,
            saturatedFat = nutrition.saturatedFat
        )

        // Update meal totals
        mealRepository.updateTotals(mealId)

        // Update recent foods
        foodRepository.updateRecentFood(profileId, request.foodId)

        return toMealItemResponse(item)
    }

    /**
     * Update a meal item.
     */
    @Transactional
    fun updateMealItem(
        mealId: UUID,
        itemId: UUID,
        profileId: UUID,
        request: UpdateMealItemRequest
    ): MealItemResponse {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        val item = mealRepository.findItemById(itemId)
            ?: throw MealItemNotFoundException()

        if (item.mealId != mealId) {
            throw MealItemNotFoundException()
        }

        val updatedItem = when {
            item.foodId != null -> updateFoodItem(item, request)
            item.recipeId != null -> updateRecipeItem(item, request)
            item.isQuickEntry -> updateQuickEntryItem(item, request)
            else -> throw InvalidItemTypeException()
        }

        // Update meal totals
        mealRepository.updateTotals(mealId)

        return toMealItemResponse(updatedItem)
    }

    /**
     * Delete a meal item.
     */
    @Transactional
    fun deleteMealItem(mealId: UUID, itemId: UUID, profileId: UUID) {
        val meal = mealRepository.findById(mealId)
            ?: throw MealNotFoundException()

        // Check ownership
        val mealProfileId = mealRepository.getMealProfileId(mealId)
        if (mealProfileId != profileId) {
            throw MealNotOwnedException()
        }

        val item = mealRepository.findItemById(itemId)
            ?: throw MealItemNotFoundException()

        if (item.mealId != mealId) {
            throw MealItemNotFoundException()
        }

        mealRepository.deleteItem(itemId)

        // Update meal totals
        mealRepository.updateTotals(mealId)
    }

    // ========== Meal Types ==========

    /**
     * Get list of meal types.
     */
    fun getMealTypes(): MealTypesResponse {
        val types = MealType.entries.mapIndexed { index, type ->
            MealTypeResponse(
                type = type,
                name = type.name.lowercase().replaceFirstChar { it.uppercase() },
                displayOrder = index
            )
        }
        return MealTypesResponse(mealTypes = types)
    }

    // ========== Private Helper Methods ==========

    private fun createFoodItem(mealId: UUID, profileId: UUID, request: AddMealItemRequest): MealItem {
        val foodId = request.foodId
            ?: throw InvalidItemTypeException("Food ID is required for FOOD type")

        val food = foodRepository.findById(foodId)
            ?: throw FoodNotFoundException()

        // Get portion if specified
        val portion = request.portionId?.let { portionId ->
            foodRepository.findPortionByIdAndFoodId(portionId, foodId)
                ?: throw PortionNotFoundException()
        }

        val quantity = request.quantity ?: BigDecimal.ONE
        if (quantity <= BigDecimal.ZERO) {
            throw InvalidQuantityException()
        }

        // Calculate amount in grams
        val amountGrams = if (portion != null) {
            portion.amountGrams.multiply(quantity)
        } else {
            quantity // Treat as grams if no portion
        }

        val nutrition = calculateFoodNutrition(food, amountGrams)

        return mealRepository.createItem(
            mealId = mealId,
            foodId = foodId,
            portionId = request.portionId,
            quantity = quantity,
            amountGrams = amountGrams,
            isQuickEntry = false,
            calories = nutrition.calories,
            fat = nutrition.fat,
            carbs = nutrition.carbs,
            protein = nutrition.protein,
            salt = nutrition.salt,
            sugar = nutrition.sugar,
            fiber = nutrition.fiber,
            saturatedFat = nutrition.saturatedFat
        )
    }

    private fun createRecipeItem(mealId: UUID, profileId: UUID, request: AddMealItemRequest): MealItem {
        val recipeId = request.recipeId
            ?: throw InvalidItemTypeException("Recipe ID is required for RECIPE type")

        // TODO: Implement recipe lookup when RecipeRepository is available
        // For now, throw exception
        throw RecipeNotFoundException()
    }

    private fun createQuickEntryItem(mealId: UUID, request: AddMealItemRequest): MealItem {
        val name = request.name
            ?: throw InvalidItemTypeException("Name is required for QUICK_ENTRY type")

        val nutrition = request.nutrition
            ?: throw InvalidItemTypeException("Nutrition is required for QUICK_ENTRY type")

        return mealRepository.createItem(
            mealId = mealId,
            quickEntryName = name,
            isQuickEntry = true,
            calories = nutrition.calories,
            fat = nutrition.fat,
            carbs = nutrition.carbs,
            protein = nutrition.protein
        )
    }

    private fun updateFoodItem(item: MealItem, request: UpdateMealItemRequest): MealItem {
        val foodId = item.foodId!!
        val food = foodRepository.findById(foodId)
            ?: throw FoodNotFoundException()

        // Get new portion if specified
        val newPortionId = request.portionId ?: item.portionId
        val portion = newPortionId?.let { portionId ->
            foodRepository.findPortionByIdAndFoodId(portionId, foodId)
                ?: throw PortionNotFoundException()
        }

        val quantity = request.quantity ?: item.quantity
        if (quantity <= BigDecimal.ZERO) {
            throw InvalidQuantityException()
        }

        // Recalculate amount in grams
        val amountGrams = if (portion != null) {
            portion.amountGrams.multiply(quantity)
        } else {
            quantity
        }

        val nutrition = calculateFoodNutrition(food, amountGrams)

        return mealRepository.updateItem(
            id = item.id,
            portionId = newPortionId,
            quantity = quantity,
            amountGrams = amountGrams,
            calories = nutrition.calories,
            fat = nutrition.fat,
            carbs = nutrition.carbs,
            protein = nutrition.protein,
            salt = nutrition.salt,
            sugar = nutrition.sugar,
            fiber = nutrition.fiber,
            saturatedFat = nutrition.saturatedFat
        ) ?: throw MealItemNotFoundException()
    }

    private fun updateRecipeItem(item: MealItem, request: UpdateMealItemRequest): MealItem {
        // TODO: Implement recipe item update when RecipeRepository is available
        throw RecipeNotFoundException()
    }

    private fun updateQuickEntryItem(item: MealItem, request: UpdateMealItemRequest): MealItem {
        val name = request.name ?: item.quickEntryName
        val nutrition = request.nutrition

        return mealRepository.updateItem(
            id = item.id,
            quickEntryName = name,
            calories = nutrition?.calories ?: item.calories,
            fat = nutrition?.fat ?: item.fat,
            carbs = nutrition?.carbs ?: item.carbs,
            protein = nutrition?.protein ?: item.protein
        ) ?: throw MealItemNotFoundException()
    }

    private fun calculateFoodNutrition(food: Food, amountGrams: BigDecimal): FoodNutritionValues {
        return FoodNutritionValues(
            calories = nutritionCalculator.calculateNutritionValue(food.caloriesPer100, amountGrams),
            fat = nutritionCalculator.calculateNutritionValue(food.fatPer100, amountGrams),
            carbs = nutritionCalculator.calculateNutritionValue(food.carbsPer100, amountGrams),
            protein = nutritionCalculator.calculateNutritionValue(food.proteinPer100, amountGrams),
            salt = food.saltPer100?.let { nutritionCalculator.calculateNutritionValue(it, amountGrams) },
            sugar = food.sugarPer100?.let { nutritionCalculator.calculateNutritionValue(it, amountGrams) },
            fiber = food.fiberPer100?.let { nutritionCalculator.calculateNutritionValue(it, amountGrams) },
            saturatedFat = food.saturatedFatPer100?.let { nutritionCalculator.calculateNutritionValue(it, amountGrams) }
        )
    }

    private fun toMealResponse(meal: Meal, date: LocalDate, items: List<MealItemResponse>): MealResponse {
        return MealResponse(
            id = meal.id,
            dayId = meal.dayId,
            date = date,
            mealType = meal.mealType,
            isCheatMeal = meal.isCheatMeal,
            items = items,
            totals = MealTotals(
                calories = meal.totalCalories,
                fat = meal.totalFat,
                carbs = meal.totalCarbs,
                protein = meal.totalProtein
            ),
            createdAt = meal.createdAt,
            updatedAt = meal.updatedAt
        )
    }

    private fun toMealItemResponse(item: MealItem): MealItemResponse {
        val type = when {
            item.isQuickEntry -> MealItemType.QUICK_ENTRY
            item.recipeId != null -> MealItemType.RECIPE
            else -> MealItemType.FOOD
        }

        val food = item.foodId?.let { foodId ->
            val f = foodRepository.findById(foodId)
            f?.let {
                FoodSummary(
                    id = it.id,
                    name = it.name,
                    metricType = it.metricType.name
                )
            }
        }

        val portion = item.portionId?.let { portionId ->
            item.foodId?.let { foodId ->
                val p = foodRepository.findPortionByIdAndFoodId(portionId, foodId)
                p?.let {
                    PortionResponse(
                        id = it.id,
                        name = it.name,
                        amountGrams = it.amountGrams
                    )
                }
            }
        }

        // TODO: Add recipe lookup when RecipeRepository is available
        val recipe: RecipeSummary? = null

        return MealItemResponse(
            id = item.id,
            type = type,
            food = food,
            recipe = recipe,
            portion = portion,
            quantity = if (type == MealItemType.FOOD) item.quantity else null,
            servings = if (type == MealItemType.RECIPE) item.quantity else null,
            amountGrams = item.amountGrams,
            name = if (type == MealItemType.QUICK_ENTRY) item.quickEntryName else null,
            nutrition = MealItemNutrition(
                calories = item.calories,
                fat = item.fat,
                carbs = item.carbs,
                protein = item.protein,
                salt = item.salt,
                sugar = item.sugar,
                fiber = item.fiber,
                saturatedFat = item.saturatedFat
            ),
            createdAt = item.createdAt
        )
    }
}

private data class FoodNutritionValues(
    val calories: BigDecimal,
    val fat: BigDecimal,
    val carbs: BigDecimal,
    val protein: BigDecimal,
    val salt: BigDecimal?,
    val sugar: BigDecimal?,
    val fiber: BigDecimal?,
    val saturatedFat: BigDecimal?
)
