## Issue

Closes #

## Thay đổi

<!-- Tóm tắt ngắn: làm gì và vì sao. -->

## Kiểm tra

<!-- Lệnh đã chạy và kết quả, ví dụ `mvn -B -pl libs/common -am verify`. -->

## Checklist

- [ ] Nhánh `feature/<ID>-<slug>` (hoặc `fix/...`) tách từ `dev`, PR vào `dev`
- [ ] Commit theo Conventional Commits
- [ ] Build và test liên quan pass; có test cho logic mới
- [ ] Không sửa file ngoài phạm vi sở hữu; không có file IDE, `.env`, secret hay file generated
- [ ] Cập nhật tài liệu liên quan
- [ ] Đánh dấu rõ nếu đổi hợp đồng API/event (`Events.java`, endpoint công khai)
