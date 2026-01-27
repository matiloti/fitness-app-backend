package com.fittrack.security

import com.fittrack.exception.TokenExpiredException
import com.fittrack.exception.TokenInvalidException
import com.fittrack.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val authHeader = request.getHeader("Authorization")

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response)
                return
            }

            val token = authHeader.substring(7)
            val claims = jwtService.validateAccessToken(token)

            val userId = jwtService.getUserIdFromClaims(claims)
            val email = jwtService.getEmailFromClaims(claims)

            val userPrincipal = UserPrincipal(
                id = userId,
                email = email,
                passwordHash = "" // Not needed for token-based auth
            )

            val authentication = UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.authorities
            )
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

            SecurityContextHolder.getContext().authentication = authentication
            log.debug("Authenticated user: {} ({})", email, userId)

        } catch (e: TokenExpiredException) {
            log.debug("Token expired: {}", e.message)
            // Don't set authentication - request will be handled by security config
        } catch (e: TokenInvalidException) {
            log.debug("Invalid token: {}", e.message)
            // Don't set authentication - request will be handled by security config
        } catch (e: Exception) {
            log.error("Error processing JWT", e)
        }

        filterChain.doFilter(request, response)
    }
}
