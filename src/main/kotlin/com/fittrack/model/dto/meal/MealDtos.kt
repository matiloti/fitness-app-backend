package com.fittrack.model.dto.meal

import com.fittrack.model.MealType
import com.fittrack.model.dto.food.NutritionInfo
import com.fittrack.model.dto.food.PortionResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ========== Meal Item Types ==========

enum class MealItemType {
    FOOD,
    RECIPE,
    QUICK_ENTRY
}

// ========== Nested Response Objects ==========

data class MealItemNutrition(
    val calories: BigDecimal,
    val fat: BigDecimal?,
    val carbs: BigDecimal?,
    val protein: BigDecimal?,
    val salt: BigDecimal? = null,
    val sugar: BigDecimal? = null,
    val fiber: BigDecimal? = null,
    val saturatedFat: BigDecimal? = null
)

data class FoodSummary(
    val id: UUID,
    val name: String,
    val metricType: String? = null
)

data class RecipeSummary(
    val id: UUID,
    val name: String,
    val totalServings: Int
)

data class MealTotals(
    val calories: BigDecimal,
    val fat: BigDecimal,
    val carbs: BigDecimal,
    val protein: BigDecimal
)

// ========== Meal Item Response ==========

data class MealItemResponse(
    val id: UUID,
    val type: MealItemType,
    val food: FoodSummary? = null,
    val recipe: RecipeSummary? = null,
    val portion: PortionResponse? = null,
    val quantity: BigDecimal? = null,
    val servings: BigDecimal? = null,
    val amountGrams: BigDecimal? = null,
    val name: String? = null, // For quick entries
    val nutrition: MealItemNutrition,
    val createdAt: Instant
)

// ========== Meal Responses ==========

data class MealResponse(
    val id: UUID,
    val dayId: UUID,
    val date: LocalDate,
    val mealType: MealType,
    val isCheatMeal: Boolean,
    val items: List<MealItemResponse>,
    val totals: MealTotals,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class MealSummaryResponse(
    val id: UUID,
    val date: LocalDate,
    val mealType: MealType,
    val isCheatMeal: Boolean,
    val itemCount: Int,
    val totals: MealTotals
)

data class MealListSummary(
    val totalMeals: Int,
    val totalCalories: BigDecimal,
    val cheatMealCount: Int
)

data class MealListResponse(
    val meals: List<MealSummaryResponse>,
    val summary: MealListSummary
)

// ========== Meal Requests ==========

data class CreateMealRequest(
    @field:NotNull(message = "Date is required")
    val date: LocalDate,

    @field:NotNull(message = "Meal type is required")
    val mealType: MealType,

    val isCheatMeal: Boolean = false
)

data class UpdateMealRequest(
    val mealType: MealType? = null,
    val isCheatMeal: Boolean? = null
)

data class CopyMealRequest(
    @field:NotNull(message = "Target date is required")
    val targetDate: LocalDate,

    val targetMealType: MealType? = null
)

// ========== Meal Item Requests ==========

data class QuickEntryNutrition(
    @field:NotNull(message = "Calories is required")
    @field:DecimalMin(value = "0", message = "Calories must be >= 0")
    val calories: BigDecimal,

    @field:DecimalMin(value = "0", message = "Fat must be >= 0")
    val fat: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Carbs must be >= 0")
    val carbs: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Protein must be >= 0")
    val protein: BigDecimal? = null
)

data class AddMealItemRequest(
    @field:NotNull(message = "Item type is required")
    val type: MealItemType,

    // For FOOD type
    val foodId: UUID? = null,
    val portionId: Int? = null,

    @field:DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
    val quantity: BigDecimal? = null,

    // For RECIPE type
    val recipeId: UUID? = null,

    @field:DecimalMin(value = "0.01", message = "Servings must be greater than 0")
    val servings: BigDecimal? = null,

    // For QUICK_ENTRY type
    @field:Size(max = 200, message = "Name cannot exceed 200 characters")
    val name: String? = null,

    @field:Valid
    val nutrition: QuickEntryNutrition? = null
)

data class UpdateMealItemRequest(
    // For FOOD items
    val portionId: Int? = null,

    @field:DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
    val quantity: BigDecimal? = null,

    // For RECIPE items
    @field:DecimalMin(value = "0.01", message = "Servings must be greater than 0")
    val servings: BigDecimal? = null,

    // For QUICK_ENTRY items
    @field:Size(max = 200, message = "Name cannot exceed 200 characters")
    val name: String? = null,

    @field:Valid
    val nutrition: QuickEntryNutrition? = null
)

data class QuickAddFoodRequest(
    @field:NotNull(message = "Food ID is required")
    val foodId: UUID,

    @field:DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
    val quantity: BigDecimal = BigDecimal.ONE
)

// ========== Meal Type Response ==========

data class MealTypeResponse(
    val type: MealType,
    val name: String,
    val displayOrder: Int
)

data class MealTypesResponse(
    val mealTypes: List<MealTypeResponse>
)
