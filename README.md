# CSCE_331_Project2

## database setup

Use TAMU Wi-Fi or VPN for the team database:

```sh
psql -h csce-315-db.engr.tamu.edu -U squad_73 -d squad_73_db
```

Enter the team password when prompted. Run SQL files from the repo root.

- `database_creation.sql` creates the tables and **deletes existing tables**.
- `generate_sales.py` generates the CSV seed data.
- `database_clear.sql` removes all rows.
- `database_seed.sql` loads the CSVs and starting demo stock.
- `queries.sql` runs the project queries and sales report.

For a new demo database:

```sql
\i database_creation.sql
\i database_seed.sql
```

For an existing database with the order-items schema, apply these updates instead
of rebuilding:

```sql
\i database_inventory_update.sql
\i database_serving_update.sql
\i database_topping_price_update.sql
\i database_payment_update.sql
\i database_restock_update.sql
```

To fill demo stock, run `\i database_stock_seed.sql`. It raises ingredients to
at least 2000 units, toppings to 1000 servings, and supplies to 1000 pieces.
Coordinate with the team before clearing, rebuilding, or restocking the shared DB.

## run the gui

Requires a JDK. Set these in your terminal, then enter the database password:

```sh
export DB_URL='jdbc:postgresql://csce-315-db.engr.tamu.edu/squad_73_db'
export DB_USER='squad_73'
read -s DB_PASSWORD
export DB_PASSWORD
```

Compile and run from the repo root:

```sh
javac -cp gui/postgresql-42.2.8.jar gui/*.java
java -cp 'gui:gui/postgresql-42.2.8.jar' PosGUI
```

For a local database, change `DB_URL`, `DB_USER`, and `DB_PASSWORD` to its connection
settings. No TAMU connection is needed when using localhost.

## login and views

Select your name and position. These are shared demo passwords:

- Manager: `2`, opens only the manager view.
- Cashier/barista: `1`, opens only the cashier view.

Log out to switch users. Cashier logout asks before clearing the cart.
Orders use the logged-in employee.

Cashiers select Tea or Coffee, customize drinks, edit the cart, and checkout.
After Confirm order, choose Cash, Card, or Gift Card to save and return to the
empty cashier screen. Back or closing payment keeps the cart without saving.
Managers can manage menu items, stock, and employees; browse all orders; and run
reports. Select an order to see its drinks. Reports include an X-report for today,
sales summaries, popular items, and ingredient usage with a chart and CSV export.

## prices and stock

- Medium uses the menu price; large adds $0.75.
- Each selected topping adds $0.50 per drink.
- Each drink uses its recipe ingredients, one cup, one seal, and its topping servings.
- Medium uses a 16oz cup; large uses a 24oz cup.
- Missing recipes or insufficient stock cancel the whole order without losing the cart.

New drinks need recipes in `menu_inventory` before checkout. Ingredient amounts
do not yet change with size, sweetness, or ice. Cup Seal has a placeholder cost
of zero. The chosen payment method is recorded, but card processing and gift card balance
checks are handled separately.

Use Inventory → Ingredients / Toppings / Supplies to update stock, then Refresh
to see deductions. Each item also has a restock minimum (seeded at 200 oz per
ingredient, 100 topping servings, and 200 supply pieces); Reports → Restock Report
lists everything currently below its minimum and ignores the date range. Restart the app after changing menu prices to reload cashier data.
Employees with linked orders cannot be deleted.

## demo

Keep the GUI beside a SQL terminal connected to the same database:

1. Submit a cashier order and check it with `\i demo_orders.sql`.
2. Add or update a menu item and its price.
3. Add an ingredient and update its quantity.
4. Add or update an employee.
5. Run a report, then rerun it after creating an order.

Use `\i demo_manager.sql` to verify manager changes. Report date ranges include
both dates; the X-report always uses today. psql is not a JDBC client—if a JDBC
console is required, run the same SELECT statements there.

For simulated orders using the configured database:

```sh
java -cp 'gui:gui/postgresql-42.2.8.jar' OrderStream 10 3
```

This saves 10 orders, three seconds apart, and deducts stock. Ctrl+C stops it;
completed orders stay saved. Use a demo database. The stream does not replace
showing a GUI change during the demo.

Run `\i queries.sql` for the project queries. The sales query defaults to all saved
dates. To choose a range, add `-v start_date=2026-09-01 -v end_date=2026-09-30`
when running psql with `-f queries.sql`.

For older demo orders without payment data, run `\i database_demo_payment_seed.sql`
after the payment migration. It assigns simulated Cash/Card/Gift Card methods
only where the method is missing. Seeding and the order stream also assign demo
payment methods; these are not historical payment records.

## order tables

`orders` stores receipts, `order_items` stores drink lines, and `order_toppings`
links toppings to each line. Line totals include quantity, size, and toppings.
Orders and stock deductions save together or roll back together.

Historical CSVs contain one drink per receipt without customization details.
Imported drinks default to medium, 100% sweetness, and normal ice.
