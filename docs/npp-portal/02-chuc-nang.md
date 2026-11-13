# Chức năng lõi và điểm nổi bật

Ma trận dưới đây là mốc khảo sát trước triển khai. Các phần backend mới đã bổ sung sau đó, cùng lựa chọn dùng dữ liệu API thay tình huống FE cố định, được cập nhật tại [04](04-backend-dong-bo.md).

`Có nền tảng` là đã thấy mã/API liên quan, chưa phải UAT hoàn chỉnh. `Một phần` cần thêm contract hoặc nối nhiều module. Kịch bản chỉ dùng làm ca nghiệm thu; màn hình đọc/ghi backend chung. Ưu tiên P0: luồng phải trình diễn được; P1: mở rộng sau luồng lõi; P2: phát triển sâu.

## Lõi toàn hệ thống

| ID  | Nhóm                       | Chủ thể chính và kết quả                                         | Hiện trạng / phần cần làm                                                                                      | Ưu tiên              |
| --- | -------------------------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | -------------------- |
| F01 | Tổ chức, người dùng, scope | NSX quản lý; NPP tác nghiệp; Sales nhận phân công                | IAM/workspace/quyền có nền tảng. Quan hệ NSX quản lý–NPP phục vụ và cấp/thời hạn cần hoàn thiện                | P0                   |
| F02 | SKU, ảnh, đơn vị/quy đổi   | NSX công bố; NPP/Sales chọn đúng quy cách                        | Catalog có nền tảng; FE cần ảnh đúng SKU, quy đổi rõ, không nhập đơn vị cơ sở nhầm                             | P0                   |
| F03 | Giá, ưu đãi, điều kiện     | NSX phát hành; server tính theo bậc giao dịch/đối tượng/hiệu lực | Pricing/policy có nền tảng; quà SKU/stacking/voucher nâng cao một phần                                         | P0/P1                |
| F04 | Sell-in                    | NPP đặt/đặt lại; NSX tiếp nhận/duyệt; NPP nhận                   | Có state machine/quote/credit/receipt. Rà auto receipt và lịch nhiều đợt                                       | P0                   |
| F05 | Sell-out                   | Sales tạo; NPP xác nhận lượng và thực hiện                       | Có confirm/reserve/ship/dispatch/receipt; thiếu lịch cam kết và vòng giao bù hoàn chỉnh                        | P0                   |
| F06 | Kho                        | Vai kho NPP/NSX quản lý theo kho/lô/HSD                          | FEFO, giữ hàng, stocktake, chuyển/quarantine có nền tảng; FE cần phân biệt vật lý/giữ/khả dụng                 | P0                   |
| F07 | Giao nhận                  | Kho/điều phối/tài xế/bên nhận                                    | Trip/stop/proof/actual có nền tảng; giao nhiều đợt/thiếu còn lại cần nối nghĩa vụ                              | P0                   |
| F08 | Đổi trả và đối chiếu       | Người có quyền duyệt, nhận và ghi chứng từ                       | Sell-in return có nền tảng; sell-out return và giao bù không tính tiền một phần                                | P0/P1                |
| F09 | Công nợ                    | Kế toán/đối chiếu theo bên phải trả                              | Credit/invoice/payment/ledger có nền tảng sell-in; phải thu điểm bán chưa đầy đủ                               | P0/P1                |
| F10 | Hồ sơ khách 360°           | Sales/NPP/NSX đọc trong scope                                    | Store/profile/interactions/appointment/orders/tickets có nền tảng; cần gom ngữ cảnh vào chi tiết đơn           | P0                   |
| F11 | Công việc, ticket, lời hẹn | Người xử lý hàng + người báo lại khách                           | Task/SLA/history/routing có nền tảng. Ticket resolved chưa tự tạo chứng từ xử lý; cần liên kết trách nhiệm kép | P0                   |
| F12 | Tuyến/MCP/lượt ghé         | NSX giao; Sales làm; NPP phối hợp                                | Route/check-in/out/handover/survey có nền tảng; checklist/attendance riêng còn thiếu                           | P0 liên thông/P1     |
| F13 | Báo cáo hai lớp            | Điều hành và kế toán theo quyền                                  | Reports/snapshot/closing/export có nền tảng; truy xuống/công thức phải thống nhất                              | P0                   |
| F14 | Kế hoạch nhập              | NPP trao đổi nhu cầu với NSX                                     | Demand plan và replenishment có nền tảng; nguồn sell-out ngoài và dự báo sâu để sau                            | P1                   |
| F15 | Trade/hỗ trợ               | NPP đề nghị POSM/đào tạo/quyền lợi; NSX duyệt                    | Proposal/budget/settlement/ticket có nền tảng; cần UX đơn giản và liên kết chứng từ                            | P1                   |
| F16 | Offline và retry           | Sales làm việc hiện trường                                       | Nháp owned, quote lại, idempotency/ACK có nền tảng; media outbox và handover draft chưa đủ                     | P0 Sales/P1 nâng cao |
| F17 | Chăm sóc/báo giá/campaign  | Sales/CS theo nhu cầu và nhóm khách                              | Lead/quote/campaign/task có nền tảng; công thức risk theo chu kỳ và doanh số còn thiếu                         | P1                   |
| F18 | Nhân sự/chấm công          | NSX quản lý ngày làm, ca, nghỉ, thay người                       | Có danh mục staff và handover; chưa có HR/attendance đầy đủ                                                    | P2                   |
| F19 | Sự kiện và kết nối         | Đào tạo/hội nghị/quan hệ đối tác                                 | Phạm vi đã định hướng, chưa có module đầy đủ                                                                   | P2                   |
| F20 | Nguồn đơn/tùy biến         | Thêm kênh, trường, báo cáo được chọn                             | Chưa mặc định tích hợp mọi sàn/ERP/transport. Cần connector và scope dữ liệu riêng                             | P2                   |

## Những điểm nên làm nổi bật trong demo SYNERA

| Điểm khác biệt đề xuất                      | Người dùng thấy gì                                                     | Hành động tạo giá trị                                    | Cách trình diễn / phát triển sâu                                                                             |
| ------------------------------------------- | ---------------------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Đơn hàng đi cùng cam kết chăm sóc           | Cùng đơn hiện lượng còn thiếu, người xử lý hàng và hạn Sales báo khách | NPP chốt phương án; Sales ghi đã báo; hồ sơ giữ kết quả  | Đã nối API giao bù với tồn kho, hạn xử lý và task phản hồi; số lượng lấy từ chứng từ thực                    |
| Một hồ sơ xuyên ba bên                      | NSX/NPP/Sales thấy phần dữ liệu được cấp, cùng lịch sử sự kiện         | Người đi thay tiếp tục đúng việc đang mở                 | Demo cùng dòng sự kiện; sau đó assignment hiệu lực và quyền đọc lịch sử                                      |
| Nhập hàng theo nhu cầu thực                 | Căn cứ tồn/giữ/bán ra/hàng về thay vì một con số vô cớ                 | Lập kế hoạch theo gợi ý rồi kiểm giá/ưu đãi khi đặt hàng | Đọc replenishment API; không thay bằng chuỗi số liệu tình huống cố định                                      |
| Hỗ trợ lên đơn từ yêu cầu tự nhiên          | Yêu cầu thành SKU, đơn vị và lượng có thể sửa                          | Người dùng xác nhận trước gửi                            | Web dùng API parser catalogue; mobile còn parser catalogue cục bộ. Không tự gửi hoặc tuyên bố model đã train |
| Chấm trưng bày có bằng chứng và người duyệt | Ảnh, tiêu chí, điểm gợi ý, mục cần sửa, kết quả duyệt riêng            | Sales bổ sung ảnh; người duyệt xác nhận                  | Provider hiện mock; demo được phép minh họa, không tự thưởng/chi tiền                                        |
| Ưu tiên đúng việc trước hỏi mua             | Ticket giao thiếu/lời hẹn đặt trước nhắc mua lại                       | Xử lý cam kết chưa xong rồi mới chăm sóc bán tiếp        | Rules minh bạch, dùng hạn và trạng thái; chưa cần ML                                                         |
| Hai lớp số liệu có thể truy nguồn           | Phân biệt đơn đang xử lý, thực nhận và kỳ đã chốt                      | Bấm số → hàng đợi/chứng từ nguồn                         | Dùng reports/snapshot có thật; không lấy tiền đơn làm doanh thu nhận                                         |

Không có căn cứ để tuyên bố SYNERA là sản phẩm duy nhất có các điểm này. Khác biệt đề xuất là cách ghép chúng thành một luồng phục vụ khách có thể đo được.

## Ranh giới demo

- Giao dịch trên API hiện có giữ nguyên scope, validation, snapshot, idempotency và nghiệp vụ tài chính.
- Giao bù, lời hẹn và phản hồi dùng API/backend chung; không có nút chạy chuỗi dữ liệu mẫu cố định. Những gì chưa triển khai phải ghi đúng khoảng thiếu.
- Không bắt buộc chữ Demo trên từng control theo lựa chọn người dùng; tài liệu và giao diện phải phản ánh nguồn dữ liệu, trạng thái thực tế.
- Dữ liệu tham khảo không được đưa vào báo cáo thực hoặc tự phát sinh thưởng.
- Chưa làm train AI, thanh toán thật, tích hợp ERP/hãng vận chuyển hoặc triển khai production trong đợt này.
