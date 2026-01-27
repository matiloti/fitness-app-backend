package com.fittrack.service

import com.fittrack.exception.InvalidCountryException
import com.fittrack.exception.InvalidFitnessGoalException
import com.fittrack.exception.ProfileNotFoundException
import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import com.fittrack.model.User
import com.fittrack.model.dto.profile.ActivityLevelDetailResponse
import com.fittrack.model.dto.profile.ActivityLevelResponse
import com.fittrack.model.dto.profile.ActivityLevelUpdateResponse
import com.fittrack.model.dto.profile.ActivityLevelsResponse
import com.fittrack.model.dto.profile.CountriesResponse
import com.fittrack.model.dto.profile.CountryResponse
import com.fittrack.model.dto.profile.FitnessGoalDetailResponse
import com.fittrack.model.dto.profile.FitnessGoalResponse
import com.fittrack.model.dto.profile.FitnessGoalUpdateResponse
import com.fittrack.model.dto.profile.FitnessGoalsResponse
import com.fittrack.model.dto.profile.MacrosResponse
import com.fittrack.model.dto.profile.MetricsUpdateResponse
import com.fittrack.model.dto.profile.ProfileCalculationsResponse
import com.fittrack.model.dto.profile.ProfileMetricsResponse
import com.fittrack.model.dto.profile.ProfileResponse
import com.fittrack.model.dto.profile.UpdateActivityLevelRequest
import com.fittrack.model.dto.profile.UpdateFitnessGoalRequest
import com.fittrack.model.dto.profile.UpdateProfileMetricsRequest
import com.fittrack.model.dto.profile.UpdateProfileRequest
import com.fittrack.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val bodyMetricsRepository: BodyMetricsRepository? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Macro distribution percentages
        private const val PROTEIN_PERCENTAGE = 0.30
        private const val CARBS_PERCENTAGE = 0.45
        private const val FAT_PERCENTAGE = 0.25

        // Calories per gram
        private const val PROTEIN_CALORIES_PER_GRAM = 4
        private const val CARBS_CALORIES_PER_GRAM = 4
        private const val FAT_CALORIES_PER_GRAM = 9

        // Minimum daily calories
        private const val MIN_DAILY_CALORIES = 1200
    }

    fun getProfile(userId: UUID): ProfileResponse {
        val user = userRepository.findById(userId)
            ?: throw ProfileNotFoundException()

        return toProfileResponse(user)
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): ProfileResponse {
        logger.debug("Updating profile for user: $userId")

        var countryId: Short? = null
        if (request.countryCode != null) {
            val country = userRepository.findCountryByCode(request.countryCode)
                ?: throw InvalidCountryException(request.countryCode)
            countryId = country.id
        }

        val user = userRepository.updateProfile(userId, request.name, request.photoUrl, countryId)
            ?: throw ProfileNotFoundException()

        return toProfileResponse(user)
    }

    @Transactional
    fun updateMetrics(userId: UUID, request: UpdateProfileMetricsRequest): MetricsUpdateResponse {
        logger.debug("Updating metrics for user: $userId")

        // Validate age (must be at least 13)
        val age = Period.between(request.dateOfBirth, LocalDate.now()).years
        if (age < 13) {
            throw InvalidFitnessGoalException("User must be at least 13 years old")
        }

        val user = userRepository.updateMetrics(userId, request.dateOfBirth, request.sex, request.heightCm)
            ?: throw ProfileNotFoundException()

        val calculations = calculateProfileMetrics(user)

        return MetricsUpdateResponse(
            dateOfBirth = request.dateOfBirth,
            age = age,
            sex = request.sex,
            heightCm = request.heightCm,
            calculations = calculations
        )
    }

    @Transactional
    fun updateActivityLevel(userId: UUID, request: UpdateActivityLevelRequest): ActivityLevelUpdateResponse {
        logger.debug("Updating activity level for user: $userId")

        val user = userRepository.updateActivityLevel(userId, request.activityLevel)
            ?: throw ProfileNotFoundException()

        val calculations = calculateProfileMetrics(user)

        return ActivityLevelUpdateResponse(
            activityLevel = ActivityLevelResponse(
                level = request.activityLevel,
                multiplier = request.activityLevel.multiplier,
                description = request.activityLevel.description
            ),
            calculations = calculations
        )
    }

    @Transactional
    fun updateFitnessGoal(userId: UUID, request: UpdateFitnessGoalRequest): FitnessGoalUpdateResponse {
        logger.debug("Updating fitness goal for user: $userId")

        // Validate goal configuration
        if (request.type == FitnessGoalType.MAINTAIN && request.intensity != null) {
            throw InvalidFitnessGoalException("MAINTAIN goal cannot have intensity")
        }
        if (request.type != FitnessGoalType.MAINTAIN && request.intensity == null) {
            throw InvalidFitnessGoalException("LOSE and GAIN goals require intensity")
        }

        val user = userRepository.updateFitnessGoal(userId, request.type, request.intensity)
            ?: throw ProfileNotFoundException()

        val calculations = calculateProfileMetrics(user)
        val adjustment = calculateCalorieAdjustment(request.type, request.intensity)

        return FitnessGoalUpdateResponse(
            fitnessGoal = FitnessGoalResponse(
                type = request.type,
                intensity = request.intensity,
                dailyCalorieAdjustment = adjustment,
                description = buildGoalDescription(request.type, request.intensity)
            ),
            calculations = calculations
        )
    }

    // ========== Reference Data ==========

    fun getCountries(search: String?): CountriesResponse {
        val countries = if (search.isNullOrBlank()) {
            userRepository.findAllCountries()
        } else {
            userRepository.searchCountries(search)
        }

        return CountriesResponse(
            countries = countries.map { CountryResponse(it.code, it.name) }
        )
    }

    fun getActivityLevels(): ActivityLevelsResponse {
        return ActivityLevelsResponse(
            activityLevels = ActivityLevel.entries.map {
                ActivityLevelDetailResponse(
                    level = it,
                    multiplier = it.multiplier,
                    description = it.description
                )
            }
        )
    }

    fun getFitnessGoals(): FitnessGoalsResponse {
        val goals = mutableListOf<FitnessGoalDetailResponse>()

        // MAINTAIN goal
        goals.add(FitnessGoalDetailResponse(
            type = FitnessGoalType.MAINTAIN,
            intensity = null,
            adjustment = 0,
            description = "Maintain current weight"
        ))

        // LOSE goals
        FitnessGoalIntensity.entries.forEach { intensity ->
            goals.add(FitnessGoalDetailResponse(
                type = FitnessGoalType.LOSE,
                intensity = intensity,
                adjustment = -intensity.adjustment,
                description = "Lose ${intensity.description}"
            ))
        }

        // GAIN goals
        FitnessGoalIntensity.entries.forEach { intensity ->
            goals.add(FitnessGoalDetailResponse(
                type = FitnessGoalType.GAIN,
                intensity = intensity,
                adjustment = intensity.adjustment,
                description = "Gain ${intensity.description}"
            ))
        }

        return FitnessGoalsResponse(fitnessGoals = goals)
    }

    // ========== Helper Methods ==========

    fun toProfileResponse(user: User): ProfileResponse {
        val country = user.countryId?.let { userRepository.findCountryById(it) }
        val age = user.dateOfBirth?.let { Period.between(it, LocalDate.now()).years }
        val calculations = calculateProfileMetrics(user)

        return ProfileResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            photoUrl = user.photoUrl,
            country = country?.let { CountryResponse(it.code, it.name) },
            metrics = ProfileMetricsResponse(
                dateOfBirth = user.dateOfBirth,
                age = age,
                sex = user.sex,
                heightCm = user.heightCm
            ),
            activityLevel = ActivityLevelResponse(
                level = user.defaultActivityLevel,
                multiplier = user.defaultActivityLevel.multiplier,
                description = user.defaultActivityLevel.description
            ),
            fitnessGoal = FitnessGoalResponse(
                type = user.fitnessGoalType,
                intensity = user.fitnessGoalIntensity,
                dailyCalorieAdjustment = calculateCalorieAdjustment(user.fitnessGoalType, user.fitnessGoalIntensity),
                description = buildGoalDescription(user.fitnessGoalType, user.fitnessGoalIntensity)
            ),
            calculations = calculations,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    private fun calculateProfileMetrics(user: User): ProfileCalculationsResponse? {
        // Can't calculate without required metrics
        if (user.dateOfBirth == null || user.sex == null || user.heightCm == null) {
            return null
        }

        // Try to get latest weight - for now, return null if no body metrics repository
        val latestWeight = getLatestWeight(user.id) ?: return null

        val age = Period.between(user.dateOfBirth, LocalDate.now()).years
        val bmr = calculateBmr(latestWeight, user.heightCm, age, user.sex)
        val tdee = calculateTdee(bmr, user.defaultActivityLevel)
        val adjustment = calculateCalorieAdjustment(user.fitnessGoalType, user.fitnessGoalIntensity)
        val dailyCalorieGoal = maxOf(tdee + adjustment, MIN_DAILY_CALORIES)
        val macros = calculateMacros(dailyCalorieGoal)

        return ProfileCalculationsResponse(
            bmr = bmr,
            tdee = tdee,
            dailyCalorieGoal = dailyCalorieGoal,
            macros = macros
        )
    }

    private fun getLatestWeight(userId: UUID): BigDecimal? {
        // For now, return null - body metrics will be implemented in a later phase
        // When implemented: return bodyMetricsRepository?.findLatestWeight(userId)
        return null
    }

    /**
     * Calculate BMR using Mifflin-St Jeor equation
     * Male: (10 x weight) + (6.25 x height) - (5 x age) + 5
     * Female: (10 x weight) + (6.25 x height) - (5 x age) - 161
     */
    private fun calculateBmr(weightKg: BigDecimal, heightCm: BigDecimal, age: Int, sex: Sex): Int {
        val base = BigDecimal("10").multiply(weightKg)
            .add(BigDecimal("6.25").multiply(heightCm))
            .subtract(BigDecimal("5").multiply(BigDecimal(age)))

        val adjustment = if (sex == Sex.MALE) BigDecimal("5") else BigDecimal("-161")

        return base.add(adjustment).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    /**
     * Calculate TDEE from BMR and activity level
     */
    private fun calculateTdee(bmr: Int, activityLevel: ActivityLevel): Int {
        return (bmr * activityLevel.multiplier).toInt()
    }

    private fun calculateCalorieAdjustment(goalType: FitnessGoalType, intensity: FitnessGoalIntensity?): Int {
        return when (goalType) {
            FitnessGoalType.MAINTAIN -> 0
            FitnessGoalType.LOSE -> -(intensity?.adjustment ?: 500)
            FitnessGoalType.GAIN -> intensity?.adjustment ?: 500
        }
    }

    private fun buildGoalDescription(goalType: FitnessGoalType, intensity: FitnessGoalIntensity?): String {
        return when (goalType) {
            FitnessGoalType.MAINTAIN -> "Maintain current weight"
            FitnessGoalType.LOSE -> "Lose ${intensity?.description ?: "~0.5 kg/week"}"
            FitnessGoalType.GAIN -> "Gain ${intensity?.description ?: "~0.5 kg/week"}"
        }
    }

    private fun calculateMacros(dailyCalories: Int): MacrosResponse {
        val proteinCalories = (dailyCalories * PROTEIN_PERCENTAGE).toInt()
        val carbsCalories = (dailyCalories * CARBS_PERCENTAGE).toInt()
        val fatCalories = (dailyCalories * FAT_PERCENTAGE).toInt()

        return MacrosResponse(
            protein = proteinCalories / PROTEIN_CALORIES_PER_GRAM,
            carbs = carbsCalories / CARBS_CALORIES_PER_GRAM,
            fat = fatCalories / FAT_CALORIES_PER_GRAM
        )
    }
}

// Placeholder interface - will be implemented in body metrics phase
interface BodyMetricsRepository {
    fun findLatestWeight(profileId: UUID): BigDecimal?
}
