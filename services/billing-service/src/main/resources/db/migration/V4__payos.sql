-- Thanh toán VietQR qua payOS (VND). NULL = không bán qua payOS.
ALTER TABLE products ADD COLUMN price_vnd INTEGER;

-- GIÁ DƯỚI ĐÂY LÀ GIÁ TẠM (VND), hãy sửa trước khi bán thật. payOS không có sandbox: mọi giao dịch là tiền thật.
UPDATE products SET price_vnd = 79000  WHERE sku = 'animallocket.pro.month';
UPDATE products SET price_vnd = 649000 WHERE sku = 'animallocket.pro.year';
UPDATE products SET price_vnd = 25000  WHERE sku = 'animallocket.boost.1';
UPDATE products SET price_vnd = 99000  WHERE sku = 'animallocket.boost.5';
UPDATE products SET price_vnd = 25000  WHERE sku = 'animallocket.invite.1';
UPDATE products SET price_vnd = 99000  WHERE sku = 'animallocket.invite.5';
UPDATE products SET price_vnd = 49000  WHERE sku = 'animallocket.pack.safari';
UPDATE products SET price_vnd = 49000  WHERE sku = 'animallocket.pack.ocean';

-- Số tiền USD của PayPal không còn bắt buộc: sản phẩm có thể chỉ bán qua payOS.
ALTER TABLE checkout_sessions ALTER COLUMN amount_cents DROP NOT NULL;

-- payos_amount: số tiền VND chốt lần đầu bấm payOS. payos_attempts: số link đã tạo (mỗi link một orderCode, xem CheckoutService).
ALTER TABLE checkout_sessions ADD COLUMN payos_amount INTEGER;
ALTER TABLE checkout_sessions ADD COLUMN payos_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE checkout_sessions ADD COLUMN payos_link_id VARCHAR(64);
ALTER TABLE checkout_sessions ADD COLUMN payos_paid_order_code BIGINT;
