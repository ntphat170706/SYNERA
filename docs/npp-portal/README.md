# Rà soát và xây lại Portal NPP

Ngày 12/11/2026. Nhánh `feat/npp-portal-20261112`. Kịch bản trong tài liệu chỉ là ca kiểm thử: màn hình dùng dữ liệu thực từ API, không đưa nguyên tên/số liệu mẫu vào portal. Người dùng yêu cầu bổ sung backend còn thiếu; train AI và tích hợp chuyên sâu để sau.

## Giá trị cần chứng minh

SYNERA kết nối vận hành phân phối và chăm sóc khách hàng trên cùng hồ sơ. Một phát sinh về hàng phải có người xử lý, hạn phản hồi và kết quả để Sales tiếp tục phục vụ khách. NSX quản lý Sales; NPP chịu trách nhiệm khả năng đáp ứng và thực hiện đơn trong phạm vi được giao.

- [Đối chiếu nguồn và mâu thuẫn](01-doi-chieu.md).
- [Chức năng lõi, điểm nổi bật và mức hỗ trợ thực tế](02-chuc-nang.md).
- [Luồng hệ thống, cấu trúc FE và cửa nghiệm thu](03-luong-va-fe.md).
- [Hợp đồng và triển khai backend dùng chung](04-backend-dong-bo.md).
- [Kiểm tra luồng và giới hạn nghiệm thu](05-kiem-tra.md).
- [Giao diện và bằng chứng desktop/mobile](06-giao-dien.md).
- [Tác nghiệp NPP, Excel và trợ lý](07-tac-nghiep-npp.md).
- [Hồ sơ điểm bán, màn xử lý và logo](08-ho-so-va-thao-tac.md).
- [Tinh gọn công nợ, hỗ trợ và form tác nghiệp](09-tinh-gon-tac-nghiep.md).
- [Định vị CRM 5in1 kết hợp DMS theo nguồn mới](10-dinh-vi-crm5-dms.md).
- [Góp ý chọn lọc: kết quả giao nhận và chăm sóc trên đơn](11-gop-y-va-ket-qua-tren-don.md).
- [Cầu nối app và hoàn tất việc sau bán](12-cau-noi-va-dong-viec-sau-ban.md).
- [Thao tác đơn hàng và thông báo cập nhật Sales](14-don-hang-va-thong-bao.md).

## Phạm vi triển khai được chọn

1. Dùng backend chung hiện tại cho tổ chức/quyền, catalog/giá, sell-in, sell-out, kho, giao nhận, tài chính, ticket và báo cáo đã có.
2. Tổ chức lại portal theo công việc NPP; tách đơn nhập và đơn bán, nâng cấp trải nghiệm chọn hàng và giỏ, đưa hàng đợi và ngoại lệ lên tổng quan.
3. Bổ sung chứng từ giao bù, công việc báo khách, thông báo theo sự kiện và các phép kiểm nghiệp vụ ở backend; cả web NSX, portal NPP và app Sales cùng dùng contract này. Không gắn việc quản lý Sales cho NPP.
4. Tập trung chuỗi nhu cầu → đơn → giao thiếu → xử lý → báo lại khách, rồi gợi ý bổ sung hàng. Cơ chế hỗ trợ lên đơn dùng danh mục và rule, cần xác nhận; không tuyên bố đã có model AI. Các module CRM 5in1 còn lại nằm trong lộ trình, không mặc định dựng đủ HR/sự kiện/cộng đồng trong đợt portal này.

Phạm vi đã triển khai và kiểm tra ghi tại 04–06; các nhóm còn thiếu vẫn nằm trong backlog. Đây không phải chứng nhận mọi nghiệp vụ đã hoàn chỉnh hoặc đã nghiệm thu production.
