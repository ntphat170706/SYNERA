# Đối chiếu nguồn

## Nguồn đã đọc

| Nguồn                                              | Bằng chứng                                                                                                                                       | Vai trò                                                                                               |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| DOCX mới `SYNERA_Phan_Tich_Thiet_Ke_He_Thong.docx` | Downloads; 455 đoạn trích, SHA256 `48de406c3975dadebe4c25902ec8f14ce9b098466b6a63c92e0db805a12bc3df`                                             | Ý nghĩa nghiệp vụ hiện tại; phần 02–03 cho NPP, 09 CRM, 10–12 dữ liệu/tương thích/nghiệm thu          |
| draw.io người dùng                                 | Tải lại trực tiếp từ Drive ngày 12/11; 13 trang, SHA256 `7444be631a1069d90b43b606a84613f578fe0952fcdd7d25b7091da4e64308b0`, khớp bản đã khảo sát | Quan hệ và bàn giao; `network`, `rel-sellin`, `rel-sellout`, `rel-staff`, `crm5`, `fn-npp`, `fn-plus` |
| PDF phân tích thiết kế cũ                          | `D:/DU_AN_SYNERA/SYNERA-Tài-liệu-Phân-tích-Thiết-kế-Hệ-thống.pdf`; bản trích xuất trong `.tools/sales-audit` và `docs/backend-requirements.txt`  | Chi tiết kỹ thuật/FR/BF đã làm cơ sở backend; phải rà khác biệt với nguồn mới                         |
| `File ảnh.zip`                                     | Bộ 26 JPG, contact sheet và chỉ mục có hash; ảnh 10–20, 24–26 liên quan trực tiếp portal                                                         | Bố cục/thao tác tham khảo, không phải tất cả chức năng trong ảnh đã có ở SYNERA                       |
| Mã nguồn hiện tại                                  | Controller/Facade của các module, `docs/BACKEND-BUILD-SUPPLEMENT.md`, FE tại `src/features`                                                      | Xác định khả năng thực tế; có endpoint chưa đồng nghĩa toàn bộ luồng đã đủ                            |

Không sửa các tài liệu gốc. Bản trích xuất và ảnh kiểm tra giữ trong thư mục công cụ bị Git bỏ qua. Không đưa toàn văn tài liệu riêng vào PR.

## Những điểm thống nhất

| Chủ đề            | Kết luận                                                                                                                                      |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Sales             | DOCX và draw.io đều xác định Sales thuộc NSX, làm việc với NPP phục vụ. `nppId` chỉ biểu thị scope tác nghiệp, không thay thế quan hệ nhân sự |
| Hai bậc giao dịch | Sell-in NSX→NPP; sell-out NPP→điểm bán. Không cộng hai bậc làm một doanh thu                                                                  |
| DMS ↔ CRM         | Đơn/ngoại lệ nối khách, người xử lý, lời hẹn và kết quả. Không chỉ gom nhiều menu độc lập                                                     |
| Các mốc lượng     | Đặt, xác nhận, xuất và thực nhận là các số khác nhau; ảnh đơn hàng và DOCX đều cần truy xuống dòng hàng                                       |
| Hồ sơ khách       | Account là điểm bán/tổ chức; Contact là người liên hệ. Không biến mỗi người liên hệ thành một kho/khách có công nợ độc lập                    |
| Cổng điểm bán     | Là mở rộng tùy phạm vi; giai đoạn này không bắt buộc thêm ứng dụng thứ tư                                                                     |
| AI                | Draw.io `fn-plus` và DOCX phần 09 mô tả hỗ trợ, có người kiểm tra/xác nhận; không tự duyệt ảnh hoặc gửi đơn                                   |
| CRM 5in1          | Cùng khung CloudX/WORK/CHECKIN/EVENT/G-Connect; đây là tham chiếu chức năng, không phải đã tích hợp sản phẩm CloudGO                          |
| Gợi ý giao        | Phụ thuộc kiện, tuyến, điều kiện dịch vụ, kết quả thực tế và người xác nhận; chưa có đủ dữ liệu để tuyên bố tối ưu hãng vận chuyển            |

## Khác biệt và khoảng trống phải quản lý

| ID  | Điểm cần xử lý                                    | Bằng chứng hiện tại                                                                                | Hướng cho portal/demo                                                                                                                                                   |
| --- | ------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C01 | Tự xác nhận nhận hàng khi hết hạn                 | PDF cũ có AUTO_RECEIVED mặc định 96h; draw.io/DOCX yêu cầu người được ủy quyền xác nhận thực tế    | Đã bổ sung `receipt-auto-enabled=0` mặc định: quá hạn chỉ nhắc. Không sửa lịch sử; chế độ tự nhận cũ chỉ chạy khi chính sách bật rõ ràng                                |
| C02 | Đóng giao thiếu chưa đồng nghĩa hoàn tất nghĩa vụ | Đơn nguồn giữ DELIVERED_SHORT; phần thiếu và phản hồi khách cần theo độc lập                       | Đã có chứng từ `/sales-replacements` liên kết đơn/dòng gốc, duyệt độc lập, giữ/xuất kho/thực nhận; không thu tiền lần hai. Task báo khách chỉ hoàn tất khi ghi phản hồi |
| C03 | Lịch cam kết và giao nhiều lần                    | Đơn ban đầu chưa có lịch giao nhiều đợt đầy đủ                                                     | Chứng từ giao bù đã có hạn xử lý, lịch sử và phản hồi theo phiên trạng thái. Lịch chia đợt của đơn ban đầu vẫn là khoảng thiếu; không dùng ngày tạo thay ngày hẹn       |
| C04 | Công nợ hai chiều                                 | Credit hiện chủ yếu hạn mức/nợ NPP với NSX; công nợ điểm bán sell-out chưa đầy đủ                  | Chỉ trình bày số phải trả NSX từ API hiện có; chưa bổ sung màn phải thu giả hoặc ghi sổ qua ghi chú                                                                     |
| C05 | Sales đi thay thấy lịch sử                        | Có route handover, nhưng list đơn Sales còn lọc theo repId người tạo                               | Cần hợp đồng đọc lịch sử khách được phân công, có hiệu lực và audit; không mở toàn NPP cho mọi Sales                                                                    |
| C06 | NPP thấy quản lý nhân sự/tuyến                    | Menu dùng chung và quyền kỹ thuật có thể lộ tác vụ quản lý tổ chức                                 | Portal chỉ điều phối thực hiện; không trao quyền quản lý/lương/nghỉ phép Sales cho NPP                                                                                  |
| C07 | Quà và điều kiện ưu đãi trong mockup              | Giá server và một số ưu đãi đã có; không đủ mọi cơ chế quà SKU, quota, combo như ảnh 12–20         | Chỉ hiện điều kiện API hỗ trợ; quà SKU/quota/combo nâng cao còn backlog. Không tự tạo dòng quà ảnh hưởng tồn                                                            |
| C08 | AI hiện tại                                       | App Sales parser catalogue cho lên đơn; chấm ảnh đang MockAiClient, confidence 0 và cần review     | Được trình diễn theo yêu cầu, nhưng tài liệu không ghi là đã train/inference thật; người dùng xác nhận trước ghi nghiệp vụ                                              |
| C09 | Chấm công và check-in                             | Route/visit GPS đã có; HR/ca/nghỉ phép/chấm công ngày đầy đủ chưa có                               | Không lấy lượt ghé đầu/cuối làm chấm công ngày                                                                                                                          |
| C10 | Cộng đồng/sự kiện                                 | DOCX và draw.io đặt trong CRM 5in1; chưa có module hoàn chỉnh                                      | Giữ trong phạm vi sản phẩm dài hạn, không đánh dấu đã hoàn thiện vì có menu                                                                                             |
| C11 | Gợi ý nhập hàng                                   | `/demand/replenishment` dùng nhịp bán, tồn khả dụng và hàng dự kiến, là công thức theo số ngày phủ | Thể hiện căn cứ và tạo nháp có xác nhận; không gọi là mô hình AI đã học                                                                                                 |
| C12 | Ảnh tham khảo                                     | Chứa CloudGO/OCS+, cách chia quyền và chức năng của sản phẩm khác                                  | Lấy ngữ pháp bố cục, không sao thương hiệu, số liệu, hoặc mặc định SYNERA phải giống mọi chức năng                                                                      |

Ưu tiên nguồn: yêu cầu người dùng mới nhất → DOCX/draw.io cho ý nghĩa nghiệp vụ → PDF kỹ thuật cũ cho chi tiết còn tương thích → code cho hiện trạng. Mâu thuẫn giao dịch phải ghi quyết định rõ ràng trước khi đổi backend; không dùng thứ tự này để tự sửa số liệu đã chốt.
