package com.fittrack.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
class JwtConfig {
    lateinit var secret: String
    var accessTokenExpiry: Long = 900000 // 15 minutes in milliseconds
    var refreshTokenExpiry: Long = 604800000 // 7 days in milliseconds
}
