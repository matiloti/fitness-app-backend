package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.dto.auth.ChangeEmailRequest
import com.fittrack.model.dto.auth.ChangePasswordRequest
import com.fittrack.model.dto.auth.ForgotPasswordRequest
import com.fittrack.model.dto.auth.LoginRequest
import com.fittrack.model.dto.auth.LogoutRequest
import com.fittrack.model.dto.auth.RefreshTokenRequest
import com.fittrack.model.dto.auth.RegisterRequest
import com.fittrack.model.dto.auth.ResetPasswordRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DisplayName("Auth Controller Integration Tests")
class AuthControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    inner class Register {

        @Test
        fun `should register new user successfully`() {
            val request = RegisterRequest(
                email = "test@example.com",
                password = "Password123!",
                name = "Test User"
            )

            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.profile.email").value("test@example.com"))
                .andExpect(jsonPath("$.profile.name").value("Test User"))
        }

        @Test
        fun `should return 409 when email already exists`() {
            // First registration
            val request = RegisterRequest(
                email = "duplicate@example.com",
                password = "Password123!",
                name = "First User"
            )
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isCreated)

            // Duplicate registration
            val duplicateRequest = request.copy(name = "Second User")
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(duplicateRequest))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
        }

        @Test
        fun `should return 400 for invalid email`() {
            val request = RegisterRequest(
                email = "invalid-email",
                password = "Password123!",
                name = "Test User"
            )

            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }

        @Test
        fun `should return 400 for weak password`() {
            val request = RegisterRequest(
                email = "test@example.com",
                password = "weak",
                name = "Test User"
            )

            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    inner class Login {

        @Test
        fun `should login successfully with valid credentials`() {
            // Register user first
            registerTestUser("login@example.com", "Password123!")

            val request = LoginRequest(
                email = "login@example.com",
                password = "Password123!"
            )

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.profile.email").value("login@example.com"))
        }

        @Test
        fun `should return 401 for invalid password`() {
            registerTestUser("login2@example.com", "Password123!")

            val request = LoginRequest(
                email = "login2@example.com",
                password = "WrongPassword123!"
            )

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        }

        @Test
        fun `should return 401 for non-existent email`() {
            val request = LoginRequest(
                email = "nonexistent@example.com",
                password = "Password123!"
            )

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    inner class RefreshToken {

        @Test
        fun `should refresh token successfully`() {
            val refreshToken = registerAndGetRefreshToken("refresh@example.com")

            val request = RefreshTokenRequest(refreshToken = refreshToken)

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
        }

        @Test
        fun `should return 401 for invalid refresh token`() {
            val request = RefreshTokenRequest(refreshToken = "invalid-token")

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    inner class Logout {

        @Test
        fun `should logout successfully`() {
            val (accessToken, refreshToken) = registerAndGetTokens("logout@example.com")

            val request = LogoutRequest(refreshToken = refreshToken)

            mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNoContent)

            // Verify refresh token is revoked
            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(RefreshTokenRequest(refreshToken)))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val request = LogoutRequest(refreshToken = "some-token")

            mockMvc.perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/forgot-password")
    inner class ForgotPassword {

        @Test
        fun `should return 202 for existing email`() {
            registerTestUser("forgot@example.com", "Password123!")

            val request = ForgotPasswordRequest(email = "forgot@example.com")

            mockMvc.perform(
                post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.message").value("If the email exists, a reset link has been sent."))
        }

        @Test
        fun `should return 202 for non-existing email to prevent enumeration`() {
            val request = ForgotPasswordRequest(email = "nonexistent@example.com")

            mockMvc.perform(
                post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.message").value("If the email exists, a reset link has been sent."))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/change-password")
    inner class ChangePassword {

        @Test
        fun `should change password successfully`() {
            val (accessToken, _) = registerAndGetTokens("changepass@example.com")

            val request = ChangePasswordRequest(
                currentPassword = "Password123!",
                newPassword = "NewPassword456!"
            )

            mockMvc.perform(
                post("/api/v1/auth/change-password")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Password changed successfully."))

            // Verify can login with new password
            val loginRequest = LoginRequest(
                email = "changepass@example.com",
                password = "NewPassword456!"
            )
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 401 for incorrect current password`() {
            val (accessToken, _) = registerAndGetTokens("changepass2@example.com")

            val request = ChangePasswordRequest(
                currentPassword = "WrongPassword!",
                newPassword = "NewPassword456!"
            )

            mockMvc.perform(
                post("/api/v1/auth/change-password")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/change-email")
    inner class ChangeEmail {

        @Test
        fun `should change email successfully`() {
            val (accessToken, _) = registerAndGetTokens("oldemail@example.com")

            val request = ChangeEmailRequest(
                password = "Password123!",
                newEmail = "newemail@example.com"
            )

            mockMvc.perform(
                post("/api/v1/auth/change-email")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Email updated successfully."))
                .andExpect(jsonPath("$.email").value("newemail@example.com"))

            // Verify can login with new email
            val loginRequest = LoginRequest(
                email = "newemail@example.com",
                password = "Password123!"
            )
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 409 when new email already exists`() {
            registerTestUser("existing@example.com", "Password123!")
            val (accessToken, _) = registerAndGetTokens("original@example.com")

            val request = ChangeEmailRequest(
                password = "Password123!",
                newEmail = "existing@example.com"
            )

            mockMvc.perform(
                post("/api/v1/auth/change-email")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
        }
    }

    // ========== Helper Methods ==========

    private fun registerTestUser(email: String, password: String) {
        val request = RegisterRequest(
            email = email,
            password = password,
            name = "Test User"
        )
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated)
    }

    private fun registerAndGetRefreshToken(email: String): String {
        val request = RegisterRequest(
            email = email,
            password = "Password123!",
            name = "Test User"
        )
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        return response.get("refreshToken").asText()
    }

    private fun registerAndGetTokens(email: String): Pair<String, String> {
        val request = RegisterRequest(
            email = email,
            password = "Password123!",
            name = "Test User"
        )
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        return response.get("accessToken").asText() to response.get("refreshToken").asText()
    }
}
