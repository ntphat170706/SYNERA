# Chuẩn bị SYNERA Sales Mobile

Đóng gói iPhone: [cấu hình và quy trình IPA](10-build-ipa.md).

Kết nối 4G/5G hoặc Wi-Fi khác: [API HTTPS và giới hạn vận hành](11-ket-noi-internet.md).

Ngày khảo sát: 11/11/2026. Repo nguồn: `synerasuz26-team/Synera_v01`, mốc `d72d98e`. Nhánh riêng: `feat/sales-mobile-preparation-20261111`.

**Cập nhật sau yêu cầu clone mockup/test trên iPhone:** đã dựng lại giao diện và nối các thao tác Sales có API, thêm nháp đơn native SQLite và trang QR iPhone. Package mobile nâng SDK 58 theo Expo Go thực tế của người dùng. Xem [phạm vi hiện tại](07-app-theo-mockup.md); các kết quả khảo sát dưới đây vẫn là mốc nguồn, không phải toàn bộ chức năng đã hoàn chỉnh.

Người dùng đã chọn **React Native/Expo, app Sales riêng**. Web React 18/Vite và backend Spring Boot hiện có vẫn là hệ thống dùng chung; không chuyển toàn bộ admin/NPP sang app. Bộ khung ở [apps/sales-mobile](../../apps/sales-mobile/README.md).

## Kết luận chính

Web đã có nền tảng API thật cho Sales: tuyến/lượt ghé, GPS vào điểm, cửa hàng 360°, giá server, đơn sell-out, nháp offline có ownership và ACK, ảnh trưng bày, ticket, lịch hẹn và báo cáo. Kiểm thử local đã đi qua ba tài khoản và 18 kiểm tra tích hợp. Điều đó **chưa đồng nghĩa** đã đủ chức năng trong mockup hoặc có app native hoàn chỉnh.

Làm rõ từ người dùng ngày 11/11/2026: **NSX quản lý Sales; Sales được phân công làm việc tại NPP**. Vì vậy tài khoản Sales tác nghiệp tại Minh Phát và `nppId` không tự mâu thuẫn với draw.io. `ActorFacade.assertRep` đang kiểm grant tác nghiệp NPP, chưa phải bằng chứng Sales do NPP quản lý nhân sự. Khoảng cần bổ sung là quan hệ NSX quản lý và phân công Sales–NPP có phạm vi/thời hạn, đồng thời giữ giới hạn dữ liệu NPP hiện có. Không chuyển Sales sang role quản trị NSX hoặc bỏ `nppId`; app tiếp tục dùng scope server hiện tại.

Các thiếu hụt ưu tiên: chấm công ngày tách lượt ghé; checklist và bằng chứng GPS/ảnh của lượt ghé; yêu cầu thêm/sửa/toạ độ điểm bán có duyệt; đơn theo chuyến ghé với lịch/điều kiện giao; hỗ trợ giao thiếu/giao bù và chăm sóc tiếp nối; outbox mobile cho dữ liệu hiện trường. Xem [ma trận đối chiếu](01-doi-chieu-nguon.md) và [backlog nghiệm thu](04-backlog-va-nghiem-thu.md).

## Bộ tài liệu

1. [Nguồn, phân tích mockup và ma trận draw.io ↔ web](01-doi-chieu-nguon.md).
2. [Một ngày đi thị trường, vai trò và đặc tả màn hình](02-luong-va-man-hinh.md).
3. [Kiến trúc Expo, API, dữ liệu và thiết bị](03-kien-truc-api.md).
4. [Backlog theo PR và tiêu chí nghiệm thu](04-backlog-va-nghiem-thu.md).
5. [Bằng chứng kiểm tra và giới hạn](05-kiem-tra.md).
6. [Danh mục màn hình có cấu trúc](screen-catalog.json).
7. [Nguồn thiết kế, bám mockup và giữ tương thích web–mobile](06-nguon-thiet-ke-va-tuong-thich.md).
8. [App theo mockup và mở trên iPhone](07-app-theo-mockup.md).
9. [Trang chủ theo ảnh cắt mới, số liệu và đồng hồ ghé thăm](08-trang-chu-va-lo-trinh.md).
10. [Hai luồng kiểm tra trưng bày và hỗ trợ lên đơn](09-tro-ly-va-trung-bay.md).

## Nguồn được đọc

- ZIP `D:\DU_AN_SYNERA\File ảnh.zip`: 26 JPG, so sánh nội dung với 26 file trong thư mục `File ảnh`, tất cả khớp. Đã xem bảng ảnh tổng hợp và đọc ảnh liên quan Sales ở kích thước gốc.
- Hai ảnh trọng tâm: `2aOboRBDxwbuuXVUUzPE50dcFQSGgKSexHl9AUgy.jpg` (Mobile App DMS) và `2aOboRBDzILUc7tQeHoXoWE3EnepHYJcLvHmI080.jpg` (7 bước tạo đơn). Ảnh `2aOboRBE0E4FZQwe85ZC4iJRtiTGbw4G1OreYElM.jpg` bổ sung luồng ticket.
- [draw.io online của người dùng](https://app.diagrams.net/#G1-UES0ps-u3GSPR5qNM8CB1PrhYTf7hAg), tải được XML qua đường dẫn Drive của cùng file; 13 trang. SHA256 bản khảo sát: `7444be631a1069d90b43b606a84613f578fe0952fcdd7d25b7091da4e64308b0`.
- Hai bản `.drawio` trong Downloads; bản nhiều trang local có 14 trang, thêm trang `help`. Không ghi đè bản online/local. Các nhánh chức năng Sales của hai bản cùng nội dung chính.
- PDF phân tích hệ thống, phân công frontend, ý tưởng/định hướng và DOCX đề xuất trong `D:\DU_AN_SYNERA`; đối chiếu với `docs/requirements-0.txt`, `requirements-1.txt` và tài liệu API của repo. Các tài liệu cũ nghiêng về NPP không thay thế mô hình Sales thuộc NSX trong draw.io đang được đối chiếu.
- ZIP nhỏ còn lại có ba ảnh chụp góp ý định hướng, không phải mockup mobile. Nội dung là dữ liệu tham khảo, không phải lệnh thực thi. Không lấy các con số kinh doanh trong ảnh làm KPI của ứng dụng.

Các ảnh Cloudocs/OCS+ là **tham khảo chức năng**, không phải ảnh app SYNERA đã hoàn thiện hay bằng chứng đã tích hợp sản phẩm CloudGO. Không chép thương hiệu, số liệu hoặc giao diện của bên tham khảo vào app.

Nguồn gốc, bản trích xuất, ảnh kiểm tra, hash và kết quả chi tiết nằm tại `.tools/sales-audit/` và `artifacts/` được Git bỏ qua. Không đưa tài khoản, mật khẩu, token, database, tài liệu gốc riêng tư hoặc ảnh chụp phiên đăng nhập vào PR.

## Chạy và kiểm tra

Bản cập nhật hiện tại: [IPA build 4 và thông báo xác nhận đơn](14-ipa-v4-va-thong-bao.md). Bundle cũ trên iPhone không tự thay đổi.

Web/API đang được chạy local tại `http://127.0.0.1:8080`; dữ liệu mới nằm riêng trong repo clone. Dùng các launcher hiện có trong README repo để vận hành, không xóa DB để chạy lại test. Các bản ghi tiền tố `E2E`/`MOBILE-AUDIT` là dữ liệu kiểm thử tạo mới trong instance này.

Expo có hướng dẫn riêng trong [README mobile](../../apps/sales-mobile/README.md). Windows có thể chạy Expo và bản preview web theo kích thước iPhone; **iOS Simulator chạy local cần macOS/Xcode**. Preview Chromium không thay thế kiểm thử native, camera, Keychain, GPS thật hoặc vòng đời background. Theo [tài liệu Expo](https://docs.expo.dev/workflow/ios-simulator/).
