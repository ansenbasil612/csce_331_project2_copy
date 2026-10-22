-- BUBBLE TEA VENDOR DATABASE - CLEAR DATA
-- CSCE 331 Project 2

TRUNCATE TABLE
    order_toppings,
    order_items,
    orders,
    menu_inventory,
    menu_items,
    toppings,
    ingredients,
    supplies,
    employees
RESTART IDENTITY CASCADE;
