# Giao diện Portal NPP

Mốc đối chiếu 12/11/2026. Đây là ghi nhận mở rộng hệ thống trắng–xanh hiện có, không phải một bộ nhận diện mới. Nguồn chuẩn: [Design System](../DESIGN-SYSTEM.md), `src/styles/tokens.css`, phần `.npp-portal` trong `src/styles/admin.css` và shared UI tại `src/shared/ui.tsx`.

## Phạm vi đã đối chiếu

- Tổng quan bỏ strip vai trò và nút cập nhật thủ công. Dock cho lập đơn điểm bán, nhập Excel, trợ lý và kế hoạch bổ sung hàng dẫn vào các luồng đã có.
- Bốn lane chọn worklist: đơn chờ xác nhận, xuất/giao hàng, nhận hàng NSX, giao nhận chênh lệch. Lane chọn dùng tone đậm; bảng công việc chiếm toàn chiều rộng, hai panel chênh lệch/phản hồi bên dưới chia bằng nhau. Không dùng banner giới thiệu hay chỉ số tăng trưởng giả.
- Trên mobile, worklist tổng quan hiện tối đa bốn tóm tắt đơn, gồm khách/trạng thái/mã/giá trị/hành động tiếp theo. Khi hàng đợi chênh lệch có hơn bốn dòng, hai link riêng mở đơn nhập chênh lệch và đơn bán giao thiếu, không bỏ mất một kênh giao dịch.
- Catalogue gom các quy cách vào một thẻ/SKU, có chọn đơn vị, giá, quy đổi, ảnh minh họa và điều khiển lượng trên cùng một hàng. Giỏ tách rõ bằng header xanh nhạt; nút gửi bị vô hiệu khi chưa có dòng hàng/báo giá phù hợp.
- Giao bù có bảng chứng từ, chi tiết lượng, trạng thái hàng, phản hồi khách và lịch sử. Hai nhóm cuối nằm cạnh nhau ở desktop và xếp dọc trên mobile; không gộp hoàn thành hàng với hoàn tất báo khách.
- PageHead chỉ giữ tên màn và thông tin ngữ cảnh thực sự cần. Các đoạn giới thiệu tính năng, giải thích server/snapshot và khẩu hiệu được bỏ khỏi màn làm việc. Nhãn trường, thông báo lỗi và cảnh báo nghiệp vụ vẫn có vai trò riêng.
- Màn `/references?view=import` tập trung nhập Excel, ẩn trình sửa danh mục và giữ backend tải mẫu/preview/import. `/reports?export=SELL_OUT` mở form xuất với loại đơn bán cửa hàng; `/catalog?assistant=1` mở trợ lý parser/quote hiện có. Lượt tinh chỉnh này không sửa backend hoặc phát hành IPA native mới.

## Đối chiếu với hệ thống hiện có

| Thuộc tính  | Kết quả đối chiếu                                                                                                                                                                      |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Thương hiệu | Giữ lockup SYNERA gốc và sidebar navy; không thêm font hay palette riêng cho feature                                                                                                   |
| Màu         | Giữ primary/accent cho đơn bán và thương hiệu; teal stock/inbound, violet care/assistant, amber chú ý, đỏ lỗi. Canvas xám trắng trung tính                                             |
| Typography  | Giữ Segoe UI/Arial/sans-serif; phân cấp PageHead/Panel/bảng rõ, số tabular và tiền căn phải                                                                                            |
| Độ nổi      | NPP scope dùng radius 12px và shadow hai lớp; đây là ngoại lệ scope có chủ đích so với card radius 5px của admin gốc                                                                   |
| Component   | Tái sử dụng PageHead, Panel, Table, Badge, SearchBox, ActionForm và router/shell hiện tại                                                                                              |
| Responsive  | Desktop worklist toàn chiều rộng, hai panel dưới bằng nhau; dock/lane thành hai cột dưới 1100px. Mobile worklist tối đa bốn tóm tắt; bảng khác bỏ width cột desktop, nhãn cột căn trái |
| Motion      | Feedback 180–220ms tại hover/thao tác/giỏ; reduced-motion tắt chuyển động. Không suy ra chất lượng animation chỉ từ ảnh tĩnh                                                           |
| Dữ liệu     | Thẻ số và chứng từ đọc API chung; ảnh danh mục được ghi là minh họa, không chứng nhận đúng bao bì SKU                                                                                  |

## Bằng chứng đã đọc

Documenter đã mở đủ 16 ảnh cuối của lượt workbench, thay cho gói `npp-final-*` trước đó. Các ảnh full-page được chụp từ đỉnh tài liệu sau khi tải nội dung, không có toast che màn. Gói cuối dùng dữ liệu API sau khi unmount/reset khỏi ca fixture trình duyệt:

| Màn              | Desktop                                                       | Mobile                                                       |
| ---------------- | ------------------------------------------------------------- | ------------------------------------------------------------ |
| Tổng quan        | `.tools/sales-audit/screens/npp-workbench-dashboard-1440.png` | `.tools/sales-audit/screens/npp-workbench-dashboard-390.png` |
| Đặt hàng/trợ lý  | `.tools/sales-audit/screens/npp-workbench-catalog-1440.png`   | `.tools/sales-audit/screens/npp-workbench-catalog-390.png`   |
| Chi tiết giao bù | `.tools/sales-audit/screens/npp-workbench-workflow-1440.png`  | `.tools/sales-audit/screens/npp-workbench-workflow-390.png`  |
| Nhập Excel       | `.tools/sales-audit/screens/npp-workbench-excel-1440.png`     | `.tools/sales-audit/screens/npp-workbench-excel-390.png`     |
| Kho              | `.tools/sales-audit/screens/npp-workbench-inventory-1440.png` | `.tools/sales-audit/screens/npp-workbench-inventory-390.png` |
| CRM              | `.tools/sales-audit/screens/npp-workbench-crm-1440.png`       | `.tools/sales-audit/screens/npp-workbench-crm-390.png`       |
| Công nợ          | `.tools/sales-audit/screens/npp-workbench-finance-1440.png`   | `.tools/sales-audit/screens/npp-workbench-finance-390.png`   |
| Đơn hàng         | `.tools/sales-audit/screens/npp-workbench-orders-1440.png`    | `.tools/sales-audit/screens/npp-workbench-orders-390.png`    |

Đọc source xác nhận header bảng không có Panel title vẫn kế thừa route tone; nhãn cột mobile nhận cùng màu chữ. Gói ảnh cho thấy bố cục 1440px và 390px, selector UOM và cả ba phần giảm–lượng–tăng, sidebar desktop, dock/lane, Excel nhập riêng và nhóm phản hồi/lịch sử. Ảnh đơn hàng mobile cuối hiển thị ngày/giờ thành chuỗi đọc được sau khi bỏ chiều rộng cột desktop; không còn vỡ thành cột ký tự hẹp. Đây là bằng chứng hữu hạn; không phải chứng nhận mọi vai trò/trạng thái hay nghiệm thu iPhone native.

Automation lượt workbench lưu tại `.tools/sales-audit/npp-workbench-results.json` và `artifacts/npp-workbench-check.log`: 24 tổ hợp route/viewport, lối tắt, tải template, trợ lý/quote và reduced-motion. Ca hơn bốn giao thiếu hỗn hợp dùng fixture cô lập trong trình duyệt, không ghi DB; ca này không chứng minh dữ liệu vận hành có sẵn. Documenter không chạy lại hoặc nâng kết quả thành chứng nhận toàn hệ thống. Luồng nghiệp vụ và giới hạn backend xem [04](04-backend-dong-bo.md).

## Giới hạn ghi nhận

- Portal đang mở rộng hệ thống CSS có sẵn. Không tái cấu trúc toàn bộ các lớp admin cũ hoặc sửa landing/native trong lượt tài liệu này.
- `docs/DESIGN-SYSTEM.md` là tài liệu chuẩn của repository hiện tại. Chưa tạo root `DESIGN.md` hay thay sidecar thiết kế mobile; không tự sửa khác biệt về tên/định dạng tài liệu như tác dụng phụ của tinh chỉnh FE.
- Chưa đo lại mọi tỷ lệ tương phản, thao tác bàn phím, công nghệ hỗ trợ hoặc cấu hình trình duyệt bằng lượt documenter này. Ảnh và đọc source không thay thế các kiểm tra đó.
- Tên người thực hiện có thể còn dạng ID ở lịch sử chứng từ; dữ liệu lịch sử và ghi chú kiểm thử trong ảnh là dữ liệu API tại thời điểm chụp, không phải hướng dẫn được nhúng vào UI.
