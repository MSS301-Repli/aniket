-- Chạy một lần khi volume MySQL còn trống. User "animal" do image tạo từ MYSQL_USER/MYSQL_PASSWORD.
-- Mỗi service sở hữu một schema riêng (user-service / media-service / post-service).
CREATE DATABASE IF NOT EXISTS user_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS media_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS post_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON user_db.*  TO 'animal'@'%';
GRANT ALL PRIVILEGES ON media_db.* TO 'animal'@'%';
GRANT ALL PRIVILEGES ON post_db.*  TO 'animal'@'%';
FLUSH PRIVILEGES;
-- Production: tách thành 3 user, mỗi user chỉ có quyền trên đúng một schema.
