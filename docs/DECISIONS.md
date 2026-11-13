# Quyết định kiến trúc

## 2026-11-13 — Khởi tạo quyền MySQL thống nhất trên Docker và portable

CI đã áp dụng đủ migration V119 nhưng dừng khi thu hồi grant `synera_local`: Docker lưu tên schema với dấu `_` được escape, khác bootstrap portable. Migration thu hồi có điều kiện cả hai cách ghi, sau đó chỉ cấp SELECT/INSERT/UPDATE/DELETE cho đúng schema; đọc `partial_revokes` để tránh hiểu `_` thành wildcard. Không bỏ bước hạn chế quyền và không cấp quyền DDL cho runtime. Kiểm thử MySQL riêng xác nhận chạy lặp lại, tài khoản mới, bootstrap Docker/portable, quyền CRUD và từ chối DDL/schema tương tự tên. Tham chiếu: [MySQL REVOKE](https://dev.mysql.com/doc/refman/8.4/en/revoke.html), [MySQL GRANT](https://dev.mysql.com/doc/refman/8.4/en/grant.html).

## 2026-11-13 — Web NSX theo hồ sơ và mạng lưới

Người dùng chỉ định cấu trúc CloudGO từ bộ ảnh của mình: rail xanh gọn, hồ sơ 360° với đầu hồ sơ/tab/thông tin hai cột, tuyến/MCP dạng bảng, báo cáo nhóm cột và bộ lọc. Áp dụng riêng `.nsx-portal`, giữ thế giới NPP/native và các token semantic. Không sao chép thương hiệu hoặc dựng tính năng voucher/quà tặng chưa có contract.

Read model `report.ManufacturerFacade` chỉ gọi Facade, không truy cập repo module khác. Đọc một NSX/installation hiện tại và fail closed nếu nhiều NSX; cùng scope vùng/NPP với dữ liệu gốc. Không dùng FE filter để tuyên bố đủ phân quyền nhãn hàng. NSX xem sell-out và giao bù như tiến độ NPP thực hiện; chỉ vận hành sell-in trên bề mặt này. API admin vẫn giữ thẩm quyền kỹ thuật để kiểm thử. [Đối chiếu nguồn và giới hạn](nsx-portal/01-nguon-va-man-hinh.md).

## 2026-11-13 — Ghi chú tùy chọn và đồng bộ xác nhận đơn

Yêu cầu trực tiếp mới nhất cho phép bỏ trống `reason` và ghi chú giao nhận. Backend chuẩn hóa missing/null thành rỗng, giữ kiểm tra quyền, trạng thái và số lượng; hồ sơ nhận thiếu vẫn ghi số thực tế. Web dùng nút “Xử lý đơn”, tiến trình và một hành động tiếp theo; đặt lại/hủy là thao tác phụ. Native và web cập nhật foreground qua polling 2 giây có chống chồng request, thông báo lưu bền qua outbox; chưa là APNs background push. Xem [14](npp-portal/14-don-hang-va-thong-bao.md).

## 2026-11-13 — Hồ sơ điểm bán, form tác nghiệp và menu NPP

- NPP dùng sáu vùng điều hướng chính, tác vụ phụ theo ngữ cảnh; không đổi permission của backend. Bổ sung lối hỗ trợ ở Kho/Giao bù để vai kho không mất quyền truy cập hiện có.
- Chế độ chi tiết thay phần danh sách cho điểm bán và đơn hàng. Hồ sơ CRM giữ năm query/phân trang server độc lập, chỉ hiện tab chọn. Appointment và interaction mới được gắn sẵn điểm bán đang xem; API và idempotency giữ nguyên.
- Form NPP dùng sheet bên phải; duyệt trưng bày xem được ảnh gốc có xác thực, kết quả máy và form duyệt cùng chỗ. Không đổi provider mock thành inference thật bằng cách sửa nhãn.
- Người dùng chỉ định rõ menu trái có dải xanh nhạt–đậm. `check-architecture.mjs` chấp nhận đúng gradient token ở selector `.npp-portal .sidebar`; vẫn cấm gradient/glass ở các vùng dữ liệu khác. Đây là cập nhật yêu cầu thiết kế, không tắt kiểm tra tổng quát.
- Dùng SVG Tabler có nguồn/MIT, logo đầy đủ từ artwork gốc thay chữ gõ tay. Không sửa file ảnh gốc của người dùng và không tạo nhãn hàng mới.
- Giữ phạm vi local, nhánh feature; không commit/push/merge hoặc phát hành IPA trong lượt FE này. Chi tiết và giới hạn: [08](npp-portal/08-ho-so-va-thao-tac.md).

## 2026-11-12 — Portal NPP và luồng lõi thật xuyên ba giao diện

- Kịch bản trong tài liệu chỉ dùng để thiết kế/kiểm thử; bỏ phương án đưa dữ liệu tình huống cố định vào FE. Bổ sung API backend dùng chung để web NSX, portal NPP và Sales mobile cùng thấy đơn, lượng, ngoại lệ và kết quả chăm sóc.
- V119 thêm giao bù sell-out liên kết đơn/dòng gốc: người duyệt khác người đề xuất, reserve/ship FEFO, thực nhận riêng; không tạo đơn bán hoặc khoản thu thứ hai. Báo cáo cộng phần thực nhận bù theo ngày nhận, giữ số gốc và snapshot đã chốt.
- OrderCreated sell-out gửi thông báo tới NPP đúng scope; SalesOrderUpdated/Replacement gửi tiến độ về Sales qua transactional outbox. Client tải định kỳ/focus; chưa triển khai OS push.
- Phản hồi khách gắn trạng thái mà người dùng đang xử lý. NPP đổi tiến độ sau phản hồi thì mở lại việc cần báo khách, giữ interaction/audit cũ; không đồng nhất xong phần hàng với xong lời hẹn.
- NPP_OWNER không quản trị Sales/tuyến; custom role bị giới hạn theo quyền base role hiện hành. Tự nhận sell-in quá hạn mặc định tắt, chỉ nhắc; legacy AUTO_RECEIVED chỉ dùng khi chủ động bật chính sách.
- Portal giữ branding/tokens chung, nền trung tính, dải xanh theo chức năng và bóng mềm; danh mục nhóm theo SKU, chọn quy cách trong một card, giá do server tính. API hỗ trợ nhập yêu cầu trả nháp catalogue-based, không tự gửi đơn hoặc tuyên bố đã train AI.

Nguồn và phạm vi cụ thể: [phân tích NPP](npp-portal/README.md), [hợp đồng backend](npp-portal/04-backend-dong-bo.md).

## 2026-11-12 — Trang chủ Sales theo ảnh cắt và số liệu cá nhân

- Vùng chào rộng chuyển xanh về nền sáng, các khung cùng dải xanh đậm/nhạt; giữ Light Mode. Theo yêu cầu dữ liệu mẫu, nhãn chỉ ghi Đơn hàng/Giá trị đơn/Doanh thu, không hiển thị ghi chú kỹ thuật trên số liệu.
- Thêm GET `/sales-orders/summary` chỉ đọc, actor/workspace server; doanh thu lấy lại lượng thực nhận/chiết khấu/ngày giao từ SalesFacade, không mở thêm quyền báo cáo NPP cho Sales và không thay quy trình tài chính. API vẫn giữ layer metadata.
- Lượt CHECKED_IN ưu tiên trước, đồng hồ từ timestamp server; giảm chuyển động theo thiết bị. Không đổi state khi chỉ bấm chọn điểm.
- Cá nhân hoá đúng tài khoản Sales local thành Quân Đào bằng thay display_name; avatar là ảnh người dùng cấp, asset chỉ gắn tài khoản này. ID/quyền/credentials không đổi, không migration. Xem [chi tiết](sales-mobile/08-trang-chu-va-lo-trinh.md).
- Thêm hai flow AI theo yêu cầu dùng dữ liệu mẫu, không nhãn Demo ở UI: display dùng upload/audit/mock worker sẵn có, luôn giữ bước xác nhận thủ công; hỗ trợ đơn dùng parser tiếng Việt/catalogue và đưa vào giỏ trước quote/gửi. Chưa phải model AI thật; phạm vi được ghi tại [tài liệu luồng](sales-mobile/09-tro-ly-va-trung-bay.md), không thay quy trình duyệt hoặc tự gửi đơn.

## 2026-11-11 — Chuẩn bị app Sales React Native/Expo riêng

- Người dùng chọn Expo cho app mobile đi thị trường, độc lập web React 18/Vite; package ở `apps/sales-mobile`, không khởi tạo lại router/store web.
- Người dùng làm rõ: NSX quản lý Sales, Sales được phân công làm việc tại NPP. Scope `nppId` hiện tại là phạm vi tác nghiệp hợp lệ; cần bổ sung quan hệ quản lý NSX/phân công, không bỏ scope NPP hoặc nâng Sales thành quản trị NSX. Lượt này chỉ phân tích và làm nền auth/đọc API, chưa đổi quyền hoặc thêm luồng ghi.
- Giữ API làm ranh giới với backend; không thực hiện chuyển MongoDB hoặc xóa dữ liệu MySQL trong công việc mobile.
- SecureStore native cho refresh token, RAM cho access token; preview web RAM-only qua proxy phát triển cùng origin, không dùng cookie browser contract và không nới CORS backend.
- Ban đầu chọn SDK 57 theo hướng dẫn giai đoạn chuyển tiếp. iPhone người dùng xác nhận đã cài Expo Go SDK 58, nên nâng riêng package mobile lên SDK 58 và đồng bộ dependency bằng Expo CLI; không nâng React/web root. Preview web trên Windows không được coi là iOS Simulator/native đã nghiệm thu.
- Phạm vi, mockup/draw.io gap matrix, component inventory, API và backlog ở `docs/sales-mobile/`.
- Người dùng yêu cầu bám mockup để tránh FE rập khuôn, đối chiếu chức năng với PDF phân tích thiết kế hệ thống 50 trang. Quy tắc truy vết FR/BF, mockup và tương thích API web–mobile ở [hướng dẫn nguồn](sales-mobile/06-nguon-thiet-ke-va-tuong-thich.md); prototype chỉ là nền kỹ thuật, chưa chốt visual design.
- Người dùng tiếp tục yêu cầu clone gần mockup và test chức năng trên iPhone. Triển khai các thao tác đã có API, order outbox SQLite native/owned browser preview, quote và ACK dùng chung server; Metro LAN bridge chỉ mở các POST Sales cụ thể. Không thay logic backend hoặc tự bổ sung loại đơn/ảnh/chấm công còn thiếu. Trạng thái mới ở [bàn giao triển khai](sales-mobile/07-app-theo-mockup.md).

## 2026-11-11 — Chọn MongoDB làm đích cho backend

- Theo yêu cầu người dùng, phạm vi là toàn bộ backend và MongoDB bắt đầu bằng database rỗng; không nhập dữ liệu từ MySQL.
- Đây là quyết định đích, chưa phải trạng thái runtime: MySQL hiện tại và dữ liệu của nó phải được giữ nguyên cho tới khi toàn bộ backend đã chuyển và nghiệm thu.
- Không đổi riêng URI/driver hoặc bật MongoDB khi các module còn dùng JPA/JDBC/Flyway. Chuyển đổi phải theo giai đoạn, giữ API và chứng minh transaction, scope, idempotency, ledger append-only trên MongoDB replica set.
- Các cổng nghiệm thu và điểm thiết kế còn mở được theo dõi tại `docs/MONGODB-MIGRATION-PLAN.md`.

## 2026-11-11 — Chỉ vận hành local (quyết định hiện tại)

- Ứng dụng chạy trên loopback bằng `scripts/run-local.py`, MySQL portable và dữ liệu/tệp lưu trên máy; không tự đồng bộ dữ liệu lên cloud.
- Đã gỡ cấu hình Netlify/Render/Docker, Compose VPS, launcher tunnel và các hướng dẫn deploy. GitHub Actions chỉ chạy kiểm tra, không deploy.
- Giữ nguyên DB, upload, artifacts và dữ liệu riêng hiện có; việc gỡ công cụ không xóa dữ liệu người dùng.
- Các quyết định hosting/demo bên dưới là lịch sử, đã được thay thế và không còn là hướng dẫn vận hành.

## Lịch sử — Commit dự án hiện tại cho Render

- Theo xác nhận mới nhất, giữ tên repo private `manhnd58/synera-web-demo-20261111`, commit nguồn `D:\Synera\synera_web` và dùng đúng nhánh main cho Render. Không đổi tên repo hoặc dùng nguồn SYNERA khác.
- Profile `local,cloud-demo` dành riêng cho hosting demo miễn phí: Java 25, cookie HTTPS, pool nhỏ, không seed lại DB, OTP mô phỏng và tệp tạm được ghi rõ trên FE. Production vẫn giữ guard SMTP và dữ liệu thật riêng.
- FreeDB bị loại vì thử tạo trigger thực tế lỗi 1419, không bỏ append-only triggers để deploy. Người dùng đã xác nhận Enzonic Free và chuyển bản sao sau kiểm tra; hiện chưa có bằng chứng nhập DB/deploy thành công.
- Snapshot cloud tạo trong DB local riêng; đổi mật khẩu/xóa phiên chỉ trên bản sao, giữ nguyên tài khoản và dữ liệu nguồn. Launcher tunnel dùng bản sao JAR để tránh giữ khóa file Maven trên Windows.
- CI Docker phát hiện thứ tự lifecycle: Spring với test instance PER_CLASS đọc datasource trước callback BeforeAll của Testcontainers. Khởi động container trong DynamicPropertySource trước khi đăng ký port suppliers; giữ nguyên 34 tình huống và điều kiện bắt buộc MySQL ở CI.

## Lịch sử — Chọn FreeDB cho bản demo cloud

- Người dùng chọn chuyển dữ liệu MySQL local sang FreeDB miễn phí và chấp nhận thời hạn tự xóa 30 ngày; giữ nguyên database nguồn. Chỉ dùng cloud sau khi kiểm tra TLS, trigger và khả năng nhập đầy đủ schema.
- Netlify build dùng `RENDER_API_ORIGIN` public và sinh API proxy trước SPA fallback, giữ API tương đối/cookie cùng origin. Không đưa secrets DB vào VITE hoặc bundle.
- Đã quan sát ba dashboard đăng nhập; FreeDB chưa xác minh email nên chưa tạo database. Chưa deploy và chưa chứng minh cookie/proxy/RAM trên các nền tảng thật. Hồ sơ triển khai cũ đã được gỡ theo quyết định local-only ở trên.
- Cập nhật: email đã xác minh, người dùng xác nhận tạo đúng một MySQL Sandbox; SQL dashboard trả ALL PRIVILEGES trên schema riêng. CLI và OpenSSL độc lập phát hiện CA không xác minh được leaf certificate/hostname của endpoint. Không tự bỏ xác minh TLS, chưa nhập dữ liệu.
- Chuẩn bị Git trong repository hiện tại; bỏ qua `.env.*` (giữ example), dữ liệu `.tools`, output/tmp và artifacts riêng tư. Scan 398 file staging không khớp các secrets local/cloud đã biết; đây không phải chứng nhận không có mọi loại secret.

## Lịch sử — Demo online không cần thẻ

- Người dùng chọn bản demo online, không có thẻ xác minh hosting. Dùng Cloudflare Quick Tunnel tạm thời, không coi là VPS miễn phí 24/7.
- Chạy thêm Java 25/H2 in-memory riêng tại 8081 và gateway link riêng tại 8082. Không tunnel website MySQL 8080 hoặc cổng database.
- Mật khẩu/JWT/link gate tạo ngẫu nhiên trong .tools/online-demo (gitignore). Gateway cấp cookie Secure/HttpOnly từ link riêng, giữ Authorization Bearer cho backend; CORS chỉ cho đúng hostname tunnel.
- OTP local, AI mock, storage riêng và background jobs demo; không dùng SMTP/MinIO/provider thật. Không thay đổi hoặc nới kiểm soát production.
- Đã kiểm tra HTTPS login/OTP, cookie Secure, chặn truy cập không có link, không lỗi JS và vòng tắt/bật demo. Dữ liệu H2 reset khi khởi động lại; hostname tunnel cũng thay đổi.
- Launcher và hướng dẫn demo online đã được gỡ; dữ liệu riêng trong `.tools` không bị xóa.

## Lịch sử — MySQL 8.4 và Caddy được cập nhật cho VPS

- [MySQL 8.0 đã EOL](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/). Cài mới/VPS/CI dùng MySQL 8.4.13 LTS, Caddy 2.11.9; giữ Java 25 và schema hiện tại.
- Suite 34 tình huống MySQL đã đạt trên cả 8.0.46 và 8.4.13. Local đã copy bằng dump sang DB 8.4 riêng, kiểm tra số dòng 93 bảng và tổng tài chính/kho/snapshot rồi mới chuyển lựa chọn. Không nâng binary trên data directory cũ, không xóa nguồn.
- Launcher cài mới chọn 8.4 tại 3308. Nếu có DB 8.0 cũ mà chưa chuyển, giữ chọn 8.0 tại 3307. Lựa chọn được xác minh lưu trong .tools/local-mysql-choice.json; --legacy dành cho tương thích cũ.
- Dừng local theo PID sở hữu và chờ process thực sự kết thúc, không suy ra process đã dừng chỉ từ health mất kết nối. Không dùng tasklist vì máy này chặn truy cập; Windows dùng QUERY_LIMITED_INFORMATION của process.
- Các thay đổi hạ tầng đã kiểm tra config và SQL local; Docker container/VPS/HTTPS/SMTP thật và restore drill vẫn phải nghiệm thu staging.

## Lịch sử — Tích hợp website/API, MySQL local và chuẩn bị VPS

- Java 25 là lựa chọn cuối cùng. Maven class version 69 được kiểm tra; m2e profile dùng target-ide, CLI dùng target-verified để tránh IDE trộn bytecode cũ. Activation dựa [m2e.version](https://wiki.eclipse.org/M2E-WTP_FAQ).
- FE chuyển auth và các màn hình nghiệp vụ sang API. Access token trong RAM, refresh HttpOnly cookie, CSRF Origin/header ở session endpoints; một workspace cho mỗi request, không union quyền chéo NPP.
- MySQL 8 portable cho local khi thiếu Docker; suite LocalMySqlIntegrationTest là bằng chứng MySQL riêng, Testcontainers vẫn bắt buộc ở CI. Không thay H2 thành bằng chứng MySQL.
- MySQL mới bootstrap V101/V102, updates dùng chung từ V103; trigger riêng V110/V113. Không sửa migration H2 cũ vì đã có dữ liệu. DB MySQL V1–V100 cũ cần đường nâng cấp riêng.
- SQL giữ OTP/rate-limit/job lock. Cache giá DTO ngắn hạn dùng version DB và scope/group; không dựng Redis khi ứng dụng chưa cần nó để chạy đúng.
- PWA giữ nháp theo user/workspace và chỉ xóa sau ACK. Không xóa dữ liệu demo cũ; cache offline chỉ tham chiếu Sales tối đa 24 giờ, không cache finance/API trong Service Worker.
- Thiết kế cũ dự kiến một image FE/API, MySQL, scanner và Caddy; đã bị thay thế bởi quyết định local-only ở đầu tài liệu.
- Filesystem private persistent là storage mặc định; S3-compatible là adapter tùy chọn. ClamAV fail closed. AI manual mặc định; HTTP/HMAC adapter tùy chọn và mock chỉ local/test.
- Hồ sơ 360 tải độc lập orders/interactions/appointments/tickets/display, phân trang SQL qua Facade và index V118; không tải toàn lịch sử vào một response.
- Service Worker chỉ cache shell/assets public; ignoreVary chỉ cho tài nguyên public để xử lý Vary: Origin của Spring khi reload offline. Hash cache gồm cả mã Service Worker.
- CI thêm job browser trên website JAR + MySQL thật; cấu hình CI chưa được chạy trên GitHub trong phiên local.
- Báo cáo async streaming XLSX/PDF, expiry/cleanup, lịch row lock + transaction, email theo chủ tài khoản. Không cam kết exactly-once đối với SMTP ngoài DB transaction.
- Quyết toán trade/chi thưởng là chứng từ append-only với người duyệt độc lập; cảnh báo đối soát chỉ phát hiện, không sửa ledger.
- Ghi chú triển khai cũ: cấu hình Compose từng được kiểm tra nhưng chưa nghiệm thu hạ tầng public; các file triển khai đã được gỡ.

## Lịch sử — Nâng backend lên Java 25 LTS

- Theo yêu cầu mới nhất, Java 25 thay Java 17 làm JDK runtime và target biên dịch của backend.
- Đồng bộ Maven compiler, bootstrap portable JDK, script build/format/smoke, Docker và CI lên Java 25; giữ Spring Boot 3.5.16.
- Java 25 class-file version là 69. H2 integration chạy trên máy; MySQL integration vẫn cần Docker/CI và không được coi H2 là bằng chứng thay thế.
- Thêm kiểm thử bắt buộc runtime Java 25 và bytecode 69, launcher local dùng JDK portable và đúng đường dẫn dữ liệu.
- Tách SMTP sender theo `OTP_MODE=smtp` để local/disabled không tạo health check SMTP chưa cấu hình; giữ kiểm tra SMTP khi dịch vụ được bật.

## 2026-11-10 — Backend dùng Java 17 theo xác nhận của người dùng

- Người dùng xác nhận dùng Java 17 theo PDF; quyết định này thay thế lựa chọn Java 25 trước đó.
- POM, Dockerfile, CI và bootstrap đều dùng Java 17. Spring Boot 3.5.16; không nâng major framework.
- Backend là modular monolith riêng trong `backend/`; giao tiếp qua Facade và kiểm tra ranh giới bằng ArchUnit.
- Local dùng H2 để chạy được khi chưa có Docker. MySQL 8 là đích production; không coi H2 là bằng chứng xác minh hành vi MySQL.
- FE tiếp tục chế độ demo đến khi tích hợp API theo từng luồng; không tự trộn LocalStorage demo với sổ công nợ thật.
- Các quy tắc bổ sung, khác biệt và phần còn thiếu được theo dõi tại `docs/BACKEND-BUILD-SUPPLEMENT.md`.

## 2026-11-10 — Áp dụng mẫu quản trị theo Sapo và logo SYNERA

- Giữ React/Vite/TypeScript, state machine và các module hiện có.
- Thay hướng xanh lá bằng navy/trắng/xám/xanh dương. Token là nguồn giá trị dùng chung.
- Tách `app/navigation.ts` và `Sidebar.tsx`; thêm rail thu gọn và drawer có focus management.
- Dùng artwork logo gốc và video đã có từ `D:\Synera\synera-web\frontend\public`; không sửa repository nguồn.
- Thêm landing riêng tại `/landing`, public root mở landing; dashboard vẫn tại `/dashboard`.
- Thêm GSAP/ScrollTrigger cho landing và Prettier cho code chung.
- Bỏ KPI tăng trưởng tĩnh và hệ số kỳ giả. Selector dashboard tổng hợp từ các đơn trong phạm vi lọc; giá trị đơn không được coi là doanh thu ghi nhận.
- Bốn bước vận chuyển trong mẫu là tham chiếu UX cho bản có module kiện/vận chuyển. Chưa tự thay thế state machine từ hai PDF.
- Đặt `ARCHITECTURE.md` làm nguồn kiến trúc chính, `AGENTS.md` làm điểm vào cho các tác vụ sau; bổ sung kiểm tra kiến trúc tự động.

## Lịch sử — Đối chiếu PDF triển khai miễn phí

- Yêu cầu mới chọn Netlify + Render + Aiven làm hướng triển khai. Kiểm tra nguồn chính thức phát hiện Aiven ngừng tạo MySQL Free từ 06/11/2026, xóa dịch vụ free cũ ngày 21/12/2026; không tự tạo dịch vụ có phí.
- Giữ Java 25, migration hiện tại, auth cookie và quy tắc production thay vì chép nguyên mẫu Java 17/seed V11/CORS trong PDF.
- Chưa deploy, chưa upload dữ liệu local; các ghi chú nhà cung cấp đã được gỡ theo quyết định local-only ở đầu tài liệu.

## Portal NPP: tinh gọn tác nghiệp và định vị CRM — 13/11/2026

- Theo góp ý trực tiếp mới nhất và đặc tả đề ngày 12/11: NSX/nhãn hàng là khách trả phí; NPP tham gia theo quan hệ/gói/quyền được cấp. Giữ mô hình Sales thuộc NSX. Đề xuất cũ về phần mềm độc lập cho NPP nhỏ không thay phạm vi đã chốt này. Phân tích và khoảng thiếu: [CRM 5in1 + DMS](npp-portal/10-dinh-vi-crm5-dms.md).
- Tinh gọn màn hình theo công việc, giữ module và API. NPP không có quyền ghi tài chính xem công nợ với NSX qua một bảng mỗi lần; quyền kế toán vẫn giữ giao diện nghiệp vụ đã có. NPP không có quyền duyệt/ngân sách xem đề xuất và phản hồi hỗ trợ, mở từ chính sách; không xóa Trade hoặc các chứng từ đang lưu.
- Form ngắn tối đa ba trường và không có bằng chứng dùng dialog theo nội dung. Form dài/bằng chứng giữ sheet. Tạo điểm bán hai bước giữ dữ liệu khi quay lại; chỉ thao tác cuối gửi API. Workspace dùng nhãn vai trò tiếng Việt và tên tổ chức thật.
- Logo PNG gốc đã có alpha; bỏ CSS nền trắng/bo góc để hiển thị trực tiếp trên menu xanh theo yêu cầu mới, không xử lý lại ảnh hay đổi màu artwork.
- CRM 5in1 là năm nhóm chức năng dùng chung dữ liệu và kích hoạt công việc với DMS. Không tạo năm bảng trống hoặc coi tuyến GPS là chấm công, outbox kỹ thuật là sự kiện khách hàng, danh mục đối tác là cộng đồng đã hoàn thiện.
- Đây là thay đổi FE và đối chiếu nguồn, chưa giải quyết hết quyền độc lập theo hãng, nghĩa vụ phải thu điểm bán, HR, sự kiện hay kết nối cộng đồng. Không suy ra backend hoàn chỉnh từ review hình thức.

## Cầu nối và việc sau bán — cập nhật tiếp 13/11/2026

- Metro Expo Go và gateway riêng dùng một policy full `/api/v1`; không mở rộng quyền backend hay browser session. Thao tác mở thông báo không phụ thuộc thành công của mutation đã đọc.
- Root giao thiếu sell-out mới dùng `SALES_DELIVERY_SHORT`. Chỉ hoàn tất khi đủ hàng thực nhận bù và đã ghi phản hồi cho các phiếu liên quan. Event đến trễ và nhịp rà lại dùng cùng điều kiện; lịch sử đơn, tiền và lượng nhận gốc không bị sửa. Task cũ được phân loại thận trọng bằng notification nguồn, không đồng nhất ID số sell-in/sell-out.
- Không tự đóng ticket/đối chiếu tài chính từ việc đã giao bù. Phần nhu cầu chưa xác nhận vẫn cần phương án riêng; không chuyển nó thành giao thiếu sau xuất. Phạm vi và kiểm tra ở [12](npp-portal/12-cau-noi-va-dong-viec-sau-ban.md).
