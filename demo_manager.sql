-- run the matching query after each gui change
SELECT menu_item_id, name, base_price, category FROM menu_items ORDER BY menu_item_id;
SELECT ingredient_id, name, cost, unit_of_measure, quantity_on_hand
FROM ingredients ORDER BY ingredient_id;
SELECT employee_id, name, position, hourly_rate, hire_date FROM employees ORDER BY employee_id;

-- use the same dates as the reports tab
SELECT COUNT(*) AS orders, COALESCE(SUM(order_total), 0) AS sales,
       COALESCE(AVG(order_total), 0) AS average_order
FROM orders
WHERE order_date >= CURRENT_DATE AND order_date < CURRENT_DATE + INTERVAL '1 day'
;

SELECT m.name, SUM(i.item_total) AS revenue
FROM orders o
JOIN order_items i ON i.order_id = o.order_id
JOIN menu_items m ON m.menu_item_id = i.menu_item_id
WHERE o.order_date >= CURRENT_DATE AND o.order_date < CURRENT_DATE + INTERVAL '1 day'
GROUP BY m.name ORDER BY revenue DESC;

-- topping servings and supply pieces after checkout
SELECT topping_id, name, quantity_on_hand FROM toppings ORDER BY topping_id;
SELECT supply_id, name, usage_key, quantity_on_hand FROM supplies ORDER BY supply_id;
SELECT * FROM drink_sizes ORDER BY size;
