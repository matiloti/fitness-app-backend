package com.fittrack.repository

import com.fittrack.model.Brand
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class BrandRepository(private val jdbcTemplate: JdbcTemplate) {

    private val brandRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Brand(
            id = rs.getInt("id"),
            profileId = UUID.fromString(rs.getString("profile_id")),
            name = rs.getString("name"),
            description = rs.getString("description"),
            photoUrl = rs.getString("photo_url"),
            countryId = rs.getObject("country_id") as? Short,
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun findById(id: Int): Brand? {
        val sql = "SELECT * FROM brands WHERE id = ?"
        return jdbcTemplate.query(sql, brandRowMapper, id).firstOrNull()
    }

    fun findByIdAndProfileId(id: Int, profileId: UUID): Brand? {
        val sql = "SELECT * FROM brands WHERE id = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, brandRowMapper, id, profileId).firstOrNull()
    }

    fun findAllByProfileId(
        profileId: UUID,
        page: Int,
        size: Int,
        search: String? = null
    ): List<Brand> {
        val conditions = mutableListOf("profile_id = ?")
        val params = mutableListOf<Any>(profileId)

        search?.let {
            if (it.isNotBlank()) {
                conditions.add("(name ILIKE ? OR name % ?)")
                params.add("%$it%")
                params.add(it)
            }
        }

        val sql = """
            SELECT * FROM brands
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY name ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        params.add(size)
        params.add(page * size)

        return jdbcTemplate.query(sql, brandRowMapper, *params.toTypedArray())
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

        val sql = "SELECT COUNT(*) FROM brands WHERE ${conditions.joinToString(" AND ")}"
        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()) ?: 0L
    }

    fun existsByProfileIdAndName(profileId: UUID, name: String): Boolean {
        val sql = "SELECT COUNT(*) FROM brands WHERE profile_id = ? AND LOWER(name) = LOWER(?)"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, profileId, name) ?: 0) > 0
    }

    fun existsByProfileIdAndNameExcludingId(profileId: UUID, name: String, excludeId: Int): Boolean {
        val sql = "SELECT COUNT(*) FROM brands WHERE profile_id = ? AND LOWER(name) = LOWER(?) AND id != ?"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, profileId, name, excludeId) ?: 0) > 0
    }

    fun create(
        profileId: UUID,
        name: String,
        description: String?,
        photoUrl: String?,
        countryId: Short?
    ): Brand {
        val now = Instant.now()

        val sql = """
            INSERT INTO brands (profile_id, name, description, photo_url, country_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, brandRowMapper,
            profileId, name, description, photoUrl, countryId, now, now
        ).first()
    }

    fun update(
        id: Int,
        name: String?,
        description: String?,
        photoUrl: String?,
        countryId: Short?
    ): Brand? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        name?.let {
            updates.add("name = ?")
            params.add(it)
        }

        // Allow setting description to null by checking if it was explicitly included
        if (description != null) {
            updates.add("description = ?")
            params.add(description)
        }

        if (photoUrl != null) {
            updates.add("photo_url = ?")
            params.add(photoUrl)
        }

        if (countryId != null) {
            updates.add("country_id = ?")
            params.add(countryId)
        }

        if (updates.isEmpty()) {
            return findById(id)
        }

        params.add(id)
        val sql = "UPDATE brands SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, brandRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun delete(id: Int): Boolean {
        val sql = "DELETE FROM brands WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }
}
