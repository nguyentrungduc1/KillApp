# KillApp ⚡

Ứng dụng Android dọn dẹp bộ nhớ thông minh — Kill app & xóa cache bằng một nút bấm.

## Tính năng

- **Kill tất cả app** (user + system) bằng Accessibility Service
- **Xóa cache** cho tất cả ứng dụng tự động
- **Clear All Recent Apps** tự động sau khi kill
- **Nút nổi (Floating Bubble)** — bấm kill từ bất kỳ màn hình nào
- **Danh sách loại trừ** — thêm/bớt app để tránh kill (vẫn xóa cache)
- **Foreground Service** — chạy ổn định nền

## Cách hoạt động

```
[Bấm KILL & CLEAN ALL]
        ↓
[KillService] → Enqueue tất cả app vào queue
        ↓
[KillAccessibilityService]
  → Mở Settings > App Info (từng app)
  → Tự bấm "Force Stop" → Confirm OK
  → Tự bấm "Clear Cache"
  → Lặp lại cho app tiếp theo
        ↓
[Sau khi xong hết]
  → Mở Recent Apps
  → Tự bấm "Clear All"
  → Về Home
```

## Permissions cần thiết

| Permission | Mục đích |
|---|---|
| `QUERY_ALL_PACKAGES` | Liệt kê tất cả ứng dụng |
| `SYSTEM_ALERT_WINDOW` | Hiển thị nút nổi overlay |
| `FOREGROUND_SERVICE` | Service chạy nền |
| `RECEIVE_BOOT_COMPLETED` | Khởi động lại overlay sau reboot |
| Accessibility Service | Tự động bấm nút trong Settings |

## Build

### GitHub Actions (khuyên dùng)
Push code lên GitHub → Actions tự động build APK → Download từ tab **Artifacts**

### Build thủ công
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Cài đặt

1. Cài APK
2. Bật **Accessibility Service**: Settings → Accessibility → KillApp Cleaner Service → Bật
3. Bật **Overlay Permission**: Settings → Apps → KillApp → Display over other apps → Bật
4. (Tuỳ chọn) Bật nút nổi từ icon 🧭 góc trên phải

## Danh sách loại trừ mặc định

Các app sau KHÔNG bao giờ bị kill (nhưng vẫn xóa cache):
- `android`, `com.android.systemui`
- Launcher, Phone, Settings
- Google Play Services, GMS
- KillApp chính nó

## Lưu ý

- Một số thiết bị MIUI/EMUI/OneUI có tên nút khác — Accessibility Service tìm theo nhiều ngôn ngữ
- App hệ thống quan trọng nên thêm vào danh sách loại trừ trước khi dùng
- Cần Android 8.0+ (API 26)
