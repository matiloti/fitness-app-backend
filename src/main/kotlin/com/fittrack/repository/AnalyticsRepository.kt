package com.fittrack.repository

import com.fittrack.model.dto.analytics.AggregationPeriod
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Raw data classes for repository layer
 */
data class DailyCalorieData(
    val date: LocalDate,
    val totalCalories: BigDecimal,
    val totalProtein: BigDecimal,
    val totalCarbs: BigDecimal,
    val totalFat: BigDecimal,
    val cheatMeals: Int
)

data class DailyWeightData(
    val date: LocalDate,
    val weightKg: BigDecimal,
    val bodyFatPercentage: BigDecimal?,
    val bodyFatKg: BigDecimal?,
    val muscleMassPercentage: BigDecimal?,
    val muscleMassKg: BigDecimal?
)

data class DailyWorkoutData(
    val date: LocalDate,
    val workoutCount: Int,
    val totalDuration: Int,
    val totalCalories: BigDecimal,
    val workoutTypes: List<String>
)

data class StreakData(
    val date: LocalDate,
    val hasLog: Boolean
)

@Repository
class AnalyticsRepository(private val jdbcTemplate: JdbcTemplate) {

    // ========== Weight/Body Metrics Queries ==========

    /**
     * Get weight data points for a date range
     */
    fun getWeightData(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyWeightData> {
        val sql = """
            SELECT date, weight_kg, body_fat_percentage, body_fat_kg,
                   muscle_mass_percentage, muscle_mass_kg
            FROM body_metrics
            WHERE profile_id = ? AND date >= ? AND date <= ?
              AND weight_kg IS NOT NULL
            ORDER BY date ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            DailyWeightData(
                date = rs.getDate("date").toLocalDate(),
                weightKg = rs.getBigDecimal("weight_kg"),
                bodyFatPercentage = rs.getBigDecimal("body_fat_percentage"),
                bodyFatKg = rs.getBigDecimal("body_fat_kg"),
                muscleMassPercentage = rs.getBigDecimal("muscle_mass_percentage"),
                muscleMassKg = rs.getBigDecimal("muscle_mass_kg")
            )
        }, profileId, startDate, endDate)
    }

    /**
     * Get body composition data points (includes entries with body fat or muscle, not just weight)
     */
    fun getBodyCompositionData(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyWeightData> {
        val sql = """
            SELECT date, weight_kg, body_fat_percentage, body_fat_kg,
                   muscle_mass_percentage, muscle_mass_kg
            FROM body_metrics
            WHERE profile_id = ? AND date >= ? AND date <= ?
              AND (weight_kg IS NOT NULL OR body_fat_percentage IS NOT NULL OR muscle_mass_percentage IS NOT NULL)
            ORDER BY date ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            DailyWeightData(
                date = rs.getDate("date").toLocalDate(),
                weightKg = rs.getBigDecimal("weight_kg"),
                bodyFatPercentage = rs.getBigDecimal("body_fat_percentage"),
                bodyFatKg = rs.getBigDecimal("body_fat_kg"),
                muscleMassPercentage = rs.getBigDecimal("muscle_mass_percentage"),
                muscleMassKg = rs.getBigDecimal("muscle_mass_kg")
            )
        }, profileId, startDate, endDate)
    }

    /**
     * Get latest weight before a date (for calculations)
     */
    fun getLatestWeightBefore(profileId: UUID, date: LocalDate): BigDecimal? {
        val sql = """
            SELECT weight_kg FROM body_metrics
            WHERE profile_id = ? AND date <= ? AND weight_kg IS NOT NULL
            ORDER BY date DESC LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ -> rs.getBigDecimal("weight_kg") }, profileId, date).firstOrNull()
    }

    /**
     * Get first weight entry (starting weight)
     */
    fun getFirstWeight(profileId: UUID): Pair<LocalDate, BigDecimal>? {
        val sql = """
            SELECT date, weight_kg FROM body_metrics
            WHERE profile_id = ? AND weight_kg IS NOT NULL
            ORDER BY date ASC LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            Pair(rs.getDate("date").toLocalDate(), rs.getBigDecimal("weight_kg"))
        }, profileId).firstOrNull()
    }

    // ========== Calorie/Nutrition Queries ==========

    /**
     * Get daily calorie and macro data
     */
    fun getDailyCalorieData(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyCalorieData> {
        val sql = """
            SELECT
                d.date,
                COALESCE(SUM(m.total_calories), 0) as total_calories,
                COALESCE(SUM(m.total_protein), 0) as total_protein,
                COALESCE(SUM(m.total_carbs), 0) as total_carbs,
                COALESCE(SUM(m.total_fat), 0) as total_fat,
                COALESCE(SUM(CASE WHEN m.is_cheat_meal THEN 1 ELSE 0 END), 0) as cheat_meals
            FROM days d
            LEFT JOIN meals m ON d.id = m.day_id
            WHERE d.profile_id = ? AND d.date >= ? AND d.date <= ?
            GROUP BY d.date
            ORDER BY d.date ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            DailyCalorieData(
                date = rs.getDate("date").toLocalDate(),
                totalCalories = rs.getBigDecimal("total_calories") ?: BigDecimal.ZERO,
                totalProtein = rs.getBigDecimal("total_protein") ?: BigDecimal.ZERO,
                totalCarbs = rs.getBigDecimal("total_carbs") ?: BigDecimal.ZERO,
                totalFat = rs.getBigDecimal("total_fat") ?: BigDecimal.ZERO,
                cheatMeals = rs.getInt("cheat_meals")
            )
        }, profileId, startDate, endDate)
    }

    /**
     * Get total calories for a specific date
     */
    fun getDayTotalCalories(profileId: UUID, date: LocalDate): BigDecimal {
        val sql = """
            SELECT COALESCE(SUM(m.total_calories), 0) as total_calories
            FROM days d
            LEFT JOIN meals m ON d.id = m.day_id
            WHERE d.profile_id = ? AND d.date = ?
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, BigDecimal::class.java, profileId, date) ?: BigDecimal.ZERO
    }

    /**
     * Get macros for a specific date
     */
    fun getDayMacros(profileId: UUID, date: LocalDate): Triple<BigDecimal, BigDecimal, BigDecimal>? {
        val sql = """
            SELECT
                COALESCE(SUM(m.total_protein), 0) as total_protein,
                COALESCE(SUM(m.total_carbs), 0) as total_carbs,
                COALESCE(SUM(m.total_fat), 0) as total_fat
            FROM days d
            LEFT JOIN meals m ON d.id = m.day_id
            WHERE d.profile_id = ? AND d.date = ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            Triple(
                rs.getBigDecimal("total_protein") ?: BigDecimal.ZERO,
                rs.getBigDecimal("total_carbs") ?: BigDecimal.ZERO,
                rs.getBigDecimal("total_fat") ?: BigDecimal.ZERO
            )
        }, profileId, date).firstOrNull()
    }

    // ========== Workout Queries ==========

    /**
     * Get workout data for a date range
     */
    fun getWorkoutData(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyWorkoutData> {
        val sql = """
            SELECT
                date,
                COUNT(*) as workout_count,
                COALESCE(SUM(duration_minutes), 0) as total_duration,
                COALESCE(SUM(COALESCE(calories_burned_actual, calories_burned_estimated)), 0) as total_calories,
                ARRAY_AGG(workout_type::text) as workout_types
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
            GROUP BY date
            ORDER BY date ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            val typesArray = rs.getArray("workout_types")
            val types = if (typesArray != null) {
                @Suppress("UNCHECKED_CAST")
                (typesArray.array as Array<String>).toList()
            } else {
                emptyList()
            }

            DailyWorkoutData(
                date = rs.getDate("date").toLocalDate(),
                workoutCount = rs.getInt("workout_count"),
                totalDuration = rs.getInt("total_duration"),
                totalCalories = rs.getBigDecimal("total_calories") ?: BigDecimal.ZERO,
                workoutTypes = types
            )
        }, profileId, startDate, endDate)
    }

    /**
     * Get workout calories for a date
     */
    fun getWorkoutCaloriesForDate(profileId: UUID, date: LocalDate): Int {
        val sql = """
            SELECT COALESCE(SUM(COALESCE(calories_burned_actual, calories_burned_estimated)), 0) as total
            FROM workouts
            WHERE profile_id = ? AND date = ?
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, Int::class.java, profileId, date) ?: 0
    }

    /**
     * Count workouts for a date range
     */
    fun countWorkouts(profileId: UUID, startDate: LocalDate, endDate: LocalDate): Int {
        val sql = """
            SELECT COUNT(*) FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, Int::class.java, profileId, startDate, endDate) ?: 0
    }

    /**
     * Get workout type distribution
     */
    fun getWorkoutTypeDistribution(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Pair<String, Int>> {
        val sql = """
            SELECT workout_type::text as type, COUNT(*) as count
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
            GROUP BY workout_type
            ORDER BY count DESC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            Pair(rs.getString("type"), rs.getInt("count"))
        }, profileId, startDate, endDate)
    }

    /**
     * Get workout calories by type
     */
    fun getWorkoutCaloriesByType(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Pair<String, Int>> {
        val sql = """
            SELECT workout_type::text as type,
                   COALESCE(SUM(COALESCE(calories_burned_actual, calories_burned_estimated)), 0)::int as calories
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
            GROUP BY workout_type
            ORDER BY calories DESC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            Pair(rs.getString("type"), rs.getInt("calories"))
        }, profileId, startDate, endDate)
    }

    // ========== Streak Queries ==========

    /**
     * Get dates with any meal logged (for logging streak)
     */
    fun getDatesWithMeals(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        val sql = """
            SELECT DISTINCT d.date
            FROM days d
            INNER JOIN meals m ON d.id = m.day_id
            INNER JOIN meal_items mi ON m.id = mi.meal_id
            WHERE d.profile_id = ? AND d.date >= ? AND d.date <= ?
            ORDER BY d.date ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            rs.getDate("date").toLocalDate()
        }, profileId, startDate, endDate)
    }

    /**
     * Get dates with workouts (for workout streak)
     */
    fun getDatesWithWorkouts(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        val sql = """
            SELECT DISTINCT date
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
            ORDER BY date ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            rs.getDate("date").toLocalDate()
        }, profileId, startDate, endDate)
    }

    /**
     * Count cheat meals in date range
     */
    fun countCheatMeals(profileId: UUID, startDate: LocalDate, endDate: LocalDate): Int {
        val sql = """
            SELECT COUNT(*) FROM meals m
            JOIN days d ON m.day_id = d.id
            WHERE d.profile_id = ? AND d.date >= ? AND d.date <= ? AND m.is_cheat_meal = true
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, Int::class.java, profileId, startDate, endDate) ?: 0
    }

    /**
     * Get last cheat meal date
     */
    fun getLastCheatMealDate(profileId: UUID): LocalDate? {
        val sql = """
            SELECT d.date FROM meals m
            JOIN days d ON m.day_id = d.id
            WHERE d.profile_id = ? AND m.is_cheat_meal = true
            ORDER BY d.date DESC LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            rs.getDate("date").toLocalDate()
        }, profileId).firstOrNull()
    }

    // ========== Aggregation Helpers ==========

    /**
     * Get aggregated weight data by period (uses last value of period)
     */
    fun getAggregatedWeightData(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        period: AggregationPeriod
    ): List<Pair<LocalDate, BigDecimal>> {
        val sql = when (period) {
            AggregationPeriod.WEEKLY -> """
                WITH ranked AS (
                    SELECT date, weight_kg,
                           date_trunc('week', date) as period_start,
                           ROW_NUMBER() OVER (PARTITION BY date_trunc('week', date) ORDER BY date DESC) as rn
                    FROM body_metrics
                    WHERE profile_id = ? AND date >= ? AND date <= ? AND weight_kg IS NOT NULL
                )
                SELECT period_start::date as period, weight_kg
                FROM ranked
                WHERE rn = 1
                ORDER BY period_start
            """.trimIndent()
            AggregationPeriod.MONTHLY -> """
                WITH ranked AS (
                    SELECT date, weight_kg,
                           date_trunc('month', date) as period_start,
                           ROW_NUMBER() OVER (PARTITION BY date_trunc('month', date) ORDER BY date DESC) as rn
                    FROM body_metrics
                    WHERE profile_id = ? AND date >= ? AND date <= ? AND weight_kg IS NOT NULL
                )
                SELECT period_start::date as period, weight_kg
                FROM ranked
                WHERE rn = 1
                ORDER BY period_start
            """.trimIndent()
            AggregationPeriod.DAILY -> """
                SELECT date as period, weight_kg
                FROM body_metrics
                WHERE profile_id = ? AND date >= ? AND date <= ? AND weight_kg IS NOT NULL
                ORDER BY date
            """.trimIndent()
        }

        return jdbcTemplate.query(sql, { rs, _ ->
            Pair(rs.getDate("period").toLocalDate(), rs.getBigDecimal("weight_kg"))
        }, profileId, startDate, endDate)
    }

    /**
     * Get aggregated calorie data by period (sums values)
     */
    fun getAggregatedCalorieData(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        period: AggregationPeriod
    ): List<Triple<LocalDate, BigDecimal, Int>> {
        val dateTrunc = when (period) {
            AggregationPeriod.WEEKLY -> "date_trunc('week', d.date)"
            AggregationPeriod.MONTHLY -> "date_trunc('month', d.date)"
            AggregationPeriod.DAILY -> "d.date"
        }

        val sql = """
            SELECT
                $dateTrunc::date as period,
                COALESCE(SUM(m.total_calories), 0) as total_calories,
                COUNT(DISTINCT d.date) as days_count
            FROM days d
            LEFT JOIN meals m ON d.id = m.day_id
            WHERE d.profile_id = ? AND d.date >= ? AND d.date <= ?
            GROUP BY $dateTrunc
            ORDER BY period
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            Triple(
                rs.getDate("period").toLocalDate(),
                rs.getBigDecimal("total_calories") ?: BigDecimal.ZERO,
                rs.getInt("days_count")
            )
        }, profileId, startDate, endDate)
    }

    // ========== Dashboard Helpers ==========

    /**
     * Check if a day has any logged data
     */
    fun hasDataForDate(profileId: UUID, date: LocalDate): Boolean {
        val sql = """
            SELECT EXISTS(
                SELECT 1 FROM days d
                JOIN meals m ON d.id = m.day_id
                JOIN meal_items mi ON m.id = mi.meal_id
                WHERE d.profile_id = ? AND d.date = ?
            ) OR EXISTS(
                SELECT 1 FROM body_metrics
                WHERE profile_id = ? AND date = ?
            ) OR EXISTS(
                SELECT 1 FROM workouts
                WHERE profile_id = ? AND date = ?
            )
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, Boolean::class.java,
            profileId, date, profileId, date, profileId, date) ?: false
    }

    /**
     * Get week summary data
     */
    fun getWeekSummary(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> {
        val caloriesSql = """
            SELECT
                COALESCE(AVG(daily_total), 0) as avg_calories,
                COUNT(DISTINCT date) as days_with_data
            FROM (
                SELECT d.date, SUM(m.total_calories) as daily_total
                FROM days d
                JOIN meals m ON d.id = m.day_id
                WHERE d.profile_id = ? AND d.date >= ? AND d.date <= ?
                GROUP BY d.date
            ) daily
        """.trimIndent()

        val result = mutableMapOf<String, Any>()

        jdbcTemplate.query(caloriesSql, { rs, _ ->
            result["avgCalories"] = rs.getBigDecimal("avg_calories")?.toInt() ?: 0
            result["daysWithData"] = rs.getInt("days_with_data")
        }, profileId, startDate, endDate)

        result["workouts"] = countWorkouts(profileId, startDate, endDate)
        result["cheatMeals"] = countCheatMeals(profileId, startDate, endDate)

        return result
    }
}
