# Chạy và kiểm tra đầy đủ SYNERA

Repo: https://github.com/ntphat170706/SYNERA. Gói phát hành gồm web NSX, portal NPP, backend và source Sales mobile. IPA unsigned được đính kèm riêng trong Releases và trong ZIP Full.

## 1. Chạy đúng bản web/backend đã đóng gói

Windows 10/11 x64, Python 3.10+ và Node.js 24 trên PATH. Giải nén ZIP Full vào thư mục mới, mở PowerShell trong thư mục `SYNERA`:

```powershell
python scripts/bootstrap-backend.py
python scripts/bootstrap-mysql.py
python scripts/run-local.py start --no-build
```

Lần đầu cần Internet để tải Java 25, Maven và MySQL portable. JAR đã chứa frontend production. `--no-build` không cần cài dependencies web; launcher vẫn cần Node trên PATH. MySQL dùng cổng 3308, website/API cổng 8080. Không chạy hai bản cùng cổng.

Mở http://127.0.0.1:8080/#/login. DB khởi tạo mới có `admin@synera.local`, `npp@synera.local`, `sales@synera.local`, mật khẩu local mẫu `SyneraLocal2026!`. Admin có OTP local hiển thị ở bước tiếp theo. Đây là seed cho máy mới, không phải mật khẩu của máy bàn giao; DB đang tồn tại không bị reset.

Đăng nhập admin để xem NSX, npp để xem portal. Dùng profile/trình duyệt khác nhau khi kiểm tra đồng thời. Hồ sơ mạng lưới tại `/#/network`, báo cáo `/#/reports`.

```powershell
# Dừng đúng runtime, giữ database và tệp đã tải lên
python scripts/run-local.py stop
```

## 2. Build từ repo mã nguồn

```powershell
git clone https://github.com/ntphat170706/SYNERA.git
cd SYNERA
npm.cmd install --global pnpm@11
pnpm.cmd install --frozen-lockfile
python scripts/bootstrap-backend.py
python scripts/bootstrap-mysql.py
python scripts/run-local.py start
```

Nếu đang chạy một bản khác, dừng bản đó trước. Không có `--no-build` sẽ build web và JAR mới. Để sửa web, giữ backend chạy rồi `pnpm.cmd dev`; Vite proxy API về 8080. Không đổi Java 25 về Java 17.

## 3. Chạy Sales mobile cùng backend

Giữ backend cổng 8080 chạy, mở terminal thứ hai:

```powershell
cd apps/sales-mobile
npm.cmd ci
npm.cmd run iphone
```

Để `EXPO_PUBLIC_API_URL` trống khi dùng launcher: Metro bridge gọi backend laptop. Trang `/phone` có QR cho Expo Go SDK 58. iPhone phải truy cập được laptop qua mạng; Wi-Fi chặn các thiết bị không phù hợp cho chế độ LAN. Windows không chạy iOS Simulator/Xcode; preview web không thay nghiệm thu iPhone thật.

Để dùng IPA khác mạng, backend cần HTTPS truy cập được. Xem `docs/sales-mobile/14-ipa-v4-va-thong-bao.md` và `apps/sales-mobile/eas.json`. IPA build 4 kèm theo nhúng URL tunnel lúc đóng gói; URL đó có thể ngừng khi laptop/tunnel dừng, không phải server cloud ổn định. Muốn đổi API/UI native cần build lại. IPA unsigned dùng Sideloadly ký/cài; không kèm tài khoản hoặc khóa ký Apple.

## 4. Kiểm tra luồng liên thông

1. Sales mở điểm bán được giao, tạo đơn với SKU/quy cách có tồn. NPP thấy đơn và có nút xử lý rõ ràng.
2. NPP xác nhận, xuất, bắt đầu giao; ghi thực nhận. Ghi chú/lý do là tùy chọn, các ràng buộc số lượng/quyền vẫn giữ.
3. Khi nhận thiếu so với lượng đã xuất, mở xử lý giao thiếu; đề xuất, người khác duyệt, xuất và ghi nhận giao bù. Không tính tiền lần hai.
4. Sales khi app đang mở thấy cập nhật và thông báo xác nhận; ghi kết quả đã báo lại khách. Trạng thái hàng và chăm sóc độc lập.
5. NSX mở hồ sơ NPP/điểm bán hoặc đơn gốc để truy vết ai phụ trách, tiến độ, lời hẹn và kết quả. Báo cáo giữ kết quả của Sales trước bàn giao.

Không dùng trường hợp xác nhận ít hơn lượng đặt để thay ca giao thiếu sau xuất. Quy trình nhu cầu chưa được cam kết, phải thu điểm bán và quyền nhiều nhãn hàng vẫn có khoảng thiếu đã ghi trong tài liệu.

## 5. Các lệnh kiểm tra

```powershell
pnpm.cmd check:architecture
pnpm.cmd format:check
pnpm.cmd test
pnpm.cmd build
.\scripts\backend.cmd verify
# API production phải đang chạy; tests tạo dữ liệu kiểm thử
$env:BOOTSTRAP_PASSWORD='SyneraLocal2026!'
pnpm.cmd test:e2e
pnpm.cmd test:design
cd apps/sales-mobile
npm.cmd run check
npm.cmd test
npx.cmd expo install --check
npm.cmd run export:web
```

MySQL suite chỉ chạy khi Docker hoặc schema kiểm thử riêng được cấu hình; skip không phải đạt. Browser mặc định dùng Microsoft Edge. Không chạy kiểm thử với DB vận hành thật. Kết quả local trước bàn giao: 15 unit web, 26 unit mobile, 18 kiểm tra luồng web, 12 nhóm thiết kế; kiểm tra NSX MySQL 5/5. Chi tiết `docs/nsx-portal/02-kiem-tra.md`.

## 6. Gói chứa gì

- `src`, `public`, `apps/sales-mobile`: đủ mã nguồn và assets; giữ riêng lockfile web/mobile.
- `backend`: mã nguồn, migrations, cấu hình mẫu và kiểm thử, cùng các hướng dẫn Java/MySQL.
- `scripts`, `.github`: bootstrap, build, kiểm tra và CI; `.eas` chứa workflow build IPA unsigned.
- ZIP Full thêm `dist`, `backend/target-verified/*.jar`, `output/ios/*v4*.ipa`, `BUILD-MANIFEST.json` và SHA256.
- Không chứa database máy bàn giao, uploads, `.env` thật, token/cookie, toolchain, node_modules hay khóa ký. Máy mới khởi tạo seed nên số liệu có thể khác ảnh chụp.

Tài liệu đối chiếu: `ARCHITECTURE.md`, `docs/BACKEND-API.md`, `docs/DESIGN-SYSTEM.md`, `docs/nsx-portal`, `docs/npp-portal`, `docs/sales-mobile`. File `SOURCE-SNAPSHOT.json` ghi provenance từ bản làm việc cũ; `BUILD-MANIFEST.json` ghi hash thực tế gói phát hành.
