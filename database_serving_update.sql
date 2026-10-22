-- stock counts toppings in servings and supplies in individual pieces
ALTER TABLE toppings ADD COLUMN IF NOT EXISTS quantity_on_hand INTEGER NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0);
ALTER TABLE supplies ADD COLUMN IF NOT EXISTS quantity_on_hand INTEGER NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0);
ALTER TABLE supplies ADD COLUMN IF NOT EXISTS usage_key VARCHAR(20) UNIQUE;

UPDATE supplies SET usage_key = 'medium' WHERE name = '16oz Cup' AND usage_key IS NULL;
UPDATE supplies SET usage_key = 'large' WHERE name = '24oz Cup' AND usage_key IS NULL;
INSERT INTO supplies (supply_id, name, cost, category, usage_key)
SELECT COALESCE(MAX(supply_id), 0) + 1, 'Cup Seal', 0, 'seal', 'seal' FROM supplies
HAVING NOT EXISTS (SELECT 1 FROM supplies WHERE usage_key = 'seal' OR name = 'Cup Seal');
UPDATE supplies SET usage_key = 'seal' WHERE name = 'Cup Seal' AND usage_key IS NULL;

CREATE TABLE IF NOT EXISTS drink_sizes (
    size VARCHAR(10) PRIMARY KEY CHECK (size IN ('medium', 'large')),
    surcharge DECIMAL(6,2) NOT NULL CHECK (surcharge >= 0)
);
INSERT INTO drink_sizes VALUES ('medium', 0), ('large', 0.75) ON CONFLICT (size) DO NOTHING;
