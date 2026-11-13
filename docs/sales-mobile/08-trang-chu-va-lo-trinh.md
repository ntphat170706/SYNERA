# Trang chủ Sales và lượt đang ghé

Cập nhật 12/11/2026 theo ảnh cắt mới và yêu cầu người dùng. Bố cục lấy trực tiếp từ tham chiếu: lời chào/tên/ảnh trên vùng xanh rộng; chuyển màu mờ dần về nền sáng; ba ô đơn hàng, giá trị đơn, doanh thu; lượt đang ghé nổi bật trước danh sách lộ trình còn lại. Người dùng yêu cầu giao diện hoàn chỉnh cho dữ liệu mẫu, không hiển thị ghi chú “tạm tính” hoặc giải thích kỹ thuật trên bảng số liệu.

## Giao diện và tương tác

- Light Mode giữ nguyên: canvas `#f6f6f7`; vùng chào xanh `#1155aa` → tint `#1769c2` → `#69a5de` → canvas. Các khung `#ffffff`, `#f0f7ff`, `#dfeeff` và header `#eaf3ff` phân cấp trong cùng dải xanh.
- Chào theo giờ Việt Nam; khoảng chào rộng phía trên có avatar. Hôm nay/Tháng này đổi kỳ số liệu thật, không dùng KPI hoặc tỷ lệ tăng trưởng giả.
- `CHECKED_IN` hiển thị lên trước, dải xanh đậm, địa chỉ, đồng hồ HH:mm:ss, nút tiếp tục/lịch sử đơn/tạo đơn. Danh sách còn lại có chỉ đường và ghé thăm; không biến việc bấm chọn thành check-in thành công.
- Đồng hồ dựa `checkedInAt` máy chủ, tính lại theo thời gian hiện tại sau reload/resume. Không ghi một bản đếm từng giây vào DB; timestamp thiếu hiển thị `—`, thời gian tương lai không tạo số âm. Check-out vẫn theo API cũ và loại khung khỏi lượt đang ghé.
- Một lần fade nhẹ trên khung khi chuyển lượt, không animation nhấp nháy lặp. Tôn trọng Reduce Motion; gradient dùng `expo-linear-gradient` có trong Expo Go, không import DOM component của React Bits vào React Native. [Fade Content](https://reactbits.dev/animations/fade-content) là nguồn tham khảo cách chuyển nhẹ, không chép implementation web.

## Dữ liệu thống kê

Bổ sung duy nhất API **chỉ đọc** `/sales-orders/summary`, dùng SalesFacade và tính doanh số thực nhận hiện có. Phạm vi lấy actor/workspace server, bỏ qua ID Sales/NPP do client gửi. Khoảng ngày tối đa 366 ngày, quy đổi theo Asia/Ho_Chi_Minh.

Đơn hàng và giá trị đơn dựa ngày tạo, bỏ đơn huỷ; doanh thu ở UI dựa ngày giao và lượng thực nhận sau chiết khấu, không dựa mọi đơn SUBMITTED. Metadata `PROVISIONAL` vẫn giữ trong API để tương thích hệ thống báo cáo; chỉ bỏ ghi chú khỏi giao diện theo yêu cầu. Không sửa sổ tài chính hoặc trạng thái đơn để có số đẹp.

## Tên và ảnh

Đổi đúng `app_user.display_name` của Sales local ID 3 thành **Quân Đào**. Đối chiếu SHA256 phần còn lại của dòng trước/sau: ID, username, password hash, role, NPP và các cột khác không đổi. Không sửa bootstrap tất cả tài khoản hoặc đổi tên chứng từ cũ. Bằng chứng local ở `.tools/sales-audit/profile-update-20261112.json` được Git bỏ qua.

Ảnh người dùng cung cấp: `D:\DU_AN_SYNERA\903751626_1533124188591445_8591931183722195030_n.jpg`, sao chép nguyên byte thành `assets/quan-dao-avatar.jpg`, chỉ dùng cho tài khoản Sales local này; các điểm bán và người dùng khác giữ avatar chữ. Không có schema avatar trong backend, nên ảnh hiện là asset cá nhân hoá app, chưa phải hệ thống upload ảnh tài khoản. `/auth/me` cấp tên mới khi màn trang chủ/tài khoản tải lại.

## Phạm vi kiểm tra

Unit test tính ngày Việt Nam, kỳ báo cáo, đồng hồ qua nửa đêm/reload/future/missing timestamp. Backend integration kiểm số liệu theo ID Sales/NPP, quyền âm, ngày sai và đối chiếu lượng thực nhận/chiết khấu. Kiểm tra browser có GPS giả lập được ghi rõ là dữ liệu kiểm thử, không phải bằng chứng đã tới điểm bán thực tế. Các ảnh `home-*-web.png` nằm trong vùng ignored, không gắn nhãn screenshot native.
