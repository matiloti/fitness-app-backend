package com.fittrack.model

import java.time.Instant
import java.util.UUID

/**
 * Refresh token entity - maps to refresh_tokens table
 */
data class RefreshToken(
    val id: UUID,
    val profileId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val revokedAt: Instant? = null
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    fun isRevoked(): Boolean = revokedAt != null
    fun isValid(): Boolean = !isExpired() && !isRevoked()
}

/**
 * Password reset token entity - maps to password_reset_tokens table
 */
data class PasswordResetToken(
    val id: UUID,
    val profileId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
    val createdAt: Instant
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    fun isUsed(): Boolean = usedAt != null
    fun isValid(): Boolean = !isExpired() && !isUsed()
}
