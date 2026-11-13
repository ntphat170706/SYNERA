# Kiến trúc, API và dữ liệu mobile

## Cấu trúc đã chọn

`apps/sales-mobile` là package Expo riêng, lockfile riêng; không nâng React 18/Vite, không thay router/store của web. Backend Spring Boot/MySQL vẫn dùng chung. React Native không thể import DOM component, CSS, IndexedDB, service worker hoặc navigator.geolocation của web như một implementation native.

Repo có kế hoạch chuyển backend sang MongoDB nhưng chưa chuyển runtime. Lượt này không thực hiện kế hoạch đó và không thay/xoá dữ liệu SQL. App chỉ phụ thuộc HTTP contract; các thay đổi ID/schema phía server cần compatibility plan riêng theo [MONGODB-MIGRATION-PLAN](../MONGODB-MIGRATION-PLAN.md).

Package được nâng lên SDK 58 theo phiên bản Expo Go thực tế trên iPhone của người dùng. Làm theo [hướng dẫn nâng SDK của Expo](https://docs.expo.dev/workflow/upgrading-expo-sdk-walkthrough/); dependency/lockfile mobile riêng, không nâng web root. Không dùng `latest` khi cài lại.

Mốc đầu là online read foundation. Sau yêu cầu clone mockup và test chức năng, app bổ sung các luồng ghi sẵn có trên server; xem [phạm vi hiện tại](07-app-theo-mockup.md). Backend/API không đổi. Chưa triển khai camera/checklist/attendance hoặc outbox cho media và lượt ghé.

## API đang có và có thể dùng lại

Mọi đường dẫn dưới đây có prefix `/api/v1`; payload/validation canonical nằm ở controller/Facade, không lấy ví dụ này thay OpenAPI.

| Chức năng | Hợp đồng hiện có | Giới hạn cần giữ |
|---|---|---|
| Native auth | POST `/auth/login`, `/auth/otp/verify`, `/auth/refresh`; GET `/auth/me`; POST `/auth/logout` | Native dùng token contract. `/auth/session/*` là cookie/browser contract có Origin/header riêng, không nới CORS/CSRF của web cho tiện mobile |
| Workspace | GET `/auth/session/workspaces`; POST `/auth/session/workspace` là browser contract | GET dùng bearer hiện tại; POST còn kiểm Origin/header và refresh cookie. Cần contract đổi workspace native trước khi port; bộ khung chưa cung cấp đổi workspace |
| Danh mục/phạm vi | GET `/organizations`, `/stores`, `/catalog/skus`, `/catalog/skus/{id}/units`, `/catalog/offerings?nppId=…&storeId=…` | Chỉ cache tham chiếu cần thiết theo user/workspace; store scope do server quyết định |
| Hôm nay | GET `/visits/today` | DTO hiện không đủ lịch hẹn giờ/sequence, GPS accuracy hay bằng chứng checkout |
| Check-in | POST `/visits/{id}/check-in`: latitude, longitude, reason | Có Idempotency-Key, scope, radius server. Chưa có photo/accuracy/capturedAt/visit policy |
| Check-out | POST `/visits/{id}/check-out`: reason | Hiện chỉ kiểm CHECKED_IN rồi tạo interaction; không tự cho rằng đã có GPS/ảnh/checklist ra điểm |
| Hồ sơ | GET `/stores/{id}/360`; GET `/stores/{id}/360/{orders|interactions|appointments|tickets|display}?page=…&size=…` | Summary hiện chứa tối đa 20 dòng/section liên quan; app phải dùng endpoints phân trang khi mở thêm |
| Giá/đơn | POST `/pricing/quote`; POST `/sales-orders`: storeId, lines; GET `/sales-orders`, `/sales-orders/{id}` | Server giữ giá, snapshot và UOM. Create chưa có visit, loại đơn/lịch giao/điều khoản/ghi chú như M2 |
| Đồng bộ đơn | POST `/sales-orders/sync`, tối đa 50 item | clientId, idempotencyKey, storeId, createdAt, lines(skuId,uom,qty,clientPrice). CREATED/DUPLICATE khác PRICE_CHANGED/REJECTED |
| Ghi nhận CRM | POST `/interactions`, `/appointments`; complete appointment | Cần giữ scope, giờ/UTC và liên kết điểm; chưa phải checklist phiên ghé |
| Khảo sát tồn | POST `/stock-surveys`; GET `/stores/{id}/stock-surveys` | Đã có server, chưa có trình tác nghiệp Sales trong lượt ghé |
| Ticket | GET/POST `/tickets`, GET `/tickets/{id}`, POST comments/assign/transition | Phân công/đóng theo quyền và điều kiện server; file upload/attachment không phải ACK ticket |
| Trưng bày | attachment upload + `/display-audits` | Dùng attachment thật; mock AI local không là kết quả đánh giá đã xác minh |
| Báo cáo ngày | POST `/sales/daily-report`: day, note, submit | Nộp xong không ghi đè; amount hiện là giá trị đơn, không phải thực nhận/tiền thu |
| Bàn giao | POST `/handovers` | Do quản lý có ROUTE_WRITE; Sales không tự đổi người phụ trách |

Các đường dẫn workspace chưa dùng trong bộ khung phải được xác nhận ở controller/OpenAPI trước khi triển khai, không gọi tên endpoint dự kiến như thể đã tồn tại.

## Quan hệ quản lý NSX và phân công làm việc tại NPP

Người dùng xác nhận: **NSX quản lý Sales, Sales làm việc cho NPP được phân công**. `nppId` hiện tại có thể tiếp tục biểu diễn scope tác nghiệp tại NPP; sự có mặt của trường này không chứng minh NPP là bên quản lý nhân sự. Không bỏ ràng buộc NPP hoặc đổi Sales thành role NSX quản trị.

Đề xuất tách ba ý nghĩa: `managingNsxId` là NSX quản lý; `assignedNppId` là NPP Sales được phân công phục vụ; `fulfillmentNppId` là NPP đáp ứng từng đơn. Đây là tên khái niệm đề xuất, chưa thêm field vào API/DB. Hai NPP sau có thể trùng trong luồng hiện tại; không tự cho chọn NPP khác hoặc hỗ trợ nhiều NPP khi chưa chốt chính sách. Phân công giữ vùng/tuyến/điểm, thời hạn, người giao và version. Không suy ra hợp đồng lao động, đơn vị trả lương hoặc quyền nhân sự của NPP từ scope đăng nhập.

Thay người phải có khoảng thời gian/đầu việc và giữ tác giả cũ. Không sửa `nppId` của chứng từ lịch sử để phản ánh việc đổi phân công; quan hệ quản lý NSX và giới hạn tác nghiệp NPP phải được server kiểm riêng.

App chỉ nhận scope từ phiên server; dữ liệu catalogue, order, visit, task, ticket và file đều kiểm lại cùng phạm vi. Cần ma trận kiểm thử âm NSX khác/NPP khác/Sales khác/người đã hết phân công. Riêng history của điểm được bàn giao cần cho đọc đúng dữ liệu NSX liên quan, không tiếp tục chỉ lọc `order.repId == currentSales` rồi làm mất ngữ cảnh.

## Hợp đồng mới cần thiết kế trước khi mở chức năng

- Attendance (ngày/ca, event vào/ra, chính sách, GPS/ảnh nếu yêu cầu, ngoại lệ/lịch sử/duyệt); tách hoàn toàn Visit.
- Visit policy/checklist có version; evidence liên kết visit, loại ảnh, thời gian chụp, accuracy, ngoài bán kính và lý do; checkout server đánh giá điều kiện.
- Store change request: trường cũ/mới, trùng lặp/bằng chứng, người duyệt, version; chưa tự thay store canonical khi request còn pending.
- Order context/snapshot: visitId, loại đơn được hỗ trợ, người/địa chỉ nhận, cam kết giao, điều kiện thanh toán, ghi chú; không thêm enum giao ngay/dùng thử chỉ ở FE.
- Follow-up/handover: tham chiếu store/visit/order/ticket, trách nhiệm, hạn, trạng thái và lịch sử; chuyển việc chưa xong có kiểm soát.

Đây là **đề xuất**, chưa phải endpoints/DTO được triển khai. Mỗi thay đổi backend cần Facade, migration mới từ version kế tiếp, validation, scope/idempotency và kiểm thử như AGENTS; không sửa migration cũ.

## Session và native storage

Access token chỉ ở RAM. Refresh token dùng [Expo SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/) trên iOS/Android, không AsyncStorage/SQLite hoặc EXPO_PUBLIC biến môi trường. Web preview dùng RAM vì không có Keychain, nên reload cần đăng nhập lại. Không lưu mật khẩu.

Các request cùng hết hạn chia sẻ một refresh. Phản hồi đến muộn sau logout/đổi phiên không được phục hồi danh tính cũ. Không gửi bearer hết hạn tới refresh. Server hiện logout thu hồi theo user; chưa có thiết kế tách revoke từng thiết bị, cần đánh giá trước UAT nhiều thiết bị.

SecureStore, cookie và file hệ thống không cùng loại storage. Cần đo riêng Keychain/keystore thật, account switching, recovery và trạng thái bị thu hồi quyền; browser preview chưa xác nhận được các phần này.

## Offline native

Đã có adapter SQLite native cho nháp đơn và UI gửi lại theo owner/workspace; browser preview dùng storage riêng, không thay SQLite native. ACK thành công lưu trạng thái ACKNOWLEDGED, không xoá cả dữ liệu; giá đổi yêu cầu người dùng xác nhận giá server mới trước gửi lại cùng mã. Đọc [Expo SQLite](https://docs.expo.dev/versions/latest/sdk/sqlite/). Chưa có cache danh mục bền vững, tự đồng bộ nền hoặc outbox media; GPS/ticket/khảo sát/lời hẹn hiện cần mạng.

Envelope cần clientId, idempotencyKey, ownerId, workspaceKey, kind, occurredAt, local sequence, schema/policy version, dependencies và payload. File có local URI, hash, upload state và server attachmentId sau ACK. Không nhét base64 ảnh vào danh sách đơn.

Luồng: lưu local atomically → chờ điều kiện/phụ thuộc → upload file nếu cần → gửi command → ACK đúng clientId + orderId → đánh dấu thành công. PRICE_CHANGED/REJECTED/timeout/response lạ giữ dữ liệu để xử lý. Không gửi nháp của tài khoản/workspace cũ; không xoá cả database khi logout.

Queue GPS/ticket/checklist không thể giả dùng endpoint sync đơn. Cần server contract và chính sách tiếp nhận sự kiện tới muộn. Chưa có mạng thì hiển thị “đã lưu trên thiết bị”, không hiển thị “đã check-in máy chủ”. Mốc giờ thiết bị không đủ để chống gian lận; giữ cả thời gian server nhận và bằng chứng chính sách.

## Chạy local và giới hạn thiết bị

Web/API giữ loopback `127.0.0.1:8080`. Theo yêu cầu iPhone, Metro mở LAN ở cổng 5173; native lấy API base từ `Constants.expoConfig.hostUri`, nối qua bridge Metro cố định backend loopback. Bridge cho GET/native auth và danh sách POST Sales cụ thể; chặn browser-session và chuyển trạng thái duyệt/xuất/nhận của NPP. Bỏ cookie/Origin ở bridge phát triển, không sửa CORS backend. Static web export cần reverse proxy tương đương.

Điện thoại thật không dùng loopback của máy tính. `npm run iphone` chuẩn bị QR native và Safari ở `/phone`; máy tính/iPhone cùng LAN, không mở DB/backend hoặc cổng router ra Internet. Đây là cấu hình phát triển local, không dùng bridge Metro làm reverse proxy production.

Windows chạy được Expo/web và có thể dùng iPhone thật qua Expo Go khi SDK/network tương thích. iOS Simulator local cần Mac/Xcode; có lựa chọn remote nhưng không phải simulator local Windows. Camera/GPS/background/ATS, quyền local network và build ký ứng dụng cần kiểm trên môi trường đích; chưa có IPA đã nghiệm thu trong lượt này.
