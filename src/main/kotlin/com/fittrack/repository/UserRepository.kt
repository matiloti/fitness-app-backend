package com.fittrack.repository

import com.fittrack.model.ActivityLevel
import com.fittrack.model.Country
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import com.fittrack.model.User
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
class UserRepository(private val jdbcTemplate: JdbcTemplate) {

    private val userRowMapper = RowMapper { rs: ResultSet, _: Int ->
        User(
            id = UUID.fromString(rs.getString("id")),
            email = rs.getString("email"),
            passwordHash = rs.getString("password_hash"),
            name = rs.getString("name"),
            photoUrl = rs.getString("photo_url"),
            countryId = rs.getObject("country_id") as? Short,
            dateOfBirth = rs.getObject("date_of_birth", LocalDate::class.java),
            sex = rs.getString("sex")?.let { Sex.valueOf(it) },
            heightCm = rs.getBigDecimal("height_cm"),
            defaultActivityLevel = ActivityLevel.valueOf(rs.getString("default_activity_level")),
            fitnessGoalType = FitnessGoalType.valueOf(rs.getString("fitness_goal_type")),
            fitnessGoalIntensity = rs.getString("fitness_goal_intensity")?.let { FitnessGoalIntensity.valueOf(it) },
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private val countryRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Country(
            id = rs.getShort("id"),
            code = rs.getString("code").trim(),
            name = rs.getString("name")
        )
    }

    fun findById(id: UUID): User? {
        val sql = "SELECT * FROM profiles WHERE id = ?"
        return jdbcTemplate.query(sql, userRowMapper, id).firstOrNull()
    }

    fun findByEmail(email: String): User? {
        val sql = "SELECT * FROM profiles WHERE email = ?"
        return jdbcTemplate.query(sql, userRowMapper, email.lowercase()).firstOrNull()
    }

    fun existsByEmail(email: String): Boolean {
        val sql = "SELECT COUNT(*) FROM profiles WHERE email = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, email.lowercase())!! > 0
    }

    fun create(
        email: String,
        passwordHash: String,
        name: String
    ): User {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, userRowMapper, id, email.lowercase(), passwordHash, name, Timestamp.from(now), Timestamp.from(now)).first()
    }

    fun updatePassword(userId: UUID, newPasswordHash: String): Boolean {
        val sql = "UPDATE profiles SET password_hash = ? WHERE id = ?"
        return jdbcTemplate.update(sql, newPasswordHash, userId) > 0
    }

    fun updateEmail(userId: UUID, newEmail: String): Boolean {
        val sql = "UPDATE profiles SET email = ? WHERE id = ?"
        return jdbcTemplate.update(sql, newEmail.lowercase(), userId) > 0
    }

    fun updateProfile(userId: UUID, name: String?, photoUrl: String?, countryId: Short?): User? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        name?.let {
            updates.add("name = ?")
            params.add(it)
        }
        photoUrl?.let {
            updates.add("photo_url = ?")
            params.add(it)
        }
        countryId?.let {
            updates.add("country_id = ?")
            params.add(it)
        }

        if (updates.isEmpty()) {
            return findById(userId)
        }

        params.add(userId)
        val sql = "UPDATE profiles SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, userRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun updateMetrics(userId: UUID, dateOfBirth: LocalDate, sex: Sex, heightCm: BigDecimal): User? {
        val sql = """
            UPDATE profiles
            SET date_of_birth = ?, sex = ?::sex_type, height_cm = ?
            WHERE id = ?
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, userRowMapper, dateOfBirth, sex.name, heightCm, userId).firstOrNull()
    }

    fun updateActivityLevel(userId: UUID, activityLevel: ActivityLevel): User? {
        val sql = """
            UPDATE profiles
            SET default_activity_level = ?::activity_level
            WHERE id = ?
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, userRowMapper, activityLevel.name, userId).firstOrNull()
    }

    fun updateFitnessGoal(userId: UUID, goalType: FitnessGoalType, intensity: FitnessGoalIntensity?): User? {
        val sql = """
            UPDATE profiles
            SET fitness_goal_type = ?::fitness_goal_type,
                fitness_goal_intensity = ?::fitness_goal_intensity
            WHERE id = ?
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, userRowMapper, goalType.name, intensity?.name, userId).firstOrNull()
    }

    // Country methods
    fun findCountryById(id: Short): Country? {
        val sql = "SELECT * FROM countries WHERE id = ?"
        return jdbcTemplate.query(sql, countryRowMapper, id).firstOrNull()
    }

    fun findCountryByCode(code: String): Country? {
        val sql = "SELECT * FROM countries WHERE code = ?"
        return jdbcTemplate.query(sql, countryRowMapper, code.uppercase()).firstOrNull()
    }

    fun findAllCountries(): List<Country> {
        val sql = "SELECT * FROM countries ORDER BY name"
        return jdbcTemplate.query(sql, countryRowMapper)
    }

    fun searchCountries(search: String): List<Country> {
        val sql = "SELECT * FROM countries WHERE LOWER(name) LIKE ? ORDER BY name"
        return jdbcTemplate.query(sql, countryRowMapper, "%${search.lowercase()}%")
    }
}
