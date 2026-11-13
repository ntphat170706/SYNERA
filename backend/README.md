# SYNERA Backend — Java 25

Spring Boot 3.5.16, MySQL 8, Flyway, JWT và modular monolith. Frontend tại root đã chuyển các màn hình nghiệp vụ sang API. JAR đóng gói cả website và API trên cùng origin.

## Chạy website trên Windows

Từ `D:\Synera\synera_web`, cần Python, Node.js 22+ và dependencies FE (`pnpm install --frozen-lockfile`). Công cụ portable nằm trong `.tools`, không cài service hệ thống:

```powershell
python scripts/bootstrap-backend.py
python scripts/bootstrap-mysql.py
python scripts/run-local.py start
```

Mở **http://127.0.0.1:8080**. Launcher build FE, đóng gói Java 25, chạy migration bằng tài khoản riêng rồi khởi động app. Cài mới dùng MySQL 8.4.13 LTS trên `127.0.0.1:3308`. Lần đầu tải công cụ/dependencies cần Internet.

```powershell
# Dừng an toàn, giữ database và upload.
python scripts/run-local.py stop
# Chỉ dùng sau khi đã build JAR mới thành công.
python scripts/run-local.py start --no-build
```

DB LTS ở `.tools/mysql84/data`; upload/báo cáo ở `backend/.data`. DB 8.0 cũ tại `.tools/mysql/data` được giữ nguyên; launcher tiếp tục chọn DB cũ nếu chưa có bản chuyển đổi được xác minh. Lựa chọn sau chuyển đổi lưu tại `.tools/local-mysql-choice.json`. Không xóa các thư mục dữ liệu để sửa lỗi. Launcher từ chối khi cổng 8080 đã phục vụ backend khác.

| Tài khoản local | Vai trò |
|---|---|
| admin@synera.local | Quản trị NSX, OTP local |
| npp@synera.local | Chủ NPP Minh Phát |
| sales@synera.local | Sales của Minh Phát |
| npp-b@synera.local | Chủ NPP Hoàng Gia, tenant khác |
| accountant@synera.local | Kế toán NSX, OTP local |
| manager-north@synera.local | Quản lý vùng NORTH, OTP local |

Mật khẩu khởi tạo local: **`SyneraLocal2026!`**, chỉ áp dụng cho user mới. OTP local hiển thị khi đăng nhập, không phải email đã gửi. Tài khoản và dữ liệu mẫu chỉ dành cho instance local. Scheduler là tài khoản nội bộ không đăng nhập HTTP.

API `/api/v1`; Swagger `/swagger-ui.html`; OpenAPI `/v3/api-docs`; health `/actuator/health`, cùng origin local ở trên.

## Phát triển FE riêng

Giữ backend chạy 8080; terminal khác chạy `pnpm dev`. Vite tại 5173 proxy API về backend; `pnpm build` rồi `pnpm preview` dùng 4173. Session dùng access token trong bộ nhớ và refresh cookie HttpOnly. Workspace chỉ xuất hiện khi server cấp grant tương ứng.

## Kiểm tra Java 25 và MySQL

```powershell
.\scripts\backend.cmd verify
python scripts/format-backend.py --check
```

Có Docker: MySqlIntegrationTest dùng Testcontainers; CI bắt buộc suite này không skip. Không có Docker trên Windows: mở MySQL portable ở terminal riêng và chạy cùng suite trên DB test mới:

```powershell
# Terminal A, nếu MySQL chưa chạy:
python scripts/local-mysql.py start
# Terminal B:
python scripts/local-mysql.py prepare-test
.\scripts\backend.cmd "-Dsynera.mysql.local=true" "-Dsynera.mysql.config=D:/Synera/synera_web/.tools/mysql84/test.properties" clean verify
python scripts/require-mysql-tests.py --local
```

`prepare-test` tạo DB `synera_test_<random>` riêng, không xóa DB ứng dụng. Không tái dùng DB test đã chạy vì suite có giao dịch đã chốt. JUnit reports: `backend/target-verified/surefire-reports`. Java 25 class-file version phải là 69. Maven profile `ide-output` tách output m2e/VS Code vào `target-ide` để tránh ghi đè class Maven.

H2 vẫn dành cho kiểm tra nhanh qua profile test hoặc launcher cũ `scripts/start-backend.cmd`; không thay thế nghiệm thu MySQL. Cách chạy website mặc định là MySQL ở trên.

## Chuyển DB local 8.0 cũ sang LTS

MySQL 8.0 đã EOL; cài mới local dùng 8.4. Không mở file data 8.0 trực tiếp bằng binary 8.4. Helper chỉ copy bằng logical dump sang DB đích trống, kiểm tra mọi bảng/số dòng và tổng credit/ledger/stock/snapshot, rồi mới chọn 8.4. Nguồn và dump đều được giữ lại.

```powershell
python scripts/bootstrap-mysql.py
# Khởi động 8.4 riêng nếu chưa chạy, giữ terminal này:
python scripts/local-mysql.py start --lts
# Terminal khác, 8.0 cũ vẫn phải đang chạy:
python scripts/run-local.py stop --keep-mysql
python scripts/migrate-local-mysql-lts.py
python scripts/run-local.py start --no-build
```

Helper từ chối nếu app còn chạy hoặc DB đích có bảng. Đọc `artifacts/mysql-lts-migration.json` sau khi thành công. DB 8.0 giữ lại là bản tại thời điểm chuyển; nếu 8.4 đã có giao dịch mới, không quay về DB cũ bằng cách đổi file lựa chọn. Cần kế hoạch khôi phục dữ liệu mới. `--legacy` trên bootstrap/local-mysql chỉ dùng cho tương thích 8.0, cổng 3307. Không tự nhập dữ liệu H2 hoặc LocalStorage vào DB mới.

## Chế độ vận hành

Repository chỉ hỗ trợ chạy local; các cấu hình hosting, Docker deploy và tunnel online đã được gỡ. Từ thư mục gốc, dùng `python scripts/run-local.py start` để chạy website/API tại `http://127.0.0.1:8080`; dùng `python scripts/run-local.py stop` để dừng an toàn. Dữ liệu MySQL và tệp tải lên được giữ trên máy.

Local OTP và AI mock không phải dịch vụ bên ngoài thật. SMS/Zalo/push, ERP, thanh toán online và vận tải bên thứ ba chưa có provider hoạt động.

Xem [kiến trúc](ARCHITECTURE.md), [API](../docs/BACKEND-API.md), [phạm vi và giới hạn](../docs/BACKEND-BUILD-SUPPLEMENT.md). Không chạy lại script sinh entity để ghi đè code đã sửa.
