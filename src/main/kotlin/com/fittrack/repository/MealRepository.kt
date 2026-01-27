package com.fittrack.repository

import com.fittrack.model.Meal
import com.fittrack.model.MealItem
import com.fittrack.model.MealType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class MealRepository(private val jdbcTemplate: JdbcTemplate) {

    private val mealRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Meal(
            id = UUID.fromString(rs.getString("id")),
            dayId = UUID.fromString(rs.getString("day_id")),
            mealType = MealType.valueOf(rs.getString("meal_type")),
            isCheatMeal = rs.getBoolean("is_cheat_meal"),
            displayOrder = rs.getInt("display_order"),
            totalCalories = rs.getBigDecimal("total_calories") ?: BigDecimal.ZERO,
            totalFat = rs.getBigDecimal("total_fat") ?: BigDecimal.ZERO,
            totalCarbs = rs.getBigDecimal("total_carbs") ?: BigDecimal.ZERO,
            totalProtein = rs.getBigDecimal("total_protein") ?: BigDecimal.ZERO,
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private val mealItemRowMapper = RowMapper { rs: ResultSet, _: Int ->
        MealItem(
            id = UUID.fromString(rs.getString("id")),
            mealId = UUID.fromString(rs.getString("meal_id")),
            foodId = rs.getString("food_id")?.let { UUID.fromString(it) },
            recipeId = rs.getString("recipe_id")?.let { UUID.fromString(it) },
            portionId = rs.getObject("portion_id") as? Int,
            quantity = rs.getBigDecimal("quantity") ?: BigDecimal.ONE,
            amountGrams = rs.getBigDecimal("amount_grams"),
            quickEntryName = rs.getString("quick_entry_name"),
            isQuickEntry = rs.getBoolean("is_quick_entry"),
            calories = rs.getBigDecimal("calories") ?: BigDecimal.ZERO,
            fat = rs.getBigDecimal("fat"),
            carbs = rs.getBigDecimal("carbs"),
            protein = rs.getBigDecimal("protein"),
            salt = rs.getBigDecimal("salt"),
            sugar = rs.getBigDecimal("sugar"),
            fiber = rs.getBigDecimal("fiber"),
            saturatedFat = rs.getBigDecimal("saturated_fat"),
            displayOrder = rs.getInt("display_order"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    // ========== Meal Methods ==========

    fun findById(id: UUID): Meal? {
        val sql = "SELECT * FROM meals WHERE id = ?"
        return jdbcTemplate.query(sql, mealRowMapper, id).firstOrNull()
    }

    fun findByDayId(dayId: UUID): List<Meal> {
        val sql = "SELECT * FROM meals WHERE day_id = ? ORDER BY display_order, meal_type, created_at"
        return jdbcTemplate.query(sql, mealRowMapper, dayId)
    }

    fun findByDayIdAndMealType(dayId: UUID, mealType: MealType): List<Meal> {
        val sql = "SELECT * FROM meals WHERE day_id = ? AND meal_type = ?::meal_type ORDER BY display_order, created_at"
        return jdbcTemplate.query(sql, mealRowMapper, dayId, mealType.name)
    }

    fun findByProfileIdAndDateRange(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<Meal> {
        val sql = """
            SELECT m.* FROM meals m
            JOIN days d ON m.day_id = d.id
            WHERE d.profile_id = ? AND d.date >= ? AND d.date <= ?
            ORDER BY d.date, m.display_order, m.meal_type, m.created_at
        """.trimIndent()
        return jdbcTemplate.query(sql, mealRowMapper, profileId, startDate, endDate)
    }

    fun create(
        dayId: UUID,
        mealType: MealType,
        isCheatMeal: Boolean = false,
        displayOrder: Int = 0
    ): Meal {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO meals (id, day_id, meal_type, is_cheat_meal, display_order,
                total_calories, total_fat, total_carbs, total_protein, created_at, updated_at)
            VALUES (?, ?, ?::meal_type, ?, ?, 0, 0, 0, 0, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, mealRowMapper,
            id, dayId, mealType.name, isCheatMeal, displayOrder, Timestamp.from(now), Timestamp.from(now)
        ).first()
    }

    fun update(
        id: UUID,
        mealType: MealType? = null,
        isCheatMeal: Boolean? = null
    ): Meal? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        mealType?.let {
            updates.add("meal_type = ?::meal_type")
            params.add(it.name)
        }

        isCheatMeal?.let {
            updates.add("is_cheat_meal = ?")
            params.add(it)
        }

        if (updates.isEmpty()) {
            return findById(id)
        }

        params.add(id)
        val sql = "UPDATE meals SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, mealRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun updateTotals(id: UUID): Meal? {
        val sql = """
            UPDATE meals SET
                total_calories = COALESCE((SELECT SUM(calories) FROM meal_items WHERE meal_id = ?), 0),
                total_fat = COALESCE((SELECT SUM(fat) FROM meal_items WHERE meal_id = ?), 0),
                total_carbs = COALESCE((SELECT SUM(carbs) FROM meal_items WHERE meal_id = ?), 0),
                total_protein = COALESCE((SELECT SUM(protein) FROM meal_items WHERE meal_id = ?), 0)
            WHERE id = ?
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, mealRowMapper, id, id, id, id, id).firstOrNull()
    }

    fun delete(id: UUID): Boolean {
        val sql = "DELETE FROM meals WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    fun countByDayId(dayId: UUID): Int {
        val sql = "SELECT COUNT(*) FROM meals WHERE day_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, dayId) ?: 0
    }

    fun countCheatMealsByDayId(dayId: UUID): Int {
        val sql = "SELECT COUNT(*) FROM meals WHERE day_id = ? AND is_cheat_meal = true"
        return jdbcTemplate.queryForObject(sql, Int::class.java, dayId) ?: 0
    }

    // ========== Meal Item Methods ==========

    fun findItemById(id: UUID): MealItem? {
        val sql = "SELECT * FROM meal_items WHERE id = ?"
        return jdbcTemplate.query(sql, mealItemRowMapper, id).firstOrNull()
    }

    fun findItemsByMealId(mealId: UUID): List<MealItem> {
        val sql = "SELECT * FROM meal_items WHERE meal_id = ? ORDER BY display_order, created_at"
        return jdbcTemplate.query(sql, mealItemRowMapper, mealId)
    }

    fun createItem(
        mealId: UUID,
        foodId: UUID? = null,
        recipeId: UUID? = null,
        portionId: Int? = null,
        quantity: BigDecimal = BigDecimal.ONE,
        amountGrams: BigDecimal? = null,
        quickEntryName: String? = null,
        isQuickEntry: Boolean = false,
        calories: BigDecimal,
        fat: BigDecimal? = null,
        carbs: BigDecimal? = null,
        protein: BigDecimal? = null,
        salt: BigDecimal? = null,
        sugar: BigDecimal? = null,
        fiber: BigDecimal? = null,
        saturatedFat: BigDecimal? = null,
        displayOrder: Int = 0
    ): MealItem {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO meal_items (
                id, meal_id, food_id, recipe_id, portion_id, quantity, amount_grams,
                quick_entry_name, is_quick_entry, calories, fat, carbs, protein,
                salt, sugar, fiber, saturated_fat, display_order, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, mealItemRowMapper,
            id, mealId, foodId, recipeId, portionId, quantity, amountGrams,
            quickEntryName, isQuickEntry, calories, fat, carbs, protein,
            salt, sugar, fiber, saturatedFat, displayOrder, Timestamp.from(now), Timestamp.from(now)
        ).first()
    }

    fun updateItem(
        id: UUID,
        portionId: Int? = null,
        quantity: BigDecimal? = null,
        amountGrams: BigDecimal? = null,
        quickEntryName: String? = null,
        calories: BigDecimal? = null,
        fat: BigDecimal? = null,
        carbs: BigDecimal? = null,
        protein: BigDecimal? = null,
        salt: BigDecimal? = null,
        sugar: BigDecimal? = null,
        fiber: BigDecimal? = null,
        saturatedFat: BigDecimal? = null
    ): MealItem? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        // For portion_id, we need to handle null explicitly (to clear it)
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

        quickEntryName?.let {
            updates.add("quick_entry_name = ?")
            params.add(it)
        }

        calories?.let {
            updates.add("calories = ?")
            params.add(it)
        }

        fat?.let {
            updates.add("fat = ?")
            params.add(it)
        }

        carbs?.let {
            updates.add("carbs = ?")
            params.add(it)
        }

        protein?.let {
            updates.add("protein = ?")
            params.add(it)
        }

        salt?.let {
            updates.add("salt = ?")
            params.add(it)
        }

        sugar?.let {
            updates.add("sugar = ?")
            params.add(it)
        }

        fiber?.let {
            updates.add("fiber = ?")
            params.add(it)
        }

        saturatedFat?.let {
            updates.add("saturated_fat = ?")
            params.add(it)
        }

        params.add(id)
        val sql = "UPDATE meal_items SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, mealItemRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun deleteItem(id: UUID): Boolean {
        val sql = "DELETE FROM meal_items WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    fun countItemsByMealId(mealId: UUID): Int {
        val sql = "SELECT COUNT(*) FROM meal_items WHERE meal_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, mealId) ?: 0
    }

    // ========== Aggregate Query Methods ==========

    fun getDayTotals(dayId: UUID): Map<String, BigDecimal> {
        val sql = """
            SELECT
                COALESCE(SUM(m.total_calories), 0) as total_calories,
                COALESCE(SUM(m.total_fat), 0) as total_fat,
                COALESCE(SUM(m.total_carbs), 0) as total_carbs,
                COALESCE(SUM(m.total_protein), 0) as total_protein
            FROM meals m
            WHERE m.day_id = ?
        """.trimIndent()

        return jdbcTemplate.queryForMap(sql, dayId).mapValues { (_, value) ->
            when (value) {
                is BigDecimal -> value
                is Number -> BigDecimal(value.toString())
                else -> BigDecimal.ZERO
            }
        }
    }

    /**
     * Get the date for a meal by looking up its associated day
     */
    fun getMealDate(mealId: UUID): LocalDate? {
        val sql = """
            SELECT d.date FROM meals m
            JOIN days d ON m.day_id = d.id
            WHERE m.id = ?
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, LocalDate::class.java, mealId)
    }

    /**
     * Get the profile ID that owns a meal
     */
    fun getMealProfileId(mealId: UUID): UUID? {
        val sql = """
            SELECT d.profile_id FROM meals m
            JOIN days d ON m.day_id = d.id
            WHERE m.id = ?
        """.trimIndent()
        val result = jdbcTemplate.queryForObject(sql, String::class.java, mealId)
        return result?.let { UUID.fromString(it) }
    }
}
