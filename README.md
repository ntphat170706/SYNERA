# SYNERA — Web NSX, Portal NPP và Sales Mobile

Repo đầy đủ trên tài khoản [ntphat170706](https://github.com/ntphat170706/SYNERA), đóng gói từ bản đã kiểm tra ngày 13/11/2026. Web và app dùng chung backend, phân quyền và dữ liệu giao dịch.

- **Web NSX:** điều hành mạng lưới, NPP/điểm bán 360°, tổ chức/vùng/tuyến/MCP, đơn và báo cáo.
- **Portal NPP:** đặt nhập, đáp ứng đơn Sales, kho, thực nhận/giao bù, chăm sóc và đối soát.
- **Sales Mobile:** React Native/Expo SDK 58 cho iPhone, đi tuyến, lên đơn, AI hỗ trợ và thông báo trong app.
- **Backend:** Java 25, Spring Boot, MySQL 8.4/Flyway; giao dịch, phạm vi, idempotency, audit/outbox dùng chung.

Hướng dẫn bàn giao và chạy cả ba vùng: **[HUONG-DAN-CHAY.md](HUONG-DAN-CHAY.md)**. ZIP bản build và IPA unsigned nằm ở **Releases**, không đưa binary lớn vào Git. Mật khẩu/database/khóa ký của máy bàn giao không được kèm theo; DB mới dùng seed local riêng.

Website DMS + CRM gồm React 18 / Vite / TypeScript và Java 25 / Spring Boot. Màn hình nghiệp vụ gọi API có phân quyền và scope; MySQL giữ đơn, tồn, công nợ và snapshot. Giao diện giữ mẫu quản trị Sapo và logo SYNERA gốc.

## Chạy local

Từ root, cần Python và Node.js 22+:

```powershell
pnpm install --frozen-lockfile
python scripts/bootstrap-backend.py
python scripts/bootstrap-mysql.py
python scripts/run-local.py start
```

Mở **http://127.0.0.1:8080**. Tài khoản local: `admin@synera.local`, `npp@synera.local`, `sales@synera.local`; mật khẩu khởi tạo `SyneraLocal2026!`. Admin cần OTP local hiển thị khi đăng nhập. Chi tiết ở [backend/README.md](backend/README.md).

Website/API cùng nằm trong một JAR. Khi sửa FE, giữ backend chạy và dùng `pnpm dev` trên 5173. Vite proxy API về 8080. Dừng bằng `python scripts/run-local.py stop` trước khi build/chạy lại JAR mới; dữ liệu được giữ nguyên.

## Luồng nghiệp vụ

NPP đặt sell-in → NSX duyệt, xác nhận số lượng, xuất kho, giao → NPP thực nhận → đối soát/chốt kỳ. Giá, FEFO, hạn mức, ledger và snapshot do server xử lý. Tài khoản khác NPP không truy cập được chứng từ ngoài scope.

Sales quản lý cửa hàng/tuyến được giao, GPS, đơn bán, báo cáo ngày. PWA production cache shell và tham chiếu cần thiết theo user/workspace. Nháp offline lưu IndexedDB, chỉ xóa từng nháp sau ACK hợp lệ; giá thay đổi cần xác nhận lại. LocalStorage demo cũ được giữ nguyên, không nhập vào sổ server.

Có màn hình danh mục/import Excel, giá/chính sách/tài liệu, kế hoạch/gợi ý nhập, CRM/báo giá/chiến dịch, chuyến giao, trade/quyết toán, trưng bày, IAM/workspace và báo cáo XLSX/PDF/lịch email. Xem [phạm vi và giới hạn](docs/BACKEND-BUILD-SUPPLEMENT.md).

Website/backend chạy local; app có gateway giới hạn API Sales và cấu hình Expo/IPA. URL tunnel trong cấu hình build cũ chỉ là kết nối tạm, cần thay bằng endpoint đang hoạt động khi build ở máy khác. Chưa có staging ổn định. Dữ liệu MySQL và tệp tải lên lưu trên máy local, không tự đồng bộ lên GitHub.

Phạm vi nhãn hàng hiện tại: installation một NSX; API mạng lưới từ chối cấu hình nhiều NSX khi chưa có ownership/grants. AI hỗ trợ hiện dùng parser danh mục/provider mẫu, cần người dùng kiểm tra. Thông báo Sales cập nhật khi app đang mở, chưa có APNs lúc app đóng. Chi tiết trong [phạm vi NSX](docs/nsx-portal/01-nguon-va-man-hinh.md), [portal NPP](docs/npp-portal/README.md) và [Sales mobile](docs/sales-mobile/README.md).

## Kiểm tra

```powershell
pnpm check:architecture
pnpm format:check
pnpm test
pnpm build
.\scripts\backend.cmd verify
python scripts/format-backend.py --check
# Website/API phải đang chạy bản production mới:
pnpm test:e2e
pnpm test:design
```

Script pnpm tương đương `npm run <script>` trong package.json. Browser QA dùng Edge; có thể đặt BROWSER_CHANNEL hoặc TEST_URL. MySQL thực được kiểm tra bằng Testcontainers hoặc suite portable trong README backend; H2 không thay thế MySQL.

## Giữ cùng luồng phát triển

- [ARCHITECTURE.md](ARCHITECTURE.md): module, dữ liệu và mẫu prompt dùng lại.
- [AGENTS.md](AGENTS.md): điểm vào cho tác vụ sau.
- [DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md): token, CSS canonical, logo, responsive.
- [DECISIONS.md](docs/DECISIONS.md): quyết định chung.
- [BACKEND-API.md](docs/BACKEND-API.md): hợp đồng và tích hợp.

Flow: **đọc yêu cầu → xác định role/route/data → tái sử dụng → sửa đúng module → kiểm tra → cập nhật tài liệu → bàn giao**. Không khởi tạo lại app/router/store, không sửa nhầm `../synera-web` và không xóa dữ liệu người dùng để che lỗi.
