# SYNERA Design System

13/11/2026, thao tác đơn và thông báo: ngoại lệ được người dùng yêu cầu cho hành động bảng là nút có viền, dùng accent/primary hiện có: **Xử lý đơn** ở danh sách, nhãn hành động theo trạng thái ở Tổng quan. Bộ chọn đặt hàng chỉ +/−, form số khác dùng spinner native. Lý do/ghi chú thao tác là tùy chọn; trường nghiệp vụ chính giữ yêu cầu riêng. Tiến trình đơn có năm bước, một hành động tiếp theo; hủy/đặt lại/hoàn là thao tác phụ. Thông báo web là panel nổi bên chuông với Tất cả/Chưa đọc, chấm chưa đọc và thời gian; không đưa mã trạng thái kỹ thuật lên nhãn. Sales dùng banner thông báo mới trên nền sáng hiện có, chạm mở Inbox; cập nhật đơn/thông báo mỗi 2 giây khi hoạt động, giữ dữ liệu trong lần tải nền, không phát lại thông báo cũ thành banner mới. Đây là foreground, chưa có APNs/push khi app đóng. Bảng đơn tablet giữ cột xử lý trong viewport, tạm ẩn ngày tạo 761–1100px; mobile vẫn có đầy đủ nhãn theo card. Chi tiết [14](npp-portal/14-don-hang-va-thong-bao.md); ảnh Expo Chromium chỉ chứng minh preview, không thay nghiệm thu iPhone.

Đặc tả áp dụng từ mẫu người dùng ngày 10/11/2026. Bản tham khảo đầy đủ: [DESIGN-BRIEF.md](DESIGN-BRIEF.md). Luồng code: [ARCHITECTURE.md](../ARCHITECTURE.md).

## Quản trị: theo bố cục Sapo

| Vai trò                | CSS token         | Giá trị   |
| ---------------------- | ----------------- | --------- |
| Canvas                 | `--canvas`        | `#F4F6F8` |
| Card/table/header/form | `--surface`       | `#FFFFFF` |
| Nền phụ                | `--surface-muted` | `#F3F5F7` |
| Sidebar                | `--nav`           | `#122B49` |
| Nav hover              | `--nav-hover`     | `#1B3A5D` |
| Nav active             | `--nav-active`    | `#1F5996` |
| Chữ sidebar            | `--nav-text`      | `#E6EDF6` |
| Chữ phụ sidebar        | `--nav-muted`     | `#AABBD0` |
| Action/link/focus      | `--primary`       | `#1769C2` |
| Action hover           | `--primary-hover` | `#1255A0` |
| Accent nhẹ             | `--accent`        | `#EAF3FF` |
| Chữ chính              | `--text`          | `#1F2937` |
| Chữ phụ                | `--text-muted`    | `#526071` |
| Metadata               | `--metadata`      | `#5D6D80` |
| Viền                   | `--border`        | `#E0E5EB` |
| Viền input             | `--border-strong` | `#BDC7D3` |

- Segoe UI / Arial; body 14px, nav 13px, bảng 13px, metadata thông thường 11–12px, heading 24px, KPI 30px.
- Shell quản trị mặc định/NPP: sidebar 230px; thu gọn 68px và lưu lựa chọn vào `synera-sidebar-collapsed`. Tablet tự dùng rail 68px; mobile dùng drawer. Portal NSX có biến thể rail riêng được ghi bên dưới.
- Header 55px desktop, 60px mobile; nội dung max 1600px, padding 24px desktop / 16px mobile, gap 20px, card radius 5px.
- Nút desktop 38px, mobile 44px; input mobile 16px để hạn chế tự zoom. Label luôn hiện, lỗi có chữ và đặt gần form.
- `Table` dùng chung: tìm kiếm từ feature, sort tại header, phân trang, mobile chuyển sang card có nhãn cột. Không làm trang tràn ngang.
- Modal và drawer quản lý focus, Escape, khóa scroll và trả focus. Drawer mobile có aria-modal. Menu rail có title/nhãn truy cập.
- Các vùng dữ liệu admin dùng nền phẳng, không dùng glass. Ngoại lệ người dùng xác nhận ngày 13/11/2026: menu trái NPP dùng dải xanh theo token; không lan gradient sang bảng/form. Xanh lá/vàng/đỏ biểu thị trạng thái và luôn có chữ.
- Chế độ tối là tùy chọn, dùng cùng token semantic; mặc định là trắng/xám với sidebar navy.
- Chart.js dùng `chartTheme()` để đọc token; không đặt hex trực tiếp trong JSX.

## Landing

Landing độc lập với admin, scope `.landing-page`:

- Cyan `#079BEA`, xanh `#2454DC`, tím `#6239DF`; chữ `#192444`, phụ `#53617D`.
- Hero dùng video `public/assets/landing/background-lp.mp4`, overlay navy đậm bên trái và mờ dần sang phải; mobile tăng overlay để giữ tương phản.
- Không đặt khối dashboard hoặc thẻ nổi lên hero. CTA bo tròn, tiêu đề các section căn giữa.
- Header fixed kính mờ, nền navy khoảng 70%, blur 22px, viền sáng. Cuộn quá 24px chuyển gradient cyan/xanh/tím; lên đầu trở về kính mờ.
- Menu giải pháp ba cột trên desktop, menu mobile xếp dọc trên nền trắng. Dùng `details/summary` cho điều hướng bàn phím.
- GSAP / ScrollTrigger có cleanup; reduced-motion dừng và ẩn video, bỏ reveal transform/opacity. Nội dung vẫn đọc được.
- Không mượn số khách hàng, tăng trưởng hoặc lời chứng thực của Sapo. Không tự công bố giá thương mại. CTA dẫn tới chức năng demo hoặc login có thật.

## Logo và tài nguyên

- Ảnh gốc: `public/assets/brand/synera-logo.png`, sao chép từ bộ tài nguyên SYNERA người dùng đã có ở repository cạnh bên.
- Biểu tượng trong ảnh khớp logo người dùng gửi. Không dùng biểu tượng xanh lá của bản dựng trước.
- `Brand` là thành phần hiển thị duy nhất của lockup; `icon.svg` đóng khung phần biểu tượng và nhúng chính dữ liệu ảnh gốc để favicon không phụ thuộc tải ảnh con.
- Giữ tỉ lệ, vùng trống, không filter đổi màu và không vẽ lại SVG giả.
- Logo gradient là artwork thương hiệu, không phải ngoại lệ cho gradient của card quản trị.
- Video landing ~26.5MB không nằm trong cache offline; bundle ứng dụng và logo vẫn được precache.

## Khi thêm màn hình

Dùng `PageHead`, `Panel`, `Table`, `Badge`, `Modal`, `SearchBox`, `ExportButton` hiện có. Màu mới phải có vai trò semantic ở tokens hoặc được scope riêng cho artwork. Bảng/ticket/hạn mức phải dùng chung nhãn trạng thái, định dạng tiền và đơn vị. Kiểm tra loading/rỗng/lỗi khi có nguồn async, cùng desktop/mobile và bàn phím.

## Sales native foundation — 11/11/2026

`apps/sales-mobile/src/theme.ts` giữ tint SYNERA và kiểu chữ hệ thống có font scaling. Người dùng yêu cầu nền sáng hơn khi thử iPhone: app cố định Light Mode, canvas trung tính `#f6f6f7`, khung trắng và bóng xám mềm `0 3px 14px rgba(27,39,51,0.075)`; xanh dành cho thao tác/trạng thái, không dùng làm nền tối. Logo PNG copy nguyên bản. Navigation là bốn tab và native stack, safe area theo platform, danh sách thay bảng quản trị. Xem [luồng/màn hình](sales-mobile/02-luong-va-man-hinh.md).

Web preview dùng để kiểm tra logic/layout cơ bản, không thay ảnh/bằng chứng iOS Simulator hoặc thiết bị thật. Native acceptance phải có Dynamic Type, light/dark, VoiceOver, bàn phím/back và vòng đời app. Icon/splash phát hành, camera/GPS và các màn thao tác chưa thuộc mốc đọc dữ liệu ban đầu.

Bố cục/thao tác Sales phải đối chiếu ảnh mockup người dùng cung cấp, không lấy prototype đọc API làm thiết kế cuối cùng. Ưu tiên lịch tuyến, hồ sơ trước ghé, danh sách sản phẩm có ảnh/UOM/lượng và giỏ theo ngữ cảnh; không thay bằng dashboard trang trí. Chức năng truy vết PDF hệ thống và dùng chung hợp đồng web/backend theo [quy tắc nguồn](sales-mobile/06-nguon-thiet-ke-va-tuong-thich.md).

Theo ảnh cắt ngày 12/11: mở rộng vùng chào xanh và fade về canvas sáng, thêm các surface cùng dải xanh `#f0f7ff`/`#dfeeff`/`#eaf3ff` cho số liệu và header; màu xanh đậm dành cho lượt đang ghé. Chỉ có một fade khi lượt nổi lên, Reduce Motion bỏ animation. Lời chào là tiêu đề chức năng của màn Hôm nay, không thêm kicker trang trí. Xem [trang chủ/lộ trình](sales-mobile/08-trang-chu-va-lo-trinh.md).

## Portal NPP — mở rộng hệ thống hiện có, 12/11/2026

Áp dụng trong shell `.npp-portal`; không thay thế palette hoặc quy tắc landing/Sales native. Token gốc vẫn ở `src/styles/tokens.css`, bố cục và trạng thái ở phần NPP của `src/styles/admin.css`. Bằng chứng hữu hạn: [giao diện NPP](npp-portal/06-giao-dien.md).

| Thành phần                     | Quy tắc NPP                                                                                                                                                   |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Canvas và nền phụ ở Light Mode | `#F7F7F8`, trung tính, không pha xanh                                                                                                                         |
| Phân cấp công việc             | Bốn lane chọn hàng đợi: chờ xác nhận, xuất/giao, nhận từ NSX, chênh lệch. Lane chọn dùng tone đậm, lane còn lại tone nhạt. Dữ liệu/số đếm từ API              |
| Panel và thẻ sản phẩm          | Radius 12px; không viền bao; bóng `--shadow-panel`. Header nền phụ, giỏ hàng dùng accent                                                                      |
| Bóng Light Mode                | `0 3px 7px rgba(31,41,55,.055), 0 10px 24px rgba(31,41,55,.075)`; hover dùng `--shadow-panel-hover`                                                           |
| Chữ                            | Giữ font hệ thống; PageHead NPP 28px, Panel 16px, bảng 13px. Số dùng tabular numerals; tiền căn phải                                                          |
| Bảng desktop                   | Padding ô 16px 20px; cột tiền 150px, trạng thái 160px, ngày 150px. Tên/địa chỉ dùng phần rộng còn lại; không căn đều máy móc                                  |
| Trạng thái                     | Chữ và chấm có màu semantic trên nền trắng, không bọc chip màu. Không chỉ dùng màu để truyền nghĩa                                                            |
| Tổng quan                      | Bỏ strip và cập nhật thủ công; dock Lập đơn/Excel/trợ lý/bổ sung; bốn lane chọn worklist toàn chiều rộng; hai panel dưới bằng nhau cho chênh lệch và phản hồi |
| Chọn hàng                      | Một thẻ/SKU, chọn UOM trong thẻ; ảnh giữ tỉ lệ; điều khiển giảm–số lượng–tăng trên một hàng. Giỏ 330px desktop, 285px ở nhịp tablet                           |
| Chi tiết giao bù               | Tiến độ hàng, lượng giao/thực nhận và phản hồi khách riêng. Phản hồi và lịch sử chia hai cột; không dùng lời giới thiệu tính năng                             |
| Responsive                     | Dưới 1100px dock/lane thành hai cột. Dưới 760px worklist là tối đa bốn tóm tắt đơn; hai panel dưới/chi tiết/giỏ xếp dọc, sản phẩm hai cột, nút tối thiểu 44px |

**Tone theo công việc:** xanh thương hiệu giữ vai trò đơn bán, action và điều hướng; kho/nhận hàng dùng `--stock-ink: #08786B` và `--stock-soft: #E8F6F1`; chăm sóc/trợ lý dùng `--care-ink: #7050AA` và `--care-soft: #F1ECFA`. Amber dùng token warning cho việc cần chú ý; đỏ giữ nghĩa lỗi/nguy hiểm. Tone khu vực không thay nghĩa trạng thái. Dark Mode có cặp stock `#78D6BF`/`#183C38`, care `#CAB4F0`/`#342744`. Panel-head, th và sort-header kế thừa tone từ route/class; bảng không có title và nhãn cột mobile vẫn nhận đúng tone.

**Worklist và lối tắt:** bảng tổng quan dùng padding 14px 20px, cột mã đơn 210px, thao tác 155px; các bảng khác giữ quy tắc ở trên. Mobile bỏ chiều rộng cột desktop, cho nội dung wrap tự nhiên và căn nhãn cột trái. Khi hàng đợi chênh lệch có hơn bốn dòng, link xem thêm tách rõ đơn nhập chênh lệch và đơn bán giao thiếu. `/references?view=import` tập trung chọn dữ liệu/tải mẫu/chọn tệp/kiểm tra/nhập, ẩn trình sửa danh mục. `/reports?export=SELL_OUT` mở form xuất với loại Đơn bán cửa hàng; `/catalog?assistant=1` mở trợ lý hiện có. Các URL này không thay contract backend template/preview/import/export/parser/quote.

**Nội dung trong portal:** tiêu đề gọi đúng đối tượng/công việc; nút gọi đúng hành động. Bỏ slogan, đoạn giới thiệu chức năng, giải thích kỹ thuật backend và câu hướng dẫn thao tác chung. Giữ nhãn biểu mẫu, dữ liệu nguồn, đơn vị, trạng thái, hạn, lỗi có cách khôi phục và cảnh báo cần cho quyết định nghiệp vụ. Nhãn ảnh minh họa hay nguồn xử lý không phải hướng dẫn sử dụng và phải phản ánh đúng sự thật.

**Motion:** dùng phản hồi ngắn tại điểm thao tác: dock/lane/hover hàng công việc 180ms, shadow/transform hàng đợi 200ms, tổng giỏ đổi nền 220ms. Không có reveal hàng loạt, glow chữ, animation số liệu lặp hay hiệu ứng trang trí che nội dung. `prefers-reduced-motion: reduce` tắt transition/animation trong portal. Trạng thái loading/disabled/lỗi vẫn phải đọc được khi không có chuyển động.

### Hồ sơ và thao tác — 13/11/2026

- Menu NPP còn sáu khu vực được lọc theo quyền: Tổng quan, Đơn hàng, Kho hàng, Điểm bán & CRM, Công nợ, Báo cáo. Tác vụ phụ nằm trong ngữ cảnh và vẫn kiểm `canVisit`; hỗ trợ có đường vào từ Kho và Giao bù cho vai kho không có dashboard/CRM.
- Chuyển sắc chỉ ở `.npp-portal .sidebar`: `--nav-gradient-light` `#236FA8` → `--nav-gradient-mid` `#17517F` → `--nav-gradient-deep` `#122B49`, góc 165°. Gradient này theo lựa chọn rõ ràng của người dùng, không làm tiền lệ cho form/table.
- Icon menu là sáu SVG gốc Tabler, giấy phép MIT trong `public/assets/icons/tabler/LICENSE`; nguồn và SHA256 tại `sources.json`. Không thêm package icon thứ hai vào runtime.
- Logo dùng toàn bộ artwork `synera-logo.png`, không ghép lại chữ SYNERA bằng font UI. Theo góp ý tiếp theo ngày 13/11, sidebar mở hiển thị ảnh PNG trong suốt 190×48px trực tiếp trên dải xanh, bỏ mảng nền trắng/bo góc quanh ảnh; rail chỉ giữ biểu tượng gốc. Không vẽ lại, sửa ảnh hay đổi màu logo. Ảnh JPEG mới trong folder người dùng được đối chiếu với artwork gốc có sẵn.
- Bấm đơn hàng hoặc điểm bán chuyển sang chế độ chi tiết, không nối thêm một khung sau danh sách. Hồ sơ điểm bán có contact, Sales phụ trách, nhóm, tuyến và trạng thái; năm phần đơn/tương tác/lịch hẹn/ticket/trưng bày dùng tab. Các query vẫn độc lập, mỗi phần giữ phân trang server; chỉ phần chọn hiện trên màn hình.
- `ActionForm` NPP chọn không gian theo lượng thông tin: tối đa ba trường và không có `intro` dùng dialog gọn rộng tối đa 640px, cao theo nội dung; nhiều trường hoặc có bằng chứng/ngữ cảnh `intro` dùng sheet sát phải rộng 640px, duyệt ảnh tối đa 920px. Header sheet giữ khi cuộn. Nội dung không có thẻ lồng/viền ngoài. Form nhiều trường chia hai cột desktop, một cột mobile; GPS không bắt buộc nằm trong disclosure. Chân form có Hủy và Lưu, có loading/error. Focus trap gồm cả Shift+Tab từ container ban đầu.
- Sheet duyệt ảnh đặt ảnh gốc tải qua API có xác thực cạnh trạng thái, điểm, độ tin cậy và provider; dưới là điểm duyệt/nhận xét. Mobile xếp dọc, cuộn tới footer. Object URL được revoke và request abort khi đóng hoặc đổi identity.
- Hành động trong ô bảng dùng chữ thay nút đóng khung, có khoảng cách khi nhiều hành động. Phân trang chỉ xuất hiện khi nhiều hơn một trang; không hiện câu đếm bản ghi phụ hoặc footer quảng bá trong NPP. Nhãn trường, lỗi và dữ liệu nghiệp vụ vẫn giữ.
- Header cột tiền và số tiền căn cùng phía phải. Sổ công nợ dùng nền trung tính; hóa đơn tách panel tone kho để không lặp hai header cùng màu. Reports worklist có cột được wrap trong chiều rộng panel, chuyển một cột ở tablet; không để overflow cắt nút xử lý.

Đây là mở rộng hệ thống đang có. Không chuẩn hóa toàn bộ CSS cũ, không tạo bộ nhận diện mới hoặc đổi tài liệu native như tác dụng phụ. Phạm vi và bằng chứng ở [08](npp-portal/08-ho-so-va-thao-tac.md).

### Tinh gọn tác nghiệp — cập nhật tiếp ngày 13/11/2026

- **Không gian làm việc:** dialog NPP tối đa 490px, radius 16px, padding 26px. Danh tính và vai được dịch ra tiếng Việt, danh sách đơn vị lấy từ phiên thật. Lựa chọn hiện tại dùng accent cùng chữ và dấu chọn; đổi mật khẩu/đăng xuất là hàng thao tác, không là hai nút đóng khung lớn. Không trình bày mã vai hoặc ID nội bộ như tên đơn vị.
- **Thêm điểm bán:** dialog tối đa 720px, radius 16px, hai bước thật: thông tin điểm bán → Sales phụ trách và vị trí. Dữ liệu giữ khi quay lại; bước đầu kiểm tra trường bắt buộc, chỉ nút Tạo điểm bán gửi API. Danh sách người phụ trách chỉ gồm Sales/giám sát trong phạm vi trả về; không chọn chủ NPP làm Sales mặc định. GPS tùy chọn nằm trong disclosure. Desktop chia hai cột, dưới 700px xếp một cột; input cao 46px.
- **Công nợ thông thường phía NPP:** với vai không có `CREDIT_WRITE`/`CLOSING_WRITE`, tiêu đề là Công nợ với NSX. Một dải số dư gồm còn phải trả, quá hạn và hạn mức được cấp; không tự tính chỉ số tài chính mới tại FE. Ba chế độ Hóa đơn phải trả, Lịch sử phát sinh, Phiếu đổi trả dùng chung một vị trí bảng. Ledger và phiếu đổi trả chỉ tải khi chọn; chứng từ ghi ngày theo định dạng địa phương. Màn kế toán có quyền tương ứng vẫn giữ nghiệp vụ riêng, không biến tinh gọn hiển thị thành bỏ chức năng kế toán.
- **Hỗ trợ bán hàng:** đối với NPP không có quyền duyệt trade hoặc ghi công nợ, `/trade` dùng nhãn Hỗ trợ bán hàng, còn hai chế độ Đề xuất/Phản hồi. Lối vào nằm cạnh Chính sách bán hàng; ngân sách/quyết toán dành cho vai có quyền tương ứng. Nếu chưa có bản ghi, hiện trạng thái rỗng và hành động phù hợp, không dựng thêm bảng rỗng. Các nhóm CRM 5in1 trong định hướng sản phẩm vẫn được giữ; điều hướng theo công việc không đồng nghĩa cắt nhóm nghiệp vụ.
- **Chuyển động và mật độ:** dialog gọn vào trong 180ms, đổi bước trong 220ms với `cubic-bezier(0.16,1,0.3,1)`; lựa chọn công nợ/workspace đổi nền và màu trong 180ms. Nội dung đứng yên sau phản hồi, không chạy animation lặp. Reduced Motion tắt các hiệu ứng này. Dialog có nội dung ngắn giữ chiều cao tự nhiên; không dành sẵn toàn bộ chiều cao viewport cho một trường ghi chú.

Đối chiếu source, bộ ảnh desktop/tablet/mobile ổn định sau animation và phạm vi kiểm tra tại [09](npp-portal/09-tinh-gon-tac-nghiep.md). Đây là mở rộng quy tắc NPP hiện có; token màu, font và hệ thống native giữ nguồn canonical cũ.

### Kết quả trên chi tiết đơn — 13/11/2026

Theo yêu cầu tiếp theo, chữ SYNERA trên menu xanh dùng tone trắng, giữ hình dáng chữ gốc và màu biểu tượng. Lớp ảnh trang trí được clip ở vùng chữ, không đổi file logo nguồn; các nơi nền sáng và biểu tượng rail giữ bản gốc. Không đưa nền trắng trở lại sau logo.

Vùng Giao nhận và Chăm sóc đứng trước bảng dòng hàng, hai cột desktop và một cột dưới 760px. Nền trung tính phẳng, stock/care dùng màu semantic hiện có; kết quả theo từng SKU/đơn vị, không cộng lẫn quy cách. Thực nhận gốc, nhận bù và việc báo khách trình bày riêng, không sửa badge lịch sử. Tổng quan đặt hàng đợi trước lối tắt; hàng đợi dùng dạng gọn đến 1100px để giữ nút xử lý trong viewport. Bằng chứng và phần được giữ thành thay đổi backend riêng tại [11](npp-portal/11-gop-y-va-ket-qua-tren-don.md).

Kết quả hiện tại ưu tiên Đã giao bù đủ/Đã báo khách; mốc giao thiếu cũ dùng chữ phụ ghi rõ Lịch sử. App Inbox đặt việc và phiếu đang xử lý trước thông báo, có lối xem lại phiếu hoàn tất. Trạng thái rỗng phải dựa trên danh sách sau lọc; không để header rỗng khi tất cả phiếu đã xong. Đây là mở rộng trình bày tác nghiệp, không thay token hoặc thêm hệ thống UI native khác.

## Portal NSX — biến thể điều hành mạng lưới, 13/11/2026

Đây là mở rộng hệ thống SYNERA hiện có, áp dụng trong `.nsx-portal`. Giữ artwork thương hiệu, font hệ thống, semantic token và thành phần dùng chung; bố cục NSX đối chiếu bộ ảnh CloudGO người dùng cung cấp. Không thay quy tắc NPP, landing hay Sales native. Nguồn và phạm vi màn hình: [Web NSX](nsx-portal/01-nguon-va-man-hinh.md). Token canonical ở `src/styles/tokens.css`; quy tắc biến thể ở khối NSX đầu `src/styles/admin.css` và `src/features/portal-nsx`.

### Màu, chữ và bề mặt

- Canvas Light Mode dùng `--nsx-canvas` (`#F6F8FA`); panel và hồ sơ dùng nền `--surface`, viền semantic, radius 5px, không bóng. Form trong sheet phẳng, không viền/bóng/bo góc riêng; sheet giữ `--shadow-sheet` để phân biệt lớp nổi.
- Rail dùng xanh `--nav-active`, nhãn sáng; mục đang chọn dùng nền trắng và chữ/icon `--primary`. Chỉ hiện biểu tượng gốc của logo trên rail; không ghép chữ bằng font UI.
- Giữ Segoe UI / Arial. PageHead 24px, tên hồ sơ 22px, tiêu đề phần 13–15px, nội dung bảng 13px; nhãn rail và header báo cáo dày 11px. Số liệu dùng tabular numerals, tiền căn phải. Đây là mật độ màn NSX, không đổi thang chữ native.
- Trạng thái dùng chữ có màu semantic, badge bỏ nền/viền/bo góc. Chăm sóc dùng `--care-ink`, hàng hoàn tất dùng `--stock-ink`, việc cần chú ý dùng warning. Màu khu vực không thay nghĩa trạng thái.

### Bố cục và điều hướng

- Desktop/tablet dùng rail xanh cố định 76px, icon trên nhãn; sáu khu vực Điều hành, Mạng lưới, Đơn hàng, Chính sách, Báo cáo, Thiết lập. Tác vụ phụ nằm ở các liên kết theo ngữ cảnh và vẫn lọc theo quyền. Không áp dụng quy tắc 230/68px của shell mặc định/NPP vào rail NSX.
- Topbar 55px; nội dung tối đa 1920px với padding 18px 24px 32px. Các vùng dữ liệu phẳng, phân tách bằng viền và header nền phụ; không kế thừa bóng/radius 12px của panel NPP.
- Điều hành đặt việc cần xử lý trước tình hình hàng theo NPP và việc chờ kết quả. Mỗi số đếm/hàng có đường tới đối tượng hoặc danh sách lọc liên quan; giữ người phụ trách, hạn và kết quả chăm sóc cạnh dấu vết giao nhận.
- Hồ sơ 360° thay vị trí danh sách khi mở: đầu hồ sơ có định danh, liên hệ, trạng thái và thao tác; tab ngang nối ngay bên dưới. Thông tin dùng hai cột label/value, chỉ mở một phần hồ sơ tại một thời điểm. Không nối toàn bộ bảng lịch sử bên dưới danh sách gốc.
- Tổ chức dùng cây phân cấp thật bên trái (250px desktop, 190px dưới 1200px) và bảng trực thuộc bên phải; thụt cấp theo quan hệ cha/con đã lưu. Hồ sơ tuyến giữ NPP và Sales phụ trách; bảng MCP có thứ tự, điểm bán, địa chỉ, NPP và lưới ngày ghé. Dấu chọn biểu diễn lịch backend, không phải toggle trang trí.
- Dưới 760px, menu dùng drawer 230px, hồ sơ và form xếp một cột; đầu hồ sơ được wrap. Tab ngang và lưới MCP/báo cáo cuộn trong vùng riêng, không kéo tràn toàn trang. Dưới 1200px, bộ lọc báo cáo và dải việc chuyển hai cột; dải việc xếp một cột dưới 760px.

### Báo cáo và thao tác

- Bộ lọc nằm trên bảng: kỳ, vùng, NPP, Sales, tuyến và nút Lọc dữ liệu. Desktop chia ba cột, nhãn luôn hiện; vùng nhóm theo Nhân viên/Nhà phân phối/Điểm bán nằm trước bảng.
- Header bảng chia nhóm ĐI TUYẾN màu `--primary` và KẾT QUẢ BÁN HÀNG màu `--report-sales-head` (`#A65A0B`), chữ trắng. Cam là dấu phân nhóm báo cáo, không mang nghĩa lỗi. Giữ các cột số căn phải, tên đối tượng căn trái, nguồn/kỳ/thời điểm cập nhật cạnh dữ liệu.
- Nút xuất mang đúng nhãn **Xuất CSV** và tạo tệp `.csv` UTF-8 mở được bằng Excel. Không gọi tệp đó là XLSX; lối xuất Excel/PDF hiện có giữ ở phân hệ gốc.
- Form điều kiện/tổ chức/tuyến dùng mặt phẳng, label rõ, hai cột desktop và một cột mobile; thao tác dài dùng sheet bên phải, tác vụ ngắn dùng dialog gọn. Chi tiết đơn giữ ảnh sản phẩm theo tỷ lệ, SKU/UOM và điều kiện giá/ưu đãi đã chốt. Không lấy bố cục tham khảo làm bằng chứng voucher, quà tặng tự động hay điều kiện chưa có backend đã hoạt động.
- Dữ liệu giao nhận và chăm sóc hiển thị riêng: giao bù đủ không tự suy ra đã báo khách. Giữ tên NPP/Sales, hạn, trạng thái và kết quả thực từ API; dữ liệu chưa tải hoặc lỗi không được chuyển thành KPI bằng 0. Không suy diễn lượt ghé thành tính năng nhân sự/chấm công hoàn chỉnh.

API NSX mới yêu cầu `REPORT_READ`, giữ phạm vi vùng/NPP của actor và chỉ mở khi installation có đúng một NSX; cấu hình nhiều NSX trả `409 MANUFACTURER_SCOPE_UNAVAILABLE`. Đây là giới hạn nguồn dữ liệu phải phản ánh trung thực trên giao diện, chưa phải cơ chế ownership/grant nhiều nhãn hàng hoàn chỉnh. Các quy tắc nghiệp vụ chi tiết nằm trong tài liệu nguồn, không được thay bằng bộ lọc hiển thị.
