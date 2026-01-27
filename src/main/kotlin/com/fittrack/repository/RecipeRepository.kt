package com.fittrack.repository

import com.fittrack.model.Recipe
import com.fittrack.model.RecipeIngredient
import com.fittrack.model.RecipeStep
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class RecipeRepository(private val jdbcTemplate: JdbcTemplate) {

    private val recipeRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Recipe(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            name = rs.getString("name"),
            description = rs.getString("description"),
            totalServings = rs.getInt("total_servings"),
            caloriesPerServing = rs.getBigDecimal("calories_per_serving"),
            fatPerServing = rs.getBigDecimal("fat_per_serving"),
            carbsPerServing = rs.getBigDecimal("carbs_per_serving"),
            proteinPerServing = rs.getBigDecimal("protein_per_serving"),
            saltPerServing = rs.getBigDecimal("salt_per_serving"),
            sugarPerServing = rs.getBigDecimal("sugar_per_serving"),
            fiberPerServing = rs.getBigDecimal("fiber_per_serving"),
            saturatedFatPerServing = rs.getBigDecimal("saturated_fat_per_serving"),
            totalDurationMinutes = rs.getObject("total_duration_minutes") as? Int,
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private val ingredientRowMapper = RowMapper { rs: ResultSet, _: Int ->
        RecipeIngredient(
            id = rs.getInt("id"),
            recipeId = UUID.fromString(rs.getString("recipe_id")),
            foodId = UUID.fromString(rs.getString("food_id")),
            portionId = rs.getObject("portion_id") as? Int,
            quantity = rs.getBigDecimal("quantity"),
            amountGrams = rs.getBigDecimal("amount_grams"),
            displayOrder = rs.getInt("display_order"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val stepRowMapper = RowMapper { rs: ResultSet, _: Int ->
        RecipeStep(
            id = rs.getInt("id"),
            recipeId = UUID.fromString(rs.getString("recipe_id")),
            stepNumber = rs.getInt("step_number"),
            description = rs.getString("description"),
            durationMinutes = rs.getObject("duration_minutes") as? Int,
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    // ========== Recipe CRUD ==========

    fun findById(id: UUID): Recipe? {
        val sql = "SELECT * FROM recipes WHERE id = ?"
        return jdbcTemplate.query(sql, recipeRowMapper, id).firstOrNull()
    }

    fun findByIdAndProfileId(id: UUID, profileId: UUID): Recipe? {
        val sql = "SELECT * FROM recipes WHERE id = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, recipeRowMapper, id, profileId).firstOrNull()
    }

    fun findAllByProfileId(
        profileId: UUID,
        page: Int,
        size: Int,
        search: String? = null,
        sort: String = "recentlyUsed"
    ): List<Recipe> {
        val conditions = mutableListOf("r.profile_id = ?")
        val params = mutableListOf<Any>(profileId)

        search?.let {
            if (it.isNotBlank()) {
                conditions.add("(r.name ILIKE ? OR r.name % ?)")
                params.add("%$it%")
                params.add(it)
            }
        }

        val orderBy = when (sort) {
            "name" -> "r.name ASC"
            "createdAt" -> "r.created_at DESC"
            else -> "r.created_at DESC" // recentlyUsed - TODO: implement with meal_items tracking
        }

        val sql = """
            SELECT r.* FROM recipes r
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()

        params.add(size)
        params.add(page * size)

        return jdbcTemplate.query(sql, recipeRowMapper, *params.toTypedArray())
    }

    fun countByProfileId(profileId: UUID, search: String? = null): Long {
        val conditions = mutableListOf("profile_id = ?")
        val params = mutableListOf<Any>(profileId)

        search?.let {
            if (it.isNotBlank()) {
                conditions.add("(name ILIKE ? OR name % ?)")
                params.add("%$it%")
                params.add(it)
            }
        }

        val sql = "SELECT COUNT(*) FROM recipes WHERE ${conditions.joinToString(" AND ")}"
        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()) ?: 0L
    }

    fun create(
        profileId: UUID,
        name: String,
        description: String?,
        totalServings: Int
    ): Recipe {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO recipes (
                id, profile_id, name, description, total_servings,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, recipeRowMapper,
            id, profileId, name, description, totalServings, Timestamp.from(now), Timestamp.from(now)
        ).first()
    }

    fun update(
        id: UUID,
        name: String?,
        description: String?,
        totalServings: Int?
    ): Recipe? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        name?.let {
            updates.add("name = ?")
            params.add(it)
        }

        if (description != null) {
            updates.add("description = ?")
            params.add(description)
        }

        totalServings?.let {
            updates.add("total_servings = ?")
            params.add(it)
        }

        if (updates.isEmpty()) {
            return findById(id)
        }

        params.add(id)
        val sql = "UPDATE recipes SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, recipeRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun updateNutrition(
        id: UUID,
        caloriesPerServing: BigDecimal?,
        fatPerServing: BigDecimal?,
        carbsPerServing: BigDecimal?,
        proteinPerServing: BigDecimal?,
        saltPerServing: BigDecimal?,
        sugarPerServing: BigDecimal?,
        fiberPerServing: BigDecimal?,
        saturatedFatPerServing: BigDecimal?
    ): Recipe? {
        val sql = """
            UPDATE recipes SET
                calories_per_serving = ?,
                fat_per_serving = ?,
                carbs_per_serving = ?,
                protein_per_serving = ?,
                salt_per_serving = ?,
                sugar_per_serving = ?,
                fiber_per_serving = ?,
                saturated_fat_per_serving = ?
            WHERE id = ?
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, recipeRowMapper,
            caloriesPerServing, fatPerServing, carbsPerServing, proteinPerServing,
            saltPerServing, sugarPerServing, fiberPerServing, saturatedFatPerServing, id
        ).firstOrNull()
    }

    fun updateTotalDuration(id: UUID, totalDurationMinutes: Int?): Recipe? {
        val sql = "UPDATE recipes SET total_duration_minutes = ? WHERE id = ? RETURNING *"
        return jdbcTemplate.query(sql, recipeRowMapper, totalDurationMinutes, id).firstOrNull()
    }

    fun delete(id: UUID): Boolean {
        val sql = "DELETE FROM recipes WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    // ========== Ingredient Methods ==========

    fun findIngredientsByRecipeId(recipeId: UUID): List<RecipeIngredient> {
        val sql = "SELECT * FROM recipe_ingredients WHERE recipe_id = ? ORDER BY display_order, id"
        return jdbcTemplate.query(sql, ingredientRowMapper, recipeId)
    }

    fun findIngredientById(id: Int): RecipeIngredient? {
        val sql = "SELECT * FROM recipe_ingredients WHERE id = ?"
        return jdbcTemplate.query(sql, ingredientRowMapper, id).firstOrNull()
    }

    fun findIngredientByIdAndRecipeId(id: Int, recipeId: UUID): RecipeIngredient? {
        val sql = "SELECT * FROM recipe_ingredients WHERE id = ? AND recipe_id = ?"
        return jdbcTemplate.query(sql, ingredientRowMapper, id, recipeId).firstOrNull()
    }

    fun countIngredientsByRecipeId(recipeId: UUID): Int {
        val sql = "SELECT COUNT(*) FROM recipe_ingredients WHERE recipe_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, recipeId) ?: 0
    }

    fun createIngredient(
        recipeId: UUID,
        foodId: UUID,
        portionId: Int?,
        quantity: BigDecimal,
        amountGrams: BigDecimal,
        displayOrder: Int = 0
    ): RecipeIngredient {
        val now = Instant.now()
        val sql = """
            INSERT INTO recipe_ingredients (
                recipe_id, food_id, portion_id, quantity, amount_grams, display_order, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, ingredientRowMapper,
            recipeId, foodId, portionId, quantity, amountGrams, displayOrder, Timestamp.from(now)
        ).first()
    }

    fun updateIngredient(
        id: Int,
        portionId: Int?,
        quantity: BigDecimal?,
        amountGrams: BigDecimal?
    ): RecipeIngredient? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        // Allow setting portionId to null
        updates.add("portion_id = ?")
        params.add(portionId)

        quantity?.let {
            updates.add("quantity = ?")
            params.add(it)
        }

        amountGrams?.let {
            updates.add("amount_grams = ?")
            params.add(it)
        }

        if (updates.isEmpty()) {
            return findIngredientById(id)
        }

        params.add(id)
        val sql = "UPDATE recipe_ingredients SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, ingredientRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun deleteIngredient(id: Int): Boolean {
        val sql = "DELETE FROM recipe_ingredients WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    // ========== Step Methods ==========

    fun findStepsByRecipeId(recipeId: UUID): List<RecipeStep> {
        val sql = "SELECT * FROM recipe_steps WHERE recipe_id = ? ORDER BY step_number"
        return jdbcTemplate.query(sql, stepRowMapper, recipeId)
    }

    fun findStepById(id: Int): RecipeStep? {
        val sql = "SELECT * FROM recipe_steps WHERE id = ?"
        return jdbcTemplate.query(sql, stepRowMapper, id).firstOrNull()
    }

    fun findStepByIdAndRecipeId(id: Int, recipeId: UUID): RecipeStep? {
        val sql = "SELECT * FROM recipe_steps WHERE id = ? AND recipe_id = ?"
        return jdbcTemplate.query(sql, stepRowMapper, id, recipeId).firstOrNull()
    }

    fun getMaxStepNumber(recipeId: UUID): Int {
        val sql = "SELECT COALESCE(MAX(step_number), 0) FROM recipe_steps WHERE recipe_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, recipeId) ?: 0
    }

    fun createStep(
        recipeId: UUID,
        stepNumber: Int,
        description: String,
        durationMinutes: Int?
    ): RecipeStep {
        val now = Instant.now()
        val sql = """
            INSERT INTO recipe_steps (recipe_id, step_number, description, duration_minutes, created_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, stepRowMapper,
            recipeId, stepNumber, description, durationMinutes, Timestamp.from(now)
        ).first()
    }

    fun updateStep(
        id: Int,
        description: String?,
        durationMinutes: Int?
    ): RecipeStep? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        description?.let {
            updates.add("description = ?")
            params.add(it)
        }

        // Allow setting durationMinutes to null (hence checking if explicitly provided)
        updates.add("duration_minutes = ?")
        params.add(durationMinutes)

        if (updates.isEmpty()) {
            return findStepById(id)
        }

        params.add(id)
        val sql = "UPDATE recipe_steps SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, stepRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun updateStepNumber(id: Int, stepNumber: Int): RecipeStep? {
        val sql = "UPDATE recipe_steps SET step_number = ? WHERE id = ? RETURNING *"
        return jdbcTemplate.query(sql, stepRowMapper, stepNumber, id).firstOrNull()
    }

    fun deleteStep(id: Int): Boolean {
        val sql = "DELETE FROM recipe_steps WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    fun renumberStepsAfterDelete(recipeId: UUID, deletedStepNumber: Int) {
        val sql = """
            UPDATE recipe_steps
            SET step_number = step_number - 1
            WHERE recipe_id = ? AND step_number > ?
        """.trimIndent()
        jdbcTemplate.update(sql, recipeId, deletedStepNumber)
    }

    fun getTotalStepDuration(recipeId: UUID): Int? {
        val sql = "SELECT SUM(duration_minutes) FROM recipe_steps WHERE recipe_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, recipeId)
    }

    // ========== Recipe Usage Tracking ==========

    fun getLastUsedAt(profileId: UUID, recipeId: UUID): Instant? {
        val sql = """
            SELECT MAX(mi.created_at)
            FROM meal_items mi
            JOIN meals m ON mi.meal_id = m.id
            JOIN days d ON m.day_id = d.id
            WHERE d.profile_id = ? AND mi.recipe_id = ?
        """.trimIndent()
        return jdbcTemplate.query(sql, { rs, _ ->
            rs.getTimestamp(1)?.toInstant()
        }, profileId, recipeId).firstOrNull()
    }

    // ========== Thumbnail Helper ==========

    fun getThumbnailUrl(recipeId: UUID): String? {
        val sql = """
            SELECT image_url FROM recipe_images
            WHERE recipe_id = ? AND is_thumbnail = true
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, { rs, _ -> rs.getString("image_url") }, recipeId).firstOrNull()
    }
}
