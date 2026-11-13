# Đóng góp cho Animal Locket

## Nhánh

| Nhánh | Vai trò | Ai được đẩy |
|---|---|---|
| `main` | Bản phát hành, luôn triển khai được | Chỉ nhận PR từ `dev` khi release |
| `dev` | Nhánh tích hợp, luôn build xanh | Chỉ nhận PR (squash merge) |
| `feature/<ID>-<slug>` | Một issue, một nhánh | Người được giao issue |
| `fix/<ID>-<slug>` | Sửa lỗi | Người được giao issue |

- `<ID>` là mã issue có tiền tố mảng: `PLT-07`, `IDN-12`, `MOB-03`... `<slug>` viết thường, nối bằng `-`.
- Luôn tách nhánh từ `dev` mới nhất: `git fetch && git switch -c feature/PLT-07-module-poms origin/dev`.
- Không đẩy thẳng lên `dev`/`main`, không force-push lên nhánh người khác.
- Nhánh bị lệch `dev`: `git rebase origin/dev` (nhánh của mình) rồi `git push --force-with-lease`.

## Commit

Theo [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <mô tả ngắn, mệnh lệnh, ≤ 72 ký tự>

<thân: giải thích vì sao, không phải làm gì>

Closes #<số issue>
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `build`, `perf`.
- `scope`: module hoặc mảng (`common`, `outbox`, `gateway`, `user`, `post`, `mobile`, `plt`...).
- Ví dụ: `feat(common): add PublicIds generator`, `fix(media): check Content-Type on complete`.
- Thay đổi phá vỡ hợp đồng API/event: thêm `!` (`feat(common)!: ...`) và mục `BREAKING CHANGE:` trong thân.

## Phạm vi sở hữu

Mỗi mảng chỉ sửa thư mục của mình (xem [`.github/CODEOWNERS`](.github/CODEOWNERS)). File dùng chung do PLT quản lý:
root `pom.xml`, `docker-compose.yml`, `infra/config-repo/`, `libs/common/.../event/Events.java`, `.env.example`.
Cần đổi các file này: mở issue gắn `area:plt` thay vì sửa trong PR của mảng khác.

## Pull request

1. Mở PR vào `dev`, tiêu đề theo Conventional Commits, thân có `Closes #<issue>` và điền [PR template](.github/pull_request_template.md).
2. Trước khi mở PR, chạy local:
   - Backend: `mvn -B verify` (hoặc `mvn -B -pl <module> -am verify` cho module đang sửa).
   - Mobile: `cd mobile && flutter analyze && flutter test`.
3. PR nhỏ, một mối quan tâm. Không kèm file IDE, `.env`, secret, file generated (`target/`, `build/`, l10n sinh tự động).
4. Có test cho logic mới; test không dùng credential thật.

## Review

- Ít nhất **1 approve** từ code owner của thư mục bị sửa; CI phải xanh.
- Bắt buộc người thứ hai review khi chạm: DTO công khai, `LocationPrivacy`, `CommonAutoConfiguration`,
  `InternalTokenFilter`, `IdentityFilter`, billing, hợp đồng event (`Events.java`).
- Reviewer kiểm: đúng phạm vi, có test, không lộ dữ liệu riêng tư (chỉ `publicId`, không `sub`/email/SĐT),
  lỗi trả qua `ApiException`, event phát qua `OutboxService` trong transaction.
- Người mở PR tự resolve comment sau khi sửa; merge bằng **Squash and merge**, xoá nhánh sau khi merge.

## Bí mật

Không commit `.env`, khoá, token, file Firebase. Lỡ commit: báo ngay cho PLT để thu hồi và đổi khoá
(xoá khỏi lịch sử không đủ — coi như khoá đã lộ).
