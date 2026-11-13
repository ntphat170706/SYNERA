# Kiến trúc backend SYNERA

Web NSX: `report.ManufacturerFacade` tạo read model mạng lưới và NPP360 qua các Facade có scope; `catalog.manufacturerContext` chặn installation nhiều NSX khi chưa có ownership/grants. `route.networkSchedule/networkVisits` cung cấp lịch MCP và lượt thực trong kỳ. Không tạo DB hoặc ledger khác; không sửa schema/migration đã dùng. `PricingFacade.appliedBenefits` đọc số tiền ưu đãi đã lưu sau kiểm scope đơn; `lineTotal` là trường bổ sung từ snapshot dòng. [Phạm vi](../docs/nsx-portal/01-nguon-va-man-hinh.md).

Portal NPP 12/11/2026: V119 bổ sung sales replacement trong module `salesorder`, gọi InventoryFacade/TaskFacade/CrmFacade thay vì truy cập repository module khác. Khóa đơn gốc trước chứng từ giao bù để kiểm soát phần thiếu. Thông báo dùng outbox cùng giao dịch; ba frontend chỉ đọc contract chung. Xem `../docs/npp-portal/04-backend-dong-bo.md`. Version đã áp dụng cao nhất V119, migration tiếp theo V120 (các mốc V118 bên dưới là lịch sử trước đợt này).

Java 25 LTS là runtime và target biên dịch hiện tại. Một Spring Boot app, một DB giao dịch; chưa tách microservice.

## Ranh giới module

`iam`, `catalog`, `policy`, `notify`, `sellin`, `credit`, `inventory`, `delivery`, `closing`, `demand`, `ticket`, `trademkt`, `performance`, `crm`, `route`, `salesorder`, `display`, `report`, `integration`, cùng `common`, `config`.

M01-M20 là phân hệ nghiệp vụ, không phải 20 service deploy riêng. Các phân hệ ghép theo cấu trúc PDF: M12/M13 trong `trademkt`, M17/M18 trong `salesorder`.

Trong mỗi module: `api` chứa controller/DTO; Facade hoặc service ở package module; `domain` và `repo` là nội bộ. Module khác gọi Facade, không import entity/repository. `ModuleArchitectureTest` kiểm tra ranh giới này. Một số controller CRUD nhỏ hiện chứa thao tác repository trong cùng module; khi mở rộng cần tách service, không sao chép quy tắc sang controller khác.

## Luồng giao dịch

1. Security xác minh chữ ký/issuer/expiry JWT, đọc trạng thái tài khoản và token version trong DB; method security kiểm tra quyền.
2. Facade áp scope NPP/vùng/nhân viên và kiểm tra quyền với ID đã truyền. Không tin `nppId`, giá hay vai trò gửi từ FE.
3. `IdempotencyService` khóa actor, kiểm tra unique `(user_id, idem_key)`, hash gồm thao tác và request, trả snapshot khi retry đúng nội dung.
4. Transaction `READ_COMMITTED` kiểm tra trạng thái, khóa chứng từ, credit account, promotion và các lô theo thứ tự ổn định. Ngoại lệ nghiệp vụ rollback mọi thay đổi chứng từ/tồn/công nợ/outbox.
5. Ghi audit và outbox cùng transaction. Worker lưu processed-event để chống tạo thông báo trùng; retry tối đa 5 lần rồi DEAD.

Giữ quy ước khóa: actor → calendar guard → chứng từ → credit account → promotion/stock theo thứ tự ổn định. Khi thêm nghiệp vụ có thứ tự khác phải review deadlock và thêm kiểm thử đồng thời. Transaction lồng nhau không được thay đổi isolation tùy ý.

`ReportCalendar` dùng shared lock khi ghi nhận và exclusive lock khi tạo/chốt kỳ. Ngày mới tự tạo kỳ DAY khi phát sinh giao dịch. Khi chốt, các giao dịch đang ghi nhận phải hoàn tất trước khi chụp snapshot. Không update snapshot; điều chỉnh tạo ledger ở kỳ mở hiện tại. H2 không thay thế kiểm chứng lock trên MySQL.

## Bất biến dữ liệu

- Tiền BigDecimal, 2 số lẻ, HALF_UP; lượng kho dùng đơn vị cơ sở, tối đa 3 số lẻ. Dòng đơn giữ đơn vị bán và `baseFactor` đã chốt.
- Giá chốt lúc submit sell-in / tạo sales-order. Chương trình chỉ phân bổ discount lên dòng đủ điều kiện; giữ ngân sách cùng transaction, giải phóng khi hủy trước xuất kho.
- Giữ tồn lúc xác nhận số lượng, FEFO bỏ qua lô hết hạn; xuất kho mới giảm on-hand. `qtyOrdered`, `qtyConfirmed`, `qtyShipped`, `qtyReceived`, hỏng và hoàn lưu riêng.
- Nhận NPP truy ngược phân bổ lô NSX để giữ hạn dùng. Hoàn hàng không đưa vào tồn bán được: nhập kho QUARANTINE và ghi giảm nợ một lần theo giá đã chốt.
- Credit ledger, stock movement, audit, lịch sử trạng thái và snapshot chỉ thêm. MySQL V102/V110 chặn UPDATE/DELETE; H2 không cài trigger này.
- Sales-order không tăng nợ NSX. Sales xem dữ liệu cá nhân trong NPP; sau bàn giao vẫn đọc được đơn cũ của mình nhưng không còn đọc hồ sơ cửa hàng đã chuyển.
- Offline sync mỗi item `REQUIRES_NEW`; một item sai không rollback item trước. Giá đổi trả PRICE_CHANGED, cần xác nhận lại, không tự chấp nhận giá cũ.

## Migration, runtime và tích hợp

MySQL mới dùng `db/mysql-bootstrap` (V101 schema gốc, V102 append-only), `db/updates` và `db/mysql-updates` (trigger riêng). H2 giữ lịch sử `db/migration` V1–V18 rồi `db/updates`. Bản MySQL bootstrap quote bảng `lead` vì đây là từ khóa MySQL 8. Không sửa checksum migration đã triển khai; thêm version lớn hơn tất cả profile. Hiện cao nhất V118, thay đổi tiếp bắt đầu V119.

Bootstrap MySQL này dành cho DB mới. DB MySQL cũ từng triển khai V1–V100 cần lập đường nâng cấp riêng và thử trên bản sao trước khi đổi locations; không chạy bootstrap CREATE trên schema cũ. DB local hiện dùng V101 trở đi. Không Flyway clean hoặc ddl-auto update/create; Hibernate validate.

Local cài mới mặc định MySQL 8.4.13 portable tại 3308; DB 8.0 cũ ở 3307 được giữ riêng cho tới khi copy/đối chiếu bằng scripts/migrate-local-mysql-lts.py. Bản local hiện đã chuyển đủ 93 bảng sang 8.4, giữ nguồn cũ. Local chạy MySQL portable, một JAR chứa FE/API; launcher scripts/run-local.py giữ dữ liệu persistent và dừng graceful qua signal chỉ có trong profile local. H2 dùng để test nhanh. Maven/m2e có output riêng để IDE không ghi đè class Java 25 version 69.

Ứng dụng hiện chỉ vận hành local qua `scripts/run-local.py`: MySQL portable, một JAR chứa FE/API và bind loopback. CI chạy kiểm thử tự động nhưng không triển khai ứng dụng. Không dùng production profile để khởi chạy dịch vụ public.

IAM có role tùy chỉnh hẹp hơn base role và nhiều grant/workspace. Mỗi request chỉ dùng một workspace, revalidate grant từ DB. Đổi quyền thu hồi token/refresh. Browser refresh là cookie HttpOnly, kiểm Origin và header riêng; token không lưu LocalStorage. Store portal chưa có luồng riêng nên fail closed; DRIVER giới hạn delivery/attachment, không trả giá thương mại.

Cache giá giữ DTO bất biến 30 giây, key gồm NPP/store/group/ngày/version DB; phát hành giá tăng version, thay nhóm thay key. Không dùng Redis làm nguồn quyền/giá chốt/OTP; SQL OTP/rate-limit và ShedLock vẫn hoạt động. Đây là khác biệt có chủ đích với gợi ý hạ tầng Redis trong PDF.

Upload filesystem private hoặc S3-compatible có ownership/hash/magic-byte/size và ClamAV; fail closed ở production khi scanner lỗi. Local ghi rõ chưa quét. Presigned object được chuyển sang key mới sau khi scan để URL PUT cũ không sửa file đã chấp nhận.

AI manual mặc định; local mock confidence 0 luôn cần duyệt. HTTP provider HTTPS có timeout/retry/job, callback HMAC thời gian/event/path/body và dedup bất biến. Chưa có model/provider benchmark thực tế. Không lấy kết quả mock để tự thưởng.

Report async dùng keyset SQL 500 dòng, streaming XLSX/PDF Unicode, scope chủ sở hữu/workspace kiểm lại khi tạo và tải. File hết hạn sau 24 giờ có cleanup. Lịch tạo job và cập nhật next_run trong cùng transaction/row lock; email có thể gửi lại khi provider đã nhận nhưng app mất phản hồi. Không hứa exactly-once SMTP. Audit/outbox/settlement/payout/policy ack có trigger append-only MySQL.

FE runtime hiện đã đọc/ghi API; Dexie chỉ giữ nháp và tham chiếu Sales có scope. LocalStorage demo cũ không chuyển vào DB server. In-app/SMTP có adapter; SMS/Zalo/push/ERP/online payment/transport ngoài chưa có provider hoạt động. Xem BACKEND-BUILD-SUPPLEMENT để biết phạm vi còn thiếu.

## Flow cho tác vụ sau

Đọc root `AGENTS.md`, tài liệu này, `docs/BACKEND-BUILD-SUPPLEMENT.md`, phần PDF liên quan → chốt trạng thái/quyền/scope/giá/đơn vị → thêm migration → sửa module qua Facade → DTO/OpenAPI → kiểm thử rollback, retry và tenant → chạy Java 25 verify → cập nhật docs.

Không tuyên bố hoàn tất production nếu còn MySQL test bị skip, adapter chưa cấu hình hoặc FE chưa tích hợp. Khi chuyển một luồng FE sang API, chuyển cả đọc và ghi cùng luồng, giữ nhãn demo cho các luồng còn lại, không hợp nhất dữ liệu demo vào sổ thật.
