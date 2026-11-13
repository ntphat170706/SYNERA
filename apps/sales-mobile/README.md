# SYNERA Sales · App theo mockup

App React Native/Expo riêng theo lựa chọn ngày 11/11/2026. Web React 18/Vite và Spring Boot vẫn giữ nguyên. Đọc [bộ phân tích Sales](../../docs/sales-mobile/README.md) trước khi mở rộng.

## Đã làm / phạm vi

- Sales đăng nhập API thật; OTP nếu được server yêu cầu; từ chối vai trò admin/NPP trong app này.
- Access token trong RAM, refresh token qua SecureStore trên native. Web preview chỉ giữ phiên trong RAM; reload cần đăng nhập lại.
- Bốn tab Hôm nay/Điểm bán/Đơn hàng/Tài khoản, native stack/back; list/detail đọc dữ liệu đúng scope server, kéo để tải lại và giao diện light/dark.
- Hồ sơ hiện đọc summary tối đa 20 dòng/section từ API; không phải màn 360° đủ phân trang/ticket/ảnh. Số lượng đơn hiển thị tách đặt/xác nhận/xuất/thực nhận.
- Tạo đơn theo mockup: tìm sản phẩm có ảnh, chọn UOM/lượng, báo giá và ưu đãi server, kiểm tra rồi gửi bằng cùng sync contract với web; đặt lại lấy giá hiện hành.
- Nháp đơn SQLite native, storage riêng trên web preview; lưu trước gửi, retry cùng key, ACK đúng owner/workspace, xác nhận giá mới khi `PRICE_CHANGED`. Không xoá cả dữ liệu sau logout hoặc gửi thành công.
- Check-in GPS qua expo-location/note checkout; kiểm tồn (gồm số 0), ghi nhu cầu, ticket văn bản, lời hẹn có picker native, xem/gửi báo cáo ngày theo API hiện có.
- Client có single-flight refresh và chặn phản hồi phiên cũ; các command có Idempotency-Key và test giữ body/key khi refresh.
- Hai mục AI ở Hôm nay và Hoạt động tại điểm: chọn/chụp ảnh gửi trưng bày và xem điểm đề xuất server; nhập yêu cầu để tạo dòng giỏ hàng. Điểm hiện từ provider mock local; hỗ trợ đơn dùng parser catalogue, chưa gọi LLM. Luôn xem lại/xác nhận, không tự gửi đơn hoặc tự duyệt ảnh. UI không gắn nhãn Demo theo yêu cầu; xem [phạm vi thực tế](../../docs/sales-mobile/09-tro-ly-va-trung-bay.md).

Chưa triển khai chấm công, checklist/ảnh lượt ghé, outbox media, cache danh mục sau kill hoặc auto-sync nền. Giao ngay/dùng thử ghi rõ chưa hỗ trợ; không dựng thao tác thành công giả. NSX quản lý Sales; Sales làm việc tại NPP được phân công. Giữ scope NPP server, không nâng Sales thành quản trị NSX. Chi tiết ở [phạm vi triển khai](../../docs/sales-mobile/07-app-theo-mockup.md).

## Chạy

Đã kiểm tra bằng Node 24.22.1 portable. Backend ở `127.0.0.1:8080` theo README root.

```powershell
cd apps/sales-mobile
npm ci
npm run iphone
```

Preview máy tính: **http://localhost:5173**. Launcher in địa chỉ LAN `/phone` có QR cho Safari và Expo Go. Có thể nhấp đúp `Mo-app-tren-iPhone.cmd` thay nhập lệnh. `Dang-nhap-Expo.cmd` mở đăng nhập Expo trên máy tính; Expo Go dùng cùng tài khoản. Tài khoản Sales dùng để đăng nhập bên trong app.

Để `EXPO_PUBLIC_API_URL` trống: web dùng cùng origin, native lấy host Metro từ Expo Constants. Bridge local nối backend 8080, chỉ cho GET/native auth và các POST Sales cụ thể; không chuyển cookie/browser-session hoặc mở thao tác duyệt/xuất/nhận của NPP. Static export cần reverse proxy tương đương. Không đặt token/password vào `EXPO_PUBLIC_*`.

```powershell
# Chỉ khi dùng một API khác mà điện thoại truy cập được.
$env:EXPO_PUBLIC_API_URL = 'http://127.0.0.1:8080'
npm start
```

**SDK 58** được khoá bằng package-lock theo Expo Go thực tế của người dùng; các module được đồng bộ bằng `expo install --fix`. Không nâng dependency của web root. Xem [hướng dẫn nâng SDK](https://docs.expo.dev/workflow/upgrading-expo-sdk-walkthrough/).

## iPhone và giả lập

### IPA cài trực tiếp

Bản build 2 dùng [API HTTPS qua Internet](../../docs/sales-mobile/11-ket-noi-internet.md), gateway riêng không cần Metro. iPhone có thể dùng mạng khác laptop; backend/database và tunnel tại laptop vẫn phải chạy. Hostname Quick Tunnel là tạm thời.

`npm run build:ios` hoặc `Build-IPA-iPhone.cmd` tạo IPA **chưa ký** bằng profile `unsigned` để người dùng ký/cài bằng Sideloadly. Còn có `preview` (Ad Hoc) và `production` (App Store/TestFlight). Xem [hướng dẫn build IPA](../../docs/sales-mobile/10-build-ipa.md). Bản Wi-Fi dùng backend laptop, không cần Expo Go để mở nhưng vẫn cần server laptop đang chạy. Chỉ có file IPA sau khi EAS biên dịch thành công; bản unsigned cần ký trước khi cài.

- Windows: iPhone thật dùng Expo Go qua Metro LAN; máy tính và iPhone cùng Wi-Fi. Backend/DB vẫn loopback, native đi qua bridge Metro. Không dùng `127.0.0.1` làm API base trên điện thoại.
- macOS + Xcode: `npm run ios` để mở iOS Simulator sau khi cài môi trường. Simulator không có đầy đủ phần cứng camera/GPS; vẫn cần thiết bị thật để nghiệm thu tác nghiệp hiện trường.
- Ảnh chụp từ web preview **không phải ảnh iOS Simulator**, và export JS bundle không phải IPA/build native đã chạy.
- Tham khảo [Expo iOS Simulator](https://docs.expo.dev/workflow/ios-simulator/), [tạo project/SDK](https://docs.expo.dev/get-started/create-a-project/) và [SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/).

## Kiểm tra

```powershell
npm run check
npm test
npx expo install --check
npm run export:web
```

Tests bao gồm role gate, refresh đồng thời, logout trong khi refresh/OTP đang chạy, mutation retry cùng body/key, UOM/bội số, ACK sai owner/workspace và xác nhận lại giá. SQLite và GPS trên thiết bị cần nghiệm thu riêng; screenshot Chromium không chứng minh native.

Token màu light/dark mirror nguồn canonical `src/styles/tokens.css` và `.app.dark` của web; giữ logo PNG gốc cùng SHA256. Asset mẫu Expo vẫn được giữ trên máy, bỏ qua Git và không dùng trong UI. Chưa chuẩn bị icon/splash bộ phát hành App Store.
