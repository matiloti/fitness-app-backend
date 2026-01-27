package com.fittrack.service

import com.fittrack.exception.InvalidCountryException
import com.fittrack.exception.InvalidFitnessGoalException
import com.fittrack.exception.ProfileNotFoundException
import com.fittrack.model.ActivityLevel
import com.fittrack.model.Country
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import com.fittrack.model.User
import com.fittrack.model.dto.profile.UpdateActivityLevelRequest
import com.fittrack.model.dto.profile.UpdateFitnessGoalRequest
import com.fittrack.model.dto.profile.UpdateProfileMetricsRequest
import com.fittrack.model.dto.profile.UpdateProfileRequest
import com.fittrack.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Profile Service Tests")
class ProfileServiceTest {

    private lateinit var profileService: ProfileService
    private lateinit var userRepository: UserRepository

    private val testUser = User(
        id = UUID.randomUUID(),
        email = "test@example.com",
        passwordHash = "hash",
        name = "Test User",
        defaultActivityLevel = ActivityLevel.MODERATE,
        fitnessGoalType = FitnessGoalType.MAINTAIN,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        profileService = ProfileService(userRepository, null)
    }

    @Nested
    @DisplayName("getProfile")
    inner class GetProfile {

        @Test
        fun `should return profile for existing user`() {
            val userId = UUID.randomUUID()
            val user = testUser.copy(id = userId)

            every { userRepository.findById(userId) } returns user
            every { userRepository.findCountryById(any()) } returns null

            val profile = profileService.getProfile(userId)

            assertNotNull(profile)
            assertEquals(userId, profile.id)
            assertEquals("test@example.com", profile.email)
        }

        @Test
        fun `should throw ProfileNotFoundException for non-existent user`() {
            val userId = UUID.randomUUID()

            every { userRepository.findById(userId) } returns null

            assertThrows<ProfileNotFoundException> {
                profileService.getProfile(userId)
            }
        }

        @Test
        fun `should include country when set`() {
            val userId = UUID.randomUUID()
            val user = testUser.copy(id = userId, countryId = 1)
            val country = Country(1, "US", "United States")

            every { userRepository.findById(userId) } returns user
            every { userRepository.findCountryById(1) } returns country

            val profile = profileService.getProfile(userId)

            assertNotNull(profile.country)
            assertEquals("US", profile.country?.code)
            assertEquals("United States", profile.country?.name)
        }
    }

    @Nested
    @DisplayName("updateProfile")
    inner class UpdateProfile {

        @Test
        fun `should update profile name`() {
            val userId = UUID.randomUUID()
            val updatedUser = testUser.copy(id = userId, name = "Updated Name")

            every { userRepository.updateProfile(userId, "Updated Name", null, null) } returns updatedUser
            every { userRepository.findCountryById(any()) } returns null

            val request = UpdateProfileRequest(name = "Updated Name")
            val profile = profileService.updateProfile(userId, request)

            assertEquals("Updated Name", profile.name)
        }

        @Test
        fun `should update country by code`() {
            val userId = UUID.randomUUID()
            val country = Country(1, "US", "United States")
            val updatedUser = testUser.copy(id = userId, countryId = 1)

            every { userRepository.findCountryByCode("US") } returns country
            every { userRepository.updateProfile(userId, null, null, 1) } returns updatedUser
            every { userRepository.findCountryById(1) } returns country

            val request = UpdateProfileRequest(countryCode = "US")
            val profile = profileService.updateProfile(userId, request)

            assertNotNull(profile.country)
            assertEquals("US", profile.country?.code)
        }

        @Test
        fun `should throw InvalidCountryException for invalid country code`() {
            val userId = UUID.randomUUID()

            every { userRepository.findCountryByCode("XX") } returns null

            val request = UpdateProfileRequest(countryCode = "XX")

            assertThrows<InvalidCountryException> {
                profileService.updateProfile(userId, request)
            }
        }
    }

    @Nested
    @DisplayName("updateMetrics")
    inner class UpdateMetrics {

        @Test
        fun `should update profile metrics`() {
            val userId = UUID.randomUUID()
            val dateOfBirth = LocalDate.of(1990, 5, 15)
            val updatedUser = testUser.copy(
                id = userId,
                dateOfBirth = dateOfBirth,
                sex = Sex.MALE,
                heightCm = BigDecimal("180.5")
            )

            every { userRepository.updateMetrics(userId, dateOfBirth, Sex.MALE, BigDecimal("180.5")) } returns updatedUser

            val request = UpdateProfileMetricsRequest(
                dateOfBirth = dateOfBirth,
                sex = Sex.MALE,
                heightCm = BigDecimal("180.5")
            )
            val response = profileService.updateMetrics(userId, request)

            assertEquals(dateOfBirth, response.dateOfBirth)
            assertEquals(Sex.MALE, response.sex)
            assertEquals(BigDecimal("180.5"), response.heightCm)
            // Age calculation depends on current date - just verify it's reasonable
            assertTrue(response.age >= 35 && response.age <= 37) // Account for date variations
        }

        @Test
        fun `should reject users under 13 years old`() {
            val userId = UUID.randomUUID()
            val dateOfBirth = LocalDate.now().minusYears(10) // 10 years old

            val request = UpdateProfileMetricsRequest(
                dateOfBirth = dateOfBirth,
                sex = Sex.MALE,
                heightCm = BigDecimal("150")
            )

            assertThrows<InvalidFitnessGoalException> {
                profileService.updateMetrics(userId, request)
            }
        }
    }

    @Nested
    @DisplayName("updateActivityLevel")
    inner class UpdateActivityLevel {

        @Test
        fun `should update activity level`() {
            val userId = UUID.randomUUID()
            val updatedUser = testUser.copy(id = userId, defaultActivityLevel = ActivityLevel.HARD)

            every { userRepository.updateActivityLevel(userId, ActivityLevel.HARD) } returns updatedUser

            val request = UpdateActivityLevelRequest(activityLevel = ActivityLevel.HARD)
            val response = profileService.updateActivityLevel(userId, request)

            assertEquals(ActivityLevel.HARD, response.activityLevel.level)
            assertEquals(1.725, response.activityLevel.multiplier)
        }

        @Test
        fun `should throw ProfileNotFoundException for non-existent user`() {
            val userId = UUID.randomUUID()

            every { userRepository.updateActivityLevel(userId, any()) } returns null

            val request = UpdateActivityLevelRequest(activityLevel = ActivityLevel.HARD)

            assertThrows<ProfileNotFoundException> {
                profileService.updateActivityLevel(userId, request)
            }
        }
    }

    @Nested
    @DisplayName("updateFitnessGoal")
    inner class UpdateFitnessGoal {

        @Test
        fun `should update fitness goal to LOSE`() {
            val userId = UUID.randomUUID()
            val updatedUser = testUser.copy(
                id = userId,
                fitnessGoalType = FitnessGoalType.LOSE,
                fitnessGoalIntensity = FitnessGoalIntensity.NORMAL
            )

            every { userRepository.updateFitnessGoal(userId, FitnessGoalType.LOSE, FitnessGoalIntensity.NORMAL) } returns updatedUser

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.LOSE,
                intensity = FitnessGoalIntensity.NORMAL
            )
            val response = profileService.updateFitnessGoal(userId, request)

            assertEquals(FitnessGoalType.LOSE, response.fitnessGoal.type)
            assertEquals(FitnessGoalIntensity.NORMAL, response.fitnessGoal.intensity)
            assertEquals(-500, response.fitnessGoal.dailyCalorieAdjustment)
        }

        @Test
        fun `should update fitness goal to MAINTAIN`() {
            val userId = UUID.randomUUID()
            val updatedUser = testUser.copy(
                id = userId,
                fitnessGoalType = FitnessGoalType.MAINTAIN,
                fitnessGoalIntensity = null
            )

            every { userRepository.updateFitnessGoal(userId, FitnessGoalType.MAINTAIN, null) } returns updatedUser

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.MAINTAIN,
                intensity = null
            )
            val response = profileService.updateFitnessGoal(userId, request)

            assertEquals(FitnessGoalType.MAINTAIN, response.fitnessGoal.type)
            assertNull(response.fitnessGoal.intensity)
            assertEquals(0, response.fitnessGoal.dailyCalorieAdjustment)
        }

        @Test
        fun `should throw InvalidFitnessGoalException when MAINTAIN has intensity`() {
            val userId = UUID.randomUUID()

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.MAINTAIN,
                intensity = FitnessGoalIntensity.NORMAL
            )

            assertThrows<InvalidFitnessGoalException> {
                profileService.updateFitnessGoal(userId, request)
            }
        }

        @Test
        fun `should throw InvalidFitnessGoalException when LOSE has no intensity`() {
            val userId = UUID.randomUUID()

            val request = UpdateFitnessGoalRequest(
                type = FitnessGoalType.LOSE,
                intensity = null
            )

            assertThrows<InvalidFitnessGoalException> {
                profileService.updateFitnessGoal(userId, request)
            }
        }
    }

    @Nested
    @DisplayName("getActivityLevels")
    inner class GetActivityLevels {

        @Test
        fun `should return all activity levels`() {
            val response = profileService.getActivityLevels()

            assertEquals(6, response.activityLevels.size)
            assertEquals(ActivityLevel.SEDENTARY, response.activityLevels[0].level)
            assertEquals(1.2, response.activityLevels[0].multiplier)
        }
    }

    @Nested
    @DisplayName("getFitnessGoals")
    inner class GetFitnessGoals {

        @Test
        fun `should return all fitness goals`() {
            val response = profileService.getFitnessGoals()

            // 1 MAINTAIN + 4 LOSE intensities + 4 GAIN intensities = 9
            assertEquals(9, response.fitnessGoals.size)

            // First should be MAINTAIN
            assertEquals(FitnessGoalType.MAINTAIN, response.fitnessGoals[0].type)
            assertEquals(0, response.fitnessGoals[0].adjustment)

            // Check LOSE SLOW
            val loseSlow = response.fitnessGoals.find {
                it.type == FitnessGoalType.LOSE && it.intensity == FitnessGoalIntensity.SLOW
            }
            assertNotNull(loseSlow)
            assertEquals(-250, loseSlow!!.adjustment)

            // Check GAIN EXTREME
            val gainExtreme = response.fitnessGoals.find {
                it.type == FitnessGoalType.GAIN && it.intensity == FitnessGoalIntensity.EXTREME
            }
            assertNotNull(gainExtreme)
            assertEquals(1000, gainExtreme!!.adjustment)
        }
    }

    @Nested
    @DisplayName("getCountries")
    inner class GetCountries {

        @Test
        fun `should return all countries when no search`() {
            val countries = listOf(
                Country(1, "US", "United States"),
                Country(2, "CA", "Canada")
            )

            every { userRepository.findAllCountries() } returns countries

            val response = profileService.getCountries(null)

            assertEquals(2, response.countries.size)
            verify { userRepository.findAllCountries() }
        }

        @Test
        fun `should filter countries by search`() {
            val countries = listOf(Country(1, "US", "United States"))

            every { userRepository.searchCountries("United") } returns countries

            val response = profileService.getCountries("United")

            assertEquals(1, response.countries.size)
            assertEquals("United States", response.countries[0].name)
            verify { userRepository.searchCountries("United") }
        }
    }
}
