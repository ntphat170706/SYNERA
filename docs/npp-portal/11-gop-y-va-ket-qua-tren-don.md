# Chọn lọc góp ý: kết quả giao nhận và chăm sóc — 13/11/2026

Nguồn: góp ý mới người dùng đính kèm sau gói ZIP `20261113-022640`. Những kết quả H2/MySQL và nhận xét đối thủ trong góp ý là bằng chứng do người dùng cung cấp, không tự coi là kiểm thử của lượt sửa này. Lượt này tiếp tục dùng MySQL local đang có, không thay schema/backend.

## Đã áp dụng

- Chi tiết đơn bán có vùng Giao nhận và Chăm sóc trước bảng dòng hàng. Theo từng SKU/đơn vị: giữ thực nhận gốc, thực nhận bù và phần hàng chưa nhận. Chỉ gọi phần chênh là Còn thiếu khi đơn đã ghi nhận giao thiếu; không đánh dấu hàng đang giao là ngoại lệ.
- Trường hợp lượng yêu cầu cao hơn lượng xác nhận hiện riêng phần Chưa xác nhận. Đây là thông tin theo dõi trên dữ liệu có sẵn, chưa giải quyết luồng chấp thuận/hẹn bổ sung phần nhu cầu này.
- Mỗi phiếu giao bù còn hiệu lực có Sales phụ trách, hạn, trạng thái báo khách, kết quả có thể mở đọc và đường đến đúng chứng từ. Không suy ra đã báo khách từ việc hàng đã nhận bù. Lỗi tải dữ liệu có thử lại, không hiện số liệu mẫu thay thế.
- Không cộng số lượng của các SKU/đơn vị khác nhau thành một con số. Tiền và lịch sử đơn không bị tính lại. Phiếu hủy không được tính là việc báo khách đang mở trong vùng tóm tắt.
- Trang giao thiếu dùng tên người từ directory theo NPP, không hiển thị `Sales #3`. Directory không có tên thì dùng nhãn vai, không bịa danh tính hay người duyệt. Nêu đúng yêu cầu duyệt độc lập, không gán một người chưa được server phân công.
- Đổi tên trang thành Giao thiếu & phản hồi khách. Tổng quan đưa hàng đợi và bảng tác nghiệp lên trước dãy tác vụ nhanh ở cả DOM và bố cục, giữ đủ các lối tắt.

## Giữ thành công việc backend riêng

| Góp ý                                                      | Quyết định                                                                                                                                                                                                                                                               |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Đặt 10 nhưng chỉ xác nhận 8; cho một dòng bằng 0           | Cần trạng thái/phương án xử lý nhu cầu chưa đáp ứng, người theo và thông báo Sales. Không chuyển phần chưa cam kết thành thiếu thực nhận hoặc tự tạo giao bù                                                                                                             |
| Hãng A/B cùng NPP C                                        | Cần quan hệ cấp quyền và chủ sở hữu dữ liệu tại server, migration và kiểm thử cả đọc/ghi/xuất/tệp. Không chữa bằng lọc FE                                                                                                                                                |
| Kho dùng quyền riêng                                       | Cần tách quyền hành động trên đơn gốc, kiểm thử các vai kho/điều phối; không nới quyền kho sang duyệt đơn bằng một thay đổi UI                                                                                                                                           |
| Bánh quy hiển thị CHAI                                     | Đã xác nhận `CatalogFacade` seed SKU-005 dùng CHAI/LOC/THUNG như đồ uống. Chưa đổi dữ liệu trong lượt FE: phải giữ quy đổi và chứng từ cũ, sửa seed cùng migration/alias an toàn, rồi kiểm tra đặt lại/giữ tồn/nhận hàng. Không thay nhãn thành GÓI khi backend vẫn CHAI |
| Nhập hàng bằng bảng, đặt lại gần nhất, đưa gợi ý sang nháp | Phù hợp nhưng là luồng tương tác riêng; giữ catalog hiện tại trong lượt này. Khi bổ sung phải giữ quote server, giá hiện hành, đơn vị và giỏ chung                                                                                                                       |
| Công nợ phải thu điểm bán, đối chiếu từ chứng từ           | Giữ khoảng thiếu đã ghi ở 10; không cộng giá trị đơn thành sổ phải thu                                                                                                                                                                                                   |

## Kiểm tra

Audit `.tools/sales-audit/npp-feedback-check.mjs`: đọc API thật và mở đơn/giao bù/Tổng quan ở 1440, 768, 390px; kiểm tên, trạng thái báo khách, vị trí hàng đợi và không tràn ngang. Có thêm ca UI dùng response contract 8/10 + nhận bù 2 + báo khách để kiểm kết quả 0 và mở nội dung phản hồi; fixture chỉ nằm trong test, không ghi DB hoặc ship thành dữ liệu ứng dụng.

Ảnh: `.tools/sales-audit/screens/npp-feedback-{order,dashboard}-{1440,768,390}.png`. Source có cùng token/typography hiện tại; vùng tóm tắt hai cột desktop, một cột mobile, nền phẳng trung tính, stock/care giữ màu semantic. Không thêm animation lặp hoặc component bên ngoài.

Build web, đóng gói JAR và 11 unit tests đạt; 18 live E2E checks đạt trước sửa CSS tablet cuối (chỉ đổi breakpoint hiển thị). Kiểm tra design và audit trên JAR cuối được chạy lại. Gói ZIP cũ giữ nguyên để không thay bản đã bàn giao.

Reviewer độc lập đã yêu cầu sửa nút xử lý bị khuất ở 768px. Sau khi dùng danh sách gọn đến 1100px và chụp lại, reviewer chấm lỗi này **resolved**, `disposition: ship` trong phạm vi lỗi tablet và các ảnh được cung cấp; không chứng nhận toàn bộ backend/pilot. Màu/font/thành phần giữ hệ thống hiện tại, không tạo bộ token mới hoặc đổi tài liệu native.

Yêu cầu kế tiếp đã áp dụng chữ logo trắng trên menu, giữ biểu tượng gốc. App Sales không đổi source nghiệp vụ trong đợt này: typecheck và 24 unit tests đạt. ZIP bàn giao mới gồm web, backend/JAR và source Expo app, có checklist chạy hai vùng trên cùng backend; không kèm database máy hiện tại, .env thật hoặc IPA ký sẵn.
