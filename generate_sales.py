import os
import csv
import random
from datetime import datetime, date, timedelta

# ============================================================================
# HARDCODED NON-NUMERICAL DATAs
# ============================================================================

MENU_ITEMS = [
    {"id": 1, "name": "Classic Milk Tea", "base_price": 4.99, "description": "Black tea with milk", "category": "Tea"},
    {"id": 2, "name": "Jasmine Green Tea", "base_price": 4.49, "description": "Green tea with jasmine", "category": "Tea"},
    {"id": 3, "name": "Oolong Tea", "base_price": 4.99, "description": "Classic oolong tea", "category": "Tea"},
    {"id": 4, "name": "Brown Sugar Milk Tea", "base_price": 5.49, "description": "Milk tea with brown sugar", "category": "Tea"},
    {"id": 5, "name": "Taro Milk Tea", "base_price": 5.49, "description": "Milk tea with taro", "category": "Tea"},
    {"id": 6, "name": "Dragonfruit Tea", "base_price": 5.99, "description": "Fruit tea with dragonfruit", "category": "Tea"},
    {"id": 7, "name": "Strawberry Tea", "base_price": 5.49, "description": "Fruit tea with strawberry", "category": "Tea"},
    {"id": 8, "name": "Mango Tea", "base_price": 5.49, "description": "Fruit tea with mango", "category": "Tea"},
    {"id": 9, "name": "Lychee Green Tea", "base_price": 5.49, "description": "Green tea with lychee", "category": "Tea"},
    {"id": 10, "name": "Thai Tea", "base_price": 5.49, "description": "Sweet Thai milk tea", "category": "Tea"},
    {"id": 11, "name": "Honey Lemon Tea", "base_price": 5.49, "description": "Tea with honey and lemon", "category": "Tea"},
    {"id": 12, "name": "Peach Oolong", "base_price": 5.49, "description": "Oolong tea with peach", "category": "Tea"},
    {"id": 13, "name": "Winter Melon Tea", "base_price": 4.99, "description": "Sweet winter melon tea", "category": "Tea"},
    {"id": 14, "name": "Brown Sugar Boba Latte", "base_price": 6.49, "description": "Latte with brown sugar and boba", "category": "Tea"},
    {"id": 15, "name": "Toffee Almond Latte", "base_price": 6.49, "description": "Latte with toffee and almond", "category": "Coffee"},
    {"id": 16, "name": "Matcha Latte", "base_price": 6.49, "description": "Latte with matcha", "category": "Coffee"},
    {"id": 17, "name": "Americano", "base_price": 5.49, "description": "Espresso with water", "category": "Coffee"},
    {"id": 18, "name": "Dirty Chai", "base_price": 5.49, "description": "Chai tea with espresso", "category": "Coffee"},
    {"id": 19, "name": "Cold Brew", "base_price": 5.49, "description": "Cold brewed coffee", "category": "Coffee"},
    {"id": 20, "name": "Coffee Latte", "base_price": 5.99, "description": "Espresso with steamed milk", "category": "Coffee"},
]

TOPPINGS = [
    {"id": 1, "name": "Boba Pearls", "cost": 0.50},
    {"id": 2, "name": "Popping Boba", "cost": 0.50},
    {"id": 3, "name": "Jelly Cubes", "cost": 0.50},
    {"id": 4, "name": "Pudding", "cost": 0.50},
    {"id": 5, "name": "Red Bean", "cost": 0.50},
    {"id": 6, "name": "Grass Jelly", "cost": 0.50},
    {"id": 7, "name": "Lychee Jelly", "cost": 0.50},
    {"id": 8, "name": "Aloe Vera", "cost": 0.50},
    {"id": 9, "name": "Whipped Cream", "cost": 0.50},
]

SUPPLIES = [
    {"id": 1, "name": "16oz Cup", "cost": 0.15, "category": "cups"},
    {"id": 2, "name": "24oz Cup", "cost": 0.20, "category": "cups"},
    {"id": 3, "name": "Plastic Straw", "cost": 0.05, "category": "straw"},
    {"id": 4, "name": "Stainless Steel Straw", "cost": 0.25, "category": "straw"},
    {"id": 5, "name": "Napkin Pack", "cost": 0.10, "category": "napkin"},
    {"id": 6, "name": "Paper Bag (Large)", "cost": 0.20, "category": "bag"},
    {"id": 7, "name": "Paper Bag (Small)", "cost": 0.10, "category": "bag"},
    {"id": 8, "name": "Cup Holder", "cost": 0.05, "category": "holder"},
]

INGREDIENTS = [
    {"id": 1, "name": "Black Tea Leaves", "cost": 0.30},
    {"id": 2, "name": "Green Tea Leaves", "cost": 0.35},
    {"id": 3, "name": "Milk", "cost": 0.50},
    {"id": 4, "name": "Brown Sugar Syrup", "cost": 0.40},
    {"id": 5, "name": "Honey", "cost": 0.60},
    {"id": 6, "name": "Taro Powder", "cost": 0.80},
    {"id": 7, "name": "Matcha Powder", "cost": 1.20},
    {"id": 8, "name": "Strawberry Syrup", "cost": 0.50},
    {"id": 9, "name": "Mango Puree", "cost": 0.70},
    {"id": 10, "name": "Lychee Syrup", "cost": 0.50},
    {"id": 11, "name": "Oolong Tea Leaves", "cost": 0.40},
    {"id": 12, "name": "Jasmine Tea Leaves", "cost": 0.45},
    {"id": 13, "name": "Espresso", "cost": 0.60},
    {"id": 14, "name": "Dragonfruit Puree", "cost": 0.75},
    
]

EMPLOYEES = [
    {"id": 1, "name": "Aaron DallaRosa", "position": "Manager", "hourly_rate": 18.50},
    {"id": 2, "name": "Ansen Basil", "position": "Barista", "hourly_rate": 15.00},
    {"id": 3, "name": "Joyce You", "position": "Barista", "hourly_rate": 15.00},
    {"id": 4, "name": "Matthew Shi", "position": "Barista", "hourly_rate": 15.00},
    {"id": 5, "name": "Vincent Gomez", "position": "Cashier", "hourly_rate": 14.50},
]

# ============================================================================
# DATA GENERATION CONFIGURATION
# ============================================================================

WEEKS = 52
TOTAL_SALES_TARGET = 1_000_000  # $1M
PEAK_MULTIPLIER = 3.5  # Peak days have 3.5x normal sales

# Peak day dates (e.g., start of semesters, holidays)
PEAK_DATES = [
    (date(2025, 12, 8), "Final Exams Period"),
    (date(2026, 1, 12), "Spring Semester Start"),
    (date(2026, 8, 24), "Fall Semester Start"),
]

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def generate_date_range(weeks=52):
    """The last `weeks` weeks, ending today."""
    end_date = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    start_date = end_date - timedelta(weeks=weeks)
    return start_date, end_date

def is_peak_day(d):
    return any(d.date() == pd for pd, _ in PEAK_DATES)

def get_hourly_pattern():
    """Return hourly sales multiplier (simulates realistic sales patterns)"""
    # Higher sales during morning/afternoon, lower at night
    patterns = {
        8: 0.5,   # Early morning
        9: 0.8,   # Morning rush start
        10: 1.2,  # Morning peak
        11: 1.5,  # Late morning
        12: 1.8,  # Lunch rush
        13: 1.6,  # Post-lunch
        14: 1.3,  # Afternoon
        15: 1.4,  # Afternoon peak
        16: 1.5,  # Late afternoon
        17: 1.3,  # Evening start
        18: 1.0,  # Evening
        19: 0.7,  # Late evening
        20: 0.4,  # Night
    }
    return patterns

def get_day_multiplier(day_of_week):
    """Return day-of-week sales multiplier"""
    # Higher sales on weekdays, lower on weekends
    patterns = {
        0: 1.2,  # Monday
        1: 1.3,  # Tuesday
        2: 1.3,  # Wednesday
        3: 1.2,  # Thursday
        4: 1.1,  # Friday
        5: 0.9,  # Saturday
        6: 0.8,  # Sunday
    }
    return patterns.get(day_of_week, 1.0)

def estimate_scale(start_date, end_date, target, avg_order=6.42):
    """Scale factor so the target sales are spread over the whole date range."""
    hour_total = sum(get_hourly_pattern().values())
    expected_orders = 0
    d = start_date
    while d <= end_date:
        peak = PEAK_MULTIPLIER if is_peak_day(d) else 1.0
        expected_orders += 40 * get_day_multiplier(d.weekday()) * peak * hour_total
        d += timedelta(days=1)
    return target / (expected_orders * avg_order)

def generate_orders(start_date, end_date, total_sales_target):    
    orders = []
    order_id = 1
    current_sales = 0
    hourly_pattern = get_hourly_pattern()
    scale = estimate_scale(start_date, end_date, total_sales_target)
    
    current_date = start_date
    
    while current_date <= end_date:
        day_of_week = current_date.weekday()
        day_mult = get_day_multiplier(day_of_week)
        
        # Check if peak day
        is_peak = is_peak_day(current_date)
        peak_mult = PEAK_MULTIPLIER if is_peak else 1.0
        
        # Generate orders for each hour
        for hour in range(8, 21):  # 8 AM to 8 PM
            hour_mult = hourly_pattern.get(hour, 0.5)
            
            # Baseline orders per hour: ~30-50 orders
            base_orders = random.randint(30, 50)
            num_orders = round(base_orders * day_mult * peak_mult * hour_mult * scale)            
            for _ in range(num_orders):
                # Randomly select menu items and toppings
                menu_item = random.choice(MENU_ITEMS)
                toppings = random.sample(TOPPINGS, k=random.randint(0, 3))                
                # Calculate price
                order_total = menu_item["base_price"]
                for topping in toppings:
                    order_total += topping["cost"]
                
                # Add small random variation
                order_total *= random.uniform(0.95, 1.05)
                order_total = round(order_total, 2)
                
                # Create order
                order = {
                    "order_id": order_id,
                    "order_date": current_date.replace(hour=hour, minute=random.randint(0, 59)),
                    "menu_item_id": menu_item["id"],
                    "order_total": order_total,
                    "employee_id": random.choice(EMPLOYEES)["id"],
                    "topping_ids": [t["id"] for t in toppings]
                    }
                
                orders.append(order)
                current_sales += order_total
                order_id += 1
                
                # Check if we've reached our sales target
                if current_sales >= total_sales_target:
                    return orders
        
        current_date += timedelta(days=1)
    
    return orders

def generate_menu_inventory():
    """Assign ingredients to each menu item."""
    menu_inventory = []
    inventory_id = 1

    keyword_map = {
        "brown sugar": 4, "matcha": 7, "taro": 6, "strawberry": 8, "mango": 9,
        "lychee": 10, "oolong": 11, "jasmine": 12, "honey": 5,
        "dragonfruit": 14, "americano": 13, "dirty chai": 13, "cold brew": 13, "coffee": 13, "toffee":13,
    }

    for menu_item in MENU_ITEMS:
        name_lower = menu_item["name"].lower()

        if menu_item["category"] == "Coffee":
            base_ingredients = [3] if ("latte" in name_lower or "chai" in name_lower) else []
        else:
            base_ingredients = [1, 2, 3]

        for ingredient_id in base_ingredients:
            menu_inventory.append({
                "inventory_id": inventory_id,
                "menu_item_id": menu_item["id"],
                "ingredient_id": ingredient_id,
                "quantity_per_serving": round(random.uniform(1, 3), 2),
            })
            inventory_id += 1

        special = []
        for kw, ing_id in keyword_map.items():
            if kw in name_lower and ing_id not in base_ingredients and ing_id not in special:
                special.append(ing_id)
        if not special:
            special = random.sample([i for i in range(4, 13) if i not in base_ingredients], k=random.randint(1, 3))

        for ingredient_id in special:
            menu_inventory.append({
                "inventory_id": inventory_id,
                "menu_item_id": menu_item["id"],
                "ingredient_id": ingredient_id,
                "quantity_per_serving": round(random.uniform(0.5, 2), 2),
            })
            inventory_id += 1

    return menu_inventory

# ============================================================================
# MAIN EXECUTION
# ============================================================================

if __name__ == "__main__":
    OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.chdir(OUTPUT_DIR)
    print("🧋 Generating Bubble Tea Sales Data for CSCE 331 Project 2...")
    print(f"   Team Size: 5 members")
    print(f"   Weeks: {WEEKS}")
    print(f"   Target Sales: ${TOTAL_SALES_TARGET:,}")
    print(f"   Peak Days: {len(PEAK_DATES)}")
    print()
    
    # Generate date range
    start_date, end_date = generate_date_range(WEEKS)
    print(f"📅 Date Range: {start_date.strftime('%Y-%m-%d')} to {end_date.strftime('%Y-%m-%d')}")
    
    # Generate orders
    print(f"📊 Generating orders...")
    orders = generate_orders(start_date, end_date, TOTAL_SALES_TARGET)
    total_revenue = sum(o["order_total"] for o in orders)
    print(f"   ✓ Generated {len(orders):,} orders")
    print(f"   ✓ Total Revenue: ${total_revenue:,.2f}")
    print(f"   ✓ Average Order: ${total_revenue / len(orders):.2f}")
    
    # Generate menu inventory
    print(f"📋 Generating menu inventory...")
    menu_inventory = generate_menu_inventory()
    print(f"   ✓ Generated {len(menu_inventory)} menu-ingredient mappings")
    
    # Write CSV files
    print(f"\n💾 Writing CSV files...")
    
    # Orders
    with open("orders.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "order_date", "menu_item_id", "order_total", "employee_id"])
        for o in orders:
            writer.writerow([o["order_id"], o["order_date"], o["menu_item_id"], o["order_total"], o["employee_id"]])
    print(f"   ✓ orders.csv ({len(orders)} rows)")

    # Order toppings (one row per topping on an order)
    topping_rows = 0
    with open("order_toppings.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "topping_id"])
        for o in orders:
            for tid in o["topping_ids"]:
                writer.writerow([o["order_id"], tid])
                topping_rows += 1
    print(f"   ✓ order_toppings.csv ({topping_rows} rows)")
    
    # Menu Items
    with open("menu_items.csv", "w", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["menu_item_id", "name", "base_price", "description", "category"]
        )
        writer.writeheader()

        for item in MENU_ITEMS:
            writer.writerow({
                "menu_item_id": item["id"],
                "name": item["name"],
                "base_price": item["base_price"],
                "description": item["description"],
                "category": item["category"]
            })

        print(f"   ✓ menu_items.csv ({len(MENU_ITEMS)} rows)")
    
            # Toppings
    with open("toppings.csv", "w", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["topping_id", "name", "cost"]
        )
        writer.writeheader()

        for topping in TOPPINGS:
            writer.writerow({
                "topping_id": topping["id"],
                "name": topping["name"],
                "cost": topping["cost"]
            })
        print(f"   ✓ toppings.csv ({len(TOPPINGS)} rows)")
    
        # Supplies
    with open("supplies.csv", "w", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["supply_id", "name", "cost", "category"]
        )
        writer.writeheader()

        for supply in SUPPLIES:
            writer.writerow({
                "supply_id": supply["id"],
                "name": supply["name"],
                "cost": supply["cost"],
                "category": supply["category"]
            })

        print(f"   ✓ supplies.csv ({len(SUPPLIES)} rows)")
    
        # Ingredients
    with open("ingredients.csv", "w", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["ingredient_id", "name", "cost", "unit_of_measure"]
        )
        writer.writeheader()

        for ingredient in INGREDIENTS:
            writer.writerow({
                "ingredient_id": ingredient["id"],
                "name": ingredient["name"],
                "cost": ingredient["cost"],
                "unit_of_measure": "oz"
            })

        print(f"   ✓ ingredients.csv ({len(INGREDIENTS)} rows)")
    
    # Menu Inventory
    with open("menu_inventory.csv", "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["inventory_id", "menu_item_id", "ingredient_id", "quantity_per_serving"])
        writer.writeheader()
        writer.writerows(menu_inventory)
    print(f"   ✓ menu_inventory.csv ({len(menu_inventory)} rows)")
    
        # Employees
    with open("employees.csv", "w", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["employee_id", "name", "position", "hourly_rate"]
        )
        writer.writeheader()

        for employee in EMPLOYEES:
            writer.writerow({
                "employee_id": employee["id"],
                "name": employee["name"],
                "position": employee["position"],
                "hourly_rate": employee["hourly_rate"]
            })

        print(f"   ✓ employees.csv ({len(EMPLOYEES)} rows)")
    
    print(f"\n✨ Data generation complete!")
    print(f"\nFiles created:")
    print(f"  • orders.csv")
    print(f"  • menu_items.csv")
    print(f"  • toppings.csv")
    print(f"  • supplies.csv")
    print(f"  • ingredients.csv")
    print(f"  • menu_inventory.csv")
    print(f"  • employees.csv")
