package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.PhotoPosition
import com.fittrack.model.dto.bodymetrics.CreateBodyMetricsRequest
import com.fittrack.model.dto.bodymetrics.CreatePhotoRequest
import com.fittrack.model.dto.bodymetrics.UpdateBodyMetricsRequest
import com.fittrack.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Body Metrics Controller Integration Tests")
class BodyMetricsControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    private lateinit var testUserId: UUID
    private lateinit var accessToken: String
    private lateinit var testEmail: String
    private val today = LocalDate.now()

    @BeforeEach
    fun setupTestUser() {
        testUserId = UUID.randomUUID()
        testEmail = "bodymetrics-test-${UUID.randomUUID()}@example.com"
        val passwordHash = passwordEncoder.encode("Test123!@#")

        jdbcTemplate.update(
            """
            INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """,
            testUserId,
            testEmail,
            passwordHash,
            "Test User"
        )

        accessToken = jwtService.generateAccessToken(testUserId, testEmail)
    }

    private fun createBodyMetricsEntry(
        date: LocalDate = today,
        weightKg: BigDecimal = BigDecimal("82.5"),
        bodyFatPercentage: BigDecimal? = BigDecimal("18.5"),
        muscleMassPercentage: BigDecimal? = BigDecimal("42.0")
    ): UUID {
        val id = UUID.randomUUID()
        val bodyFatKg = bodyFatPercentage?.let { weightKg.multiply(it).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP) }
        val muscleMassKg = muscleMassPercentage?.let { weightKg.multiply(it).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP) }

        jdbcTemplate.update(
            """
            INSERT INTO body_metrics (id, profile_id, date, weight_kg, body_fat_percentage, body_fat_kg,
                                       muscle_mass_percentage, muscle_mass_kg, notes, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            id, testUserId, date, weightKg, bodyFatPercentage, bodyFatKg,
            muscleMassPercentage, muscleMassKg, "Test entry"
        )
        return id
    }

    private fun createProgressPhoto(metricsId: UUID, position: PhotoPosition = PhotoPosition.FRONT): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO progress_photos (id, body_metrics_id, position, image_url, created_at)
            VALUES (?, ?, ?::photo_position, ?, NOW())
            """,
            id, metricsId, position.name, "https://example.com/photo.jpg"
        )
        return id
    }

    @Nested
    @DisplayName("GET /api/v1/body-metrics")
    inner class GetBodyMetrics {

        @Test
        fun `should return paginated body metrics list`() {
            val entry1 = createBodyMetricsEntry(today)
            val entry2 = createBodyMetricsEntry(today.minusDays(1))
            createProgressPhoto(entry1, PhotoPosition.FRONT)

            mockMvc.perform(
                get("/api/v1/body-metrics")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].date").value(today.toString()))
                .andExpect(jsonPath("$.content[0].hasPhotos").value(true))
                .andExpect(jsonPath("$.content[0].photoCount").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.summary").exists())
        }

        @Test
        fun `should filter by date range`() {
            createBodyMetricsEntry(today)
            createBodyMetricsEntry(today.minusDays(10))
            createBodyMetricsEntry(today.minusDays(60)) // Outside range

            mockMvc.perform(
                get("/api/v1/body-metrics")
                    .param("startDate", today.minusDays(30).toString())
                    .param("endDate", today.toString())
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
        }

        @Test
        fun `should filter by hasPhotos`() {
            val entryWithPhoto = createBodyMetricsEntry(today)
            createBodyMetricsEntry(today.minusDays(1)) // No photo
            createProgressPhoto(entryWithPhoto)

            mockMvc.perform(
                get("/api/v1/body-metrics")
                    .param("hasPhotos", "true")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].hasPhotos").value(true))
        }

        @Test
        fun `should return 401 without authentication`() {
            mockMvc.perform(get("/api/v1/body-metrics"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/body-metrics/{id}")
    inner class GetBodyMetricsById {

        @Test
        fun `should return body metrics details with photos`() {
            val metricsId = createBodyMetricsEntry()
            createProgressPhoto(metricsId, PhotoPosition.FRONT)
            createProgressPhoto(metricsId, PhotoPosition.BACK)

            mockMvc.perform(
                get("/api/v1/body-metrics/$metricsId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(metricsId.toString()))
                .andExpect(jsonPath("$.weightKg").value(82.5))
                .andExpect(jsonPath("$.photos").isArray)
                .andExpect(jsonPath("$.photos.length()").value(2))
        }

        @Test
        fun `should return 404 for non-existent entry`() {
            mockMvc.perform(
                get("/api/v1/body-metrics/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/body-metrics")
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

            mockMvc.perform(
                post("/api/v1/body-metrics")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.weightKg").value(82.5))
                .andExpect(jsonPath("$.bodyFatPercentage").value(18.5))
                .andExpect(jsonPath("$.bodyFatKg").exists())
                .andExpect(jsonPath("$.muscleMassPercentage").value(42.0))
                .andExpect(jsonPath("$.muscleMassKg").exists())
        }

        @Test
        fun `should update existing entry for same date (upsert)`() {
            createBodyMetricsEntry(today, BigDecimal("83.0"))

            val request = CreateBodyMetricsRequest(
                date = today,
                weightKg = BigDecimal("82.5")
            )

            mockMvc.perform(
                post("/api/v1/body-metrics")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk) // 200 for update
                .andExpect(jsonPath("$.weightKg").value(82.5))
        }

        @Test
        fun `should return 400 when no measurements provided`() {
            val request = CreateBodyMetricsRequest(
                date = today,
                notes = "Just a note"
            )

            mockMvc.perform(
                post("/api/v1/body-metrics")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("NO_MEASUREMENTS"))
        }

        @Test
        fun `should calculate body fat kg from percentage`() {
            val request = CreateBodyMetricsRequest(
                date = today,
                weightKg = BigDecimal("100.00"),
                bodyFatPercentage = BigDecimal("20.00")
            )

            mockMvc.perform(
                post("/api/v1/body-metrics")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.bodyFatKg").value(20.0))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/body-metrics/{id}")
    inner class UpdateBodyMetrics {

        @Test
        fun `should update body metrics`() {
            val metricsId = createBodyMetricsEntry()

            val request = UpdateBodyMetricsRequest(
                weightKg = BigDecimal("81.5"),
                notes = "Updated measurement"
            )

            mockMvc.perform(
                put("/api/v1/body-metrics/$metricsId")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.weightKg").value(81.5))
                .andExpect(jsonPath("$.notes").value("Updated measurement"))
        }

        @Test
        fun `should return 404 for non-existent entry`() {
            val request = UpdateBodyMetricsRequest(weightKg = BigDecimal("81.5"))

            mockMvc.perform(
                put("/api/v1/body-metrics/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/body-metrics/{id}")
    inner class DeleteBodyMetrics {

        @Test
        fun `should delete body metrics entry and photos`() {
            val metricsId = createBodyMetricsEntry()
            createProgressPhoto(metricsId)

            mockMvc.perform(
                delete("/api/v1/body-metrics/$metricsId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNoContent)

            // Verify deleted
            mockMvc.perform(
                get("/api/v1/body-metrics/$metricsId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/body-metrics/latest")
    inner class GetLatestBodyMetrics {

        @Test
        fun `should return latest body metrics entry`() {
            createBodyMetricsEntry(today.minusDays(5))
            val latestId = createBodyMetricsEntry(today)
            createProgressPhoto(latestId)

            mockMvc.perform(
                get("/api/v1/body-metrics/latest")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(latestId.toString()))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.hasPhotos").value(true))
        }

        @Test
        fun `should return 404 when no entries exist`() {
            mockMvc.perform(
                get("/api/v1/body-metrics/latest")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/body-metrics/trends")
    inner class GetTrends {

        @Test
        fun `should return trends for period`() {
            createBodyMetricsEntry(today.minusDays(20), BigDecimal("85.0"), BigDecimal("20.0"), BigDecimal("40.0"))
            createBodyMetricsEntry(today.minusDays(10), BigDecimal("83.5"), BigDecimal("19.0"), BigDecimal("41.0"))
            createBodyMetricsEntry(today, BigDecimal("82.5"), BigDecimal("18.5"), BigDecimal("42.0"))

            mockMvc.perform(
                get("/api/v1/body-metrics/trends")
                    .param("period", "30d")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.dataPoints").value(3))
                .andExpect(jsonPath("$.weight.start").value(85.0))
                .andExpect(jsonPath("$.weight.end").value(82.5))
                .andExpect(jsonPath("$.weight.trend").value("DECREASING"))
                .andExpect(jsonPath("$.bodyFat").exists())
                .andExpect(jsonPath("$.muscleMass").exists())
        }

        @Test
        fun `should return empty trends when no data`() {
            mockMvc.perform(
                get("/api/v1/body-metrics/trends")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dataPoints").value(0))
                .andExpect(jsonPath("$.weight").doesNotExist())
        }
    }

    @Nested
    @DisplayName("Progress Photos API")
    inner class ProgressPhotosApi {

        @Test
        fun `should add photo to body metrics`() {
            val metricsId = createBodyMetricsEntry()

            val request = CreatePhotoRequest(
                position = PhotoPosition.FRONT,
                imageUrl = "https://example.com/new-photo.jpg"
            )

            mockMvc.perform(
                post("/api/v1/body-metrics/$metricsId/photos")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.position").value("FRONT"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/new-photo.jpg"))
        }

        @Test
        fun `should get photos for body metrics entry`() {
            val metricsId = createBodyMetricsEntry()
            createProgressPhoto(metricsId, PhotoPosition.FRONT)
            createProgressPhoto(metricsId, PhotoPosition.BACK)

            mockMvc.perform(
                get("/api/v1/body-metrics/$metricsId/photos")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `should delete photo`() {
            val metricsId = createBodyMetricsEntry()
            val photoId = createProgressPhoto(metricsId)

            mockMvc.perform(
                delete("/api/v1/body-metrics/$metricsId/photos/$photoId")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNoContent)

            // Verify deleted
            mockMvc.perform(
                get("/api/v1/body-metrics/$metricsId/photos")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should get photo timeline`() {
            val metrics1 = createBodyMetricsEntry(today)
            val metrics2 = createBodyMetricsEntry(today.minusDays(7))
            createProgressPhoto(metrics1, PhotoPosition.FRONT)
            createProgressPhoto(metrics2, PhotoPosition.FRONT)

            mockMvc.perform(
                get("/api/v1/body-metrics/photos")
                    .param("position", "FRONT")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.photos").isArray)
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].position").value("FRONT"))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/body-metrics/date/{date}")
    inner class GetBodyMetricsByDate {

        @Test
        fun `should return body metrics for specific date`() {
            val metricsId = createBodyMetricsEntry(today)

            mockMvc.perform(
                get("/api/v1/body-metrics/date/$today")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(metricsId.toString()))
                .andExpect(jsonPath("$.date").value(today.toString()))
        }

        @Test
        fun `should return 404 for date with no entry`() {
            mockMvc.perform(
                get("/api/v1/body-metrics/date/$today")
                    .header("Authorization", "Bearer $accessToken")
            )
                .andExpect(status().isNotFound)
        }
    }
}
