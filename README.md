# FoldReader

Ứng dụng Android cá nhân để đọc EPUB, PDF, Word DOCX và ảnh. Giao diện Compose tự mở rộng cho màn hình trong của điện thoại gập; tiến độ được lưu theo URI của tài liệu để lần sau tiếp tục.

## Build APK

Mở thư mục `FoldReader` bằng Android Studio (JDK 17, Android SDK 35), đồng bộ Gradle, sau đó chạy `:app:assembleDebug`. APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Có thể tạo APK từ GitHub Actions ở tab **Actions → Build Android APK** sau khi đẩy project lên GitHub.

## Thêm tài liệu

Nhấn **Thêm sách** để chọn nhiều file trong bộ nhớ/ổ đĩa được Android cung cấp. Với URL `http(s)://`, chọn **Mở đường dẫn** và dán liên kết tải trực tiếp. Một số trang web yêu cầu đăng nhập/chống tải sẽ không thể đọc như URL trực tiếp.

## Phạm vi định dạng

- EPUB: trích xuất văn bản theo thứ tự mục trong spine.
- PDF: dùng Android PdfRenderer, hiển thị từng trang.
- DOCX: trích văn bản trong `word/document.xml`.
- Ảnh: hiển thị ảnh nguyên trang, có thể phóng to bằng thao tác hệ thống.

DOC cũ (OLE), DRM, EPUB mã hóa, DOCX có bố cục phức tạp và link không truy cập trực tiếp chưa được hỗ trợ. Hiệu ứng lật trang hiện là chuyển cảnh 3D nhẹ giữa các trang, không phải mô phỏng uốn cong vật lý.
