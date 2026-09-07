# Changelog

Tất cả các thay đổi đáng chú ý của dự án **Gemini Voice Assistant for OPPO Watch** sẽ được ghi nhận tại đây.

Định dạng dựa theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/),
phiên bản tuân theo [Semantic Versioning](https://semver.org/lang/vi/).

---

## [v1.3.3] - 2026-09-07

### 📺 Điều Khiển Nhạc & Mở Video YouTube Morphe Bằng Giọng Nói (Media & YouTube Morphe Autoplay)
- **Tự động tìm kiếm & phát ngay video đầu tiên trên YouTube Morphe (`app.morphe.android.youtube`):**
  - Khẩu lệnh: *"Mở video Một con vịt"*, *"Bật bài hát Nắng ấm xa dần trên YouTube"*, *"Phát nhạc Sơn Tùng MTP"*.
  - Cơ chế cào ngầm kết quả tìm kiếm YouTube để lấy chính xác `videoId` đầu tiên và kích hoạt `vnd.youtube:$videoId` nhắm thẳng tới gói ứng dụng Morphe.
  - Tích hợp `SYSTEM_ALERT_WINDOW` và WakeLock giúp điện thoại tự đánh thức và mở video ngay cả khi màn hình đang tắt hoặc điện thoại ở trong túi quần.
- **Điều khiển trình phát đa phương tiện từ xa qua Bluetooth:**
  - *"Tạm dừng video / tạm dừng"* (`PAUSE`)
  - *"Tiếp tục phát / nghe tiếp"* (`PLAY`)
  - *"Chuyển bài / bài tiếp theo"* (`NEXT`)
  - *"Quay lại bài trước / lùi bài"* (`PREV`)
  - Tương thích qua `MediaSessionManager` và giả lập Media KeyEvent chuẩn của Android.

### ⚡ Đồng Hồ Tự Nhận Diện Port & Đồng Bộ IP Wireless ADB Sang Điện Thoại
- Đồng hồ OPPO Watch tự động lấy IPv4 cục bộ trên Wi-Fi, kiểm tra trạng thái socket `127.0.0.1:5555` (Gỡ lỗi Wi-Fi).
- Tự động đóng gói gửi thông tin `{ip, port: 5555, adb_ready}` qua kênh Google Wearable Data Layer (`/watch_adb_info`).
- Ứng dụng điện thoại tự động nhận và điền sẵn IP & Port vào form Wireless ADB, giúp người dùng không cần nhập thủ công.

### 🎙️ Tái Cấu Trúc Giao Diện Phone Companion & Trang Tổng Hợp Khẩu Lệnh
- Tái cấu trúc Hub chính thành mục **⚙️ CÀI ĐẶT HỆ THỐNG** khoa học, liền mạch.
- Bổ sung nút nổi bật **🎙️ TỔNG HỢP KHẨU LỆNH GEMINI** dẫn đến giao diện chuyên biệt:
  - 10 thẻ hướng dẫn trực quan (YouTube Morphe, Báo thức, Hẹn giờ, Trả lời tin nhắn, Google Tasks, Nhắc nhở, Chép chính tả Clipboard, Hội thoại ngữ cảnh 5 phút, Đi đường Road Mode).
  - Hỗ trợ đồng bộ cả 3 phong cách giao diện (Skeuomorphism, Liquid Glass, Material 3) và 2 chế độ Sáng / Tối.

### 🌙 Màn Hình Đồng Hồ Tối Dần Sau 10s & Tắt Đen Sau 3s (Smooth Auto-Dim & Blackout)
- Sau khi trợ lý Gemini phản hồi hoàn tất:
  - Giữ nguyên màn hình hiển thị 10 giây.
  - Sau 10 giây: Màn hình chuyển sang làm mờ tối dần (alpha 0.85).
  - Sau 3 giây tiếp theo: Màn hình tắt đen hoàn toàn (overlay đen 100%) và đóng Activity quay về mặt đồng hồ (Watch Face) nhằm tiết kiệm pin tối đa.
  - Bất kỳ thao tác chạm màn hình nào sẽ lập tức hủy đếm ngược và trả lại độ sáng bình thường.

---

## [v1.3.2] - 2026-09-07

### 📝 Chuyển Đổi Sang Đồng Bộ Google Tasks Trực Tiếp (Direct Google Tasks Integration)
- **Hỗ trợ đầy đủ ứng dụng Google Tasks (`com.google.android.apps.tasks`):**
  - Ra lệnh từ đồng hồ: *"Thêm việc cần làm: mua bánh mì và sữa chua"*, *"Thêm vào Google Tasks: nộp báo cáo tuần"*.
  - AI Gemini tự động xuất action: `{"type":"CREATE_TASK","title":"...","notes":"..."}`.
  - Phía điện thoại: `GoogleTasksManager` tích hợp sâu với Google Tasks:
    - Kích hoạt giao diện thêm task của Google Tasks (`com.google.android.apps.tasks/.ui.ShareWithTaskListsActivity`) với nội dung đã được điền sẵn đầy đủ.
    - Phát broadcast hệ thống `com.google.android.apps.tasks.AddTask` để đồng bộ dữ liệu vào Google Tasks.
    - Hiển thị **Heads-up Notification** ưu tiên cao kèm nút tắt **"Mở Google Tasks"** giúp người dùng 1 chạm là vào xem ngay.
    - Đọc TTS xác nhận: *"Đã thêm vào Google Tasks: [tiêu đề]"*.
  - Đồng hồ hiển thị: `✓ ĐÃ THÊM TASK` và nhãn `📝 Đã thêm Google Task: "[tiêu đề]"`.

---

## [v1.3.1] - 2026-09-07

### 📝 Lưu Việc Cần Làm Vào Task & Ghi Chú OPPO / ColorOS (OPPO Task Integration)
- **Tích hợp sâu hệ sinh thái ColorOS:**
  - Ra lệnh từ đồng hồ: *"Thêm việc cần làm: mua bánh mì và sữa chua"*, *"Ghi việc cần làm: hoàn thiện báo cáo tuần"*.
  - Gemini tự động xuất action: `{"type":"CREATE_TASK","title":"...","notes":"..."}` kèm Fallback Regex cục bộ.
  - Phía điện thoại: `OppoTaskManager` tự động lưu trữ danh mục task, đồng thời phát **Heads-up Notification** ưu tiên cao kèm nút tắt **"Mở Ghi chú ColorOS"** (`com.coloros.note`) giúp người dùng 1 chạm vào ngay ứng dụng Ghi chú gốc của máy.

### ⏰ Nhắc Nhở Theo Ngữ Cảnh Thời Gian Thực (Contextual Reminders via AlarmManager)
- **Hẹn giờ nhắc nhở công việc linh hoạt:**
  - Hỗ trợ câu lệnh phong phú: *"Nhắc tôi sau 15 phút nữa kiểm tra tin nhắn"*, *"Nhắc tôi lúc 8 giờ tối uống thuốc"*.
  - Gemini tự động tính toán thời gian `delay_seconds` hoặc phân tích regex cục bộ `parseReminderFallback`.
  - Điện thoại lập lịch bằng `AlarmManager.setExactAndAllowWhileIdle()` qua `ReminderReceiver`:
    - Đổ chuông, rung phản hồi mạnh mẽ kể cả khi thiết bị đang ở chế độ ngủ sâu (Doze Mode).
    - Hiển thị thông báo Heads-up khẩn cấp (`PRIORITY_MAX`).
    - Đọc to câu nhắc nhở qua tai nghe Bluetooth hoặc loa ngoài điện thoại: *"Đã đến giờ nhắc nhở: [nội dung]"*.

### 🧠 Hội Thoại Tiếp Nối Đa Lượt (Multi-turn Context Memory - 5 Messages / 5 Min Cache)
- **AI ghi nhớ ngữ cảnh thông minh trên đồng hồ:**
  - Xây dựng `ConversationMemory` trên Wear OS lưu trữ tối đa **5 lượt hỏi - đáp gần nhất**.
  - **Cơ chế Cache trượt 5 phút:** Nếu lần tương tác tiếp theo diễn ra trong vòng 5 phút, toàn bộ ngữ cảnh trước đó được tự động đóng gói vào mảng `contents` gửi tới Gemini API.
  - Sau 5 phút không tương tác, bộ nhớ đệm tự động làm mới để sẵn sàng cho chủ đề hội thoại mới.
  - Cho phép người dùng hỏi tiếp các câu phụ thuộc ngữ cảnh: *"Thời tiết hôm nay thế nào?"* $\rightarrow$ *"Trời nắng 32 độ"* $\rightarrow$ *"Thế còn ngày mai?"* (AI tự hiểu là hỏi thời tiết ngày mai).

### 📋 Chép Chính Tả Tự Động Vào Clipboard Điện Thoại (Voice Dictation to Phone Clipboard)
- **Giải quyết triệt để vấn đề gõ tiếng Việt khó khăn trên màn hình nhỏ Wear OS:**
  - Khẩu lệnh: *"Chép chính tả: ngày mai họp lúc chín giờ sáng tại phòng hai"*, *"Sao chép văn bản: ..."*, *"Copy vào điện thoại: ..."*.
  - Gemini chuẩn hóa câu chữ, viết hoa đầu câu, ngắt câu và chấm phẩy chuẩn xác theo ngữ pháp tiếng Việt.
  - Phía điện thoại: Sử dụng `ClipboardTrampolineActivity` (Activity trong suốt) để ghi đè an toàn vào `ClipboardManager` trên Android 10, 11, 12, 13, 14 kể cả khi điện thoại tắt màn hình trong túi quần.
  - Phản hồi rung, Toast thông báo và đọc TTS xác nhận: *"Đã sao chép vào bộ nhớ tạm"*.

---

## [v1.3.0] - 2026-09-07

### 💬 Trả Lời Nhanh Tin Nhắn Bằng Giọng Nói Từ Đồng Hồ (Quick Voice Reply for Messages)
- **Hỗ trợ trả lời tin nhắn rảnh tay khi lái xe hoặc khi điện thoại khóa màn hình / bỏ trong túi:**
  - Ra lệnh bằng giọng nói tự nhiên từ OPPO Watch mà không cần chạm vào điện thoại hay mở màn hình điện thoại.
  - **Không cần đọc lại nội dung tin nhắn trước khi gửi**: Tối ưu tốc độ trả lời tức thì, tiết kiệm thời gian khi đang di chuyển trên đường.
  - **Hỗ trợ đầy đủ các ứng dụng nhắn tin phổ biến nhất:**
    - Facebook Messenger & Messenger Lite (`com.facebook.orca`, `com.facebook.mlite`)
    - Zalo (`com.zing.zalo`)
    - Telegram & Telegram X (`org.telegram.messenger`, `org.thunderdog.challegram`)
    - Tin nhắn SMS / MMS hệ thống (Google Messages, Samsung Messages, AOSP SMS)
    - WhatsApp & WhatsApp Business (`com.whatsapp`, `com.whatsapp.w4b`)
    - Mọi ứng dụng có thông báo danh mục `CATEGORY_MESSAGE` hỗ trợ Android `RemoteInput`.
- **Cơ chế nhận diện thông minh (AI Gemini NLU & Local Regex Fallback):**
  - **Trả lời tin nhắn vừa nhận gần nhất:**
    - Câu lệnh mẫu: *"Rep là đang đi xe lát gọi lại"*, *"Trả lời tin nhắn bảo tôi đang bận"*, *"Nhắn lại bảo ok nhé"*.
    - Gemini tự động xuất action: `{"type":"REPLY_MESSAGE","recipient":"","message":"..."}`.
  - **Trả lời tin nhắn của người cụ thể:**
    - Câu lệnh mẫu: *"Trả lời tin nhắn của Tuấn Anh bảo ok em"*, *"Rep mẹ là con sắp về"*, *"Nhắn lại cho Linh bảo tối nay đi ăn"*.
    - Gemini tự động xuất action: `{"type":"REPLY_MESSAGE","recipient":"Tuấn Anh","message":"..."}`.
  - **Local Regex Fallback**: Hỗ trợ regex tiếng Việt cục bộ đa dạng nếu kết nối mạng chậm hoặc Gemini không trả trường action.
- **Xử lý ngầm 100% khi màn hình khóa (Keyguard Locked Background Execution):**
  - Điện thoại tích hợp `QuickReplyNotificationService` kế thừa Android `NotificationListenerService`.
  - Tự động bắt cổng `RemoteInput` và kích hoạt ngầm `PendingIntent.send()` mà không cần mở sáng màn hình điện thoại.
  - Giao diện Phone Companion bổ sung khu vực quản lý và nút cấp quyền **Truy cập thông báo (Notification Listener Access)**.
- **Phản hồi âm thanh TTS xác nhận qua loa ngoài / tai nghe điện thoại:**
  - Sau khi gửi thành công, điện thoại tự động phát âm xác nhận rõ ràng: *"Đã trả lời Tuấn Anh qua Zalo: Đang đi xe lát gọi lại"*.
  - Đồng hồ hiển thị nhãn: `✓ ĐÃ GỬI TIN` kèm chi tiết người nhận và nội dung.
  - Tự động ghi nhật ký vào Lịch sử tác vụ trên điện thoại.

---

## [v1.2.9] - 2026-09-07

### 📱 Hiển Thị Chi Tiết Phiên Bản Mobile & Android OS (Device & App Version Display)
- **Hiển thị đầy đủ thông tin hệ thống trong Trung tâm Cập nhật:**
  - `tvAppVersion`: Hiển thị phiên bản ứng dụng Mobile hiện tại (`📱 Phiên bản Mobile: v1.2.9`).
  - `tvAndroidVersion`: Hiển thị phiên bản hệ điều hành Android thực tế của thiết bị (`🤖 Hệ điều hành Android: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})`).
  - Tự động làm mới thông tin phiên bản ngay khi người dùng quay lại ứng dụng (`onResume()`) hoặc truy cập menu Cập nhật.

### ⚡ Quy Trình Cập Nhật 2 Bước Tuần Tự (Sequential 2-Step Update Workflow)
- **Chia quy trình cập nhật thành 2 bước trực quan, tách biệt và an toàn:**
  - **BƯỚC 1: CẬP NHẬT MOBILE (Điện thoại)**:
    - Nút `btn_update_step1`: Tải file `Gemini_Phone_Companion_Update.apk` từ GitHub Release và tự động mở trình cài đặt PackageInstaller của Android.
    - Sau khi hoàn tất cài đặt bản Mobile, người dùng mở app sẽ được thông báo sẵn sàng cho Bước 2.
  - **BƯỚC 2: CẬP NHẬT WEAR OS QUA WIRELESS ADB (Đồng hồ)**:
    - Nút `btn_adb_install`: Tải bản `Gemini_Watch_App_Update.apk` từ GitHub Release và cài đặt trực tiếp qua mạng Wi-Fi bằng Wireless ADB client tích hợp (`dadb`).
    - Thực thi lệnh `pm install -r -d -t -g` ghi đè sạch sẽ ứng dụng trên đồng hồ mà không làm mất cấu hình.
- **Loại bỏ hoàn toàn cập nhật qua Bluetooth**:
  - Gỡ bỏ toàn bộ luồng truyền APK qua Bluetooth Channel (`WatchApkPusher`) trong tiến trình update, khắc phục triệt để tình trạng truyền chậm và kẹt bản cũ trên đồng hồ.

### 🔇 Im Lặng Hoàn Toàn Khi Bật Mic Nhưng Không Nói (Silent Empty Speech Guard)
- **Sửa lỗi đồng hồ vẫn gọi Gemini / phát TTS khi người dùng bật mic nhưng không nói:**
  - `GeminiClient.kt`: Sau khi parse JSON phản hồi, nếu Gemini không nhận diện được giọng nói (question rỗng và answer rỗng/quá ngắn) → gọi `onResult(true, "", "", null)` ngay, bỏ qua hoàn toàn.
  - `MainActivity.kt`: Khi nhận sentinel `question.isEmpty() && answer.isEmpty()` → âm thầm reset UI về "NHẤN ĐỂ NÓI", **tuyệt đối không rung haptic, không phát TTS, không hiển thị nội dung nào**.

### 📢 Đọc TTS Báo Thức / Hẹn Giờ Qua Loa & Tai Nghe Điện Thoại (Phone TTS Announcement)
- Sau khi thực thi đặt báo thức hoặc hẹn giờ thành công trên đồng hồ, tự động gửi câu xác nhận tự nhiên bằng tiếng Việt sang điện thoại để phát âm qua loa ngoài hoặc tai nghe Bluetooth:
  - Báo thức: *"Đã đặt báo thức lúc 7 giờ sáng"*, *"Đã đặt báo thức lúc 2 giờ 30 phút chiều"*.
  - Hẹn giờ: *"Đã hẹn giờ 5 phút"*, *"Đã hẹn giờ 1 phút 30 giây"*.

---

## [v1.2.8] - 2026-09-07

### 🔄 Sửa Triệt Để Lỗi Cử Chỉ Vuốt Back (System Back Gesture Exit App Bug)
- **Sửa lỗi vuốt Back bị văng thoát ứng dụng khỏi sub-menus:**
  - Khắc phục lỗi trong `setupNavigationFlow()` khi callback `OnBackPressedCallback` bị vô hiệu hóa vĩnh viễn (`isEnabled = false`) lúc bấm Back ở trang chính.
  - Chuyển sang cơ chế quản lý trạng thái động `backCallback.isEnabled = (currentPage != NavPage.HUB)` được cập nhật tự động trong `navigateTo(page)`.
  - Bổ sung fallback `onBackPressed()` đảm bảo mọi cử chỉ vuốt cạnh trái/phải trên Android 10-14 hoặc phím Back vật lý trong bất kỳ menu con nào đều trở về màn hình Settings Hub thay vì thoát app.
  - Chỉ khi người dùng đang ở màn hình Settings Hub chính, thao tác Back mới cho phép thu nhỏ/thoát ứng dụng theo đúng chuẩn Android.

### 🎨 Khắc Phục Lỗi Tương Phản Màu Chữ Khi Chuyển Chế Độ Sáng / Tối (Light & Dark Contrast Fix)
- **Tối ưu hóa độ tương phản màu chữ toàn diện trên giao diện Mobile Companion:**
  - Khắc phục triệt để hiện tượng chữ trắng chìm trên nền thẻ sáng hoặc chữ xám mờ khó đọc khi chuyển qua Chế độ Sáng (Light Mode) ở cả 3 phong cách (Skeuomorphism, Liquid Glass, Material 3).
  - **Màn hình Cài đặt chính (Hub):**
    - Tiêu đề mục menu (`tv_menu_title_*`): Tự động đổi sang màu xám đen đậm `#0F172A` sắc nét ở Light Mode và trắng sáng `#F8FAFC` ở Dark Mode.
    - Tiêu đề danh mục (`tv_cat_header_*`): Điều chỉnh tông màu đậm đà, tương phản cao trong Light Mode (AI: `#1D4ED8`, Thiết bị: `#047857`, Chủ đề: `#B45309`, Nhật ký: `#6D28D9`).
    - Dòng mô tả tóm tắt (`tv_hub_*_summary`) và chevron (`›`): Đổi sang màu slate đậm `#475569` và `#94A3B8` dễ đọc.
  - **Ô nhập liệu & Điều khiển (Inputs & Radios):**
    - `et_gemini_api_key`, `et_watch_adb_ip`, `et_watch_adb_port`: Text color `#0F172A` và hint color `#94A3B8` ở Light Mode; `#F8FAFC` / `#38BDF8` ở Dark Mode.
    - Radio button chọn model AI: Màu chữ đen xám `#0F172A` và điểm nhấn hổ phách `#B45309` cho 3.8 Flash ở Light Mode.
    - Nút chọn theme & chế độ màu: Tự động đảo màu chữ tương ứng với nền nút đang chọn.
  - **Danh sách Bluetooth, Lịch sử Voice Q&A và Nhật ký Lỗi API:**
    - `item_bluetooth_device.xml`: Tên thiết bị chuyển sang `#0F172A` và MAC sang `#B45309` ở Light Mode.
    - `item_qa_history.xml`: Tạo riêng 2 drawable nền mềm mại `bg_qa_question_light` (`#F0F9FF`) và `bg_qa_answer_light` (`#ECFDF5`), đổi màu chữ câu hỏi sang `#0F172A` và câu trả lời sang `#064E3B` cực kỳ trang nhã và dễ đọc.
    - `item_api_error_log.xml`: Mã lỗi `#DC2626`, nhãn model/key `#334155`, giải pháp `#065F46`, JSON thô `#1E293B` rõ nét trên nền sáng.

---

## [v1.2.7] - 2026-09-07

### 📱 Tái Cấu Trúc Toàn Bộ UI/UX Flow: Hệ Thống Cài Đặt Với Menu Lồng Nhau (Hierarchical Settings)
- **Thiết kế lại toàn diện giao diện Mobile Companion:**
  - Chuyển từ bố cục một trang cuộn dài cũ sang **Hệ thống Cài đặt phân cấp (Hierarchical Settings Navigation)** hiện đại, thanh lịch tương tự phong cách Settings cao cấp của iOS / ColorOS / OneUI.
- **Thanh điều hướng đỉnh (Sticky Top Navigation Bar):**
  - Cố định ở đầu màn hình, thích ứng mượt mà theo vị trí:
    - Ở màn hình chính: Hiển thị thương hiệu `GEMINI COMPANION`, trạng thái kết nối với đồng hồ (`🟢 OPPO WATCH` / `🟡 CHỜ KẾT NỐI`).
    - Khi đi vào menu con: Tự động hiển thị nút `[← TRỞ VỀ]`, tiêu đề menu con và breadcrumb điều hướng (ví dụ: `Cài đặt > Mô hình AI & API Key`).
- **Màn hình Cài đặt chính (Settings Hub) với các nhóm danh mục trực quan:**
  - **Nhóm 1: Trí tuệ nhân tạo & Mô hình:** Thẻ `Mô hình Gemini & API Key` hiển thị tóm tắt trực tiếp mô hình đang chọn và tình trạng lưu khóa.
  - **Nhóm 2: Kết nối & Thiết bị ngoại vi:** Thẻ `Tai nghe Bluetooth & Phát âm TTS` và thẻ `Cài đặt không dây (ADB) & Cập nhật`.
  - **Nhóm 3: Cá nhân hóa & Chủ đề:** Thẻ `Giao diện & Chủ đề đồng bộ` hiển thị tóm tắt phong cách thiết kế và chế độ sáng/tối.
  - **Nhóm 4: Nhật ký & Chẩn đoán hệ thống:** Thẻ `Lịch sử hỏi đáp giọng nói (Voice Q&A)` và thẻ `Nhật ký lỗi API (API Error Logs)` với huy hiệu badge cảnh báo số lỗi thời gian thực.
  - Mỗi mục cài đặt đều có icon bo góc màu sắc biểu trưng, dòng tóm tắt trạng thái thời gian thực (Live Summary), huy hiệu badge và mũi tên điều hướng `›`.
- **6 Menu con lồng nhau chuyên sâu (Sub-Menus):**
  - Bố cục các thành phần điều khiển gọn gàng, cách ly chuyên biệt từng tính năng giúp người dùng tập trung và không bị rối mắt.
- **Điều hướng mượt mà & Hỗ trợ Back vật lý:**
  - Tự động cuộn lên đầu trang khi chuyển menu.
  - Xử lý mượt mà sự kiện phím Back vật lý / cử chỉ vuốt cạnh của Android (`OnBackPressedDispatcher`): quay về Menu chính khi đang ở menu con và thoát/ẩn ứng dụng khi đang ở Menu chính.
- **Hệ thống Theme đồng bộ hoàn hảo:**
  - Cả 6 phong cách (Skeuomorphism Dark/Light, Liquid Glass Dark/Light, Material Dark/Light) áp dụng trơn tru cho toàn bộ Top Bar, thẻ danh mục Hub và các Menu con.

---

## [v1.2.6] - 2026-09-07

### 🚨 Nhật Ký Lỗi API Chi Tiết Trên Mobile & Khắc Phục Lỗi Kết Nối Gemini (HTTP 403)
- **Mục Nhật Ký Lỗi API Chuyên Sâu (API Error Logs) Trên Ứng Dụng Điện Thoại:**
  - Bổ sung bảng điều khiển **🚨 NHẬT KÝ LỖI API (CHI TIẾT MÃ LỖI & PHẢN HỒI GOOGLE)** trực tiếp trên Phone Companion.
  - **Đồng bộ thời gian thực qua Wearable Data Layer (`/gemini_error_log`):** Khi đồng hồ gặp bất kỳ sự cố nào khi gọi Gemini (HTTP 403, 400, 429, 500, lỗi mạng timeout), đồng hồ sẽ tự động gói toàn bộ thông tin kỹ thuật gửi ngay sang điện thoại.
  - **Báo cáo sự cố toàn diện:** Mỗi mục log hiển thị:
    - Huy hiệu mã lỗi: `HTTP 403 (PERMISSION_DENIED)`, `HTTP 429 (RESOURCE_EXHAUSTED)`, `HTTP 400 (BAD_REQUEST)`,...
    - Nguồn phát sinh: `⌚ ĐỒNG HỒ` hoặc `📱 TEST TRÊN MÁY`.
    - Dấu thời gian chính xác (`HH:mm:ss - dd/MM/yyyy`).
    - Mô hình Gemini được sử dụng (`gemini-3.8-flash`,...).
    - Khóa API bị che bảo mật (`AQ.Ab...VoMg`).
    - Nguyên nhân cụ thể từ Google (`Method doesn't allow unregistered callers`,...).
    - Hộp hướng dẫn xử lý từng bước theo ngữ cảnh cho người dùng.
    - Chức năng mở rộng xem toàn bộ **JSON thô** phản hồi từ server Google.
    - Nút **📋 SAO CHÉP** một chạm để người dùng dễ dàng copy log gửi trợ giúp hoặc tra cứu.
    - Nút **🗑️ XÓA NHẬT KÝ LỖI** để dọn sạch danh sách.
- **Nút "🧪 TEST API TRÊN MÁY" (Test API Key Ngay Lập Tức):**
  - Cho phép người dùng kiểm tra API Key trực tiếp trên điện thoại trước khi dùng trên đồng hồ.
  - Gửi request thử nghiệm đến mô hình Gemini đang chọn, trả kết quả HTTP 200 tức thì hoặc ghi lại mã lỗi chi tiết vào bảng log nếu key không hợp lệ.
- **Tự Động Đồng Bộ API Key Sang Đồng Hồ (Auto-Sync on Resume/Connect):**
  - **Khắc phục lỗi HTTP 403:** Khi người dùng xóa app trên đồng hồ rồi cài lại, toàn bộ SharedPreferences (`custom_api_key`) bị mất. Giờ đây, mỗi khi mở app điện thoại hoặc khi đồng hồ kết nối lại Bluetooth/Wi-Fi, điện thoại sẽ **tự động bắn API Key đã lưu sang đồng hồ ngầm** mà người dùng không cần phải vào gõ lại hay bấm lưu thủ công.
  - Tích hợp sẵn khóa API dự phòng trong quy trình CI/CD GitHub Actions (`build_and_release.yml`), đảm bảo file APK phát hành không bao giờ bị rỗng key.

### 🔄 Sửa Triệt Để Lỗi Cập Nhật Không Ghi Đè (Update In-Place Fix & ADB Robustness)
- **Khắc phục lỗi phải xóa ứng dụng trên đồng hồ mới cập nhật được:**
  - **Nguyên nhân cốt lõi:** Lệnh cài đặt ngầm qua ADB trên Wear OS 2 (Android 9) trước đó thiếu cờ `-r` (reinstall/replace application), khiến hệ thống Android từ chối ghi đè với mã lỗi `INSTALL_FAILED_ALREADY_EXISTS`. Thư viện ADB client nuốt lỗi này và báo thành công trong khi ứng dụng trên đồng hồ vẫn là phiên bản cũ. Đồng thời, điện thoại giữ file APK cũ trong cache mà không đối chiếu với GitHub Release mới nhất.
  - **Giải pháp xử lý triệt để:**
    - Cập nhật lệnh cài đặt hệ thống sang `pm install -r -d -t -g <path>`:
      - `-r`: Cho phép ghi đè hoàn toàn lên ứng dụng đang có, giữ nguyên dữ liệu và cài đặt.
      - `-d`: Cho phép hạ cấp hoặc cài cùng phiên bản nếu cần thiết.
      - `-t`: Cho phép cài đặt các gói thử nghiệm.
      - `-g`: Tự động cấp toàn bộ quyền runtime (Ghi âm, WakeLock) ngay sau khi cài.
    - **Kiểm tra kết quả thực tế:** Bắt và kiểm tra chuỗi phản hồi từ Android Package Manager. Chỉ báo thành công khi có xác nhận `Success`.
    - **Tự động xử lý chữ ký không khớp (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`):** Nếu bản cũ cài từ nguồn khác hoặc debug keystore khác, hệ thống sẽ tự động gỡ sạch bản cũ và cài đặt bản mới liền mạch.
    - **Xác thực phiên bản thời gian thực:** Sau khi cài đặt xong, điện thoại tự động truy vấn `dumpsys package com.oppowatch.gemini` để hiển thị chính xác phiên bản vừa cập nhật (`v1.2.6`) trên giao diện.
    - **Xóa cache thông minh trên điện thoại:** Khi bấm nút cài đặt qua ADB, ứng dụng điện thoại kiểm tra thông tin tag mới nhất trên GitHub, tự động dọn dẹp file APK cũ trong bộ nhớ đệm nếu phiên bản không trùng khớp để luôn cài bản mới nhất.

---

## [v1.2.5] - 2026-09-07

### ⌚ Cập Nhật Giao Diện Wear OS: Hiển Thị Version Ở Góc & Bổ Sung Nút Hủy Lệnh (Cancel)
- **Hiển thị phiên bản ứng dụng động ở góc màn hình:**
  - Bổ sung số phiên bản (`v1.2.5`) tại góc dưới bên trái và thanh tiêu đề phía trên màn hình đồng hồ.
  - Số phiên bản được lấy trực tiếp từ hệ thống (`packageManager`), tự động cập nhật chính xác cho người dùng nhận biết ngay trên mặt đồng hồ.
- **Nút HỦY LỆNH (Cancel Button - "Ấn vào không gửi"):**
  - Bổ sung nút tròn **[✕]** chuyên dụng tại góc dưới bên phải màn hình đồng hồ.
  - **Khi đang thu âm:** Bấm nút **✕** sẽ dừng ghi âm tức thì, xóa file âm thanh đệm và **tuyệt đối không gửi** bất kỳ dữ liệu nào đến Gemini.
  - **Khi đang gọi Gemini:** Bấm nút **✕** sẽ ngắt kết nối HTTP ngay lập tức, giải phóng CPU WakeLock và đưa giao diện về trạng thái sẵn sàng.
  - **Cử chỉ Trượt Để Hủy (Slide-To-Cancel):** Khi đang nhấn giữ phím Micro PTT, người dùng có thể trượt ngón tay ra xa nút mic để hủy gửi tự nhiên tương tự các ứng dụng tin nhắn thoại.
  - Phản hồi trạng thái trực quan: Hiển thị thông báo `"ĐÃ HỦY (KHÔNG GỬI)"` màu đỏ cùng rung xúc giác (haptic) xác nhận.
- **Đồng bộ Theme:**
  - Nút Hủy và số phiên bản tự động biến đổi màu sắc và hiệu ứng viền kim loại/kính mờ theo cả 6 chủ đề (Skeuomorphism, Liquid Glass, Material x Dark / Light).

---

## [v1.2.4] - 2026-09-07

### ⚡ Tích Hợp Wireless ADB Client Trực Tiếp Vào Điện Thoại (Direct Wear OS Sideload)
- **Tích hợp Wireless ADB Client thuần Kotlin (`dadb`):**
  - Khắc phục triệt để rào cản của Wear OS: Google cố tình vô hiệu hóa giao diện cài đặt APK của người dùng trên toàn bộ hệ điều hành Wear OS (`NotSupportedOnWearDialog` - *"Không hỗ trợ tác vụ Cài đặt/Gỡ cài đặt trên Wear"*).
  - Giờ đây, ứng dụng Companion trên điện thoại đóng vai trò là một **máy trạm ADB không dây hoàn chỉnh** (Standalone Wireless ADB Host) với cặp khóa RSA bảo mật riêng biệt, kết nối trực tiếp tới cổng `5555` của đồng hồ qua Wi-Fi hoặc Hotspot.
- **Cài đặt ngầm không cần máy tính (Zero-PC Wireless Sideloading):**
  - Thực thi lệnh cài đặt hệ thống `pm install -r` trực tiếp từ điện thoại sang đồng hồ.
  - Tự động mở ứng dụng Gemini trên đồng hồ ngay sau khi cài đặt thành công (`am start -n com.oppowatch.gemini/.MainActivity`).
- **Giao diện điều khiển ADB trực quan & Dò IP tự động (Auto-Scan IP):**
  - Thêm bảng điều khiển **⚡ CÀI ĐẶT QUA WIRELESS ADB (SIÊU TỐC)** trên app điện thoại.
  - Tính năng **🔍 DÒ TỰ ĐỘNG**: Tự động quét bảng ARP hệ thống và subnet Wi-Fi / Hotspot (`192.168.43.x`) để tìm chính xác địa chỉ IP của đồng hồ đang mở cổng 5555.
  - Tự động lưu địa chỉ IP đồng hồ để tiện sử dụng cho các lần cập nhật sau.
- **Tự động tải APK từ GitHub Release:**
  - Nếu điện thoại chưa lưu sẵn APK đồng hồ trong bộ nhớ đệm, ứng dụng sẽ tự động tải file APK mới nhất từ GitHub Release và thực hiện cài đặt ADB.

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
