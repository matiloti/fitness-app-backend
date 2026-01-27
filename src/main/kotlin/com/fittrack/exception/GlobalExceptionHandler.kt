package com.fittrack.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    data class ErrorResponse(
        val error: ErrorDetail
    )

    data class ErrorDetail(
        val code: String,
        val message: String,
        val timestamp: Instant = Instant.now(),
        val errors: Map<String, String>? = null
    )

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid credentials attempt: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExists(ex: EmailAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.warn("Email already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpired(ex: TokenExpiredException): ResponseEntity<ErrorResponse> {
        logger.debug("Token expired: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(TokenInvalidException::class)
    fun handleTokenInvalid(ex: TokenInvalidException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid token: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(ProfileNotFoundException::class)
    fun handleProfileNotFound(ex: ProfileNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Profile not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidCountryException::class)
    fun handleInvalidCountry(ex: InvalidCountryException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid country: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidFitnessGoalException::class)
    fun handleInvalidFitnessGoal(ex: InvalidFitnessGoalException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid fitness goal: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(ex: RateLimitExceededException): ResponseEntity<ErrorResponse> {
        logger.warn("Rate limit exceeded: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    // ========== Food Exceptions ==========

    @ExceptionHandler(FoodNotFoundException::class)
    fun handleFoodNotFound(ex: FoodNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Food not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(FoodNotOwnedException::class)
    fun handleFoodNotOwned(ex: FoodNotOwnedException): ResponseEntity<ErrorResponse> {
        logger.warn("Food access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(FoodInUseException::class)
    fun handleFoodInUse(ex: FoodInUseException): ResponseEntity<ErrorResponse> {
        logger.debug("Food in use: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(CategoryNotFoundException::class)
    fun handleCategoryNotFound(ex: CategoryNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Category not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(PortionNotFoundException::class)
    fun handlePortionNotFound(ex: PortionNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Portion not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(PortionAlreadyExistsException::class)
    fun handlePortionAlreadyExists(ex: PortionAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.debug("Portion already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    // ========== Brand Exceptions ==========

    @ExceptionHandler(BrandNotFoundException::class)
    fun handleBrandNotFound(ex: BrandNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Brand not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(BrandNotOwnedException::class)
    fun handleBrandNotOwned(ex: BrandNotOwnedException): ResponseEntity<ErrorResponse> {
        logger.warn("Brand access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(BrandAlreadyExistsException::class)
    fun handleBrandAlreadyExists(ex: BrandAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.debug("Brand already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    // ========== Day Exceptions ==========

    @ExceptionHandler(DayNotFoundException::class)
    fun handleDayNotFound(ex: DayNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Day not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidDateException::class)
    fun handleInvalidDate(ex: InvalidDateException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid date: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidDateRangeException::class)
    fun handleInvalidDateRange(ex: InvalidDateRangeException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid date range: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(MissingProfileDataException::class)
    fun handleMissingProfileData(ex: MissingProfileDataException): ResponseEntity<ErrorResponse> {
        logger.debug("Missing profile data: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    // ========== Meal Exceptions ==========

    @ExceptionHandler(MealNotFoundException::class)
    fun handleMealNotFound(ex: MealNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Meal not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(MealNotOwnedException::class)
    fun handleMealNotOwned(ex: MealNotOwnedException): ResponseEntity<ErrorResponse> {
        logger.warn("Meal access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(MealItemNotFoundException::class)
    fun handleMealItemNotFound(ex: MealItemNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Meal item not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidMealTypeException::class)
    fun handleInvalidMealType(ex: InvalidMealTypeException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid meal type: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidQuantityException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid quantity: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidItemTypeException::class)
    fun handleInvalidItemType(ex: InvalidItemTypeException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid item type: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(RecipeNotFoundException::class)
    fun handleRecipeNotFound(ex: RecipeNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Recipe not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(RecipeNotOwnedException::class)
    fun handleRecipeNotOwned(ex: RecipeNotOwnedException): ResponseEntity<ErrorResponse> {
        logger.warn("Recipe access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(RecipeIngredientNotFoundException::class)
    fun handleRecipeIngredientNotFound(ex: RecipeIngredientNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Recipe ingredient not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(RecipeStepNotFoundException::class)
    fun handleRecipeStepNotFound(ex: RecipeStepNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Recipe step not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(LastIngredientException::class)
    fun handleLastIngredient(ex: LastIngredientException): ResponseEntity<ErrorResponse> {
        logger.debug("Cannot delete last ingredient: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidStepOrderException::class)
    fun handleInvalidStepOrder(ex: InvalidStepOrderException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid step order: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    // ========== Body Metrics Exceptions ==========

    @ExceptionHandler(BodyMetricsNotFoundException::class)
    fun handleBodyMetricsNotFound(ex: BodyMetricsNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Body metrics not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(BodyMetricsNotOwnedException::class)
    fun handleBodyMetricsNotOwned(ex: BodyMetricsNotOwnedException): ResponseEntity<ErrorResponse> {
        logger.warn("Body metrics access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(NoMeasurementsProvidedException::class)
    fun handleNoMeasurementsProvided(ex: NoMeasurementsProvidedException): ResponseEntity<ErrorResponse> {
        logger.debug("No measurements provided: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(PhotoNotFoundException::class)
    fun handlePhotoNotFound(ex: PhotoNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Photo not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidPhotoPositionException::class)
    fun handleInvalidPhotoPosition(ex: InvalidPhotoPositionException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid photo position: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(MaxPhotosExceededException::class)
    fun handleMaxPhotosExceeded(ex: MaxPhotosExceededException): ResponseEntity<ErrorResponse> {
        logger.debug("Max photos exceeded: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    // ========== Workout Exceptions ==========

    @ExceptionHandler(WorkoutNotFoundException::class)
    fun handleWorkoutNotFound(ex: WorkoutNotFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("Workout not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(WorkoutNotOwnedException::class)
    fun handleWorkoutNotOwned(ex: WorkoutNotOwnedException): ResponseEntity<ErrorResponse> {
        logger.warn("Workout access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidWorkoutTypeException::class)
    fun handleInvalidWorkoutType(ex: InvalidWorkoutTypeException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid workout type: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidDurationException::class)
    fun handleInvalidDuration(ex: InvalidDurationException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid duration: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(InvalidCaloriesException::class)
    fun handleInvalidCalories(ex: InvalidCaloriesException): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid calories: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(MissingWeightException::class)
    fun handleMissingWeight(ex: MissingWeightException): ResponseEntity<ErrorResponse> {
        logger.debug("Missing weight: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        logger.debug("Validation error: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message, errors = ex.errors)))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.allErrors.associate { error ->
            val fieldName = (error as? FieldError)?.field ?: "unknown"
            fieldName to (error.defaultMessage ?: "Invalid value")
        }
        logger.debug("Validation errors: $errors")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail("VALIDATION_ERROR", "Request validation failed", errors = errors)))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.debug("Message not readable: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail("VALIDATION_ERROR", "Invalid request body")))
    }

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException): ResponseEntity<ErrorResponse> {
        logger.warn("Application exception: ${ex.errorCode} - ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorDetail(ex.errorCode, ex.message)))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ErrorDetail("INTERNAL_ERROR", "An unexpected error occurred")))
    }
}
