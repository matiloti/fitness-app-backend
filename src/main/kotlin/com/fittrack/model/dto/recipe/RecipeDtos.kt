package com.fittrack.model.dto.recipe

import com.fittrack.model.MetricType
import com.fittrack.model.dto.food.PageInfo
import com.fittrack.model.dto.food.PortionResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// ========== Nutrition Summary ==========

data class NutritionSummary(
    val calories: BigDecimal,
    val fat: BigDecimal,
    val carbs: BigDecimal,
    val protein: BigDecimal,
    val salt: BigDecimal? = null,
    val sugar: BigDecimal? = null,
    val fiber: BigDecimal? = null,
    val saturatedFat: BigDecimal? = null
)

// ========== Food Summary for Ingredients ==========

data class FoodSummary(
    val id: UUID,
    val name: String,
    val metricType: MetricType
)

data class FoodDetailSummary(
    val id: UUID,
    val name: String,
    val metricType: MetricType,
    val nutritionPer100: NutritionSummary? = null
)

// ========== Recipe Ingredient Responses ==========

data class RecipeIngredientResponse(
    val id: Int,
    val food: FoodSummary,
    val portion: PortionResponse?,
    val quantity: BigDecimal,
    val amountGrams: BigDecimal,
    val nutrition: NutritionSummary
)

data class RecipeIngredientDetailResponse(
    val id: Int,
    val food: FoodDetailSummary,
    val portion: PortionResponse?,
    val quantity: BigDecimal,
    val amountGrams: BigDecimal,
    val nutrition: NutritionSummary
)

// ========== Recipe Step Responses ==========

data class RecipeStepResponse(
    val id: Int,
    val stepNumber: Int,
    val description: String,
    val durationMinutes: Int?
)

// ========== Recipe List Item (for pagination) ==========

data class RecipeListItem(
    val id: UUID,
    val name: String,
    val description: String?,
    val totalServings: Int,
    val totalDurationMinutes: Int?,
    val nutritionPerServing: NutritionSummary?,
    val thumbnailUrl: String?,
    val ingredientCount: Int,
    val lastUsedAt: Instant?
)

// ========== Recipe List Response ==========

data class RecipeListResponse(
    val content: List<RecipeListItem>,
    val page: PageInfo
)

// ========== Recipe Detail Response ==========

data class RecipeDetailResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val totalServings: Int,
    val totalDurationMinutes: Int?,
    val ingredients: List<RecipeIngredientDetailResponse>,
    val steps: List<RecipeStepResponse>,
    val nutritionPerServing: NutritionSummary?,
    val nutritionTotal: NutritionSummary?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// ========== Create Recipe Request ==========

data class CreateIngredientRequest(
    @field:NotNull(message = "Food ID is required")
    val foodId: UUID,

    val portionId: Int? = null,

    @field:NotNull(message = "Quantity is required")
    @field:DecimalMin(value = "0.01", inclusive = true, message = "Quantity must be greater than 0")
    val quantity: BigDecimal
)

data class CreateStepRequest(
    @field:NotBlank(message = "Step description is required")
    @field:Size(max = 2000, message = "Step description cannot exceed 2000 characters")
    val description: String,

    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int? = null
)

data class CreateRecipeRequest(
    @field:NotBlank(message = "Recipe name is required")
    @field:Size(max = 200, message = "Recipe name cannot exceed 200 characters")
    val name: String,

    @field:Size(max = 2000, message = "Description cannot exceed 2000 characters")
    val description: String? = null,

    @field:NotNull(message = "Total servings is required")
    @field:Min(value = 1, message = "Total servings must be at least 1")
    val totalServings: Int,

    @field:NotEmpty(message = "At least one ingredient is required")
    @field:Valid
    val ingredients: List<CreateIngredientRequest>,

    @field:Valid
    val steps: List<CreateStepRequest> = emptyList()
)

// ========== Update Recipe Request ==========

data class UpdateRecipeRequest(
    @field:Size(max = 200, message = "Recipe name cannot exceed 200 characters")
    val name: String? = null,

    @field:Size(max = 2000, message = "Description cannot exceed 2000 characters")
    val description: String? = null,

    @field:Min(value = 1, message = "Total servings must be at least 1")
    val totalServings: Int? = null
)

// ========== Add Ingredient Request ==========

data class AddIngredientRequest(
    @field:NotNull(message = "Food ID is required")
    val foodId: UUID,

    val portionId: Int? = null,

    @field:NotNull(message = "Quantity is required")
    @field:DecimalMin(value = "0.01", inclusive = true, message = "Quantity must be greater than 0")
    val quantity: BigDecimal
)

// ========== Update Ingredient Request ==========

data class UpdateIngredientRequest(
    val portionId: Int? = null,

    @field:DecimalMin(value = "0.01", inclusive = true, message = "Quantity must be greater than 0")
    val quantity: BigDecimal? = null
)

// ========== Add Step Request ==========

data class AddStepRequest(
    @field:NotBlank(message = "Step description is required")
    @field:Size(max = 2000, message = "Step description cannot exceed 2000 characters")
    val description: String,

    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int? = null
)

// ========== Update Step Request ==========

data class UpdateStepRequest(
    @field:Size(max = 2000, message = "Step description cannot exceed 2000 characters")
    val description: String? = null,

    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int? = null
)

// ========== Reorder Steps Request ==========

data class ReorderStepsRequest(
    @field:NotEmpty(message = "Step IDs list cannot be empty")
    val stepIds: List<Int>
)

// ========== Reorder Steps Response ==========

data class ReorderStepsResponse(
    val steps: List<RecipeStepResponse>
)
