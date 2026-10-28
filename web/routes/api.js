const express = require('express');
const { pool } = require('../db');
const { placeOrder, httpError } = require('../orders');

const router = express.Router();

// ---------- menu (cashier, kiosk, menu board) ----------

router.get('/menu', async (req, res) => {
  const [items, toppings, sizes] = await Promise.all([
    pool.query(`SELECT menu_item_id AS id, name, base_price AS price, category, description
                FROM menu_items ORDER BY category, name`),
    pool.query('SELECT topping_id AS id, name, cost AS price FROM toppings ORDER BY name'),
    pool.query('SELECT size, surcharge FROM drink_sizes ORDER BY surcharge'),
  ]);
  res.json({ items: items.rows, toppings: toppings.rows, sizes: sizes.rows });
});

router.get('/employees', async (req, res) => {
  const { rows } = await pool.query(
    'SELECT employee_id AS id, name, position FROM employees ORDER BY name',
  );
  res.json(rows);
});

// ---------- orders ----------

router.post('/orders', async (req, res) => {
  const order = await placeOrder({ ...req.body, employeeId: Number(req.body.employeeId) });
  res.status(201).json(order);
});

// Kiosk orders have no cashier, so they are saved under KIOSK_EMPLOYEE_ID from .env.
router.post('/kiosk/orders', async (req, res) => {
  const employeeId = Number(process.env.KIOSK_EMPLOYEE_ID);
  if (!Number.isInteger(employeeId)) throw httpError(500, 'Set KIOSK_EMPLOYEE_ID in web/.env.');
  const paymentMethod = req.body.paymentMethod;
  if (paymentMethod === 'Cash') throw httpError(400, 'The kiosk takes Card or Gift Card.');
  const order = await placeOrder({ ...req.body, employeeId, paymentMethod });
  res.status(201).json(order);
});

router.get('/orders/:id/items', async (req, res) => {
  const { rows } = await pool.query(
    `SELECT m.name, oi.quantity, oi.size, oi.sugar_percent, oi.ice_level, oi.item_total,
            COALESCE(string_agg(t.name, ', ' ORDER BY t.name), '') AS toppings
     FROM order_items oi
     JOIN menu_items m USING (menu_item_id)
     LEFT JOIN order_toppings ot USING (order_item_id)
     LEFT JOIN toppings t USING (topping_id)
     WHERE oi.order_id = $1
     GROUP BY oi.order_item_id, m.name ORDER BY oi.order_item_id`,
    [Number(req.params.id)],
  );
  res.json(rows);
});

// ---------- manager (read-only for now) ----------

// Fixed list of views; the URL never goes into SQL directly.
const MANAGER_VIEWS = {
  menu: `SELECT menu_item_id AS "ID", name AS "Name", category AS "Category",
                base_price AS "Price", description AS "Description"
         FROM menu_items ORDER BY menu_item_id`,
  ingredients: `SELECT ingredient_id AS "ID", name AS "Name", quantity_on_hand AS "On hand",
                       min_quantity AS "Minimum", unit_of_measure AS "Unit", cost AS "Cost"
                FROM ingredients ORDER BY ingredient_id`,
  toppings: `SELECT topping_id AS "ID", name AS "Name", quantity_on_hand AS "On hand",
                    min_quantity AS "Minimum", cost AS "Price"
             FROM toppings ORDER BY topping_id`,
  supplies: `SELECT supply_id AS "ID", name AS "Name", category AS "Category",
                    quantity_on_hand AS "On hand", min_quantity AS "Minimum", cost AS "Cost"
             FROM supplies ORDER BY supply_id`,
  employees: `SELECT employee_id AS "ID", name AS "Name", position AS "Position",
                     hourly_rate AS "Hourly rate", to_char(hire_date, 'YYYY-MM-DD') AS "Hired"
              FROM employees ORDER BY employee_id`,
  orders: `SELECT o.order_id AS "Order", to_char(o.order_date, 'YYYY-MM-DD HH24:MI') AS "Date",
                  e.name AS "Employee", o.payment_method AS "Payment", o.order_total AS "Total"
           FROM orders o JOIN employees e USING (employee_id)
           ORDER BY o.order_date DESC, o.order_id DESC LIMIT 50`,
};

router.get('/manager/:view', async (req, res) => {
  const sql = MANAGER_VIEWS[req.params.view];
  if (!sql) throw httpError(404, 'Unknown view.');
  const { rows, fields } = await pool.query(sql);
  res.json({ columns: fields.map((f) => f.name), rows });
});

// Today's totals, a simple start on the X-report.
router.get('/reports/today', async (req, res) => {
  const [summary, hourly, top] = await Promise.all([
    pool.query(`SELECT COUNT(*)::int AS orders, COALESCE(SUM(order_total), 0) AS revenue,
                       COALESCE(AVG(order_total), 0) AS average
                FROM orders WHERE order_date::date = CURRENT_DATE`),
    pool.query(`SELECT to_char(date_trunc('hour', order_date), 'HH24:00') AS "Hour",
                       COUNT(*)::int AS "Orders", SUM(order_total) AS "Sales"
                FROM orders WHERE order_date::date = CURRENT_DATE
                GROUP BY 1 ORDER BY 1`),
    pool.query(`SELECT m.name AS "Drink", SUM(oi.quantity)::int AS "Sold"
                FROM order_items oi JOIN orders o USING (order_id) JOIN menu_items m USING (menu_item_id)
                WHERE o.order_date::date = CURRENT_DATE
                GROUP BY m.name ORDER BY 2 DESC LIMIT 5`),
  ]);
  res.json({ summary: summary.rows[0], hourly: hourly.rows, top: top.rows });
});

module.exports = router;
