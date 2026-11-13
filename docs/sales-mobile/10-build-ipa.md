# IPA cho iPhone thật

Bản mới nhất ngày 13/11 là [IPA build 3](13-ipa-v3.md), dùng API HTTPS mới và source Sales cập nhật. Các build 1/2 dưới đây được giữ làm lịch sử, không phải file bàn giao hiện tại.

Build unsigned đã thành công ngày 12/11/2026: [EAS 09c00793](https://expo.dev/accounts/ntp11111/projects/synera-sales/builds/09c00793-b9f4-45a4-b21f-b0cf888fd3bb). File bàn giao local: `output/ios/SYNERA-Sales-unsigned.ipa` tại root repo (không commit binary). Cài/ký và kiểm thử IPA trên iPhone qua Sideloadly chưa được nghiệm thu.

## Lựa chọn hiện tại: IPA chưa ký cho Sideloadly

Người dùng chọn tự ký/cài bằng Sideloadly. Lệnh mặc định `npm run build:ios` dùng profile `unsigned`, không cần đăng nhập Apple, đăng ký UDID hay tạo chứng chỉ trên EAS. `Build-IPA-iPhone.cmd` đã chuyển sang lệnh này.

Workflow `.eas/build/ios-unsigned.yml` chạy trên máy macOS EAS: cài dependencies, prebuild, CocoaPods, archive Release `iphoneos` với `CODE_SIGNING_ALLOWED=NO`, kiểm tra bundle JS + nền tảng iPhoneOS + arm64, rồi đóng `Payload/*.app` vào `SYNERA-Sales-unsigned.ipa`. Không thay simulator bundle thành IPA. File chưa ký cần được Sideloadly ký trước khi iOS chấp nhận cài/mở.

`unsigned` kế thừa API LAN và ngoại lệ ATS của preview. Backend laptop vẫn phải hoạt động. Hồ sơ ký `preview` và `production` dưới đây chỉ là lựa chọn khác, không dùng cho lần build hiện tại.

## Cấu hình

Package riêng `apps/sales-mobile`, Expo SDK 58, bundle ID `vn.synera.sales`.
EAS project: [ntp11111/synera-sales](https://expo.dev/accounts/ntp11111/projects/synera-sales).
Project ID `1e68dccf-0eb3-4ece-9068-16997a610125` là định danh công khai, không phải credential.

- `unsigned`: IPA Release arm64 chưa ký, người dùng tự ký bằng Sideloadly.
- `preview`: Release bundle, phân phối nội bộ Ad Hoc, iPhone thật; không cần Expo Go để mở. Cần Apple Developer và UDID thiết bị trong provisioning profile.
- `production`: bản App Store/TestFlight, API HTTPS bắt buộc. Chưa cấu hình submit tự động hoặc phát hành.
- `app.config.cjs` giữ permission camera/ảnh/GPS từ app.json, thêm định danh, build number và mô tả quyền mạng cục bộ. Không thêm background tracking.
- Chỉ preview/unsigned cho phép HTTP LAN qua ATS; production không có ngoại lệ HTTP. Cấu hình chặn thiếu API, loopback, URL chứa credential/path/query và HTTP công khai.
- `.easignore` tại root repo chỉ đưa package mobile lên EAS; không đưa backend, DB, tài liệu riêng, cache hoặc credential vào archive.

## Backend laptop cùng Wi-Fi

Theo lựa chọn người dùng, preview hiện nối `http://172.30.84.160:5173` qua bridge Metro hiện có. API vẫn chạy loopback `127.0.0.1:8080`, giữ phân quyền/idempotency backend. Đây không phải backend production; giữ laptop, DB, backend và `npm run iphone` hoạt động, iPhone cùng Wi-Fi và cấp quyền Mạng cục bộ.

Địa chỉ trên được đóng vào IPA. Nếu laptop đổi IP, sửa `build.preview.env.EXPO_PUBLIC_API_URL` trong `eas.json` rồi build lại; nên giữ IP cố định trong mạng thử nghiệm. Không dùng `localhost`/`127.0.0.1` trên iPhone. IPA chứa mã giao diện nhưng không chứa Java/MySQL; các chức năng trực tuyến cần backend.

## Ký và build

Để tạo file cho Sideloadly, chỉ chạy `npm run build:ios`. Phần dưới áp dụng nếu chọn EAS ký Ad Hoc thay Sideloadly.

Trong `apps/sales-mobile`:

```powershell
npx eas-cli@latest whoami
npm run ios:register
npm run build:ios:signed
```

Đăng nhập Apple và nhập 2FA trực tiếp trong terminal của bạn. Không gửi mật khẩu/2FA qua chat hoặc lưu vào source. CLI có thể quản lý distribution certificate và Ad Hoc provisioning profile khi bạn xác nhận bằng tài khoản có quyền tương ứng. Dùng Safari trên iPhone theo link đăng ký UDID, sau đó link cài đặt do build thành công cung cấp. Thiết bị thêm sau cần build lại/resign profile.

Sau khi có API HTTPS, đặt `EXPO_PUBLIC_API_URL` trong EAS environment `production` rồi chạy `npm run build:ios:store`. Không đưa token/password vào biến `EXPO_PUBLIC_*` vì chúng nằm trong bundle.

## Kiểm tra

- TypeScript, tests mobile, dependency compatibility và export web phải qua trước khi build.
- `eas build:inspect --platform ios --profile preview --stage archive --output .expo/ipa-archive` kiểm tra archive trước khi upload.
- Kiểm tra iPhone thật: cài IPA, đăng nhập, GPS, chọn/chụp ảnh, nháp SQLite, quote/sync đơn, tắt/mở app và phục hồi phiên.
- JS export hoặc cấu hình build không chứng minh IPA đã được ký/build/cài thành công. Kết quả EAS và nghiệm thu thiết bị được ghi riêng sau khi chạy.

Nguồn: [Expo internal distribution](https://docs.expo.dev/build/internal-distribution/), [cấu hình EAS](https://docs.expo.dev/build/eas-json/), [quy tắc archive](https://docs.expo.dev/build-reference/easignore/).
