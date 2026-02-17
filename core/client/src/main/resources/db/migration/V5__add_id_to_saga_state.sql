-- Add auto-incrementing id column to saga_state table and make saga_id a unique business key
-- This migration modifies the existing saga_state table structure
-- Note: This migration will work with existing data. MySQL's AUTO_INCREMENT will automatically
-- assign sequential values starting from 1 to all existing rows.

-- Step 1: Drop the old primary key on saga_id
ALTER TABLE saga_state DROP PRIMARY KEY;

-- Step 2: Add the new id column as auto-increment primary key
-- MySQL will automatically assign sequential IDs (1, 2, 3, ...) to existing rows
ALTER TABLE saga_state ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;

-- Step 3: Add unique constraint on saga_id (this automatically creates an index)
ALTER TABLE saga_state ADD CONSTRAINT uk_saga_state_saga_id UNIQUE (saga_id);
