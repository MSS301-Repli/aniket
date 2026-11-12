-- Lưu capture id của PayPal để webhook hoàn tiền/chargeback tìm lại được phiên (và số tiền đã thu).
ALTER TABLE checkout_sessions ADD COLUMN paypal_capture_id VARCHAR(100);
CREATE INDEX idx_checkout_capture ON checkout_sessions (paypal_capture_id);
CREATE INDEX idx_checkout_order ON checkout_sessions (paypal_order_id);
