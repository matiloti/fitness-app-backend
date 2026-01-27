package com.fittrack.service

import com.fittrack.model.Category
import com.fittrack.repository.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("Category Service Tests")
class CategoryServiceTest {

    private lateinit var categoryService: CategoryService
    private lateinit var categoryRepository: CategoryRepository

    private val now = Instant.now()

    private val testCategories = listOf(
        Category(id = 1, name = "Fruits", icon = "apple", isSystem = true, createdAt = now),
        Category(id = 2, name = "Vegetables", icon = "carrot", isSystem = true, createdAt = now),
        Category(id = 3, name = "Protein", icon = "drumstick", isSystem = true, createdAt = now)
    )

    @BeforeEach
    fun setup() {
        categoryRepository = mockk()
        categoryService = CategoryService(categoryRepository)
    }

    @Test
    fun `should return all categories`() {
        every { categoryRepository.findAll() } returns testCategories

        val response = categoryService.getAllCategories()

        assertNotNull(response)
        assertEquals(3, response.categories.size)
        assertEquals("Fruits", response.categories[0].name)
        assertEquals("apple", response.categories[0].icon)
    }

    @Test
    fun `should return empty list when no categories exist`() {
        every { categoryRepository.findAll() } returns emptyList()

        val response = categoryService.getAllCategories()

        assertNotNull(response)
        assertEquals(0, response.categories.size)
    }
}
