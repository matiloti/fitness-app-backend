package com.fittrack.service

import com.fittrack.exception.FoodNotFoundException
import com.fittrack.exception.InvalidDateRangeException
import com.fittrack.exception.InvalidItemTypeException
import com.fittrack.exception.MealItemNotFoundException
import com.fittrack.exception.MealNotFoundException
import com.fittrack.exception.MealNotOwnedException
import com.fittrack.model.Day
import com.fittrack.model.Food
import com.fittrack.model.FoodPortion
import com.fittrack.model.Meal
import com.fittrack.model.MealItem
import com.fittrack.model.MealType
import com.fittrack.model.MetricType
import com.fittrack.model.dto.meal.AddMealItemRequest
import com.fittrack.model.dto.meal.CopyMealRequest
import com.fittrack.model.dto.meal.CreateMealRequest
import com.fittrack.model.dto.meal.MealItemType
import com.fittrack.model.dto.meal.QuickAddFoodRequest
import com.fittrack.model.dto.meal.QuickEntryNutrition
import com.fittrack.model.dto.meal.UpdateMealItemRequest
import com.fittrack.model.dto.meal.UpdateMealRequest
import com.fittrack.repository.DayRepository
import com.fittrack.repository.FoodRepository
import com.fittrack.repository.MealRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Meal Service Tests")
class MealServiceTest {

    private lateinit var mealService: MealService
    private lateinit var mealRepository: MealRepository
    private lateinit var dayRepository: DayRepository
    private lateinit var foodRepository: FoodRepository
    private lateinit var nutritionCalculator: NutritionCalculatorService

    private val testProfileId = UUID.randomUUID()
    private val otherProfileId = UUID.randomUUID()
    private val testDayId = UUID.randomUUID()
    private val testMealId = UUID.randomUUID()
    private val testFoodId = UUID.randomUUID()
    private val testItemId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val testDay = Day(
        id = testDayId,
        profileId = testProfileId,
        date = today,
        activityLevelOverride = null,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    private val testMeal = Meal(
        id = testMealId,
        dayId = testDayId,
        mealType = MealType.BREAKFAST,
        isCheatMeal = false,
        displayOrder = 0,
        totalCalories = BigDecimal("500"),
        totalFat = BigDecimal("20"),
        totalCarbs = BigDecimal("60"),
        totalProtein = BigDecimal("30"),
        createdAt = now,
        updatedAt = now
    )

    private val testFood = Food(
        id = testFoodId,
        profileId = testProfileId,
        name = "Chicken Breast",
        categoryId = 1,
        brandId = null,
        metricType = MetricType.GRAMS,
        caloriesPer100 = BigDecimal("165"),
        fatPer100 = BigDecimal("3.6"),
        carbsPer100 = BigDecimal("0"),
        proteinPer100 = BigDecimal("31"),
        saltPer100 = null,
        sugarPer100 = null,
        fiberPer100 = null,
        saturatedFatPer100 = null,
        createdAt = now,
        updatedAt = now
    )

    private val testPortion = FoodPortion(
        id = 1,
        foodId = testFoodId,
        name = "1 medium breast",
        amountGrams = BigDecimal("174"),
        createdAt = now
    )

    private val testMealItem = MealItem(
        id = testItemId,
        mealId = testMealId,
        foodId = testFoodId,
        recipeId = null,
        portionId = 1,
        quantity = BigDecimal.ONE,
        amountGrams = BigDecimal("174"),
        quickEntryName = null,
        isQuickEntry = false,
        calories = BigDecimal("287.1"),
        fat = BigDecimal("6.26"),
        carbs = BigDecimal.ZERO,
        protein = BigDecimal("53.94"),
        salt = null,
        sugar = null,
        fiber = null,
        saturatedFat = null,
        displayOrder = 0,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setup() {
        mealRepository = mockk()
        dayRepository = mockk()
        foodRepository = mockk()
        nutritionCalculator = NutritionCalculatorService()

        mealService = MealService(mealRepository, dayRepository, foodRepository, nutritionCalculator)
    }

    @Nested
    @DisplayName("createMeal")
    inner class CreateMeal {

        @Test
        fun `should create meal successfully`() {
            val request = CreateMealRequest(
                date = today,
                mealType = MealType.BREAKFAST,
                isCheatMeal = false
            )

            every { dayRepository.findOrCreate(testProfileId, today) } returns testDay
            every { mealRepository.create(testDayId, MealType.BREAKFAST, false, 0) } returns testMeal

            val response = mealService.createMeal(testProfileId, request)

            assertNotNull(response)
            assertEquals(testMealId, response.id)
            assertEquals(testDayId, response.dayId)
            assertEquals(MealType.BREAKFAST, response.mealType)
            assertFalse(response.isCheatMeal)
            assertEquals(0, response.items.size)
        }

        @Test
        fun `should create cheat meal when flag is true`() {
            val request = CreateMealRequest(
                date = today,
                mealType = MealType.DINNER,
                isCheatMeal = true
            )

            val cheatMeal = testMeal.copy(mealType = MealType.DINNER, isCheatMeal = true)

            every { dayRepository.findOrCreate(testProfileId, today) } returns testDay
            every { mealRepository.create(testDayId, MealType.DINNER, true, 0) } returns cheatMeal

            val response = mealService.createMeal(testProfileId, request)

            assertEquals(MealType.DINNER, response.mealType)
            assertEquals(true, response.isCheatMeal)
        }
    }

    @Nested
    @DisplayName("getMeal")
    inner class GetMeal {

        @Test
        fun `should return meal details`() {
            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.getMealDate(testMealId) } returns today
            every { mealRepository.findItemsByMealId(testMealId) } returns listOf(testMealItem)
            every { foodRepository.findById(testFoodId) } returns testFood
            every { foodRepository.findPortionByIdAndFoodId(1, testFoodId) } returns testPortion

            val response = mealService.getMeal(testMealId, testProfileId)

            assertNotNull(response)
            assertEquals(testMealId, response.id)
            assertEquals(1, response.items.size)
            assertEquals(MealItemType.FOOD, response.items[0].type)
        }

        @Test
        fun `should throw MealNotFoundException when meal does not exist`() {
            every { mealRepository.findById(testMealId) } returns null

            assertThrows<MealNotFoundException> {
                mealService.getMeal(testMealId, testProfileId)
            }
        }

        @Test
        fun `should throw MealNotOwnedException when meal belongs to another user`() {
            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns otherProfileId

            assertThrows<MealNotOwnedException> {
                mealService.getMeal(testMealId, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("updateMeal")
    inner class UpdateMeal {

        @Test
        fun `should update meal properties`() {
            val request = UpdateMealRequest(
                mealType = MealType.LUNCH,
                isCheatMeal = true
            )

            val updatedMeal = testMeal.copy(mealType = MealType.LUNCH, isCheatMeal = true)

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.update(testMealId, MealType.LUNCH, true) } returns updatedMeal
            every { mealRepository.getMealDate(testMealId) } returns today
            every { mealRepository.findItemsByMealId(testMealId) } returns emptyList()

            val response = mealService.updateMeal(testMealId, testProfileId, request)

            assertEquals(MealType.LUNCH, response.mealType)
            assertEquals(true, response.isCheatMeal)
        }
    }

    @Nested
    @DisplayName("deleteMeal")
    inner class DeleteMeal {

        @Test
        fun `should delete meal successfully`() {
            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.delete(testMealId) } returns true

            mealService.deleteMeal(testMealId, testProfileId)

            verify { mealRepository.delete(testMealId) }
        }

        @Test
        fun `should throw MealNotFoundException when meal does not exist`() {
            every { mealRepository.findById(testMealId) } returns null

            assertThrows<MealNotFoundException> {
                mealService.deleteMeal(testMealId, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("copyMeal")
    inner class CopyMeal {

        @Test
        fun `should copy meal to another day`() {
            val targetDate = today.plusDays(1)
            val request = CopyMealRequest(targetDate = targetDate)

            val targetDay = testDay.copy(id = UUID.randomUUID(), date = targetDate)
            val newMeal = testMeal.copy(id = UUID.randomUUID(), dayId = targetDay.id, isCheatMeal = false)

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { dayRepository.findOrCreate(testProfileId, targetDate) } returns targetDay
            every { mealRepository.create(targetDay.id, MealType.BREAKFAST, false, 0) } returns newMeal
            every { mealRepository.findItemsByMealId(testMealId) } returns listOf(testMealItem)
            every { mealRepository.createItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testMealItem.copy(id = UUID.randomUUID(), mealId = newMeal.id)
            every { mealRepository.updateTotals(newMeal.id) } returns newMeal
            every { foodRepository.findById(testFoodId) } returns testFood
            every { foodRepository.findPortionByIdAndFoodId(1, testFoodId) } returns testPortion

            val response = mealService.copyMeal(testMealId, testProfileId, request)

            assertNotNull(response)
            assertEquals(targetDate, response.date)
            assertFalse(response.isCheatMeal) // Cheat meal flag is NOT copied
        }
    }

    @Nested
    @DisplayName("getMeals")
    inner class GetMeals {

        @Test
        fun `should return meals for date range`() {
            val startDate = today.minusDays(7)
            val endDate = today

            every { mealRepository.findByProfileIdAndDateRange(testProfileId, startDate, endDate) } returns listOf(testMeal)
            every { mealRepository.getMealDate(testMealId) } returns today
            every { mealRepository.countItemsByMealId(testMealId) } returns 3

            val response = mealService.getMeals(testProfileId, startDate, endDate)

            assertNotNull(response)
            assertEquals(1, response.meals.size)
            assertEquals(1, response.summary.totalMeals)
        }

        @Test
        fun `should throw InvalidDateRangeException when range exceeds 90 days`() {
            val startDate = today.minusDays(100)
            val endDate = today

            assertThrows<InvalidDateRangeException> {
                mealService.getMeals(testProfileId, startDate, endDate)
            }
        }
    }

    @Nested
    @DisplayName("addMealItem - Food")
    inner class AddFoodItem {

        @Test
        fun `should add food item to meal`() {
            val request = AddMealItemRequest(
                type = MealItemType.FOOD,
                foodId = testFoodId,
                portionId = 1,
                quantity = BigDecimal.ONE
            )

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { foodRepository.findById(testFoodId) } returns testFood
            every { foodRepository.findPortionByIdAndFoodId(1, testFoodId) } returns testPortion
            every { mealRepository.createItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testMealItem
            every { mealRepository.updateTotals(testMealId) } returns testMeal
            every { foodRepository.updateRecentFood(testProfileId, testFoodId) } returns Unit

            val response = mealService.addMealItem(testMealId, testProfileId, request)

            assertNotNull(response)
            assertEquals(MealItemType.FOOD, response.type)
            assertNotNull(response.food)
            assertEquals(testFoodId, response.food?.id)

            verify { foodRepository.updateRecentFood(testProfileId, testFoodId) }
        }

        @Test
        fun `should throw FoodNotFoundException when food does not exist`() {
            val request = AddMealItemRequest(
                type = MealItemType.FOOD,
                foodId = testFoodId,
                quantity = BigDecimal.ONE
            )

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { foodRepository.findById(testFoodId) } returns null

            assertThrows<FoodNotFoundException> {
                mealService.addMealItem(testMealId, testProfileId, request)
            }
        }
    }

    @Nested
    @DisplayName("addMealItem - Quick Entry")
    inner class AddQuickEntry {

        @Test
        fun `should add quick entry item to meal`() {
            val request = AddMealItemRequest(
                type = MealItemType.QUICK_ENTRY,
                name = "Restaurant pasta",
                nutrition = QuickEntryNutrition(
                    calories = BigDecimal("650"),
                    fat = BigDecimal("22"),
                    carbs = BigDecimal("85"),
                    protein = BigDecimal("20")
                )
            )

            val quickEntryItem = MealItem(
                id = UUID.randomUUID(),
                mealId = testMealId,
                foodId = null,
                recipeId = null,
                portionId = null,
                quantity = BigDecimal.ONE,
                amountGrams = null,
                quickEntryName = "Restaurant pasta",
                isQuickEntry = true,
                calories = BigDecimal("650"),
                fat = BigDecimal("22"),
                carbs = BigDecimal("85"),
                protein = BigDecimal("20"),
                salt = null,
                sugar = null,
                fiber = null,
                saturatedFat = null,
                displayOrder = 0,
                createdAt = now,
                updatedAt = now
            )

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.createItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns quickEntryItem
            every { mealRepository.updateTotals(testMealId) } returns testMeal

            val response = mealService.addMealItem(testMealId, testProfileId, request)

            assertNotNull(response)
            assertEquals(MealItemType.QUICK_ENTRY, response.type)
            assertEquals("Restaurant pasta", response.name)
            assertEquals(BigDecimal("650"), response.nutrition.calories)
        }

        @Test
        fun `should throw InvalidItemTypeException when name is missing for quick entry`() {
            val request = AddMealItemRequest(
                type = MealItemType.QUICK_ENTRY,
                name = null,
                nutrition = QuickEntryNutrition(calories = BigDecimal("650"))
            )

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId

            assertThrows<InvalidItemTypeException> {
                mealService.addMealItem(testMealId, testProfileId, request)
            }
        }
    }

    @Nested
    @DisplayName("quickAddFood")
    inner class QuickAddFood {

        @Test
        fun `should quick add food with default portion`() {
            val request = QuickAddFoodRequest(
                foodId = testFoodId,
                quantity = BigDecimal.ONE
            )

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { foodRepository.findById(testFoodId) } returns testFood
            every { foodRepository.findPortionsByFoodId(testFoodId) } returns listOf(testPortion)
            every { mealRepository.createItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testMealItem
            every { mealRepository.updateTotals(testMealId) } returns testMeal
            every { foodRepository.updateRecentFood(testProfileId, testFoodId) } returns Unit
            every { foodRepository.findPortionByIdAndFoodId(1, testFoodId) } returns testPortion

            val response = mealService.quickAddFood(testMealId, testProfileId, request)

            assertNotNull(response)
            assertEquals(MealItemType.FOOD, response.type)

            verify { foodRepository.updateRecentFood(testProfileId, testFoodId) }
        }
    }

    @Nested
    @DisplayName("updateMealItem")
    inner class UpdateMealItem {

        @Test
        fun `should update food item quantity`() {
            val request = UpdateMealItemRequest(quantity = BigDecimal("2"))

            val updatedItem = testMealItem.copy(
                quantity = BigDecimal("2"),
                amountGrams = BigDecimal("348")
            )

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.findItemById(testItemId) } returns testMealItem
            every { foodRepository.findById(testFoodId) } returns testFood
            every { foodRepository.findPortionByIdAndFoodId(1, testFoodId) } returns testPortion
            every { mealRepository.updateItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns updatedItem
            every { mealRepository.updateTotals(testMealId) } returns testMeal

            val response = mealService.updateMealItem(testMealId, testItemId, testProfileId, request)

            assertNotNull(response)
            assertEquals(BigDecimal("2"), response.quantity)
        }

        @Test
        fun `should throw MealItemNotFoundException when item does not exist`() {
            val request = UpdateMealItemRequest(quantity = BigDecimal("2"))

            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.findItemById(testItemId) } returns null

            assertThrows<MealItemNotFoundException> {
                mealService.updateMealItem(testMealId, testItemId, testProfileId, request)
            }
        }
    }

    @Nested
    @DisplayName("deleteMealItem")
    inner class DeleteMealItem {

        @Test
        fun `should delete meal item successfully`() {
            every { mealRepository.findById(testMealId) } returns testMeal
            every { mealRepository.getMealProfileId(testMealId) } returns testProfileId
            every { mealRepository.findItemById(testItemId) } returns testMealItem
            every { mealRepository.deleteItem(testItemId) } returns true
            every { mealRepository.updateTotals(testMealId) } returns testMeal

            mealService.deleteMealItem(testMealId, testItemId, testProfileId)

            verify { mealRepository.deleteItem(testItemId) }
            verify { mealRepository.updateTotals(testMealId) }
        }
    }

    @Nested
    @DisplayName("getMealTypes")
    inner class GetMealTypes {

        @Test
        fun `should return all meal types`() {
            val response = mealService.getMealTypes()

            assertNotNull(response)
            assertEquals(4, response.mealTypes.size)
            assertEquals(MealType.BREAKFAST, response.mealTypes[0].type)
            assertEquals("Breakfast", response.mealTypes[0].name)
        }
    }
}
