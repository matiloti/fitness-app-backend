package com.fittrack.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Meal type - maps to PostgreSQL meal_type enum
 */
enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK
}

/**
 * Day domain model - maps to days table
 */
data class Day(
    val id: UUID,
    val profileId: UUID,
    val date: LocalDate,
    val activityLevelOverride: ActivityLevel? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Meal domain model - maps to meals table
 */
data class Meal(
    val id: UUID,
    val dayId: UUID,
    val mealType: MealType,
    val isCheatMeal: Boolean = false,
    val displayOrder: Int = 0,
    val totalCalories: BigDecimal = BigDecimal.ZERO,
    val totalFat: BigDecimal = BigDecimal.ZERO,
    val totalCarbs: BigDecimal = BigDecimal.ZERO,
    val totalProtein: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Meal item domain model - maps to meal_items table
 * Can be a food, recipe, or quick entry
 */
data class MealItem(
    val id: UUID,
    val mealId: UUID,
    val foodId: UUID? = null,
    val recipeId: UUID? = null,
    val portionId: Int? = null,
    val quantity: BigDecimal = BigDecimal.ONE,
    val amountGrams: BigDecimal? = null,
    val quickEntryName: String? = null,
    val isQuickEntry: Boolean = false,
    val calories: BigDecimal,
    val fat: BigDecimal? = null,
    val carbs: BigDecimal? = null,
    val protein: BigDecimal? = null,
    val salt: BigDecimal? = null,
    val sugar: BigDecimal? = null,
    val fiber: BigDecimal? = null,
    val saturatedFat: BigDecimal? = null,
    val displayOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Body metric domain model - maps to body_metrics table
 */
data class BodyMetric(
    val id: UUID,
    val profileId: UUID,
    val date: LocalDate,
    val weightKg: BigDecimal? = null,
    val bodyFatPercentage: BigDecimal? = null,
    val bodyFatKg: BigDecimal? = null,
    val muscleMassPercentage: BigDecimal? = null,
    val muscleMassKg: BigDecimal? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
