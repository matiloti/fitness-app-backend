package com.fittrack.repository

import com.fittrack.model.Food
import com.fittrack.model.FoodPortion
import com.fittrack.model.MetricType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class FoodRepository(private val jdbcTemplate: JdbcTemplate) {

    private val foodRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Food(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            name = rs.getString("name"),
            categoryId = rs.getObject("category_id") as? Int,
            brandId = rs.getObject("brand_id") as? Int,
            metricType = MetricType.valueOf(rs.getString("metric_type")),
            caloriesPer100 = rs.getBigDecimal("calories_per_100"),
            fatPer100 = rs.getBigDecimal("fat_per_100"),
            carbsPer100 = rs.getBigDecimal("carbs_per_100"),
            proteinPer100 = rs.getBigDecimal("protein_per_100"),
            saltPer100 = rs.getBigDecimal("salt_per_100"),
            sugarPer100 = rs.getBigDecimal("sugar_per_100"),
            fiberPer100 = rs.getBigDecimal("fiber_per_100"),
            saturatedFatPer100 = rs.getBigDecimal("saturated_fat_per_100"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private val portionRowMapper = RowMapper { rs: ResultSet, _: Int ->
        FoodPortion(
            id = rs.getInt("id"),
            foodId = UUID.fromString(rs.getString("food_id")),
            name = rs.getString("name"),
            amountGrams = rs.getBigDecimal("amount_grams"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun findById(id: UUID): Food? {
        val sql = "SELECT * FROM foods WHERE id = ?"
        return jdbcTemplate.query(sql, foodRowMapper, id).firstOrNull()
    }

    fun findByIdAndProfileId(id: UUID, profileId: UUID): Food? {
        val sql = "SELECT * FROM foods WHERE id = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, foodRowMapper, id, profileId).firstOrNull()
    }

    fun findAllByProfileId(
        profileId: UUID,
        page: Int,
        size: Int,
        categoryId: Int? = null,
        brandId: Int? = null,
        search: String? = null,
        sort: String = "createdAt"
    ): List<Food> {
        val conditions = mutableListOf("f.profile_id = ?")
        val params = mutableListOf<Any>(profileId)

        categoryId?.let {
            conditions.add("f.category_id = ?")
            params.add(it)
        }

        brandId?.let {
            conditions.add("f.brand_id = ?")
            params.add(it)
        }

        search?.let {
            if (it.isNotBlank()) {
                conditions.add("(f.name ILIKE ? OR f.name % ?)")
                params.add("%$it%")
                params.add(it)
            }
        }

        val orderBy = when (sort) {
            "name" -> "f.name ASC"
            "recentlyUsed" -> "COALESCE(rf.last_used_at, '1970-01-01'::timestamptz) DESC, f.created_at DESC"
            else -> "f.created_at DESC"
        }

        val sql = """
            SELECT f.* FROM foods f
            LEFT JOIN recent_foods rf ON f.id = rf.food_id AND rf.profile_id = f.profile_id
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()

        params.add(size)
        params.add(page * size)

        return jdbcTemplate.query(sql, foodRowMapper, *params.toTypedArray())
    }

    fun countByProfileId(
        profileId: UUID,
        categoryId: Int? = null,
        brandId: Int? = null,
        search: String? = null
    ): Long {
        val conditions = mutableListOf("profile_id = ?")
        val params = mutableListOf<Any>(profileId)

        categoryId?.let {
            conditions.add("category_id = ?")
            params.add(it)
        }

        brandId?.let {
            conditions.add("brand_id = ?")
            params.add(it)
        }

        search?.let {
            if (it.isNotBlank()) {
                conditions.add("(name ILIKE ? OR name % ?)")
                params.add("%$it%")
                params.add(it)
            }
        }

        val sql = "SELECT COUNT(*) FROM foods WHERE ${conditions.joinToString(" AND ")}"
        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()) ?: 0L
    }

    fun create(
        profileId: UUID,
        name: String,
        categoryId: Int?,
        brandId: Int?,
        metricType: MetricType,
        caloriesPer100: BigDecimal,
        fatPer100: BigDecimal,
        carbsPer100: BigDecimal,
        proteinPer100: BigDecimal,
        saltPer100: BigDecimal?,
        sugarPer100: BigDecimal?,
        fiberPer100: BigDecimal?,
        saturatedFatPer100: BigDecimal?
    ): Food {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO foods (
                id, profile_id, name, category_id, brand_id, metric_type,
                calories_per_100, fat_per_100, carbs_per_100, protein_per_100,
                salt_per_100, sugar_per_100, fiber_per_100, saturated_fat_per_100,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?::metric_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, foodRowMapper,
            id, profileId, name, categoryId, brandId, metricType.name,
            caloriesPer100, fatPer100, carbsPer100, proteinPer100,
            saltPer100, sugarPer100, fiberPer100, saturatedFatPer100,
            now, now
        ).first()
    }

    fun update(
        id: UUID,
        name: String?,
        categoryId: Int?,
        brandId: Int?,
        caloriesPer100: BigDecimal?,
        fatPer100: BigDecimal?,
        carbsPer100: BigDecimal?,
        proteinPer100: BigDecimal?,
        saltPer100: BigDecimal?,
        sugarPer100: BigDecimal?,
        fiberPer100: BigDecimal?,
        saturatedFatPer100: BigDecimal?
    ): Food? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        name?.let {
            updates.add("name = ?")
            params.add(it)
        }

        // Handle category_id - use a flag approach to distinguish "update to null" from "don't update"
        if (categoryId != null || name != null) { // Only include if explicitly provided
            updates.add("category_id = ?")
            params.add(categoryId)
        }

        if (brandId != null || name != null) { // Only include if explicitly provided
            updates.add("brand_id = ?")
            params.add(brandId)
        }

        caloriesPer100?.let {
            updates.add("calories_per_100 = ?")
            params.add(it)
        }

        fatPer100?.let {
            updates.add("fat_per_100 = ?")
            params.add(it)
        }

        carbsPer100?.let {
            updates.add("carbs_per_100 = ?")
            params.add(it)
        }

        proteinPer100?.let {
            updates.add("protein_per_100 = ?")
            params.add(it)
        }

        // Optional nutrition fields - only update if explicitly set
        saltPer100?.let {
            updates.add("salt_per_100 = ?")
            params.add(it)
        }

        sugarPer100?.let {
            updates.add("sugar_per_100 = ?")
            params.add(it)
        }

        fiberPer100?.let {
            updates.add("fiber_per_100 = ?")
            params.add(it)
        }

        saturatedFatPer100?.let {
            updates.add("saturated_fat_per_100 = ?")
            params.add(it)
        }

        if (updates.isEmpty()) {
            return findById(id)
        }

        params.add(id)
        val sql = "UPDATE foods SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, foodRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun delete(id: UUID): Boolean {
        val sql = "DELETE FROM foods WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    fun isUsedInRecipes(id: UUID): Boolean {
        val sql = "SELECT COUNT(*) FROM recipe_ingredients WHERE food_id = ?"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, id) ?: 0) > 0
    }

    // ========== Portion Methods ==========

    fun findPortionsByFoodId(foodId: UUID): List<FoodPortion> {
        val sql = "SELECT * FROM food_portions WHERE food_id = ? ORDER BY id"
        return jdbcTemplate.query(sql, portionRowMapper, foodId)
    }

    fun findPortionById(id: Int): FoodPortion? {
        val sql = "SELECT * FROM food_portions WHERE id = ?"
        return jdbcTemplate.query(sql, portionRowMapper, id).firstOrNull()
    }

    fun findPortionByIdAndFoodId(id: Int, foodId: UUID): FoodPortion? {
        val sql = "SELECT * FROM food_portions WHERE id = ? AND food_id = ?"
        return jdbcTemplate.query(sql, portionRowMapper, id, foodId).firstOrNull()
    }

    fun portionExistsForFood(foodId: UUID, name: String): Boolean {
        val sql = "SELECT COUNT(*) FROM food_portions WHERE food_id = ? AND LOWER(name) = LOWER(?)"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, foodId, name) ?: 0) > 0
    }

    fun createPortion(foodId: UUID, name: String, amountGrams: BigDecimal): FoodPortion {
        val now = Instant.now()
        val sql = """
            INSERT INTO food_portions (food_id, name, amount_grams, created_at)
            VALUES (?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, portionRowMapper, foodId, name, amountGrams, now).first()
    }

    fun createPortions(foodId: UUID, portions: List<Pair<String, BigDecimal>>): List<FoodPortion> {
        if (portions.isEmpty()) return emptyList()

        val now = Instant.now()
        return portions.map { (name, amountGrams) ->
            createPortion(foodId, name, amountGrams)
        }
    }

    fun deletePortion(id: Int): Boolean {
        val sql = "DELETE FROM food_portions WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    // ========== Recent Foods Methods ==========

    fun updateRecentFood(profileId: UUID, foodId: UUID) {
        val sql = """
            INSERT INTO recent_foods (profile_id, food_id, last_used_at, use_count)
            VALUES (?, ?, NOW(), 1)
            ON CONFLICT (profile_id, food_id)
            DO UPDATE SET last_used_at = NOW(), use_count = recent_foods.use_count + 1
        """.trimIndent()

        jdbcTemplate.update(sql, profileId, foodId)
    }

    fun findRecentFoods(profileId: UUID, limit: Int): List<Pair<Food, Pair<Instant, Int>>> {
        val sql = """
            SELECT f.*, rf.last_used_at as rf_last_used_at, rf.use_count as rf_use_count
            FROM foods f
            INNER JOIN recent_foods rf ON f.id = rf.food_id
            WHERE rf.profile_id = ?
            ORDER BY rf.last_used_at DESC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            val food = Food(
                id = UUID.fromString(rs.getString("id")),
                profileId = UUID.fromString(rs.getString("profile_id")),
                name = rs.getString("name"),
                categoryId = rs.getObject("category_id") as? Int,
                brandId = rs.getObject("brand_id") as? Int,
                metricType = MetricType.valueOf(rs.getString("metric_type")),
                caloriesPer100 = rs.getBigDecimal("calories_per_100"),
                fatPer100 = rs.getBigDecimal("fat_per_100"),
                carbsPer100 = rs.getBigDecimal("carbs_per_100"),
                proteinPer100 = rs.getBigDecimal("protein_per_100"),
                saltPer100 = rs.getBigDecimal("salt_per_100"),
                sugarPer100 = rs.getBigDecimal("sugar_per_100"),
                fiberPer100 = rs.getBigDecimal("fiber_per_100"),
                saturatedFatPer100 = rs.getBigDecimal("saturated_fat_per_100"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant()
            )
            val lastUsedAt = rs.getTimestamp("rf_last_used_at").toInstant()
            val useCount = rs.getInt("rf_use_count")
            food to (lastUsedAt to useCount)
        }, profileId, limit)
    }

    fun getLastUsedAt(profileId: UUID, foodId: UUID): Instant? {
        val sql = "SELECT last_used_at FROM recent_foods WHERE profile_id = ? AND food_id = ?"
        return jdbcTemplate.query(sql, { rs, _ -> rs.getTimestamp("last_used_at").toInstant() }, profileId, foodId)
            .firstOrNull()
    }
}
