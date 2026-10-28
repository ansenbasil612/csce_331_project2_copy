// Checkout logic, ported from PosGUI.java (submit) and OrderInventory.java.
// Prices are always recomputed from the database, never trusted from the browser.
const { pool } = require('./db');

const SIZES = ['medium', 'large'];
const SUGAR = [0, 25, 50, 75, 100];
const ICE = ['no ice', 'less', 'normal'];
const PAYMENTS = ['Cash', 'Card', 'Gift Card'];

function httpError(status, message) {
  const err = new Error(message);
  err.status = status;
  return err;
}

const cents = (dollars) => Math.round(Number(dollars) * 100);

/** Checks the request body and returns cleaned drink lines. */
function validate({ employeeId, paymentMethod, items }) {
  if (!Number.isInteger(employeeId)) throw httpError(400, 'Choose an employee first.');
  if (!PAYMENTS.includes(paymentMethod)) throw httpError(400, 'Choose Cash, Card, or Gift Card.');
  if (!Array.isArray(items) || items.length === 0) throw httpError(400, 'Add a drink first.');
  return items.map((item) => {
    const line = {
      menuItemId: Number(item.menuItemId),
      quantity: Number(item.quantity ?? 1),
      size: item.size ?? 'medium',
      sugar: Number(item.sugar ?? 100),
      ice: item.ice ?? 'normal',
      toppings: [...new Set((item.toppings ?? []).map(Number))],
    };
    if (!Number.isInteger(line.menuItemId)) throw httpError(400, 'Unknown drink.');
    if (!Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 50) {
      throw httpError(400, 'Quantity must be between 1 and 50.');
    }
    if (!SIZES.includes(line.size)) throw httpError(400, 'Size must be medium or large.');
    if (!SUGAR.includes(line.sugar)) throw httpError(400, 'Sugar must be 0, 25, 50, 75, or 100%.');
    if (!ICE.includes(line.ice)) throw httpError(400, 'Ice must be no ice, less, or normal.');
    if (!line.toppings.every(Number.isInteger)) throw httpError(400, 'Unknown topping.');
    return line;
  });
}

/** Next id the same way the Java GUI picks it, so both apps can share the database. */
async function nextId(client, table, column) {
  const { rows } = await client.query(`SELECT COALESCE(MAX(${column}), 0) + 1 AS id FROM ${table}`);
  return rows[0].id;
}

/**
 * Saves an order with its drinks and toppings and deducts stock, all in one transaction.
 * Returns { orderId, total }.
 */
async function placeOrder(body) {
  const lines = validate(body);
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    // same lock as PosGUI so the Java order stream and the web app don't pick the same ids
    await client.query('LOCK TABLE orders, order_items, order_toppings IN SHARE ROW EXCLUSIVE MODE');

    const employee = await client.query('SELECT 1 FROM employees WHERE employee_id = $1', [body.employeeId]);
    if (employee.rowCount === 0) throw httpError(400, 'That employee does not exist.');

    const menuIds = [...new Set(lines.map((l) => l.menuItemId))];
    const toppingIds = [...new Set(lines.flatMap((l) => l.toppings))];
    const [menu, toppings, sizes] = await Promise.all([
      client.query('SELECT menu_item_id, base_price FROM menu_items WHERE menu_item_id = ANY($1)', [menuIds]),
      client.query('SELECT topping_id, cost FROM toppings WHERE topping_id = ANY($1)', [toppingIds]),
      client.query('SELECT size, surcharge FROM drink_sizes'),
    ]);
    const basePrice = new Map(menu.rows.map((r) => [r.menu_item_id, cents(r.base_price)]));
    const toppingCost = new Map(toppings.rows.map((r) => [r.topping_id, cents(r.cost)]));
    const surcharge = new Map(sizes.rows.map((r) => [r.size, cents(r.surcharge)]));

    let totalCents = 0;
    for (const line of lines) {
      if (!basePrice.has(line.menuItemId)) throw httpError(400, 'A drink in the cart is no longer on the menu.');
      if (line.toppings.some((t) => !toppingCost.has(t))) throw httpError(400, 'A topping in the cart no longer exists.');
      const each = basePrice.get(line.menuItemId)
        + (surcharge.get(line.size) ?? 0)
        + line.toppings.reduce((sum, t) => sum + toppingCost.get(t), 0);
      line.totalCents = each * line.quantity;
      totalCents += line.totalCents;
    }

    const orderId = await nextId(client, 'orders', 'order_id');
    let itemId = await nextId(client, 'order_items', 'order_item_id');
    let toppingRowId = await nextId(client, 'order_toppings', 'order_topping_id');

    await client.query(
      `INSERT INTO orders (order_id, order_date, order_total, employee_id, payment_method)
       VALUES ($1, CURRENT_TIMESTAMP, $2, $3, $4)`,
      [orderId, totalCents / 100, body.employeeId, body.paymentMethod],
    );
    for (const line of lines) {
      await client.query(
        `INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, size,
           sugar_percent, ice_level, item_total)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
        [itemId, orderId, line.menuItemId, line.quantity, line.size, line.sugar, line.ice, line.totalCents / 100],
      );
      for (const toppingId of line.toppings) {
        await client.query(
          'INSERT INTO order_toppings (order_topping_id, order_item_id, topping_id) VALUES ($1, $2, $3)',
          [toppingRowId++, itemId, toppingId],
        );
      }
      itemId++;
    }

    await deductInventory(client, orderId);
    await client.query('COMMIT');
    return { orderId, total: totalCents / 100 };
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    throw err;
  } finally {
    client.release();
  }
}

/** Port of OrderInventory.deduct: ingredients, toppings, then cups and seals. */
async function deductInventory(client, orderId) {
  const noRecipe = await client.query(
    `SELECT m.name FROM order_items oi JOIN menu_items m USING (menu_item_id)
     LEFT JOIN menu_inventory mi USING (menu_item_id) WHERE oi.order_id = $1
     GROUP BY m.menu_item_id, m.name
     HAVING COUNT(mi.inventory_id) = 0 OR MIN(mi.quantity_per_serving) <= 0`,
    [orderId],
  );
  if (noRecipe.rowCount > 0) throw httpError(409, `Set up a recipe for ${noRecipe.rows[0].name} first.`);

  await deductRows(client, orderId, 'ingredients', 'ingredient_id',
    `SELECT i.ingredient_id AS id, i.name, SUM(mi.quantity_per_serving * oi.quantity) AS amount
     FROM order_items oi JOIN menu_inventory mi USING (menu_item_id)
     JOIN ingredients i USING (ingredient_id) WHERE oi.order_id = $1
     GROUP BY i.ingredient_id, i.name ORDER BY i.ingredient_id`);

  await deductRows(client, orderId, 'toppings', 'topping_id',
    `SELECT t.topping_id AS id, t.name, SUM(oi.quantity) AS amount FROM order_toppings ot
     JOIN order_items oi USING (order_item_id) JOIN toppings t USING (topping_id)
     WHERE oi.order_id = $1 GROUP BY t.topping_id, t.name ORDER BY t.topping_id`);

  const missingCups = await client.query(
    `SELECT DISTINCT oi.size FROM order_items oi WHERE oi.order_id = $1
     AND (NOT EXISTS (SELECT 1 FROM supplies WHERE usage_key = oi.size)
       OR NOT EXISTS (SELECT 1 FROM supplies WHERE usage_key = 'seal'))`,
    [orderId],
  );
  if (missingCups.rowCount > 0) throw httpError(409, 'Set up cup and seal supplies first.');

  await deductRows(client, orderId, 'supplies', 'supply_id',
    `SELECT s.supply_id AS id, s.name, SUM(oi.quantity) AS amount FROM order_items oi
     JOIN supplies s ON s.usage_key = oi.size OR s.usage_key = 'seal'
     WHERE oi.order_id = $1 GROUP BY s.supply_id, s.name ORDER BY s.supply_id`);
}

async function deductRows(client, orderId, table, idColumn, neededSql) {
  const needed = await client.query(neededSql, [orderId]);
  for (const row of needed.rows) {
    const result = await client.query(
      `UPDATE ${table} SET quantity_on_hand = quantity_on_hand - $1
       WHERE ${idColumn} = $2 AND quantity_on_hand >= $1`,
      [row.amount, row.id],
    );
    if (result.rowCount !== 1) throw httpError(409, `Not enough ${row.name} in stock.`);
  }
}

module.exports = { placeOrder, httpError };
