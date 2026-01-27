package com.fittrack.service

import com.fittrack.config.JwtConfig
import com.fittrack.exception.TokenExpiredException
import com.fittrack.exception.TokenInvalidException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

@DisplayName("JWT Service Tests")
class JwtServiceTest {

    private lateinit var jwtService: JwtService
    private lateinit var jwtConfig: JwtConfig

    private val testSecret = "test-secret-key-for-jwt-service-unit-tests-must-be-at-least-256-bits"

    @BeforeEach
    fun setup() {
        jwtConfig = JwtConfig().apply {
            secret = testSecret
            accessTokenExpiry = 900000 // 15 minutes
            refreshTokenExpiry = 604800000 // 7 days
        }
        jwtService = JwtService(jwtConfig)
    }

    @Nested
    @DisplayName("generateAccessToken")
    inner class GenerateAccessToken {

        @Test
        fun `should generate valid access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)

            assertNotNull(token)
            assertTrue(token.isNotEmpty())
        }

        @Test
        fun `should include user ID in token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)
            val claims = jwtService.validateAccessToken(token)

            assertEquals(userId.toString(), claims.subject)
        }

        @Test
        fun `should include email in token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)
            val claims = jwtService.validateAccessToken(token)

            assertEquals(email, claims["email"])
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    inner class GenerateRefreshToken {

        @Test
        fun `should generate valid refresh token with jti`() {
            val userId = UUID.randomUUID()

            val (token, jti) = jwtService.generateRefreshToken(userId)

            assertNotNull(token)
            assertNotNull(jti)
            assertTrue(token.isNotEmpty())
            assertTrue(jti.isNotEmpty())
        }

        @Test
        fun `should generate unique jti for each token`() {
            val userId = UUID.randomUUID()

            val (_, jti1) = jwtService.generateRefreshToken(userId)
            val (_, jti2) = jwtService.generateRefreshToken(userId)

            assertNotEquals(jti1, jti2)
        }
    }

    @Nested
    @DisplayName("validateAccessToken")
    inner class ValidateAccessToken {

        @Test
        fun `should validate valid token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val token = jwtService.generateAccessToken(userId, email)

            val claims = jwtService.validateAccessToken(token)

            assertEquals(userId.toString(), claims.subject)
        }

        @Test
        fun `should throw TokenInvalidException for malformed token`() {
            assertThrows<TokenInvalidException> {
                jwtService.validateAccessToken("not-a-valid-token")
            }
        }

        @Test
        fun `should throw TokenExpiredException for expired token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            // Create an expired token manually
            val secretKey = Keys.hmacShaKeyFor(testSecret.toByteArray(StandardCharsets.UTF_8))
            val expiredToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date(System.currentTimeMillis() - 2000))
                .expiration(Date(System.currentTimeMillis() - 1000)) // Expired 1 second ago
                .signWith(secretKey)
                .compact()

            assertThrows<TokenExpiredException> {
                jwtService.validateAccessToken(expiredToken)
            }
        }
    }

    @Nested
    @DisplayName("hashToken")
    inner class HashToken {

        @Test
        fun `should hash token consistently`() {
            val token = "test-token-123"

            val hash1 = jwtService.hashToken(token)
            val hash2 = jwtService.hashToken(token)

            assertEquals(hash1, hash2)
        }

        @Test
        fun `should produce different hashes for different tokens`() {
            val hash1 = jwtService.hashToken("token-1")
            val hash2 = jwtService.hashToken("token-2")

            assertNotEquals(hash1, hash2)
        }

        @Test
        fun `should produce 64 character hex hash`() {
            val hash = jwtService.hashToken("test-token")

            assertEquals(64, hash.length) // SHA-256 produces 64 hex characters
            assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }

    @Nested
    @DisplayName("getAccessTokenExpirySeconds")
    inner class GetAccessTokenExpirySeconds {

        @Test
        fun `should return expiry in seconds`() {
            val expirySeconds = jwtService.getAccessTokenExpirySeconds()

            assertEquals(900L, expirySeconds) // 15 minutes = 900 seconds
        }
    }
}
