-- Add auto-incrementing id column to saga_state table and make saga_id a unique business key
-- This migration modifies the existing saga_state table structure

-- Step 1: Drop the old primary key on saga_id
ALTER TABLE saga_state DROP PRIMARY KEY;

-- Step 2: Add the new id column as auto-increment primary key
ALTER TABLE saga_state ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;

-- Step 3: Add unique constraint on saga_id
ALTER TABLE saga_state ADD CONSTRAINT uk_saga_state_saga_id UNIQUE (saga_id);

-- Step 4: The unique constraint automatically creates an index, 
-- but we add an explicit one for documentation purposes
CREATE INDEX idx_saga_state_saga_id ON saga_state (saga_id);
