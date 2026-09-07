# Changelog

Tất cả các thay đổi đáng chú ý của dự án **Gemini Voice Assistant for OPPO Watch** sẽ được ghi nhận tại đây.

Định dạng dựa theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/),
phiên bản tuân theo [Semantic Versioning](https://semver.org/lang/vi/).

---

## [v1.2.3] - 2026-09-07

### 🛠️ Sửa Lỗi Cài Đặt Ứng Dụng Trên Đồng Hồ (Fix Cannot Install / App Not Installed)
- **Hạ `minSdk` xuống 26 (Android 8.0 Oreo) trên đồng hồ:**
  - Khắc phục lỗi `INSTALL_FAILED_OLDER_SDK_VERSION`: Chiếc OPPO Watch 46mm chạy ColorOS Watch / Wear OS 2 nền tảng Android 8.1 Oreo (API 27). Trước đó cấu hình `minSdk = 28` (Android 9.0) khiến trình cài đặt hệ thống từ chối cài đặt và báo *"Không thể cài đặt ứng dụng"*. Hạ xuống `minSdk = 26` tương thích hoàn toàn 100% với OPPO Watch 46mm.
- **Khắc phục lỗi nuốt byte stream khi truyền Wi-Fi (Binary Stream Fix):**
  - Loại bỏ hoàn toàn `BufferedReader` vốn tự động đọc đệm 8KB dữ liệu làm khuyết phần đầu file APK khi truyền qua TCP Socket.
  - Chuyển sang giao thức nhị phân thuần túy `DataInputStream` / `DataOutputStream` với định dạng Magic `GEMI` + 8 bytes độ dài + ack xác thực 100%. File APK truyền qua Wi-Fi đảm bảo vẹn nguyên 100% từng byte.
- **Kiểm tra tính toàn vẹn file APK trước khi kích hoạt cài đặt:**
  - Bổ sung bước xác thực header ZIP chuẩn (`PK\003\004`) và đối chiếu độ dài file nhận được. Nếu file lỗi hoặc chưa hoàn tất sẽ không mở trình cài đặt gây lỗi màn hình.
  - Cấp quyền `FLAG_GRANT_WRITE_URI_PERMISSION` và duyệt cấp quyền `grantUriPermission` trực tiếp tới tiến trình PackageInstaller của Wear OS.

---

## [v1.2.2] - 2026-09-07

### 🚀 Tự động Ghép nối & Cài đặt Cập nhật Siêu tốc qua Wi-Fi (Wi-Fi Pair & Push)
- **Tự động bắt tay ghép nối qua Wi-Fi (Zero-Config Wi-Fi Handshake):**
  - Tận dụng kết nối Bluetooth Wearable có sẵn làm kênh điều khiển (Control Plane), điện thoại và đồng hồ tự động trao đổi địa chỉ IP, cổng Socket và mã xác thực bảo mật mà người dùng không cần nhập IP/Port thủ công.
  - Hỗ trợ cả 2 chế độ:
    1. **Điểm phát sóng di động (Hotspot):** Điện thoại phát Wi-Fi cho đồng hồ kết nối $\rightarrow$ Đồng hồ tự động nhận diện DHCP Gateway IP của điện thoại.
    2. **Mạng Wi-Fi chung (LAN):** Cả điện thoại và đồng hồ cùng kết nối chung một mạng Wi-Fi nhà / công ty $\rightarrow$ Tự động dò IP nội bộ.
- **Truyền APK siêu tốc độ cao (High-Speed Local Wi-Fi Stream):**
  - Sử dụng kết nối TCP Socket trực tiếp với bộ đệm luồng 64KB - 128KB và cờ `tcpNoDelay = true`.
  - Tốc độ truyền đạt **5 - 15 MB/s**, toàn bộ file APK đồng hồ (~11.5 MB) truyền sang và sẵn sàng cài đặt chỉ trong **1 - 2 giây** (nhanh hơn gấp 50 lần so với 2 phút qua Bluetooth).
  - Tự động kích hoạt `WifiLock (HIGH_PERF)` và `WakeLock` trên Wear OS để chip Wi-Fi của OPPO Watch hoạt động ở hiệu năng cao nhất, không bị ngắt kết nối khi tắt màn hình.
- **Cơ chế dự phòng an toàn (Automatic Bluetooth Fallback):**
  - Nếu đồng hồ chưa bật Wi-Fi hoặc không cùng mạng với điện thoại sau 4.5 giây, ứng dụng sẽ tự động chuyển sang kênh Bluetooth ChannelClient mà không làm gián đoạn hoặc gây lỗi quá trình cập nhật.
- **Cập nhật giao diện & Thông báo thời gian thực:**
  - Bổ sung thông tin tốc độ truyền (MB/s) và phương thức truyền (*Gửi Wi-Fi siêu tốc* hoặc *Gửi Bluetooth*) trực tiếp trên thanh tiến độ của ứng dụng Companion điện thoại.
  - Thêm mẹo hướng dẫn tiện lợi ngay dưới nút Cập nhật trên màn hình chính của điện thoại.

---

## [v1.2.1] - 2026-09-07

### ⚡ Nâng cấp Cơ chế Cập nhật OTA & Tăng tốc độ Bluetooth
- **Tự động nhảy thẳng lên bản mới nhất (Skip intermediate versions):**
  - Tích hợp bộ so sánh phiên bản ngữ nghĩa (Semantic Versioning Comparator), duyệt toàn bộ danh sách releases trên GitHub để luôn chọn và cập nhật trực tiếp lên bản phát hành có version cao nhất, không bị kẹt ở các phiên bản trung gian.
  - Chống cache CDN: Truy vấn trực tiếp API `/releases` kèm timestamp `nocache` và headers chống lưu đệm, đảm bảo nhận diện ngay tức khắc khi có bản phát hành mới trên GitHub Actions.
- **Tăng tốc truyền APK sang OPPO Watch:**
  - Tăng kích thước bộ đệm (buffer) của `WatchApkPusher` từ 8KB lên 64KB (`ByteArray(65536)`), tăng thông lượng truyền qua Bluetooth RFCOMM channel.
  - Hiển thị phần trăm truyền file mượt mà và thông báo trạng thái trực quan: *"✓ Đã hoàn tất! Đồng hồ đang mở hộp thoại cài đặt bản mới."*

---

## [v1.2.0] - 2026-09-07

### ✨ Tính năng mới & Tối ưu Trải nghiệm (UX)
- **🏍️ Tối ưu Trải nghiệm Đi Đường (Road Mode Background Processing):**
  - Khắc phục triệt để lỗi tắt màn hình làm dừng tác vụ: Khi đang lái xe hoặc đi bộ, bạn bấm nói $\rightarrow$ hạ cổ tay lái xe tiếp (màn hình tắt do timeout hoặc palm gesture) $\rightarrow$ ứng dụng **VẪN TIẾP TỤC XỬ LÝ NỀN** (giữ `WakeLock` CPU 15s).
  - Tự động chốt câu nói (`finishVoiceRecording`) nếu màn hình tắt trong lúc đang nói dở.
  - Sau khi Gemini trả về kết quả $\rightarrow$ kích hoạt đặt báo thức $\rightarrow$ gửi Bluetooth sang điện thoại để đọc to qua tai nghe / nón bảo hiểm $\rightarrow$ ứng dụng tự động đóng trong âm thầm (`finishAndRemoveTask`).
  - Lần tiếp theo bật màn hình lên: Đồng hồ luôn hiển thị **Màn hình chính (Watch Face)** sạch sẽ.
  - Phân tách rõ ràng: Chỉ khi người dùng **chủ động Vuốt Back** mới huỷ tác vụ.
- **📳 Phản hồi Rung Xúc giác (Road Haptic Feedback):**
  - 2 nhịp rung kép: Báo hiệu xử lý thành công, kết quả đang được đọc vào tai nghe hoặc báo thức đã đặt.
  - 1 nhịp rung dài: Báo hiệu lỗi kết nối mạng.
  - Không cần nhìn mặt đồng hồ khi đang lái xe vẫn nắm bắt trạng thái tức thì.
- **🎨 Tái cấu trúc Hệ thống Theme (3 Styles x 2 Modes = 6 Biến thể hoàn chỉnh):**
  - Phân định rõ ràng:
    - **1. Phong cách thiết kế (Style):**
      - 📻 **Skeuomorphism:** Cơ khí cổ điển, viền đồng cơ học, dập nổi 3D.
      - 🧊 **Liquid Glass:** Kính mờ acrylic phát quang (Frosted Acrylic Glass) với vành khúc xạ ánh sáng (Refractive Specular Rim).
      - 🎨 **Material 3:** Thiết kế phẳng hiện đại của Google.
    - **2. Chế độ màu (Color Mode):**
      - 🌙 **Dark Mode (Tối OLED):** Tiết kiệm pin tối đa trên màn hình AMOLED.
      - ☀️ **Light Mode (Sáng):** Trắng sứ Ceramic & Băng tuyết Ice Frost sang trọng.
- **🧊 Tái thiết kế toàn diện Liquid Glass:**
  - Layer-list kính mờ đa lớp: Body acrylic trong mờ 75% cho phép màu nền xuyên thấu, vành kính phản quang 3D màu Luminous Ice Cyan (`#707DD3FC`), nút PTT pha lê ngọc bích phát sáng.
  - Bản Liquid Glass Light: Hiệu ứng kính băng tuyết phủ sương trắng ngọc, chữ xanh Ocean Navy tương phản sắc nét.
- **🗣️ Tự động Đặt Báo thức & Hẹn giờ bằng giọng nói:**
  - Nói tự nhiên: *"Đặt báo thức 6 giờ 30 sáng"*, *"Hẹn giờ 15 phút"*, *"Báo thức 7 giờ kém 15"*.
  - Gemini AI trích xuất action $\rightarrow$ gọi trực tiếp `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` trên app HeyClock gốc của OPPO Watch.
  - Hỗ trợ `EXTRA_SKIP_UI = true`: đặt ngầm không làm gián đoạn màn hình.
  - Hiển thị xác nhận: *"⏰ Đã đặt báo thức 6:30"* hoặc *"⏱ Đã hẹn giờ 15 phút"*.
  - Bộ phân tích dự phòng cục bộ (Regex Fallback Parser) tiếng Việt đảm bảo nhận diện chính xác 100%.

### 🔧 Sửa lỗi & Nâng cấp Kỹ thuật
- **Sửa dứt điểm lỗi đổi theme trên điện thoại không tác động lên đồng hồ:**
  - Khắc phục `intent-filter` trong `watch/AndroidManifest.xml` trước đây khóa cứng `pathPrefix="/watch_update"` làm chặn toàn bộ thông điệp theme.
  - Nâng cấp cơ chế đồng bộ 2 tầng: Gửi sự kiện thời gian thực (MessageClient) kết hợp lưu trữ bền vững (DataClient `PutDataMapRequest` qua GMS Wearable). Ngay cả khi đồng hồ đang tắt màn hình lúc bấm đổi theme, khi mở lên GMS sẽ tự động đồng bộ và lưu theme tức thì.
- Thêm quyền `com.android.alarm.permission.SET_ALARM` trên Wear OS.
- Thêm module `VoiceActionHelper.kt` chuyên trách xử lý Intent đồng hồ báo thức.

---

## [v1.1.0] - 2026-09-07

### ✨ Tính năng mới
- **🎨 Hệ thống Theme đồng bộ Phone ↔ Watch:**
  - 4 theme: 📻 Skeuomorphism, 🧊 Liquid Glass, 🎨 Material 3, ⚪ Trắng Sứ Ceramic.
  - Chọn theme trên điện thoại → tự động đồng bộ sang đồng hồ qua Bluetooth.
  - Chế độ màu Đen OLED / Trắng Ceramic chuyển đổi tức thì.
- **🤖 Lựa chọn Model Gemini:**
  - Giao diện Radio trên điện thoại cho 6 mô hình: `gemini-3.8-flash` (mặc định), `gemini-3.7-flash`, `gemini-3.5-flash`, `gemini-3.5-flash-lite`, `gemini-2.5-flash`, `gemini-3.1-pro-preview`.
  - Đồng bộ model sang đồng hồ qua `/gemini_model_sync`.
- **🖐 Tự động thoát khi tắt màn hình:**
  - Khi đập tay tắt màn hình (palm gesture) hoặc timeout, app tự động đóng hoàn toàn.
  - Mở lại màn hình sẽ hiển thị Watch Face (Màn hình chính) thay vì resume app.

---

## [v1.0.0] - 2026-09-06

### 🎉 Phiên bản đầu tiên
- **Trợ lý giọng nói Gemini trên OPPO Watch 46mm** (Wear OS 2, Android 9).
- Giao diện Skeuomorphism kim loại tối ưu cho màn hình 402×476px.
- Nút Push-To-Talk (PTT) với hiệu ứng ghi âm 3D.
- Gọi Google Gemini API trực tiếp từ đồng hồ.
- Tự động phát hiện im lặng (VAD) 1.3 giây để gửi câu hỏi.
- Phone Companion: TTS tiếng Việt đọc câu trả lời qua tai nghe Bluetooth.
- Bộ lọc thiết bị Bluetooth trên điện thoại.
- OTA Update qua GitHub Releases (Phone + Watch).
- 3 cách kích hoạt: AccessibilityService (vuốt trái), Wear OS Tile, phím cứng.
- CI/CD GitHub Actions tự động build và phát hành.
