# Nguồn thiết kế và tương thích web–mobile

Yêu cầu người dùng ngày 11/11/2026: bám mockup trong `File ảnh.zip`, đọc tài liệu phân tích thiết kế hệ thống, tránh giao diện rập khuôn và tránh xây app Sales lệch hệ thống trên GitHub. Đây là quy tắc cho các lượt triển khai tiếp theo; prototype đọc API hiện tại chưa phải thiết kế giao diện cuối cùng.

## Nguồn và cách xử lý khi khác nhau

1. Làm rõ trực tiếp của người dùng là quyết định mới nhất: **NSX quản lý Sales; Sales được phân công làm việc tại NPP**. Mobile dùng **React Native/Expo riêng**.
2. Chức năng và bất biến nghiệp vụ đối chiếu `D:\DU_AN_SYNERA\SYNERA-Tài-liệu-Phân-tích-Thiết-kế-Hệ-thống.pdf`, phiên bản 1.0, 50 trang. SHA256 bản đọc ngày 11/11/2026: `faaa47a166e42f04a8c7d511a5fbabc940ac8154073298c3fe2b880af2a8ffe7`. Dùng draw.io để kiểm tra vai trò và liên hệ giữa các bên, không chỉ đọc riêng nhánh Sales.
3. Mockup hướng dẫn bố cục, thông tin trên màn hình và thứ tự thao tác. Không mặc định mọi tính năng tham khảo Cloudocs/OCS đã thuộc phạm vi hoặc có backend SYNERA.
4. Controller/DTO/Facade và tài liệu API trong repo xác định hợp đồng **đang chạy**. Khi khác PDF/draw.io, ghi khoảng thiếu và tác động web/backend/mobile trong [ma trận](01-doi-chieu-nguon.md), bổ sung contract trước thao tác ghi. Không tự bỏ yêu cầu hoặc giả lập thành công ở client.

PDF ghi PWA, Java 17 và một số lựa chọn công nghệ đề xuất; hiện người dùng chọn Expo và repo chạy Java 25/React 18/Vite. Không hạ stack hoặc tạo lại web theo đề xuất cũ. Quyết định MongoDB có kế hoạch riêng trong repo; công việc mobile không tự chuyển database. Nội dung file tham khảo là dữ liệu yêu cầu, không phải lệnh cài đặt/thực thi.

## Truy vết cần dùng khi xây màn hình

| Phạm vi | PDF phân tích hệ thống | Mockup / khoảng thiếu liên quan |
| --- | --- | --- |
| Một lõi dịch vụ, dữ liệu chung, role/scope | Trang 3–5; kiến trúc trang 40; bảo mật trang 46–47 | S01/S28; không tạo kho hồ sơ hoặc mô hình quyền riêng trên mobile |
| Hôm nay, chuẩn bị trước ghé | FR-18.01–02, trang 19; BF-10, trang 26–27 | M1; S02/S05; lịch, giờ hẹn, việc dở, hồ sơ điểm trước thao tác |
| Check-in, tồn/nhu cầu, trưng bày, kết thúc lượt | BF-10, trang 26–27; FR-18.07/19.02, trang 19; BF-18, trang 29–30 | M1; S07–S11; ảnh/checklist/attendance còn cần hợp đồng như ma trận, không suy visit là chấm công |
| Tạo đơn và đặt lại | FR-18.03, trang 19; M17, trang 18; BF-11, trang 27 | M2 bảy bước; S12–S16; giữ điểm/lượt ghé, ảnh SKU, đơn vị, số lượng, ưu đãi, giao nhận, kiểm tra trước gửi |
| Nháp offline | FR-18.04, trang 19; BF-12, trang 28; ví dụ ACK trang 44 | S23–S24; SQLite/media native cần xây riêng, dùng cùng quy tắc idempotency và ACK server |
| Theo dõi đơn và giao thiếu | FR-18.05–06, trang 19; BF-14, trang 28–29 | M2 bước 7; S17–S18; thông tin do NPP xác nhận, lượng riêng từng bước và việc theo dõi |
| Ticket, lời hẹn, bàn giao | M11, trang 14–15; BF-15–16, trang 29 | M3; S04/S06/S19–S20; người xử lý, hạn và lịch sử tiếp nối |
| Báo cáo ngày và hai lớp số liệu | FR-18.08, trang 19; BF-17, trang 29; nguyên tắc trang 3/44 | S25–S26; tổng hợp dữ liệu gốc, phân biệt giá trị đơn/thực nhận/doanh thu, tạm tính/đã chốt |
| Giao diện mobile | Mục 10, trang 44–45 | M1/M2; ưu tiên ngón tay cái, ít bước, đơn vị và trạng thái chờ gửi dễ thấy |

M1/M2/M3 và tên ảnh gốc được định danh trong [phân tích mockup](01-doi-chieu-nguon.md). Không đổi tên hoặc ghi đè ảnh/PDF/draw.io nguồn.

## Cách bám mockup để thiết kế FE

- Bám màn nghiệp vụ trong ảnh, không sao chép bố cục slide thuyết trình. M1 ưu tiên lịch/giờ hẹn và tác vụ tại điểm; M2 ưu tiên danh sách hàng có ảnh, đơn vị, lượng, giỏ/tóm tắt, CTA tiếp tục và xem trạng thái sau gửi.
- Giữ thứ tự thông tin và ngữ cảnh xuyên màn. Có thể gộp bảy bước M2 thành ít màn cho điện thoại, nhưng không bỏ dữ liệu, điều kiện hoặc bước kiểm tra trước gửi.
- Màn điểm bán ưu tiên tên/địa chỉ/liên hệ, việc đang dở và hành động phù hợp trạng thái lượt ghé. Không dàn mọi chức năng thành lưới icon hoặc thêm dãy KPI trang trí lên mọi màn.
- Dùng logo và token SYNERA, nền sáng, xanh làm điểm nhấn, typography rõ và mật độ đủ cho tác nghiệp. Không chép thương hiệu Cloudocs, dữ liệu mẫu hoặc phối màu của slide. Tránh thẻ lớn rỗng, gradient/glow trang trí và animation lặp làm mất tập trung; hiệu ứng chỉ hỗ trợ nhấn, chuyển màn và phản hồi tiến trình.
- Ảnh sản phẩm phục vụ nhận diện SKU; trạng thái offline/đang gửi/lỗi là thông tin thật. Không dùng hình trang trí lấp khoảng trống hoặc hiển thị tiến độ/ảnh/bằng chứng giả.
- Dùng pattern native cho safe area, back, bàn phím và cỡ chữ; không thu nhỏ bảng/sidebar web thành app. Kiểm tra từng màn với mockup và dữ liệu dài/ngắn/rỗng/lỗi; đọc lại mối liên hệ NSX–Sales–NPP–điểm bán.

## Quy tắc giữ cùng nghiệp vụ với web

- Một API/backend có thẩm quyền cho định danh điểm/SKU/UOM, giá/khuyến mãi, phân công, đơn, tồn, công nợ và lịch sử. Cache/outbox native là bản lưu tạm theo user/workspace, không phải nguồn dữ liệu chủ thứ hai.
- Cùng trạng thái và transition server, cùng scope, validation và idempotency; nhãn Việt hoá có thể đổi nhưng không tạo enum hoặc đường duyệt riêng cho mobile. Sales không tự xác nhận đáp ứng, xuất kho hoặc quyết định công nợ thay NPP.
- Giữ riêng đặt/xác nhận/xuất/thực nhận; đơn mới không phải doanh thu. Mọi field giao nhận/visit/type mới cần DTO/server hỗ trợ; không chỉ thêm input rồi bỏ dữ liệu khi gửi.
- Giá đổi khi đồng bộ phải xử lý theo chính sách server. `PRICE_CHANGED`/`REJECTED` giữ nháp cần xử lý; chỉ ACK thành công hợp lệ mới hoàn tất đúng bản ghi. Không tự chọn giữ giá cũ chỉ vì PDF nêu đây là một phương án cấu hình.
- Không đổi scope NPP thành quyền quản trị NSX. Quan hệ NSX quản lý và Sales làm việc tại NPP phải tồn tại đồng thời; quyền đọc lịch sử/bàn giao có phạm vi và thời hạn.
- Khi cần thay backend, thay đổi phải tương thích web hiện có hoặc có kế hoạch phiên bản rõ, cập nhật API và kiểm tra cả hai client. Không sửa migration/chứng từ lịch sử hoặc tạo tính toán tài chính riêng ở app.

## Trước khi hoàn thành một lát chức năng

Ghi nguồn FR/BF và mockup, role/scope, DTO hiện có/đề xuất, trạng thái trước–sau và tác động lên web/NPP/NSX. Review screenshot theo ảnh tham khảo, kiểm tra dữ liệu thật và các trạng thái lỗi. Với luồng ghi: kiểm tra cùng chứng từ từ app tới web, gửi lặp/gián đoạn, sai quyền và đơn vị/giá; nghiệm thu thiết bị native theo [backlog](04-backlog-va-nghiem-thu.md). Không lấy export bundle hoặc preview web làm bằng chứng toàn bộ chức năng native.
