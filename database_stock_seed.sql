-- starting stock so every drink, topping, and size can be ordered
-- raises low counts and never lowers them, so it is safe to run again to restock
-- ingredients are in their unit of measure (oz); 2000 oz covers ~700+ drinks of the largest recipe
UPDATE ingredients SET quantity_on_hand = GREATEST(quantity_on_hand, 2000);
-- toppings are counted in servings, supplies in pieces (cups, seals, straws, ...)
UPDATE toppings SET quantity_on_hand = GREATEST(quantity_on_hand, 1000);
UPDATE supplies SET quantity_on_hand = GREATEST(quantity_on_hand, 1000);
