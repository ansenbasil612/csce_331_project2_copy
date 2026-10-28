# web GUI (Express.js)

Preliminary web version of the POS. It uses the same squad_73 database and schema as
the Java GUI, so orders placed in either app show up in both.

## run it

Requires Node.js 18 or newer (`node -v`). From this `web/` folder:

```sh
npm install
cp .env.example .env      # Windows PowerShell: copy .env.example .env
```

Put the team password in `.env` as `PGPASSWORD=...`, then:

```sh
npm start                 # or: npm run dev  (restarts when you save a file)
```

Open http://localhost:3000. The TAMU database needs TAMU Wi-Fi or the VPN.

## pages

| Page | URL | What works now |
| --- | --- | --- |
| Home | `/` | Links to each view |
| Cashier | `/cashier.html` | Pick employee, customize drinks (size, sugar, ice, toppings, qty), cart, pay with Cash/Card/Gift Card |
| Customer kiosk | `/kiosk.html` | Same ordering flow with larger buttons; Card/Gift Card only; saved under `KIOSK_EMPLOYEE_ID` |
| Manager | `/manager.html` | Today's sales, recent orders (click for drinks), menu, ingredients/toppings/supplies with low-stock highlighting, employees. Read-only for now |
| Menu board | `/menuboard.html` | Menu by category with toppings and size pricing; refreshes every minute |

## how it fits together

```
server.js          starts Express, serves public/ and mounts /api
db.js              PostgreSQL connection pool (reads .env)
orders.js          checkout: prices, inserts, stock deduction (port of PosGUI + OrderInventory)
routes/api.js      JSON endpoints used by the pages
public/            HTML, CSS and browser JS for each page
```

API endpoints:

- `GET /api/menu` – drinks, toppings, sizes
- `GET /api/employees`
- `POST /api/orders` – `{ employeeId, paymentMethod, items: [{ menuItemId, quantity, size, sugar, ice, toppings: [ids] }] }`
- `POST /api/kiosk/orders` – same without `employeeId`
- `GET /api/orders/:id/items`
- `GET /api/manager/:view` – `menu`, `ingredients`, `toppings`, `supplies`, `employees`, `orders`
- `GET /api/reports/today`

Checkout follows the Java rules: medium is the menu price, large adds the `drink_sizes`
surcharge, each topping adds its price, and the whole order rolls back if a recipe,
cup, or stock is missing. The server recalculates prices; it never trusts the browser's total.

## not done yet

- Login (cashier/manager passwords or OAuth)
- Manager editing: add/update menu items, inventory, employees
- Full reports: X/Z report, sales by date range, product usage, restock report
