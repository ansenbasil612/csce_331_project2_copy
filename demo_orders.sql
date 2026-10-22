-- run again after submitting an order in the gui
SELECT o.order_id, o.order_date, e.name AS employee, o.order_total,
       m.name AS drink, i.quantity, i.size, i.sugar_percent, i.ice_level,
       i.item_total, string_agg(t.name, ', ' ORDER BY t.name) AS toppings
FROM orders o
JOIN employees e ON e.employee_id = o.employee_id
JOIN order_items i ON i.order_id = o.order_id
JOIN menu_items m ON m.menu_item_id = i.menu_item_id
LEFT JOIN order_toppings ot ON ot.order_item_id = i.order_item_id
LEFT JOIN toppings t ON t.topping_id = ot.topping_id
WHERE o.order_id IN (SELECT order_id FROM orders ORDER BY order_id DESC LIMIT 10)
GROUP BY o.order_id, e.name, m.name, i.order_item_id
ORDER BY o.order_id DESC, i.order_item_id;
