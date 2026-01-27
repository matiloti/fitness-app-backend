package com.fittrack.service

import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.exception.MissingWeightException
import com.fittrack.exception.WorkoutNotFoundException
import com.fittrack.exception.WorkoutNotOwnedException
import com.fittrack.model.BodyMetrics
import com.fittrack.model.Workout
import com.fittrack.model.WorkoutType
import com.fittrack.model.WorkoutTypeMet
import com.fittrack.model.dto.workout.CreateWorkoutRequest
import com.fittrack.model.dto.workout.UpdateWorkoutRequest
import com.fittrack.repository.BodyMetricsRepository
import com.fittrack.repository.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Workout Service Tests")
class WorkoutServiceTest {

    private lateinit var workoutService: WorkoutService
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var bodyMetricsRepository: BodyMetricsRepository

    private val testProfileId = UUID.randomUUID()
    private val otherProfileId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val testWorkout = Workout(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        date = today,
        workoutType = WorkoutType.STRENGTH,
        name = "Upper Body Push",
        durationMinutes = 60,
        caloriesBurnedEstimated = BigDecimal("412.50"),
        caloriesBurnedActual = null,
        notes = "PR on bench press",
        createdAt = now,
        updatedAt = now
    )

    private val testBodyMetrics = BodyMetrics(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        date = today.minusDays(1),
        weightKg = BigDecimal("82.5"),
        bodyFatPercentage = null,
        bodyFatKg = null,
        muscleMassPercentage = null,
        muscleMassKg = null,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    private val testMet = WorkoutTypeMet(
        workoutType = WorkoutType.STRENGTH,
        metValue = BigDecimal("5.0"),
        description = "Strength Training"
    )

    @BeforeEach
    fun setup() {
        workoutRepository = mockk()
        bodyMetricsRepository = mockk()

        workoutService = WorkoutService(workoutRepository, bodyMetricsRepository)
    }

    @Nested
    @DisplayName("getWorkouts")
    inner class GetWorkouts {

        @Test
        fun `should return paginated workout list with summary`() {
            val startDate = today.minusDays(30)
            val endDate = today

            every { workoutRepository.findAllByProfileId(testProfileId, startDate, endDate, null, 0, 20) } returns listOf(testWorkout)
            every { workoutRepository.countByProfileId(testProfileId, startDate, endDate, null) } returns 1L
            every { workoutRepository.getSummaryStats(testProfileId, startDate, endDate) } returns mapOf(
                "total_workouts" to 1L,
                "total_duration" to 60L,
                "total_calories" to BigDecimal("412.50"),
                "avg_duration" to BigDecimal("60"),
                "avg_calories" to BigDecimal("412.50")
            )

            val response = workoutService.getWorkouts(testProfileId, startDate, endDate, null, 0, 20)

            assertNotNull(response)
            assertEquals(1, response.content.size)
            assertEquals(testWorkout.name, response.content[0].name)
            assertEquals(0, response.page.number)
            assertEquals(20, response.page.size)
            assertEquals(1, response.page.totalElements)
            assertEquals(1, response.summary.totalWorkouts)
        }

        @Test
        fun `should filter by workout type`() {
            val startDate = today.minusDays(30)
            val endDate = today

            every { workoutRepository.findAllByProfileId(testProfileId, startDate, endDate, WorkoutType.STRENGTH, 0, 20) } returns listOf(testWorkout)
            every { workoutRepository.countByProfileId(testProfileId, startDate, endDate, WorkoutType.STRENGTH) } returns 1L
            every { workoutRepository.getSummaryStats(testProfileId, startDate, endDate) } returns mapOf(
                "total_workouts" to 1L,
                "total_duration" to 60L,
                "total_calories" to BigDecimal("412.50"),
                "avg_duration" to BigDecimal("60"),
                "avg_calories" to BigDecimal("412.50")
            )

            val response = workoutService.getWorkouts(testProfileId, startDate, endDate, WorkoutType.STRENGTH, 0, 20)

            assertEquals(1, response.content.size)
            assertEquals(WorkoutType.STRENGTH, response.content[0].workoutType)
        }

        @Test
        fun `should clamp page size to 100`() {
            val startDate = today.minusDays(30)
            val endDate = today

            every { workoutRepository.findAllByProfileId(testProfileId, startDate, endDate, null, 0, 100) } returns emptyList()
            every { workoutRepository.countByProfileId(testProfileId, startDate, endDate, null) } returns 0L
            every { workoutRepository.getSummaryStats(testProfileId, startDate, endDate) } returns mapOf(
                "total_workouts" to 0L,
                "total_duration" to 0L,
                "total_calories" to BigDecimal.ZERO,
                "avg_duration" to BigDecimal.ZERO,
                "avg_calories" to BigDecimal.ZERO
            )

            val response = workoutService.getWorkouts(testProfileId, startDate, endDate, null, 0, 200)

            assertEquals(100, response.page.size)
        }

        @Test
        fun `should throw InvalidDateRangeException when date range exceeds 90 days`() {
            val startDate = today.minusDays(100)
            val endDate = today

            assertThrows<InvalidDateRangeException> {
                workoutService.getWorkouts(testProfileId, startDate, endDate, null, 0, 20)
            }
        }
    }

    @Nested
    @DisplayName("getWorkoutById")
    inner class GetWorkoutById {

        @Test
        fun `should return workout details with calculation info`() {
            every { workoutRepository.findById(testWorkout.id) } returns testWorkout
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics

            val response = workoutService.getWorkoutById(testWorkout.id, testProfileId)

            assertNotNull(response)
            assertEquals(testWorkout.id, response.id)
            assertEquals(testWorkout.name, response.name)
            assertNotNull(response.workoutTypeInfo)
            assertEquals(WorkoutType.STRENGTH, response.workoutTypeInfo.type)
            assertNotNull(response.calculationDetails)
            assertEquals(BigDecimal("82.5"), response.calculationDetails?.weightUsed)
        }

        @Test
        fun `should throw WorkoutNotFoundException when workout does not exist`() {
            val nonExistentId = UUID.randomUUID()
            every { workoutRepository.findById(nonExistentId) } returns null

            assertThrows<WorkoutNotFoundException> {
                workoutService.getWorkoutById(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw WorkoutNotOwnedException when workout belongs to another user`() {
            every { workoutRepository.findById(testWorkout.id) } returns testWorkout

            assertThrows<WorkoutNotOwnedException> {
                workoutService.getWorkoutById(testWorkout.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("createWorkout")
    inner class CreateWorkout {

        @Test
        fun `should create workout with calculated estimated calories`() {
            val request = CreateWorkoutRequest(
                date = today,
                workoutType = WorkoutType.STRENGTH,
                name = "Upper Body Push",
                durationMinutes = 60,
                notes = "PR on bench press"
            )

            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { workoutRepository.create(
                profileId = testProfileId,
                date = today,
                workoutType = WorkoutType.STRENGTH,
                name = "Upper Body Push",
                durationMinutes = 60,
                caloriesBurnedEstimated = any(),
                caloriesBurnedActual = null,
                notes = "PR on bench press"
            ) } returns testWorkout

            val response = workoutService.createWorkout(request, testProfileId)

            assertNotNull(response)
            assertEquals(testWorkout.id, response.id)
            assertEquals(testWorkout.name, response.name)
            verify { workoutRepository.create(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should create workout with user-provided actual calories`() {
            val request = CreateWorkoutRequest(
                date = today,
                workoutType = WorkoutType.CARDIO_RUNNING,
                name = "Morning 5K",
                durationMinutes = 28,
                caloriesBurnedActual = BigDecimal("410"),
                notes = null
            )

            val runningWorkout = testWorkout.copy(
                workoutType = WorkoutType.CARDIO_RUNNING,
                name = "Morning 5K",
                durationMinutes = 28,
                caloriesBurnedActual = BigDecimal("410")
            )

            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics
            every { workoutRepository.findMetByType(WorkoutType.CARDIO_RUNNING) } returns testMet.copy(
                workoutType = WorkoutType.CARDIO_RUNNING,
                metValue = BigDecimal("9.8")
            )
            every { workoutRepository.create(any(), any(), any(), any(), any(), any(), any(), any()) } returns runningWorkout

            val response = workoutService.createWorkout(request, testProfileId)

            assertNotNull(response)
            assertEquals(BigDecimal("410"), response.caloriesBurnedActual)
        }

        @Test
        fun `should use default weight when no body metrics exist`() {
            val request = CreateWorkoutRequest(
                date = today,
                workoutType = WorkoutType.STRENGTH,
                durationMinutes = 60
            )

            // With no body metrics at all, should use default 70kg
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns null
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { workoutRepository.create(any(), any(), any(), any(), any(), any(), any(), any()) } returns testWorkout.copy(
                caloriesBurnedEstimated = BigDecimal("350.00") // 5.0 * 70 * 1 = 350
            )

            val response = workoutService.createWorkout(request, testProfileId)

            assertNotNull(response)
        }

        @Test
        fun `should use default weight when body metrics has no weight`() {
            val metricsWithoutWeight = testBodyMetrics.copy(weightKg = null)
            val request = CreateWorkoutRequest(
                date = today,
                workoutType = WorkoutType.STRENGTH,
                durationMinutes = 60
            )

            // With no weight in body metrics, should use default 70kg
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns metricsWithoutWeight
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { workoutRepository.create(any(), any(), any(), any(), any(), any(), any(), any()) } returns testWorkout.copy(
                caloriesBurnedEstimated = BigDecimal("350.00") // 5.0 * 70 * 1 = 350
            )

            val response = workoutService.createWorkout(request, testProfileId)

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("updateWorkout")
    inner class UpdateWorkout {

        @Test
        fun `should update workout successfully`() {
            val request = UpdateWorkoutRequest(
                name = "Upper Body Push (Heavy)",
                durationMinutes = 75
            )
            val updatedWorkout = testWorkout.copy(
                name = "Upper Body Push (Heavy)",
                durationMinutes = 75
            )

            every { workoutRepository.findById(testWorkout.id) } returns testWorkout
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { workoutRepository.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns updatedWorkout

            val response = workoutService.updateWorkout(testWorkout.id, request, testProfileId)

            assertNotNull(response)
            assertEquals("Upper Body Push (Heavy)", response.name)
            assertEquals(75, response.durationMinutes)
        }

        @Test
        fun `should recalculate estimated calories when duration changes`() {
            val request = UpdateWorkoutRequest(durationMinutes = 90)
            val updatedWorkout = testWorkout.copy(
                durationMinutes = 90,
                caloriesBurnedEstimated = BigDecimal("618.75") // 5.0 * 82.5 * 1.5
            )

            every { workoutRepository.findById(testWorkout.id) } returns testWorkout
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { workoutRepository.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns updatedWorkout

            val response = workoutService.updateWorkout(testWorkout.id, request, testProfileId)

            assertNotNull(response)
            assertEquals(90, response.durationMinutes)
        }

        @Test
        fun `should clear actual calories when clearActualCalories is true`() {
            val workoutWithActual = testWorkout.copy(caloriesBurnedActual = BigDecimal("400"))
            val request = UpdateWorkoutRequest(clearActualCalories = true)
            val updatedWorkout = workoutWithActual.copy(caloriesBurnedActual = null)

            every { workoutRepository.findById(testWorkout.id) } returns workoutWithActual
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { workoutRepository.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns updatedWorkout

            val response = workoutService.updateWorkout(testWorkout.id, request, testProfileId)

            assertNull(response.caloriesBurnedActual)
        }

        @Test
        fun `should throw WorkoutNotFoundException when workout does not exist`() {
            val nonExistentId = UUID.randomUUID()
            val request = UpdateWorkoutRequest(name = "Updated")

            every { workoutRepository.findById(nonExistentId) } returns null

            assertThrows<WorkoutNotFoundException> {
                workoutService.updateWorkout(nonExistentId, request, testProfileId)
            }
        }

        @Test
        fun `should throw WorkoutNotOwnedException when workout belongs to another user`() {
            val request = UpdateWorkoutRequest(name = "Updated")

            every { workoutRepository.findById(testWorkout.id) } returns testWorkout

            assertThrows<WorkoutNotOwnedException> {
                workoutService.updateWorkout(testWorkout.id, request, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("deleteWorkout")
    inner class DeleteWorkout {

        @Test
        fun `should delete workout successfully`() {
            every { workoutRepository.findById(testWorkout.id) } returns testWorkout
            every { workoutRepository.delete(testWorkout.id) } returns true

            workoutService.deleteWorkout(testWorkout.id, testProfileId)

            verify { workoutRepository.delete(testWorkout.id) }
        }

        @Test
        fun `should throw WorkoutNotFoundException when workout does not exist`() {
            val nonExistentId = UUID.randomUUID()

            every { workoutRepository.findById(nonExistentId) } returns null

            assertThrows<WorkoutNotFoundException> {
                workoutService.deleteWorkout(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw WorkoutNotOwnedException when workout belongs to another user`() {
            every { workoutRepository.findById(testWorkout.id) } returns testWorkout

            assertThrows<WorkoutNotOwnedException> {
                workoutService.deleteWorkout(testWorkout.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("getWorkoutTypes")
    inner class GetWorkoutTypes {

        @Test
        fun `should return all workout types with MET values`() {
            val mets = WorkoutType.values().map {
                WorkoutTypeMet(it, BigDecimal(it.metValue.toString()), it.description)
            }

            every { workoutRepository.findAllMets() } returns mets
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics

            val response = workoutService.getWorkoutTypes(testProfileId)

            assertNotNull(response)
            assertEquals(WorkoutType.values().size, response.workoutTypes.size)
        }

        @Test
        fun `should calculate estimated calories per hour based on user weight`() {
            val mets = listOf(testMet)

            every { workoutRepository.findAllMets() } returns mets
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics

            val response = workoutService.getWorkoutTypes(testProfileId)

            assertNotNull(response)
            assertEquals(1, response.workoutTypes.size)
            // 5.0 MET * 82.5 kg * 1 hour = 412.5 kcal/hour
            assertEquals(412, response.workoutTypes[0].estimatedCaloriesPerHour)
        }

        @Test
        fun `should use default weight when no weight logged`() {
            val mets = listOf(testMet)

            every { workoutRepository.findAllMets() } returns mets
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns null

            val response = workoutService.getWorkoutTypes(testProfileId)

            assertNotNull(response)
            // 5.0 MET * 70 kg (default) * 1 hour = 350 kcal/hour
            assertEquals(350, response.workoutTypes[0].estimatedCaloriesPerHour)
        }
    }

    @Nested
    @DisplayName("estimateCalories")
    inner class EstimateCalories {

        @Test
        fun `should estimate calories correctly`() {
            every { workoutRepository.findMetByType(WorkoutType.CARDIO_RUNNING) } returns WorkoutTypeMet(
                WorkoutType.CARDIO_RUNNING, BigDecimal("9.8"), "Running"
            )
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns testBodyMetrics

            val response = workoutService.estimateCalories(testProfileId, WorkoutType.CARDIO_RUNNING, 30)

            assertNotNull(response)
            assertEquals(WorkoutType.CARDIO_RUNNING, response.workoutType)
            assertEquals(30, response.durationMinutes)
            // 9.8 * 82.5 * 0.5 = 404.25 -> 404
            assertEquals(404, response.estimatedCalories)
            assertEquals(BigDecimal("82.5"), response.calculation.weightUsed)
            assertEquals(BigDecimal("9.8"), response.calculation.metValue)
        }

        @Test
        fun `should use default weight when no weight logged`() {
            every { workoutRepository.findMetByType(WorkoutType.STRENGTH) } returns testMet
            every { bodyMetricsRepository.findLatestByProfileId(testProfileId) } returns null

            val response = workoutService.estimateCalories(testProfileId, WorkoutType.STRENGTH, 60)

            assertNotNull(response)
            // 5.0 * 70 * 1 = 350
            assertEquals(350, response.estimatedCalories)
            assertEquals(BigDecimal("70.0"), response.calculation.weightUsed)
        }
    }

    @Nested
    @DisplayName("getWorkoutSummary")
    inner class GetWorkoutSummary {

        @Test
        fun `should return workout summary for date range`() {
            val startDate = today.minusDays(30)
            val endDate = today

            every { workoutRepository.countByProfileId(testProfileId, startDate, endDate, null) } returns 15L
            every { workoutRepository.getSummaryStats(testProfileId, startDate, endDate) } returns mapOf(
                "total_workouts" to 15L,
                "total_duration" to 825L,
                "total_calories" to BigDecimal("5250"),
                "avg_duration" to BigDecimal("55"),
                "avg_calories" to BigDecimal("350")
            )
            every { workoutRepository.getMaxStats(testProfileId, startDate, endDate) } returns mapOf(
                "longest_workout" to 90,
                "most_calories" to BigDecimal("520")
            )
            every { workoutRepository.getWorkoutsByType(testProfileId, startDate, endDate) } returns listOf(
                mapOf("workout_type" to "STRENGTH", "count" to 8L, "total_duration" to 480L, "total_calories" to BigDecimal("2800"))
            )
            every { workoutRepository.getWeeklyTrend(testProfileId, startDate, endDate) } returns listOf(
                mapOf("week_start" to java.sql.Date.valueOf(startDate), "workouts" to 3L, "calories" to BigDecimal("1050"))
            )

            val response = workoutService.getWorkoutSummary(testProfileId, startDate, endDate)

            assertNotNull(response)
            assertEquals(startDate, response.period.startDate)
            assertEquals(endDate, response.period.endDate)
            assertEquals(15, response.summary.totalWorkouts)
            assertEquals(825, response.summary.totalDurationMinutes)
            assertEquals(5250, response.summary.totalCaloriesBurned)
        }
    }
}
