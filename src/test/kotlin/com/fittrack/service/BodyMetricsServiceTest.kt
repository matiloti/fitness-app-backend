package com.fittrack.service

import com.fittrack.exception.BodyMetricsNotFoundException
import com.fittrack.exception.BodyMetricsNotOwnedException
import com.fittrack.exception.NoMeasurementsProvidedException
import com.fittrack.exception.PhotoNotFoundException
import com.fittrack.model.BodyMetrics
import com.fittrack.model.PhotoPosition
import com.fittrack.model.ProgressPhoto
import com.fittrack.model.dto.bodymetrics.CreateBodyMetricsRequest
import com.fittrack.model.dto.bodymetrics.CreatePhotoRequest
import com.fittrack.model.dto.bodymetrics.UpdateBodyMetricsRequest
import com.fittrack.repository.BodyMetricsRepository
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

@DisplayName("Body Metrics Service Tests")
class BodyMetricsServiceTest {

    private lateinit var bodyMetricsService: BodyMetricsService
    private lateinit var bodyMetricsRepository: BodyMetricsRepository

    private val testProfileId = UUID.randomUUID()
    private val otherProfileId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val testMetrics = BodyMetrics(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        date = today,
        weightKg = BigDecimal("82.5"),
        bodyFatPercentage = BigDecimal("18.5"),
        bodyFatKg = BigDecimal("15.26"),
        muscleMassPercentage = BigDecimal("42.0"),
        muscleMassKg = BigDecimal("34.65"),
        notes = "Morning measurement",
        createdAt = now,
        updatedAt = now
    )

    private val testPhoto = ProgressPhoto(
        id = UUID.randomUUID(),
        bodyMetricsId = testMetrics.id,
        position = PhotoPosition.FRONT,
        imageUrl = "https://example.com/photo.jpg",
        createdAt = now
    )

    @BeforeEach
    fun setup() {
        bodyMetricsRepository = mockk()
        bodyMetricsService = BodyMetricsService(bodyMetricsRepository)
    }

    @Nested
    @DisplayName("getBodyMetrics")
    inner class GetBodyMetrics {

        @Test
        fun `should return paginated body metrics list`() {
            val startDate = today.minusDays(30)
            val endDate = today

            every { bodyMetricsRepository.findAllByProfileId(testProfileId, startDate, endDate, 0, 30, null) } returns listOf(testMetrics)
            every { bodyMetricsRepository.countByProfileId(testProfileId, startDate, endDate, null) } returns 1L
            every { bodyMetricsRepository.countPhotosByMetricsId(testMetrics.id) } returns 2

            val response = bodyMetricsService.getBodyMetrics(
                profileId = testProfileId,
                startDate = startDate,
                endDate = endDate,
                page = 0,
                size = 30,
                hasPhotos = null
            )

            assertNotNull(response)
            assertEquals(1, response.content.size)
            assertEquals(testMetrics.date, response.content[0].date)
            assertEquals(2, response.content[0].photoCount)
            assertTrue(response.content[0].hasPhotos)
            assertEquals(0, response.page.number)
        }

        @Test
        fun `should clamp page size to 100`() {
            val startDate = today.minusDays(30)
            val endDate = today

            every { bodyMetricsRepository.findAllByProfileId(testProfileId, startDate, endDate, 0, 100, null) } returns emptyList()
            every { bodyMetricsRepository.countByProfileId(testProfileId, startDate, endDate, null) } returns 0L

            val response = bodyMetricsService.getBodyMetrics(
                profileId = testProfileId,
                startDate = startDate,
                endDate = endDate,
                page = 0,
                size = 200,
                hasPhotos = null
            )

            assertEquals(100, response.page.size)
        }

        @Test
        fun `should calculate summary when entries exist`() {
            val startDate = today.minusDays(30)
            val endDate = today
            val olderMetrics = testMetrics.copy(
                id = UUID.randomUUID(),
                date = today.minusDays(7),
                weightKg = BigDecimal("85.0"),
                bodyFatPercentage = BigDecimal("20.0")
            )

            every { bodyMetricsRepository.findAllByProfileId(testProfileId, startDate, endDate, 0, 30, null) } returns listOf(testMetrics, olderMetrics)
            every { bodyMetricsRepository.countByProfileId(testProfileId, startDate, endDate, null) } returns 2L
            every { bodyMetricsRepository.countPhotosByMetricsId(any()) } returns 0

            val response = bodyMetricsService.getBodyMetrics(
                profileId = testProfileId,
                startDate = startDate,
                endDate = endDate,
                page = 0,
                size = 30,
                hasPhotos = null
            )

            assertNotNull(response.summary)
            assertEquals(BigDecimal("82.5"), response.summary?.latestWeight)
            assertEquals(BigDecimal("85.0"), response.summary?.oldestWeight)
            assertEquals(BigDecimal("-2.5"), response.summary?.weightChange)
        }
    }

    @Nested
    @DisplayName("getBodyMetricsById")
    inner class GetBodyMetricsById {

        @Test
        fun `should return body metrics details`() {
            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.findPhotosByMetricsId(testMetrics.id) } returns listOf(testPhoto)

            val response = bodyMetricsService.getBodyMetricsById(testMetrics.id, testProfileId)

            assertNotNull(response)
            assertEquals(testMetrics.id, response.id)
            assertEquals(testMetrics.weightKg, response.weightKg)
            assertEquals(1, response.photos.size)
            assertEquals(PhotoPosition.FRONT, response.photos[0].position)
        }

        @Test
        fun `should throw BodyMetricsNotFoundException when not found`() {
            val nonExistentId = UUID.randomUUID()
            every { bodyMetricsRepository.findById(nonExistentId) } returns null

            assertThrows<BodyMetricsNotFoundException> {
                bodyMetricsService.getBodyMetricsById(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw BodyMetricsNotOwnedException when belongs to another user`() {
            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics

            assertThrows<BodyMetricsNotOwnedException> {
                bodyMetricsService.getBodyMetricsById(testMetrics.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("createBodyMetrics")
    inner class CreateBodyMetrics {

        @Test
        fun `should create new body metrics entry`() {
            val request = CreateBodyMetricsRequest(
                date = today,
                weightKg = BigDecimal("82.5"),
                bodyFatPercentage = BigDecimal("18.5"),
                muscleMassPercentage = BigDecimal("42.0"),
                notes = "Morning measurement"
            )

            every { bodyMetricsRepository.findByDateAndProfileId(today, testProfileId) } returns null
            every { bodyMetricsRepository.create(
                testProfileId, today,
                BigDecimal("82.5"), BigDecimal("18.5"), any(),
                BigDecimal("42.0"), any(), "Morning measurement"
            ) } returns testMetrics
            every { bodyMetricsRepository.findPhotosByMetricsId(testMetrics.id) } returns emptyList()

            val result = bodyMetricsService.createBodyMetrics(request, testProfileId)

            assertNotNull(result.response)
            assertTrue(result.isCreated)
            assertEquals(testMetrics.weightKg, result.response.weightKg)
        }

        @Test
        fun `should update existing body metrics for same date (upsert)`() {
            val existingMetrics = testMetrics.copy(weightKg = BigDecimal("83.0"))
            val request = CreateBodyMetricsRequest(
                date = today,
                weightKg = BigDecimal("82.5"),
                bodyFatPercentage = BigDecimal("18.5"),
                muscleMassPercentage = BigDecimal("42.0")
            )

            every { bodyMetricsRepository.findByDateAndProfileId(today, testProfileId) } returns existingMetrics
            every { bodyMetricsRepository.update(
                existingMetrics.id,
                any(), any(), any(), any(), any(), any(),
                updateWeight = true, updateBodyFat = true, updateMuscleMass = true, updateNotes = false
            ) } returns testMetrics
            every { bodyMetricsRepository.findPhotosByMetricsId(existingMetrics.id) } returns emptyList()

            val result = bodyMetricsService.createBodyMetrics(request, testProfileId)

            assertNotNull(result.response)
            assertEquals(false, result.isCreated)
        }

        @Test
        fun `should throw NoMeasurementsProvidedException when no measurements provided`() {
            val request = CreateBodyMetricsRequest(
                date = today,
                notes = "Just a note"
            )

            assertThrows<NoMeasurementsProvidedException> {
                bodyMetricsService.createBodyMetrics(request, testProfileId)
            }
        }

        @Test
        fun `should calculate body fat kg from percentage and weight`() {
            val request = CreateBodyMetricsRequest(
                date = today,
                weightKg = BigDecimal("100.00"),
                bodyFatPercentage = BigDecimal("20.00")
            )

            every { bodyMetricsRepository.findByDateAndProfileId(today, testProfileId) } returns null
            every { bodyMetricsRepository.create(
                testProfileId, today,
                BigDecimal("100.00"), BigDecimal("20.00"), BigDecimal("20.00"),
                null, null, null
            ) } returns testMetrics.copy(
                weightKg = BigDecimal("100.00"),
                bodyFatPercentage = BigDecimal("20.00"),
                bodyFatKg = BigDecimal("20.00")
            )
            every { bodyMetricsRepository.findPhotosByMetricsId(any()) } returns emptyList()

            val result = bodyMetricsService.createBodyMetrics(request, testProfileId)

            verify { bodyMetricsRepository.create(
                testProfileId, today,
                BigDecimal("100.00"), BigDecimal("20.00"), BigDecimal("20.00"),
                null, null, null
            ) }
        }
    }

    @Nested
    @DisplayName("updateBodyMetrics")
    inner class UpdateBodyMetrics {

        @Test
        fun `should update body metrics successfully`() {
            val request = UpdateBodyMetricsRequest(
                weightKg = BigDecimal("81.5"),
                notes = "Updated measurement"
            )
            val updatedMetrics = testMetrics.copy(weightKg = BigDecimal("81.5"), notes = "Updated measurement")

            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.update(
                testMetrics.id,
                any(), any(), any(), any(), any(), "Updated measurement",
                updateWeight = true, updateBodyFat = false, updateMuscleMass = false, updateNotes = true
            ) } returns updatedMetrics
            every { bodyMetricsRepository.findPhotosByMetricsId(testMetrics.id) } returns emptyList()

            val response = bodyMetricsService.updateBodyMetrics(testMetrics.id, request, testProfileId)

            assertNotNull(response)
            assertEquals(BigDecimal("81.5"), response.weightKg)
        }

        @Test
        fun `should throw BodyMetricsNotFoundException when not found`() {
            val nonExistentId = UUID.randomUUID()
            val request = UpdateBodyMetricsRequest(weightKg = BigDecimal("81.5"))

            every { bodyMetricsRepository.findById(nonExistentId) } returns null

            assertThrows<BodyMetricsNotFoundException> {
                bodyMetricsService.updateBodyMetrics(nonExistentId, request, testProfileId)
            }
        }

        @Test
        fun `should throw BodyMetricsNotOwnedException when belongs to another user`() {
            val request = UpdateBodyMetricsRequest(weightKg = BigDecimal("81.5"))

            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics

            assertThrows<BodyMetricsNotOwnedException> {
                bodyMetricsService.updateBodyMetrics(testMetrics.id, request, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("deleteBodyMetrics")
    inner class DeleteBodyMetrics {

        @Test
        fun `should delete body metrics successfully`() {
            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.deletePhotosByMetricsId(testMetrics.id) } returns 2
            every { bodyMetricsRepository.delete(testMetrics.id) } returns true

            bodyMetricsService.deleteBodyMetrics(testMetrics.id, testProfileId)

            verify { bodyMetricsRepository.delete(testMetrics.id) }
        }

        @Test
        fun `should throw BodyMetricsNotFoundException when not found`() {
            val nonExistentId = UUID.randomUUID()

            every { bodyMetricsRepository.findById(nonExistentId) } returns null

            assertThrows<BodyMetricsNotFoundException> {
                bodyMetricsService.deleteBodyMetrics(nonExistentId, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("getLatestBodyMetrics")
    inner class GetLatestBodyMetrics {

        @Test
        fun `should return latest body metrics`() {
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testMetrics
            every { bodyMetricsRepository.countPhotosByMetricsId(testMetrics.id) } returns 2

            val response = bodyMetricsService.getLatestBodyMetrics(testProfileId)

            assertNotNull(response)
            assertEquals(testMetrics.id, response?.id)
            assertEquals(true, response?.hasPhotos)
        }

        @Test
        fun `should return null when no entries exist`() {
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns null

            val response = bodyMetricsService.getLatestBodyMetrics(testProfileId)

            assertNull(response)
        }
    }

    @Nested
    @DisplayName("getTrends")
    inner class GetTrends {

        @Test
        fun `should return trends for period`() {
            val startDate = today.minusDays(30)
            val olderMetrics = testMetrics.copy(
                id = UUID.randomUUID(),
                date = startDate,
                weightKg = BigDecimal("85.0"),
                bodyFatPercentage = BigDecimal("20.0"),
                muscleMassPercentage = BigDecimal("40.0")
            )

            every { bodyMetricsRepository.findForTrends(testProfileId, any(), today) } returns listOf(olderMetrics, testMetrics)

            val response = bodyMetricsService.getTrends(testProfileId, "30d")

            assertNotNull(response)
            assertEquals("30d", response.period)
            assertEquals(2, response.dataPoints)
            assertNotNull(response.weight)
            assertEquals(BigDecimal("85.0"), response.weight?.start)
            assertEquals(BigDecimal("82.5"), response.weight?.end)
        }

        @Test
        fun `should handle empty data for trends`() {
            every { bodyMetricsRepository.findForTrends(testProfileId, any(), today) } returns emptyList()

            val response = bodyMetricsService.getTrends(testProfileId, "30d")

            assertNotNull(response)
            assertEquals(0, response.dataPoints)
            assertNull(response.weight)
        }
    }

    @Nested
    @DisplayName("addPhoto")
    inner class AddPhoto {

        @Test
        fun `should add photo to body metrics`() {
            val request = CreatePhotoRequest(
                position = PhotoPosition.FRONT,
                imageUrl = "https://example.com/new-photo.jpg"
            )

            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.findPhotoByMetricsIdAndPosition(testMetrics.id, PhotoPosition.FRONT) } returns null
            every { bodyMetricsRepository.createPhoto(testMetrics.id, PhotoPosition.FRONT, "https://example.com/new-photo.jpg") } returns testPhoto

            val response = bodyMetricsService.addPhoto(testMetrics.id, request, testProfileId)

            assertNotNull(response)
            assertEquals(PhotoPosition.FRONT, response.position)
        }

        @Test
        fun `should replace existing photo for same position`() {
            val existingPhoto = testPhoto
            val request = CreatePhotoRequest(
                position = PhotoPosition.FRONT,
                imageUrl = "https://example.com/new-photo.jpg"
            )
            val updatedPhoto = testPhoto.copy(imageUrl = "https://example.com/new-photo.jpg")

            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.findPhotoByMetricsIdAndPosition(testMetrics.id, PhotoPosition.FRONT) } returns existingPhoto
            every { bodyMetricsRepository.updatePhoto(existingPhoto.id, "https://example.com/new-photo.jpg") } returns updatedPhoto

            val response = bodyMetricsService.addPhoto(testMetrics.id, request, testProfileId)

            assertNotNull(response)
            assertEquals("https://example.com/new-photo.jpg", response.imageUrl)
        }

        @Test
        fun `should throw BodyMetricsNotFoundException when metrics not found`() {
            val nonExistentId = UUID.randomUUID()
            val request = CreatePhotoRequest(
                position = PhotoPosition.FRONT,
                imageUrl = "https://example.com/photo.jpg"
            )

            every { bodyMetricsRepository.findById(nonExistentId) } returns null

            assertThrows<BodyMetricsNotFoundException> {
                bodyMetricsService.addPhoto(nonExistentId, request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("getPhotos")
    inner class GetPhotos {

        @Test
        fun `should return photos for body metrics entry`() {
            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.findPhotosByMetricsId(testMetrics.id) } returns listOf(testPhoto)

            val response = bodyMetricsService.getPhotos(testMetrics.id, testProfileId)

            assertNotNull(response)
            assertEquals(1, response.size)
            assertEquals(PhotoPosition.FRONT, response[0].position)
        }
    }

    @Nested
    @DisplayName("deletePhoto")
    inner class DeletePhoto {

        @Test
        fun `should delete photo successfully`() {
            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.findPhotoByIdAndMetricsId(testPhoto.id, testMetrics.id) } returns testPhoto
            every { bodyMetricsRepository.deletePhoto(testPhoto.id) } returns true

            bodyMetricsService.deletePhoto(testMetrics.id, testPhoto.id, testProfileId)

            verify { bodyMetricsRepository.deletePhoto(testPhoto.id) }
        }

        @Test
        fun `should throw PhotoNotFoundException when photo not found`() {
            val nonExistentPhotoId = UUID.randomUUID()

            every { bodyMetricsRepository.findById(testMetrics.id) } returns testMetrics
            every { bodyMetricsRepository.findPhotoByIdAndMetricsId(nonExistentPhotoId, testMetrics.id) } returns null

            assertThrows<PhotoNotFoundException> {
                bodyMetricsService.deletePhoto(testMetrics.id, nonExistentPhotoId, testProfileId)
            }
        }
    }
}
