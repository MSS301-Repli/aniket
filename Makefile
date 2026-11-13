# Lối tắt cho build và docker compose. `make help` để xem danh sách. Compose cần file .env (`make env`).
COMPOSE ?= docker compose
MVN ?= mvn -B -ntp
ALL_PROFILES := --profile core --profile app --profile obs

.PHONY: help verify test-lib env check core up obs logs ps down reset lint-deploy

help:
	@echo "make verify            mvn verify toàn bộ reactor (giống CI)"
	@echo "make test-lib M=libs/common   test một module kèm module nó phụ thuộc"
	@echo "make env               tạo .env từ .env.example (ĐỔI mật khẩu trước khi chạy)"
	@echo "make check             kiểm tra cú pháp compose (mọi profile)"
	@echo "make core | up | obs   hạ tầng | hạ tầng + service | thêm giám sát"
	@echo "make logs S=user-service | ps | down"
	@echo "make reset             XOÁ cả volume dữ liệu (chỉ dev)"
	@echo "make lint-deploy       kiểm tra script + overlay demo (như CI Deploy lint)"

verify:
	$(MVN) verify

test-lib:
	@test -n "$(M)" || (echo "Dùng: make test-lib M=libs/common" && exit 1)
	$(MVN) -pl $(M) -am verify

env:
	@test -f .env && echo ".env đã có, không ghi đè" || (cp .env.example .env && echo "Đã tạo .env: sửa mọi CHANGE_ME_* trước khi chạy")

check:
	COMPOSE_PROFILES=core,app,obs $(COMPOSE) config --quiet && echo "compose hợp lệ"

core:
	$(COMPOSE) --profile core up -d

up:
	$(COMPOSE) --profile core --profile app up -d --build

obs:
	$(COMPOSE) --profile core --profile obs up -d

logs:
	$(COMPOSE) logs -f --tail=200 $(S)

ps:
	$(COMPOSE) $(ALL_PROFILES) ps -a

down:
	$(COMPOSE) $(ALL_PROFILES) down

# CẢNH BÁO: xoá toàn bộ volume (Kafka, Redis, Keycloak, Grafana...). Chỉ dùng cho môi trường dev.
reset:
	$(COMPOSE) $(ALL_PROFILES) down -v

lint-deploy:
	bash deploy/demo/validate-ci.sh
