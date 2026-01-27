package com.fittrack.controller

import com.fittrack.model.dto.auth.AuthResponse
import com.fittrack.model.dto.auth.ChangeEmailRequest
import com.fittrack.model.dto.auth.ChangeEmailResponse
import com.fittrack.model.dto.auth.ChangePasswordRequest
import com.fittrack.model.dto.auth.ForgotPasswordRequest
import com.fittrack.model.dto.auth.LoginRequest
import com.fittrack.model.dto.auth.LogoutRequest
import com.fittrack.model.dto.auth.MessageResponse
import com.fittrack.model.dto.auth.RefreshTokenRequest
import com.fittrack.model.dto.auth.RegisterRequest
import com.fittrack.model.dto.auth.ResetPasswordRequest
import com.fittrack.model.dto.auth.TokenRefreshResponse
import com.fittrack.security.UserPrincipal
import com.fittrack.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<TokenRefreshResponse> {
        val response = authService.refreshToken(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        authService.logout(request.refreshToken, principal.id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<MessageResponse> {
        val response = authService.forgotPassword(request)
        return ResponseEntity.accepted().body(response)
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<MessageResponse> {
        val response = authService.resetPassword(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MessageResponse> {
        val response = authService.changePassword(request, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/change-email")
    fun changeEmail(
        @Valid @RequestBody request: ChangeEmailRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ChangeEmailResponse> {
        val response = authService.changeEmail(request, principal.id)
        return ResponseEntity.ok(response)
    }
}
