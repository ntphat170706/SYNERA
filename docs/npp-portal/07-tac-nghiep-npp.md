# Portal tập trung tác nghiệp NPP — 12/11/2026

Lượt này điều chỉnh FE và đường vào chức năng hiện có; không thay schema hoặc contract backend, không thay native app/IPA, không mở rộng quyền NSX/nhãn hàng.

## Công việc và thao tác

| Vấn đề vận hành                                       | Thành phần đã chỉnh                                                                                                                |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Khó thấy đơn cần xác nhận, xuất/giao hoặc nhận từ NSX | Bốn hàng đợi chọn được ngay đầu tổng quan; số đếm và bảng cùng một bộ lọc API                                                      |
| Khối dài/ngắn tạo khoảng trống lớn                    | Bảng đơn dùng toàn chiều ngang; chênh lệch và phản hồi ở hai panel 1:1, không ép chiều cao rỗng                                    |
| Lặp việc nhập tay và tìm menu                         | Thanh tác vụ lập đơn điểm bán, nhập Excel, trợ lý lên đơn, kế hoạch bổ sung; đặt nhập NSX và xuất Excel nằm ở PageHead             |
| Không nổi bật ngoại lệ và lời hẹn khách               | Cam cho việc cần chú ý, đỏ cho chênh lệch/lỗi; tím cho phản hồi và trợ lý, xanh ngọc cho kho/nhận hàng                             |
| Màn nhỏ phải đọc nhiều nhãn lặp                       | Tổng quan mobile dùng tối đa bốn đơn tóm tắt, có đường mở danh sách; nếu nhiều chênh lệch có hai lối riêng cho đơn nhập và đơn bán |
| Bảng mobile bị độ rộng desktop ép ngày/tiền           | Bỏ độ rộng cố định ở td trên mobile; giữ độ rộng cột desktop                                                                       |

Thanh tên NPP lặp dưới header và các nút tải lại thủ công đã bỏ khỏi portal NPP. Việc sửa bản ghi vẫn giữ nguyên; đây không phải xóa các lệnh cập nhật dữ liệu nghiệp vụ. Hook dùng refresh khi focus, sau command và định kỳ 20 giây; lỗi vẫn có nút thử lại.

## Excel và trợ lý

- `/references?view=import` vào thẳng chọn dữ liệu/tải mẫu/chọn tệp/kiểm tra/nhập dòng hợp lệ. Dùng `/imports` hiện có, giữ idempotency và phản hồi lỗi theo dòng. NPP nhập cửa hàng hoặc tồn theo lô tùy quyền; tạo SKU vẫn thuộc quyền danh mục hiện tại. Chưa thêm import đơn hàng hoặc tự đoán ID nhân viên từ tên.
- `/reports?export=SELL_OUT` mở sẵn biểu mẫu xuất đơn bán. Người dùng chọn kỳ và gửi yêu cầu; dùng job xuất XLSX/PDF hiện có. Không gán file sell-in thành xuất sell-out.
- `/catalog?assistant=1` mở sẵn trợ lý, phân tích yêu cầu bằng parser danh mục phía server, xác nhận vào giỏ rồi quote giá. Không tự gửi đơn, không tuyên bố LLM/model đã train.
- Bộ chọn NPP bị khóa trước đây được bỏ ở catalogue khi tài khoản đã có NPP cố định. Backend vẫn kiểm scope; đây không phải cơ chế cấp quyền thương hiệu mới.

## Tham khảo

- Ảnh CloudGO người dùng cung cấp: nhóm nghiệp vụ rõ, bảng dày vừa đủ và nhấn màu theo vùng nội dung.
- [CloudGO — Hợp nhất luồng đơn](https://cloudgo.vn/giai-phap-hop-nhat-dms-va-omnichannel): tham khảo cách đưa đơn về hàng đợi và giảm nhập lại dữ liệu. Không sao chép quy trình tự duyệt, kết nối ERP hay omnichannel thành tính năng SYNERA chưa có.
- [Tài liệu CloudGO](https://docs.cloudgo.vn/faq): tham khảo lối tạo nhanh, import và hồ sơ dữ liệu. Không lấy số liệu quảng bá làm KPI của SYNERA.

## Kiểm tra

- TypeScript, architecture, 11 shared unit tests và production build đã qua.
- Bản tích hợp JAR tại `http://localhost:8080/#/dashboard` đã qua smoke test đăng nhập/queue/giỏ/quote/trợ lý/giao bù, 18 E2E checks và 12 design checks sau đóng gói. Bằng chứng `artifacts/npp-workbench-production.log`, `npp-workbench-e2e.log`, `npp-workbench-design.log`.
- Design suite 12 kiểm tra qua. Kiểm tra mới gồm các lối tắt, tải XLSX mẫu, parser/quote qua API, 8 màn × 1440/900/390px, reduced motion và console/API alerts.
- Trường hợp mobile nhiều chênh lệch hai kênh dùng fixture trong browser kiểm thử, không ghi DB. Fixture được gỡ và Overview được remount trước khi chụp API thật. Không đưa chuỗi tình huống cố định vào FE runtime.
- Nguồn bằng chứng: `.tools/sales-audit/npp-workbench-results.json`, `artifacts/npp-workbench-*.log`, `screens/npp-workbench-*.png`. Phạm vi review/render và giới hạn cập nhật tại [06](06-giao-dien.md).
- Kiểm tra format toàn repo còn 28 file sai format từ nền hiện tại; không sửa hàng loạt ngoài phạm vi. Các file thay đổi được kiểm riêng và đã qua.
- Reviewer kết luận `ship` cho phạm vi FE đã xem; ba phát hiện đã sửa gồm đích mở danh sách chênh lệch hai kênh, màu header bảng không có panel-title và độ rộng ô mobile. Đây không phải nghiệm thu mọi trạng thái/role hoặc app native.
- Migration/DB/user data được giữ nguyên trong lượt này. Chưa hoàn thiện cấp quyền SKU theo NSX/nhãn hàng, import đơn Excel hoặc provider AI thật.
