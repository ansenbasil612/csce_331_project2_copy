-- add the payment field without changing existing orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(10)
CHECK (payment_method IN ('Cash', 'Card', 'Gift Card'));
