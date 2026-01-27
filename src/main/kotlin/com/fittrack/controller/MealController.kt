package com.fittrack.controller

import com.fittrack.model.MealType
import com.fittrack.model.dto.meal.AddMealItemRequest
import com.fittrack.model.dto.meal.CopyMealRequest
import com.fittrack.model.dto.meal.CreateMealRequest
import com.fittrack.model.dto.meal.MealItemResponse
import com.fittrack.model.dto.meal.MealListResponse
import com.fittrack.model.dto.meal.MealResponse
import com.fittrack.model.dto.meal.MealTypesResponse
import com.fittrack.model.dto.meal.QuickAddFoodRequest
import com.fittrack.model.dto.meal.UpdateMealItemRequest
import com.fittrack.model.dto.meal.UpdateMealRequest
import com.fittrack.security.UserPrincipal
import com.fittrack.service.MealService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meals")
class MealController(private val mealService: MealService) {

    // ========== Meal Operations ==========

    /**
     * POST /api/v1/meals - Create a new meal
     */
    @PostMapping
    fun createMeal(
        @Valid @RequestBody request: CreateMealRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealResponse> {
        val response = mealService.createMeal(principal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * GET /api/v1/meals/{id} - Get meal details
     */
    @GetMapping("/{id}")
    fun getMeal(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealResponse> {
        val response = mealService.getMeal(id, principal.id)
        return ResponseEntity.ok(response)
    }

    /**
     * PATCH /api/v1/meals/{id} - Update meal properties
     */
    @PatchMapping("/{id}")
    fun updateMeal(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMealRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealResponse> {
        val response = mealService.updateMeal(id, principal.id, request)
        return ResponseEntity.ok(response)
    }

    /**
     * DELETE /api/v1/meals/{id} - Delete a meal
     */
    @DeleteMapping("/{id}")
    fun deleteMeal(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        mealService.deleteMeal(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    /**
     * POST /api/v1/meals/{id}/copy - Copy a meal to another day
     */
    @PostMapping("/{id}/copy")
    fun copyMeal(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CopyMealRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealResponse> {
        val response = mealService.copyMeal(id, principal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * GET /api/v1/meals - List meals for a date range
     */
    @GetMapping
    fun getMeals(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) mealType: MealType?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealListResponse> {
        val start = startDate ?: LocalDate.now()
        val end = endDate ?: LocalDate.now()
        val response = mealService.getMeals(principal.id, start, end, mealType)
        return ResponseEntity.ok(response)
    }

    // ========== Meal Item Operations ==========

    /**
     * POST /api/v1/meals/{mealId}/items - Add an item to a meal
     */
    @PostMapping("/{mealId}/items")
    fun addMealItem(
        @PathVariable mealId: UUID,
        @Valid @RequestBody request: AddMealItemRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealItemResponse> {
        val response = mealService.addMealItem(mealId, principal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * POST /api/v1/meals/{mealId}/items/quick-add - Quick add a food
     */
    @PostMapping("/{mealId}/items/quick-add")
    fun quickAddFood(
        @PathVariable mealId: UUID,
        @Valid @RequestBody request: QuickAddFoodRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealItemResponse> {
        val response = mealService.quickAddFood(mealId, principal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * PATCH /api/v1/meals/{mealId}/items/{itemId} - Update a meal item
     */
    @PatchMapping("/{mealId}/items/{itemId}")
    fun updateMealItem(
        @PathVariable mealId: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: UpdateMealItemRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MealItemResponse> {
        val response = mealService.updateMealItem(mealId, itemId, principal.id, request)
        return ResponseEntity.ok(response)
    }

    /**
     * DELETE /api/v1/meals/{mealId}/items/{itemId} - Delete a meal item
     */
    @DeleteMapping("/{mealId}/items/{itemId}")
    fun deleteMealItem(
        @PathVariable mealId: UUID,
        @PathVariable itemId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        mealService.deleteMealItem(mealId, itemId, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Meal Types ==========

    /**
     * GET /api/v1/meals/types - Get list of meal types
     */
    @GetMapping("/types")
    fun getMealTypes(): ResponseEntity<MealTypesResponse> {
        val response = mealService.getMealTypes()
        return ResponseEntity.ok(response)
    }
}
