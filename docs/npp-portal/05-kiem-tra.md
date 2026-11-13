# Kiểm tra bản NPP ngày 12/11/2026

## Phạm vi đã thực hiện

Bản tích hợp FE/backend được đóng gói và chạy tại `http://localhost:8080/#/dashboard`. Smoke test cuối trên bản production tích hợp đã qua: đăng nhập NPP, ba hàng đợi, các nút lượng hiện đủ, quote API, phân tích yêu cầu lên đơn và chi tiết giao bù (`artifacts/npp-final-smoke.log`).

- Backend chung cho đề xuất giao bù, duyệt độc lập, giữ/xuất tồn, thực nhận và phản hồi khách theo trạng thái hiện hành.
- Thông báo Sales → NPP và cập nhật tiến độ quay lại người Sales phụ trách qua transactional outbox.
- Portal NPP đọc/ghi API; danh mục đặt hàng theo SKU và quy cách, giá từ quote, hỗ trợ phân tích yêu cầu có xác nhận.
- FE theo hệ màu trắng–xanh hiện hữu: phân cấp các khối đậm/nhạt, bóng có độ lệch, bảng gọn và vùng chi tiết riêng. Bỏ câu giới thiệu/hướng dẫn sử dụng khỏi màn làm việc; giữ lỗi, cảnh báo và thông tin quyết định nghiệp vụ.

## Bằng chứng

| Kiểm tra                                         | Kết quả                                                                                                             | Bằng chứng local                                                                     |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Backend verify                                   | 150 tổng: 76 pass, 74 skip (70 Docker MySQL + 4 suite MySQL portable bật riêng)                                     | Log verify trong artifacts; không gọi skip là pass                                   |
| API integration sau sửa giới hạn quyền tùy chỉnh | 35/35 pass                                                                                                          | `artifacts/npp-final-role-verify.log`                                                |
| Giao bù trên MySQL 8.4 portable riêng            | 4/4 pass; schema `synera_npp_verify_20261112_185952`                                                                | `artifacts/npp-mysql-test.log`, `.tools/sales-audit/npp-mysql-result.json`           |
| Luồng liên ứng dụng trên runtime thật            | 6 nhóm kiểm tra: tạo đơn Sales, notification NPP, xác nhận/giao/nhận thiếu, giao bù, báo khách và các màn liên quan | `.tools/sales-audit/npp-live-results.json`; đơn #6, chứng từ #1 được giữ để truy vết |
| Bộ E2E production                                | 18 kiểm tra pass                                                                                                    | `artifacts/npp-e2e.log`                                                              |
| FE shared unit tests                             | 11/11 pass                                                                                                          | `npm test`                                                                           |
| TypeScript, architecture, production build       | Pass                                                                                                                | `npx tsc --noEmit`, `npm run check:architecture`, `npm run build`                    |
| Design checks sau tinh chỉnh FE                  | 12 kiểm tra pass                                                                                                    | `artifacts/npp-final-design-check.log`                                               |
| Portal responsive                                | 15 màn × 1440/768/390px: 45 lượt pass; không tràn ngang, không API alert hoặc JS exception; reduced motion pass     | `.tools/sales-audit/npp-polish-results.json` và `screens/npp-final-*.png`            |
| Format                                           | Các file FE thay đổi pass; kiểm tra toàn repo còn 41 file sai format có từ nền hiện tại                             | Không sửa hàng loạt file ngoài phạm vi                                               |

Các màn responsive được kiểm tra: tổng quan, đặt hàng, đơn bán, giao bù, kho, công nợ, CRM, kế hoạch nhập, báo cáo, thiết lập, báo giá/chiến dịch, trade, giao hàng, danh mục và ảnh trưng bày.

Detector FE chạy một lần, báo `product-pack:before` có border-bottom 12px. Đây là tam giác CSS của minh họa incumbent, không phải viền nhấn khối NPP; không sửa phần không thuộc phạm vi. Asset danh mục lưu nguồn và hash trong `public/assets/npp-products/sources.json`, có provenance embedded/sidecar; đây là ảnh minh họa loại hàng, chưa xác minh bao bì SKU thực.

Reviewer kết luận `ship` ở phạm vi hữu hạn: hai lỗi nguồn đã sửa (counter đúng đích và mở lại phản hồi khi tiến độ thay đổi), catalogue và chi tiết giao bù tại 1440/390px. Đây không phải phê duyệt mọi màn hoặc nghiệm thu native iPhone. Documenter đối chiếu hệ thống incumbent và sáu ảnh cuối tại [06](06-giao-dien.md).

## Giới hạn nghiệm thu

- Các luồng đã kiểm tra không đồng nghĩa mọi nhóm trong ma trận 20 chức năng đã đầy đủ. Khoảng thiếu ở [04](04-backend-dong-bo.md#chưa-bao-phủ) vẫn được giữ.
- Màn Inbox/đề xuất giao bù mobile đã nối API và qua kiểm tra type/unit; chưa có UAT phần mới trên iPhone thật. IPA đã cài trước đó không tự nhận các thay đổi native mới.
- Notification cập nhật trong app theo polling/focus, chưa phải push của hệ điều hành.
- Parser lên đơn theo catalogue và provider chấm ảnh hiện tại chưa phải model AI được huấn luyện.
- Các tài khoản, giao dịch kiểm thử và schema MySQL tách riêng được giữ lại; không xóa dữ liệu người dùng.
- Không commit, push hoặc merge nhánh vào main trong lượt tinh chỉnh này.
