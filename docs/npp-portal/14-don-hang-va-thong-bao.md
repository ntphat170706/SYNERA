# Xử lý đơn và cập nhật Sales — 13/11/2026

Yêu cầu trực tiếp mới nhất thay thế quy định bắt buộc nhập lý do trong tài liệu cũ.

## Hợp đồng dùng chung

- Trường `reason` được bỏ trống hoặc không gửi; DTO chuẩn hóa thành chuỗi rỗng. Ghi chú giao hàng `note` cũng không bắt buộc. Audit vẫn ghi người thực hiện, thời điểm, hành động và số lượng thực tế; không tự đặt lời giải thích thay người dùng.
- Quyền, phạm vi NPP/Sales, trạng thái nguồn, số lượng hợp lệ, hạn mức, tồn kho, idempotency và transaction giữ kiểm tra tại backend. Nội dung nghiệp vụ chính như tiêu đề lịch hẹn, nội dung phản ánh khách hàng không trở thành ghi chú tùy chọn.
- Nhận thiếu vẫn tạo hồ sơ chênh lệch. Khi không có lý do, mô tả hồ sơ dùng số thực xuất/thực nhận/hỏng/hoàn đã ghi; không tạo lý do giả hoặc bỏ hồ sơ.
- `SalesOrderUpdated` ghi cùng giao dịch chuyển trạng thái, gồm `orderId`, `code`, `channel`, `status`, `storeId`. Outbox phân phối tới Sales phụ trách và vai vận hành NPP trong phạm vi; notification lưu bền và dedup bằng event key.
- Frontend không tự giả lập xác nhận thành công hoặc sửa trạng thái trước ACK của server.

## Trình bày và tương tác

- Danh sách đơn có nút đóng khung “Xử lý đơn”. Tổng quan có nút hành động riêng theo trạng thái, không buộc người mới đoán mã đơn là liên kết.
- Chi tiết trình bày năm bước và một hành động tiếp theo theo quyền. Hủy/đặt lại/hoàn là hành động phụ, không được trình bày như bước tiếp theo của quy trình. Quay lại danh sách giữ kênh và bộ lọc.
- Bộ chọn số lượng sản phẩm/giỏ hàng dùng +/− và ẩn spinner mặc định. Form số khác giữ spinner native. Không dùng đồng thời hai bộ điều khiển trên một ô đặt hàng.
- Thông báo web là bảng nổi bên chuông, danh sách không viền từng thẻ, có Tất cả/Chưa đọc, thời gian tương đối và chấm chưa đọc. Tên sự kiện/trạng thái được dịch; mã ID, SKU vẫn là định danh.
- Sales có banner thông báo mới khi app đang mở; chạm mở hộp thư. Thông báo lịch sử vẫn xem được trong Inbox. Thông báo chưa đọc từ trước lúc đăng nhập không bị phát lại thành banner mới.

## Đồng bộ và giới hạn

Web và app kiểm tra đơn/việc/thông báo mỗi 2 giây khi màn hình đang hoạt động, chống request chồng và giữ nội dung trong lần tải nền. Outbox xử lý theo chu kỳ 2 giây; độ trễ quan sát gồm cả outbox, truy vấn và mạng, không cam kết tức thời tuyệt đối.

Đây là cập nhật gần thời gian thực trong app đang mở, **chưa có APNs/push khi app bị đóng hoặc thông báo màn hình khóa**. Native đổi giao diện/logic cần IPA mới; IPA đã cài không tự đổi bundle. Endpoint tunnel tạm phụ thuộc laptop và tiến trình tunnel đang chạy.

Kiểm thử bổ sung: xác nhận không lý do vẫn phát notification cho Sales; nhận thiếu không lý do giữ đúng số lượng; nhãn thông báo tiếng Việt và deep link; hai phiên Sales/NPP kiểm tra cập nhật tự động và banner. Preview Chromium không thay thế nghiệm thu trên iPhone thật.
