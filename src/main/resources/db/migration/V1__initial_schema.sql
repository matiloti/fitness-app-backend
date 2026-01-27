-- ============================================================================
-- FitTrack Pro - Complete Database Schema
-- ============================================================================
-- Version: 1.0.0
-- Last Updated: 2026-01-27
-- Database: PostgreSQL 15+
--
-- This schema is designed to be executable - copy directly into a Flyway migration.
-- All tables include proper indexes, constraints, and cascade rules.
-- ============================================================================

-- ============================================================================
-- EXTENSIONS
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For trigram similarity search

-- ============================================================================
-- CUSTOM TYPES (ENUMS)
-- ============================================================================

-- Sex type for profile metrics
CREATE TYPE sex_type AS ENUM ('MALE', 'FEMALE');

-- Activity level with TDEE multipliers
CREATE TYPE activity_level AS ENUM (
    'SEDENTARY',       -- 1.2
    'LIGHT',           -- 1.375
    'MODERATE',        -- 1.55
    'HARD',            -- 1.725
    'VERY_HARD',       -- 1.9
    'ATHLETE'          -- 2.4
);

-- Fitness goal type
CREATE TYPE fitness_goal_type AS ENUM ('LOSE', 'MAINTAIN', 'GAIN');

-- Fitness goal intensity (caloric adjustment)
CREATE TYPE fitness_goal_intensity AS ENUM (
    'SLOW',     -- 250 kcal/day
    'NORMAL',   -- 500 kcal/day
    'HARD',     -- 750 kcal/day
    'EXTREME'   -- 1000 kcal/day
);

-- Metric type for foods (grams vs milliliters)
CREATE TYPE metric_type AS ENUM ('GRAMS', 'MILLILITERS');

-- Meal type
CREATE TYPE meal_type AS ENUM ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK');

-- Workout type
CREATE TYPE workout_type AS ENUM (
    'STRENGTH',
    'CARDIO_RUNNING',
    'CARDIO_CYCLING',
    'CARDIO_SWIMMING',
    'HIIT',
    'YOGA',
    'PILATES',
    'SPORTS',
    'WALKING',
    'OTHER'
);

-- Progress photo position
CREATE TYPE photo_position AS ENUM ('FRONT', 'BACK', 'LEFT', 'RIGHT');

-- ============================================================================
-- LOOKUP TABLES
-- ============================================================================

-- Countries (seeded with ISO 3166-1 data)
CREATE TABLE countries (
    id SMALLINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code CHAR(2) NOT NULL UNIQUE,              -- ISO 3166-1 alpha-2
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_countries_name ON countries(name);
CREATE INDEX idx_countries_code ON countries(code);

-- Food categories (system-defined, can be extended)
CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    icon VARCHAR(50),                          -- Icon identifier/name
    is_system BOOLEAN NOT NULL DEFAULT FALSE,  -- System categories cannot be deleted
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_name ON categories(name);

-- ============================================================================
-- USER & AUTHENTICATION
-- ============================================================================

-- User profiles
CREATE TABLE profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,       -- BCrypt hash
    name VARCHAR(100) NOT NULL,
    photo_url VARCHAR(500),                    -- Profile photo URL/path
    country_id SMALLINT REFERENCES countries(id),

    -- Profile metrics
    date_of_birth DATE,
    sex sex_type,
    height_cm DECIMAL(5,2),                    -- Height in centimeters

    -- Default activity and goal
    default_activity_level activity_level NOT NULL DEFAULT 'MODERATE',
    fitness_goal_type fitness_goal_type NOT NULL DEFAULT 'MAINTAIN',
    fitness_goal_intensity fitness_goal_intensity,  -- NULL for MAINTAIN

    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_height_positive CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT chk_dob_past CHECK (date_of_birth IS NULL OR date_of_birth < CURRENT_DATE),
    CONSTRAINT chk_goal_intensity CHECK (
        (fitness_goal_type = 'MAINTAIN' AND fitness_goal_intensity IS NULL) OR
        (fitness_goal_type IN ('LOSE', 'GAIN') AND fitness_goal_intensity IS NOT NULL)
    )
);

CREATE INDEX idx_profiles_email ON profiles(email);
CREATE INDEX idx_profiles_country ON profiles(country_id);
CREATE INDEX idx_profiles_created_at ON profiles(created_at);

-- Refresh tokens for JWT authentication
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,   -- SHA-256 hash of the token
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ                     -- NULL if not revoked
);

CREATE INDEX idx_refresh_tokens_profile ON refresh_tokens(profile_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_profile ON password_reset_tokens(profile_id);
CREATE INDEX idx_password_reset_hash ON password_reset_tokens(token_hash);

-- ============================================================================
-- BRANDS & FOODS
-- ============================================================================

-- Brands for food products
CREATE TABLE brands (
    id SERIAL PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    photo_url VARCHAR(500),
    country_id SMALLINT REFERENCES countries(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_brand_name_per_user UNIQUE (profile_id, name)
);

CREATE INDEX idx_brands_profile ON brands(profile_id);
CREATE INDEX idx_brands_name ON brands(name);
CREATE INDEX idx_brands_name_trgm ON brands USING gin(name gin_trgm_ops);

-- Foods with nutritional information
CREATE TABLE foods (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    category_id INT REFERENCES categories(id) ON DELETE SET NULL,
    brand_id INT REFERENCES brands(id) ON DELETE SET NULL,
    metric_type metric_type NOT NULL DEFAULT 'GRAMS',

    -- Nutritional decomposition per 100g or 100ml
    calories_per_100 DECIMAL(7,2) NOT NULL,        -- kcal
    fat_per_100 DECIMAL(6,2) NOT NULL,             -- grams
    carbs_per_100 DECIMAL(6,2) NOT NULL,           -- grams
    protein_per_100 DECIMAL(6,2) NOT NULL,         -- grams
    salt_per_100 DECIMAL(6,3),                     -- grams (optional)
    sugar_per_100 DECIMAL(6,2),                    -- grams (optional)
    fiber_per_100 DECIMAL(6,2),                    -- grams (optional)
    saturated_fat_per_100 DECIMAL(6,2),            -- grams (optional)

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_calories_positive CHECK (calories_per_100 >= 0),
    CONSTRAINT chk_fat_positive CHECK (fat_per_100 >= 0),
    CONSTRAINT chk_carbs_positive CHECK (carbs_per_100 >= 0),
    CONSTRAINT chk_protein_positive CHECK (protein_per_100 >= 0),
    CONSTRAINT chk_salt_positive CHECK (salt_per_100 IS NULL OR salt_per_100 >= 0),
    CONSTRAINT chk_sugar_positive CHECK (sugar_per_100 IS NULL OR sugar_per_100 >= 0),
    CONSTRAINT chk_fiber_positive CHECK (fiber_per_100 IS NULL OR fiber_per_100 >= 0),
    CONSTRAINT chk_saturated_fat_positive CHECK (saturated_fat_per_100 IS NULL OR saturated_fat_per_100 >= 0)
);

CREATE INDEX idx_foods_profile ON foods(profile_id);
CREATE INDEX idx_foods_name ON foods(name);
CREATE INDEX idx_foods_name_trgm ON foods USING gin(name gin_trgm_ops);
CREATE INDEX idx_foods_category ON foods(category_id);
CREATE INDEX idx_foods_brand ON foods(brand_id);
CREATE INDEX idx_foods_created_at ON foods(created_at DESC);

-- Custom portions for foods
CREATE TABLE food_portions (
    id SERIAL PRIMARY KEY,
    food_id UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,                 -- e.g., "1 cup", "1 tablespoon"
    amount_grams DECIMAL(7,2) NOT NULL,        -- Amount in grams (or ml)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_amount_positive CHECK (amount_grams > 0),
    CONSTRAINT uq_portion_name_per_food UNIQUE (food_id, name)
);

CREATE INDEX idx_food_portions_food ON food_portions(food_id);

-- ============================================================================
-- RECIPES
-- ============================================================================

-- Recipes
CREATE TABLE recipes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    total_servings INT NOT NULL DEFAULT 1,

    -- Cached nutritional values per serving (calculated from ingredients)
    calories_per_serving DECIMAL(7,2),
    fat_per_serving DECIMAL(6,2),
    carbs_per_serving DECIMAL(6,2),
    protein_per_serving DECIMAL(6,2),
    salt_per_serving DECIMAL(6,3),
    sugar_per_serving DECIMAL(6,2),
    fiber_per_serving DECIMAL(6,2),
    saturated_fat_per_serving DECIMAL(6,2),

    -- Cached total duration (calculated from steps)
    total_duration_minutes INT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_servings_positive CHECK (total_servings > 0)
);

CREATE INDEX idx_recipes_profile ON recipes(profile_id);
CREATE INDEX idx_recipes_name ON recipes(name);
CREATE INDEX idx_recipes_name_trgm ON recipes USING gin(name gin_trgm_ops);
CREATE INDEX idx_recipes_created_at ON recipes(created_at DESC);

-- Recipe ingredients (foods with amounts)
CREATE TABLE recipe_ingredients (
    id SERIAL PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    food_id UUID NOT NULL REFERENCES foods(id) ON DELETE RESTRICT,
    portion_id INT REFERENCES food_portions(id) ON DELETE SET NULL,
    quantity DECIMAL(7,2) NOT NULL,            -- Quantity of portion (or raw amount if no portion)
    amount_grams DECIMAL(7,2) NOT NULL,        -- Calculated total grams
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_amount_grams_positive CHECK (amount_grams > 0)
);

CREATE INDEX idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id);
CREATE INDEX idx_recipe_ingredients_food ON recipe_ingredients(food_id);

-- Recipe steps
CREATE TABLE recipe_steps (
    id SERIAL PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    description TEXT NOT NULL,
    duration_minutes INT,                      -- Optional duration for this step
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_step_number_positive CHECK (step_number > 0),
    CONSTRAINT chk_duration_positive CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT uq_step_number_per_recipe UNIQUE (recipe_id, step_number)
);

CREATE INDEX idx_recipe_steps_recipe ON recipe_steps(recipe_id);

-- Recipe images (future feature, but table structure ready)
CREATE TABLE recipe_images (
    id SERIAL PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_images_recipe ON recipe_images(recipe_id);

-- ============================================================================
-- DAYS & MEALS
-- ============================================================================

-- Days (created lazily when user interacts with a day)
CREATE TABLE days (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    activity_level_override activity_level,    -- NULL means use profile default
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_day_per_profile UNIQUE (profile_id, date)
);

CREATE INDEX idx_days_profile ON days(profile_id);
CREATE INDEX idx_days_date ON days(date);
CREATE INDEX idx_days_profile_date ON days(profile_id, date DESC);

-- Meals
CREATE TABLE meals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    day_id UUID NOT NULL REFERENCES days(id) ON DELETE CASCADE,
    meal_type meal_type NOT NULL,
    is_cheat_meal BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,

    -- Cached totals (calculated from items)
    total_calories DECIMAL(8,2) DEFAULT 0,
    total_fat DECIMAL(7,2) DEFAULT 0,
    total_carbs DECIMAL(7,2) DEFAULT 0,
    total_protein DECIMAL(7,2) DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_meals_day ON meals(day_id);
CREATE INDEX idx_meals_type ON meals(meal_type);

-- Meal items (can be food or recipe or quick entry)
CREATE TABLE meal_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,

    -- One of these will be set
    food_id UUID REFERENCES foods(id) ON DELETE SET NULL,
    recipe_id UUID REFERENCES recipes(id) ON DELETE SET NULL,

    -- For food items
    portion_id INT REFERENCES food_portions(id) ON DELETE SET NULL,
    quantity DECIMAL(7,2) NOT NULL DEFAULT 1,  -- Quantity of portion or servings
    amount_grams DECIMAL(7,2),                 -- Calculated total grams (for food)

    -- For quick entries (arbitrary calories)
    quick_entry_name VARCHAR(200),
    is_quick_entry BOOLEAN NOT NULL DEFAULT FALSE,

    -- Calculated/entered nutritional values
    calories DECIMAL(7,2) NOT NULL,
    fat DECIMAL(6,2),
    carbs DECIMAL(6,2),
    protein DECIMAL(6,2),
    salt DECIMAL(6,3),
    sugar DECIMAL(6,2),
    fiber DECIMAL(6,2),
    saturated_fat DECIMAL(6,2),

    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_calories_positive CHECK (calories >= 0),
    CONSTRAINT chk_meal_item_type CHECK (
        (is_quick_entry = TRUE AND quick_entry_name IS NOT NULL AND food_id IS NULL AND recipe_id IS NULL) OR
        (is_quick_entry = FALSE AND food_id IS NOT NULL AND recipe_id IS NULL) OR
        (is_quick_entry = FALSE AND recipe_id IS NOT NULL AND food_id IS NULL)
    )
);

CREATE INDEX idx_meal_items_meal ON meal_items(meal_id);
CREATE INDEX idx_meal_items_food ON meal_items(food_id);
CREATE INDEX idx_meal_items_recipe ON meal_items(recipe_id);

-- Recent foods for quick add (tracks user's food logging history)
CREATE TABLE recent_foods (
    id SERIAL PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    food_id UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    use_count INT NOT NULL DEFAULT 1,

    CONSTRAINT uq_recent_food UNIQUE (profile_id, food_id)
);

CREATE INDEX idx_recent_foods_profile ON recent_foods(profile_id);
CREATE INDEX idx_recent_foods_last_used ON recent_foods(profile_id, last_used_at DESC);

-- ============================================================================
-- BODY METRICS
-- ============================================================================

-- Body metrics entries
CREATE TABLE body_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    date DATE NOT NULL,

    -- Measurements
    weight_kg DECIMAL(5,2),                    -- Weight in kg
    body_fat_percentage DECIMAL(4,2),          -- Body fat as percentage (0-100)
    body_fat_kg DECIMAL(5,2),                  -- Body fat in kg (calculated or entered)
    muscle_mass_percentage DECIMAL(4,2),       -- Muscle as percentage
    muscle_mass_kg DECIMAL(5,2),               -- Muscle in kg (calculated or entered)

    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_body_metrics_per_day UNIQUE (profile_id, date),
    CONSTRAINT chk_weight_positive CHECK (weight_kg IS NULL OR weight_kg > 0),
    CONSTRAINT chk_body_fat_range CHECK (body_fat_percentage IS NULL OR (body_fat_percentage >= 0 AND body_fat_percentage <= 100)),
    CONSTRAINT chk_muscle_range CHECK (muscle_mass_percentage IS NULL OR (muscle_mass_percentage >= 0 AND muscle_mass_percentage <= 100))
);

CREATE INDEX idx_body_metrics_profile ON body_metrics(profile_id);
CREATE INDEX idx_body_metrics_date ON body_metrics(date);
CREATE INDEX idx_body_metrics_profile_date ON body_metrics(profile_id, date DESC);

-- Progress photos
CREATE TABLE progress_photos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    body_metrics_id UUID NOT NULL REFERENCES body_metrics(id) ON DELETE CASCADE,
    position photo_position NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_photo_position_per_entry UNIQUE (body_metrics_id, position)
);

CREATE INDEX idx_progress_photos_metrics ON progress_photos(body_metrics_id);

-- ============================================================================
-- WORKOUTS
-- ============================================================================

-- Workout types with MET values
CREATE TABLE workout_type_mets (
    workout_type workout_type PRIMARY KEY,
    met_value DECIMAL(4,2) NOT NULL,           -- MET value for calorie calculation
    description VARCHAR(100) NOT NULL
);

-- Seed MET values (Metabolic Equivalent of Task)
INSERT INTO workout_type_mets (workout_type, met_value, description) VALUES
    ('STRENGTH', 5.0, 'Strength Training'),
    ('CARDIO_RUNNING', 9.8, 'Running'),
    ('CARDIO_CYCLING', 7.5, 'Cycling'),
    ('CARDIO_SWIMMING', 8.0, 'Swimming'),
    ('HIIT', 8.0, 'High Intensity Interval Training'),
    ('YOGA', 2.5, 'Yoga'),
    ('PILATES', 3.0, 'Pilates'),
    ('SPORTS', 6.0, 'General Sports'),
    ('WALKING', 3.5, 'Walking'),
    ('OTHER', 5.0, 'Other Exercise');

-- Workouts
CREATE TABLE workouts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    workout_type workout_type NOT NULL,
    name VARCHAR(200),                         -- Optional custom name
    duration_minutes INT NOT NULL,

    -- Calories
    calories_burned_estimated DECIMAL(7,2),    -- Auto-calculated from MET
    calories_burned_actual DECIMAL(7,2),       -- User override

    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_calories_positive CHECK (
        (calories_burned_estimated IS NULL OR calories_burned_estimated >= 0) AND
        (calories_burned_actual IS NULL OR calories_burned_actual >= 0)
    )
);

CREATE INDEX idx_workouts_profile ON workouts(profile_id);
CREATE INDEX idx_workouts_date ON workouts(date);
CREATE INDEX idx_workouts_profile_date ON workouts(profile_id, date DESC);
CREATE INDEX idx_workouts_type ON workouts(workout_type);

-- ============================================================================
-- ACTIVITY LEVEL MULTIPLIERS (Reference Table)
-- ============================================================================

CREATE TABLE activity_level_multipliers (
    activity_level activity_level PRIMARY KEY,
    multiplier DECIMAL(4,3) NOT NULL,
    description VARCHAR(100) NOT NULL
);

INSERT INTO activity_level_multipliers (activity_level, multiplier, description) VALUES
    ('SEDENTARY', 1.200, 'Little or no exercise'),
    ('LIGHT', 1.375, 'Light exercise 1-2 days/week'),
    ('MODERATE', 1.550, 'Moderate exercise 3-5 days/week'),
    ('HARD', 1.725, 'Hard exercise 6-7 days/week'),
    ('VERY_HARD', 1.900, 'Very hard exercise or physical job'),
    ('ATHLETE', 2.400, 'Professional athlete');

-- ============================================================================
-- FITNESS GOAL ADJUSTMENTS (Reference Table)
-- ============================================================================

CREATE TABLE fitness_goal_adjustments (
    id SERIAL PRIMARY KEY,
    goal_type fitness_goal_type NOT NULL,
    intensity fitness_goal_intensity,
    calorie_adjustment INT NOT NULL,           -- Daily calorie adjustment (+/-)
    description VARCHAR(100) NOT NULL,
    CONSTRAINT uq_goal_intensity UNIQUE NULLS NOT DISTINCT (goal_type, intensity),
    CONSTRAINT chk_maintain_no_intensity CHECK (
        (goal_type = 'MAINTAIN' AND intensity IS NULL) OR
        (goal_type != 'MAINTAIN' AND intensity IS NOT NULL)
    )
);

INSERT INTO fitness_goal_adjustments (goal_type, intensity, calorie_adjustment, description) VALUES
    ('MAINTAIN', NULL, 0, 'Maintain current weight'),
    ('LOSE', 'SLOW', -250, 'Lose ~0.25 kg/week'),
    ('LOSE', 'NORMAL', -500, 'Lose ~0.5 kg/week'),
    ('LOSE', 'HARD', -750, 'Lose ~0.75 kg/week'),
    ('LOSE', 'EXTREME', -1000, 'Lose ~1 kg/week'),
    ('GAIN', 'SLOW', 250, 'Gain ~0.25 kg/week'),
    ('GAIN', 'NORMAL', 500, 'Gain ~0.5 kg/week'),
    ('GAIN', 'HARD', 750, 'Gain ~0.75 kg/week'),
    ('GAIN', 'EXTREME', 1000, 'Gain ~1 kg/week');

-- ============================================================================
-- SEED DATA - CATEGORIES
-- ============================================================================

INSERT INTO categories (name, icon, is_system) VALUES
    ('Fruits', 'apple', TRUE),
    ('Vegetables', 'carrot', TRUE),
    ('Grains & Cereals', 'wheat', TRUE),
    ('Protein', 'drumstick', TRUE),
    ('Dairy', 'milk', TRUE),
    ('Beverages', 'cup', TRUE),
    ('Snacks', 'cookie', TRUE),
    ('Condiments & Sauces', 'sauce', TRUE),
    ('Fats & Oils', 'oil', TRUE),
    ('Sweets & Desserts', 'cake', TRUE),
    ('Prepared Foods', 'plate', TRUE),
    ('Supplements', 'pill', TRUE);

-- ============================================================================
-- FUNCTIONS
-- ============================================================================

-- Function to calculate BMR using Mifflin-St Jeor equation
CREATE OR REPLACE FUNCTION calculate_bmr(
    p_weight_kg DECIMAL,
    p_height_cm DECIMAL,
    p_age_years INT,
    p_sex sex_type
) RETURNS DECIMAL AS $$
BEGIN
    IF p_sex = 'MALE' THEN
        -- Male: (10 x weight) + (6.25 x height) - (5 x age) + 5
        RETURN (10 * p_weight_kg) + (6.25 * p_height_cm) - (5 * p_age_years) + 5;
    ELSE
        -- Female: (10 x weight) + (6.25 x height) - (5 x age) - 161
        RETURN (10 * p_weight_kg) + (6.25 * p_height_cm) - (5 * p_age_years) - 161;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to calculate TDEE
CREATE OR REPLACE FUNCTION calculate_tdee(
    p_bmr DECIMAL,
    p_activity_level activity_level
) RETURNS DECIMAL AS $$
DECLARE
    v_multiplier DECIMAL;
BEGIN
    SELECT multiplier INTO v_multiplier
    FROM activity_level_multipliers
    WHERE activity_level = p_activity_level;

    RETURN p_bmr * v_multiplier;
END;
$$ LANGUAGE plpgsql STABLE;

-- Function to calculate daily calorie goal
CREATE OR REPLACE FUNCTION calculate_daily_calorie_goal(
    p_tdee DECIMAL,
    p_goal_type fitness_goal_type,
    p_goal_intensity fitness_goal_intensity
) RETURNS DECIMAL AS $$
DECLARE
    v_adjustment INT;
BEGIN
    SELECT calorie_adjustment INTO v_adjustment
    FROM fitness_goal_adjustments
    WHERE goal_type = p_goal_type
      AND (intensity = p_goal_intensity OR (intensity IS NULL AND p_goal_intensity IS NULL));

    RETURN GREATEST(p_tdee + COALESCE(v_adjustment, 0), 1200); -- Minimum 1200 kcal
END;
$$ LANGUAGE plpgsql STABLE;

-- Function to estimate workout calories
CREATE OR REPLACE FUNCTION estimate_workout_calories(
    p_workout_type workout_type,
    p_duration_minutes INT,
    p_weight_kg DECIMAL
) RETURNS DECIMAL AS $$
DECLARE
    v_met DECIMAL;
BEGIN
    SELECT met_value INTO v_met
    FROM workout_type_mets
    WHERE workout_type = p_workout_type;

    -- Calories = MET x weight(kg) x duration(hours)
    RETURN v_met * p_weight_kg * (p_duration_minutes / 60.0);
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at triggers
CREATE TRIGGER trg_profiles_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_brands_updated_at
    BEFORE UPDATE ON brands
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_foods_updated_at
    BEFORE UPDATE ON foods
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_recipes_updated_at
    BEFORE UPDATE ON recipes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_days_updated_at
    BEFORE UPDATE ON days
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_meals_updated_at
    BEFORE UPDATE ON meals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_meal_items_updated_at
    BEFORE UPDATE ON meal_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_body_metrics_updated_at
    BEFORE UPDATE ON body_metrics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_workouts_updated_at
    BEFORE UPDATE ON workouts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ============================================================================
-- COMMENTS (Documentation)
-- ============================================================================

COMMENT ON TABLE profiles IS 'User profiles with authentication and fitness settings';
COMMENT ON TABLE foods IS 'Food items with nutritional information per 100g/100ml';
COMMENT ON TABLE food_portions IS 'Custom portion sizes for foods';
COMMENT ON TABLE recipes IS 'User-created recipes with ingredients and steps';
COMMENT ON TABLE recipe_ingredients IS 'Ingredients in a recipe with amounts';
COMMENT ON TABLE recipe_steps IS 'Cooking steps for a recipe';
COMMENT ON TABLE days IS 'Tracking days with optional activity level override';
COMMENT ON TABLE meals IS 'Meals within a day (breakfast, lunch, dinner, snack)';
COMMENT ON TABLE meal_items IS 'Items in a meal (food, recipe, or quick entry)';
COMMENT ON TABLE body_metrics IS 'Daily body measurements (weight, body fat, muscle)';
COMMENT ON TABLE progress_photos IS 'Progress photos attached to body metrics';
COMMENT ON TABLE workouts IS 'Workout sessions with type, duration, and calories';

COMMENT ON FUNCTION calculate_bmr IS 'Calculate Basal Metabolic Rate using Mifflin-St Jeor equation';
COMMENT ON FUNCTION calculate_tdee IS 'Calculate Total Daily Energy Expenditure from BMR and activity level';
COMMENT ON FUNCTION calculate_daily_calorie_goal IS 'Calculate daily calorie goal from TDEE and fitness goal';
COMMENT ON FUNCTION estimate_workout_calories IS 'Estimate calories burned during workout using MET values';
