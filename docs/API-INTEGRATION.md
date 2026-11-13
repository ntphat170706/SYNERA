# Tích hợp FE–API hiện tại

FE đã dùng shared/api.ts để đọc/ghi backend. Provider trong shared/store.tsx chỉ là projection cho shell/feature; mocks không tham gia runtime. Hợp đồng chi tiết ở BACKEND-API.md, OpenAPI local /v3/api-docs.

Session: access token RAM, refresh cookie HttpOnly. Refresh đồng thời dùng một promise; refresh không gửi bearer hết hạn. Session endpoints kiểm Origin/header, logout thu hồi cookie. Route guard/menu dựa authorities/workspace server; backend vẫn kiểm quyền và scope mỗi request.

useApi abort request cũ khi đổi path/user/workspace, làm mới sau mutation và khi tab hoạt động. Các command tài liệu dùng idempotency; mất phản hồi giữ key cho lần thử lại. Không tự tính lại giá/ledger/snapshot ở FE.

Offline: Dexie giữ draft user/workspace; cache Sales tham chiếu 24 giờ không chứa auth token. Service Worker chỉ cache shell/tài nguyên. Sync API mỗi item transaction riêng; chỉ xóa CREATED/DUPLICATE ACK, giữ REJECTED/PRICE_CHANGED để sửa. Không xóa nháp khi logout/đổi user; không gửi nháp khác user/workspace. Dữ liệu demo LocalStorage cũ giữ nguyên và không nhập vào DB server.

Mỗi luồng mới phải chuyển trọn đọc và ghi: DTO/validation → server Facade/scope/transaction → UI/loading/error → API test MySQL → browser kiểm thử trên runtime local. Không làm nút thành công giả hoặc dùng số mẫu thay lỗi API. OTP local, AI mock và manual review phải ghi nhãn đúng; provider chưa cấu hình không coi là tích hợp đã nghiệm thu.

Chạy app bằng `scripts/run-local.py`; phát triển Vite 5173 hoặc preview 4173 proxy API về 8080. Website và API dùng cùng origin local trong JAR; repository không có cấu hình deploy.