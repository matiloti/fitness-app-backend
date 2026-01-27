package com.fittrack.service

import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * Service for nutritional calculations following established formulas:
 * - BMR: Mifflin-St Jeor equation
 * - TDEE: BMR x activity level multiplier
 * - Daily Calorie Goal: TDEE + fitness goal adjustment
 * - Macros: Standard percentages (30% protein, 25% fat, 45% carbs)
 */
@Service
class NutritionCalculatorService {

    companion object {
        // Macro distribution percentages
        const val PROTEIN_PERCENT = 30
        const val FAT_PERCENT = 25
        const val CARBS_PERCENT = 45

        // Calories per gram of macronutrients
        const val CALORIES_PER_GRAM_PROTEIN = 4
        const val CALORIES_PER_GRAM_CARBS = 4
        const val CALORIES_PER_GRAM_FAT = 9

        // Minimum daily calorie goal
        const val MINIMUM_CALORIE_GOAL = 1200
    }

    /**
     * Calculate Basal Metabolic Rate using Mifflin-St Jeor equation.
     *
     * Male:   BMR = (10 x weight_kg) + (6.25 x height_cm) - (5 x age) + 5
     * Female: BMR = (10 x weight_kg) + (6.25 x height_cm) - (5 x age) - 161
     *
     * @param weightKg Weight in kilograms
     * @param heightCm Height in centimeters
     * @param age Age in years
     * @param sex Biological sex
     * @return BMR in kcal/day
     */
    fun calculateBmr(weightKg: BigDecimal, heightCm: BigDecimal, age: Int, sex: Sex): Int {
        val weight = weightKg.toDouble()
        val height = heightCm.toDouble()

        val bmr = when (sex) {
            Sex.MALE -> (10 * weight) + (6.25 * height) - (5 * age) + 5
            Sex.FEMALE -> (10 * weight) + (6.25 * height) - (5 * age) - 161
        }

        return bmr.roundToInt()
    }

    /**
     * Calculate Total Daily Energy Expenditure.
     *
     * TDEE = BMR x activity level multiplier
     *
     * @param bmr Basal Metabolic Rate
     * @param activityLevel Activity level
     * @return TDEE in kcal/day
     */
    fun calculateTdee(bmr: Int, activityLevel: ActivityLevel): Int {
        return (bmr * activityLevel.multiplier).roundToInt()
    }

    /**
     * Get the calorie adjustment for a fitness goal.
     *
     * @param goalType Type of fitness goal (lose, maintain, gain)
     * @param intensity Intensity of goal (slow, normal, hard, extreme) - required for lose/gain
     * @return Daily calorie adjustment (+/-)
     */
    fun getFitnessGoalAdjustment(goalType: FitnessGoalType, intensity: FitnessGoalIntensity?): Int {
        return when (goalType) {
            FitnessGoalType.MAINTAIN -> 0
            FitnessGoalType.LOSE -> -(intensity?.adjustment ?: 500)
            FitnessGoalType.GAIN -> (intensity?.adjustment ?: 500)
        }
    }

    /**
     * Calculate daily calorie goal.
     *
     * Goal = TDEE + fitness_goal_adjustment
     * Minimum = 1200 kcal (floor)
     *
     * @param tdee Total Daily Energy Expenditure
     * @param goalType Type of fitness goal
     * @param intensity Intensity of goal
     * @return Daily calorie goal in kcal
     */
    fun calculateDailyCalorieGoal(
        tdee: Int,
        goalType: FitnessGoalType,
        intensity: FitnessGoalIntensity?
    ): Int {
        val adjustment = getFitnessGoalAdjustment(goalType, intensity)
        val goal = tdee + adjustment
        return maxOf(goal, MINIMUM_CALORIE_GOAL)
    }

    /**
     * Calculate macro goals in grams based on daily calorie goal.
     *
     * Default distribution:
     * - Protein: 30% of calories (4 kcal/g)
     * - Carbs:   45% of calories (4 kcal/g)
     * - Fat:     25% of calories (9 kcal/g)
     *
     * @param dailyCalorieGoal Daily calorie target in kcal
     * @return MacroGoals with grams for each macro
     */
    fun calculateMacroGoals(dailyCalorieGoal: Int): MacroGoals {
        val proteinCalories = (dailyCalorieGoal * PROTEIN_PERCENT) / 100
        val carbsCalories = (dailyCalorieGoal * CARBS_PERCENT) / 100
        val fatCalories = (dailyCalorieGoal * FAT_PERCENT) / 100

        return MacroGoals(
            proteinGrams = proteinCalories / CALORIES_PER_GRAM_PROTEIN,
            proteinCalories = proteinCalories,
            carbsGrams = carbsCalories / CALORIES_PER_GRAM_CARBS,
            carbsCalories = carbsCalories,
            fatGrams = fatCalories / CALORIES_PER_GRAM_FAT,
            fatCalories = fatCalories
        )
    }

    /**
     * Calculate complete daily goals including calories and macros.
     *
     * @param weightKg Weight in kilograms
     * @param heightCm Height in centimeters
     * @param age Age in years
     * @param sex Biological sex
     * @param activityLevel Activity level
     * @param goalType Type of fitness goal
     * @param intensity Intensity of goal
     * @return Complete daily goals calculation
     */
    fun calculateDailyGoals(
        weightKg: BigDecimal,
        heightCm: BigDecimal,
        age: Int,
        sex: Sex,
        activityLevel: ActivityLevel,
        goalType: FitnessGoalType,
        intensity: FitnessGoalIntensity?
    ): DailyGoalsCalculation {
        val bmr = calculateBmr(weightKg, heightCm, age, sex)
        val tdee = calculateTdee(bmr, activityLevel)
        val calorieGoal = calculateDailyCalorieGoal(tdee, goalType, intensity)
        val macros = calculateMacroGoals(calorieGoal)

        return DailyGoalsCalculation(
            bmr = bmr,
            tdee = tdee,
            dailyCalorieGoal = calorieGoal,
            adjustment = getFitnessGoalAdjustment(goalType, intensity),
            macros = macros
        )
    }

    /**
     * Calculate nutrition from food per 100g/ml values and amount.
     *
     * Formula: value = (valuePer100 * amountGrams) / 100
     *
     * @param per100Value Nutritional value per 100g/ml
     * @param amountGrams Actual amount in grams/ml
     * @return Calculated nutritional value
     */
    fun calculateNutritionValue(per100Value: BigDecimal, amountGrams: BigDecimal): BigDecimal {
        return per100Value.multiply(amountGrams)
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }

    /**
     * Calculate adherence status based on consumed vs goal calories.
     *
     * UNDER:     < 90%
     * ON_TARGET: 90% - 110%
     * OVER:      > 110%
     *
     * @param consumed Consumed calories
     * @param goal Calorie goal
     * @return Adherence status
     */
    fun calculateAdherence(consumed: Int, goal: Int): AdherenceResult {
        if (goal <= 0) {
            return AdherenceResult(AdherenceStatus.ON_TARGET, 0.0)
        }

        val percent = (consumed.toDouble() / goal) * 100
        val status = when {
            percent < 90 -> AdherenceStatus.UNDER
            percent > 110 -> AdherenceStatus.OVER
            else -> AdherenceStatus.ON_TARGET
        }

        return AdherenceResult(status, percent)
    }

    /**
     * Calculate progress percentage (capped at 100% for display purposes).
     *
     * @param consumed Consumed amount
     * @param goal Goal amount
     * @return Progress percentage (0-100+)
     */
    fun calculateProgress(consumed: Int, goal: Int): Double {
        if (goal <= 0) return 0.0
        return (consumed.toDouble() / goal) * 100
    }
}

/**
 * Macro goals calculation result
 */
data class MacroGoals(
    val proteinGrams: Int,
    val proteinCalories: Int,
    val carbsGrams: Int,
    val carbsCalories: Int,
    val fatGrams: Int,
    val fatCalories: Int
)

/**
 * Complete daily goals calculation result
 */
data class DailyGoalsCalculation(
    val bmr: Int,
    val tdee: Int,
    val dailyCalorieGoal: Int,
    val adjustment: Int,
    val macros: MacroGoals
)

/**
 * Adherence status enum
 */
enum class AdherenceStatus {
    UNDER,
    ON_TARGET,
    OVER
}

/**
 * Adherence calculation result
 */
data class AdherenceResult(
    val status: AdherenceStatus,
    val percent: Double
)
