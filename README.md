# CleanBrowsing DNS Blocker & Automation Enforcer

Native Android automation tool to enforce CleanBrowsing Family Filter across system network settings and mobile browsers using an Accessibility Service bot.

## 🎯 Enforced Target Configurations
- **System Private DNS (DoT):** `family-filter-dns.cleanbrowsing.org`
- **Wi-Fi DNS 1 (IPv4):** `185.228.168.168`
- **Wi-Fi DNS 2 (IPv4):** `185.228.169.168`
- **Google Chrome Secure DNS (DoH):** `https://doh.cleanbrowsing.org/doh/family-filter/`
- **Brave Browser Secure DNS (DoH):** `https://doh.cleanbrowsing.org/doh/family-filter/`

---

## 🏗 Architecture
- **`DnsAutomationService`**: Android `AccessibilityService` monitoring UI window changes and content events.
- **`NodeHelper`**: Traversal engine finding UI nodes by multiple query keys (bilingual Arabic & English, resource IDs), performing synthetic clicks and typing.
- **`AutomationStateMachine`**: 4-phase sequential workflow controller:
  1. `PrivateDnsStep`: Navigates to system Private DNS, selects hostname provider, enters CleanBrowsing host, saves.
  2. `WifiDnsStep`: Opens current Wi-Fi SSID details, switches IP configuration to Static, writes primary & secondary DNS, saves.
  3. `ChromeDnsStep`: Opens Chrome -> Settings -> Privacy and security -> Use secure DNS -> enters CleanBrowsing DoH URL.
  4. `BraveDnsStep`: Opens Brave -> Settings -> Brave Shields & privacy -> Use secure DNS -> enters CleanBrowsing DoH URL.
- **`MainActivity`**: Real-time status dashboard displaying service health, individual step progress chips, and console log output.

---

## 🚀 Building & Running

### Option 1: Android Studio
1. Open this directory (`g:\تقي\blocker`) in Android Studio.
2. Let Gradle sync dependencies.
3. Connect your Android device (Android 9.0+ / API 28+).
4. Run the app (`app`).

### Option 2: ADB Fast Permission Grant (Optional)
Grant the accessibility service directly via ADB without manual prompt:
```bash
adb shell settings put secure enabled_accessibility_services com.blocker.dns/com.blocker.dns.service.DnsAutomationService
adb shell settings put secure accessibility_enabled 1
```
