# Backend chung và đồng bộ ba giao diện

Đây là thiết kế triển khai thay thế phương án dữ liệu kịch bản FE. Các bảng 01–03 ghi hiện trạng khảo sát trước thay đổi; mức hỗ trợ mới phải đối chiếu tài liệu này và kết quả kiểm thử cuối đợt.

## Nguyên tắc

- Một API, một dữ liệu gốc. Web NSX, portal NPP và app Sales chỉ trình bày/người dùng gửi command; backend kiểm quyền/scope/trạng thái/tồn và chống lặp.
- Order, stock movement, ledger, follow-up và notification là các trạng thái riêng; không dùng một biến FE để giả lập tất cả đã xong.
- API mới bổ sung, không bỏ trường cũ. Bản app chưa cập nhật vẫn đọc được đơn cũ; app mới dùng thêm thông báo và giao bù. Số đã nhận ban đầu giữ nguyên, số giao bù có trường riêng.

## Giao bù sell-out

Migration V119 thêm `sales_replacement` và `sales_replacement_line`, liên kết đơn/dòng sell-out gốc. Chứng từ có số lượng, kho, người đề xuất, người duyệt, hạn, thực nhận và kết quả báo khách; không thêm tiền bán/invoice/credit ledger.

| API | Hành vi |
|---|---|
| GET `/sales-replacements` và `/{id}` | Đọc theo NPP; Sales chỉ đọc đơn do mình phụ trách theo scope hiện hành |
| POST `/sales-replacements` | Người có SALES_CREATE/SALES_APPROVE đề xuất từ đơn DELIVERED_SHORT; lượng không vượt phần thiếu chưa phân bổ; tạo công việc Sales báo khách |
| POST `/{id}/approve` | SALES_APPROVE, người duyệt khác người đề xuất; giữ tồn FEFO theo baseFactor gốc, tất cả cùng transaction |
| POST `/{id}/ship` | WAREHOUSE_WRITE; chỉ khi đã duyệt, xuất tồn đúng một lần |
| POST `/{id}/receipt` | SALES_APPROVE; ghi thực nhận từng dòng, không vượt lượng xuất; giữ phần chưa bù để có thể đề xuất tiếp |
| POST `/{id}/cancel` | SALES_APPROVE; chỉ trước xuất, giải phóng giữ hàng |
| POST `/{id}/customer-feedback` | Sales phụ trách hoặc NSX trong scope gửi note và expectedStatus; từ chối phản hồi dựa trên trạng thái cũ, lưu interaction và hoàn tất công việc tương ứng; không thay thế chứng từ nhận hàng |

Mọi POST thay đổi dùng Idempotency-Key và hash body/actor/workspace. Khóa đơn gốc trước chứng từ giao bù; tính capacity trên dữ liệu hiện hành. Không chỉ kiểm số lượng tại FE. Canceled không chiếm phần thiếu; chứng từ đã nhận chỉ chiếm lượng thực nhận; chứng từ còn mở chiếm lượng đề xuất. Vì vậy một lần giao bù vẫn thiếu có thể được theo tiếp bằng chứng từ khác.

DTO đơn bổ sung `hasOutstandingShortage`; dòng đơn bổ sung `qtyReplacementReceived`, `qtyAvailableToReplace`. Không sửa qtyReceived ban đầu thành số gộp, không đổi lịch sử giao thiếu thành đã giao đủ giả. Báo cáo sell-out thực nhận cộng phần giao bù theo ngày nhận bù, giá/chiết khấu chốt của dòng gốc; không tăng số đơn hoặc thu lần hai.

Nếu Sales đã báo khách rồi NPP đổi tiến độ/kết quả (gồm hủy hoặc nhận thiếu tiếp), backend mở lại công việc báo khách. Phản hồi cũ vẫn giữ trong interaction/audit. Chi tiết chứng từ có lịch sử xử lý; đóng hàng và phản hồi khách là hai việc độc lập. API hoàn tất task chung không được bỏ qua ghi kết quả báo khách.

## Thông báo và lời hẹn

Sales tạo đơn → OrderCreated cùng transaction → outbox worker → notification cho NPP_OWNER/WAREHOUSE_NPP đúng scope và người Sales liên quan. Cập nhật trạng thái tạo SalesOrderUpdated. Đề xuất/tiến độ/kết quả báo khách có sự kiện Replacement riêng. Worker có dedup processed_event/event_key và retry, không phát thông báo trước khi giao dịch thành công.

Web đọc thông báo/hàng đợi bằng `useApi`, cập nhật định kỳ 20 giây và khi focus/command. App Sales có màn Thông báo & lời hẹn và Đề xuất giao bù từ đơn giao thiếu, tải khi focus/kéo và định kỳ khi màn thông báo mở. Đây là cập nhật trong app, chưa phải push notification của hệ điều hành khi app đóng. Nếu cần tức thời hơn, thay cơ chế vận chuyển bằng SSE/WebSocket/push sau, giữ nguyên contract/entity IDs.

Payload đơn có orderId/channel/code để các frontend mở đúng đơn. Không gửi raw JSON kỹ thuật thành nội dung thông báo cho người dùng.

## Chọn hàng và hỗ trợ lên đơn

POST `/catalog/order-assistance`: nppId, storeId tùy chọn, text. Backend đọc offerings đúng scope và trả dòng SKU/UOM/qty cùng danh sách chưa xác định. Parser chỉ nhận mặt hàng/đơn vị rõ ràng từ catalogue, không tự đoán giá hoặc tạo đơn. Người dùng xác nhận vào giỏ; `/pricing/quote` vẫn là nguồn giá và điều kiện. Đây là hỗ trợ bằng rule, chưa phải LLM/ASR hoặc model đã train.

Giỏ hiển thị ảnh minh họa theo loại hàng, đơn vị/quy đổi, nhóm hàng, tìm kiếm, bỏ dòng và giá server. Ảnh danh mục hiện lấy từ asset đã có trong dự án, không chứng nhận bao bì của SKU thực.

## Quyền và chính sách nhận hàng

- NPP_OWNER không còn ROUTE_WRITE; NSX chịu trách nhiệm cấu hình/phân công Sales. Test route/staff/handover dùng quản lý NSX; không nới guard để test cũ tiếp tục dùng NPP tạo nhân sự.
- Quyền custom cũng bị giới hạn theo base role tại thời điểm truy cập; cấu hình cũ không giữ quyền đã bị bỏ khỏi base role. NPP vẫn được phân nhóm chăm sóc cửa hàng trong scope qua SALES_APPROVE.
- Đơn cửa hàng do NPP thao tác lấy Sales phụ trách từ hồ sơ điểm bán, người thao tác được lưu audit; không gán NPP owner thành Sales để tính KPI.
- Tự xác nhận sell-in quá hạn mặc định tắt qua `receipt-auto-enabled=0`. Job vẫn nhắc nhận hàng; chỉ chính sách bật rõ ràng mới dùng legacy AUTO_RECEIVED. Không sửa trạng thái/snapshot lịch sử. Tham số 0/1 được kiểm tra như các tham số hệ thống khác.

## Chưa bao phủ

Giao bù hiện xuất từ kho đã gắn đơn gốc, chưa chọn kho khác; không tự đóng ticket nghiệp vụ chỉ vì giao bù xong hoặc đã báo khách. Các phương án miễn/giảm tiền, đổi sản phẩm hoặc trả hàng sell-out cần contract riêng, không được thực hiện bằng cách sửa ghi chú. Assignment có thời hạn, công nợ điểm bán đầy đủ, HR/events/community và provider AI thật vẫn nằm trong backlog. Không gọi đây là toàn bộ 20 module đã hoàn thiện.
