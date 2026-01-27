package com.fittrack.service

import com.fittrack.exception.EmailAlreadyExistsException
import com.fittrack.exception.InvalidCredentialsException
import com.fittrack.exception.RateLimitExceededException
import com.fittrack.exception.TokenExpiredException
import com.fittrack.exception.TokenInvalidException
import com.fittrack.model.User
import com.fittrack.model.dto.auth.AuthResponse
import com.fittrack.model.dto.auth.ChangeEmailRequest
import com.fittrack.model.dto.auth.ChangeEmailResponse
import com.fittrack.model.dto.auth.ChangePasswordRequest
import com.fittrack.model.dto.auth.ForgotPasswordRequest
import com.fittrack.model.dto.auth.LoginRequest
import com.fittrack.model.dto.auth.MessageResponse
import com.fittrack.model.dto.auth.RefreshTokenRequest
import com.fittrack.model.dto.auth.RegisterRequest
import com.fittrack.model.dto.auth.ResetPasswordRequest
import com.fittrack.model.dto.auth.TokenRefreshResponse
import com.fittrack.repository.RefreshTokenRepository
import com.fittrack.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val profileService: ProfileService,
    private val passwordEncoder: PasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PASSWORD_RESET_EXPIRY_HOURS = 1L
        private const val MAX_PASSWORD_RESET_REQUESTS_PER_HOUR = 3
    }

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        logger.info("Registering new user: ${request.email}")

        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException(request.email)
        }

        val passwordHash = passwordEncoder.encode(request.password)
        val user = userRepository.create(
            email = request.email,
            passwordHash = passwordHash,
            name = request.name
        )

        return createAuthResponse(user)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        logger.debug("Login attempt for: ${request.email}")

        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        return createAuthResponse(user)
    }

    @Transactional
    fun refreshToken(request: RefreshTokenRequest): TokenRefreshResponse {
        logger.debug("Refreshing token")

        val tokenHash = jwtService.hashToken(request.refreshToken)
        val storedToken = refreshTokenRepository.findValidByTokenHash(tokenHash)
            ?: throw TokenInvalidException("Refresh token is invalid or has been revoked")

        // Validate the JWT
        val claims = jwtService.validateRefreshToken(request.refreshToken)
        val userId = jwtService.getUserIdFromClaims(claims)

        // Verify the token belongs to the same user
        if (storedToken.profileId != userId) {
            throw TokenInvalidException("Token mismatch")
        }

        // Revoke the old token (token rotation)
        refreshTokenRepository.revokeToken(tokenHash)

        // Get user to generate new tokens
        val user = userRepository.findById(userId)
            ?: throw TokenInvalidException("User not found")

        // Generate new tokens
        val accessToken = jwtService.generateAccessToken(user.id, user.email)
        val (newRefreshToken, _) = jwtService.generateRefreshToken(user.id)
        val newTokenHash = jwtService.hashToken(newRefreshToken)

        refreshTokenRepository.create(
            profileId = user.id,
            tokenHash = newTokenHash,
            expiresAt = jwtService.getRefreshTokenExpiry()
        )

        return TokenRefreshResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            expiresIn = jwtService.getAccessTokenExpirySeconds()
        )
    }

    @Transactional
    fun logout(refreshToken: String, userId: UUID) {
        logger.debug("Logging out user: $userId")

        val tokenHash = jwtService.hashToken(refreshToken)
        val storedToken = refreshTokenRepository.findByTokenHash(tokenHash)

        if (storedToken != null && storedToken.profileId == userId) {
            refreshTokenRepository.revokeToken(tokenHash)
        }
    }

    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest): MessageResponse {
        logger.info("Password reset requested for: ${request.email}")

        val user = userRepository.findByEmail(request.email)

        // Always return success message to prevent email enumeration
        val message = "If the email exists, a reset link has been sent."

        if (user == null) {
            return MessageResponse(message)
        }

        // Rate limiting: 3 requests per hour
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        val recentRequests = refreshTokenRepository.countRecentPasswordResetRequests(user.id, oneHourAgo)

        if (recentRequests >= MAX_PASSWORD_RESET_REQUESTS_PER_HOUR) {
            logger.warn("Rate limit exceeded for password reset: ${request.email}")
            // Still return success message to prevent enumeration
            return MessageResponse(message)
        }

        // Generate reset token (using UUID for simplicity)
        val resetToken = UUID.randomUUID().toString()
        val tokenHash = jwtService.hashToken(resetToken)
        val expiresAt = Instant.now().plus(PASSWORD_RESET_EXPIRY_HOURS, ChronoUnit.HOURS)

        refreshTokenRepository.createPasswordResetToken(user.id, tokenHash, expiresAt)

        // In a real application, send email here
        // For now, log the token (mock email)
        logger.info("Password reset token for ${user.email}: $resetToken (mock email)")

        return MessageResponse(message)
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest): MessageResponse {
        logger.debug("Resetting password with token")

        val tokenHash = jwtService.hashToken(request.token)
        val resetToken = refreshTokenRepository.findPasswordResetByTokenHash(tokenHash)
            ?: throw TokenInvalidException("Invalid reset token")

        if (resetToken.isUsed()) {
            throw TokenInvalidException("Reset token has already been used")
        }

        if (resetToken.isExpired()) {
            throw TokenExpiredException("Reset token has expired")
        }

        // Update password
        val newPasswordHash = passwordEncoder.encode(request.newPassword)
        userRepository.updatePassword(resetToken.profileId, newPasswordHash)

        // Mark token as used
        refreshTokenRepository.markPasswordResetAsUsed(tokenHash)

        // Revoke all refresh tokens for this user (force re-login)
        refreshTokenRepository.revokeAllForUser(resetToken.profileId)

        return MessageResponse("Password has been reset successfully.")
    }

    @Transactional
    fun changePassword(request: ChangePasswordRequest, userId: UUID): MessageResponse {
        logger.debug("Changing password for user: $userId")

        val user = userRepository.findById(userId)
            ?: throw InvalidCredentialsException("User not found")

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw InvalidCredentialsException("Current password is incorrect")
        }

        val newPasswordHash = passwordEncoder.encode(request.newPassword)
        userRepository.updatePassword(userId, newPasswordHash)

        return MessageResponse("Password changed successfully.")
    }

    @Transactional
    fun changeEmail(request: ChangeEmailRequest, userId: UUID): ChangeEmailResponse {
        logger.debug("Changing email for user: $userId")

        val user = userRepository.findById(userId)
            ?: throw InvalidCredentialsException("User not found")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException("Password is incorrect")
        }

        if (userRepository.existsByEmail(request.newEmail)) {
            throw EmailAlreadyExistsException(request.newEmail)
        }

        userRepository.updateEmail(userId, request.newEmail)

        return ChangeEmailResponse(
            message = "Email updated successfully.",
            email = request.newEmail.lowercase()
        )
    }

    private fun createAuthResponse(user: User): AuthResponse {
        val accessToken = jwtService.generateAccessToken(user.id, user.email)
        val (refreshToken, _) = jwtService.generateRefreshToken(user.id)
        val tokenHash = jwtService.hashToken(refreshToken)

        refreshTokenRepository.create(
            profileId = user.id,
            tokenHash = tokenHash,
            expiresAt = jwtService.getRefreshTokenExpiry()
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtService.getAccessTokenExpirySeconds(),
            profile = profileService.toProfileResponse(user)
        )
    }
}
