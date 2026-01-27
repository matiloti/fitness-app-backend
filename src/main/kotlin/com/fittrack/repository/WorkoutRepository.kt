package com.fittrack.repository

import com.fittrack.model.Workout
import com.fittrack.model.WorkoutType
import com.fittrack.model.WorkoutTypeMet
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
class WorkoutRepository(private val jdbcTemplate: JdbcTemplate) {

    private val workoutRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Workout(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            date = rs.getDate("date").toLocalDate(),
            workoutType = WorkoutType.valueOf(rs.getString("workout_type")),
            name = rs.getString("name"),
            durationMinutes = rs.getInt("duration_minutes"),
            caloriesBurnedEstimated = rs.getBigDecimal("calories_burned_estimated"),
            caloriesBurnedActual = rs.getBigDecimal("calories_burned_actual"),
            notes = rs.getString("notes"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private val metRowMapper = RowMapper { rs: ResultSet, _: Int ->
        WorkoutTypeMet(
            workoutType = WorkoutType.valueOf(rs.getString("workout_type")),
            metValue = rs.getBigDecimal("met_value"),
            description = rs.getString("description")
        )
    }

    // ========== Workout CRUD ==========

    fun findById(id: UUID): Workout? {
        val sql = "SELECT * FROM workouts WHERE id = ?"
        return jdbcTemplate.query(sql, workoutRowMapper, id).firstOrNull()
    }

    fun findByIdAndProfileId(id: UUID, profileId: UUID): Workout? {
        val sql = "SELECT * FROM workouts WHERE id = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, workoutRowMapper, id, profileId).firstOrNull()
    }

    fun findAllByProfileId(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        workoutType: WorkoutType? = null,
        page: Int,
        size: Int
    ): List<Workout> {
        val conditions = mutableListOf("profile_id = ?", "date >= ?", "date <= ?")
        val params = mutableListOf<Any>(profileId, startDate, endDate)

        workoutType?.let {
            conditions.add("workout_type = ?::workout_type")
            params.add(it.name)
        }

        val sql = """
            SELECT * FROM workouts
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY date DESC, created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        params.add(size)
        params.add(page * size)

        return jdbcTemplate.query(sql, workoutRowMapper, *params.toTypedArray())
    }

    fun countByProfileId(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        workoutType: WorkoutType? = null
    ): Long {
        val conditions = mutableListOf("profile_id = ?", "date >= ?", "date <= ?")
        val params = mutableListOf<Any>(profileId, startDate, endDate)

        workoutType?.let {
            conditions.add("workout_type = ?::workout_type")
            params.add(it.name)
        }

        val sql = "SELECT COUNT(*) FROM workouts WHERE ${conditions.joinToString(" AND ")}"
        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()) ?: 0L
    }

    fun findByDateAndProfileId(date: LocalDate, profileId: UUID): List<Workout> {
        val sql = """
            SELECT * FROM workouts
            WHERE profile_id = ? AND date = ?
            ORDER BY created_at DESC
        """.trimIndent()
        return jdbcTemplate.query(sql, workoutRowMapper, profileId, date)
    }

    fun getSummaryStats(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> {
        val sql = """
            SELECT
                COUNT(*) as total_workouts,
                COALESCE(SUM(duration_minutes), 0) as total_duration,
                COALESCE(SUM(COALESCE(calories_burned_actual, calories_burned_estimated)), 0) as total_calories,
                COALESCE(AVG(duration_minutes), 0) as avg_duration,
                COALESCE(AVG(COALESCE(calories_burned_actual, calories_burned_estimated)), 0) as avg_calories
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
        """.trimIndent()

        return jdbcTemplate.queryForMap(sql, profileId, startDate, endDate)
    }

    fun create(
        profileId: UUID,
        date: LocalDate,
        workoutType: WorkoutType,
        name: String?,
        durationMinutes: Int,
        caloriesBurnedEstimated: BigDecimal?,
        caloriesBurnedActual: BigDecimal?,
        notes: String?
    ): Workout {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO workouts (
                id, profile_id, date, workout_type, name, duration_minutes,
                calories_burned_estimated, calories_burned_actual, notes, created_at, updated_at
            ) VALUES (?, ?, ?, ?::workout_type, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, workoutRowMapper,
            id, profileId, date, workoutType.name, name, durationMinutes,
            caloriesBurnedEstimated, caloriesBurnedActual, notes, Timestamp.from(now), Timestamp.from(now)
        ).first()
    }

    fun update(
        id: UUID,
        workoutType: WorkoutType?,
        name: String?,
        durationMinutes: Int?,
        caloriesBurnedEstimated: BigDecimal?,
        caloriesBurnedActual: BigDecimal?,
        notes: String?,
        updateWorkoutType: Boolean = false,
        updateName: Boolean = false,
        updateDuration: Boolean = false,
        updateEstimated: Boolean = false,
        updateActual: Boolean = false,
        updateNotes: Boolean = false
    ): Workout? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        if (updateWorkoutType && workoutType != null) {
            updates.add("workout_type = ?::workout_type")
            params.add(workoutType.name)
        }

        if (updateName) {
            updates.add("name = ?")
            params.add(name)
        }

        if (updateDuration && durationMinutes != null) {
            updates.add("duration_minutes = ?")
            params.add(durationMinutes)
        }

        if (updateEstimated) {
            updates.add("calories_burned_estimated = ?")
            params.add(caloriesBurnedEstimated)
        }

        if (updateActual) {
            updates.add("calories_burned_actual = ?")
            params.add(caloriesBurnedActual)
        }

        if (updateNotes) {
            updates.add("notes = ?")
            params.add(notes)
        }

        if (updates.isEmpty()) {
            return findById(id)
        }

        params.add(id)
        val sql = "UPDATE workouts SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, workoutRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun delete(id: UUID): Boolean {
        val sql = "DELETE FROM workouts WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    // ========== MET Values ==========

    fun findAllMets(): List<WorkoutTypeMet> {
        val sql = "SELECT * FROM workout_type_mets ORDER BY workout_type"
        return jdbcTemplate.query(sql, metRowMapper)
    }

    fun findMetByType(workoutType: WorkoutType): WorkoutTypeMet? {
        val sql = "SELECT * FROM workout_type_mets WHERE workout_type = ?::workout_type"
        return jdbcTemplate.query(sql, metRowMapper, workoutType.name).firstOrNull()
    }

    // ========== Analytics/Summary Methods ==========

    fun getWorkoutsByType(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Map<String, Any>> {
        val sql = """
            SELECT
                workout_type,
                COUNT(*) as count,
                SUM(duration_minutes) as total_duration,
                SUM(COALESCE(calories_burned_actual, calories_burned_estimated)) as total_calories
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
            GROUP BY workout_type
            ORDER BY count DESC
        """.trimIndent()

        return jdbcTemplate.queryForList(sql, profileId, startDate, endDate)
    }

    fun getWeeklyTrend(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Map<String, Any>> {
        val sql = """
            SELECT
                DATE_TRUNC('week', date)::date as week_start,
                COUNT(*) as workouts,
                SUM(COALESCE(calories_burned_actual, calories_burned_estimated)) as calories
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
            GROUP BY DATE_TRUNC('week', date)
            ORDER BY week_start
        """.trimIndent()

        return jdbcTemplate.queryForList(sql, profileId, startDate, endDate)
    }

    fun getMaxStats(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any?> {
        val sql = """
            SELECT
                MAX(duration_minutes) as longest_workout,
                MAX(COALESCE(calories_burned_actual, calories_burned_estimated)) as most_calories
            FROM workouts
            WHERE profile_id = ? AND date >= ? AND date <= ?
        """.trimIndent()

        return jdbcTemplate.queryForMap(sql, profileId, startDate, endDate)
    }
}
