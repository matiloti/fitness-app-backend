package com.fittrack.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Workout domain model - maps to workouts table
 */
data class Workout(
    val id: UUID,
    val profileId: UUID,
    val date: LocalDate,
    val workoutType: WorkoutType,
    val name: String? = null,
    val durationMinutes: Int,
    val caloriesBurnedEstimated: BigDecimal? = null,
    val caloriesBurnedActual: BigDecimal? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /**
     * Returns actual calories if set, otherwise estimated calories
     */
    val caloriesBurned: BigDecimal?
        get() = caloriesBurnedActual ?: caloriesBurnedEstimated
}

/**
 * Workout type MET values - maps to workout_type_mets table
 */
data class WorkoutTypeMet(
    val workoutType: WorkoutType,
    val metValue: BigDecimal,
    val description: String
)
