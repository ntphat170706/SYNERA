# Bằng chứng kiểm tra SYNERA

Kết quả bàn giao hiện tại ở [verification.json](verification.json), liên kết với SHA-256 JAR. Log có tên khác hoặc file failure là lịch sử sửa lỗi; không dùng thay kết quả cuối.

- Backend verify: `backend-final-verify.log`, JUnit XML tại `../backend/target-verified/surefire-reports`.
- MySQL: `mysql80-result.json`, `mysql84-result.json`, `backend-mysql84.log`.
- Chuyển local 8.4: `mysql-lts-migration.json`; nguồn 8.0 được giữ riêng, không tiếp tục ghi song song.
- FE: `frontend-architecture.log`, `frontend-format-check.log`, `frontend-unit.log`, `frontend-final-build.log`.
- Browser: `e2e-final.log`, `e2e-live-result.json`, `design-final.log`, `design-results.json` và ảnh desktop/mobile.
- Runtime: `runtime-smoke.json`; `deploy-validation.json` và `caddy-validation.log` là bằng chứng cấu hình lịch sử, không còn được dùng để chạy ứng dụng.
- Hợp đồng hiện tại: `backend-openapi.json`, [index endpoint](../docs/BACKEND-ENDPOINTS.md).
- [PDF bổ sung](../output/pdf/SYNERA-Bo-sung-huong-dan-build-Backend.pdf), [phạm vi còn thiếu](../docs/BACKEND-BUILD-SUPPLEMENT.md).

GitHub Actions chỉ kiểm tra, không deploy. Các log deploy cũ được giữ như lịch sử; không dùng làm hướng dẫn vận hành. Log có thể chứa dữ liệu fixture nên không commit hoặc công khai log chưa rà soát.
