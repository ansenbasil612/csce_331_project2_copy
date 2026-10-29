-- add stock amounts without clearing existing data
ALTER TABLE ingredients
ADD COLUMN IF NOT EXISTS quantity_on_hand DECIMAL(10,2) NOT NULL DEFAULT 0
CHECK (quantity_on_hand >= 0);
