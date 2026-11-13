# Lộ trình chuyển backend SYNERA sang MongoDB

## Phạm vi đã chốt

- Chuyển toàn bộ backend, không chỉ một module.
- Dùng MongoDB database mới, ban đầu rỗng; không nhập dữ liệu từ MySQL.
- Giữ nguyên MySQL và dữ liệu hiện có trong suốt giai đoạn chuyển đổi. Chỉ bỏ runtime MySQL sau khi toàn bộ cổng nghiệm thu đạt.
- Không đổi API/frontend nếu không có lý do tương thích cụ thể.

## Hiện trạng và rủi ro

Backend hiện dùng Spring Data JPA, Hibernate, JdbcTemplate, Flyway và SQL trực tiếp trên nhiều module. Giao dịch dựa vào `READ_COMMITTED`, khóa hàng, ràng buộc unique/foreign key và trigger MySQL bảo vệ ledger append-only. Báo cáo cũng có truy vấn SQL phân trang và tổng hợp. Chỉ thêm MongoDB driver hoặc đổi URI sẽ không chuyển các luồng này; ứng dụng sẽ còn phụ thuộc SQL hoặc không khởi động.

MongoDB transaction nhiều document cần deployment replica set. Local và test phải dùng replica set thật, không dùng standalone để giả lập transaction. MongoDB không cung cấp trực tiếp những trigger append-only đang dùng; phải thiết kế và kiểm thử cơ chế thay thế trước khi chuyển ledger.

## Các giai đoạn

1. **Thiết kế tương thích**: lập inventory entity/repository/JDBC query/lock/trigger/Flyway; chốt quy tắc ID nội bộ và API, document boundaries, reference/embed policy, Decimal128, versioning/index migration, transaction và ledger immutability. Không sửa runtime trong giai đoạn này.
2. **Nền tảng Mongo**: thêm cấu hình và health/readiness, phiên bản migration/index, replica-set local/test setup và test transaction. Xây unique index và concurrency tests cho idempotency, workspace/scope, token/session và outbox. MySQL vẫn là runtime.
3. **Chuyển module theo aggregate**: thay entity/repository/service cho từng module, giữ Facade/API; chuyển SQL report và background jobs tương ứng; mỗi module có API/integration tests và kiểm thử cạnh tranh, rollback, retry, scope. Không dual-write dữ liệu giao dịch.
4. **Hoàn tất nghiệp vụ dùng chung**: thay audit, idempotency, ShedLock/job state, outbox, bootstrap/seed, migration và kiểm soát ledger; chạy đầy đủ suite trên MongoDB replica set.
5. **Cutover database mới**: khởi tạo MongoDB rỗng, seed dữ liệu local cần thiết theo cơ chế bootstrap có kiểm soát, xác minh health/API/browser flows, sau đó mới gỡ MySQL/Flyway/JDBC dependencies, scripts và tài liệu MySQL runtime. Không copy database MySQL.

## Cổng bắt buộc trước khi gỡ MySQL

- Không còn JPA, JdbcTemplate, SQL-native query, datasource, Flyway, ShedLock JDBC hay MySQL-only runtime/test path.
- Giao dịch đơn/tồn/công nợ, chốt kỳ, idempotency và rollback được chứng minh trên MongoDB replica set bằng integration/concurrency tests.
- Ledger, audit, snapshot và payout không thể bị sửa/xóa qua bất kỳ đường ghi ứng dụng nào; có test phủ repository/API và quyền.
- Unique indexes và tenant/workspace scope được kiểm tra trên dữ liệu cạnh tranh, không chỉ unit test.
- Toàn bộ API contract, Java 25 verification, FE/API E2E và hướng dẫn local được cập nhật, chạy đạt.
- MySQL database và bản sao hiện hữu không bị xóa hay ghi trong quá trình chuẩn bị; yêu cầu hiện tại là không migrate dữ liệu.

## Chưa quyết định trong thiết kế

- Cách giữ tương thích ID số hiện được dùng trong DTO và foreign references, hay đổi API sang ObjectId/string.
- Công cụ migration có versioning cho collection/index và cách kiểm soát schema evolution.
- Cơ chế thực thi append-only ở MongoDB thay thế trigger MySQL, cùng quy trình backup/restore.

Các điểm này cần chốt trong giai đoạn thiết kế trước khi thay đổi persistence. Trạng thái runtime hiện tại vẫn được mô tả trong `backend/ARCHITECTURE.md` và `backend/README.md`.
