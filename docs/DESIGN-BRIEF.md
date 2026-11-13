# SYNERA design system — giao diện quản trị theo Sapo

## Landing page — cập nhật 28/10/2026

Quy tắc và kết quả áp dụng UI UX Pro Max mới nhất: [pages/landing.md](pages/landing.md).

Landing page tham khảo cách tổ chức nội dung tại https://www.sapo.vn/: giới thiệu sản phẩm, khám phá giải pháp, demo quy trình, vai trò, gói sử dụng và CTA. Giữ nội dung ngắn và các chức năng SYNERA hiện có; không dùng số liệu, khách hàng hay lời chứng thực của Sapo.

- Bảng màu landing độc lập: cyan #079BEA, xanh #2454DC, tím #6239DF; chữ chính #192444, chữ phụ #53617D. Nút chính gradient xanh–tím, nền trắng và các sắc xanh/tím nhạt.
- Hero dùng video `background-lp.mp4` phủ nền, lớp phủ navy đậm phía trái mờ dần về bên phải theo ảnh tham khảo, điểm sắc cyan–xanh–tím nhẹ; trên mobile tăng độ phủ để chữ sáng dễ đọc. Đã bỏ khối tổng quan minh họa và các thẻ nổi ở hero. Menu giải pháp ba cột trên desktop, xếp dọc trên mobile; nút bấm bo tròn và tiêu đề các phần căn giữa.
- Giữ GSAP/ScrollTrigger, hỗ trợ giảm chuyển động và thao tác bàn phím. Phần giới thiệu không thay đổi API, quyền hay nghiệp vụ.
- Header cố định trên hero, kính mờ với nền navy pha tím bán trong suốt 68–72%, blur 22px và viền sáng nhẹ ở đầu trang; cuộn quá 24px chuyển sang gradient cyan–xanh–tím, trở về kính mờ khi lên đầu. Logo/chữ sáng; menu mở giữ nền trắng dễ đọc. Khoảng đệm hero bù chiều cao header theo breakpoint.
- Các quy tắc không dùng gradient ở bên dưới áp dụng cho giao diện quản trị, không áp dụng cho landing page.

Cập nhật 25/10/2026, prototype v0.2. Yêu cầu trực tiếp mới nhất của người dùng chốt giao diện gần Sapo đã clone và bảng màu trắng, xám, xanh dương, xanh dương đậm. Quyết định này thay thế hướng kem/nâu của master prompt trước đó. Nghiệp vụ tiếp tục theo SRS SYNERA v0.2.

## Cơ sở lựa chọn

Áp dụng bộ UI/UX Pro Max người dùng cung cấp. Python/python3/py không có trên máy; không chạy CLI và không giả định đây là output tự động. Đã đọc SKILL.md, Quick Reference và checklist trong template skill. Dùng bản Sapo local `../kieu-hoa-web` để đối chiếu cấu trúc sidebar, header tìm kiếm, bảng trắng, tabs và form quản trị.

Đã kiểm tra dữ liệu `products.csv` #101 CRM, #102 Inventory: flat/minimal, chức năng trước trang trí. `colors.csv` #101 CRM và #49 Logistics có primary blue #2563EB, trắng và gray/slate; #7 Analytics hỗ trợ màu dữ liệu xanh. Chọn xanh #1769C2 để gần Sapo đồng thời có tương phản chữ trắng 5,47:1. Không dùng dark dashboard từ nhóm Financial Dashboard vì chỉ sidebar cần nền đậm.

UX áp dụng: #9 reduced motion; #28 focus visible; #33/#44/#55 phản hồi lỗi; #36 contrast; #41 keyboard; #71 bảng mobile; #100 focus không bị che. Nguồn trong archive: `work/synera-research/ui-skill/ui-ux-pro-max-skill-main/src/ui-ux-pro-max/data/`.

## Màu sắc

| Vai trò                  | Token         | Màu     |
| ------------------------ | ------------- | ------- |
| Nền ứng dụng             | canvas        | #F4F6F8 |
| Card, bảng, header, form | surface       | #FFFFFF |
| Nền trung tính           | muted surface | #F3F5F7 |
| Sidebar                  | nav           | #122B49 |
| Nav hover                | nav hover     | #1B3A5D |
| Nav active               | nav active    | #1F5996 |
| Chữ sidebar              | nav text      | #E6EDF6 |
| Chữ phụ sidebar          | nav muted     | #AABBD0 |
| Nút chính / link / focus | primary       | #1769C2 |
| Nút hover                | primary hover | #1255A0 |
| Accent nhẹ               | accent        | #EAF3FF |
| Văn bản chính            | text          | #1F2937 |
| Văn bản phụ              | muted         | #526071 |
| Metadata                 | metadata      | #5D6D80 |
| Viền                     | border        | #E0E5EB |
| Viền control             | strong border | #BDC7D3 |

Xanh đậm tạo cấu trúc điều hướng; xanh dương nhấn hành động và đường dẫn. Mặt nội dung dùng trắng/xám. Xanh lá, vàng và đỏ chỉ dùng nhỏ cho trạng thái thành công/cảnh báo/lỗi, luôn có chữ kèm theo.

Kiểm tra cặp màu lõi: primary/trắng 5,47:1; nav text/sidebar 12,14:1; nav muted/sidebar 7,31:1; secondary/trắng 6,42:1. Không coi các phép đo token này là audit WCAG đầy đủ.

## Bố cục theo Sapo

- Sidebar 230px, brand/header 55px, nav có icon và nhãn, active xanh. Có thu gọn 68px và giữ lựa chọn trên thiết bị.
- Header trắng: tìm kiếm toàn cục, nhãn dữ liệu demo, công việc, vai trò.
- Content max 1600px, padding 24px, gap 20px; card radius 5px, viền 1px, shadow rất nhẹ.
- Segoe UI / Arial; body 14px, nav 13–14px, bảng 13px, metadata 11–12px, heading 24px, số KPI 30px.
- Desktop CTA 36–40px; mobile 44px, input 16px. Form có nhãn, lỗi gần trường.
- Tablet rail; mobile drawer, một cột nội dung và bảng thành card. Focus drawer dùng preventScroll, khóa nền, Escape và trả focus.
- Animation ngắn, reduced-motion được tôn trọng. Không gradient, glass hoặc trang trí màu nâu.

## Màn hình và hành vi

Tổng quan: chào người thao tác, KPI từ dữ liệu đang có, bảng đơn, tồn kho và cột phải cho công việc/gợi ý/hành trình. Không hiển thị KPI giả tăng trưởng hoặc doanh thu suy từ trạng thái giao.

Xử lý đơn hàng: bốn hàng đợi đóng kiện → đặt giao → bàn giao → kiểm tra kết quả. Chọn trạng thái có URL riêng. Các link việc cần làm mở đúng hàng đợi.

Tạo đơn/chi tiết: form quản trị trên nền xám, card trắng, bảng hàng và panel gợi ý theo kiện; giữ các quyết định xác nhận đơn, kiện, đặt giao và bàn giao riêng.

Danh mục/kho/khách: tabs và toolbar gọn, tìm kiếm và sắp xếp bảng. Search toàn cục tìm đơn, SKU và khách; mở danh mục/CRM kèm bộ lọc đã chọn.

Kho chỉ nhận payload nghiệp vụ cho phép. Màu hoặc cách bố trí không thay thế phân quyền API. Các hãng, quy tắc và dữ liệu vẫn minh họa; không coi D03–D05 hoặc chính sách giá là đã chốt.
