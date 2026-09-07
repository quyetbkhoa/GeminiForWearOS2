# AGENTS.md - Quy Trình Chuẩn Hoạt Động (Agent Workflow & Guidelines)

Tài liệu này định nghĩa toàn bộ quy trình chuẩn (standard operating procedure) và các nguyên tắc cốt lõi của AI Agent khi nhận bất kỳ tin nhắn yêu cầu nào từ người dùng trong dự án **Gemini Voice Assistant for OPPO Watch**.
Mọi session làm việc mới đều phải tuân thủ nghiêm ngặt tài liệu này.

---

## 1. Tổng Quan Dự Án & Cấu Trúc Hệ Thống

- **Thư mục dự án**: `X:\OppoWatch\GeminiVoiceAssistant` (Git repository chính).
- **Thư mục gốc workspace**: `X:\OppoWatch`.
- **Hệ thống gồm 2 module Android song hành**:
  - `watch/`: Ứng dụng Wear OS 2.0 chạy trên đồng hồ OPPO Watch 46mm (Snapdragon Wear 3100, Android 8.1/9, API 26-28).
  - `phone/`: Ứng dụng Phone Companion chạy trên điện thoại Android (OPPO Find N3 / Android 14, ColorOS/OxygenOS, API 34).
- **Kênh kết nối & Truyền thông**:
  - **Bluetooth Google Wearable Data Layer**: Đồng bộ dữ liệu, API Key, Model, Theme và truyền giọng nói TTS giữa 2 thiết bị (`Wearable.getMessageClient`, `Wearable.getNodeClient`).
  - **Wireless ADB qua Wi-Fi**: Dùng để nạp và cài đặt APK Wear OS (`dadb` client) cũng như debug thiết bị (`192.168.110.26:5555` cho Watch, `192.168.110.217:...` cho Phone).
- **File APK phát hành đầu ra**:
  - `X:\OppoWatch\Gemini_Phone_Companion.apk` (ứng dụng điện thoại)
  - `X:\OppoWatch\Gemini_Watch_App.apk` (ứng dụng đồng hồ)

---

## 2. Quy Trình Chuẩn Khi Nhận Tin Nhắn Của Người Dùng (Standard Agent Workflow)

Khi nhận được một tin nhắn yêu cầu từ người dùng, Agent phải thực hiện tuần tự theo quy trình 6 bước sau:

```
[1. Tiếp nhận & Phân tích] ──> [2. Triển khai Code chuẩn] ──> [3. Build & Verify Gradle]
                                                                        │
[6. Setup & Hướng dẫn Test] <── [5. Git Commit & Push] <── [4. Đóng gói Release & Changelog]
```

### BƯỚC 1: Tiếp Nhận, Nghiên Cứu & Phân Tích (Analysis & Alignment)
- **Đọc kỹ và nắm bắt mục đích thực tế của người dùng**: Người dùng yêu cầu rất súc tích, chú trọng tính thực dụng khi lái xe hoặc sử dụng hàng ngày (rảnh tay, màn hình tắt, điện thoại trong túi, rung phản hồi, TTS rõ ràng).
- **Nghiên cứu codebase trước khi chỉnh sửa**: Dùng `view_file` hoặc `grep_search` để định vị chính xác file cần sửa, hiểu luồng dữ liệu hiện tại.
- **Xác định tính chất yêu cầu**:
  - *Nếu là câu hỏi tìm hiểu hoặc yêu cầu "phân tích tính khả thi, chưa code"*: Phân tích chi tiết kiến trúc, các giải pháp khả thi, ưu/nhược điểm, các ràng buộc kỹ thuật. **Tuyệt đối KHÔNG tự ý sửa code** khi người dùng chưa chốt phương án.
  - *Nếu là sửa lỗi (bug fix)*: Tìm nguyên nhân gốc rễ (root cause) thay vì chỉ sửa phần ngọn.
  - *Nếu là tính năng mới*: Thiết kế ăn khớp giữa cả 2 phía Watch và Phone.

### BƯỚC 2: Triển Khai Mã Nguồn (Clean Coding Standards)
- **Công cụ sửa file**: Dùng `replace_file_content` để chỉnh sửa file trong codebase.
- **Nguyên tắc giao diện Phone Companion**:
  - Mọi màn hình, thẻ (Card), ô nhập (EditText), nút bấm (Button) phải hỗ trợ đồng bộ **cả 3 Theme Style**:
    1. *Skeuomorphism* (Retro kim loại/da)
    2. *Liquid Glass* (Kính mờ xuyên thấu)
    3. *Material 3* (Phẳng hiện đại)
  - Phải hỗ trợ cả **Dark Mode** và **Light Mode**: Chú ý độ tương phản màu chữ:
    - Light Mode: Chữ tiêu đề màu tối `#0F172A`, mô tả `#475569`, không để chữ trắng/xám mờ chìm trên nền sáng.
    - Dark Mode: Chữ sáng `#F8FAFC`, accent `#38BDF8` / `#34D399`.
- **Nguyên tắc cử chỉ Back trên Phone**:
  - Luôn tuân thủ cơ chế `backCallback.isEnabled = (currentPage != NavPage.HUB)` để thao tác vuốt Back từ sub-page luôn quay về Settings Hub, không làm văng/thoát ứng dụng.
- **Nguyên tắc phía Watch (Road Mode)**:
  - Tối ưu cho người đi đường: Rung kép khi thành công, rung dài khi lỗi kết nối.
  - Xử lý im lặng tuyệt đối khi bật mic nhầm nhưng không nói (`question.isEmpty() && answer.isEmpty()`).
  - Hỗ trợ Partial WakeLock để gửi xong TTS trong nền kể cả khi người dùng hạ tay xuống làm tắt màn hình.
- **Nguyên tắc âm thanh TTS**:
  - Chỉ đọc TTS qua loa ngoài hoặc tai nghe của điện thoại (`PhoneCommunicator.sendTextToPhone` hoặc Service ngầm).
  - Không phát âm thanh trùng lặp khi thực thi Voice Actions (như Báo thức, Hẹn giờ, Trả lời tin nhắn).

### BƯỚC 3: Biên Dịch & Xác Minh (Build Verification)
- Sau khi hoàn thành chỉnh sửa code, **BẮT BUỘC** phải chạy lệnh biên dịch:
  ```powershell
  .\gradlew assembleRelease
  ```
- Kiểm tra kết quả biên dịch: Phải đạt `BUILD SUCCESSFUL` cho cả `:phone:assembleRelease` và `:watch:assembleRelease`.
- Nếu có lỗi lint hoặc deprecation warning nghiêm trọng, phải xử lý dứt điểm trước khi chuyển bước.

### BƯỚC 4: Đóng Gói Phát Hành & Ghi Nhật Ký (Release Packaging & Versioning)
- **Khi có tính năng mới hoặc sửa đổi lớn**:
  1. Tăng `versionCode` (+1) và `versionName` (ví dụ `1.3.0` -> `1.3.1`) đồng bộ ở cả:
     - `phone/build.gradle.kts`
     - `watch/build.gradle.kts`
  2. Cập nhật chuỗi hiển thị phiên bản trong `PhoneMainActivity.kt` và `activity_phone_main.xml`.
- **Sao chép APK phát hành ra thư mục gốc `X:\OppoWatch\`**:
  ```powershell
  Copy-Item phone\build\outputs\apk\release\phone-release.apk -Destination X:\OppoWatch\Gemini_Phone_Companion.apk -Force
  Copy-Item watch\build\outputs\apk\release\watch-release.apk -Destination X:\OppoWatch\Gemini_Watch_App.apk -Force
  ```
- **Cập nhật `CHANGELOG.md`**: Ghi rõ mục phiên bản mới với các tính năng và bản sửa lỗi theo chuẩn Keep a Changelog.

### BƯỚC 5: Đồng Bộ Mã Nguồn Git (Git Sync)
- Đưa tất cả file thay đổi vào staging:
  ```powershell
  git add phone/ watch/ CHANGELOG.md
  ```
- Commit với thông điệp rõ ràng theo chuẩn Conventional Commits:
  - `feat(...)`: Tính năng mới
  - `fix(...)`: Sửa lỗi
  - `refactor(...)`: Tái cấu trúc
  - `ui(...)`: Tối ưu giao diện
- Đẩy commit lên remote repository:
  ```powershell
  git push origin main
  ```

### BƯỚC 6: Triển Khai Thiết Bị & Hỗ Trợ Test Tự Động (Deployment & Auto-Setup)
Khi người dùng yêu cầu "setup full", "cài đặt lên máy", hoặc "chuẩn bị để test":
1. **Kiểm tra trạng thái ADB**:
   - Chạy `adb devices -l` để lấy danh sách IP và thiết bị.
   - Thường gồm:
     - Điện thoại (OPPO Find N3 / CPH2499): `192.168.110.217:...`
     - Đồng hồ (OPPO Watch / belugaxl): `192.168.110.26:5555`
2. **Cài đặt APK lên thiết bị**:
   - Điện thoại: `adb -s <phone_ip> install -r -d -t -g X:\OppoWatch\Gemini_Phone_Companion.apk`
   - Đồng hồ: `adb -s <watch_ip> install -r -d -t -g X:\OppoWatch\Gemini_Watch_App.apk`
3. **Tự động cấp toàn bộ quyền cần thiết qua ADB**:
   - Phía điện thoại:
     ```powershell
     # Cấp quyền truy cập thông báo (bắt buộc cho trả lời tin nhắn ngầm):
     adb -s <phone_ip> shell "cmd notification allow_listener com.oppowatch.gemini/com.oppowatch.gemini.phone.QuickReplyNotificationService"
     # Cấp quyền runtime:
     adb -s <phone_ip> shell "pm grant com.oppowatch.gemini android.permission.POST_NOTIFICATIONS"
     adb -s <phone_ip> shell "pm grant com.oppowatch.gemini android.permission.BLUETOOTH_CONNECT"
     adb -s <phone_ip> shell "pm grant com.oppowatch.gemini android.permission.BLUETOOTH_SCAN"
     ```
   - Phía đồng hồ:
     ```powershell
     # Cấp quyền Micro:
     adb -s <watch_ip> shell "pm grant com.oppowatch.gemini android.permission.RECORD_AUDIO"
     ```
4. **Khởi chạy ứng dụng**:
   - Phone: `adb -s <phone_ip> shell am start -n com.oppowatch.gemini/.phone.PhoneMainActivity`
   - Watch: `adb -s <watch_ip> shell am start -n com.oppowatch.gemini/.MainActivity`
5. **Hướng dẫn kiểm thử**: Liệt kê kịch bản test cụ thể, ngắn gọn để người dùng chỉ việc làm theo.

---

## 3. Danh Mục Tính Năng Cốt Lõi Cần Duy Trì Ổn Định

1. **Nhận diện giọng nói Gemini AI**:
   - Trả lời siêu ngắn gọn trong 1-2 câu súc tích.
   - Đọc kết quả TTS qua điện thoại / tai nghe Bluetooth.
   - Tự động im lặng nếu bật mic nhưng không nói gì.
2. **Hành động bằng giọng nói (Voice Actions)**:
   - `SET_ALARM`: Đặt báo thức hệ thống qua `AlarmClock.ACTION_SET_ALARM`.
   - `SET_TIMER`: Hẹn giờ đếm ngược qua `AlarmClock.ACTION_SET_TIMER`.
   - `REPLY_MESSAGE`: Trả lời nhanh tin nhắn gần nhất hoặc theo tên người gửi qua `NotificationListenerService` + `RemoteInput` (chạy ngầm 100% khi điện thoại tắt màn hình trong túi quần).
3. **Cập nhật 2 bước tuần tự (2-Step Update Workflow)**:
   - Bước 1: Tải & cập nhật app Mobile qua PackageInstaller.
   - Bước 2: Tải & cập nhật app Wear OS qua Wireless ADB client tích hợp (`dadb`).
   - Tuyệt đối không dùng Bluetooth để truyền APK cập nhật.

---

## 4. Quy Chuẩn Giao Tiếp Với Người Dùng

- Luôn giao tiếp bằng tiếng Việt tự nhiên, ngắn gọn, súc tích, đi thẳng vào đáp án.
- Sau khi thực hiện xong công việc, tóm tắt rõ:
  - Những gì đã được sửa hoặc phát triển.
  - File APK đã được copy ra đâu.
  - Trạng thái thiết bị hiện tại (nếu có kết nối ADB).
  - Hướng dẫn các bước kiểm thử trực quan.