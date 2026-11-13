# Đối chiếu ảnh, draw.io và code Sales

Trạng thái: **Có** = tìm thấy code/API và kiểm tra được phần mô tả; **Một phần** = có nền tảng nhưng thiếu bước/điều kiện; **Thiếu** = chưa thấy hợp đồng hoặc UI tương ứng trong phạm vi khảo sát. Không dùng nhãn Có để tuyên bố toàn bộ module đã nghiệm thu.

## Hai ảnh trọng tâm

**M1 — Mobile App DMS**, file `2aOboRBDxwbuuXVUUzPE50dcFQSGgKSexHl9AUgy.jpg`: liệt kê cấu hình tiêu chí chấm công; quy trình viếng thăm; chấm công đầu/cuối ngày và lịch sử; lịch tuyến hôm nay; check-in/out GPS + ảnh; đơn giao ngay/giao sau/dùng thử; CTKM/voucher; ý kiến khách hàng dạng ticket; yêu cầu thêm điểm bán; yêu cầu sửa thông tin/toạ độ; báo cáo đi tuyến/bán hàng trên web. Hai mục cấu hình do quản lý thực hiện, app chỉ áp dụng phiên bản đã phát hành; không trao quyền cấu hình toàn hệ thống cho Sales.

**M2 — Tạo đơn 7 bước**, file `2aOboRBDzILUc7tQeHoXoWE3EnepHYJcLvHmI080.jpg`:

1. Từ lượt ghé chọn hoạt động Lên đơn; giữ `visitId/storeId`, không chọn lại khách vô cớ.
2. Chọn giao sau/giao ngay/dùng thử; hoặc đi tắt từ sản phẩm/đơn gần đây. Lựa chọn gần đây là nguồn điền nháp, không phải một loại đơn tài chính.
3. Tìm SKU, xem ảnh/đơn vị, tăng giảm số lượng. Tách đơn vị bán và đơn vị cơ sở.
4. Hiển thị ưu đãi đủ điều kiện, chiết khấu và ghi chú. Giá/điều kiện do server xác nhận; app không tự cấp ưu đãi.
5. Xác nhận người/địa chỉ nhận, lịch giao và điều kiện thanh toán. Không tự gắn thanh toán ngay nếu khách đang có điều khoản khác.
6. Kiểm tra tổng tiền và gửi một lần với idempotency key. Mất phản hồi không tạo đơn mới để gửi lại.
7. Xem danh sách/chi tiết và trạng thái thực hiện. Phân biệt đơn lưu trên máy, đã gửi server, NPP xác nhận và khách thực nhận.

Ảnh này minh hoạ các bước nghiệp vụ, không bắt buộc biến thành bảy màn hình tách rời. App đề xuất gom thành giỏ hàng → điều kiện/giao nhận → kiểm tra/gửi, có lưu nháp và quay lại không mất dữ liệu.

**M3 — Ý kiến khách hàng**, file `2aOboRBE0E4FZQwe85ZC4iJRtiTGbw4G1OreYElM.jpg`: ghé → ghi tiêu đề/nội dung/ảnh → ticket gắn điểm bán/Sales/thời gian → quản lý phân công/phản hồi → đóng sau kết quả xử lý. Lưu ảnh riêng không đồng nghĩa ticket đã được gửi. Sales cần nhìn được người nhận xử lý, hạn và kết quả báo lại khách.

Các ảnh 06–07 (MCP/tuyến), 10–11 (mô hình NSX–NPP–điểm bán, Account/Contact), 12 (giá), 13–20 (đơn, CTKM/voucher), 24–25 (báo cáo) và 26 (360°) cung cấp ngữ cảnh liên quan Sales. Đặc biệt: quản lý tuyến trên web; công việc Sales trên điện thoại; báo cáo cuộn cấp cho quản lý. Không đồng nhất màn hình quản lý tuyến với màn hình đi tuyến.

## Ma trận chức năng

| ID | Nhu cầu / nguồn | Hiện trạng web/API | Khoảng thiếu để thành app Sales |
|---|---|---|---|
| S01 | NSX quản lý Sales, Sales làm việc tại NPP — `network`, `rel-staff`, `fn-sales`; người dùng làm rõ 11/11/2026 | **Một phần**: `SALES.nppId` và `assertRep` xác định phạm vi tác nghiệp NPP; không đủ để kết luận NPP quản lý nhân sự | Bổ sung NSX quản lý và phân công Sales–NPP; giữ scope NPP, không nâng Sales thành quản trị NSX hoặc mở toàn bộ dữ liệu NPP |
| S02 | Hôm nay, MCP — M1, `fn-sales/v4-5` | **Có nền tảng**: routes, stops, weekday, visits/today | Lịch/map một ngày, thứ tự, hẹn giờ, cảnh báo việc dở; frequency phức tạp và tối ưu tuyến chưa có |
| S03 | Chấm công đầu/cuối ngày, lịch sử — M1, `crm5` | **Thiếu**: visit không phải attendance | Ca/ngày làm, chính sách, lý do ngoại lệ, lịch sử; không lấy lần ghé đầu/cuối giả làm chấm công |
| S04 | Nghỉ/thay lịch/bàn giao — `rel-staff` | **Một phần**: `/handovers` chuyển route, PLANNED visits, stores, appointments, OPEN tasks | Chưa có nghỉ/ca, thời hạn thay thế, tiếp nhận bàn giao và toàn bộ việc chưa đóng; giữ lịch sử người làm cũ |
| S05 | Chuẩn bị từ hồ sơ chung — `rel-visit` | **Có nền tảng**: 360 có orders/interactions/appointments/tickets/display | Gom thành bản tóm tắt trước ghé; đưa hẹn/ticket chưa xong lên trước đề nghị mua mới |
| S06 | Lịch sử để người đi thay tiếp tục — `fn-sales/v4-7` | **Một phần**: Sales đọc đơn theo `repId` của chính mình | Sau bàn giao, đơn lịch sử do người cũ tạo vẫn bị lọc; cần chính sách đọc lịch sử điểm được giao, không mở toàn bộ dữ liệu NPP |
| S07 | Check-in GPS + ảnh — M1, `fn-sales/v4-6` | **Một phần**: GPS, khoảng cách server, lý do ngoài bán kính, audit | Không có ảnh/attachment, accuracy, capture time và policy version trong CheckIn DTO; chưa phát hiện GPS giả |
| S08 | Checklist viếng thăm — M1, draw.io | **Thiếu hợp đồng hoàn chỉnh** | Checklist có phiên bản, bắt buộc/tuỳ chọn, bằng chứng, lý do bỏ qua; server xác nhận điều kiện kết thúc |
| S09 | Check-out GPS + ảnh/kết quả — M1 | **Một phần**: endpoint nhận `reason`, kết thúc visit và tạo interaction | Chưa có GPS/ảnh ra điểm, thời lượng/chính sách, trạng thái chưa đồng bộ; kiểm thử xác nhận hiện chỉ cần note |
| S10 | Quan sát tồn điểm bán — `rel-visit`, `fn-sales` | **Một phần**: `/stock-surveys` + lịch sử có API | Chưa có giao diện Sales tích hợp lượt ghé; ghi nguồn/thời gian/đơn vị/người xác nhận; không suy tồn từ lần mua |
| S11 | Trưng bày/ảnh — M1, `fn-plus` | **Có nền tảng**: upload, criteria, audit, manual review, AI mock | Camera mobile, gắn visit, chất lượng ảnh, upload lại và phản hồi; AI mock không đủ bằng chứng để tự thưởng |
| S12 | Đơn tại điểm bán — M2, `fn-sales/v4-8` | **Có nền tảng**: catalog, quote, sales-orders | Tạo đơn còn là màn riêng; chưa lưu liên kết visit trong Create DTO; chuẩn bị giỏ mobile và context xuyên màn |
| S13 | Giao ngay/giao sau/dùng thử — M1/M2 | **Thiếu loại đơn tương ứng**: Create chỉ có storeId/lines | Giao ngay cần nguồn hàng và quyền xuất/nhận; dùng thử cần chính sách hàng mẫu, giá, định mức; không thêm enum UI rồi tự quyết toán |
| S14 | Đơn/sản phẩm gần đây — M2 | **Một phần**: có list/detail đơn của tôi | Chưa có luồng reorder sell-out trên UI; điền nháp mới rồi quote lại, không sao chép giá/khuyến mãi cũ thành giá chốt |
| S15 | CTKM/voucher/quà — M1/M2 và ảnh 12–20 | **Một phần**: pricing theo scope, CTKM và snapshot | Voucher và dòng quà SKU chưa có luồng đủ như ảnh; quyền mở trang policies của Sales cũng chưa có; không hứa tất cả ưu đãi hoạt động |
| S16 | Giao nhận/điều kiện thanh toán/ghi chú — M2 bước 5 | **Thiếu trường trong sales Create DTO hiện tại** | Snapshot địa chỉ/người nhận, lịch cam kết, điều khoản và ghi chú; mặc định lấy hồ sơ nhưng cho kiểm tra theo quyền |
| S17 | NPP xác nhận lượng và lịch — `rel-sellout`, `fn-npp` | **Một phần**: confirm/ship/dispatch/receipt, quantities từng dòng | Cần ngày/hẹn đáp ứng và cập nhật cho Sales; không lấy SUBMITTED = NPP đã nhận đủ hàng để giao |
| S18 | Thực nhận/thiếu/hỏng/bù — `fn-npp`, `fn-outlet` | **Một phần**: các lượng tách, thiếu tạo ticket | Giao bù/đổi trả sell-out chưa hoàn chỉnh; Sales theo dõi, không tự nhận đủ thay khách hoặc tạo đơn thu tiền lần hai |
| S19 | Ý kiến khách hàng/ticket — M3 | **Có nền tảng**: create, comments/attachment, assign/SLA, transition | Gắn visit và ảnh tại chỗ; inbox follow-up; không đánh dấu đóng chỉ vì đã có lời nhắn |
| S20 | Nhu cầu/lý do chưa mua/hẹn lại — `rel-visit` | **Một phần**: interaction/appointment/task có API | Biểu mẫu ngắn theo lượt ghé, reason taxonomy, người nhận và hạn; checkout note tự do chưa thay thế cấu trúc này |
| S21 | Yêu cầu điểm mới — M1 | **Khác luồng**: Sales có CRM_WRITE, tạo store trực tiếp | Bổ sung request → duyệt/trả bổ sung/từ chối → store; chống trùng theo phone/toạ độ theo chính sách |
| S22 | Yêu cầu sửa thông tin/toạ độ — M1 | **Thiếu workflow tương ứng** | Không cho tự thay điểm chuẩn để vượt radius; lưu trước/sau, bằng chứng và người duyệt |
| S23 | Đơn offline — M2 + yêu cầu thực địa | **Có, đã kiểm thử web**: Dexie, scope, per-item ACK, PRICE_CHANGED | Native cần SQLite/queue riêng; không mang Dexie/IndexedDB/service worker vào React Native |
| S24 | Ảnh, lượt ghé, ticket, khảo sát offline | **Thiếu outbox tương ứng** | Queue có phụ thuộc file → upload → command; thời gian thiết bị/server; server xử lý xung đột và quyền bị thu hồi |
| S25 | Báo cáo ngày — `fn-sales/v4-10` | **Có nền tảng**: nháp/nộp, đếm server, không ghi đè đã nộp | Hiện visits gồm CHECKED_IN/COMPLETED; order count gồm mọi trạng thái, amount bỏ CANCELLED. Phải đặt nhãn đúng, không gọi là doanh thu thực nhận |
| S26 | KPI cá nhân, tuyến, coaching | **Một phần**: reports/targets/snapshots và quyền REPORT_READ | Mobile cần KPI đúng scope/kỳ, nguồn và OPEN/CLOSED; không đưa màn quản trị báo cáo đầy đủ xuống điện thoại |
| S27 | Thông báo, cam kết, phối hợp | **Một phần**: in-app/task/outbox | Push/APNs chưa có provider hoạt động; badge app không chứng minh đã gửi push tới thiết bị |
| S28 | Account/Contact và đồng bộ hồ sơ — ảnh 11/26, `crm5` | **Một phần**: hồ sơ store và các section | Cần phân biệt tổ chức, người liên hệ, nhiệm vụ, quan hệ NSX/NPP; tránh nhiều bản sao hồ sơ cho từng Sales |

## Draw.io đã đủ chưa?

Đủ để thống nhất vai trò, mục đích và các tuyến giao dịch chính. Chưa đủ để triển khai/kiểm thử mobile chỉ từ sơ đồ: thiếu trạng thái attendance, điều kiện checklist, dữ liệu bằng chứng, lifecycle offline, xung đột bàn giao, bộ trường giao nhận và quyền chi tiết của từng action. M1 nêu rõ yêu cầu điểm mới/sửa toạ độ; `fn-sales` hiện chưa tách các bước này thành quy trình duyệt. M2 liệt kê loại đơn và điều khoản; draw.io mới ở mức kiểm đơn vị/giá/đáp ứng, chưa định nghĩa đủ các điều kiện này.

Chỉnh sơ đồ bằng bản sao/PR sau khi thống nhất, không sửa trực tiếp file gốc trong lượt khảo sát: thêm swimlane NSX–Sales–NPP–Điểm bán; tách attendance/visit/order; thêm nhánh offline/đợi ACK, không mua/hẹn lại, giao thiếu, request phê duyệt và bàn giao tạm thời. Bản online có liên kết tới trang `help` nhưng không chứa trang đó; bản local 14 trang có `help`. Cần sửa đích link hoặc bổ sung trang khi cập nhật draw.io.

## Điểm tựa trong source

- [Sales.tsx](../../src/features/sales-app/Sales.tsx), [Catalog.tsx](../../src/features/portal-npp/Catalog.tsx), [Orders.tsx](../../src/features/orders/Orders.tsx).
- [navigation.ts](../../src/app/navigation.ts), [offline.ts](../../src/shared/offline.ts), [store.tsx](../../src/shared/store.tsx).
- [RouteFacade](../../backend/src/main/java/vn/synera/route/RouteFacade.java), [RouteController](../../backend/src/main/java/vn/synera/route/api/RouteController.java), [ActorFacade](../../backend/src/main/java/vn/synera/iam/ActorFacade.java).
- [SalesController](../../backend/src/main/java/vn/synera/salesorder/api/SalesController.java), [SalesFacade](../../backend/src/main/java/vn/synera/salesorder/SalesFacade.java), [SalesSyncWorker](../../backend/src/main/java/vn/synera/salesorder/SalesSyncWorker.java).
- [StoreProfileFacade](../../backend/src/main/java/vn/synera/crm/StoreProfileFacade.java), [StoreSections](../../src/features/crm/StoreSections.tsx), [backend gap matrix](../BACKEND-BUILD-SUPPLEMENT.md).
