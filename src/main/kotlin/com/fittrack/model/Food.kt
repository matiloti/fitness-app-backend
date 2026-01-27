package com.fittrack.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Metric type for foods - maps to PostgreSQL metric_type enum
 */
enum class MetricType {
    GRAMS,
    MILLILITERS
}

/**
 * Food item domain model - maps to foods table
 */
data class Food(
    val id: UUID,
    val profileId: UUID,
    val name: String,
    val categoryId: Int? = null,
    val brandId: Int? = null,
    val metricType: MetricType = MetricType.GRAMS,
    val caloriesPer100: BigDecimal,
    val fatPer100: BigDecimal,
    val carbsPer100: BigDecimal,
    val proteinPer100: BigDecimal,
    val saltPer100: BigDecimal? = null,
    val sugarPer100: BigDecimal? = null,
    val fiberPer100: BigDecimal? = null,
    val saturatedFatPer100: BigDecimal? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Food portion domain model - maps to food_portions table
 */
data class FoodPortion(
    val id: Int,
    val foodId: UUID,
    val name: String,
    val amountGrams: BigDecimal,
    val createdAt: Instant
)

/**
 * Food category domain model - maps to categories table
 */
data class Category(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val isSystem: Boolean = false,
    val createdAt: Instant? = null
)

/**
 * Brand domain model - maps to brands table
 */
data class Brand(
    val id: Int,
    val profileId: UUID,
    val name: String,
    val description: String? = null,
    val photoUrl: String? = null,
    val countryId: Short? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Recent food tracking - maps to recent_foods table
 */
data class RecentFood(
    val id: Int,
    val profileId: UUID,
    val foodId: UUID,
    val lastUsedAt: Instant,
    val useCount: Int
)
