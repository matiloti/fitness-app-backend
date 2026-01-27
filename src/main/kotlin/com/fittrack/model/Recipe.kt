package com.fittrack.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Recipe domain model - maps to recipes table
 */
data class Recipe(
    val id: UUID,
    val profileId: UUID,
    val name: String,
    val description: String? = null,
    val totalServings: Int = 1,
    // Cached nutritional values per serving (calculated from ingredients)
    val caloriesPerServing: BigDecimal? = null,
    val fatPerServing: BigDecimal? = null,
    val carbsPerServing: BigDecimal? = null,
    val proteinPerServing: BigDecimal? = null,
    val saltPerServing: BigDecimal? = null,
    val sugarPerServing: BigDecimal? = null,
    val fiberPerServing: BigDecimal? = null,
    val saturatedFatPerServing: BigDecimal? = null,
    // Cached total duration from steps
    val totalDurationMinutes: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Recipe ingredient domain model - maps to recipe_ingredients table
 */
data class RecipeIngredient(
    val id: Int,
    val recipeId: UUID,
    val foodId: UUID,
    val portionId: Int? = null,
    val quantity: BigDecimal,
    val amountGrams: BigDecimal,
    val displayOrder: Int = 0,
    val createdAt: Instant
)

/**
 * Recipe step domain model - maps to recipe_steps table
 */
data class RecipeStep(
    val id: Int,
    val recipeId: UUID,
    val stepNumber: Int,
    val description: String,
    val durationMinutes: Int? = null,
    val createdAt: Instant
)

/**
 * Recipe image domain model - maps to recipe_images table
 */
data class RecipeImage(
    val id: Int,
    val recipeId: UUID,
    val imageUrl: String,
    val displayOrder: Int = 0,
    val isThumbnail: Boolean = false,
    val createdAt: Instant
)
