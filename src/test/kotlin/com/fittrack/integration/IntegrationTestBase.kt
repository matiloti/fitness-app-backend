package com.fittrack.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

/**
 * Base class for integration tests.
 * Uses the test profile which configures Testcontainers via JDBC URL.
 * Tests will be skipped if DOCKER_HOST is not available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanupTables() {
        // Clean tables in reverse dependency order - wrapped in try-catch for initialization failures
        try {
            // Workout-related tables
            jdbcTemplate.execute("DELETE FROM workouts")
            // Meal-related tables
            jdbcTemplate.execute("DELETE FROM meal_items")
            jdbcTemplate.execute("DELETE FROM meals")
            jdbcTemplate.execute("DELETE FROM days")
            jdbcTemplate.execute("DELETE FROM progress_photos")
            jdbcTemplate.execute("DELETE FROM body_metrics")
            // Recipe-related tables
            jdbcTemplate.execute("DELETE FROM recipe_images")
            jdbcTemplate.execute("DELETE FROM recipe_steps")
            jdbcTemplate.execute("DELETE FROM recipe_ingredients")
            jdbcTemplate.execute("DELETE FROM recipes")
            // Food-related tables
            jdbcTemplate.execute("DELETE FROM recent_foods")
            jdbcTemplate.execute("DELETE FROM food_portions")
            jdbcTemplate.execute("DELETE FROM foods")
            jdbcTemplate.execute("DELETE FROM brands")
            // Auth-related tables
            jdbcTemplate.execute("DELETE FROM password_reset_tokens")
            jdbcTemplate.execute("DELETE FROM refresh_tokens")
            jdbcTemplate.execute("DELETE FROM profiles")
        } catch (e: Exception) {
            // Log but don't fail - table might not exist yet during container startup
        }
    }
}
