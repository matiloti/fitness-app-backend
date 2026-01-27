package com.fittrack.repository

import com.fittrack.model.ActivityLevel
import com.fittrack.model.BodyMetric
import com.fittrack.model.Day
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class DayRepository(private val jdbcTemplate: JdbcTemplate) {

    private val dayRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Day(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            date = rs.getDate("date").toLocalDate(),
            activityLevelOverride = rs.getString("activity_level_override")?.let { ActivityLevel.valueOf(it) },
            notes = rs.getString("notes"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private val bodyMetricRowMapper = RowMapper { rs: ResultSet, _: Int ->
        BodyMetric(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            date = rs.getDate("date").toLocalDate(),
            weightKg = rs.getBigDecimal("weight_kg"),
            bodyFatPercentage = rs.getBigDecimal("body_fat_percentage"),
            bodyFatKg = rs.getBigDecimal("body_fat_kg"),
            muscleMassPercentage = rs.getBigDecimal("muscle_mass_percentage"),
            muscleMassKg = rs.getBigDecimal("muscle_mass_kg"),
            notes = rs.getString("notes"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun findById(id: UUID): Day? {
        val sql = "SELECT * FROM days WHERE id = ?"
        return jdbcTemplate.query(sql, dayRowMapper, id).firstOrNull()
    }

    fun findByProfileIdAndDate(profileId: UUID, date: LocalDate): Day? {
        val sql = "SELECT * FROM days WHERE profile_id = ? AND date = ?"
        return jdbcTemplate.query(sql, dayRowMapper, profileId, date).firstOrNull()
    }

    fun findByProfileIdAndDateRange(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<Day> {
        val sql = """
            SELECT * FROM days
            WHERE profile_id = ? AND date >= ? AND date <= ?
            ORDER BY date
        """.trimIndent()
        return jdbcTemplate.query(sql, dayRowMapper, profileId, startDate, endDate)
    }

    fun findByIdAndProfileId(id: UUID, profileId: UUID): Day? {
        val sql = "SELECT * FROM days WHERE id = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, dayRowMapper, id, profileId).firstOrNull()
    }

    fun create(profileId: UUID, date: LocalDate): Day {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO days (id, profile_id, date, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, dayRowMapper, id, profileId, date, Timestamp.from(now), Timestamp.from(now)).first()
    }

    fun findOrCreate(profileId: UUID, date: LocalDate): Day {
        return findByProfileIdAndDate(profileId, date) ?: create(profileId, date)
    }

    fun updateActivityLevelOverride(id: UUID, activityLevel: ActivityLevel?): Day? {
        val sql = """
            UPDATE days
            SET activity_level_override = ?::activity_level
            WHERE id = ?
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, dayRowMapper, activityLevel?.name, id).firstOrNull()
    }

    fun delete(id: UUID): Boolean {
        val sql = "DELETE FROM days WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    // ========== Body Metrics Methods ==========

    fun findBodyMetricByProfileIdAndDate(profileId: UUID, date: LocalDate): BodyMetric? {
        val sql = "SELECT * FROM body_metrics WHERE profile_id = ? AND date = ?"
        return jdbcTemplate.query(sql, bodyMetricRowMapper, profileId, date).firstOrNull()
    }

    fun findLatestBodyMetric(profileId: UUID, beforeDate: LocalDate? = null): BodyMetric? {
        val sql = if (beforeDate != null) {
            "SELECT * FROM body_metrics WHERE profile_id = ? AND date <= ? ORDER BY date DESC LIMIT 1"
        } else {
            "SELECT * FROM body_metrics WHERE profile_id = ? ORDER BY date DESC LIMIT 1"
        }

        return if (beforeDate != null) {
            jdbcTemplate.query(sql, bodyMetricRowMapper, profileId, beforeDate).firstOrNull()
        } else {
            jdbcTemplate.query(sql, bodyMetricRowMapper, profileId).firstOrNull()
        }
    }

    fun hasBodyMetricsForDate(profileId: UUID, date: LocalDate): Boolean {
        val sql = "SELECT COUNT(*) FROM body_metrics WHERE profile_id = ? AND date = ?"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, profileId, date) ?: 0) > 0
    }

    fun hasProgressPhotosForMetric(bodyMetricId: UUID): Boolean {
        val sql = "SELECT COUNT(*) FROM progress_photos WHERE body_metrics_id = ?"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, bodyMetricId) ?: 0) > 0
    }

    // ========== Day Navigation Methods ==========

    fun findFirstDayWithData(profileId: UUID): LocalDate? {
        val sql = """
            SELECT MIN(date) FROM (
                SELECT date FROM days WHERE profile_id = ?
                UNION
                SELECT date FROM body_metrics WHERE profile_id = ?
            ) dates
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, LocalDate::class.java, profileId, profileId)
    }

    fun findLastDayWithData(profileId: UUID): LocalDate? {
        val sql = """
            SELECT MAX(date) FROM (
                SELECT date FROM days WHERE profile_id = ?
                UNION
                SELECT date FROM body_metrics WHERE profile_id = ?
            ) dates
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, LocalDate::class.java, profileId, profileId)
    }

    fun hasMealsForDate(profileId: UUID, date: LocalDate): Boolean {
        val sql = """
            SELECT COUNT(*) FROM meals m
            JOIN days d ON m.day_id = d.id
            WHERE d.profile_id = ? AND d.date = ?
        """.trimIndent()
        return (jdbcTemplate.queryForObject(sql, Int::class.java, profileId, date) ?: 0) > 0
    }
}
