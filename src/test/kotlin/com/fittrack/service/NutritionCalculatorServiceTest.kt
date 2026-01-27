package com.fittrack.service

import com.fittrack.model.ActivityLevel
import com.fittrack.model.FitnessGoalIntensity
import com.fittrack.model.FitnessGoalType
import com.fittrack.model.Sex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Nutrition Calculator Service Tests")
class NutritionCalculatorServiceTest {

    private lateinit var service: NutritionCalculatorService

    @BeforeEach
    fun setup() {
        service = NutritionCalculatorService()
    }

    @Nested
    @DisplayName("BMR Calculation (Mifflin-St Jeor)")
    inner class BmrCalculation {

        @Test
        fun `should calculate BMR for male`() {
            // Male: BMR = (10 x 80) + (6.25 x 180) - (5 x 30) + 5 = 800 + 1125 - 150 + 5 = 1780
            val result = service.calculateBmr(
                weightKg = BigDecimal("80"),
                heightCm = BigDecimal("180"),
                age = 30,
                sex = Sex.MALE
            )

            assertEquals(1780, result)
        }

        @Test
        fun `should calculate BMR for female`() {
            // Female: BMR = (10 x 65) + (6.25 x 165) - (5 x 28) - 161 = 650 + 1031.25 - 140 - 161 = 1380
            val result = service.calculateBmr(
                weightKg = BigDecimal("65"),
                heightCm = BigDecimal("165"),
                age = 28,
                sex = Sex.FEMALE
            )

            assertEquals(1380, result)
        }

        @Test
        fun `should handle decimal weight and height`() {
            // Male: BMR = (10 x 82.5) + (6.25 x 180.5) - (5 x 30) + 5 = 825 + 1128.125 - 150 + 5 = 1808
            val result = service.calculateBmr(
                weightKg = BigDecimal("82.5"),
                heightCm = BigDecimal("180.5"),
                age = 30,
                sex = Sex.MALE
            )

            assertEquals(1808, result)
        }
    }

    @Nested
    @DisplayName("TDEE Calculation")
    inner class TdeeCalculation {

        @Test
        fun `should calculate TDEE with sedentary activity`() {
            // TDEE = 1780 * 1.2 = 2136
            val result = service.calculateTdee(1780, ActivityLevel.SEDENTARY)
            assertEquals(2136, result)
        }

        @Test
        fun `should calculate TDEE with light activity`() {
            // TDEE = 1780 * 1.375 = 2447.5 -> 2448
            val result = service.calculateTdee(1780, ActivityLevel.LIGHT)
            assertEquals(2448, result)
        }

        @Test
        fun `should calculate TDEE with moderate activity`() {
            // TDEE = 1780 * 1.55 = 2759
            val result = service.calculateTdee(1780, ActivityLevel.MODERATE)
            assertEquals(2759, result)
        }

        @Test
        fun `should calculate TDEE with hard activity`() {
            // TDEE = 1780 * 1.725 = 3070.5 -> 3071
            val result = service.calculateTdee(1780, ActivityLevel.HARD)
            assertEquals(3071, result)
        }

        @Test
        fun `should calculate TDEE with very hard activity`() {
            // TDEE = 1780 * 1.9 = 3382
            val result = service.calculateTdee(1780, ActivityLevel.VERY_HARD)
            assertEquals(3382, result)
        }

        @Test
        fun `should calculate TDEE with athlete activity`() {
            // TDEE = 1780 * 2.4 = 4272
            val result = service.calculateTdee(1780, ActivityLevel.ATHLETE)
            assertEquals(4272, result)
        }
    }

    @Nested
    @DisplayName("Fitness Goal Adjustment")
    inner class FitnessGoalAdjustment {

        @Test
        fun `should return 0 adjustment for maintain goal`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.MAINTAIN, null)
            assertEquals(0, result)
        }

        @Test
        fun `should return negative adjustment for lose goal - slow`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.LOSE, FitnessGoalIntensity.SLOW)
            assertEquals(-250, result)
        }

        @Test
        fun `should return negative adjustment for lose goal - normal`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.LOSE, FitnessGoalIntensity.NORMAL)
            assertEquals(-500, result)
        }

        @Test
        fun `should return negative adjustment for lose goal - hard`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.LOSE, FitnessGoalIntensity.HARD)
            assertEquals(-750, result)
        }

        @Test
        fun `should return negative adjustment for lose goal - extreme`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.LOSE, FitnessGoalIntensity.EXTREME)
            assertEquals(-1000, result)
        }

        @Test
        fun `should return positive adjustment for gain goal - slow`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.GAIN, FitnessGoalIntensity.SLOW)
            assertEquals(250, result)
        }

        @Test
        fun `should return positive adjustment for gain goal - normal`() {
            val result = service.getFitnessGoalAdjustment(FitnessGoalType.GAIN, FitnessGoalIntensity.NORMAL)
            assertEquals(500, result)
        }
    }

    @Nested
    @DisplayName("Daily Calorie Goal Calculation")
    inner class DailyCalorieGoalCalculation {

        @Test
        fun `should calculate calorie goal for maintain`() {
            val result = service.calculateDailyCalorieGoal(2500, FitnessGoalType.MAINTAIN, null)
            assertEquals(2500, result)
        }

        @Test
        fun `should calculate calorie goal for lose - normal`() {
            // 2500 - 500 = 2000
            val result = service.calculateDailyCalorieGoal(2500, FitnessGoalType.LOSE, FitnessGoalIntensity.NORMAL)
            assertEquals(2000, result)
        }

        @Test
        fun `should calculate calorie goal for gain - normal`() {
            // 2500 + 500 = 3000
            val result = service.calculateDailyCalorieGoal(2500, FitnessGoalType.GAIN, FitnessGoalIntensity.NORMAL)
            assertEquals(3000, result)
        }

        @Test
        fun `should enforce minimum 1200 calorie floor`() {
            // TDEE 1500 - 1000 (extreme loss) = 500, should be capped to 1200
            val result = service.calculateDailyCalorieGoal(1500, FitnessGoalType.LOSE, FitnessGoalIntensity.EXTREME)
            assertEquals(1200, result)
        }
    }

    @Nested
    @DisplayName("Macro Goals Calculation")
    inner class MacroGoalsCalculation {

        @Test
        fun `should calculate macro goals correctly`() {
            // For 2000 calories:
            // Protein: 30% = 600 cal = 150g (600/4)
            // Carbs: 45% = 900 cal = 225g (900/4)
            // Fat: 25% = 500 cal = 55g (500/9 = 55.55 -> 55)
            val result = service.calculateMacroGoals(2000)

            assertEquals(150, result.proteinGrams)
            assertEquals(600, result.proteinCalories)
            assertEquals(225, result.carbsGrams)
            assertEquals(900, result.carbsCalories)
            assertEquals(55, result.fatGrams)
            assertEquals(500, result.fatCalories)
        }

        @Test
        fun `should calculate macro goals for 2500 calories`() {
            // For 2500 calories:
            // Protein: 30% = 750 cal = 187g (750/4 = 187.5 -> 187)
            // Carbs: 45% = 1125 cal = 281g (1125/4 = 281.25 -> 281)
            // Fat: 25% = 625 cal = 69g (625/9 = 69.44 -> 69)
            val result = service.calculateMacroGoals(2500)

            assertEquals(187, result.proteinGrams)
            assertEquals(750, result.proteinCalories)
            assertEquals(281, result.carbsGrams)
            assertEquals(1125, result.carbsCalories)
            assertEquals(69, result.fatGrams)
            assertEquals(625, result.fatCalories)
        }
    }

    @Nested
    @DisplayName("Complete Daily Goals Calculation")
    inner class CompleteDailyGoalsCalculation {

        @Test
        fun `should calculate complete daily goals`() {
            val result = service.calculateDailyGoals(
                weightKg = BigDecimal("80"),
                heightCm = BigDecimal("180"),
                age = 30,
                sex = Sex.MALE,
                activityLevel = ActivityLevel.MODERATE,
                goalType = FitnessGoalType.LOSE,
                intensity = FitnessGoalIntensity.NORMAL
            )

            // BMR = 1780, TDEE = 1780 * 1.55 = 2759, Goal = 2759 - 500 = 2259
            assertEquals(1780, result.bmr)
            assertEquals(2759, result.tdee)
            assertEquals(2259, result.dailyCalorieGoal)
            assertEquals(-500, result.adjustment)

            // Macros for 2259 calories
            // Protein: 30% = 677.7 -> 677 cal = 169g
            // Carbs: 45% = 1016.55 -> 1016 cal = 254g
            // Fat: 25% = 564.75 -> 564 cal = 62g
            assertEquals(169, result.macros.proteinGrams)
            assertEquals(254, result.macros.carbsGrams)
            assertEquals(62, result.macros.fatGrams)
        }
    }

    @Nested
    @DisplayName("Nutrition Value Calculation")
    inner class NutritionValueCalculation {

        @Test
        fun `should calculate nutrition value from per100 and amount`() {
            // 165 cal per 100g, 150g consumed = 247.5 calories
            val result = service.calculateNutritionValue(BigDecimal("165"), BigDecimal("150"))
            assertEquals(BigDecimal("247.50"), result)
        }

        @Test
        fun `should handle small amounts`() {
            // 10 cal per 100g, 25g consumed = 2.5 calories
            val result = service.calculateNutritionValue(BigDecimal("10"), BigDecimal("25"))
            assertEquals(BigDecimal("2.50"), result)
        }
    }

    @Nested
    @DisplayName("Adherence Calculation")
    inner class AdherenceCalculation {

        @Test
        fun `should return ON_TARGET for consumption between 90-110 percent`() {
            // 2000 consumed / 2000 goal = 100%
            val result = service.calculateAdherence(2000, 2000)
            assertEquals(AdherenceStatus.ON_TARGET, result.status)
            assertEquals(100.0, result.percent)
        }

        @Test
        fun `should return ON_TARGET for 95 percent`() {
            // 1900 consumed / 2000 goal = 95%
            val result = service.calculateAdherence(1900, 2000)
            assertEquals(AdherenceStatus.ON_TARGET, result.status)
            assertEquals(95.0, result.percent)
        }

        @Test
        fun `should return ON_TARGET for 105 percent`() {
            // 2100 consumed / 2000 goal = 105%
            val result = service.calculateAdherence(2100, 2000)
            assertEquals(AdherenceStatus.ON_TARGET, result.status)
            assertEquals(105.0, result.percent)
        }

        @Test
        fun `should return UNDER for consumption below 90 percent`() {
            // 1700 consumed / 2000 goal = 85%
            val result = service.calculateAdherence(1700, 2000)
            assertEquals(AdherenceStatus.UNDER, result.status)
            assertEquals(85.0, result.percent)
        }

        @Test
        fun `should return OVER for consumption above 110 percent`() {
            // 2500 consumed / 2000 goal = 125%
            val result = service.calculateAdherence(2500, 2000)
            assertEquals(AdherenceStatus.OVER, result.status)
            assertEquals(125.0, result.percent)
        }

        @Test
        fun `should handle zero goal`() {
            val result = service.calculateAdherence(1000, 0)
            assertEquals(AdherenceStatus.ON_TARGET, result.status)
            assertEquals(0.0, result.percent)
        }
    }

    @Nested
    @DisplayName("Progress Calculation")
    inner class ProgressCalculation {

        @Test
        fun `should calculate progress percentage`() {
            // 1500 consumed / 2000 goal = 75%
            val result = service.calculateProgress(1500, 2000)
            assertEquals(75.0, result)
        }

        @Test
        fun `should allow progress over 100 percent`() {
            // 2500 consumed / 2000 goal = 125%
            val result = service.calculateProgress(2500, 2000)
            assertEquals(125.0, result)
        }

        @Test
        fun `should handle zero goal`() {
            val result = service.calculateProgress(1000, 0)
            assertEquals(0.0, result)
        }
    }
}
