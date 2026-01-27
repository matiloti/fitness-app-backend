package com.fittrack.model.dto.profile

import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ========== Response DTOs ==========

data class ProfileResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val photoUrl: String? = null,
    val country: CountryResponse? = null,
    val metrics: ProfileMetricsResponse? = null,
    val activityLevel: ActivityLevelResponse? = null,
    val fitnessGoal: FitnessGoalResponse? = null,
    val calculations: ProfileCalculationsResponse? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class CountryResponse(
    val code: String,
    val name: String
)

data class ProfileMetricsResponse(
    val dateOfBirth: LocalDate?,
    val age: Int?,
    val sex: Sex?,
    val heightCm: BigDecimal?
)

data class ActivityLevelResponse(
    val level: ActivityLevel,
    val multiplier: Double,
    val description: String
)

data class FitnessGoalResponse(
    val type: FitnessGoalType,
    val intensity: FitnessGoalIntensity?,
    val dailyCalorieAdjustment: Int,
    val description: String
)

data class ProfileCalculationsResponse(
    val bmr: Int?,
    val tdee: Int?,
    val dailyCalorieGoal: Int?,
    val macros: MacrosResponse? = null
)

data class MacrosResponse(
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

// ========== Request DTOs ==========

data class UpdateProfileRequest(
    @field:Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    val name: String? = null,

    val photoUrl: String? = null,

    @field:Size(min = 2, max = 2, message = "Country code must be 2 characters")
    val countryCode: String? = null
)

data class UpdateProfileMetricsRequest(
    @field:NotNull(message = "Date of birth is required")
    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate,

    @field:NotNull(message = "Sex is required")
    val sex: Sex,

    @field:NotNull(message = "Height is required")
    @field:DecimalMin(value = "50", message = "Height must be at least 50 cm")
    @field:DecimalMax(value = "300", message = "Height must be at most 300 cm")
    val heightCm: BigDecimal
)

data class UpdateActivityLevelRequest(
    @field:NotNull(message = "Activity level is required")
    val activityLevel: ActivityLevel
)

data class UpdateFitnessGoalRequest(
    @field:NotNull(message = "Goal type is required")
    val type: FitnessGoalType,

    val intensity: FitnessGoalIntensity? = null
)

// ========== List Response DTOs ==========

data class CountriesResponse(
    val countries: List<CountryResponse>
)

data class ActivityLevelsResponse(
    val activityLevels: List<ActivityLevelDetailResponse>
)

data class ActivityLevelDetailResponse(
    val level: ActivityLevel,
    val multiplier: Double,
    val description: String
)

data class FitnessGoalsResponse(
    val fitnessGoals: List<FitnessGoalDetailResponse>
)

data class FitnessGoalDetailResponse(
    val type: FitnessGoalType,
    val intensity: FitnessGoalIntensity?,
    val adjustment: Int,
    val description: String
)

// ========== Metrics Update Response ==========

data class MetricsUpdateResponse(
    val dateOfBirth: LocalDate,
    val age: Int,
    val sex: Sex,
    val heightCm: BigDecimal,
    val calculations: ProfileCalculationsResponse?
)

data class ActivityLevelUpdateResponse(
    val activityLevel: ActivityLevelResponse,
    val calculations: ProfileCalculationsResponse?
)

data class FitnessGoalUpdateResponse(
    val fitnessGoal: FitnessGoalResponse,
    val calculations: ProfileCalculationsResponse?
)
