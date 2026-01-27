package com.fittrack.controller

import com.fittrack.model.dto.food.CategoryListResponse
import com.fittrack.service.CategoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(private val categoryService: CategoryService) {

    @GetMapping
    fun getCategories(): ResponseEntity<CategoryListResponse> {
        val response = categoryService.getAllCategories()
        return ResponseEntity.ok(response)
    }
}
