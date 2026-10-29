-- minimum stock to keep on hand; the Restock Report lists anything below it
-- uses the same units as quantity_on_hand (ingredient unit, topping servings, supply pieces)
ALTER TABLE ingredients ADD COLUMN IF NOT EXISTS min_quantity DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (min_quantity >= 0);
ALTER TABLE toppings ADD COLUMN IF NOT EXISTS min_quantity INTEGER NOT NULL DEFAULT 0 CHECK (min_quantity >= 0);
ALTER TABLE supplies ADD COLUMN IF NOT EXISTS min_quantity INTEGER NOT NULL DEFAULT 0 CHECK (min_quantity >= 0);

-- starting minimums, about a tenth of the seeded stock; only fills in items with no minimum set
UPDATE ingredients SET min_quantity = 200 WHERE min_quantity = 0;
UPDATE toppings SET min_quantity = 100 WHERE min_quantity = 0;
UPDATE supplies SET min_quantity = 200 WHERE min_quantity = 0;
