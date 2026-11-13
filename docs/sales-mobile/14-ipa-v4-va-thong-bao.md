# Sales iPhone build 4 — 13/11/2026

IPA unsigned mới: `output/ios/SYNERA-Sales-v4-Internet-unsigned.ipa`. Dùng Sideloadly ký/cài như bản trước; không ghi đè IPA v3. Bundle ID `vn.synera.sales`, build `4`, iPhoneOS arm64, SDK Expo 58 với patch package tương thích.

SHA256: `72866db4de19d5c376af33cc6a3b60f60f0ce60fa610885226f63de14526d908`.

EAS build `b913b21b-c0d2-4db1-939c-1531a02299ad` hoàn tất. Kiểm tra CRC, Info.plist, Mach-O arm64, thiếu provisioning ký sẵn và API HTTPS nhúng trong main.jsbundle đã đạt. Kết quả máy đọc ở file `.verification.json` cạnh IPA. Các kiểm tra này không thay thế việc cài/chạy trên iPhone thật.

App đang mở tự cập nhật đơn/thông báo mỗi 2 giây, không làm trống màn hình khi tải nền. NPP xác nhận → Sales thấy trạng thái và banner “Đơn hàng đã được NPP xác nhận”, chạm mở Inbox. Không phát lại toàn bộ thông báo cũ thành banner sau login. Ghi chú giao bù và kết thúc lượt ghé không bắt buộc; nội dung phản ánh/lịch hẹn vẫn có dữ liệu nghiệp vụ chính. Chi tiết hợp đồng tại [Portal 14](../npp-portal/14-don-hang-va-thong-bao.md).

Endpoint build 4: `https://facility-weapon-tutorials-kenneth.trycloudflare.com`, đi qua gateway 8787 → backend 8080 → MySQL persistent 3308. Gateway giữ giới hạn API Sales; không mở portal quản trị qua tunnel. Bản v3 nhúng hostname tunnel cũ đã ngừng, không thể tự thay URL bằng việc sửa backend.

Đây vẫn là tunnel tạm từ laptop: laptop/backend/gateway/tunnel phải chạy. Chưa có tên miền backend ổn định, APNs hoặc OTA update. Thay mã native/giao diện cần cài IPA mới.
