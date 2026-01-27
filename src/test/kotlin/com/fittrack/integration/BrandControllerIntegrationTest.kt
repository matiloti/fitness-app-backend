package com.fittrack.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fittrack.model.dto.food.CreateBrandRequest
import com.fittrack.model.dto.food.UpdateBrandRequest
import com.fittrack.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@DisplayName("Brand Controller Integration Tests")
class BrandControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var testUserId: UUID
    private lateinit var authToken: String

    @BeforeEach
    fun setupUser() {
        // Create a test user
        testUserId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, 'test@example.com', 'hash', 'Test User', NOW(), NOW())
            """.trimIndent(),
            testUserId
        )

        // Generate auth token
        authToken = jwtService.generateAccessToken(testUserId, "test@example.com")
    }

    @Nested
    @DisplayName("GET /api/v1/brands")
    inner class GetBrands {

        @Test
        fun `should return empty list when no brands exist`() {
            mockMvc.perform(
                get("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content").isEmpty)
                .andExpect(jsonPath("$.page.totalElements").value(0))
        }

        @Test
        fun `should return brands with pagination`() {
            createTestBrand("Brand A")
            createTestBrand("Brand B")

            mockMvc.perform(
                get("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .param("page", "0")
                    .param("size", "10")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
        }

        @Test
        fun `should search brands by name`() {
            createTestBrand("Organic Valley")
            createTestBrand("Nature's Best")

            mockMvc.perform(
                get("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .param("q", "organic")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Organic Valley"))
        }

        @Test
        fun `should require authentication`() {
            mockMvc.perform(get("/api/v1/brands"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/brands/{id}")
    inner class GetBrandById {

        @Test
        fun `should return brand details`() {
            val brandId = createTestBrand("Test Brand")

            mockMvc.perform(
                get("/api/v1/brands/$brandId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(brandId))
                .andExpect(jsonPath("$.name").value("Test Brand"))
        }

        @Test
        fun `should return brand with country`() {
            val brandId = createTestBrandWithCountry("US Brand", 1) // Assuming country ID 1 exists

            mockMvc.perform(
                get("/api/v1/brands/$brandId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.country").isNotEmpty)
        }

        @Test
        fun `should return 404 for non-existent brand`() {
            mockMvc.perform(
                get("/api/v1/brands/999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("BRAND_NOT_FOUND"))
        }

        @Test
        fun `should return 403 for brand owned by another user`() {
            val otherUserId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO profiles (id, email, password_hash, name, created_at, updated_at)
                VALUES (?, 'other@example.com', 'hash', 'Other User', NOW(), NOW())
                """.trimIndent(),
                otherUserId
            )

            val brandId = jdbcTemplate.queryForObject(
                """
                INSERT INTO brands (profile_id, name, created_at, updated_at)
                VALUES (?, 'Other Brand', NOW(), NOW())
                RETURNING id
                """.trimIndent(),
                Int::class.java,
                otherUserId
            )

            mockMvc.perform(
                get("/api/v1/brands/$brandId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("BRAND_NOT_OWNED"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/brands")
    inner class CreateBrand {

        @Test
        fun `should create brand successfully`() {
            val request = CreateBrandRequest(
                name = "New Brand",
                description = "A great brand"
            )

            mockMvc.perform(
                post("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.name").value("New Brand"))
                .andExpect(jsonPath("$.description").value("A great brand"))
        }

        @Test
        fun `should create brand with country`() {
            // First, get a valid country ID
            val countryId = jdbcTemplate.queryForObject(
                "SELECT id FROM countries WHERE code = 'US' LIMIT 1",
                Short::class.java
            )

            val request = CreateBrandRequest(
                name = "US Brand",
                countryCode = "US"
            )

            mockMvc.perform(
                post("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.country.code").value("US"))
        }

        @Test
        fun `should return 409 for duplicate brand name`() {
            createTestBrand("Existing Brand")

            val request = CreateBrandRequest(name = "Existing Brand")

            mockMvc.perform(
                post("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("BRAND_ALREADY_EXISTS"))
        }

        @Test
        fun `should return 400 for invalid country code`() {
            val request = CreateBrandRequest(
                name = "New Brand",
                countryCode = "XX"
            )

            mockMvc.perform(
                post("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_COUNTRY"))
        }

        @Test
        fun `should return 400 for missing name`() {
            val request = mapOf("description" to "No name")

            mockMvc.perform(
                post("/api/v1/brands")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/brands/{id}")
    inner class UpdateBrand {

        @Test
        fun `should update brand name`() {
            val brandId = createTestBrand("Original Name")

            val request = UpdateBrandRequest(name = "Updated Name")

            mockMvc.perform(
                put("/api/v1/brands/$brandId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Name"))
        }

        @Test
        fun `should update brand description`() {
            val brandId = createTestBrand("Test Brand")

            val request = UpdateBrandRequest(description = "New description")

            mockMvc.perform(
                put("/api/v1/brands/$brandId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.description").value("New description"))
        }

        @Test
        fun `should return 404 for non-existent brand`() {
            val request = UpdateBrandRequest(name = "Updated")

            mockMvc.perform(
                put("/api/v1/brands/999")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 409 when updating to existing name`() {
            createTestBrand("Existing Brand")
            val brandId = createTestBrand("My Brand")

            val request = UpdateBrandRequest(name = "Existing Brand")

            mockMvc.perform(
                put("/api/v1/brands/$brandId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("BRAND_ALREADY_EXISTS"))
        }
    }

    private fun createTestBrand(name: String): Int {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO brands (profile_id, name, created_at, updated_at)
            VALUES (?, ?, NOW(), NOW())
            RETURNING id
            """.trimIndent(),
            Int::class.java,
            testUserId,
            name
        )!!
    }

    private fun createTestBrandWithCountry(name: String, countryId: Short): Int {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO brands (profile_id, name, country_id, created_at, updated_at)
            VALUES (?, ?, ?, NOW(), NOW())
            RETURNING id
            """.trimIndent(),
            Int::class.java,
            testUserId,
            name,
            countryId
        )!!
    }
}
