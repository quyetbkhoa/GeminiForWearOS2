# ⌚ Wear OS 2 Gemini Voice Assistant & Phone Companion
### Trợ lý ảo Gemini cho OPPO Watch 46mm & Ứng dụng Companion đồng bộ Bluetooth TTS trên Điện thoại

Một giải pháp mã nguồn mở hoàn chỉnh nhằm thay thế **Google Assistant** cũ (đã bị Google khai tử trên Wear OS 2), biến chiếc **OPPO Watch 46mm** thành trợ lý AI hiện đại:
- **Trên Đồng hồ (Watch):** Giao diện Skeuomorphism cơ khí sang trọng, nút bấm Push-To-Talk (Nhấn giữ để nói), tự động gọi Google Gemini API (`gemini-3.5-flash-lite`) với prompt tối ưu câu trả lời ngắn gọn súc tích, hiển thị kết quả trực tiếp trên màn hình cong AMOLED.
- **Trên Điện thoại (Phone Companion):** Tự động nhận kết quả từ đồng hồ qua Bluetooth (Google Play Services Wearable Data Layer). **Chỉ khi điện thoại kết nối đúng với 1 trong các thiết bị Bluetooth được người dùng tick chọn (tai nghe, nón bảo hiểm thông minh...)** thì mới kích hoạt Google Text-To-Speech (TTS tiếng Việt) đọc to câu trả lời.

---

## 🌟 Các tính năng nổi bật

### 1. 3 Giải pháp kích hoạt tức thì trên Đồng hồ
1. **Chặn cử chỉ vuốt mép trái (`AccessibilityService`):** Lắng nghe khi người dùng vuốt từ trái sang phải tại mặt đồng hồ chính (nơi Google Assistant feed cũ ngự trị) để mở đè app Gemini Voice lên.
2. **Wear OS Tile:** Đặt làm Tile số 1 (chỉ cần vuốt từ phải sang trái 1 nấc).
3. **Phím cứng chức năng OPPO Watch:** Cho phép gán vào phím phụ vật lý ở cạnh dưới để bấm 1 phát vào ngay màn hình nói.

### 2. Thiết kế Skeuomorphism tối ưu cho OPPO Watch 46mm
- Kích thước chuẩn màn hình 402 x 476 px.
- Vùng đệm lề an toàn chống méo ở viền cong 3D.
- Nút bấm micro cơ học 3D, hiệu ứng dập nổi/chìm và viền sáng đỏ/vàng khi thu âm.

### 3. Prompt thông minh tối ưu màn hình nhỏ
- Ràng buộc Gemini trả lời trực diện, siêu ngắn gọn (1–2 câu) cho các câu hỏi Có/Không hoặc câu hỏi tra cứu dữ liệu, giúp hiển thị trọn vẹn không cần cuộn trang và đọc TTS nhanh chóng.

### 4. Bộ lọc thiết bị Bluetooth trên Điện thoại
- Liệt kê toàn bộ thiết bị Bluetooth đã ghép nối với Checkbox.
- Người dùng chỉ tick chọn thiết bị cá nhân (ví dụ: tai nghe Bluetooth, intercom nón bảo hiểm).
- Nếu ngắt kết nối hoặc kết nối loa ngoài / loa ô tô chưa được tick, app sẽ **tự động giữ im lặng** để bảo đảm tính riêng tư.

### 5. Tự động Cập nhật Không Dây Siêu Tốc (OTA Wi-Fi & Bluetooth)
- **Kiểm tra trực tiếp từ GitHub Releases:** App điện thoại có nút kiểm tra bản mới từ repository `quyetbkhoa/GeminiForWearOS2`, tự động chọn bản phát hành mới nhất (SemVer) mà không qua trung gian.
- **Tự động ghép nối Wi-Fi & Truyền APK trong 2 giây (Wi-Fi Pair & Push):**
  - Khi người dùng bật Điểm phát sóng (Hotspot) trên điện thoại cho đồng hồ bắt hoặc cả 2 cùng kết nối một Wi-Fi, app sẽ **tự động bắt tay ghép nối qua kết nối Bluetooth có sẵn** mà không cần nhập IP thủ công.
  - APK đồng hồ (~11.5 MB) được truyền thẳng qua TCP Socket tốc độ cao (5 - 15 MB/s) chỉ mất **1 - 2 giây**!
  - Giữ `WifiLock (HIGH_PERF)` và `WakeLock` trên OPPO Watch để đường truyền luôn thông suốt kể cả khi tắt màn hình.
- **Dự phòng an toàn qua Bluetooth (ChannelClient):** Nếu đồng hồ không bật Wi-Fi, app tự động chuyển sang Bluetooth an toàn mà không gây lỗi.
- **Cài đặt không cần máy tính:** Đồng hồ và điện thoại tự động mở hộp thoại cài đặt APK ngay khi nhận xong mà không cần cáp USB hay lệnh ADB!
- **Chữ ký số đồng nhất (Keystore):** Dự án sử dụng keystore cố định cho cả build local và GitHub Actions CI/CD, đảm bảo mọi lần cập nhật APK không bao giờ bị lỗi xung đột chữ ký (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

### 6. 🗣️ Đặt Báo thức & Hẹn giờ Tự động bằng Giọng nói
- Ra lệnh tự nhiên bằng tiếng Việt: *"Đặt báo thức 6 giờ 30 sáng"*, *"Hẹn giờ 15 phút"*, *"Báo thức 7 giờ kém 15"*.
- Tự động trích xuất tham số và gọi Intent `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` trên ứng dụng HeyClock gốc của OPPO Watch.
- Hỗ trợ `EXTRA_SKIP_UI=true`: Thiết lập ngầm không làm đè màn hình, kèm bộ lọc Regex Fallback nội bộ bảo đảm độ chính xác 100%.

### 7. 🏍️ Tối ưu Trải nghiệm Đi Đường (Road Mode & Rung Xúc giác)
- **Nói xong rảnh tay:** Bấm nói rồi buông tay lái xe tiếp (màn hình tắt do timeout hoặc úp tay), app **vẫn tiếp tục xử lý nền** (giữ CPU WakeLock 15s) $\rightarrow$ nhận kết quả $\rightarrow$ gửi Bluetooth sang điện thoại để đọc to qua tai nghe/nón bảo hiểm $\rightarrow$ tự động đóng task về Watch Face.
- **Rung xúc giác đi đường:** Rung kép (2 nhịp) khi hoàn tất thành công; rung dài khi lỗi kết nối.
- **Tự động chốt câu nói:** Nếu màn hình tắt trong lúc đang nói, app tự động chốt âm thanh và gửi đi.

### 8. 🎨 Hệ thống Theme Đa dạng (3 Styles x 2 Modes = 6 Biến thể)
- **3 Phong cách:** 📻 Skeuomorphism cơ khí cổ điển, 🧊 Liquid Glass kính mờ acrylic dạ quang, 🎨 Material 3 hiện đại.
- **2 Chế độ màu:** 🌙 Dark Mode (Tối AMOLED tiết kiệm pin) & ☀️ Light Mode (Sáng trắng sứ & băng tuyết).
- **Đồng bộ 2 chiều Phone ↔ Watch:** Đổi trên điện thoại lập tức đồng bộ sang đồng hồ qua cả MessageClient thời gian thực và DataClient lưu trữ bền vững.

---

## 📁 Cấu trúc Dự án (Multi-Module)

```
GeminiVoiceAssistant/
├── .github/workflows/                   # GitHub Actions CI/CD
│   └── build_and_release.yml            # Tự động build và phát hành Release khi push tag v*
├── keystore/                            # Keystore chuẩn hóa ký APK
├── watch/                               # Module ứng dụng chạy trên Đồng hồ Wear OS 2
│   ├── src/main/java/com/oppowatch/gemini/
│   │   ├── MainActivity.kt              # Màn hình Push-To-Talk, gọi Gemini API
│   │   ├── AudioRecorderHelper.kt       # Ghi âm nén M4A/AAC 16kHz
│   │   ├── GeminiClient.kt              # Kết nối REST API Gemini 3.5 Flash
│   │   ├── PhoneCommunicator.kt         # Gửi payload sang điện thoại qua GMS Wearable
│   │   ├── SwipeAccessibilityService.kt # Chặn cử chỉ mép trái trên Home
│   │   └── WatchUpdateReceiverService.kt# Nhận stream APK từ điện thoại và cài đặt OTA
│   └── src/main/res/                    # Giao diện Skeuomorphism kim loại
└── phone/                               # Module ứng dụng chạy trên Điện thoại Android
    ├── src/main/java/com/oppowatch/gemini/phone/
    │   ├── PhoneMainActivity.kt         # Giao diện lọc Checkbox & Nút Cập nhật GitHub
    │   ├── BluetoothFilterManager.kt    # Quản lý danh sách MAC address được tick
    │   ├── TtsSpeaker.kt                # Khởi tạo và phát Google TTS tiếng Việt
    │   ├── PhoneWearableListenerService.kt # Lắng nghe tin nhắn từ đồng hồ
    │   ├── GitHubUpdateManager.kt       # Kiểm tra và tải bản cập nhật từ GitHub API
    │   └── WatchApkPusher.kt            # Đẩy APK đồng hồ qua Bluetooth ChannelClient
    └── src/main/res/
```

---

## 🤖 CI/CD GitHub Actions (Tự động build bản phát hành)

Quy trình tự động hóa đã được thiết lập trong `.github/workflows/build_and_release.yml`:
1. Mỗi khi bạn tạo một tag mới trên GitHub (ví dụ: `v1.0.1`, `v1.1.0`):
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
2. GitHub Actions sẽ tự động:
   - Dựng môi trường máy chủ Linux với JDK 21.
   - Biên dịch cả 2 module: `./gradlew assembleRelease`.
   - Ký số APK bằng keystore của dự án.
   - Tự động tạo một bản **GitHub Release** và đính kèm:
     - `Gemini_Watch_App.apk`
     - `Gemini_Phone_Companion.apk`
3. Khi người dùng mở app Companion trên điện thoại và bấm **"KIỂM TRA & CẬP NHẬT"**, app sẽ phát hiện ngay bản Release mới này và tiến hành tải về!

---

## 🚀 Hướng dẫn Cài đặt Lần đầu

### 1. Biên dịch APK (Gradle)
```bash
./gradlew assembleRelease
```
- File APK cho đồng hồ: `watch/build/outputs/apk/release/watch-release.apk`
- File APK cho điện thoại: `phone/build/outputs/apk/release/phone-release.apk`

### 2. Cài đặt lên Đồng hồ qua ADB (Chỉ cần làm 1 lần đầu)
```bash
adb -s 22ff0e24 install -r Gemini_Watch_App.apk

# Cấp quyền Micro và dịch vụ tiếp cận:
adb -s 22ff0e24 shell pm grant com.oppowatch.gemini android.permission.RECORD_AUDIO
adb -s 22ff0e24 shell settings put secure enabled_accessibility_services com.oppowatch.gemini/.SwipeAccessibilityService
adb -s 22ff0e24 shell settings put secure accessibility_enabled 1
```
*(Hoặc chạy trực tiếp script `CAI_DAT_GEMINI_WATCH.bat`)*

### 3. Cài đặt lên Điện thoại
1. Chuyển file `Gemini_Phone_Companion.apk` sang điện thoại và cài đặt.
2. Mở app **Gemini Voice Companion** trên điện thoại:
   - Cấp quyền Bluetooth.
   - Tick chọn tai nghe Bluetooth hoặc nón bảo hiểm bạn muốn nghe giọng nói.
   - Bấm nút **"THỬ PHÁT GIỌNG NÓI"** để kiểm tra âm thanh TTS tiếng Việt.
   - Các bản nâng cấp sau này, bạn chỉ cần bấm **"KIỂM TRA & CẬP NHẬT"** trong app là cả điện thoại và đồng hồ đều tự cập nhật qua mạng!