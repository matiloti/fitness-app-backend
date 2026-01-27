package com.fittrack.model.dto.food

import com.fittrack.model.MetricType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// ========== Nested Response Objects ==========

data class CategorySummary(
    val id: Int,
    val name: String,
    val icon: String?
)

data class BrandSummary(
    val id: Int,
    val name: String
)

data class NutritionInfo(
    val caloriesPer100: BigDecimal,
    val fatPer100: BigDecimal,
    val carbsPer100: BigDecimal,
    val proteinPer100: BigDecimal,
    val saltPer100: BigDecimal? = null,
    val sugarPer100: BigDecimal? = null,
    val fiberPer100: BigDecimal? = null,
    val saturatedFatPer100: BigDecimal? = null
)

data class PortionResponse(
    val id: Int,
    val name: String,
    val amountGrams: BigDecimal
)

data class CountrySummary(
    val code: String,
    val name: String
)

// ========== Food Responses ==========

data class FoodListItem(
    val id: UUID,
    val name: String,
    val category: CategorySummary?,
    val brand: BrandSummary?,
    val metricType: MetricType,
    val nutrition: NutritionInfo,
    val lastUsedAt: Instant?
)

data class FoodDetailResponse(
    val id: UUID,
    val name: String,
    val category: CategorySummary?,
    val brand: BrandSummary?,
    val metricType: MetricType,
    val nutrition: NutritionInfo,
    val portions: List<PortionResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class FoodListResponse(
    val content: List<FoodListItem>,
    val page: PageInfo
)

data class PageInfo(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

// ========== Food Requests ==========

data class NutritionRequest(
    @field:NotNull(message = "Calories is required")
    @field:DecimalMin(value = "0", message = "Calories must be >= 0")
    val caloriesPer100: BigDecimal,

    @field:NotNull(message = "Fat is required")
    @field:DecimalMin(value = "0", message = "Fat must be >= 0")
    val fatPer100: BigDecimal,

    @field:NotNull(message = "Carbs is required")
    @field:DecimalMin(value = "0", message = "Carbs must be >= 0")
    val carbsPer100: BigDecimal,

    @field:NotNull(message = "Protein is required")
    @field:DecimalMin(value = "0", message = "Protein must be >= 0")
    val proteinPer100: BigDecimal,

    @field:DecimalMin(value = "0", message = "Salt must be >= 0")
    val saltPer100: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Sugar must be >= 0")
    val sugarPer100: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Fiber must be >= 0")
    val fiberPer100: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Saturated fat must be >= 0")
    val saturatedFatPer100: BigDecimal? = null
)

data class PortionRequest(
    @field:NotBlank(message = "Portion name is required")
    @field:Size(max = 50, message = "Portion name cannot exceed 50 characters")
    val name: String,

    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than 0")
    val amountGrams: BigDecimal
)

data class CreateFoodRequest(
    @field:NotBlank(message = "Food name is required")
    @field:Size(max = 200, message = "Food name cannot exceed 200 characters")
    val name: String,

    val categoryId: Int? = null,

    val brandId: Int? = null,

    @field:NotNull(message = "Metric type is required")
    val metricType: MetricType = MetricType.GRAMS,

    @field:Valid
    @field:NotNull(message = "Nutrition info is required")
    val nutrition: NutritionRequest,

    @field:Valid
    val portions: List<PortionRequest> = emptyList()
)

data class UpdateFoodRequest(
    @field:Size(max = 200, message = "Food name cannot exceed 200 characters")
    val name: String? = null,

    val categoryId: Int? = null,

    val brandId: Int? = null,

    @field:Valid
    val nutrition: NutritionRequest? = null
)

data class CreatePortionRequest(
    @field:NotBlank(message = "Portion name is required")
    @field:Size(max = 50, message = "Portion name cannot exceed 50 characters")
    val name: String,

    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than 0")
    val amountGrams: BigDecimal
)

// ========== Brand Responses ==========

data class BrandDetailResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val photoUrl: String?,
    val country: CountrySummary?,
    val createdAt: Instant
)

data class BrandListResponse(
    val content: List<BrandDetailResponse>,
    val page: PageInfo
)

// ========== Brand Requests ==========

data class CreateBrandRequest(
    @field:NotBlank(message = "Brand name is required")
    @field:Size(max = 100, message = "Brand name cannot exceed 100 characters")
    val name: String,

    @field:Size(max = 1000, message = "Description cannot exceed 1000 characters")
    val description: String? = null,

    @field:Size(max = 500, message = "Photo URL cannot exceed 500 characters")
    val photoUrl: String? = null,

    @field:Size(min = 2, max = 2, message = "Country code must be 2 characters")
    val countryCode: String? = null
)

data class UpdateBrandRequest(
    @field:Size(max = 100, message = "Brand name cannot exceed 100 characters")
    val name: String? = null,

    @field:Size(max = 1000, message = "Description cannot exceed 1000 characters")
    val description: String? = null,

    @field:Size(max = 500, message = "Photo URL cannot exceed 500 characters")
    val photoUrl: String? = null,

    @field:Size(min = 2, max = 2, message = "Country code must be 2 characters")
    val countryCode: String? = null
)

// ========== Category Responses ==========

data class CategoryResponse(
    val id: Int,
    val name: String,
    val icon: String?
)

data class CategoryListResponse(
    val categories: List<CategoryResponse>
)

// ========== Recent Foods ==========

data class RecentFoodItem(
    val id: UUID,
    val name: String,
    val nutrition: NutritionInfo,
    val defaultPortion: PortionResponse?,
    val lastUsedAt: Instant,
    val useCount: Int
)

data class RecentFoodsResponse(
    val foods: List<RecentFoodItem>
)
