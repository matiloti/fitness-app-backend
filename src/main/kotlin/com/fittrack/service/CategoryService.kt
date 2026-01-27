package com.fittrack.service

import com.fittrack.model.dto.food.CategoryListResponse
import com.fittrack.model.dto.food.CategoryResponse
import com.fittrack.repository.CategoryRepository
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository
) {

    fun getAllCategories(): CategoryListResponse {
        val categories = categoryRepository.findAll()

        return CategoryListResponse(
            categories = categories.map { category ->
                CategoryResponse(
                    id = category.id,
                    name = category.name,
                    icon = category.icon
                )
            }
        )
    }
}
