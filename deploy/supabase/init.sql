-- Chạy MỘT LẦN trong Supabase → SQL Editor (đăng nhập role postgres).
-- Tạo hai role + hai schema tách biệt: billing (billing-service) và keycloak (Keycloak).
-- TRƯỚC KHI CHẠY: thay hai chuỗi CHANGE_ME_... bằng mật khẩu CHỈ GỒM CHỮ VÀ SỐ (>= 24 ký tự), rồi lưu vào .env.
-- KHÔNG commit file này sau khi đã điền mật khẩu thật.

CREATE ROLE billing_app  LOGIN PASSWORD 'CHANGE_ME_billing_password';
CREATE ROLE keycloak_app LOGIN PASSWORD 'CHANGE_ME_keycloak_password';

-- Supabase: role postgres không phải superuser, cần là thành viên của role đích mới tạo được schema thuộc về nó.
GRANT billing_app  TO postgres;
GRANT keycloak_app TO postgres;

CREATE SCHEMA billing  AUTHORIZATION billing_app;
CREATE SCHEMA keycloak AUTHORIZATION keycloak_app;

-- Mỗi role chỉ thấy schema của mình; không đụng vào public hay schema của Supabase.
REVOKE ALL ON SCHEMA public FROM billing_app, keycloak_app;
REVOKE ALL ON SCHEMA billing  FROM keycloak_app;
REVOKE ALL ON SCHEMA keycloak FROM billing_app;

-- Flyway/Hibernate/Keycloak dùng schema mặc định của role.
ALTER ROLE billing_app  SET search_path = billing;
ALTER ROLE keycloak_app SET search_path = keycloak;
