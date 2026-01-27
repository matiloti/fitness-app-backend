package com.fittrack.repository

import com.fittrack.model.PasswordResetToken
import com.fittrack.model.RefreshToken
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class RefreshTokenRepository(private val jdbcTemplate: JdbcTemplate) {

    private val refreshTokenRowMapper = RowMapper { rs: ResultSet, _: Int ->
        RefreshToken(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            tokenHash = rs.getString("token_hash"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            revokedAt = rs.getTimestamp("revoked_at")?.toInstant()
        )
    }

    private val passwordResetTokenRowMapper = RowMapper { rs: ResultSet, _: Int ->
        PasswordResetToken(
            id = UUID.fromString(rs.getString("id")),
            profileId = UUID.fromString(rs.getString("profile_id")),
            tokenHash = rs.getString("token_hash"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            usedAt = rs.getTimestamp("used_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    // ========== Refresh Token Methods ==========

    fun create(profileId: UUID, tokenHash: String, expiresAt: Instant): RefreshToken {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO refresh_tokens (id, profile_id, token_hash, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, refreshTokenRowMapper, id, profileId, tokenHash, expiresAt, now).first()
    }

    fun findByTokenHash(tokenHash: String): RefreshToken? {
        val sql = "SELECT * FROM refresh_tokens WHERE token_hash = ?"
        return jdbcTemplate.query(sql, refreshTokenRowMapper, tokenHash).firstOrNull()
    }

    fun findValidByTokenHash(tokenHash: String): RefreshToken? {
        val sql = """
            SELECT * FROM refresh_tokens
            WHERE token_hash = ?
              AND expires_at > NOW()
              AND revoked_at IS NULL
        """.trimIndent()
        return jdbcTemplate.query(sql, refreshTokenRowMapper, tokenHash).firstOrNull()
    }

    fun revokeToken(tokenHash: String): Boolean {
        val sql = "UPDATE refresh_tokens SET revoked_at = NOW() WHERE token_hash = ? AND revoked_at IS NULL"
        return jdbcTemplate.update(sql, tokenHash) > 0
    }

    fun revokeAllForUser(profileId: UUID): Int {
        val sql = "UPDATE refresh_tokens SET revoked_at = NOW() WHERE profile_id = ? AND revoked_at IS NULL"
        return jdbcTemplate.update(sql, profileId)
    }

    fun deleteExpiredTokens(): Int {
        val sql = "DELETE FROM refresh_tokens WHERE expires_at < NOW()"
        return jdbcTemplate.update(sql)
    }

    // ========== Password Reset Token Methods ==========

    fun createPasswordResetToken(profileId: UUID, tokenHash: String, expiresAt: Instant): PasswordResetToken {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val sql = """
            INSERT INTO password_reset_tokens (id, profile_id, token_hash, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING *
        """.trimIndent()

        return jdbcTemplate.query(sql, passwordResetTokenRowMapper, id, profileId, tokenHash, expiresAt, now).first()
    }

    fun findPasswordResetByTokenHash(tokenHash: String): PasswordResetToken? {
        val sql = "SELECT * FROM password_reset_tokens WHERE token_hash = ?"
        return jdbcTemplate.query(sql, passwordResetTokenRowMapper, tokenHash).firstOrNull()
    }

    fun findValidPasswordResetByTokenHash(tokenHash: String): PasswordResetToken? {
        val sql = """
            SELECT * FROM password_reset_tokens
            WHERE token_hash = ?
              AND expires_at > NOW()
              AND used_at IS NULL
        """.trimIndent()
        return jdbcTemplate.query(sql, passwordResetTokenRowMapper, tokenHash).firstOrNull()
    }

    fun markPasswordResetAsUsed(tokenHash: String): Boolean {
        val sql = "UPDATE password_reset_tokens SET used_at = NOW() WHERE token_hash = ? AND used_at IS NULL"
        return jdbcTemplate.update(sql, tokenHash) > 0
    }

    fun countRecentPasswordResetRequests(profileId: UUID, since: Instant): Int {
        val sql = """
            SELECT COUNT(*) FROM password_reset_tokens
            WHERE profile_id = ? AND created_at > ?
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, Int::class.java, profileId, since) ?: 0
    }

    fun deleteExpiredPasswordResetTokens(): Int {
        val sql = "DELETE FROM password_reset_tokens WHERE expires_at < NOW()"
        return jdbcTemplate.update(sql)
    }
}
