# Tinh gọn tác nghiệp NPP — 13/11/2026

## Phạm vi và mục đích

Góp ý của người dùng chỉ ra quá nhiều luồng kế toán cùng xuất hiện, tên Trade marketing khó hiểu, hộp tài khoản lộ mã kỹ thuật, form ngắn dùng sheet quá cao và form thêm cửa hàng thiếu phân cấp. Đợt này tổ chức lại các màn đó trên API hiện có, giữ các nhóm nghiệp vụ CRM 5in1 và DMS trong phạm vi sản phẩm. Việc một bảng không còn hiện trên tài khoản NPP thông thường không xóa module hoặc quyền nghiệp vụ của vai khác.

Định hướng mới nhất tiếp tục là NSX/nhãn hàng quản lý mạng lưới, NPP tác nghiệp trong phạm vi hãng cấp, Sales thuộc NSX. Nội dung góp ý về màn hình là căn cứ hiểu công việc; bố cục thực hiện có thể thay đổi để giảm bước và làm rõ trách nhiệm. Phân tích nguồn và mức hoàn thiện toàn bộ CRM/DMS cần được đọc cùng các tài liệu đối chiếu, không suy từ số màn đã dựng.

## Thay đổi thực tế

| Vùng                     | Hành vi sau chỉnh                                                                                                                             | Nguồn và giới hạn                                                                                                                                                                         |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Công nợ NPP thông thường | Công nợ với NSX; số dư phải trả, quá hạn và hạn mức. Chọn một trong Hóa đơn phải trả, Lịch sử phát sinh, Phiếu đổi trả; mỗi lần hiện một bảng | `/credit`, `/credit/{nppId}/invoices`, `/credit/{nppId}/ledger`, `/returns`. Ledger/returns tải theo lựa chọn. Đây là nghĩa vụ với NSX, không là chứng nhận đã có đủ sổ phải thu điểm bán |
| Nghiệp vụ kế toán        | Tài khoản có `CREDIT_WRITE` hoặc `CLOSING_WRITE` vẫn tới màn kế toán hiện có                                                                  | Giữ luồng kỳ chốt, điều chỉnh và ngoại lệ theo quyền. Không sửa ledger, snapshot hoặc backend                                                                                             |
| Không gian làm việc      | Hộp gọn gồm người dùng, vai tiếng Việt, đơn vị và dấu hiện tại; đổi mật khẩu/đăng xuất thành hàng thao tác                                    | Danh sách từ `/auth/session/workspaces`; chuyển workspace qua phiên thật, có trạng thái chờ/lỗi. Không dùng mã `NPP_OWNER` hoặc số ID làm nhãn chính                                      |
| Thêm cửa hàng            | Hai bước thông tin → phụ trách/vị trí, giữ giá trị khi quay lại, chỉ bước cuối gửi                                                            | Gọi `/stores` như trước, giữ các trường backend. Sales/giám sát từ `/staff` trong phạm vi, không lấy chủ NPP làm Sales mặc định. Không đổi quyền quản lý nhân sự                          |
| Kết quả hẹn và form ngắn | Dialog cao theo nội dung, giữ ngữ cảnh điểm bán, nút Hủy/Lưu rõ                                                                               | `ActionForm` tối đa ba trường, không có `intro`; form dài hoặc bằng chứng vẫn dùng sheet                                                                                                  |
| Hỗ trợ bán hàng          | NPP thông thường thấy Đề xuất/Phản hồi; tên tiếng Việt, tiền được định dạng, sản phẩm hiển thị tên, trạng thái rỗng có hành động              | `/trade` và contract hiện có; `TRADE_APPROVE`/`CREDIT_WRITE` giữ ngân sách/quyết toán. Lối vào từ Chính sách bán hàng, không thêm menu chính                                              |
| Logo                     | PNG gốc trong suốt nằm trực tiếp trên dải xanh sidebar, bỏ mảng trắng quanh ảnh                                                               | Chỉ bỏ CSS nền/bo góc, không sửa file ảnh, vẽ lại hoặc đổi màu artwork                                                                                                                    |

## Hình thức đã đối chiếu

- Giữ canvas NPP trung tính `--canvas`, các surface trắng, primary cho lựa chọn; stock/care/warning/danger giữ nghĩa semantic hiện có. Không thêm palette theo feature.
- Dialog tài khoản 490px, dialog ngắn 640px, thêm cửa hàng 720px; width bị giới hạn theo viewport. Dialog mới radius 16px và dùng bóng mềm có offset từ `--shadow-sheet`.
- Store form dùng bước thực với số thứ tự và dấu hoàn thành; input 46px, desktop hai cột và mobile một cột. Phần không chọn dùng `hidden`, không chỉ làm mờ.
- Chuyển bước 220ms và mở dialog ngắn 180ms; lựa chọn đổi nền/màu 180ms. `prefers-reduced-motion` vô hiệu animation/transition trong portal. Focus, Escape, scroll lock và trả focus tiếp tục qua Modal dùng chung.
- Màn thao tác giữ nhãn và lỗi cần thiết; không đưa phần giải thích định vị hoặc hướng dẫn chung thành các đoạn văn trong portal.

Source đã đọc: `src/styles/tokens.css`, phần canonical NPP trong `src/styles/admin.css`, `src/shared/ui.tsx`, `src/shared/ApiUI.tsx`, `src/app/WorkspacePanel.tsx`, `src/app/navigation.ts`, `src/features/crm/{CRM,StoreCreate}.tsx`, `src/features/finance/Finance.tsx`, `src/features/trade/Trade.tsx`.

## Tham khảo và tài nguyên

[Uiverse Forms](https://uiverse.io/forms), [form của alexruix](https://uiverse.io/alexruix/fat-cobra-72), [21st](https://21st.dev/) và [Form Layout](https://docs.21st.dev/%40ephraimduncan/components/form-layout/early-access-application-with-package-selection) được dùng làm tham khảo cách nhóm trường và phản hồi tương tác. Implementation dùng component/CSS hiện có của repository; không sao mã component hoặc thêm dependency trong đợt này. Không suy giấy phép của toàn bộ 21st từ một trang tham khảo.

## Bằng chứng và kiểm tra

| Kiểm tra                                          | Kết quả đã có tại thời điểm ghi tài liệu                                                                                                                                   |
| ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TypeScript và architecture                        | Pass theo lượt kiểm tra của agent chính; không đổi ranh giới import hay cơ chế backend                                                                                     |
| Shared tests                                      | 11/11 pass                                                                                                                                                                 |
| Finance                                           | Audit xác nhận một panel bảng và ba lựa chọn; ledger/returns không bị tải trước khi chọn                                                                                   |
| Workspace và form                                 | Vai được dịch, workspace chọn từ phiên, focus nằm trong dialog khi Shift+Tab; giá trị store giữ khi đổi bước                                                               |
| Tạo điểm bán qua UI/API                           | Agent chính đã kiểm tra tạo bản ghi thật với `repId` Sales và đúng NPP, không xóa dữ liệu kiểm thử                                                                         |
| Responsive                                        | Audit 1440/900/390px không tràn trang, form ngắn không cao như sheet, nút điều hướng/lưu hiện được                                                                         |
| Reduced Motion                                    | Audit tạo điểm bán xác nhận animation của bước bị tắt                                                                                                                      |
| Format                                            | Các file thay đổi được kiểm riêng và pass; format toàn repo còn 24 file lệch từ trước, không sửa đại trà                                                                   |
| Bản production/JAR, E2E và design suite sau chỉnh | Build web và đóng gói JAR thành công; 18 live integration checks và 12 design checks pass trên bản production mới tại cổng 8080. MySQL và tệp tải lên được giữ khi restart |

Audit: `.tools/sales-audit/npp-simplify-check.mjs` và `.tools/sales-audit/npp-simplify-results.json`; lần chụp lại dùng chế độ chỉ chụp, không tạo thêm cửa hàng. Ảnh tại `.tools/sales-audit/screens/npp-simplify-{finance,store-step1,store-step2,workspace,feedback,support,appointment}-{1440,900,390}.png`, thêm `ledger-1440` và `logo-1440`; đối chiếu theo những file thực tế tồn tại. Dialog được chụp sau khi chờ animation ổn định, mobile 390×844 dùng viewport thay vì full-page lẫn nội dung nền.

Đã chạy lại audit chỉ chụp/đọc trên bản JAR mới tại `http://127.0.0.1:8080`, log `artifacts/npp-simplify-production.log`; không tạo thêm cửa hàng ở lượt kiểm tra production. Detector chạy một lần trên các file UI liên quan: chỉ nêu viền tam giác `product-pack::before` 12px kế thừa, không phải accent mới trên card/form. Không sửa minh họa cũ ngoài phạm vi. Kết quả này không thay thế kiểm tra phân quyền nhiều hãng hoặc UAT toàn bộ nghiệp vụ.

Reviewer độc lập `npp_simplify_review` đã xem bộ ảnh sau chụp lại, ghi `disposition: ship` cho các bề mặt đang sửa. Verdict này chỉ xác nhận bố cục/hình thức trong phạm vi finance, workspace, store form, appointment, support và logo; không chứng nhận backend hoàn chỉnh, toàn bộ CRM 5in1/DMS hoặc native iPhone. Chuyển động và lưu dữ liệu dựa vào kiểm tra runtime của agent chính, không thể chứng minh chỉ bằng ảnh tĩnh.

## Giới hạn còn giữ

Không có schema/backend mới trong đợt tinh gọn này. Sổ phải thu điểm bán, quyền độc lập theo NSX/nhãn hàng, HR/chấm công, sự kiện và cộng đồng cần được đánh giá theo contract/nguồn riêng; không coi một lối tắt hay tên module là chức năng đã hoàn thiện. Không phát hành IPA, commit/push/merge hoặc xóa dữ liệu. Hệ thống thiết kế gốc được mở rộng ở [DESIGN-SYSTEM](../DESIGN-SYSTEM.md); các chênh format/cấu trúc cũ ngoài phạm vi không được hợp thức hóa thành quy tắc mới.
