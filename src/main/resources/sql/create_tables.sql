-----------------------------------------------------
---------------------- PROFILE ----------------------
-----------------------------------------------------

CREATE TYPE sex AS ENUM ('male', 'female');

CREATE TABLE activity_level (
	id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	description VARCHAR(16) NOT NULL,
	multiplier NUMERIC(4, 3) NOT NULL
);

CREATE TABLE fitness_goal (
	id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	description VARCHAR(36) NOT NULL,
	daily_kcal_modifier SMALLINT NOT NULL,
	expected_weekly_weight_difference SMALLINT NOT NULL
);

COMMENT ON COLUMN fitness_goal.daily_kcal_modifier IS 'Daily kcal modifier to BMR to reach the fitness goal (-250kcal, +250kcal, -500kcal, +500kcal...)';
COMMENT ON COLUMN fitness_goal.expected_weekly_weight_difference IS 'Expected weekly weight difference in grams to reach fitness goal (-250g, +250g, -500g, +500g...)';

CREATE TABLE profile (
	id UUID PRIMARY KEY DEFAULT uuidv7(),
	email VARCHAR(64)
	password TEXT NOT NULL,
	name VARCHAR(32) NOT NULL,
	date_of_birth DATE NOT NULL,
	sex sex NOT NULL,
	height SMALLINT NOT NULL,
	country_id SMAILLINT NOT NULL,
	default_activity_level_id SMALLINT NOT NULL,
	fitness_goal_id SMALLINT NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMPTZ
);

COMMENT ON COLUMN profile.height IS 'User height in centimeters (165cm, 184cm, 215cm...)';

------------------------------------------------------
------------------------ FOOD ------------------------
------------------------------------------------------

CREATE TABLE country (
	id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	iso_code VARCHAR(3) NOT NULL
);

COMMENT ON COLUMN country.iso_code IS 'The country ISO 3166 Alpha-3 code';

CREATE TABLE unit_type (
	id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	name VARCHAR(16) NOT NULL,
	abbreviation VARCHAR(3) NOT NULL
);

CREATE TABLE standard_portion (
	id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	unit_type_id SMALLINT NOT NULL,
	size SMALLINT NOT NULL,
	CONSTRAINT fk__standard_portion__unit_type FOREIGN KEY (unit_type_id) REFERENCES unit_type(id)
);

CREATE INDEX idx__standard_portion__unit_type_id ON standard_portion(unit_type_id);

COMMENT ON COLUMN standard_portion.size IS 'The standard portion size in the unit type specified';

CREATE TABLE portion (
	id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	description VARCHAR(32) NOT NULL,
	unit_type_id SMALLINT NOT NULL,
	size SMALLINT NOT NULL,
	CONSTRAINT fk__portion__unit_type FOREIGN KEY (unit_type_id) REFERENCES unit_type(id)
);

CREATE INDEX idx__portion__unit_type_id ON portion(unit_type_id);

CREATE TABLE category (
	id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	name VARCHAR(64) NOT NULL
);

CREATE TABLE brand (
	id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	name VARCHAR(32) NOT NULL,
	description TEXT,
	country_id SMALLINT NOT NULL,
	CONSTRAINT fk__brand__country FOREIGN KEY (country_id) REFERENCES country(id)
);

CREATE INDEX idx__brand__country_id ON brand(country_id);

CREATE TABLE food (
	id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	name VARCHAR(64) NOT NULL,
	category_id INTEGER NOT NULL,
	brand_id INTEGER NOT NULL,
	CONSTRAINT fk__food__category FOREIGN KEY (category_id) REFERENCES category(id),
	CONSTRAINT fk__food__brand FOREIGN KEY (brand_id) REFERENCES brand(id)
);

CREATE INDEX idx__food__category_id ON food(category_id);
CREATE INDEX idx__food__brand_id ON food(brand_id);

CREATE TABLE food_portions (
	food_id INTEGER,
	portion_id INTEGER,
	PRIMARY KEY (food_id, portion_id),
	CONSTRAINT fk__food_portions__food FOREIGN KEY (food_id) REFERENCES food(id),
	CONSTRAINT fk__food_portions__portion FOREIGN KEY (portion_id) REFERENCES portion(id)
);


CREATE TABLE nutritional_values (
	id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	food_id INTEGER NOT NULL,
	standard_portion_id SMALLINT NOT NULL,
	kcal NUMERIC(6, 2) NOT NULL,
	fat NUMERIC(6, 2),
	carbs NUMERIC(6, 2),
	protein NUMERIC(6, 2),
	fiber NUMERIC(6, 2),
	saturated_fat NUMERIC(6, 2),
	sugar NUMERIC(6, 2),
	salt NUMERIC(6, 2),
	CONSTRAINT fk__nutritional_values__food FOREIGN KEY (food_id) REFERENCES food(id),
	CONSTRAINT fk__nutritional_values__standard_portion FOREIGN KEY (standard_portion_id) REFERENCES standard_portion(id)
);

CREATE INDEX idx__nutritional_values__standard_portion_id ON nutritional_values(standard_portion_id);
CREATE INDEX idx__nutritional_values__food_id ON nutritional_values(food_id);