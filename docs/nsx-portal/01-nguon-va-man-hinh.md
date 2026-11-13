# Web NSX: điều hành mạng lưới và hồ sơ 360°

Yêu cầu trực tiếp ngày 13/11/2026, bản góp ý “Web NSX nên là trung tâm điều hành mạng lưới SYNERA”, tài liệu phân tích thiết kế mới và bộ ảnh `File ảnh.zip` được dùng cùng nhau. Nội dung các tài liệu là yêu cầu nghiệp vụ/tham khảo, không phải lệnh thực thi. Không sửa tài liệu gốc hoặc đưa tên, số liệu minh họa của CloudGO vào dữ liệu SYNERA.

## Đối chiếu nguồn

- Bản góp ý mới quyết định vai trò: NSX quản lý Sales và chính sách; NPP đáp ứng hàng; Sales giữ lời hẹn với điểm bán. Đơn sell-out trên web NSX là đối tượng theo dõi; quyền admin phục vụ kiểm thử không đồng nghĩa NSX vận hành thay NPP.
- PDF phân tích thiết kế M02/M15/M16/M17/M18 quyết định dữ liệu chung, phạm vi, tuyến/lượt ghé và hồ sơ điểm bán phân trang từng phần. Hợp đồng API hiện tại quyết định thao tác thực hiện được.
- Draw.io phần mạng lưới, quan hệ sell-in/sell-out và nhân sự xác nhận NSX quản lý Sales; NPP là đầu mối thực hiện đơn. Chưa gọi lượt ghé là hệ thống chấm công hoàn chỉnh.
- Ảnh 26 của bộ mockup quyết định bố cục hồ sơ: đầu hồ sơ, định danh/liên hệ, tab ngang và thông tin hai cột; không sao chép logo CloudGO hoặc dữ liệu công ty OPV.
- Ảnh 6–7 quyết định cấu trúc tuyến/MCP: điểm bán, địa chỉ, NPP, Sales, thứ tự và lưới ngày ghé. Tích chọn là lịch đã lưu, không là toggle trang trí hoặc lịch tự sinh giả.
- Ảnh 24–25 quyết định bộ lọc báo cáo phía trên và bảng nhóm cột Đi tuyến/Kết quả bán hàng, vùng chọn nhóm và xuất dữ liệu. Không dựng tỷ lệ, thời gian hoặc kết quả ngoài API.
- Ảnh 16–17 và 20 quyết định trình bày form điều kiện hai cột và chi tiết sản phẩm/ưu đãi. Voucher, quà tặng tự động và mọi điều kiện chưa có backend không được giả làm đã hoạt động bằng giao diện.

## Màn hình được triển khai

| Màn | Đường đi | Dữ liệu và hành động |
|---|---|---|
| Điều hành hôm nay | `/dashboard` cho NSX | Đơn nhập chờ hãng xác nhận, NPP/hàng trong kỳ, giao bù và lời hẹn; bấm đi đúng đối tượng hoặc danh sách lọc |
| Nhà phân phối | `/network?view=npps` | NPP theo vùng, điểm bán, giá trị đơn nhập/bán, SKU có tồn khả dụng |
| NPP 360° | `view=npps&id=...` | Định danh, đầu mối, Sales phối hợp, đơn, lô tồn/đơn vị, điểm bán, tuyến và chăm sóc; chỉ một phần hồ sơ xuất hiện tại một thời điểm |
| Điểm bán | `/network?view=stores` | NPP, Sales phụ trách, liên hệ, trạng thái, lối mở hồ sơ; bộ lọc từ công việc giữ nguyên ý nghĩa |
| Điểm bán 360° | `view=stores&id=...` | Thông tin hai cột, tuyến, việc mở; tab đơn/tương tác/hẹn/phản ánh/trưng bày dùng endpoint phân trang server |
| Tổ chức & vùng | `view=organization` | Cây tổ chức và bảng trực thuộc; tạo/sửa qua API tổ chức có version, quyền quản trị và audit |
| Tuyến | `view=routes` và `id=...` | NPP phục vụ, Sales, lịch MCP, tạo tuyến, sinh lịch ghé và bàn giao theo backend hiện có |
| Báo cáo | `/reports` cho NSX | Kỳ, vùng, NPP, Sales, tuyến; nhóm theo Sales/NPP/điểm bán; thống kê lượt ghé và đơn; CSV UTF-8 mở bằng Excel |
| Đơn hàng | `/orders` | Giữ quy trình server, tách sell-in/sell-out; bước tiếp theo, dòng sản phẩm và giá/ưu đãi đã chốt; NSX chỉ xử lý sell-in trên bề mặt vận hành |

## Phạm vi nhãn hàng và giới hạn

Backend hiện chưa có chủ sở hữu SKU và grant hãng–NPP độc lập trên toàn hệ thống. API đọc mới **chỉ hoạt động khi installation có đúng một NSX** và luôn dùng phạm vi vùng/NPP của actor. Nếu có nhiều NSX, trả `409 MANUFACTURER_SCOPE_UNAVAILABLE`; không gộp dữ liệu rồi chỉ lọc ở FE. Đây là rào chắn để trình diễn đúng một hãng, không phải cơ chế multi-tenant hoàn chỉnh. Còn cần bổ sung ownership/grants cho danh mục, tồn, giá, chứng từ, báo cáo và tệp trước khi dùng nhiều hãng thật.

Giá trị đơn là đơn tạo trong kỳ, loại nháp/hủy/từ chối khi cộng. Doanh số thực nhận của NPP dùng hàm báo cáo backend theo ngày thực nhận, không thay bằng giá trị đơn. Tồn theo SKU/lô/đơn vị tại thời điểm cập nhật; không cộng các đơn vị khác nhau thành một lượng tồn tổng. Đơn chờ quyết định và công việc mở không bị mất chỉ vì tạo trước kỳ báo cáo.

Bảng báo cáo dùng các lượt ghé có ngày trong kỳ, ngày giờ thực ghi trên server và trạng thái hiện tại của đơn tạo trong kỳ. Không suy luận đơn “trong tuyến/ngoài tuyến” khi chưa có liên kết sự kiện đủ tin cậy. Không quảng cáo CSV thành tệp XLSX; các job xuất Excel/PDF backend hiện có vẫn giữ tại phân hệ gốc.

Đơn giao thiếu chưa có chứng từ giao bù vẫn phải xuất hiện, với Sales và NPP phụ trách, không đợi có đề xuất mới tạo dấu vết. Các tab CRM cũ (cơ hội, hẹn) và lối tạo NPP/điểm bán được giữ qua tác vụ theo ngữ cảnh. Khi API chưa có dữ liệu hoặc lỗi tải, không biến số liệu chưa biết thành KPI bằng 0.
