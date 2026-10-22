-- ============================================================================
-- SPECIAL QUERIES
-- ============================================================================

-- Special Query #1: "Weekly Sales History"
-- pseudocode: select count of orders grouped by week
-- about: given a specific week, how many orders were placed?
-- example: "week of 2025-09-29 has 3,250 orders"
SELECT
    date_trunc('week', order_date)::date AS week_start,
    COUNT(*) AS order_count
FROM orders
GROUP BY week_start
ORDER BY week_start;


-- Special Query #2: "Peak Sales Day"
-- pseudocode: select top 10 sums of order total grouped by day in
--             descending order by order total
-- about: given a specific day, what was the sum of the top 10 order totals?
-- example: "2025-11-10 has $16,000 of top sales"
SELECT
    order_date::date AS sales_day,
    SUM(order_total) AS total_sales
FROM orders
GROUP BY sales_day
ORDER BY total_sales DESC
LIMIT 10;


-- Special Query #3: "Realistic Sales History"
-- pseudocode: select count of orders, sum of order total grouped by hour
-- about: given a specific hour of the day, how many orders were placed and
--        what was the total sum of the orders?
-- example: "12pm has 12,323 orders totaling $78,000"
SELECT
    EXTRACT(HOUR FROM order_date) AS hour_of_day,
    COUNT(*) AS order_count,
    SUM(order_total) AS total_sales
FROM orders
GROUP BY hour_of_day
ORDER BY hour_of_day;



-- Special Query #4: "Menu Item Inventory"
-- pseudocode: select count of inventory items from inventory and menu
--             grouped by menu item
-- about: given a specific menu item, how many items from the inventory does
--        that menu item use?
-- example: "Taro Milk Tea uses 4 items"
SELECT
    m.name AS menu_item,
    COUNT(mi.ingredient_id) AS ingredient_count
FROM menu_items m
JOIN menu_inventory mi ON mi.menu_item_id = m.menu_item_id
GROUP BY m.name
ORDER BY ingredient_count DESC, m.name;


-- ============================================================================
-- QUERIES
-- ============================================================================

-- Query #1: "Order Count"
-- pseudocode: select count of all orders
-- about: how many orders are in the database?
-- example: "there are 157,661 orders"
SELECT COUNT(*) AS total_orders
FROM orders;


-- Query #2: "Total Sales"
-- pseudocode: select sum of order total from orders
-- about: how much money did the store make over the whole sales history?
-- example: "total sales are $1,000,005.09"
SELECT SUM(order_total) AS total_sales
FROM orders;


-- Query #3: "Revenue by Menu Item"
-- pseudocode: select sum of order total grouped by menu item, highest first
-- about: which drinks bring in the most money?
-- example: "Brown Sugar Boba Latte brought in $57,400.88"
SELECT m.name, SUM(oi.item_total) AS revenue
FROM order_items oi
JOIN menu_items m ON m.menu_item_id = oi.menu_item_id
GROUP BY m.name
ORDER BY revenue DESC;


-- Query #4: "Sales History Dates"
-- pseudocode: select earliest and latest order date from orders
-- about: what date range does the sales history cover?
-- example: "orders run from 2025-09-30 to 2026-09-29"
SELECT
    MIN(order_date) AS first_order,
    MAX(order_date) AS last_order
FROM orders;


-- Query #5: "Largest Order"
-- pseudocode: select the order with the highest order total
-- about: what was the most expensive single order?
-- example: "order 141330 was the largest at $9.17"
SELECT *
FROM orders
ORDER BY order_total DESC
LIMIT 1;


-- Query #6: "Menu Item Count"
-- pseudocode: select count of all menu items
-- about: how many drinks are on the menu?
-- example: "the menu has 20 items"
SELECT COUNT(*) AS menu_item_count
FROM menu_items;


-- Query #7: "Sales by Day of Week"
-- pseudocode: select count of orders, sum of order total grouped by day of
--             week
-- about: which days of the week are busiest?
-- example: "Monday has 26,797 orders totaling $170,648.14"
SELECT
    to_char(order_date, 'Day') AS day_of_week,
    COUNT(*) AS order_count,
    SUM(order_total) AS total_sales
FROM orders
GROUP BY day_of_week, EXTRACT(ISODOW FROM order_date)
ORDER BY EXTRACT(ISODOW FROM order_date);


-- Query #8: "Most Popular Drinks"
-- pseudocode: select count of orders grouped by menu item, highest first
-- about: which drinks are ordered the most?
-- example: "Rose Milk Tea was ordered 8,104 times"
SELECT m.name, SUM(oi.quantity) AS times_ordered
FROM order_items oi
JOIN menu_items m ON m.menu_item_id = oi.menu_item_id
GROUP BY m.name
ORDER BY times_ordered DESC;


-- Query #9: "Employee List"
-- pseudocode: select name, position, and hourly rate of every employee
-- about: who works at the store and what are they paid?
-- example: "Alice Chen is a Manager at $18.50/hour"
SELECT name, position, hourly_rate
FROM employees;


-- Query #10: "Orders per Employee"
-- pseudocode: select count of orders grouped by employee
-- about: how many orders did each employee handle?
-- example: "Bob Johnson handled 31,674 orders"
SELECT e.name, COUNT(*) AS orders_handled
FROM orders o
JOIN employees e ON e.employee_id = o.employee_id
GROUP BY e.name
ORDER BY orders_handled DESC;


-- Query #11: "Ingredient Usage"
-- pseudocode: select sum of quantity per serving from orders and menu
--             inventory grouped by ingredient, highest first
-- about: how much of each ingredient was used over the sales history?
-- example: "Milk was used 301,066.30 oz"
SELECT i.name, SUM(mi.quantity_per_serving * oi.quantity) AS total_used, i.unit_of_measure
FROM order_items oi
JOIN menu_inventory mi ON mi.menu_item_id = oi.menu_item_id
JOIN ingredients i ON i.ingredient_id = mi.ingredient_id
GROUP BY i.name, i.unit_of_measure
ORDER BY total_used DESC;


-- Query #12: "Most Popular Toppings"
-- pseudocode: select count of order toppings grouped by topping, highest first
-- about: which toppings are added to orders the most?
-- example: "Aloe Vera was added 24,775 times"
SELECT t.name, SUM(oi.quantity) AS times_added
FROM order_toppings ot
JOIN order_items oi ON oi.order_item_id = ot.order_item_id
JOIN toppings t ON t.topping_id = ot.topping_id
GROUP BY t.name
ORDER BY times_added DESC;


-- Query #13: "Ingredient Cost per Drink"
-- pseudocode: select sum of ingredient cost times quantity per serving
--             grouped by menu item, highest first
-- about: how much do the ingredients in one serving of each drink cost?
-- example: "Winter Melon Tea costs $4.65 in ingredients"
SELECT m.name, SUM(i.cost * mi.quantity_per_serving) AS ingredient_cost
FROM menu_items m
JOIN menu_inventory mi ON mi.menu_item_id = m.menu_item_id
JOIN ingredients i ON i.ingredient_id = mi.ingredient_id
GROUP BY m.name
ORDER BY ingredient_cost DESC;


-- Query #14: "Supplies by Category"
-- pseudocode: select count of supplies grouped by category
-- about: how many kinds of supplies are in each category?
-- example: "there are 2 kinds of cups"
SELECT category, COUNT(*) AS supply_count
FROM supplies
GROUP BY category;


-- Query #15: "Drink Recipe"
-- pseudocode: select ingredient name and quantity for one menu item
-- about: what goes into a specific drink?
-- example: "Classic Milk Tea (menu item 1) uses 2.1 oz of Milk"
SELECT i.name, mi.quantity_per_serving
FROM menu_inventory mi
JOIN ingredients i ON i.ingredient_id = mi.ingredient_id
WHERE mi.menu_item_id = 1;

-- defaults cover saved orders; override with psql -v start_date=YYYY-MM-DD -v end_date=YYYY-MM-DD
\if :{?start_date}
\else
SELECT COALESCE(MIN(order_date)::date, CURRENT_DATE)::text AS start_date FROM orders \gset
\endif
\if :{?end_date}
\else
SELECT COALESCE(MAX(order_date)::date, CURRENT_DATE)::text AS end_date FROM orders \gset
\endif

-- Sales Report: Sales by item within a date range
SELECT
    m.menu_item_id,
    m.name AS item_name,
    SUM(oi.quantity) AS quantity_sold,
    SUM(oi.item_total) AS total_sales
FROM orders o
JOIN order_items oi
    ON o.order_id = oi.order_id
JOIN menu_items m
    ON m.menu_item_id = oi.menu_item_id
WHERE o.order_date >= :'start_date'::date
  AND o.order_date < :'end_date'::date + INTERVAL '1 day'
GROUP BY m.menu_item_id, m.name
ORDER BY total_sales DESC;
