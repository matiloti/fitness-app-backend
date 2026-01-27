package com.fittrack.service

import com.fittrack.exception.BrandAlreadyExistsException
import com.fittrack.exception.BrandNotFoundException
import com.fittrack.exception.BrandNotOwnedException
import com.fittrack.exception.InvalidCountryException
import com.fittrack.model.Brand
import com.fittrack.model.Country
import com.fittrack.model.dto.food.BrandDetailResponse
import com.fittrack.model.dto.food.BrandListResponse
import com.fittrack.model.dto.food.CountrySummary
import com.fittrack.model.dto.food.CreateBrandRequest
import com.fittrack.model.dto.food.PageInfo
import com.fittrack.model.dto.food.UpdateBrandRequest
import com.fittrack.repository.BrandRepository
import com.fittrack.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.math.ceil

@Service
class BrandService(
    private val brandRepository: BrandRepository,
    private val userRepository: UserRepository
) {

    fun getBrands(
        profileId: UUID,
        page: Int,
        size: Int,
        search: String?
    ): BrandListResponse {
        val validatedSize = size.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)

        val brands = brandRepository.findAllByProfileId(
            profileId = profileId,
            page = validatedPage,
            size = validatedSize,
            search = search
        )

        val totalElements = brandRepository.countByProfileId(profileId, search)

        val content = brands.map { brand ->
            val country = brand.countryId?.let { userRepository.findCountryById(it) }
            toBrandDetailResponse(brand, country)
        }

        return BrandListResponse(
            content = content,
            page = PageInfo(
                number = validatedPage,
                size = validatedSize,
                totalElements = totalElements,
                totalPages = ceil(totalElements.toDouble() / validatedSize).toInt()
            )
        )
    }

    fun getBrandById(id: Int, profileId: UUID): BrandDetailResponse {
        val brand = brandRepository.findById(id)
            ?: throw BrandNotFoundException()

        if (brand.profileId != profileId) {
            throw BrandNotOwnedException()
        }

        val country = brand.countryId?.let { userRepository.findCountryById(it) }
        return toBrandDetailResponse(brand, country)
    }

    @Transactional
    fun createBrand(request: CreateBrandRequest, profileId: UUID): BrandDetailResponse {
        // Check if brand name already exists for this user
        if (brandRepository.existsByProfileIdAndName(profileId, request.name.trim())) {
            throw BrandAlreadyExistsException(request.name)
        }

        // Validate country code if provided
        val country = request.countryCode?.let { code ->
            userRepository.findCountryByCode(code)
                ?: throw InvalidCountryException(code)
        }

        val brand = brandRepository.create(
            profileId = profileId,
            name = request.name.trim(),
            description = request.description?.trim(),
            photoUrl = request.photoUrl?.trim(),
            countryId = country?.id
        )

        return toBrandDetailResponse(brand, country)
    }

    @Transactional
    fun updateBrand(id: Int, request: UpdateBrandRequest, profileId: UUID): BrandDetailResponse {
        val existingBrand = brandRepository.findById(id)
            ?: throw BrandNotFoundException()

        if (existingBrand.profileId != profileId) {
            throw BrandNotOwnedException()
        }

        // Check for name conflicts if name is being changed
        request.name?.let { newName ->
            if (brandRepository.existsByProfileIdAndNameExcludingId(profileId, newName.trim(), id)) {
                throw BrandAlreadyExistsException(newName)
            }
        }

        // Validate country code if provided
        val countryId = request.countryCode?.let { code ->
            val country = userRepository.findCountryByCode(code)
                ?: throw InvalidCountryException(code)
            country.id
        }

        val updatedBrand = brandRepository.update(
            id = id,
            name = request.name?.trim(),
            description = request.description?.trim(),
            photoUrl = request.photoUrl?.trim(),
            countryId = countryId
        ) ?: throw BrandNotFoundException()

        val country = updatedBrand.countryId?.let { userRepository.findCountryById(it) }
        return toBrandDetailResponse(updatedBrand, country)
    }

    private fun toBrandDetailResponse(brand: Brand, country: Country?): BrandDetailResponse {
        return BrandDetailResponse(
            id = brand.id,
            name = brand.name,
            description = brand.description,
            photoUrl = brand.photoUrl,
            country = country?.let { CountrySummary(it.code, it.name) },
            createdAt = brand.createdAt
        )
    }
}
