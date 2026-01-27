package com.fittrack.service

import com.fittrack.exception.EmailAlreadyExistsException
import com.fittrack.exception.InvalidCredentialsException
import com.fittrack.exception.TokenInvalidException
import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.RefreshToken
import com.fittrack.model.User
import com.fittrack.model.dto.auth.ChangeEmailRequest
import com.fittrack.model.dto.auth.ChangePasswordRequest
import com.fittrack.model.dto.auth.ForgotPasswordRequest
import com.fittrack.model.dto.auth.LoginRequest
import com.fittrack.model.dto.auth.RefreshTokenRequest
import com.fittrack.model.dto.auth.RegisterRequest
import com.fittrack.repository.RefreshTokenRepository
import com.fittrack.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

@DisplayName("Auth Service Tests")
class AuthServiceTest {

    private lateinit var authService: AuthService
    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var jwtService: JwtService
    private lateinit var profileService: ProfileService
    private lateinit var passwordEncoder: PasswordEncoder

    private val testUser = User(
        id = UUID.randomUUID(),
        email = "test@example.com",
        passwordHash = "\$2a\$12\$dummyHash",
        name = "Test User",
        defaultActivityLevel = ActivityLevel.MODERATE,
        fitnessGoalType = FitnessGoalType.MAINTAIN,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        refreshTokenRepository = mockk()
        jwtService = mockk()
        profileService = mockk()
        passwordEncoder = BCryptPasswordEncoder(12)

        authService = AuthService(
            userRepository,
            refreshTokenRepository,
            jwtService,
            profileService,
            passwordEncoder
        )
    }

    @Nested
    @DisplayName("register")
    inner class Register {

        @Test
        fun `should register new user successfully`() {
            val request = RegisterRequest(
                email = "new@example.com",
                password = "Password123!",
                name = "New User"
            )

            val newUser = testUser.copy(
                id = UUID.randomUUID(),
                email = "new@example.com",
                name = "New User"
            )

            every { userRepository.existsByEmail(request.email) } returns false
            every { userRepository.create(any(), any(), any()) } returns newUser
            every { jwtService.generateAccessToken(any(), any()) } returns "access-token"
            every { jwtService.generateRefreshToken(any()) } returns ("refresh-token" to "jti")
            every { jwtService.hashToken(any()) } returns "hashed-token"
            every { jwtService.getRefreshTokenExpiry() } returns Instant.now().plusSeconds(86400)
            every { jwtService.getAccessTokenExpirySeconds() } returns 900L
            every { refreshTokenRepository.create(any(), any(), any()) } returns mockk()
            every { profileService.toProfileResponse(any()) } returns mockk()

            val response = authService.register(request)

            assertNotNull(response)
            assertEquals("access-token", response.accessToken)
            assertEquals("refresh-token", response.refreshToken)
            verify { userRepository.create(any(), any(), "New User") }
        }

        @Test
        fun `should throw EmailAlreadyExistsException when email exists`() {
            val request = RegisterRequest(
                email = "existing@example.com",
                password = "Password123!",
                name = "New User"
            )

            every { userRepository.existsByEmail(request.email) } returns true

            assertThrows<EmailAlreadyExistsException> {
                authService.register(request)
            }
        }
    }

    @Nested
    @DisplayName("login")
    inner class Login {

        @Test
        fun `should login with valid credentials`() {
            val password = "Password123!"
            val hashedPassword = passwordEncoder.encode(password)
            val user = testUser.copy(passwordHash = hashedPassword)

            val request = LoginRequest(
                email = "test@example.com",
                password = password
            )

            every { userRepository.findByEmail(request.email) } returns user
            every { jwtService.generateAccessToken(any(), any()) } returns "access-token"
            every { jwtService.generateRefreshToken(any()) } returns ("refresh-token" to "jti")
            every { jwtService.hashToken(any()) } returns "hashed-token"
            every { jwtService.getRefreshTokenExpiry() } returns Instant.now().plusSeconds(86400)
            every { jwtService.getAccessTokenExpirySeconds() } returns 900L
            every { refreshTokenRepository.create(any(), any(), any()) } returns mockk()
            every { profileService.toProfileResponse(any()) } returns mockk()

            val response = authService.login(request)

            assertNotNull(response)
            assertEquals("access-token", response.accessToken)
        }

        @Test
        fun `should throw InvalidCredentialsException for wrong password`() {
            val password = "Password123!"
            val hashedPassword = passwordEncoder.encode(password)
            val user = testUser.copy(passwordHash = hashedPassword)

            val request = LoginRequest(
                email = "test@example.com",
                password = "WrongPassword!"
            )

            every { userRepository.findByEmail(request.email) } returns user

            assertThrows<InvalidCredentialsException> {
                authService.login(request)
            }
        }

        @Test
        fun `should throw InvalidCredentialsException for non-existent user`() {
            val request = LoginRequest(
                email = "nonexistent@example.com",
                password = "Password123!"
            )

            every { userRepository.findByEmail(request.email) } returns null

            assertThrows<InvalidCredentialsException> {
                authService.login(request)
            }
        }
    }

    @Nested
    @DisplayName("refreshToken")
    inner class RefreshToken {

        @Test
        fun `should refresh token successfully`() {
            val userId = UUID.randomUUID()
            val request = RefreshTokenRequest(refreshToken = "old-refresh-token")
            val storedToken = RefreshToken(
                id = UUID.randomUUID(),
                profileId = userId,
                tokenHash = "hashed-token",
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now()
            )

            every { jwtService.hashToken("old-refresh-token") } returns "hashed-token"
            every { refreshTokenRepository.findValidByTokenHash("hashed-token") } returns storedToken
            every { jwtService.validateRefreshToken("old-refresh-token") } returns mockk {
                every { subject } returns userId.toString()
            }
            every { jwtService.getUserIdFromClaims(any()) } returns userId
            every { refreshTokenRepository.revokeToken("hashed-token") } returns true
            every { userRepository.findById(userId) } returns testUser.copy(id = userId)
            every { jwtService.generateAccessToken(any(), any()) } returns "new-access-token"
            every { jwtService.generateRefreshToken(any()) } returns ("new-refresh-token" to "new-jti")
            every { jwtService.hashToken("new-refresh-token") } returns "new-hashed-token"
            every { jwtService.getRefreshTokenExpiry() } returns Instant.now().plusSeconds(86400)
            every { jwtService.getAccessTokenExpirySeconds() } returns 900L
            every { refreshTokenRepository.create(any(), "new-hashed-token", any()) } returns mockk()

            val response = authService.refreshToken(request)

            assertNotNull(response)
            assertEquals("new-access-token", response.accessToken)
            assertEquals("new-refresh-token", response.refreshToken)
            verify { refreshTokenRepository.revokeToken("hashed-token") }
        }

        @Test
        fun `should throw TokenInvalidException for invalid refresh token`() {
            val request = RefreshTokenRequest(refreshToken = "invalid-token")

            every { jwtService.hashToken("invalid-token") } returns "hashed-invalid-token"
            every { refreshTokenRepository.findValidByTokenHash("hashed-invalid-token") } returns null

            assertThrows<TokenInvalidException> {
                authService.refreshToken(request)
            }
        }
    }

    @Nested
    @DisplayName("changePassword")
    inner class ChangePassword {

        @Test
        fun `should change password successfully`() {
            val userId = UUID.randomUUID()
            val currentPassword = "CurrentPassword123!"
            val hashedPassword = passwordEncoder.encode(currentPassword)
            val user = testUser.copy(id = userId, passwordHash = hashedPassword)

            val request = ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = "NewPassword456!"
            )

            every { userRepository.findById(userId) } returns user
            every { userRepository.updatePassword(userId, any()) } returns true

            val response = authService.changePassword(request, userId)

            assertEquals("Password changed successfully.", response.message)
            verify { userRepository.updatePassword(userId, any()) }
        }

        @Test
        fun `should throw InvalidCredentialsException for wrong current password`() {
            val userId = UUID.randomUUID()
            val currentPassword = "CurrentPassword123!"
            val hashedPassword = passwordEncoder.encode(currentPassword)
            val user = testUser.copy(id = userId, passwordHash = hashedPassword)

            val request = ChangePasswordRequest(
                currentPassword = "WrongPassword!",
                newPassword = "NewPassword456!"
            )

            every { userRepository.findById(userId) } returns user

            assertThrows<InvalidCredentialsException> {
                authService.changePassword(request, userId)
            }
        }
    }

    @Nested
    @DisplayName("changeEmail")
    inner class ChangeEmail {

        @Test
        fun `should change email successfully`() {
            val userId = UUID.randomUUID()
            val password = "Password123!"
            val hashedPassword = passwordEncoder.encode(password)
            val user = testUser.copy(id = userId, passwordHash = hashedPassword)

            val request = ChangeEmailRequest(
                password = password,
                newEmail = "newemail@example.com"
            )

            every { userRepository.findById(userId) } returns user
            every { userRepository.existsByEmail("newemail@example.com") } returns false
            every { userRepository.updateEmail(userId, "newemail@example.com") } returns true

            val response = authService.changeEmail(request, userId)

            assertEquals("Email updated successfully.", response.message)
            assertEquals("newemail@example.com", response.email)
        }

        @Test
        fun `should throw EmailAlreadyExistsException when new email exists`() {
            val userId = UUID.randomUUID()
            val password = "Password123!"
            val hashedPassword = passwordEncoder.encode(password)
            val user = testUser.copy(id = userId, passwordHash = hashedPassword)

            val request = ChangeEmailRequest(
                password = password,
                newEmail = "existing@example.com"
            )

            every { userRepository.findById(userId) } returns user
            every { userRepository.existsByEmail("existing@example.com") } returns true

            assertThrows<EmailAlreadyExistsException> {
                authService.changeEmail(request, userId)
            }
        }
    }

    @Nested
    @DisplayName("forgotPassword")
    inner class ForgotPassword {

        @Test
        fun `should return success message regardless of email existence`() {
            val request = ForgotPasswordRequest(email = "any@example.com")

            every { userRepository.findByEmail("any@example.com") } returns null

            val response = authService.forgotPassword(request)

            assertEquals("If the email exists, a reset link has been sent.", response.message)
        }

        @Test
        fun `should create password reset token for existing user`() {
            val request = ForgotPasswordRequest(email = "existing@example.com")

            every { userRepository.findByEmail("existing@example.com") } returns testUser
            every { refreshTokenRepository.countRecentPasswordResetRequests(any(), any()) } returns 0
            every { jwtService.hashToken(any()) } returns "hashed-reset-token"
            every { refreshTokenRepository.createPasswordResetToken(any(), any(), any()) } returns mockk()

            val response = authService.forgotPassword(request)

            assertEquals("If the email exists, a reset link has been sent.", response.message)
            verify { refreshTokenRepository.createPasswordResetToken(any(), any(), any()) }
        }
    }
}
