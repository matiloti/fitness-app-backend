package com.fittrack.controller

import com.fittrack.model.dto.recipe.AddIngredientRequest
import com.fittrack.model.dto.recipe.AddStepRequest
import com.fittrack.model.dto.recipe.CreateRecipeRequest
import com.fittrack.model.dto.recipe.RecipeDetailResponse
import com.fittrack.model.dto.recipe.RecipeIngredientResponse
import com.fittrack.model.dto.recipe.RecipeListResponse
import com.fittrack.model.dto.recipe.RecipeStepResponse
import com.fittrack.model.dto.recipe.ReorderStepsRequest
import com.fittrack.model.dto.recipe.ReorderStepsResponse
import com.fittrack.model.dto.recipe.UpdateIngredientRequest
import com.fittrack.model.dto.recipe.UpdateRecipeRequest
import com.fittrack.model.dto.recipe.UpdateStepRequest
import com.fittrack.security.UserPrincipal
import com.fittrack.service.RecipeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/recipes")
class RecipeController(private val recipeService: RecipeService) {

    // ========== Recipe CRUD ==========

    @GetMapping
    fun getRecipes(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "recentlyUsed") sort: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeListResponse> {
        val response = recipeService.getRecipes(
            profileId = principal.id,
            page = page,
            size = size,
            search = q,
            sort = sort
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getRecipeById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeDetailResponse> {
        val response = recipeService.getRecipeById(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createRecipe(
        @Valid @RequestBody request: CreateRecipeRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeDetailResponse> {
        val response = recipeService.createRecipe(request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    fun updateRecipe(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRecipeRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeDetailResponse> {
        val response = recipeService.updateRecipe(id, request, principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun deleteRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        recipeService.deleteRecipe(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Ingredients ==========

    @PostMapping("/{recipeId}/ingredients")
    fun addIngredient(
        @PathVariable recipeId: UUID,
        @Valid @RequestBody request: AddIngredientRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeIngredientResponse> {
        val response = recipeService.addIngredient(recipeId, request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{recipeId}/ingredients/{ingredientId}")
    fun updateIngredient(
        @PathVariable recipeId: UUID,
        @PathVariable ingredientId: Int,
        @Valid @RequestBody request: UpdateIngredientRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeIngredientResponse> {
        val response = recipeService.updateIngredient(recipeId, ingredientId, request, principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{recipeId}/ingredients/{ingredientId}")
    fun deleteIngredient(
        @PathVariable recipeId: UUID,
        @PathVariable ingredientId: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        recipeService.deleteIngredient(recipeId, ingredientId, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Steps ==========

    @PostMapping("/{recipeId}/steps")
    fun addStep(
        @PathVariable recipeId: UUID,
        @Valid @RequestBody request: AddStepRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeStepResponse> {
        val response = recipeService.addStep(recipeId, request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{recipeId}/steps/{stepId}")
    fun updateStep(
        @PathVariable recipeId: UUID,
        @PathVariable stepId: Int,
        @Valid @RequestBody request: UpdateStepRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RecipeStepResponse> {
        val response = recipeService.updateStep(recipeId, stepId, request, principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{recipeId}/steps/{stepId}")
    fun deleteStep(
        @PathVariable recipeId: UUID,
        @PathVariable stepId: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        recipeService.deleteStep(recipeId, stepId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{recipeId}/steps/reorder")
    fun reorderSteps(
        @PathVariable recipeId: UUID,
        @Valid @RequestBody request: ReorderStepsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ReorderStepsResponse> {
        val response = recipeService.reorderSteps(recipeId, request, principal.id)
        return ResponseEntity.ok(response)
    }
}
