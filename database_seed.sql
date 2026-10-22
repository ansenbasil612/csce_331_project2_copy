\set ON_ERROR_STOP on
BEGIN;
-- BUBBLE TEA VENDOR DATABASE - SEED DATA
-- CSCE 331 Project 2

\copy employees (employee_id, name, position, hourly_rate) FROM 'data/employees.csv' WITH (FORMAT csv, HEADER true)
\copy menu_items (menu_item_id, name, base_price, description, category) FROM 'data/menu_items.csv' WITH (FORMAT csv, HEADER true)
\copy toppings (topping_id, name, cost) FROM 'data/toppings.csv' WITH (FORMAT csv, HEADER true)
\copy ingredients (ingredient_id, name, cost, unit_of_measure) FROM 'data/ingredients.csv' WITH (FORMAT csv, HEADER true)
\copy supplies (supply_id, name, cost, category) FROM 'data/supplies.csv' WITH (FORMAT csv, HEADER true)

\copy menu_inventory (inventory_id, menu_item_id, ingredient_id, quantity_per_serving) FROM 'data/menu_inventory.csv' WITH (FORMAT csv, HEADER true)

-- The existing CSVs contain one drink per order.
CREATE TEMP TABLE old_orders (
    order_id INT, order_date TIMESTAMP, menu_item_id INT,
    order_total DECIMAL(8,2), employee_id INT
) ON COMMIT DROP;
CREATE TEMP TABLE old_toppings (order_id INT, topping_id INT) ON COMMIT DROP;
\copy old_orders FROM 'data/orders.csv' WITH (FORMAT csv, HEADER true)
\copy old_toppings FROM 'data/order_toppings.csv' WITH (FORMAT csv, HEADER true)

INSERT INTO orders (order_id, order_date, order_total, employee_id)
SELECT order_id, order_date, order_total, employee_id FROM old_orders;
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, item_total)
SELECT order_id, order_id, menu_item_id, 1, order_total FROM old_orders;
INSERT INTO order_toppings (order_item_id, topping_id)
SELECT order_id, topping_id FROM old_toppings;

SELECT setval(pg_get_serial_sequence('orders', 'order_id'),
    COALESCE(MAX(order_id), 1), MAX(order_id) IS NOT NULL) FROM orders;
SELECT setval(pg_get_serial_sequence('order_items', 'order_item_id'),
    COALESCE(MAX(order_item_id), 1), MAX(order_item_id) IS NOT NULL) FROM order_items;
\i database_serving_update.sql
\i database_restock_update.sql
\i database_stock_seed.sql
\i database_payment_update.sql
\i database_demo_payment_seed.sql
COMMIT;


SELECT 'employees' AS table_name, COUNT(*) AS row_count FROM employees
UNION ALL SELECT 'menu_items', COUNT(*) FROM menu_items
UNION ALL SELECT 'toppings', COUNT(*) FROM toppings
UNION ALL SELECT 'ingredients', COUNT(*) FROM ingredients
UNION ALL SELECT 'supplies', COUNT(*) FROM supplies
UNION ALL SELECT 'menu_inventory', COUNT(*) FROM menu_inventory
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL SELECT 'order_toppings', COUNT(*) FROM order_toppings;

SELECT
    MIN(order_date) AS first_order,
    MAX(order_date) AS last_order,
    SUM(order_total) AS total_sales
FROM orders;
