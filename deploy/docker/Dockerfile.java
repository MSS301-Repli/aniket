# syntax=docker/dockerfile:1.7
# Một Dockerfile dùng chung cho mọi module Spring Boot. Build từ GỐC repo:
#   docker build -f deploy/docker/Dockerfile.java \
#     --build-arg MODULE_PATH=services/user-service --build-arg APP_PORT=8101 .
# (docker compose đã khai báo sẵn các build-arg này cho từng service.)

############################ Giai đoạn build ############################
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE_PATH
WORKDIR /src
# Copy cả repo (đã lọc bằng Dockerfile.java.dockerignore). -am build luôn libs/common, libs/outbox-jpa.
COPY pom.xml ./
COPY libs ./libs
COPY infra ./infra
COPY services ./services
# Cache thư mục ~/.m2 giữa các lần build để không tải lại thư viện.
RUN --mount=type=cache,target=/root/.m2 \
    test -n "$MODULE_PATH" || (echo "Thiếu --build-arg MODULE_PATH" && exit 1); \
    mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=30 -Daether.connector.http.connectionMaxTtl=30 -pl "$MODULE_PATH" -am -DskipTests package

############################ Giai đoạn chạy ############################
FROM eclipse-temurin:24-jre AS runtime
ARG MODULE_PATH
ARG APP_PORT=8080
ENV APP_PORT=${APP_PORT} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# curl cho HEALTHCHECK; tạo user không có quyền root.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app

WORKDIR /app
# Spring Boot tạo <tên>-<phiên bản>.jar (và .jar.original bị loại bởi glob này).
COPY --from=build --chown=app:app /src/${MODULE_PATH}/target/*-SNAPSHOT.jar /app/app.jar
USER app

EXPOSE ${APP_PORT}
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=6 \
  CMD curl -fs "http://localhost:${APP_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# exec để java nhận SIGTERM trực tiếp (tắt êm: hoàn tất request, đóng consumer Kafka).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
