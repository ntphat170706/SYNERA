# Kiểm tra trưng bày và hỗ trợ lên đơn

Cập nhật 12/11/2026 theo yêu cầu thêm hai luồng thử. Người dùng yêu cầu dùng tên tính năng bình thường, không gắn nhãn Demo trên giao diện. Khả năng và nguồn xử lý thực tế vẫn ghi rõ trong tài liệu kỹ thuật này; không coi đây là tích hợp mô hình AI production đã được nghiệm thu.

## Điểm vào

Trang Hôm nay có phần Trợ lý bán hàng với hai chức năng. Chọn chức năng → chọn điểm bán được giao → bắt đầu. Trong hồ sơ điểm bán, tab Hoạt động cũng có hai hành động tương ứng, giữ sẵn storeId và tên điểm. Không mặc định thao tác trên điểm bán khác hoặc mở danh mục ngoài scope.

## Kiểm tra trưng bày

- Native dùng expo-image-picker để chọn ảnh hoặc xin quyền chụp camera; browser preview dùng file picker. Preview ảnh trước gửi, giới hạn 5 MB theo backend. Không đưa ảnh chân dung người dùng vào dữ liệu kiểm thử chấm kệ.
- Chọn bộ tiêu chí ACTIVE từ server. Gửi ảnh multipart `/attachments/upload?nppId=…`, sau đó `/display-audits` với attachmentId, criteriaId, storeId và mã idempotency. Không tạo API chấm điểm riêng hoặc tự duyệt ảnh ở app.
- Local backend hiện đã có `MockAiClient`, trả điểm fixture 75/100 và provider `MOCK_LOCAL_NOT_AI`; worker xử lý qua ai_job hiện có. UI đọc lại kết quả server, không hardcode điểm ở frontend. Ảnh trùng được gắn cần kiểm tra, có thể chưa có điểm.
- Hiển thị điểm đề xuất nếu có, trạng thái chờ người phụ trách xác nhận và đường quay về điểm bán. Không lấy aiScore làm finalScore, không tự phát thưởng hoặc sửa doanh thu. Không tuyên bố nhận diện SKU/đếm mặt kệ thật khi provider hiện là mock.
- Dữ liệu mẫu đã thêm bộ tiêu chí **Tiêu chuẩn trưng bày SYNERA** qua API quản trị khi local chưa có tiêu chí; không migration hoặc mở quyền cấu hình cho Sales.

## Hỗ trợ lên đơn

Mốc này dùng bộ phân tích yêu cầu tiếng Việt ở client để minh hoạ tương tác; **chưa gọi LLM hoặc dịch vụ nhận dạng giọng nói riêng**. Có thể nhập bằng micro bàn phím iPhone. Hỗ trợ câu có số lượng/đơn vị/tên SKU, nhiều dòng hoặc “và”; chuẩn hoá dấu tiếng Việt và số từ cơ bản, đối chiếu offering thật của điểm bán.

Kết quả chỉ gồm SKU/UOM/lượng trong danh mục. Tên mơ hồ, đơn vị không tồn tại, không có số lượng, số âm hoặc sai bội số để ở phần cần làm rõ, không tự đoán hết. Người dùng sửa lượng rồi chủ động đưa vào giỏ nháp. Composer lấy giá/ưu đãi server lại và giữ nguyên các bước kiểm tra/gửi đơn; không có nút AI tự gửi hay tự duyệt. Giá không do bộ phân tích tự tạo.

## Dữ liệu và giới hạn

- Không có khóa API AI, audio hoặc dữ liệu ảnh gửi tới nhà cung cấp ngoài hệ thống trong mốc này. Ảnh chỉ được gửi khi người dùng bấm Gửi ảnh kiểm tra.
- Chấm trưng bày và lấy danh mục/gửi ảnh cần mạng; chưa có media outbox. Tắt màn trong khi chờ thì poll dừng; bản ghi server vẫn giữ.
- Camera, picker native, quyền Photos/Camera và bàn phím cần thử tiếp trên iPhone. Browser QA chỉ xác nhận file upload/pipeline và giỏ gợi ý, không chứng minh chất lượng nhận diện hình ảnh hoặc khả năng LLM.

Các giới hạn này nằm ở tài liệu/dev, không chèn nhãn Demo vào flow theo yêu cầu. Khi chuyển sang AI thật, dùng provider và hợp đồng hiện có, đánh giá chất lượng trước khi sử dụng kết quả tự động cho quyết định nghiệp vụ.
