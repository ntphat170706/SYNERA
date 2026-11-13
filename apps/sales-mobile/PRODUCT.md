# Product

<!-- impeccable:product-schema 1 -->

## Platform

ios

## Users

Sales do NSX quản lý, được phân công làm việc tại NPP; đi tuyến, ghé điểm bán, lên đơn và theo dõi cam kết với khách. Người dùng chọn iPhone thật/Expo Go để xem và thử bản phát triển.

## Product Purpose

Cùng hệ thống SYNERA DMS/CRM, phục vụ tác nghiệp hiện trường bằng app React Native/Expo riêng. Giữ hồ sơ cửa hàng, đơn và trạng thái nhất quán với web qua API Spring Boot hiện có.

## Operating Context

Thao tác bằng một tay tại điểm bán, có thể mất mạng. PDF phân tích thiết kế hệ thống 50 trang, draw.io và mockup gốc trong D:/DU_AN_SYNERA là nguồn đối chiếu; xem docs/sales-mobile/06-nguon-thiet-ke-va-tuong-thich.md tại root repo.

## Capabilities and Constraints

Luồng phải theo quyền và transition server. Không tự cấp quyền quản trị NSX, không giả chấm công hoặc ảnh chứng cứ, không thêm trường giao nhận rồi bỏ dữ liệu khi gửi. Backend hiện chưa có đầy đủ loại đơn giao ngay/dùng thử, attendance và ảnh checkout. Những phần đó cần ghi rõ là chưa hỗ trợ.

## Brand Commitments

Tên và logo gốc SYNERA. Người dùng yêu cầu giao diện gần mockup Sales Cloudocs/OCS về bố cục và thứ tự thao tác; thay thương hiệu/nội dung bằng SYNERA, không chép dữ liệu mẫu của bên tham khảo.

Người dùng tiếp tục yêu cầu nền sáng, không xanh tối: mặc định cố định Light Mode, nền xám trắng rất nhạt và khung trắng có bóng nhẹ, xanh chỉ làm điểm nhấn.

Ngày 12/11 người dùng bổ sung ảnh cắt trang chủ: vùng chào xanh rộng mờ dần, số liệu đơn/doanh thu bên dưới, lượt đang ghé nổi lên và đếm thời gian. Các nhóm khung có sắc xanh đậm/nhạt trên nền sáng. Người dùng yêu cầu bỏ chữ “tạm tính” và ghi chú kỹ thuật khỏi số liệu vì đang dùng dữ liệu mẫu. Tên Sales local đổi riêng thành Quân Đào và dùng ảnh chân dung người dùng cung cấp.

Người dùng tiếp tục yêu cầu hai luồng AI trưng bày/hỗ trợ lên đơn có thể chạy thử với dữ liệu mẫu, không gắn nhãn Demo ở UI. Kết quả đề xuất luôn có bước người dùng xem lại; provider chấm ảnh hiện là mock backend, hỗ trợ đơn hiện là parser danh mục, chưa phải dịch vụ AI thật.

## Evidence on Hand

Hai ảnh Sales M1/M2 và ảnh ticket M3 đã được định danh ở ma trận nguồn. API local thật và ba vai trò đã được kiểm tra. Chưa có bằng chứng app chạy trên iPhone người dùng; screenshot web không phải ảnh iOS.

## Product Principles

- Nghiệp vụ do backend xác nhận, phản hồi UI đúng trạng thái thật.
- Đơn vị, giá, số lượng và điểm bán luôn rõ khi lên đơn.
- Giữ nháp theo user/workspace và cùng mã gửi lại; không mất đơn vì mất mạng.
- Tác vụ bắt đầu từ ngữ cảnh điểm/lượt ghé, tránh nhập lại.
