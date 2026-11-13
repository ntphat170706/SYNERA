# Backlog và cửa nghiệm thu Sales Mobile

## Các lát cắt để làm PR

| Thứ tự | Phạm vi | Phụ thuộc / kết quả cần chứng minh |
|---|---|---|
| PR-01 · lượt này | Phân tích nguồn, Expo riêng, auth native, list/detail đọc API, test session/ACK | Root web/backend không đổi; app đọc đúng scope, không có dữ liệu mẫu thay API; bản web preview không bị gọi là native đã kiểm thử |
| PR-02 · P0 | Bổ sung NSX quản lý và phân công Sales làm việc tại NPP; lịch sử bàn giao; đổi workspace native | Giữ scope NPP hiện có; bổ sung schema bằng migration mới khi cần, tương thích web cũ; không sửa ledger/order history hoặc nâng Sales thành quản trị NSX; người đi thay thấy ngữ cảnh cần thiết |
| PR-03 · P1 | Hôm nay/chuẩn bị/phiên ghé, GPS + ảnh, checklist versioned và khảo sát tồn | Contract server trước UI ghi; phân biệt attendance/visit; refusal/ngoài radius/thời gian/ảnh lỗi và checkout thiếu điều kiện |
| PR-04 · P1 | Giỏ hàng, quote, điều kiện giao nhận, đơn gần đây, theo dõi từng lượng | UOM và giá server, visit linkage, idempotency; SKU hết hàng/ưu đãi đổi; loại giao ngay/dùng thử chỉ mở khi backend hỗ trợ |
| PR-05 · P1 | SQLite outbox + media queue + sync centre | Ownership/workspace, dependency, transaction, retry/backoff, ACK từng item, PRICE_CHANGED/REJECTED giữ nháp; khôi phục sau app kill/upgrade |
| PR-06 · P1 | Ticket/nhu cầu/lý do chưa mua/lời hẹn; request điểm mới/sửa toạ độ | Có đầu mối/hạn/duyệt/audit; không dùng createStore trực tiếp thay quy trình request khi thiết kế mới yêu cầu duyệt |
| PR-07 · P1 | Attendance đầu/cuối ngày, lịch sử, nghỉ/thay lịch; báo cáo ngày/KPI | Ngày Asia/Ho_Chi_Minh, ca/ngày lễ, không đồng nhất với visit; báo cáo đã nộp giữ nguyên; KPI có nguồn và không giả doanh thu |
| PR-08 · P2 | Push, bản đồ/định tuyến nâng cao, AI đã đánh giá, quà/voucher, mẫu/dùng thử, giao bù nâng cao | Có provider/contract/dữ liệu đánh giá; không dùng mock để tự duyệt, thưởng hay chi tiền |

Các PR có thể tách nhỏ hơn theo module và reviewer, không cần đợi xây xong mọi chức năng mới review. Không merge đổi mô hình backend chỉ từ việc đã xem được app.

## Quyết định đã chốt / còn cần xác định

- Đã chốt: React Native/Expo riêng; NSX quản lý Sales, Sales được phân công làm việc tại NPP; NPP đáp ứng đơn; điểm bán không bắt buộc app riêng. `nppId` là scope tác nghiệp hợp lệ, không phải kết luận NPP quản lý nhân sự. Giữ backend có thẩm quyền.
- Cần chốt trước PR-02: quan hệ NSX–điểm–NPP trong từng vùng, một điểm có nhiều NPP đáp ứng hay không, thời hạn thay thế và quyền đọc đơn do người cũ lập.
- Cần chốt trước PR-03/07: tiêu chí chấm công, ca/ngày lễ, radius/độ chính xác, ảnh bắt buộc ở bước nào, ngoại lệ và người duyệt. Không tự chọn chính sách gian lận thay chủ đề tài.
- Cần chốt trước PR-04: giao ngay có hàng tại xe/nguồn nào; dùng thử có thu tiền/định mức gì; lịch và người xác nhận đáp ứng, điều kiện thanh toán. Chưa lấy toàn bộ mẫu Cloudocs làm phạm vi SYNERA mặc định.
- Cần chốt trước pilot: thiết bị iPhone/Android, tài khoản build, cấu hình API truy cập từ thiết bị, dung lượng/retention ảnh và quy trình mất thiết bị.

## Bộ ca UAT bắt buộc

1. Sales đăng nhập/refresh/OTP khi yêu cầu; admin/NPP không vào app Sales; server thu hồi quyền thì request tiếp theo bị từ chối. Đổi phiên khi request đang chạy không lộ dữ liệu cũ.
2. NSX khác, NPP khác, Sales khác, điểm không được giao và phân công đã hết hạn: từ chối cả read/write/file. Không tin id do client gửi.
3. MCP ngày thường/ngày nghỉ, không có tuyến, đổi người tạm, điểm mới, cửa hàng đóng cửa, ghé ngoài lịch. Có thứ tự và dấu vết quản lý.
4. Chấm công ngày khác check-in điểm; nghỉ nửa ngày, quên ra ca, làm qua ngày, đồng hồ thiết bị sai, người xác nhận ngoại lệ.
5. GPS không có quyền, accuracy kém, toạ độ điểm thiếu, ngoài radius, ảnh chưa upload hoặc upload hỏng, chuyển background khi xin quyền; không tự tạo chứng cứ giả.
6. Lượt không mua, kiểm tồn có số 0, đơn vị khác nhau, không chụp được ảnh, bỏ checklist có lý do, quay lại không mất nháp. Không cho checkout giả khi server chưa ACK.
7. Quote thay giá/ưu đãi, đổi UOM, hết hàng, hạn mức/chính sách bị đổi, nhấn gửi liên tục, request mất phản hồi. Cùng key không sinh đơn thứ hai.
8. Offline tạo nhiều nháp, restart/app kill, 24h cache hết hạn, đổi user/workspace, logout offline, media còn phụ thuộc; chỉ ACK hợp lệ retire đúng nháp, không xoá cả kho dữ liệu.
9. Đặt 10, xác nhận 8, xuất 8, nhận 7 và 1 hỏng: hiển thị riêng, liên kết ticket/phương án bù; không tự nhận đủ hoặc thu tiền lại phần bù.
10. Khách phản ánh → ticket → người xử lý/hạn → phản hồi → khách biết kết quả; đổi Sales không làm mất cam kết hoặc đóng ticket.
11. Request điểm mới/sửa GPS bị trả bổ sung/từ chối/trùng; chỉ bản đã duyệt mới đổi danh mục chuẩn. Có audit trước/sau.
12. Báo cáo phân biệt số đơn/giá trị đơn/thực nhận/thu tiền, có dữ liệu gốc và trạng thái kỳ. Submit lặp không nhân đôi, bản đã nộp không ghi đè.
13. iPhone light/dark, Dynamic Type lớn, VoiceOver, bàn phím, safe area, tab/back/swipe, quay lại từ camera, mạng chập chờn và dung lượng thấp. Thiết bị thật cho GPS/camera; simulator cho độ phủ layout.

## Điều kiện bắt đầu pilot

P0 có backend thực, luồng chính có native persistence và lỗi/khôi phục, MySQL integration không skip, app được kiểm trên iPhone thật hoặc simulator với giới hạn phần cứng ghi rõ, không dùng AI/OTP/provider mock làm bằng chứng tích hợp thật. Không thay câu “build thành công” cho nghiệm thu nghiệp vụ.
