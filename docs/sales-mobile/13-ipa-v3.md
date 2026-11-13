# IPA iPhone bản 3 — 13/11/2026

Người dùng yêu cầu đóng gói lại IPA mới để kiểm tra các chức năng Sales hiện có sau khi hostname Quick Tunnel của bản 2 không còn phân giải được. Không ký bằng Apple credentials; người dùng tiếp tục dùng Sideloadly.

- EAS build: [2db76bdd](https://expo.dev/accounts/ntp11111/projects/synera-sales/builds/2db76bdd-1127-4b92-884b-b7aaf281712a), trạng thái FINISHED.
- Source: package Expo SDK 58 hiện tại, `vn.synera.sales`, version 0.1.0, build number **3**. Không thay source web hoặc backend trong lượt đóng gói này.
- API mới được đóng trong bundle: `https://plan-acceptance-sum-plug.trycloudflare.com`. Profile `unsigned` đã cập nhật origin và `app.config.cjs` đã tăng build number.
- File: `output/ios/SYNERA-Sales-v3-Internet-unsigned.ipa`, 11.089.465 byte. Bản 1/2 được giữ nguyên.
- SHA256: `793424dbbd3f6bf8543d6ee5702d690bd8e9ee9fe97210de67abe174eafe3122`. Manifest và checksum nằm cùng thư mục output.

## Kiểm tra thực tế

TypeScript và 25 tests mobile đạt. `expo install --check` offline báo dependencies up to date nhưng cảnh báo độ tin cậy của chế độ offline; không xem đây là xác nhận từ registry online. EAS đã cài dependencies, prebuild, pod install và archive Release thành công.

Sau tải về: ZIP CRC đạt; Info.plist đúng bundle ID/build 3/iPhoneOS; Mach-O executable là arm64; có `main.jsbundle` chứa đúng API mới và không có hostname API cũ. Không có provisioning profile trong app; IPA là bản chưa ký cho Sideloadly. HTTPS dùng ATS mặc định, không mở `NSAllowsArbitraryLoads`.

API public đã kiểm: health 200, không token auth/me 401, Actuator 404. Đăng nhập Sales và GET đơn bằng bearer qua HTTPS đều 200. Không ghi token/password vào manifest hoặc tài liệu.

Bản này chứa các chức năng Sales đã triển khai: đi tuyến/check-in/out, khảo sát tồn, tương tác, phản ánh, lời hẹn, đơn/nháp/quote/sync/đặt lại, báo cáo ngày, AI hỗ trợ hiện có và Inbox/giao bù/báo khách. Đây không phải tuyên bố đã hoàn thiện HR, sự kiện/cộng đồng hay AI đã train. Chưa nghiệm thu camera/GPS/SecureStore/SQLite và bản IPA 3 trên iPhone thật.

## Cài và vận hành

Dùng Sideloadly ký/cài với cùng Apple ID và định danh ứng dụng đang dùng để cập nhật. Không yêu cầu xóa app/nháp trước. Backend, MySQL, gateway 8787 và tunnel phải tiếp tục chạy trên laptop; IPA không chứa server.

Hostname này vẫn là Quick Tunnel tạm. Khi tunnel khởi động lại có thể đổi hostname; IPA 3 không tự đổi địa chỉ theo server. Muốn URL bền vững cần endpoint HTTPS cố định hoặc bổ sung cấu hình endpoint có kiểm soát vào app ở lượt riêng. Không gọi đây là staging/production ổn định.
