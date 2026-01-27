package com.fittrack.model

/**
 * Sex type for profile metrics - maps to PostgreSQL sex_type enum
 */
enum class Sex {
    MALE,
    FEMALE
}

/**
 * Activity level with TDEE multipliers - maps to PostgreSQL activity_level enum
 */
enum class ActivityLevel(val multiplier: Double, val description: String) {
    SEDENTARY(1.2, "Little or no exercise"),
    LIGHT(1.375, "Light exercise 1-2 days/week"),
    MODERATE(1.55, "Moderate exercise 3-5 days/week"),
    HARD(1.725, "Hard exercise 6-7 days/week"),
    VERY_HARD(1.9, "Very hard exercise or physical job"),
    ATHLETE(2.4, "Professional athlete")
}

/**
 * Fitness goal type - maps to PostgreSQL fitness_goal_type enum
 */
enum class FitnessGoalType {
    LOSE,
    MAINTAIN,
    GAIN
}

/**
 * Fitness goal intensity - maps to PostgreSQL fitness_goal_intensity enum
 */
enum class FitnessGoalIntensity(val adjustment: Int, val description: String) {
    SLOW(250, "~0.25 kg/week"),
    NORMAL(500, "~0.5 kg/week"),
    HARD(750, "~0.75 kg/week"),
    EXTREME(1000, "~1 kg/week")
}
