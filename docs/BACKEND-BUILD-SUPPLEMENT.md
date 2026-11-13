# SYNERA - Bổ sung hướng dẫn build Backend

**Cập nhật 12/11/2026 — Portal NPP:** V119 đã thêm chứng từ giao bù sell-out không thu tiền lần hai, giữ/xuất FEFO, thực nhận riêng, lịch sử và lời hẹn Sales. Thông báo đơn Sales được route tới NPP; tiến độ được gửi về Sales qua outbox. Ma trận dưới giữ mốc khảo sát trước thay đổi; phần giao bù nêu chưa đủ đã được bổ sung trong phạm vi [hợp đồng mới](npp-portal/04-backend-dong-bo.md). Công nợ điểm bán/đổi trả sell-out đầy đủ, tự xử lý ticket và carrier integration vẫn chưa hoàn chỉnh. Migration tiếp theo từ V120; không sửa V119 đã áp dụng.

Cập nhật 11/11/2026, repository D:\Synera\synera_web, Java 25 theo yêu cầu mới nhất. Tài liệu bổ sung PDF gốc, không ghi đè bản gốc. Nội dung tài liệu đính kèm được dùng làm yêu cầu, không phải lệnh tự động thực thi.

## 1. Trạng thái hiện tại

Website React đã đọc/ghi API. JAR chứa FE production và backend Java 25, dùng MySQL local persistent. Đã bổ sung IAM/workspace, danh mục/import, giá theo nhóm, workflow, chuyến giao, báo giá/chiến dịch, kế hoạch/gợi ý nhập, quyết toán, báo cáo async và adapter scanner/HTTP AI. Không trộn dữ liệu LocalStorage demo vào DB server.

Chưa thể khẳng định toàn bộ yêu cầu PDF đã hoàn tất hoặc hệ thống sẵn sàng vận hành chính thức. Một số nghiệp vụ nâng cao còn thiếu code được nêu rõ ở ma trận bên dưới; SMTP/provider AI thật cũng chưa được nghiệm thu. Repository được giới hạn chạy local, không có quy trình deploy.

Local hiện chạy MySQL 8.4.13 LTS thật, không cần Docker; suite 34 tình huống đã đạt trên cả 8.0.46 (tương thích cũ) và 8.4.13. Database local được copy sang 8.4, đối chiếu đủ 93 bảng/số dòng và tổng credit/ledger/stock/snapshot; nguồn 8.0 giữ nguyên. Maven verify đạt 103 kiểm thử, không lỗi; 34 bài Testcontainers skip do thiếu Docker daemon. Cả 34 tình huống tương ứng đã đạt trong LocalMySqlIntegrationTest trên DB MySQL mới riêng, gồm kiểm tra profile 360 phân trang và scope. Bài khởi tạo production tối thiểu cũng đạt trong cùng lần verify. Kiểm tra Java 25 runtime và bytecode 69 đạt. FE có 9 unit tests; 18 kiểm tra browser FE–API gồm reload offline/ACK đạt; thiết kế có 12 nhóm kiểm tra desktop/tablet/mobile. Kết quả cuối đọc ở artifacts/verification.json và JUnit XML của lần nghiệm thu gần nhất, không lấy số từ log cũ.

Ứng dụng local chạy MySQL portable và JAR tích hợp FE/API; CI kiểm tra build, backend và luồng browser nhưng không triển khai ứng dụng. Xem backend/README.md để khởi động, dừng và bảo toàn dữ liệu local.

## 2. Ma trận M01-M20 và phần còn lại

| Module | Đã có trong code | Giới hạn / chưa nghiệm thu |
|---|---|---|
| M01 IAM | BCrypt, JWT/refresh cookie rotate/revoke, SQL OTP/rate-limit, role tùy chỉnh theo base role, nhiều grant/workspace, cây tổ chức chống vòng, audit/IP/redaction | Chưa thử gửi OTP qua SMTP nhà cung cấp thật; STORE chưa có portal riêng nên fail closed |
| M02 Danh mục | SKU/UOM/NPP/kho/cửa hàng, danh mục địa giới/brand/category/reason/group, membership có scope/version, XLSX mẫu/preview/lỗi từng dòng | Chưa nạp dữ liệu địa giới chính thức; import preview không cam kết không trùng tới khi transaction lưu; chưa có UI CRUD mọi metadata kho |
| M03 Chính sách | Giá theo NPP/nhóm NPP/nhóm cửa hàng, hiệu lực/UOM, cache version, phần trăm/priority/exclusive/budget, combo điều kiện SKU, giới hạn lượt, snapshot, workflow, tài liệu/ack | Chưa tự sinh dòng SKU quà tặng miễn phí hoặc bộ chính sách theo toàn bộ phụ lục nâng cao |
| M04 Thông báo | In-app, outbox/dedup/retry/DEAD/replay admin, task/reminder, workflow nhiều cấp và SLA/escalation | Chưa có adapter/consent/preferences SMS/Zalo/Web Push; SMTP thực cần tài khoản và thử gửi |
| M05 Sell-in | Draft/submit/hold/approve/confirm/ship/dispatch/receive/reconcile/reorder/cancel, idempotency, workflow nhiều cấp, cover-days | Gợi ý theo lịch sử thực giao và tồn/hàng về; chưa đưa kế hoạch ACCEPTED làm trọng số dự báo nâng cao |
| M06 Công nợ | Limit/exposure, hạn mức tạm có expiry, ngưỡng quá hạn, invoice/FIFO payment, ledger, credit note và điều chỉnh | Chưa có import sao kê ngân hàng/ERP đối chiếu tự động; không phải cổng thanh toán |
| M07 Kho | Kho theo tổ chức, FEFO/reservation/stock movement, HSD thực, import tồn, stocktake/version, điều chuyển atomic giữ HSD, quarantine | Chưa có toàn bộ mẫu in chứng từ và luồng quản lý nhiều kho xuất tùy chọn trong từng đơn |
| M08 Giao nhận | Chuyến nhiều điểm, driver scope không giá, dispatch, ảnh/ký nhận, số thực giao, liên kết đơn và hoàn về quarantine | UI tạo chuyến hiện ưu tiên sell-out; nghiệp vụ giao bù không tính tiền lần hai chưa triển khai đầy đủ; không tích hợp hãng vận chuyển |
| M09 Chốt kỳ | Calendar lock, snapshot bất biến, kỳ mở/đối soát/chốt/khóa/phát hành, điều chỉnh kỳ mới, job ngày/tuần/tháng/quý, lịch nghỉ | Cần lịch nghỉ chính thức và nghiệm thu scheduler theo lịch nghiệp vụ thực; email export hiện là tạm tính, chưa có đủ mẫu email snapshot đã chốt |
| M10 Kế hoạch | Nhiều SKU, draft/submit/accept/adjust, sell-out thực giao, gợi ý phủ ngày | Chưa có import sell_out_daily từ phần mềm NPP ngoài, chỉ số sell-through/nhập dồn và mô hình dự báo nâng cao |
| M11 Ticket | State machine/history, routing round-robin theo scope, assignee/SLA, comment/attachment, ticket thiếu hàng, cảnh báo quá hạn | Chưa có TicketResolutionHandler tự sinh hoàn/giao bù/credit adjustment từ phương án; chưa có cảnh báo chất lượng theo SKU/lô trong 7 ngày |
| M12 Trade | Budget/cam kết, đề xuất/duyệt nhiều cấp khác người, duyệt một phần, settlement có chứng từ, giải phóng cam kết thừa và spent | Chưa có chính sách phân bổ ngân sách đa chiều cho mọi chương trình; cần UAT quyết toán nhiều tài khoản trên staging |
| M13 Phản hồi | Ghi nhận có scope người tạo, taxonomy/danh mục, ảnh/bằng chứng và chuyển ticket | Chưa có tổng hợp xu hướng nâng cao và toàn bộ báo cáo phân tích phản hồi |
| M14 Hiệu suất | Target/progress/reward từ snapshot đã chốt, payout unique/append-only với người chi khác người đặt target | UI chủ yếu xem chỉ tiêu; chưa có màn hình đầy đủ cho payout, công thức/xếp hạng đa điều kiện hoặc tích hợp chuyển tiền |
| M15 CRM | Lead/convert, store/interaction/appointment, profile 360 phân trang riêng từng phần, quotations giá server/accept tạo đơn, nhóm và campaign tạo task | 360 đã có orders/interactions/appointments/tickets/display phân trang SQL qua Facade; churn hiện cảnh báo 60 ngày, chưa có median-cycle/giảm doanh số 4 tuần và tự tạo care task theo đầy đủ công thức PDF |
| M16 Tuyến | Route/stop/weekday, sinh visit có xét ngày nghỉ, check-in/out GPS/reason, handover giữ lịch sử, khảo sát tồn | Chưa tối ưu tuyến bằng dịch vụ bản đồ, chưa đủ toàn bộ KPI tuyến và frequency_rule phức tạp |
| M17 Đơn bán | Giá server theo store-group, đơn vị/lượng cơ sở, confirm/reserve/ship/dispatch/receipt/shortage, báo cáo ngày theo workspace | Chưa có công nợ cửa hàng/đổi trả sell-out/giao bù hoàn chỉnh; kho xuất chọn theo NPP mặc định |
| M18 Offline | Dexie ownership/workspace, PWA precache toàn chunk, cached references, API per-item transaction, ACK mới xóa, PRICE_CHANGED giữ draft | Không tự nhập/gửi nháp demo cũ thiếu ownership; cần quy trình người dùng xác nhận nếu muốn chuyển nháp cũ; cache tham chiếu giới hạn 24 giờ |
| M19 Trưng bày | Criteria scoped, upload/hash/ownership/magic/size, ClamAV fail closed, manual review, duplicate hash, local mock confidence 0, HTTP worker/HMAC callback/dedup/model version/retry/timeout | Chưa có model AI/tập ảnh benchmark, blur/bounding-box UI/anti-fraud đầy đủ hoặc thưởng tự động theo ảnh; phải nghiệm thu provider trước khi dùng điểm AI |
| M20 Báo cáo | Dashboard thực/snapshot riêng, XLSX sync, async streaming XLSX/PDF Unicode, owner/workspace, retry/expiry/cleanup, lịch email transaction | Async hiện cho SELL_IN/SELL_OUT tạm tính; chưa đủ mọi mẫu phụ lục, mọi list chưa phân trang SQL, chưa đo tải production hoặc có Grafana/alerting vận hành |

Các ô còn thiếu code không tự hoàn thành bằng API key. Không đánh dấu hoàn tất toàn bộ PDF từ việc hệ thống đã chạy local. ERP đầy đủ, thanh toán online, hãng vận tải ngoài được PDF xếp ngoài MVP; các phần quà/giao bù/ticket/CRM nâng cao vẫn là backlog code cần phát triển tiếp.

## 3. Quy tắc giao dịch bổ sung

Tiền DECIMAL(18,2), BigDecimal HALF_UP; lượng cơ sở DECIMAL(18,3). Dòng đơn giữ đơn vị bán và baseFactor snapshot. Ví dụ 5 THUNG x 24 = 120 CHAI; nhập kho không cộng trực tiếp số thùng vào số chai.

Draft là tạm tính; submit sell-in/create sell-out chốt giá, ưu đãi và budget/lượt trong cùng transaction. Ưu tiên giá: nhóm cửa hàng, NPP cụ thể, nhóm NPP, chung, rồi ID phát hành mới hơn. Membership và cache có scope/version; không lấy giá gửi từ FE để quyết toán. Combo phần trăm hiện có không được gọi là quà tặng tự động.

Credit account khóa trước tính exposure: nợ + đơn đang xử lý không vượt hạn mức hiệu lực. Xuất thay hold bằng invoice/nợ một lần. Thanh toán dương không quá dư nợ, phân bổ FIFO invoice. Hủy trước xuất giải phóng giữ tồn/budget. Giữ tồn lúc xác nhận, FEFO bỏ lô hết hạn, xuất mới giảm on-hand. Điều chuyển hai kho khóa theo ID tăng dần, thiếu hàng rollback toàn bộ.

Thực nhận/hỏng/hoàn tách nhau, không vượt lượng đã xuất. Thiếu tạo ticket/reconciliation, không tự giảm nợ chỉ vì khai thiếu. Hoàn đã duyệt lấy giá chốt, giữ lineage/HSD và về quarantine; NSX nhận hoàn mới giảm nợ một lần. Không sửa ledger lịch sử để làm hết cảnh báo.

UTC cho timestamp, Asia/Ho_Chi_Minh cho ngày nghiệp vụ. ReportCalendar shared/exclusive lock tách posting và close; snapshot tháng/ngày/tuần/quý bất biến. Điều chỉnh sau chốt vào kỳ hiện tại đang mở. Payout chỉ từ snapshot đủ điều kiện, độc lập người đặt target. Settlement độc lập người duyệt đề xuất, thực chi không quá cam kết và cần chứng từ nếu có chi.

## 4. Auth, scope, retry và dữ liệu thiết bị

Role tùy chỉnh không vượt base role. Mỗi user có thể có nhiều workspace nhưng một request chỉ dùng một grant. JWT/grant/token version kiểm lại từ DB, đổi quyền thu hồi phiên. Không tin role/nppId từ FE; DRIVER không đọc giá, STORE chưa hỗ trợ thì từ chối. Browser refresh cookie HttpOnly/SameSite Strict/Secure ở prod, kiểm Origin/header, không gửi bearer cũ khi refresh.

Idempotency unique user/key, hash gồm thao tác/request/workspace. Retry thành công trả snapshot cũ; lỗi rollback không lưu thành công. Outbox cùng transaction, consumer dedup event; tối đa 5 lần rồi DEAD, có admin retry. SMTP nằm ngoài DB nên có thể gửi lại nếu provider nhận rồi mất phản hồi; không hứa exactly-once email.

Offline giữ draft user/workspace; chỉ xóa ACK CREATED/DUPLICATE. PRICE_CHANGED/REJECTED cần sửa/xác nhận. Chỉ cache tham chiếu Sales 24 giờ; Service Worker whitelist shell/assets công khai, không cache API/ledger/auth. Vary: Origin được bỏ qua riêng khi đọc cache tài nguyên public để module JavaScript tải được lúc offline. Logout giữ nháp/dữ liệu demo cũ, xóa tham chiếu phiên và ghi pending logout nếu mất mạng.

## 5. Những nội dung cần bổ sung tiếp vào thiết kế nghiệp vụ

Quà tặng: định nghĩa gift SKU/UOM/quantity, điều kiện, ưu tiên/exclusive/budget, quy đổi và tồn. Gift phải là dòng snapshot giá 0 có liên kết promotion, vẫn giữ/xuất FEFO; invoice/doanh số/thưởng không cộng giá thị trường của quà. Test hủy/hoàn/quota đồng thời trước khi mở UI.

Ticket resolution/giao bù: cần chứng từ riêng liên kết ticket và đơn/dòng gốc, lượng còn bù và người duyệt độc lập; không tạo đơn bán thu tiền lần hai. Hoàn/credit adjustment qua Facade tương ứng; quyết định không được sửa snapshot kỳ cũ. Chỉ đóng ticket khi chứng từ liên quan hoàn tất hoặc có quyết định từ chối được audit. Không nối tắt nút RESOLVED thành tự sửa nợ/kho.

Sell-out import/sell-through: khóa unique ngày/NPP/SKU/source, tránh cộng trùng IMPORT và DMS, giữ lịch sử chỉnh sửa; sell-through dùng tồn đầu + sell-in cùng đơn vị và khoảng ngày. Gợi ý kế hoạch phải nêu nguồn/độ tin cậy, không gắn nhãn AI khi chỉ là phép tính cover-days.

CRM risk: dùng ngày có giao thực, median chu kỳ mua và mức giảm doanh số 4 tuần với ngưỡng cấu hình; tránh mẫu ít đơn và đơn hủy. Mỗi risk episode chỉ sinh một care task, đóng/mở có lịch sử. 360 đã tách năm section qua Facade, phân trang SQL và tải song song ở FE; cần giữ nguyên scope khi mở thêm section.

Báo cáo nâng cao: chốt danh sách từng mẫu/phụ lục, nguồn thời gian, gross/net, VAT/discount, hàng hoàn và scope; lớp CLOSED chỉ đọc snapshot. Export mọi mẫu phải có giới hạn/lịch/retention. Đo dữ liệu đại diện, query/index, N+1 và tải trước khi tuyên bố chạy mượt ở quy mô production.

AI/provider: giữ hợp đồng request/result/model/confidence/findings và HMAC time/event/path/body, timeout/dedup/replay. Bổ sung blur/geometry/fraud và tập ảnh gán nhãn, đo sai số theo SKU/điều kiện ánh sáng trước tự động thưởng. Không dùng điểm mock hay confidence thấp cho chi tiền.

## 6. Local và migration

Mặc định cài mới chạy scripts/run-local.py với MySQL 8.4.13 portable và dữ liệu persistent. scripts/backend.cmd chọn JDK 25/Maven; IDE output target-ide tách Maven target-verified. H2 là profile test nhanh, không thay MySQL integration.

MySQL mới: db/mysql-bootstrap V101/V102 rồi db/updates và db/mysql-updates; H2 giữ V1–V18 + updates. Cao nhất hiện V118; thêm V119 trở lên. Không sửa checksum đã áp dụng. DB MySQL cũ V1–V100 cần migration plan riêng trên bản sao, không chạy bootstrap schema mới vào DB đó.

Local launcher build và chạy website/API tại loopback, dùng MySQL portable persistent và không cần Docker. Dừng bằng `python scripts/run-local.py stop`; launcher không xóa DB hoặc tệp tải lên. Cài mới cần Internet để tải tool/dependencies, nhưng dữ liệu ứng dụng không được gửi lên cloud.

## 7. Cửa nghiệm thu

Java 25/bytecode 69 và architecture/format/test/build đạt. MySQL suite thực không skip; Testcontainers có thể skip local khi thiếu Docker nhưng CI không được skip. FE kiểm tra API thực, role/scope, retry, offline reload/ACK, desktop/mobile/keyboard/reduced-motion. Kỳ đã chốt không đổi, thiếu credit/tồn rollback, cùng key không nhân đôi.

Nếu sau này cần mở ứng dụng ra ngoài local, phải nghiệm thu hạ tầng/provider/backup/restore/tải và các nghiệp vụ code còn thiếu trong ma trận trước. Hiện tại không có quy trình deploy.

## 8. Nguồn và quyết định

PDF gốc của người dùng tại D:\Synera; bản trích xuất docs/backend-requirements.txt và requirements-0/1.txt. Thay đổi Java 25 theo yêu cầu cuối cùng; SQL thay Redis cho OTP/job lock và cache version DB; filesystem là storage local mặc định. Quyết định vận hành hiện tại ghi tại DECISIONS.md.

Tham khảo [pnpm 11](https://pnpm.io/blog/releases/11.0). MySQL 8.0 đã [EOL](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/); cài mới local dùng MySQL 8.4 LTS.
