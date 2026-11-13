# App Sales theo mockup và thử trên iPhone

Cập nhật 11/11/2026 theo yêu cầu dựng gần mockup và test chức năng. Mốc này mở rộng phần chuẩn bị đọc API ban đầu; không sửa logic backend hoặc web React 18/Vite.

## Đã dựng

| Màn/luồng | Thiết kế và hành vi |
| --- | --- |
| Hôm nay | Lịch/ngày, số lượt thật, lọc chưa xong/đã ghé, điểm được giao, báo cáo và nháp chờ gửi |
| Điểm bán | Tìm tên/địa chỉ/điện thoại, hồ sơ chuẩn bị–hoạt động–lịch sử, gọi điện/chỉ đường khi có dữ liệu |
| Tạo đơn theo M2 | Loại giao sau đang được server hỗ trợ; danh mục có ảnh, tìm kiếm/nhóm, UOM và stepper, kiểm tra giá/ưu đãi server, tổng kết rồi gửi |
| Theo dõi/đặt lại | Danh sách và lọc trạng thái, tên điểm, từng lượng đặt/xác nhận/xuất/thực nhận; lấy lại giỏ rồi báo giá mới |
| Nháp đơn | SQLite trên native; preview web dùng local storage theo owner/workspace. Lưu trước gửi, cùng clientId/key khi retry, ACK từng item. PRICE_CHANGED có bước lấy và đồng ý giá mới. ACKNOWLEDGED được giữ, ẩn khỏi hàng chờ |
| Lượt ghé | Xin quyền/lấy GPS thật qua expo-location; check-in theo radius/reason server; checkout ghi kết quả theo API hiện có |
| Kiểm tồn/CRM | Số tồn theo đơn vị cơ sở server, số 0 hợp lệ; ghi nhu cầu/tương tác, tạo ticket và lời hẹn ngày/giờ |
| Cuối ngày | Lấy nháp báo cáo từ server, xem lượt/đơn/giá trị đơn không huỷ rồi chủ động gửi; không gọi giá trị đơn là doanh thu |

Phân tích ảnh M1/M2/M3 vẫn ở [ma trận nguồn](01-doi-chieu-nguon.md). Luồng M2 được gom thành loại đơn → chọn hàng → kiểm tra/gửi; không thêm input điều kiện giao/ghi chú tài chính mà DTO chưa lưu được. Giao ngay/dùng thử ghi rõ chưa hỗ trợ. Trạng thái/SKU/UOM lấy server; dữ liệu seed có bất thường phải sửa ở danh mục nguồn, không tự đổi label để che khác biệt.

Ảnh danh mục là minh hoạ, có nguồn và SHA256 ở `apps/sales-mobile/assets/products/sources.json`; chưa phải ảnh bao bì được xác nhận cho các SKU seed. Không chép branding Cloudocs/OCS vào app. Logo SYNERA gốc giữ nguyên.

Theo góp ý nền xanh tối trên điện thoại, giao diện hiện cố định Light Mode: canvas xám trắng nhạt, khung trắng có bóng nhẹ, tint xanh cho nút/trạng thái. Không tự theo Dark Mode của hệ điều hành.

## Mở trên iPhone

1. Chạy backend bằng launcher repo như trước.
2. Mở `apps/sales-mobile/Mo-app-tren-iPhone.cmd` hoặc chạy `npm run iphone` trong package. Giữ terminal/server đang chạy.
3. Mở `/phone` trên địa chỉ LAN được launcher in ra; có QR Safari và QR Expo Go riêng. Safari là preview web, Expo Go chạy React Native trên điện thoại.
4. Expo CLI và Expo Go đăng nhập cùng tài khoản Expo. File `Dang-nhap-Expo.cmd` mở luồng đăng nhập trình duyệt, không lưu mật khẩu vào source. Tài khoản Sales chỉ dùng bên trong app.

iPhone người dùng đang có Expo Go **SDK 58**, vì vậy package được nâng cùng SDK 58; không yêu cầu hạ app điện thoại xuống SDK 57. Sau nâng phải restart Metro để manifest và module cùng phiên bản. Xem [hướng dẫn Expo](https://docs.expo.dev/workflow/upgrading-expo-sdk-walkthrough/).

## Giới hạn còn lại

- Chấm công, ảnh/checklist lượt ghé, yêu cầu điểm bán duyệt, loại đơn giao ngay/dùng thử và nhiều trường giao nhận còn cần backend contract.
- Nháp đơn đã bền vững; danh mục chưa cache bền vững sau kill và chưa có auto sync nền. Các thao tác ghi ngoài đơn hiện cần mạng; chưa có media queue.
- Summary 360° còn giới hạn dữ liệu trả về; chưa triển khai tất cả section phân trang, camera/trưng bày và inbox push native.
- Windows không chạy iOS Simulator chính thức. Bundle iOS và screenshot Chromium không chứng minh Keychain, SQLite, GPS, bàn phím, VoiceOver hoặc vòng đời iPhone đã nghiệm thu. Ghi nhận riêng phản hồi thiết bị của người dùng.
- Chưa ký IPA, TestFlight hoặc phát hành App Store. Xem [kết quả kiểm tra](05-kiem-tra.md) cho bằng chứng và các lỗi nền còn mở.
