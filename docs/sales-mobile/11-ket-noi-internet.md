# IPA kết nối khác mạng

## Nguyên nhân và phạm vi

IPA build 1 dùng IP LAN `172.30.84.160:5173`; chỉ dùng được khi iPhone truy cập laptop. Ngày 12/11/2026, Safari iPhone trên UIT không truy cập được địa chỉ này, dù backend và Metro trả 200 tại laptop và Windows có rule Allow cho Node. Chưa có bằng chứng xác định chính xác thiết bị mạng chặn kết nối. Cấu hình Internet chuyển sang HTTPS để không phụ thuộc truy cập LAN.

## Kết nối hiện tại

`iPhone → HTTPS Cloudflare Tunnel → gateway loopback 8787 → Spring Boot loopback 8080 → MySQL local`.

URL hiện tại, đã xác thực ngày 13/11/2026: `https://plan-acceptance-sum-plug.trycloudflare.com`. [IPA build 3](13-ipa-v3.md) đã đóng origin mới. Host `extend-studying-narrow-retreat.trycloudflare.com` của build 2 không còn phân giải tại lúc kiểm tra; bật backend không tự sửa origin trong IPA cũ.
`GET /` hoặc `/health` trả `{ "service": "SYNERA Sales API", "ready": true }` để kiểm tra đường đến gateway; không khẳng định DB đã sẵn sàng. Mở URL bằng 4G/5G để kiểm tra kết nối khác mạng. `NOT_FOUND` là phản hồi gateway với đường dẫn không hỗ trợ, không phải bằng chứng lỗi DNS/kết nối.

`scripts/mobile-gateway.cjs` chỉ chuyển tiếp GET/POST đang dùng trong Sales mobile, giữ bearer token, Idempotency-Key, query, multipart/body và mã HTTP backend. Backend vẫn quyết định role/scope/validation. Không đưa web quản trị, Actuator, browser-session auth hoặc API duyệt của NPP lên tunnel; không đưa cookie browser vào backend hoặc trả Set-Cookie ra ngoài. Gateway không ghi password/token/body vào log, chỉ lắng nghe loopback.

## IPA build 2

- Lịch sử: profile `unsigned` của build number 2 dùng hostname `extend-studying-narrow-retreat.trycloudflare.com`. Profile hiện đã chuyển sang build 3; preview LAN giữ lại cho thử nội bộ.
- Build unsigned không cần Apple credentials; ký/cài bằng Sideloadly với cùng Apple account/bundle ID để cập nhật app. Không cần xóa app/nháp đang có.
- Chỉ cấp ngoại lệ HTTP ATS cho profile LAN thực sự có URL `http:`; build HTTPS dùng chính sách mặc định iOS.
- Gateway chạy độc lập Metro: IPA Internet không cần Expo Go hoặc server Metro; vẫn cần Java/MySQL, gateway và tunnel chạy.
- Build: [EAS 4e705ea4](https://expo.dev/accounts/ntp11111/projects/synera-sales/builds/4e705ea4-3ffc-4871-82ca-1af3d111810d) đã FINISHED ngày 12/11/2026. File local `output/ios/SYNERA-Sales-Internet-unsigned.ipa`; file cũ LAN được giữ nguyên.
- Kiểm tra: TypeScript qua, 24/24 tests mobile, định dạng file thay đổi qua. Gateway HTTPS trả health 200, auth/me không token 401, chặn Actuator/web 404 và chuyển tiếp login đến backend. Người dùng báo thấy JSON `NOT_FOUND` qua mạng di động, xác nhận đã đến gateway; sau đó root được đổi sang health. Chưa nghiệm thu đăng nhập bản IPA mới trên iPhone thật.

## Giới hạn vận hành

Đây là Quick Tunnel, **không phải triển khai backend production**. Laptop tắt/ngủ, mất mạng hoặc tunnel ngừng thì app không gọi API được. Quick Tunnel tạo hostname mới khi khởi động lại; IPA hiện đóng URL vào bundle, nên hostname mới cần build IPA lại. Không hứa địa chỉ này tồn tại cố định.

Để dùng bền vững và khi laptop tắt: triển khai backend + database + kho ảnh lên hosting/VPS, đặt domain HTTPS ổn định, cấu hình backup/secrets theo hệ thống rồi đổi API URL và build lại. Named tunnel có domain cố định chỉ giải quyết hostname, vẫn phụ thuộc laptop nếu backend đặt tại laptop.

Xem [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/). Binary tunnel và runtime logs nằm trong `.tools/`/`artifacts/` bị Git/EAS bỏ qua; không chứa trong IPA.
