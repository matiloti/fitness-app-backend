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
import com.fittrack.model.dto.food.BrandSummary
import com.fittrack.model.dto.food.CategorySummary
import com.fittrack.model.dto.food.CreateFoodRequest
import com.fittrack.model.dto.food.CreatePortionRequest
import com.fittrack.model.dto.food.FoodDetailResponse
import com.fittrack.model.dto.food.FoodListItem
import com.fittrack.model.dto.food.FoodListResponse
import com.fittrack.model.dto.food.NutritionInfo
import com.fittrack.model.dto.food.PageInfo
import com.fittrack.model.dto.food.PortionResponse
import com.fittrack.model.dto.food.RecentFoodItem
import com.fittrack.model.dto.food.RecentFoodsResponse
import com.fittrack.model.dto.food.UpdateFoodRequest
import com.fittrack.repository.BrandRepository
import com.fittrack.repository.CategoryRepository
import com.fittrack.repository.FoodRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

@Service
class FoodService(
    private val foodRepository: FoodRepository,
    private val categoryRepository: CategoryRepository,
    private val brandRepository: BrandRepository
) {

    fun getFoods(
        profileId: UUID,
        page: Int,
        size: Int,
        categoryId: Int?,
        brandId: Int?,
        search: String?,
        sort: String
    ): FoodListResponse {
        val validatedSize = size.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)

        val foods = foodRepository.findAllByProfileId(
            profileId = profileId,
            page = validatedPage,
            size = validatedSize,
            categoryId = categoryId,
            brandId = brandId,
            search = search,
            sort = sort
        )

        val totalElements = foodRepository.countByProfileId(
            profileId = profileId,
            categoryId = categoryId,
            brandId = brandId,
            search = search
        )

        val content = foods.map { food ->
            val category = food.categoryId?.let { categoryRepository.findById(it) }
            val brand = food.brandId?.let { brandRepository.findById(it) }
            val lastUsedAt = foodRepository.getLastUsedAt(profileId, food.id)

            toFoodListItem(food, category, brand, lastUsedAt)
        }

        return FoodListResponse(
            content = content,
            page = PageInfo(
                number = validatedPage,
                size = validatedSize,
                totalElements = totalElements,
                totalPages = ceil(totalElements.toDouble() / validatedSize).toInt()
            )
        )
    }

    fun getFoodById(id: UUID, profileId: UUID): FoodDetailResponse {
        val food = foodRepository.findById(id)
            ?: throw FoodNotFoundException()

        // Check ownership
        if (food.profileId != profileId) {
            throw FoodNotOwnedException()
        }

        val category = food.categoryId?.let { categoryRepository.findById(it) }
        val brand = food.brandId?.let { brandRepository.findById(it) }
        val portions = foodRepository.findPortionsByFoodId(id)

        return toFoodDetailResponse(food, category, brand, portions)
    }

    @Transactional
    fun createFood(request: CreateFoodRequest, profileId: UUID): FoodDetailResponse {
        // Validate category exists if provided
        request.categoryId?.let { catId ->
            if (!categoryRepository.existsById(catId)) {
                throw CategoryNotFoundException()
            }
        }

        // Validate brand exists and is owned by user if provided
        val brand = request.brandId?.let { brandId ->
            brandRepository.findById(brandId)?.also {
                if (it.profileId != profileId) {
                    throw BrandNotFoundException()
                }
            } ?: throw BrandNotFoundException()
        }

        val food = foodRepository.create(
            profileId = profileId,
            name = request.name.trim(),
            categoryId = request.categoryId,
            brandId = request.brandId,
            metricType = request.metricType,
            caloriesPer100 = request.nutrition.caloriesPer100,
            fatPer100 = request.nutrition.fatPer100,
            carbsPer100 = request.nutrition.carbsPer100,
            proteinPer100 = request.nutrition.proteinPer100,
            saltPer100 = request.nutrition.saltPer100,
            sugarPer100 = request.nutrition.sugarPer100,
            fiberPer100 = request.nutrition.fiberPer100,
            saturatedFatPer100 = request.nutrition.saturatedFatPer100
        )

        // Create portions if provided
        val portions = if (request.portions.isNotEmpty()) {
            foodRepository.createPortions(
                food.id,
                request.portions.map { it.name.trim() to it.amountGrams }
            )
        } else {
            emptyList()
        }

        val category = food.categoryId?.let { categoryRepository.findById(it) }

        return toFoodDetailResponse(food, category, brand, portions)
    }

    @Transactional
    fun updateFood(id: UUID, request: UpdateFoodRequest, profileId: UUID): FoodDetailResponse {
        val existingFood = foodRepository.findById(id)
            ?: throw FoodNotFoundException()

        if (existingFood.profileId != profileId) {
            throw FoodNotOwnedException()
        }

        // Validate category if provided
        request.categoryId?.let { catId ->
            if (!categoryRepository.existsById(catId)) {
                throw CategoryNotFoundException()
            }
        }

        // Validate brand if provided
        request.brandId?.let { brandId ->
            val brand = brandRepository.findById(brandId)
                ?: throw BrandNotFoundException()
            if (brand.profileId != profileId) {
                throw BrandNotFoundException()
            }
        }

        val updatedFood = foodRepository.update(
            id = id,
            name = request.name?.trim(),
            categoryId = request.categoryId,
            brandId = request.brandId,
            caloriesPer100 = request.nutrition?.caloriesPer100,
            fatPer100 = request.nutrition?.fatPer100,
            carbsPer100 = request.nutrition?.carbsPer100,
            proteinPer100 = request.nutrition?.proteinPer100,
            saltPer100 = request.nutrition?.saltPer100,
            sugarPer100 = request.nutrition?.sugarPer100,
            fiberPer100 = request.nutrition?.fiberPer100,
            saturatedFatPer100 = request.nutrition?.saturatedFatPer100
        ) ?: throw FoodNotFoundException()

        val category = updatedFood.categoryId?.let { categoryRepository.findById(it) }
        val brand = updatedFood.brandId?.let { brandRepository.findById(it) }
        val portions = foodRepository.findPortionsByFoodId(id)

        return toFoodDetailResponse(updatedFood, category, brand, portions)
    }

    @Transactional
    fun deleteFood(id: UUID, profileId: UUID) {
        val food = foodRepository.findById(id)
            ?: throw FoodNotFoundException()

        if (food.profileId != profileId) {
            throw FoodNotOwnedException()
        }

        // Check if food is used in recipes
        if (foodRepository.isUsedInRecipes(id)) {
            throw FoodInUseException()
        }

        foodRepository.delete(id)
    }

    // ========== Portion Methods ==========

    fun getPortions(foodId: UUID, profileId: UUID): List<PortionResponse> {
        val food = foodRepository.findById(foodId)
            ?: throw FoodNotFoundException()

        if (food.profileId != profileId) {
            throw FoodNotOwnedException()
        }

        val portions = foodRepository.findPortionsByFoodId(foodId)
        return portions.map { toPortionResponse(it) }
    }

    fun addPortion(foodId: UUID, request: CreatePortionRequest, profileId: UUID): PortionResponse {
        val food = foodRepository.findById(foodId)
            ?: throw FoodNotFoundException()

        if (food.profileId != profileId) {
            throw FoodNotOwnedException()
        }

        // Check if portion with same name already exists
        if (foodRepository.portionExistsForFood(foodId, request.name.trim())) {
            throw PortionAlreadyExistsException(request.name)
        }

        val portion = foodRepository.createPortion(
            foodId = foodId,
            name = request.name.trim(),
            amountGrams = request.amountGrams
        )

        return toPortionResponse(portion)
    }

    fun deletePortion(foodId: UUID, portionId: Int, profileId: UUID) {
        val food = foodRepository.findById(foodId)
            ?: throw FoodNotFoundException()

        if (food.profileId != profileId) {
            throw FoodNotOwnedException()
        }

        val portion = foodRepository.findPortionByIdAndFoodId(portionId, foodId)
            ?: throw PortionNotFoundException()

        foodRepository.deletePortion(portionId)
    }

    // ========== Recent Foods ==========

    fun getRecentFoods(profileId: UUID, limit: Int): RecentFoodsResponse {
        val validatedLimit = limit.coerceIn(1, 50)

        val recentFoods = foodRepository.findRecentFoods(profileId, validatedLimit)

        val items = recentFoods.map { (food, recentInfo) ->
            val (lastUsedAt, useCount) = recentInfo
            val portions = foodRepository.findPortionsByFoodId(food.id)
            val defaultPortion = portions.firstOrNull()

            RecentFoodItem(
                id = food.id,
                name = food.name,
                nutrition = toNutritionInfo(food),
                defaultPortion = defaultPortion?.let { toPortionResponse(it) },
                lastUsedAt = lastUsedAt,
                useCount = useCount
            )
        }

        return RecentFoodsResponse(foods = items)
    }

    // ========== Helper Methods ==========

    private fun toFoodListItem(
        food: Food,
        category: Category?,
        brand: Brand?,
        lastUsedAt: Instant?
    ): FoodListItem {
        return FoodListItem(
            id = food.id,
            name = food.name,
            category = category?.let { CategorySummary(it.id, it.name, it.icon) },
            brand = brand?.let { BrandSummary(it.id, it.name) },
            metricType = food.metricType,
            nutrition = toNutritionInfo(food),
            lastUsedAt = lastUsedAt
        )
    }

    private fun toFoodDetailResponse(
        food: Food,
        category: Category?,
        brand: Brand?,
        portions: List<FoodPortion>
    ): FoodDetailResponse {
        return FoodDetailResponse(
            id = food.id,
            name = food.name,
            category = category?.let { CategorySummary(it.id, it.name, it.icon) },
            brand = brand?.let { BrandSummary(it.id, it.name) },
            metricType = food.metricType,
            nutrition = toNutritionInfo(food),
            portions = portions.map { toPortionResponse(it) },
            createdAt = food.createdAt,
            updatedAt = food.updatedAt
        )
    }

    private fun toNutritionInfo(food: Food): NutritionInfo {
        return NutritionInfo(
            caloriesPer100 = food.caloriesPer100,
            fatPer100 = food.fatPer100,
            carbsPer100 = food.carbsPer100,
            proteinPer100 = food.proteinPer100,
            saltPer100 = food.saltPer100,
            sugarPer100 = food.sugarPer100,
            fiberPer100 = food.fiberPer100,
            saturatedFatPer100 = food.saturatedFatPer100
        )
    }

    private fun toPortionResponse(portion: FoodPortion): PortionResponse {
        return PortionResponse(
            id = portion.id,
            name = portion.name,
            amountGrams = portion.amountGrams
        )
    }
}
