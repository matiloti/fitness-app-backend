package com.fittrack.integration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DisplayName("Category Controller Integration Tests")
class CategoryControllerIntegrationTest : IntegrationTestBase() {

    @Test
    fun `should return all categories`() {
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories").isArray)
            .andExpect(jsonPath("$.categories.length()").value(12)) // 12 seeded categories
            .andExpect(jsonPath("$.categories[0].id").value(1))
            .andExpect(jsonPath("$.categories[0].name").value("Fruits"))
            .andExpect(jsonPath("$.categories[0].icon").value("apple"))
    }

    @Test
    fun `should include all default categories`() {
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories[?(@.name == 'Fruits')]").exists())
            .andExpect(jsonPath("$.categories[?(@.name == 'Vegetables')]").exists())
            .andExpect(jsonPath("$.categories[?(@.name == 'Grains & Cereals')]").exists())
            .andExpect(jsonPath("$.categories[?(@.name == 'Protein')]").exists())
            .andExpect(jsonPath("$.categories[?(@.name == 'Dairy')]").exists())
            .andExpect(jsonPath("$.categories[?(@.name == 'Beverages')]").exists())
    }

    @Test
    fun `categories endpoint should be publicly accessible`() {
        // No authentication header needed
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk)
    }
}
