# Cầu nối app và đóng việc sau bán — 13/11/2026

Nguồn: nhận xét mới sau khi người dùng chạy gói `SYNERA-NPP-Sales-20261113-025851.zip`. Kết quả chạy do người dùng cung cấp được đối chiếu lại với mã, không thay bằng chứng kiểm thử của lượt sửa này.

## Đã sửa

- Metro và gateway riêng dùng chung `apps/sales-mobile/scripts/mobile-policy.cjs`, nhận đường dẫn đầy đủ `/api/v1`. Ba POST đề xuất giao bù, ghi phản hồi khách và đọc thông báo được đi qua; browser session, quản lý người dùng và duyệt đơn vẫn bị chặn. API backend tiếp tục kiểm quyền, scope và idempotency. Metro đã được chạy thật trên cổng 5175: ba request không có token tới backend trả 401, thay vì bị cầu nối chặn 405.
- Mở nội dung thông báo được tách khỏi việc đánh dấu đã đọc. Lỗi read không chặn mở đơn/phiếu; hiển thị lỗi có thể thử lại. Ghi phản hồi giữ key riêng để retry, không bị request read thay key.
- Inbox ưu tiên Công việc đang mở rồi phiếu cần theo dõi; thông báo nằm sau các phần việc. Phiếu nhận đủ/đã báo khách và phiếu hủy được ẩn khỏi danh sách đang xử lý mặc định, có nút xem lại. Khi mở phiếu, ngữ cảnh và form lên đầu màn hình. Phiếu hiển thị tên điểm bán theo dữ liệu được cấp.
- Nút tạo đề xuất trên chi tiết đơn chỉ xuất hiện khi có dòng `qtyAvailableToReplace > 0`. Liên kết theo dõi/báo khách vẫn giữ khi lượng thiếu đã được phân bổ hết.
- Việc gốc giao thiếu chỉ hoàn thành khi tất cả dòng đã được nhận bù đủ và mọi phiếu liên quan đã có phản hồi khách. Trạng thái hàng và liên hệ vẫn độc lập. Không đổi thực nhận ban đầu, tiền, tồn, ledger hoặc trạng thái lịch sử của đơn.
- Sự kiện đến trễ cũng chạy lại kiểm tra sau khi tạo task. Nhịp rà soát công việc hiện có kiểm tra lại các root còn mở sau nâng cấp. Đây là hoàn tất task phục vụ giao thiếu; không tự đóng ticket đang cần xác minh hay đối chiếu tài chính.
- Task mới dùng `SALES_DELIVERY_SHORT` để phân biệt với sell-in có thể trùng ID số. Task cũ chỉ được nhận dạng là sell-out khi có notification cùng lần ghi với payload điểm bán và không có nguồn sell-in mơ hồ. Bản ghi không xác định được giữ lại để rà, không tự đóng theo ID đơn đơn thuần. Mọi đóng tự động có audit hệ thống.
- Portal ưu tiên dòng kết quả hiện tại Đã giao bù đủ và kết quả báo khách; trạng thái Giao thiếu trên toolbar được ghi rõ là lịch sử. API `GET /sales-replacements?orderId=` lọc tại truy vấn server và kiểm quyền đơn trước, giúp chi tiết đơn không tải toàn bộ phiếu.

## Bằng chứng kiểm tra

- App: typecheck, 25 unit tests; export web thành công. Có ca policy cho đúng prefix và các đường bị cấm; gateway vẫn kiểm việc bỏ cookie và giữ token/key/body.
- H2: bộ SalesReplacementIntegrationTest và ModuleArchitectureTest đạt 5 bài, gồm giữ tiền, scope, idempotency, đóng việc và sự kiện đến trễ.
- MySQL 8.4 thật: bộ SalesReplacementMySqlTest đạt 4 bài trên schema `synera_npp_verify_followup_*` mới riêng, không dùng database ứng dụng. Ca bộ lọc theo đơn và từ chối NPP khác được chạy trong cùng suite.
- Expo web qua Metro thật: đăng nhập Sales, mở Inbox, xem lịch sử và render ở 390px không có lỗi JS. Ảnh `.tools/sales-audit/screens/followup-mobile-inbox-390.png`. Đây là bằng chứng web của ứng dụng Expo, chưa thay UAT GPS/camera/SecureStore/SQLite trên iPhone thật.
- Log: `artifacts/followup-{mobile-tests,mobile-ui,backend-tests,mysql-tests}.log`. Kiểm tra full verify, production browser và đóng gói được cập nhật theo kết quả cuối, không lấy số từ bản ZIP cũ.

## Các phần chưa được coi là đã sửa

Xác nhận ít hơn lượng yêu cầu, chấp nhận một dòng bằng 0 và quyết định hẹn bổ sung/giảm/hủy còn cần luồng nhu cầu riêng với phương án, người phụ trách và phản hồi khách. Không đưa phần chưa cam kết vào giao bù đã xuất hàng. Phạm vi hãng–NPP, quyền đội kho, quy cách SKU bánh và nghĩa vụ phải thu điểm bán vẫn theo khoảng thiếu ở 10–11. Bộ lọc theo hạn, nhập nhanh, đặt lại và kết nối phần mềm kế toán là các nhóm công việc riêng, không tuyên bố hoàn thiện nhờ những thay đổi trên.

Không đổi schema hay thêm model AI trong lượt này. Không xóa database, dữ liệu người dùng hoặc gói ZIP trước đó.

## Kết quả cuối

- `backend verify`: 150 test được báo cáo, 74 skip theo điều kiện môi trường, 76 thực chạy không lỗi; không gọi đây là 150 bài đạt. Bộ MySQL 8.4 riêng đạt 4/4 như ghi trên.
- JAR cuối chạy cùng MySQL ứng dụng tại cổng 8080. Audit đọc 3 kích thước, 18 E2E và 12 design checks đạt; 11 unit test web đạt. App typecheck và 25 tests đạt; export web cuối đạt.
- UI cuối được chụp lại từ bundle Expo web xuất mới qua gateway tại 5181. Trạng thái rỗng dùng danh sách phiếu sau lọc. Reviewer đã chấm finding này resolved, `disposition: ship` trong phạm vi screenshot 390px, không chứng nhận native.
- Format toàn repo vẫn có 24 tệp lệch cũ, không sửa hàng loạt để che. Các file thay đổi được format riêng. Backend/frontend/app nằm trong gói mới; không sửa IPA cũ trên điện thoại.
