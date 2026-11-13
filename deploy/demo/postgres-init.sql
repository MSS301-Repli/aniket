-- Chạy một lần khi volume PostgreSQL còn trống (psql của image). Role "animal" và billing_db do image tạo
-- (POSTGRES_USER/POSTGRES_DB); ở đây thêm keycloak_db + schema keycloak (KC_DB_SCHEMA trong deploy/compose/edge.yml).
SELECT 'CREATE DATABASE keycloak_db OWNER animal ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak_db')\gexec

REVOKE ALL ON DATABASE keycloak_db FROM PUBLIC;
REVOKE ALL ON DATABASE billing_db FROM PUBLIC;

\connect keycloak_db
CREATE SCHEMA IF NOT EXISTS keycloak AUTHORIZATION animal;
REVOKE ALL ON SCHEMA keycloak FROM PUBLIC;
