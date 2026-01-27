package com.fittrack.service

import com.fittrack.exception.BrandNotFoundException
import com.fittrack.exception.CategoryNotFoundException
import com.fittrack.exception.FoodInUseException
import com.fittrack.exception.FoodNotFoundException
import com.fittrack.exception.FoodNotOwnedException
import com.fittrack.exception.PortionAlreadyExistsException
import com.fittrack.exception.PortionNotFoundException
import com.fittrack.model.Brand
import com.fittrack.model.Category
import com.fittrack.model.Food
import com.fittrack.model.FoodPortion
import com.fittrack.model.MetricType
import com.fittrack.model.dto.food.CreateFoodRequest
import com.fittrack.model.dto.food.CreatePortionRequest
import com.fittrack.model.dto.food.NutritionRequest
import com.fittrack.model.dto.food.PortionRequest
import com.fittrack.model.dto.food.UpdateFoodRequest
import com.fittrack.repository.BrandRepository
import com.fittrack.repository.CategoryRepository
import com.fittrack.repository.FoodRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("Food Service Tests")
class FoodServiceTest {

    private lateinit var foodService: FoodService
    private lateinit var foodRepository: FoodRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var brandRepository: BrandRepository

    private val testProfileId = UUID.randomUUID()
    private val otherProfileId = UUID.randomUUID()
    private val now = Instant.now()

    private val testCategory = Category(
        id = 1,
        name = "Protein",
        icon = "drumstick",
        isSystem = true,
        createdAt = now
    )

    private val testBrand = Brand(
        id = 1,
        profileId = testProfileId,
        name = "Test Brand",
        description = "Test Description",
        photoUrl = null,
        countryId = null,
        createdAt = now,
        updatedAt = now
    )

    private val testFood = Food(
        id = UUID.randomUUID(),
        profileId = testProfileId,
        name = "Chicken Breast",
        categoryId = 1,
        brandId = 1,
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
        foodId = testFood.id,
        name = "1 medium breast",
        amountGrams = BigDecimal("174"),
        createdAt = now
    )

    @BeforeEach
    fun setup() {
        foodRepository = mockk()
        categoryRepository = mockk()
        brandRepository = mockk()

        foodService = FoodService(foodRepository, categoryRepository, brandRepository)
    }

    @Nested
    @DisplayName("getFoods")
    inner class GetFoods {

        @Test
        fun `should return paginated food list`() {
            every { foodRepository.findAllByProfileId(testProfileId, 0, 20, null, null, null, "createdAt") } returns listOf(testFood)
            every { foodRepository.countByProfileId(testProfileId, null, null, null) } returns 1L
            every { categoryRepository.findById(1) } returns testCategory
            every { brandRepository.findById(1) } returns testBrand
            every { foodRepository.getLastUsedAt(testProfileId, testFood.id) } returns null

            val response = foodService.getFoods(testProfileId, 0, 20, null, null, null, "createdAt")

            assertNotNull(response)
            assertEquals(1, response.content.size)
            assertEquals(testFood.name, response.content[0].name)
            assertEquals(0, response.page.number)
            assertEquals(20, response.page.size)
            assertEquals(1, response.page.totalElements)
        }

        @Test
        fun `should clamp page size to 100`() {
            every { foodRepository.findAllByProfileId(testProfileId, 0, 100, null, null, null, "createdAt") } returns emptyList()
            every { foodRepository.countByProfileId(testProfileId, null, null, null) } returns 0L

            val response = foodService.getFoods(testProfileId, 0, 200, null, null, null, "createdAt")

            assertEquals(100, response.page.size)
        }

        @Test
        fun `should filter by category and brand`() {
            every { foodRepository.findAllByProfileId(testProfileId, 0, 20, 1, 1, null, "createdAt") } returns listOf(testFood)
            every { foodRepository.countByProfileId(testProfileId, 1, 1, null) } returns 1L
            every { categoryRepository.findById(1) } returns testCategory
            every { brandRepository.findById(1) } returns testBrand
            every { foodRepository.getLastUsedAt(testProfileId, testFood.id) } returns null

            val response = foodService.getFoods(testProfileId, 0, 20, 1, 1, null, "createdAt")

            assertEquals(1, response.content.size)
        }
    }

    @Nested
    @DisplayName("getFoodById")
    inner class GetFoodById {

        @Test
        fun `should return food details`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { categoryRepository.findById(1) } returns testCategory
            every { brandRepository.findById(1) } returns testBrand
            every { foodRepository.findPortionsByFoodId(testFood.id) } returns listOf(testPortion)

            val response = foodService.getFoodById(testFood.id, testProfileId)

            assertNotNull(response)
            assertEquals(testFood.id, response.id)
            assertEquals(testFood.name, response.name)
            assertEquals(1, response.portions.size)
        }

        @Test
        fun `should throw FoodNotFoundException when food does not exist`() {
            val nonExistentId = UUID.randomUUID()
            every { foodRepository.findById(nonExistentId) } returns null

            assertThrows<FoodNotFoundException> {
                foodService.getFoodById(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw FoodNotOwnedException when food belongs to another user`() {
            every { foodRepository.findById(testFood.id) } returns testFood

            assertThrows<FoodNotOwnedException> {
                foodService.getFoodById(testFood.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("createFood")
    inner class CreateFood {

        @Test
        fun `should create food successfully`() {
            val request = CreateFoodRequest(
                name = "Chicken Breast",
                categoryId = 1,
                brandId = 1,
                metricType = MetricType.GRAMS,
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("165"),
                    fatPer100 = BigDecimal("3.6"),
                    carbsPer100 = BigDecimal("0"),
                    proteinPer100 = BigDecimal("31")
                ),
                portions = listOf(
                    PortionRequest("1 medium breast", BigDecimal("174"))
                )
            )

            every { categoryRepository.existsById(1) } returns true
            every { brandRepository.findById(1) } returns testBrand
            every { foodRepository.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns testFood
            every { foodRepository.createPortions(testFood.id, any()) } returns listOf(testPortion)
            every { categoryRepository.findById(1) } returns testCategory

            val response = foodService.createFood(request, testProfileId)

            assertNotNull(response)
            assertEquals(testFood.name, response.name)
            verify { foodRepository.create(any(), "Chicken Breast", 1, 1, MetricType.GRAMS, any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should throw CategoryNotFoundException when category does not exist`() {
            val request = CreateFoodRequest(
                name = "Test Food",
                categoryId = 999,
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("100"),
                    fatPer100 = BigDecimal("5"),
                    carbsPer100 = BigDecimal("10"),
                    proteinPer100 = BigDecimal("20")
                )
            )

            every { categoryRepository.existsById(999) } returns false

            assertThrows<CategoryNotFoundException> {
                foodService.createFood(request, testProfileId)
            }
        }

        @Test
        fun `should throw BrandNotFoundException when brand does not exist`() {
            val request = CreateFoodRequest(
                name = "Test Food",
                brandId = 999,
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("100"),
                    fatPer100 = BigDecimal("5"),
                    carbsPer100 = BigDecimal("10"),
                    proteinPer100 = BigDecimal("20")
                )
            )

            every { brandRepository.findById(999) } returns null

            assertThrows<BrandNotFoundException> {
                foodService.createFood(request, testProfileId)
            }
        }

        @Test
        fun `should throw BrandNotFoundException when brand belongs to another user`() {
            val otherUserBrand = testBrand.copy(profileId = otherProfileId)
            val request = CreateFoodRequest(
                name = "Test Food",
                brandId = 1,
                nutrition = NutritionRequest(
                    caloriesPer100 = BigDecimal("100"),
                    fatPer100 = BigDecimal("5"),
                    carbsPer100 = BigDecimal("10"),
                    proteinPer100 = BigDecimal("20")
                )
            )

            every { brandRepository.findById(1) } returns otherUserBrand

            assertThrows<BrandNotFoundException> {
                foodService.createFood(request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("updateFood")
    inner class UpdateFood {

        @Test
        fun `should update food successfully`() {
            val request = UpdateFoodRequest(name = "Updated Chicken")
            val updatedFood = testFood.copy(name = "Updated Chicken")

            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.update(testFood.id, "Updated Chicken", null, null, null, null, null, null, null, null, null, null) } returns updatedFood
            every { categoryRepository.findById(1) } returns testCategory
            every { brandRepository.findById(1) } returns testBrand
            every { foodRepository.findPortionsByFoodId(testFood.id) } returns emptyList()

            val response = foodService.updateFood(testFood.id, request, testProfileId)

            assertNotNull(response)
            assertEquals("Updated Chicken", response.name)
        }

        @Test
        fun `should throw FoodNotFoundException when food does not exist`() {
            val nonExistentId = UUID.randomUUID()
            val request = UpdateFoodRequest(name = "Updated")

            every { foodRepository.findById(nonExistentId) } returns null

            assertThrows<FoodNotFoundException> {
                foodService.updateFood(nonExistentId, request, testProfileId)
            }
        }

        @Test
        fun `should throw FoodNotOwnedException when food belongs to another user`() {
            val request = UpdateFoodRequest(name = "Updated")

            every { foodRepository.findById(testFood.id) } returns testFood

            assertThrows<FoodNotOwnedException> {
                foodService.updateFood(testFood.id, request, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("deleteFood")
    inner class DeleteFood {

        @Test
        fun `should delete food successfully`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.isUsedInRecipes(testFood.id) } returns false
            every { foodRepository.delete(testFood.id) } returns true

            foodService.deleteFood(testFood.id, testProfileId)

            verify { foodRepository.delete(testFood.id) }
        }

        @Test
        fun `should throw FoodInUseException when food is used in recipes`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.isUsedInRecipes(testFood.id) } returns true

            assertThrows<FoodInUseException> {
                foodService.deleteFood(testFood.id, testProfileId)
            }
        }

        @Test
        fun `should throw FoodNotFoundException when food does not exist`() {
            val nonExistentId = UUID.randomUUID()

            every { foodRepository.findById(nonExistentId) } returns null

            assertThrows<FoodNotFoundException> {
                foodService.deleteFood(nonExistentId, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("addPortion")
    inner class AddPortion {

        @Test
        fun `should add portion successfully`() {
            val request = CreatePortionRequest("1 cup", BigDecimal("240"))

            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.portionExistsForFood(testFood.id, "1 cup") } returns false
            every { foodRepository.createPortion(testFood.id, "1 cup", BigDecimal("240")) } returns testPortion.copy(name = "1 cup", amountGrams = BigDecimal("240"))

            val response = foodService.addPortion(testFood.id, request, testProfileId)

            assertNotNull(response)
            assertEquals("1 cup", response.name)
        }

        @Test
        fun `should throw PortionAlreadyExistsException when portion name exists`() {
            val request = CreatePortionRequest("1 medium breast", BigDecimal("174"))

            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.portionExistsForFood(testFood.id, "1 medium breast") } returns true

            assertThrows<PortionAlreadyExistsException> {
                foodService.addPortion(testFood.id, request, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("getPortions")
    inner class GetPortions {

        @Test
        fun `should return portions for food`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionsByFoodId(testFood.id) } returns listOf(testPortion)

            val response = foodService.getPortions(testFood.id, testProfileId)

            assertNotNull(response)
            assertEquals(1, response.size)
            assertEquals(testPortion.name, response[0].name)
        }

        @Test
        fun `should return empty list when no portions exist`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionsByFoodId(testFood.id) } returns emptyList()

            val response = foodService.getPortions(testFood.id, testProfileId)

            assertNotNull(response)
            assertEquals(0, response.size)
        }

        @Test
        fun `should throw FoodNotFoundException when food does not exist`() {
            val nonExistentId = UUID.randomUUID()
            every { foodRepository.findById(nonExistentId) } returns null

            assertThrows<FoodNotFoundException> {
                foodService.getPortions(nonExistentId, testProfileId)
            }
        }

        @Test
        fun `should throw FoodNotOwnedException when food belongs to another user`() {
            every { foodRepository.findById(testFood.id) } returns testFood

            assertThrows<FoodNotOwnedException> {
                foodService.getPortions(testFood.id, otherProfileId)
            }
        }
    }

    @Nested
    @DisplayName("deletePortion")
    inner class DeletePortion {

        @Test
        fun `should delete portion successfully`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionByIdAndFoodId(1, testFood.id) } returns testPortion
            every { foodRepository.deletePortion(1) } returns true

            foodService.deletePortion(testFood.id, 1, testProfileId)

            verify { foodRepository.deletePortion(1) }
        }

        @Test
        fun `should throw PortionNotFoundException when portion does not exist`() {
            every { foodRepository.findById(testFood.id) } returns testFood
            every { foodRepository.findPortionByIdAndFoodId(999, testFood.id) } returns null

            assertThrows<PortionNotFoundException> {
                foodService.deletePortion(testFood.id, 999, testProfileId)
            }
        }
    }

    @Nested
    @DisplayName("getRecentFoods")
    inner class GetRecentFoods {

        @Test
        fun `should return recent foods`() {
            val recentInfo = now to 5
            every { foodRepository.findRecentFoods(testProfileId, 20) } returns listOf(testFood to recentInfo)
            every { foodRepository.findPortionsByFoodId(testFood.id) } returns listOf(testPortion)

            val response = foodService.getRecentFoods(testProfileId, 20)

            assertNotNull(response)
            assertEquals(1, response.foods.size)
            assertEquals(testFood.name, response.foods[0].name)
            assertEquals(5, response.foods[0].useCount)
        }

        @Test
        fun `should clamp limit to 50`() {
            every { foodRepository.findRecentFoods(testProfileId, 50) } returns emptyList()

            val response = foodService.getRecentFoods(testProfileId, 100)

            assertNotNull(response)
            verify { foodRepository.findRecentFoods(testProfileId, 50) }
        }
    }
}
