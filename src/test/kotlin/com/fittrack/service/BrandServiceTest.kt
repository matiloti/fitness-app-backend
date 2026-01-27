package com.fittrack.service

import com.fittrack.exception.BrandAlreadyExistsException
import com.fittrack.exception.BrandNotFoundException
import com.fittrack.exception.BrandNotOwnedException
import com.fittrack.exception.InvalidCountryException
import com.fittrack.model.Brand
import com.fittrack.model.Country
import com.fittrack.model.dto.food.CreateBrandRequest
import com.fittrack.model.dto.food.UpdateBrandRequest
import com.fittrack.repository.BrandRepository
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
import java.time.Instant
import java.util.UUID

@DisplayName("Brand Service Tests")
class BrandServiceTest {

    private lateinit var brandService: BrandService
    private lateinit var brandRepository: BrandRepository
    private lateinit var userRepository: UserRepository

    private val testProfileId = UUID.randomUUID()
    private val otherProfileId = UUID.randomUUID()
    private val now = Instant.now()

    private val testCountry = Country(
        id = 1,
        code = "US",
        name = "United States"
    )

    private val testBrand = Brand(
        id = 1,
        profileId = testProfileId,
        name = "Test Brand",
        description = "Test Description",
        photoUrl = "https://example.com/logo.jpg",
        countryId = 1,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setup() {
        brandRepository = mockk()
        userRepository = mockk()

        brandService = BrandService(brandRepository, userRepository)
    }

    @Nested
    @DisplayName("getBrands")
    inner class GetBrands {

        @Test
        fun `should return paginated brand list`() {
            every { brandRepository.findAllByProfileId(testProfileId, 0, 20, null) } returns listOf(testBrand)
            every { brandRepository.countByProfileId(testProfileId, null) } returns 1L
            every { userRepository.findCountryById(1) } returns testCountry

            val response = brandService.getBrands(testProfileId, 0, 20, null)

            assertNotNull(response)
            assertEquals(1, response.content.size)
            assertEquals(testBrand.name, response.content[0].name)
            assertEquals("US", response.content[0].country?.code)
        }

        @Test
        fun `should search brands by name`() {
            every { brandRepository.findAllByProfileId(testProfileId, 0, 20, "test") } returns listOf(testBrand)
            every { brandRepository.countByProfileId(testProfileId, "test") } returns 1L
            every { userRepository.findCountryById(1) } returns testCountry

            val response = brandService.getBrands(testProfileId, 0, 20, "test")

            assertEquals(1, response.content.size)
        }

        @Test
        fun `should clamp page size to 100`() {
            every { brandRepository.findAllByProfileId(testProfileId, 0, 100, null) } returns emptyList()
            every { brandRepository.countByProfileId(testProfileId, null) } returns 0L

            val response = brandService.getBrands(testProfileId, 0, 200, null)

            assertEquals(100, response.page.size)
        }
    }

    @Nested
    @DisplayName("getBrandById")
    inner class GetBrandById {

        @Test
        fun `should return brand details`() {
            every { brandRepository.findById(1) } returns testBrand
            every { userRepository.findCountryById(1) } returns testCountry

            val response = brandService.getBrandById(1, testProfileId)

            assertNotNull(response)
            assertEquals(testBrand.name, response.name)
            assertEquals("US", response.country?.code)
        }

        @Test
        fun `should throw BrandNotFoundException when brand does not exist`() {
            every { brandRepository.findById(999) } returns null

            assertThrows<BrandNotFoundException> {
                brandService.getBrandById(999, testProfileId)
            }
        }

        @Test
        fun `should throw BrandNotOwnedException when brand belongs to another user`() {
            every { brandRepository.findById(1) } returns testBrand

            assertThrows<BrandNotOwnedException> {
                brandService.getBrandById(1, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("createBrand")
    inner class CreateBrand {

        @Test
        fun `should create brand successfully`() {
            val request = CreateBrandRequest(
                name = "New Brand",
                description = "Description",
                photoUrl = "https://example.com/logo.jpg",
                countryCode = "US"
            )

            every { brandRepository.existsByProfileIdAndName(testProfileId, "New Brand") } returns false
            every { userRepository.findCountryByCode("US") } returns testCountry
            every { brandRepository.create(testProfileId, "New Brand", "Description", "https://example.com/logo.jpg", 1) } returns testBrand.copy(name = "New Brand")
            every { userRepository.findCountryById(1) } returns testCountry

            val response = brandService.createBrand(request, testProfileId)

            assertNotNull(response)
            assertEquals("New Brand", response.name)
        }

        @Test
        fun `should create brand without country`() {
            val request = CreateBrandRequest(
                name = "New Brand",
                description = "Description"
            )

            every { brandRepository.existsByProfileIdAndName(testProfileId, "New Brand") } returns false
            every { brandRepository.create(testProfileId, "New Brand", "Description", null, null) } returns testBrand.copy(name = "New Brand", countryId = null)

            val response = brandService.createBrand(request, testProfileId)

            assertNotNull(response)
            assertEquals("New Brand", response.name)
        }

        @Test
        fun `should throw BrandAlreadyExistsException when brand name exists`() {
            val request = CreateBrandRequest(name = "Test Brand")

            every { brandRepository.existsByProfileIdAndName(testProfileId, "Test Brand") } returns true

            assertThrows<BrandAlreadyExistsException> {
                brandService.createBrand(request, testProfileId)
            }
        }

        @Test
        fun `should throw InvalidCountryException when country code is invalid`() {
            val request = CreateBrandRequest(
                name = "New Brand",
                countryCode = "XX"
            )

            every { brandRepository.existsByProfileIdAndName(testProfileId, "New Brand") } returns false
            every { userRepository.findCountryByCode("XX") } returns null

            assertThrows<InvalidCountryException> {
                brandService.createBrand(request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("updateBrand")
    inner class UpdateBrand {

        @Test
        fun `should update brand successfully`() {
            val request = UpdateBrandRequest(name = "Updated Brand")
            val updatedBrand = testBrand.copy(name = "Updated Brand")

            every { brandRepository.findById(1) } returns testBrand
            every { brandRepository.existsByProfileIdAndNameExcludingId(testProfileId, "Updated Brand", 1) } returns false
            every { brandRepository.update(1, "Updated Brand", null, null, null) } returns updatedBrand
            every { userRepository.findCountryById(1) } returns testCountry

            val response = brandService.updateBrand(1, request, testProfileId)

            assertNotNull(response)
            assertEquals("Updated Brand", response.name)
        }

        @Test
        fun `should throw BrandNotFoundException when brand does not exist`() {
            val request = UpdateBrandRequest(name = "Updated")

            every { brandRepository.findById(999) } returns null

            assertThrows<BrandNotFoundException> {
                brandService.updateBrand(999, request, testProfileId)
            }
        }

        @Test
        fun `should throw BrandNotOwnedException when brand belongs to another user`() {
            val request = UpdateBrandRequest(name = "Updated")

            every { brandRepository.findById(1) } returns testBrand

            assertThrows<BrandNotOwnedException> {
                brandService.updateBrand(1, request, otherProfileId)
            }
        }

        @Test
        fun `should throw BrandAlreadyExistsException when new name conflicts`() {
            val request = UpdateBrandRequest(name = "Existing Brand")

            every { brandRepository.findById(1) } returns testBrand
            every { brandRepository.existsByProfileIdAndNameExcludingId(testProfileId, "Existing Brand", 1) } returns true

            assertThrows<BrandAlreadyExistsException> {
                brandService.updateBrand(1, request, testProfileId)
            }
        }

        @Test
        fun `should update country successfully`() {
            val request = UpdateBrandRequest(countryCode = "CA")
            val canadaCountry = Country(id = 2, code = "CA", name = "Canada")
            val updatedBrand = testBrand.copy(countryId = 2)

            every { brandRepository.findById(1) } returns testBrand
            every { userRepository.findCountryByCode("CA") } returns canadaCountry
            every { brandRepository.update(1, null, null, null, 2) } returns updatedBrand
            every { userRepository.findCountryById(2) } returns canadaCountry

            val response = brandService.updateBrand(1, request, testProfileId)

            assertNotNull(response)
            assertEquals("CA", response.country?.code)
        }
    }
}
