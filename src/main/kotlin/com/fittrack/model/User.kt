package com.fittrack.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * User profile entity - maps to profiles table
 */
data class User(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val name: String,
    val photoUrl: String? = null,
    val countryId: Short? = null,
    val dateOfBirth: LocalDate? = null,
    val sex: Sex? = null,
    val heightCm: BigDecimal? = null,
    val defaultActivityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val fitnessGoalType: FitnessGoalType = FitnessGoalType.MAINTAIN,
    val fitnessGoalIntensity: FitnessGoalIntensity? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Country lookup entity - maps to countries table
 */
data class Country(
    val id: Short,
    val code: String,
    val name: String
)
