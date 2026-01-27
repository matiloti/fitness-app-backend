package com.fittrack.repository

import com.fittrack.model.BodyMetrics
import com.fittrack.model.PhotoPosition
import com.fittrack.model.ProgressPhoto
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class BodyMetricsRepository(private val jdbcTemplate: JdbcTemplate) {

    private val bodyMetricsRowMapper = RowMapper { rs: ResultSet, _: Int ->
        BodyMetrics(
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

    private val photoRowMapper = RowMapper { rs: ResultSet, _: Int ->
        ProgressPhoto(
            id = UUID.fromString(rs.getString("id")),
            bodyMetricsId = UUID.fromString(rs.getString("body_metrics_id")),
            position = PhotoPosition.valueOf(rs.getString("position")),
            imageUrl = rs.getString("image_url"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    // ========== Body Metrics CRUD ==========

    fun findById(id: UUID): BodyMetrics? {
        val sql = "SELECT * FROM body_metrics WHERE id = ?"
        return jdbcTemplate.query(sql, bodyMetricsRowMapper, id).firstOrNull()
    }

    fun findByIdAndProfileId(id: UUID, profileId: UUID): BodyMetrics? {
        val sql = "SELECT * FROM body_metrics WHERE id = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, bodyMetricsRowMapper, id, profileId).firstOrNull()
    }

    fun findByDateAndProfileId(date: LocalDate, profileId: UUID): BodyMetrics? {
        val sql = "SELECT * FROM body_metrics WHERE date = ? AND profile_id = ?"
        return jdbcTemplate.query(sql, bodyMetricsRowMapper, date, profileId).firstOrNull()
    }

    fun findAllByProfileId(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        page: Int,
        size: Int,
        hasPhotos: Boolean? = null
    ): List<BodyMetrics> {
        val conditions = mutableListOf("bm.profile_id = ?", "bm.date >= ?", "bm.date <= ?")
        val params = mutableListOf<Any>(profileId, startDate, endDate)

        val sql = if (hasPhotos == true) {
            """
                SELECT DISTINCT bm.* FROM body_metrics bm
                INNER JOIN progress_photos pp ON bm.id = pp.body_metrics_id
                WHERE ${conditions.joinToString(" AND ")}
                ORDER BY bm.date DESC
                LIMIT ? OFFSET ?
            """.trimIndent()
        } else if (hasPhotos == false) {
            """
                SELECT bm.* FROM body_metrics bm
                LEFT JOIN progress_photos pp ON bm.id = pp.body_metrics_id
                WHERE ${conditions.joinToString(" AND ")} AND pp.id IS NULL
                ORDER BY bm.date DESC
                LIMIT ? OFFSET ?
            """.trimIndent()
        } else {
            """
                SELECT bm.* FROM body_metrics bm
                WHERE ${conditions.joinToString(" AND ")}
                ORDER BY bm.date DESC
                LIMIT ? OFFSET ?
            """.trimIndent()
        }

        params.add(size)
        params.add(page * size)

        return jdbcTemplate.query(sql, bodyMetricsRowMapper, *params.toTypedArray())
    }

    fun countByProfileId(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        hasPhotos: Boolean? = null
    ): Long {
        val conditions = mutableListOf("bm.profile_id = ?", "bm.date >= ?", "bm.date <= ?")
        val params = mutableListOf<Any>(profileId, startDate, endDate)

        val sql = if (hasPhotos == true) {
            """
                SELECT COUNT(DISTINCT bm.id) FROM body_metrics bm
                INNER JOIN progress_photos pp ON bm.id = pp.body_metrics_id
                WHERE ${conditions.joinToString(" AND ")}
            """.trimIndent()
        } else if (hasPhotos == false) {
            """
                SELECT COUNT(bm.id) FROM body_metrics bm
                LEFT JOIN progress_photos pp ON bm.id = pp.body_metrics_id
                WHERE ${conditions.joinToString(" AND ")} AND pp.id IS NULL
            """.trimIndent()
        } else {
            """
                SELECT COUNT(*) FROM body_metrics bm
                WHERE ${conditions.joinToString(" AND ")}
            """.trimIndent()
        }

        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()) ?: 0L
    }

    fun findLatestByProfileId(profileId: UUID): BodyMetrics? {
        val sql = "SELECT * FROM body_metrics WHERE profile_id = ? ORDER BY date DESC LIMIT 1"
        return jdbcTemplate.query(sql, bodyMetricsRowMapper, profileId).firstOrNull()
    }

    fun findForTrends(profileId: UUID, startDate: LocalDate, endDate: LocalDate): List<BodyMetrics> {
        val sql = """
            SELECT * FROM body_metrics
            WHERE profile_id = ? AND date >= ? AND date <= ?
            ORDER BY date ASC
        """.trimIndent()
        return jdbcTemplate.query(sql, bodyMetricsRowMapper, profileId, startDate, endDate)
    }

    fun create(
        profileId: UUID,
        date: LocalDate,
        weightKg: java.math.BigDecimal?,
        bodyFatPercentage: java.math.BigDecimal?,
        bodyFatKg: java.math.BigDecimal?,
        muscleMassPercentage: java.math.BigDecimal?,
        muscleMassKg: java.math.BigDecimal?,
        notes: String?
    ): BodyMetrics {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO body_metrics (
                id, profile_id, date, weight_kg, body_fat_percentage, body_fat_kg,
                muscle_mass_percentage, muscle_mass_kg, notes, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(
            sql, bodyMetricsRowMapper,
            id, profileId, date, weightKg, bodyFatPercentage, bodyFatKg,
            muscleMassPercentage, muscleMassKg, notes, Timestamp.from(now), Timestamp.from(now)
        ).first()
    }

    fun update(
        id: UUID,
        weightKg: java.math.BigDecimal?,
        bodyFatPercentage: java.math.BigDecimal?,
        bodyFatKg: java.math.BigDecimal?,
        muscleMassPercentage: java.math.BigDecimal?,
        muscleMassKg: java.math.BigDecimal?,
        notes: String?,
        updateWeight: Boolean = false,
        updateBodyFat: Boolean = false,
        updateMuscleMass: Boolean = false,
        updateNotes: Boolean = false
    ): BodyMetrics? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        if (updateWeight) {
            updates.add("weight_kg = ?")
            params.add(weightKg)
            // Also update calculated kg values if weight changes
            if (bodyFatKg != null || bodyFatPercentage != null) {
                updates.add("body_fat_kg = ?")
                params.add(bodyFatKg)
            }
            if (muscleMassKg != null || muscleMassPercentage != null) {
                updates.add("muscle_mass_kg = ?")
                params.add(muscleMassKg)
            }
        }

        if (updateBodyFat) {
            updates.add("body_fat_percentage = ?")
            params.add(bodyFatPercentage)
            updates.add("body_fat_kg = ?")
            params.add(bodyFatKg)
        }

        if (updateMuscleMass) {
            updates.add("muscle_mass_percentage = ?")
            params.add(muscleMassPercentage)
            updates.add("muscle_mass_kg = ?")
            params.add(muscleMassKg)
        }

        if (updateNotes) {
            updates.add("notes = ?")
            params.add(notes)
        }

        if (updates.isEmpty()) {
            return findById(id)
        }

        params.add(id)
        val sql = "UPDATE body_metrics SET ${updates.joinToString(", ")} WHERE id = ? RETURNING *"

        return jdbcTemplate.query(sql, bodyMetricsRowMapper, *params.toTypedArray()).firstOrNull()
    }

    fun delete(id: UUID): Boolean {
        val sql = "DELETE FROM body_metrics WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    // ========== Progress Photos ==========

    fun findPhotosByMetricsId(metricsId: UUID): List<ProgressPhoto> {
        val sql = "SELECT * FROM progress_photos WHERE body_metrics_id = ? ORDER BY position"
        return jdbcTemplate.query(sql, photoRowMapper, metricsId)
    }

    fun findPhotoById(id: UUID): ProgressPhoto? {
        val sql = "SELECT * FROM progress_photos WHERE id = ?"
        return jdbcTemplate.query(sql, photoRowMapper, id).firstOrNull()
    }

    fun findPhotoByIdAndMetricsId(id: UUID, metricsId: UUID): ProgressPhoto? {
        val sql = "SELECT * FROM progress_photos WHERE id = ? AND body_metrics_id = ?"
        return jdbcTemplate.query(sql, photoRowMapper, id, metricsId).firstOrNull()
    }

    fun findPhotoByMetricsIdAndPosition(metricsId: UUID, position: PhotoPosition): ProgressPhoto? {
        val sql = "SELECT * FROM progress_photos WHERE body_metrics_id = ? AND position = ?::photo_position"
        return jdbcTemplate.query(sql, photoRowMapper, metricsId, position.name).firstOrNull()
    }

    fun countPhotosByMetricsId(metricsId: UUID): Int {
        val sql = "SELECT COUNT(*) FROM progress_photos WHERE body_metrics_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, metricsId) ?: 0
    }

    fun createPhoto(
        metricsId: UUID,
        position: PhotoPosition,
        imageUrl: String
    ): ProgressPhoto {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO progress_photos (id, body_metrics_id, position, image_url, created_at)
            VALUES (?, ?, ?::photo_position, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, photoRowMapper, id, metricsId, position.name, imageUrl, Timestamp.from(now)).first()
    }

    fun updatePhoto(id: UUID, imageUrl: String): ProgressPhoto? {
        val sql = "UPDATE progress_photos SET image_url = ? WHERE id = ? RETURNING *"
        return jdbcTemplate.query(sql, photoRowMapper, imageUrl, id).firstOrNull()
    }

    fun deletePhoto(id: UUID): Boolean {
        val sql = "DELETE FROM progress_photos WHERE id = ?"
        return jdbcTemplate.update(sql, id) > 0
    }

    fun deletePhotosByMetricsId(metricsId: UUID): Int {
        val sql = "DELETE FROM progress_photos WHERE body_metrics_id = ?"
        return jdbcTemplate.update(sql, metricsId)
    }

    // ========== Photo Timeline ==========

    fun findPhotosForTimeline(
        profileId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        position: PhotoPosition? = null
    ): List<Triple<ProgressPhoto, LocalDate, java.math.BigDecimal?>> {
        val conditions = mutableListOf("bm.profile_id = ?", "bm.date >= ?", "bm.date <= ?")
        val params = mutableListOf<Any>(profileId, startDate, endDate)

        position?.let {
            conditions.add("pp.position = ?::photo_position")
            params.add(it.name)
        }

        val sql = """
            SELECT pp.*, bm.date as metrics_date, bm.weight_kg as metrics_weight_kg
            FROM progress_photos pp
            INNER JOIN body_metrics bm ON pp.body_metrics_id = bm.id
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY bm.date DESC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            val photo = ProgressPhoto(
                id = UUID.fromString(rs.getString("id")),
                bodyMetricsId = UUID.fromString(rs.getString("body_metrics_id")),
                position = PhotoPosition.valueOf(rs.getString("position")),
                imageUrl = rs.getString("image_url"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
            val date = rs.getDate("metrics_date").toLocalDate()
            val weightKg = rs.getBigDecimal("metrics_weight_kg")
            Triple(photo, date, weightKg)
        }, *params.toTypedArray())
    }
}
