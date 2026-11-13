# Hồ sơ điểm bán và màn xử lý — 13/11/2026

## Thay đổi đã thực hiện

- Menu NPP có sáu khu vực chính, icon Tabler SVG có nguồn và giấy phép MIT. Các phần nhập hàng, giao bù, chuyến giao, Excel, trưng bày, hỗ trợ và báo giá đi từ ngữ cảnh; guard/backend quyền vẫn giữ. Kho và Giao bù có đường hỗ trợ cho vai kho.
- Menu trái có dải xanh nhạt–đậm đúng câu trả lời của người dùng. Không dùng gradient ở vùng dữ liệu.
- Hồ sơ điểm bán riêng gồm người liên hệ/điện thoại/địa chỉ, Sales, nhóm, tuyến và trạng thái. Đơn hàng, tương tác, lịch hẹn, ticket và trưng bày là tab; các query và phân trang server vẫn độc lập. Từ hồ sơ tạo lịch hẹn/ghi tương tác đã chọn sẵn điểm bán, có thao tác ghi kết quả hẹn. Không dựng đủ chức năng mạng lưới NPP của CloudGO thành trường giả.
- Chi tiết đơn thay danh sách khi mở. Xác nhận lượng có ngữ cảnh mã đơn/khách, tổng tiền, trạng thái, mặt hàng/đơn vị và các input lượng. Sheet dùng command hiện có; không tự chuyển trạng thái khi mở.
- Form NPP là sheet sát phải với header và nút Hủy/Lưu; form dài chia cột, GPS không bắt buộc được thu gọn. Ô lựa chọn rỗng không cho gửi thiếu đối tượng bắt buộc. Focus trap nhận cả Shift+Tab từ container ban đầu.
- Duyệt ảnh xem ảnh gốc qua API có xác thực cùng điểm xử lý/confidence/provider; dưới là điểm duyệt 0–100 và nhận xét. Thu hồi object URL/abort request khi đóng hoặc đổi phiên. Điểm/nhận xét chỉ ghi khi người có quyền gửi form; chưa duyệt các ảnh hiện có chỉ để chụp UI.
- Bỏ nút đóng khung trong ô xử lý và chữ phụ ở phân trang/footer NPP. Tách hóa đơn khỏi sổ công nợ, căn header/số tiền cùng phía. Nhãn khoản còn lại ở NPP là Còn phải trả, dữ liệu ledger không đổi.
- Logo đầy đủ dùng `public/assets/brand/synera-logo.png` hiện có, đối chiếu ảnh mới `D:\DU_AN_SYNERA\2aOboRCBBx2mYqmoW5epPzMWgc9Bfc00KbAc03hg.jpg`. Bỏ chữ SYNERA gõ bằng font UI; không vẽ lại, đổi màu hay chỉnh ảnh gốc.

## Nguồn tham khảo

- Mockup 26 — hồ sơ 360° outlet, `File ảnh.zip` của người dùng: thông tin tổ chức, lịch sử tương tác, kế hoạch chăm sóc và giao dịch liên quan. Ảnh gốc được giữ nguyên.
- [CloudGO — Màn hình chi tiết](https://docs.cloudgo.vn/en/man-hinh-tong-quan-chi-tiet): tham khảo cách nhóm hồ sơ và tab liên quan; endpoint tài liệu có lúc timeout, kết hợp mockup đã đọc trực tiếp.
- [shadcn — Sidebar blocks](https://ui.shadcn.com/blocks/sidebar): tham khảo navigation và thao tác theo ngữ cảnh, không thay app/router/component library.
- [Tabler Icons](https://github.com/tabler/tabler-icons): sáu SVG gốc. `public/assets/icons/tabler/sources.json` ghi URL/SHA256, kèm `LICENSE`.

## Kiểm tra và bằng chứng

| Nhóm kiểm tra                                  | Kết quả / phạm vi                                                                                                                                                   |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TypeScript, architecture, production build/JAR | Pass. Guard cho phép duy nhất gradient menu trái đã được người dùng chỉ định, các vùng dữ liệu còn nền phẳng                                                        |
| Shared tests                                   | 11/11 pass                                                                                                                                                          |
| CRM trên API thật                              | Tạo tương tác, tạo hẹn và ghi kết quả hẹn từ hồ sơ; chọn sẵn đúng điểm bán. Giao dịch kiểm thử được giữ lại, không xóa dữ liệu                                      |
| Responsive và sheet                            | 8 màn × 1440/900/390px; form tương tác, điều chuyển, chi tiết/xác nhận đơn, preview ảnh có xác thực, icon tải được, reduced motion                                  |
| Bản tích hợp production                        | 18 E2E checks pass, gồm receipt, năm phần hồ sơ độc lập, draft offline và session. 12 design checks pass                                                            |
| Sửa cuối                                       | Kiểm tra biên ô trong Reports không vượt panel tại1440/900, mobile không tràn; Shift+Tab lúc vừa mở sheet vẫn ở dialog. Logo và hồ sơ được chụp lại trên production |

Log: `artifacts/npp-crm-{check,final,e2e,design,types,architecture,unit,build,package}.log`. Dữ liệu kiểm thử giao diện tại `.tools/sales-audit/npp-crm-results.json`; ảnh `screens/npp-crm-*.png`. Ảnh mobile sheet chụp viewport 390×844, có thêm ảnh cuộn tới trường duyệt/footer; không dùng full-page lẫn nội dung nền làm bằng chứng sheet.

Reviewer độc lập đã xem bộ ảnh/source và báo ba lỗi: đường hỗ trợ của vai kho, Shift+Tab ban đầu và cột công việc Reports bị cắt. Các lỗi đã sửa; reviewer hết khả năng tiếp tục trước verdict cuối. Agent chính tự đối chiếu lại đúng các sửa cuối và ghi nhận pass hữu hạn bằng log `npp-crm-final.log`, không gán đây là phê duyệt độc lập toàn bộ hệ thống. Tài liệu thiết kế cũng được đối chiếu inline từ source và ảnh cuối vì bước phụ không còn khả dụng.

Detector chạy một lần: cảnh báo còn ở tam giác CSS `product-pack:before` kế thừa, không phải accent viền thẻ mới. Không sửa minh họa cũ ngoài phạm vi.

## Giới hạn

Đây là thay FE/UX dùng API hiện có, không thêm schema hay train AI, không ký/phát hành IPA mới. Provider chấm ảnh vẫn phản ánh nguồn thật trong dữ liệu. Quyền SKU theo NSX/nhãn hàng chưa được hoàn thiện bởi lượt này. Chưa UAT native iPhone hoặc chứng nhận mọi role/trạng thái. Toàn repo vẫn có format drift cũ; các file thay đổi kiểm riêng. Không commit, push, merge hoặc xóa dữ liệu người dùng.

Local đang chạy: `http://localhost:8080/#/crm`.
