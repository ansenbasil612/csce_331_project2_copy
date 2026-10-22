-- simulated methods for demo orders, not historical payment records
UPDATE orders
SET payment_method = CASE MOD(order_id, 3)
    WHEN 0 THEN 'Cash'
    WHEN 1 THEN 'Card'
    ELSE 'Gift Card'
END
WHERE payment_method IS NULL;
