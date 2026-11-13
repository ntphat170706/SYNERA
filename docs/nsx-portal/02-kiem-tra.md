# Kiểm tra bản NSX ngày 13/11/2026

Bản chạy tích hợp: `http://127.0.0.1:8080`, workspace NSX (tài khoản quản trị local hiện có). Không đổi mật khẩu, không xóa DB/tài liệu gốc. Không commit, push hoặc gộp main trong đợt này.

## Bằng chứng

- Backend full verify: 158 kiểm thử, không thất bại, 76 skip trong lần đầy đủ trước bước tinh chỉnh cuối; skip gồm các suite MySQL chỉ chạy khi có môi trường riêng. Không tính skip là đạt.
- Sau phần bổ sung trách nhiệm lịch sử: 12 kiểm thử mục tiêu đạt (ManufacturerIntegrationTest 5, ModuleArchitectureTest 1, SalesReplacementIntegrationTest 6).
- ManufacturerMySqlTest: 5/5 đạt trên MySQL 8.4, schema `synera_npp_verify_nsx_*` riêng. Có kiểm tra tài khoản NPP/Sales bị chặn, quản lý miền Bắc không đọc NPP miền Nam, hồ sơ NPP đúng scope, kỳ ngày hợp lệ, nhiều NSX fail closed và việc chờ xử lý không biến mất khi nằm ngoài kỳ báo cáo.
- Frontend: 15/15 unit tests đạt, gồm 2 tình huống giữ đơn/lượt ghé của Sales cũ sau khi điểm bán bàn giao.
- Browser production: 18 kiểm tra luồng API–web đạt; 12 nhóm kiểm tra thiết kế đạt, cập nhật kỳ vọng rail NSX thành 76px theo mẫu người dùng, không nới kiểm tra tràn trang hoặc bảo mật.
- Bản NSX được chụp/kiểm tại 1440, 900 và 390px: điều hành, danh sách NPP, NPP360, điểm bán360, tổ chức, báo cáo, MCP, danh sách/chi tiết đơn; thêm các form tổ chức, tuyến, NPP, điểm bán và chính sách. Có tải CSV thật và chuyển tab lịch sử điểm bán.
- Phần sửa cuối kiểm tra cả độ rộng bảng nội bộ ở tablet (không chỉ độ rộng toàn trang), giữ cột thao tác hiện đủ; lịch sử đơn tách trạng thái/thời gian/nội dung. Chụp lại đúng các đường dẫn ảnh sau sửa.
- Architecture checks, build production và format các tệp đã sửa đạt. Format toàn repo còn 23 tệp lệch có sẵn; không tự format hàng loạt ngoài phạm vi. `git diff --check` trên mã thay đổi không có lỗi whitespace.

Log và ảnh trong `artifacts/nsx-*`, `.tools/nsx-review/` là bằng chứng local bị Git bỏ qua; không đưa ảnh phiên đăng nhập hoặc tài liệu gốc riêng tư vào PR. `docs/BACKEND-ENDPOINTS.md` đã sinh lại từ API local: 170 path / 207 operation.

## Rà thiết kế

Ảnh CloudGO người dùng cung cấp là nguồn bố cục, SYNERA giữ logo/tokens/dữ liệu và các trạng thái thực. Lượt review/documenter độc lập dừng do giới hạn sử dụng trước khi có kết luận cuối; phần kết luận cuối được tác nhân chính tự rà theo fallback của skill Impeccable, không nhận là phê duyệt độc lập. Hai điểm đã xử lý: cột thao tác tablet bị khuất và lịch sử đơn thiếu nhịp hàng. Tài liệu thiết kế chuẩn cập nhật tại `docs/DESIGN-SYSTEM.md`.

## Giới hạn còn lại

Đây là installation một NSX, chưa hoàn thành ownership/grants nhiều nhãn hàng trên toàn hệ thống. Không mô phỏng voucher/quà tặng tự động, HR/chấm công hay AI đã train bằng các nút không có nghiệp vụ. Tệp xuất mới là CSV UTF-8, không phải XLSX. Tham khảo đầy đủ tại [nguồn và màn hình](01-nguon-va-man-hinh.md).
