package com.fittrack.controller

import com.fittrack.model.dto.profile.ActivityLevelUpdateResponse
import com.fittrack.model.dto.profile.ActivityLevelsResponse
import com.fittrack.model.dto.profile.CountriesResponse
import com.fittrack.model.dto.profile.FitnessGoalUpdateResponse
import com.fittrack.model.dto.profile.FitnessGoalsResponse
import com.fittrack.model.dto.profile.MetricsUpdateResponse
import com.fittrack.model.dto.profile.ProfileResponse
import com.fittrack.model.dto.profile.UpdateActivityLevelRequest
import com.fittrack.model.dto.profile.UpdateFitnessGoalRequest
import com.fittrack.model.dto.profile.UpdateProfileMetricsRequest
import com.fittrack.model.dto.profile.UpdateProfileRequest
import com.fittrack.security.UserPrincipal
import com.fittrack.service.ProfileService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/profile")
class ProfileController(private val profileService: ProfileService) {

    @GetMapping
    fun getProfile(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<ProfileResponse> {
        val response = profileService.getProfile(principal.id)
        return ResponseEntity.ok(response)
    }

    @PatchMapping
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ProfileResponse> {
        val response = profileService.updateProfile(principal.id, request)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/metrics")
    fun updateMetrics(
        @Valid @RequestBody request: UpdateProfileMetricsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<MetricsUpdateResponse> {
        val response = profileService.updateMetrics(principal.id, request)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/activity-level")
    fun updateActivityLevel(
        @Valid @RequestBody request: UpdateActivityLevelRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ActivityLevelUpdateResponse> {
        val response = profileService.updateActivityLevel(principal.id, request)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/fitness-goal")
    fun updateFitnessGoal(
        @Valid @RequestBody request: UpdateFitnessGoalRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<FitnessGoalUpdateResponse> {
        val response = profileService.updateFitnessGoal(principal.id, request)
        return ResponseEntity.ok(response)
    }

    // ========== Public Reference Data Endpoints ==========

    @GetMapping("/countries")
    fun getCountries(@RequestParam(required = false) search: String?): ResponseEntity<CountriesResponse> {
        val response = profileService.getCountries(search)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/activity-levels")
    fun getActivityLevels(): ResponseEntity<ActivityLevelsResponse> {
        val response = profileService.getActivityLevels()
        return ResponseEntity.ok(response)
    }

    @GetMapping("/fitness-goals")
    fun getFitnessGoals(): ResponseEntity<FitnessGoalsResponse> {
        val response = profileService.getFitnessGoals()
        return ResponseEntity.ok(response)
    }
}
