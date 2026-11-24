# A17 Assist (`com.a17.assist.ffvn`)

Ứng dụng Android native viết bằng Kotlin + XML layout hỗ trợ điều chỉnh thao tác vuốt cảm ứng và thông số độ nhạy cho Free Fire (`com.garena.ffvn` / `com.dts.freefireth`) **không cần root**.

## Kiến trúc & Nguyên tắc An toàn
- **Không root, không đọc/ghi bộ nhớ game, không hook, không mod APK, không MediaProjection, không mạng.**
- **Inject chạm chuẩn hệ thống:** Ưu tiên **Shizuku UserService** (`InputManager.injectInputEvent` qua `IA17Input.aidl`, tự hạ về **AccessibilityService** `dispatchGesture` sau 3 lỗi liên tiếp và đổi bubble sang màu vàng).
- **Cửa sổ Overlay an toàn:** Toàn bộ cửa sổ dùng `TYPE_APPLICATION_OVERLAY` với `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS`. Vùng HUD dùng `FLAG_NOT_TOUCHABLE` xuyên chạm hoàn toàn.
- **Tự ngủ khi thoát game:** `GameWatcher` theo dõi foreground (`UsageStatsManager`), thoát Free Fire 3 giây tự tắt inject và ngủ engine.
- **Safety Caps luôn thắng:** Mọi thông số slider hoặc JSON import đều bị `SafetyGuard` giới hạn lại ở bước cuối.

> **Lưu ý:** Không có aimlock nào chống ban tuyệt đối. Ứng dụng này tránh mọi kỹ thuật bị Garena quét.

## Hướng dẫn Build APK

### 1. Build qua GitHub Actions
Push mã nguồn lên GitHub (nhánh `main` hoặc `master`), workflow `.github/workflows/build.yml` sẽ tự động:
1. Cài JDK 17 (Temurin) và Android SDK (`platforms;android-34`, `build-tools;34.0.0`, `platforms;android-36`).
2. Chạy `assembleDebug`.
3. Tải lên artifact **`a17-assist-debug`** tại tab **Actions** của repository.

### 2. Build trực tiếp trên máy
```bash
gradle assembleDebug
```
File APK xuất ra tại: `app/build/outputs/apk/debug/app-debug.apk`.

## Hướng dẫn Cài đặt & Sử dụng
1. Cài file `app-debug.apk` lên điện thoại Android 8.0+ (API 26+).
2. Mở **A17 Assist** → vào tab **Thiết lập** (3 bước):
   - **Bước 1:** Cấp quyền Hiển thị trên ứng dụng khác (`SYSTEM_ALERT_WINDOW`) và Thông báo.
   - **Bước 2:** Cấp quyền Truy cập sử dụng (`PACKAGE_USAGE_STATS`) để `GameWatcher` nhận diện Free Fire.
   - **Bước 3:** Cấp quyền **Shizuku** (bubble **Xanh**) hoặc bật **Dịch vụ Trợ năng** làm dự phòng (bubble **Vàng**).
3. Quay lại **Trang chủ**, bấm **Vào Free Fire**:
   - Dịch vụ Overlay khởi động và mở `com.garena.ffvn` (hoặc `com.dts.freefireth`, nếu chưa cài sẽ mở Play Store).
   - Chạm bong bóng nổi 48dp để mở bảng chỉnh trượt từ phải (300dp, tự lưu vào DataStore). Chạm ra ngoài để đóng và tiếp tục chơi; bấm **Tắt khẩn cấp** để ngắt toàn bộ inject ngay lập tức.
