# Hoàn thiện website local và gói triển khai VPS

Yêu cầu ngày 11/11/2026: Java 25, React/Vite hiện có, toàn bộ luồng dùng backend và dữ liệu bền vững; chuẩn bị deploy VPS Linux + Docker Compose. Không xóa dữ liệu demo người dùng, không coi adapter local là nhà cung cấp bên ngoài đã hoạt động.

## Các cửa nghiệm thu

- [ ] FE đăng nhập/OTP/refresh/logout/reset thật; không đổi role ở client để tăng quyền.
- [ ] Mọi màn hình nghiệp vụ đọc/ghi API, loading/error/empty; không ghi LocalStorage demo vào DB.
- [ ] Đơn/kho/nợ/chốt kỳ/hoàn, CRM/tuyến/Sales/offline qua browser và API thật.
- [ ] Đóng các thiếu sót M01-M20 trong ma trận, test scope/retry/rollback và quy tắc tài chính.
- [ ] Upload, AI adapter/mock local có nhãn, callback xác thực, job/retry và báo cáo đầy đủ.
- [ ] MySQL thật chạy migration và integration; bảo vệ ledger/snapshot bằng trigger.
- [ ] Local một lệnh build/chạy toàn website; DB và tệp tồn tại sau restart.
- [ ] VPS Compose FE/BE, proxy/TLS, secret bootstrap, health, backup/restore, CI và hướng dẫn.
- [ ] Báo cáo cuối dựa trên kiểm thử thực tế, phân biệt phần cần credentials/domain/máy chủ thật.

Các ô chỉ được đánh dấu sau khi có bằng chứng. Chi tiết yêu cầu gốc tại `docs/backend-requirements.txt`, ma trận M01-M20 tại `docs/BACKEND-BUILD-SUPPLEMENT.md`.
