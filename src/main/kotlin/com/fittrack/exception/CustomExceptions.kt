package com.fittrack.exception

/**
 * Base class for all application-specific exceptions
 */
sealed class AppException(
    val errorCode: String,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// ========== Authentication Exceptions ==========

class InvalidCredentialsException(
    message: String = "The email or password is incorrect."
) : AppException("INVALID_CREDENTIALS", message)

class EmailAlreadyExistsException(
    email: String
) : AppException("EMAIL_ALREADY_EXISTS", "Email '$email' is already registered.")

class TokenExpiredException(
    message: String = "Token has expired."
) : AppException("TOKEN_EXPIRED", message)

class TokenInvalidException(
    message: String = "Token is invalid."
) : AppException("TOKEN_INVALID", message)

class PasswordTooWeakException(
    message: String = "Password does not meet requirements."
) : AppException("PASSWORD_TOO_WEAK", message)

class RateLimitExceededException(
    message: String = "Too many requests. Please try again later."
) : AppException("RATE_LIMIT_EXCEEDED", message)

// ========== Profile Exceptions ==========

class ProfileNotFoundException(
    message: String = "Profile not found."
) : AppException("PROFILE_NOT_FOUND", message)

class InvalidCountryException(
    countryCode: String
) : AppException("INVALID_COUNTRY", "Country code '$countryCode' is not valid.")

class InvalidActivityLevelException(
    message: String = "Activity level is not valid."
) : AppException("INVALID_ACTIVITY_LEVEL", message)

class InvalidFitnessGoalException(
    message: String = "Fitness goal configuration is invalid."
) : AppException("INVALID_FITNESS_GOAL", message)

class InvalidSexException(
    message: String = "Sex value is not valid."
) : AppException("INVALID_SEX", message)

class InvalidDateOfBirthException(
    message: String = "Date of birth is invalid or in the future."
) : AppException("INVALID_DATE_OF_BIRTH", message)

class InvalidHeightException(
    message: String = "Height is out of valid range."
) : AppException("INVALID_HEIGHT", message)

// ========== File Exceptions ==========

class FileTooLargeException(
    maxSizeMb: Int = 5
) : AppException("FILE_TOO_LARGE", "Uploaded file exceeds ${maxSizeMb}MB.")

class InvalidFileTypeException(
    message: String = "File type is not supported."
) : AppException("INVALID_FILE_TYPE", message)

// ========== Food Exceptions ==========

class FoodNotFoundException(
    message: String = "Food not found."
) : AppException("FOOD_NOT_FOUND", message)

class FoodNotOwnedException(
    message: String = "You don't have permission to access this food."
) : AppException("FOOD_NOT_OWNED", message)

class FoodInUseException(
    message: String = "Food is used in recipes and cannot be deleted."
) : AppException("FOOD_IN_USE", message)

class CategoryNotFoundException(
    message: String = "Category not found."
) : AppException("CATEGORY_NOT_FOUND", message)

class PortionNotFoundException(
    message: String = "Portion not found."
) : AppException("PORTION_NOT_FOUND", message)

class PortionAlreadyExistsException(
    portionName: String
) : AppException("PORTION_ALREADY_EXISTS", "Portion '$portionName' already exists for this food.")

// ========== Brand Exceptions ==========

class BrandNotFoundException(
    message: String = "Brand not found."
) : AppException("BRAND_NOT_FOUND", message)

class BrandNotOwnedException(
    message: String = "You don't have permission to access this brand."
) : AppException("BRAND_NOT_OWNED", message)

class BrandAlreadyExistsException(
    brandName: String
) : AppException("BRAND_ALREADY_EXISTS", "Brand '$brandName' already exists.")

// ========== Validation Exception ==========

class ValidationException(
    message: String,
    val errors: Map<String, String> = emptyMap()
) : AppException("VALIDATION_ERROR", message)
