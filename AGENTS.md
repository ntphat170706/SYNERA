# Hướng dẫn tác vụ SYNERA

Áp dụng cho repository này và các thư mục con.

1. Trước khi sửa code, đọc [ARCHITECTURE.md](ARCHITECTURE.md) và [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md). Đọc module và phần yêu cầu liên quan trong `docs/requirements-*.txt` khi sửa nghiệp vụ.
2. Theo flow: **đọc yêu cầu → xác định role/route/data → tìm phần dùng lại → sửa đúng module → kiểm tra → cập nhật tài liệu → bàn giao**.
3. Giữ FE React 18 + Vite + TypeScript; BE Java 25 (LTS) + Spring Boot tại `backend/`. Làm việc trong repository hiện tại; không khởi tạo lại app, router, store hoặc sửa nhầm `../synera-web`.
4. Menu và route permissions dùng `src/app/navigation.ts`; route/shell tại `src/app/App.tsx`. Public landing/auth không bọc shell quản trị.
5. Màu/kích thước/font lấy từ `src/styles/tokens.css`. Sửa component quản trị trong rule canonical của `admin.css`; landing scope riêng trong `landing.css`. Không thêm file override hoặc palette mới trong từng feature.
6. Dùng `shared/Brand.tsx` và logo gốc. Không vẽ lại logo, đổi màu hoặc thay bằng biểu tượng khác.
7. Dùng shared UI, `money`, `total`, `canTransition`, `creditAvailable`, selector và mutation có sẵn. Giữ riêng tạm tính/đã chốt, giá trị đơn/doanh thu; không tạo KPI tăng trưởng giả.
8. Không thay đổi nghiệp vụ khi chỉ đổi UI. Thay đổi domain phải cập nhật type, transition, validation, dữ liệu demo và kiểm thử tương ứng. Không xóa dữ liệu LocalStorage/IndexedDB của người dùng để che lỗi.
9. Frontend guard không thay thế phân quyền backend. Phân biệt rõ demo với API, OTP, AI và thông báo thật.
10. Chạy `npm run check:architecture`, `npm run format:check`, `npm test`, `npm run build`. Nếu đổi layout/theme/shared UI, kiểm tra thêm `npm run test:design`; nếu đổi luồng nghiệp vụ, chạy `npm run test:e2e` với production preview. Với thay đổi nhỏ chỉ tài liệu, kiểm tra link/nội dung là đủ.
11. Ghi quyết định chung mới vào `docs/DECISIONS.md` và cập nhật kiến trúc/design system khi cần. Không nới check chỉ để bỏ qua lỗi.
12. Kết thúc bằng thay đổi thực tế, kết quả kiểm tra và giới hạn còn lại. Không bảo đảm mọi tác nhân tương lai sẽ đọc hướng dẫn; khi cần, chỉ người dùng tới mẫu prompt trong `ARCHITECTURE.md`.
13. Khi sửa backend, đọc `backend/ARCHITECTURE.md`, `docs/BACKEND-API.md`, `docs/BACKEND-BUILD-SUPPLEMENT.md` và phần liên quan trong `docs/backend-requirements.txt`. Module khác chỉ gọi Facade. Giữ transaction, scope, idempotency, BigDecimal, snapshot và append-only ledger. Thêm migration mới thay vì sửa schema đã triển khai.
14. Kiểm tra backend bằng Java 25: `.\scripts\backend.cmd verify`. Chạy MySQL integration khi Docker có sẵn; báo rõ skip, không đổi H2 thành bằng chứng MySQL. Format Java bằng `python scripts/format-backend.py`; không chạy lại script sinh model để ghi đè service/entity đã sửa.
15. FE hiện đọc/ghi API. Giữ nguồn dữ liệu server; không trộn LocalStorage demo cũ vào DB. OTP local, AI mock và duyệt thủ công phải ghi nhãn; khi mở rộng chuyển trọn luồng đọc/ghi và kiểm thử API end-to-end.
16. Khi làm Sales web/mobile, đọc [quy tắc nguồn và tương thích](docs/sales-mobile/06-nguon-thiet-ke-va-tuong-thich.md): chức năng truy vết theo PDF phân tích thiết kế hệ thống, bố cục/thao tác bám mockup `File ảnh.zip`, role đối chiếu draw.io và làm rõ của người dùng. NSX quản lý Sales; Sales làm việc tại NPP. Giữ API/backend chung, ghi rõ khoảng thiếu trước khi thêm nghiệp vụ; không tự đổi contract hoặc tạo logic riêng trên mobile. Prototype hiện tại chưa là bản thiết kế UI cuối cùng.
