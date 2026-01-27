package com.fittrack.service

import com.fittrack.config.JwtConfig
import com.fittrack.exception.TokenExpiredException
import com.fittrack.exception.TokenInvalidException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtConfig: JwtConfig) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Generate an access token for the given user ID and email
     */
    fun generateAccessToken(userId: UUID, email: String): String {
        val now = Instant.now()
        val expiry = now.plusMillis(jwtConfig.accessTokenExpiry)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey)
            .compact()
    }

    /**
     * Generate a refresh token for the given user ID
     * Returns pair of (token, jti)
     */
    fun generateRefreshToken(userId: UUID): Pair<String, String> {
        val now = Instant.now()
        val expiry = now.plusMillis(jwtConfig.refreshTokenExpiry)
        val jti = UUID.randomUUID().toString()

        val token = Jwts.builder()
            .subject(userId.toString())
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey)
            .compact()

        return token to jti
    }

    /**
     * Validate and parse an access token
     */
    fun validateAccessToken(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            logger.debug("Access token expired")
            throw TokenExpiredException("Access token has expired")
        } catch (e: JwtException) {
            logger.warn("Invalid access token: ${e.message}")
            throw TokenInvalidException("Invalid access token")
        }
    }

    /**
     * Validate and parse a refresh token
     */
    fun validateRefreshToken(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            logger.debug("Refresh token expired")
            throw TokenExpiredException("Refresh token has expired")
        } catch (e: JwtException) {
            logger.warn("Invalid refresh token: ${e.message}")
            throw TokenInvalidException("Invalid refresh token")
        }
    }

    /**
     * Extract user ID from token claims
     */
    fun getUserIdFromClaims(claims: Claims): UUID {
        return UUID.fromString(claims.subject)
    }

    /**
     * Extract email from token claims
     */
    fun getEmailFromClaims(claims: Claims): String {
        return claims["email"] as String
    }

    /**
     * Extract JTI (token ID) from refresh token claims
     */
    fun getJtiFromClaims(claims: Claims): String {
        return claims.id
    }

    /**
     * Get access token expiry in seconds
     */
    fun getAccessTokenExpirySeconds(): Long = jwtConfig.accessTokenExpiry / 1000

    /**
     * Get refresh token expiry instant
     */
    fun getRefreshTokenExpiry(): Instant = Instant.now().plusMillis(jwtConfig.refreshTokenExpiry)

    /**
     * Hash a token for secure storage
     */
    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
