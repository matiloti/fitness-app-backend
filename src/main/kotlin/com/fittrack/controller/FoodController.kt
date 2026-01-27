package com.fittrack.controller

import com.fittrack.model.dto.food.CreateFoodRequest
import com.fittrack.model.dto.food.CreatePortionRequest
import com.fittrack.model.dto.food.FoodDetailResponse
import com.fittrack.model.dto.food.FoodListResponse
import com.fittrack.model.dto.food.PortionResponse
import com.fittrack.model.dto.food.RecentFoodsResponse
import com.fittrack.model.dto.food.UpdateFoodRequest
import com.fittrack.security.UserPrincipal
import com.fittrack.service.FoodService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/foods")
class FoodController(private val foodService: FoodService) {

    @GetMapping
    fun getFoods(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: Int?,
        @RequestParam(required = false) brandId: Int?,
        @RequestParam(defaultValue = "recentlyUsed") sort: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<FoodListResponse> {
        val response = foodService.getFoods(
            profileId = principal.id,
            page = page,
            size = size,
            categoryId = categoryId,
            brandId = brandId,
            search = q,
            sort = sort
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getFoodById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<FoodDetailResponse> {
        val response = foodService.getFoodById(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createFood(
        @Valid @RequestBody request: CreateFoodRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<FoodDetailResponse> {
        val response = foodService.createFood(request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    fun updateFood(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateFoodRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<FoodDetailResponse> {
        val response = foodService.updateFood(id, request, principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun deleteFood(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        foodService.deleteFood(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Portions ==========

    @GetMapping("/{id}/portions")
    fun getPortions(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<PortionResponse>> {
        val response = foodService.getPortions(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/portions")
    fun addPortion(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreatePortionRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortionResponse> {
        val response = foodService.addPortion(id, request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/{id}/portions/{portionId}")
    fun deletePortion(
        @PathVariable id: UUID,
        @PathVariable portionId: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        foodService.deletePortion(id, portionId, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Recent Foods ==========

    @GetMapping("/recent")
    fun getRecentFoods(
        @RequestParam(defaultValue = "20") limit: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecentFoodsResponse> {
        val response = foodService.getRecentFoods(principal.id, limit)
        return ResponseEntity.ok(response)
    }
}
