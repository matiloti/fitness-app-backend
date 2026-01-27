package com.fittrack.controller

import com.fittrack.model.dto.food.BrandDetailResponse
import com.fittrack.model.dto.food.BrandListResponse
import com.fittrack.model.dto.food.CreateBrandRequest
import com.fittrack.model.dto.food.UpdateBrandRequest
import com.fittrack.security.UserPrincipal
import com.fittrack.service.BrandService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/brands")
class BrandController(private val brandService: BrandService) {

    @GetMapping
    fun getBrands(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrandListResponse> {
        val response = brandService.getBrands(
            profileId = principal.id,
            page = page,
            size = size,
            search = q
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getBrandById(
        @PathVariable id: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrandDetailResponse> {
        val response = brandService.getBrandById(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createBrand(
        @Valid @RequestBody request: CreateBrandRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrandDetailResponse> {
        val response = brandService.createBrand(request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    fun updateBrand(
        @PathVariable id: Int,
        @Valid @RequestBody request: UpdateBrandRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrandDetailResponse> {
        val response = brandService.updateBrand(id, request, principal.id)
        return ResponseEntity.ok(response)
    }
}
