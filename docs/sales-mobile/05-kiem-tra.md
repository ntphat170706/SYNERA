# Kiểm tra và giới hạn bàn giao

Baseline repo `d72d98e`, nhánh `feat/sales-mobile-preparation-20261111`. Mốc chuẩn bị 11/11 chỉ thêm package Expo/tài liệu. Cập nhật 12/11 bổ sung API thống kê Sales chỉ đọc và luồng app theo góp ý; không migration, không sửa transition/ledger, web source hoặc lockfile root. Các kết quả mốc đầu được giữ bên dưới, thay đổi mới tách riêng.

## Cập nhật kiểm tra 12/11/2026

- Người dùng xác nhận mở được app native trên iPhone sau nâng SDK 58. Manifest LAN trả SDK 58/Light Mode; việc mở được app không thay bằng chứng kiểm thử camera, VoiceOver hay SQLite trên thiết bị.
- TypeScript mobile và **20/20 unit tests đạt**: session/refresh/logout, multipart upload, giỏ/UOM/ACK/đổi giá, đồng hồ từ timestamp qua nửa đêm, ngày Việt Nam và phân tích yêu cầu đặt hàng có trường hợp mơ hồ/sai đơn vị.
- Expo dependency check đạt; **Expo Doctor 20/20 đạt**; xuất web và bundle iOS đạt sau thêm ảnh đại diện/gradient/image picker.
- Backend Java 25 `verify` đạt: **70 test chạy đạt, 70 test MySQL được skip theo cấu hình suite**. Không gọi 140 là số test đã chạy đạt. Integration có kiểm summary theo scope, số thực nhận/chiết khấu và quyền âm. Runtime MySQL local được kiểm bổ sung qua API với DB đang chạy; đây không thay suite lock/concurrency MySQL đã skip.
- Root Vitest 9/9 và check:architecture đạt. Lỗi format baseline 52 file vẫn giữ riêng; file mới/chỉnh mobile có kiểm format riêng.
- Browser workflow trước: 10 nhóm đạt — quote/UOM, mất phản hồi rồi ACK duplicate không tạo trùng, reorder, interaction/ticket/survey/lời hẹn/báo cáo, không tràn 390px.
- Browser home mới: **7 nhóm đạt**, gồm số liệu Hôm nay/Tháng này đúng actor, tên Quân Đào và ảnh, check-in đưa lượt lên trước, đồng hồ tăng và giữ qua reload/login, quick actions, 375/390/430px/reduced motion/light dưới dark preference, checkout loại khung đang ghé. Review độc lập chấp nhận ở phạm vi Chromium preview sau sửa target segment 44pt và chụp lại dữ liệu đã tải xong.
- Browser AI: upload ảnh thực → display-audit → worker mock → điểm đề xuất/chờ xác nhận; không tự duyệt finalScore. Yêu cầu văn bản → SKU/UOM/lượng → giỏ → quote server, không tự tạo đơn. Không có nhãn Demo theo yêu cầu, không tràn ở 375/390/430px. Chất lượng nhận diện AI thật chưa được kiểm vì provider đang là mock.
- Nguồn chân dung copy đúng SHA256; đổi tên đúng một dòng local, digest các cột khác không đổi. Tài liệu/ảnh gốc không bị ghi đè; hồ sơ kiểm tra local được Git bỏ qua.

Kết quả máy đọc mới: `.tools/sales-audit/home-redesign-results.json`, `ai-workflow-results.json`, `mobile-workflow-results.json`; screenshot có hậu tố `web`, không phải screenshot iOS Simulator. Giới hạn chức năng ở [trang chủ/lộ trình](08-trang-chu-va-lo-trinh.md) và [luồng AI](09-tro-ly-va-trung-bay.md).

## Mốc kiểm tra nền 11/11/2026

## Runtime local

- Web và API: **http://127.0.0.1:8080**. Launcher root build Vite/TypeScript và JAR thành công, dùng Java 25 và MySQL 8.4 portable trong repo mới. Không thay hệ thống bằng H2. Chưa chạy bộ backend `verify`/MySQL integration riêng; build JAR của launcher bỏ qua backend tests.
- Preview Expo: **http://localhost:5173**, Metro nối API loopback qua bridge phát triển cùng origin. Đây là Chromium/web, không phải iOS Simulator.
- DB mới giữ lại dữ liệu kiểm thử có tiền tố `E2E`/`MOBILE-AUDIT`. Không reset DB, không đụng DB/source dự án `synera-web` cũ.
- Runtime đang dùng SQL theo code hiện tại. Quyết định MongoDB trong repo là đích chuyển đổi riêng, không thực hiện migration trong mốc này.

## Kết quả thực tế

| Kiểm tra | Kết quả | Ý nghĩa/phạm vi |
| --- | --- | --- |
| Root `npm test` | 9/9 đạt | Bộ test hiện có của web |
| Root `npm run check:architecture` | Đạt | Không khởi tạo lại web/router/store |
| Root build trong launcher | Đạt | TypeScript/Vite và đóng gói JAR |
| Root `npm run test:e2e` | 18 kiểm tra đạt | Ba tài khoản; quyền/phiên; sell-in và sell-out, xác nhận/xuất/giao/nhận; credit; GPS; CRM 360°; nháp offline và ACK |
| Root `npm run test:design` | 12 kiểm tra đạt | Desktop/tablet/mobile, bảng/tìm kiếm, giảm chuyển động; không JS exception |
| API Sales khảo sát bổ sung | 8 kiểm tra đạt | Checkout trước check-in bị từ chối; ngoài bán kính cần lý do; retry check-in; checkout hiện chỉ cần note; số báo cáo từ server; Sales không tự duyệt; giá cũ trả `PRICE_CHANGED`; identity đúng scope hiện tại |
| Đọc màn web Sales | 9 route | Không tràn ngang ở viewport khảo sát, không JS exception |
| Expo `npm run check` | Đạt | TypeScript của package độc lập |
| Expo `npm test` | 9/9 đạt | Chặn sai role; refresh single-flight; kết quả refresh/OTP muộn sau logout; logout khi mất mạng; lỗi SecureStore có thể retry; chặn đọc lúc logout; ownership và ACK từng bản ghi |
| Expo `npx expo install --check` | Đạt | Dependency phù hợp SDK đã chọn |
| Expo preview với API thật | 9 kiểm tra đạt | Sales login; 4 tab; chi tiết/quay lại; đơn theo scope; dark mode; phiên RAM trên web; logout; từ chối NPP; bridge chặn browser-session và lệnh ghi nghiệp vụ |
| Expo `npm run export:web` | Đạt | Bundle web; phục vụ tĩnh riêng cần reverse proxy tương đương Metro bridge |
| Expo `expo export --platform ios` | Đạt | Bundle JavaScript/Hermes iOS; **không phải IPA, build Xcode hoặc bằng chứng đã chạy native** |
| Format file mới trong Expo | Đạt | Prettier cho source/test/config của package mới |

Kết quả E2E chứng minh hành vi đã chạy trên instance local, không có nghĩa mọi chức năng trong ảnh/draw.io đều được triển khai. Tài khoản Sales hiện có scope tác nghiệp NPP theo server; scope này không xác định bên quản lý nhân sự. Theo xác nhận của người dùng, NSX quản lý Sales và Sales làm việc tại NPP. Kiểm tra cross-NPP ở runtime khảo sát bổ sung không chạy vì seed không có điểm bán NPP thứ hai phù hợp; không dùng kết luận code review thay bằng chứng runtime cho trường hợp này.

## Vấn đề còn mở

- `npm run format:check` ở root thất bại với **52 file có sẵn từ baseline**. Không mass-format source cũ hoặc nới check để che lỗi. Package mới có kiểm tra format riêng.
- `npm audit` package mobile báo **4 mục moderate** trong chuỗi Expo/fingerprint/glob/minimatch; phương án tự động đề nghị nâng major lên SDK 58. Chưa dùng `--force`; cần triage và kiểm tra lại SDK/Expo Go trước phát hành. Không kết luận app đã qua đánh giá bảo mật sản xuất.
- Preflight origin 5173 tới backend thực tế trả CORS 403 dù source có cấu hình origin đó. Preview dùng bridge Metro giới hạn GET/native auth, không thay cấu hình phân quyền hoặc bật CORS toàn cục. Native không dựa vào browser cookie session.
- Windows chưa chạy iOS Simulator. Chưa kiểm tra Keychain/SecureStore trên máy thật, camera/GPS chính xác, quyền bị từ chối, background/kill/relaunch, Dynamic Type lớn, VoiceOver, APNs hoặc mạng yếu trên thiết bị. Xem [hướng dẫn Expo](https://docs.expo.dev/workflow/ios-simulator/).
- Native SQLite/media outbox chưa triển khai; test ACK chỉ xác nhận contract phân loại. Không có thao tác gửi đơn/check-in/chấm công giả trong prototype.
- Chưa có bộ icon/splash và signing phát hành. Chưa tạo IPA, phân phối TestFlight hoặc xuất bản App Store.

## Dấu vết và bảo toàn nguồn

Nguồn ZIP, mockup, draw.io và tài liệu gốc chỉ được đọc; bản giải nén/trích xuất phục vụ khảo sát nằm trong vùng riêng `.tools/sales-audit/`. Đối chiếu SHA256 cuối lượt: **37/37 file trong manifest không đổi**. Logo app khớp SHA256 với logo gốc. Link tài liệu và 18 ID màn hình/gap được kiểm tra hợp lệ; scan file thay đổi không thấy thông tin đăng nhập được cấp hoặc token. Không đưa nguồn riêng tư, ảnh phiên đăng nhập, DB, token hoặc mật khẩu vào Git.

Kết quả máy đọc và screenshot nằm trong `.tools/sales-audit/browser-audit-results.json`, `expo-preview-results.json`, `mobile-dependency-audit.json` và `screens/`; log/runtime khác ở `artifacts/`. Các đường dẫn này được Git bỏ qua. Screenshot Expo có hậu tố `web-390`, không đặt tên giả lập iPhone.

Phạm vi bàn giao và thứ tự xây tiếp ở [backlog](04-backlog-va-nghiem-thu.md); danh mục màn và trạng thái hiện thực ở [screen-catalog.json](screen-catalog.json).
