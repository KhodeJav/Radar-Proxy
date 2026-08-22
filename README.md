# 🚀 Radar Proxy

⚡ Native Android Telegram Proxy Manager
> Discover • Collect • Test • Connect

---
📘[EN](#-En-english) | [FA](#-فارسی)📕
---

## EN English

💚 **Donate With Crypto:** [Payments.tipora.ir](https://payments.tipora.ir)

Radar Proxy is an open-source native Android application for **collecting, retaining, organizing, testing, and opening Telegram MTProto and SOCKS5 proxies**.

Built with modern Android technologies including **Kotlin, Jetpack Compose, Room, DataStore, WorkManager, OkHttp**, and official Telegram deep links.

### 🔗 Subscription

The official raw subscription is available here:

**[Open Subscription](https://raw.githubusercontent.com/khodejav/telegram-proxy-collector/main/output/proxies.txt)**

```text
https://raw.githubusercontent.com/khodejav/telegram-proxy-collector/main/output/proxies.txt
```

> Add the URL above to Radar Proxy or any compatible subscription client.

---

## ✨ Highlights

* 🔄 Automatic subscription updates
* 📦 Persistent proxy collection
* 🧹 Optional automatic cleanup
* 🛡️ Strong duplicate prevention
* 🔎 Search and sorting
* ⚡ Real protocol testing
* 📡 Wi-Fi & mobile-data support
* 🔗 One-tap Telegram connection
* 🌙 Modern Android UI
* 🌐 Persian & English
* 🎨 Theme support
* 📱 Android 8.0+ support

---

## 📡 Supported Proxy Types

Radar Proxy supports:

* **MTProto**
* **SOCKS5**

Supported Telegram proxy links include:

```text
tg://proxy
tg://socks

https://t.me/proxy
https://t.me/socks

https://telegram.me/proxy
https://telegram.me/socks

t.me/...
telegram.me/...
```

The parser normalizes supported links, decodes parameters, validates ports, and handles supported MTProto secret formats.

---

## 🔄 Subscription System

The subscription system is designed to be **append-only**.

When a subscription is updated:

```text
Fetch
  ↓
Parse
  ↓
Validate
  ↓
Normalize
  ↓
Merge
  ↓
Deduplicate
  ↓
Store
```

New proxies are added to the local collection while existing proxies remain available.

Removing a source does not automatically delete previously collected proxies.

Only the optional **age-based cleanup system** can remove old entries.

---

## 🧹 Automatic Cleanup

Cleanup is completely optional.

Available retention periods:

```text
4H
8H
12H
24H
```

When enabled, Radar Proxy removes entries whose `lastSeen` exceeds the selected retention period.

When disabled:

> No automatic proxy deletion occurs.

The cleanup worker is managed by **WorkManager**, allowing scheduled work to continue while the application is closed and after Android restores scheduled tasks.

---

## 🔄 Subscription Update Feedback

The **Sources** screen provides a manual:

> **Update Subscription**

button.

The application reports the result directly:

```text
✅ Successful update
🟡 Partial success
❌ Complete failure
```

Successful results are merged into the existing collection.

If all enabled sources fail, the current cache is preserved instead of being cleared.

---

## 🧪 Real Protocol Testing

Radar Proxy performs **real network-level proxy tests** from the device's current Wi-Fi or mobile-data connection.

### MTProto

Testing performs the required MTProxy communication flow, including:

```text
Connection
    ↓
Obfuscated Initialization
    ↓
AES-256-CTR Setup
    ↓
req_pq
    ↓
Telegram Response
    ↓
Constructor Validation
```

### SOCKS5

Testing performs:

```text
SOCKS5 Greeting
    ↓
Authentication (if required)
    ↓
CONNECT
    ↓
Tunnel Validation
```

> ICMP ping is not used.

Each proxy receives an independent socket and its own timeout, so one failed proxy does not stop the remaining tests.

### Test Results

```text
🟢 Success
🔴 Failed
⏱️ Timeout
```

Successful results remain valid regardless of latency.

---

## 🎨 UI & Experience

Radar Proxy is designed around a clean, modern Android interface inspired by a radar-style monitoring experience.

### Main Experience

* 📊 Proxy overview
* 🔍 Search
* ↕️ Sorting
* 🗂️ Protocol categories
* 🧩 Proxy cards
* 🧪 Individual testing
* ⚡ Test All
* 🔗 Telegram connect actions
* ℹ️ About section
* 🌐 Persian / English
* 🌙 Theme support

The summary area displays the retained proxy count together with the latest update time using the device's local clock and Persian calendar.

---

## 🏗️ Tech Stack

| Technology          | Purpose          |
| ------------------- | ---------------- |
| Kotlin              | Main language    |
| Jetpack Compose     | UI               |
| Room                | Local database   |
| DataStore           | Preferences      |
| WorkManager         | Background tasks |
| OkHttp              | Networking       |
| Telegram Deep Links | Proxy connection |

---

## 📦 Build & Distribution

Radar Proxy targets:

```text
Target SDK : API 36
Minimum   : API 26
```

Supported architectures:

```text
armeabi-v7a
arm64-v8a
Universal
```

Build commands:

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew assembleRelease
./gradlew bundleRelease
```

### Release

For direct distribution:

> Use the signed **Universal Release APK**.

For Google Play:

> Upload the signed **Android App Bundle (.aab)** through Play Console.

Release signing information should remain outside the repository.

> Never commit your keystore or signing passwords to GitHub.

---

## 🛡️ Privacy

Radar Proxy does **not** request:

```text
❌ Telegram Login
❌ Phone Number
❌ Telegram API ID / Hash
❌ Telegram Session
```

Proxy information and SOCKS credentials remain local to the device and are used only for proxy connection or protocol testing.

Diagnostic messages do not expose proxy secrets or credentials.

---

## 🔐 Permissions

The application uses only the permissions required for its functionality.

The base manifest declares:

```text
INTERNET
ACCESS_NETWORK_STATE
```

Background scheduling may add standard Android scheduling permissions required by WorkManager.

Radar Proxy does not request unnecessary access to:

```text
SMS
Contacts
Accessibility
Notifications
Storage
Overlay
VPN
Device Admin
Package Installation
```

---

## 📱 Android Compatibility

```text
Android 8.0+
API 26+
```

Radar Proxy is designed for modern Android devices while retaining compatibility with older supported versions.

---

## 📚 References

* [Android App Bundle](https://developer.android.com/guide/app-bundle)
* [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
* [Google Play Protect Developer Guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)

---
🇮🇷 Radar Proxy

⚡ مدیریت بومی پروکسی تلگرام برای اندروید

کشف • جمع‌آوری • تست • اتصال

---
# 🇮🇷 فارسی

💚 **Donate With Crypto:** [Payments.tipora.ir](https://payments.tipora.ir)

💚 **Dono With Rial:** `Soon`

Radar Proxy یک برنامه **Native اندروید و متن‌باز** برای جمع‌آوری، نگهداری، مرتب‌سازی، تست و اتصال به پروکسی‌های **MTProto و SOCKS5 تلگرام** است.

این پروژه با فناوری‌های مدرن اندروید مانند **Kotlin، Jetpack Compose، Room، DataStore، WorkManager و OkHttp** ساخته شده است.

---

## 🔗 Subscription | اشتراک

لینک Subscription:

**[Open Subscription](https://raw.githubusercontent.com/khodejav/telegram-proxy-collector/main/output/proxies.txt)**

```text
https://raw.githubusercontent.com/khodejav/telegram-proxy-collector/main/output/proxies.txt
```

> لینک بالا را در Radar Proxy یا هر کلاینت سازگار با Subscription وارد کنید.

---

## ✨ امکانات

* 🔄 بروزرسانی خودکار Subscription
* 📦 نگهداری دائمی پروکسی‌ها
* 🧹 حذف خودکار اختیاری
* 🛡️ سیستم قدرتمند ضد Duplicate
* 🔎 جستجو و مرتب‌سازی
* ⚡ تست واقعی پروتکل
* 📡 پشتیبانی از Wi-Fi و اینترنت موبایل
* 🔗 اتصال سریع به تلگرام
* 🎨 رابط کاربری مدرن
* 🌙 پشتیبانی از Theme
* 🌐 پشتیبانی فارسی و انگلیسی
* 📱 پشتیبانی از Android 8+

---

## 📡 پروکسی‌های پشتیبانی‌شده

Radar Proxy از این پروتکل‌ها پشتیبانی می‌کند:

```text
MTProto
SOCKS5
```

فرمت‌های مختلف لینک‌های تلگرام نیز پشتیبانی می‌شوند:

```text
tg://proxy
tg://socks

https://t.me/proxy
https://t.me/socks

https://telegram.me/proxy
https://telegram.me/socks

t.me/...
telegram.me/...
```

سیستم Parser لینک‌ها را بررسی، Normalize و اعتبارسنجی می‌کند.

---

## 🔄 سیستم Subscription

فرآیند بروزرسانی به‌صورت زیر انجام می‌شود:

```text
دریافت
  ↓
استخراج
  ↓
اعتبارسنجی
  ↓
Normalize
  ↓
ادغام
  ↓
حذف تکراری‌ها
  ↓
ذخیره
```

پروکسی‌های جدید اضافه می‌شوند و اطلاعات قبلی حفظ می‌شود.

حذف منبع باعث حذف خودکار Proxyهای قبلی نمی‌شود.

فقط سیستم Cleanup می‌تواند پروکسی‌های قدیمی را حذف کند.

---

## 🧹 حذف خودکار

این قابلیت کاملاً اختیاری است.

زمان‌های قابل انتخاب:

```text
4H
8H
12H
24H
```

در صورت فعال بودن، پروکسی‌هایی که بیشتر از مدت انتخاب‌شده از آخرین مشاهده‌شان گذشته باشد، حذف می‌شوند.

در صورت غیرفعال بودن:

> هیچ حذف خودکاری انجام نمی‌شود.

اجرای وظایف زمان‌بندی‌شده توسط **WorkManager** مدیریت می‌شود.

---

## 🧪 تست واقعی پروکسی

تست‌ها مستقیماً از شبکه‌ای که دستگاه در آن قرار دارد اجرا می‌شوند:

```text
Wi-Fi
Mobile Data
```

برای MTProto و SOCKS5 تست واقعی پروتکل انجام می‌شود و **ICMP Ping** مورد استفاده قرار نمی‌گیرد.

هر Proxy تست مستقل خود را دارد؛ در نتیجه شکست یک Proxy باعث توقف تست سایر پروکسی‌ها نمی‌شود.

نتایج:

```text
🟢 موفق
🔴 ناموفق
⏱️ Timeout
```

---

## 🎨 رابط کاربری

Radar Proxy با تمرکز روی یک رابط مدرن و تمیز طراحی شده است.

امکانات اصلی:

* 📊 نمایش وضعیت کلی
* 🔍 جستجو
* ↕️ مرتب‌سازی
* 🗂️ دسته‌بندی پروتکل‌ها
* 🧩 کارت‌های Proxy
* 🧪 تست تکی
* ⚡ تست همه
* 🔗 اتصال به Telegram
* ℹ️ بخش About
* 🌐 فارسی / English
* 🌙 Theme

تعداد Proxyهای نگهداری‌شده و آخرین بروزرسانی نیز در صفحه اصلی نمایش داده می‌شود.

---

## 🏗️ تکنولوژی

| تکنولوژی            | کاربرد          |
| ------------------- | --------------- |
| Kotlin              | زبان اصلی       |
| Jetpack Compose     | رابط کاربری     |
| Room                | دیتابیس محلی    |
| DataStore           | تنظیمات         |
| WorkManager         | کارهای پس‌زمینه |
| OkHttp              | ارتباط شبکه     |
| Telegram Deep Links | اتصال به Proxy  |

---

## 📦 Build & Release

```text
Target SDK : API 36
Minimum   : API 26
```

معماری‌های قابل خروجی:

```text
armeabi-v7a
arm64-v8a
Universal
```

دستورات Build:

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew assembleRelease
./gradlew bundleRelease
```

برای نصب مستقیم:

> از **Universal Release APK** امضاشده استفاده کنید.

برای Google Play:

> فایل **AAB** امضاشده را از طریق Play Console منتشر کنید.

اطلاعات Signing نباید داخل Repository قرار بگیرد.

> هرگز Keystore یا Password آن را در GitHub منتشر نکنید.

---

## 🔐 حریم خصوصی

Radar Proxy به موارد زیر نیازی ندارد:

```text
❌ ورود به Telegram
❌ شماره تلفن
❌ Telegram API ID / Hash
❌ Telegram Session
```

اطلاعات Proxy و Credentialهای SOCKS در دستگاه باقی می‌مانند و فقط برای اتصال یا تست پروتکل استفاده می‌شوند.

اطلاعات حساس نیز در پیام‌های Diagnostic نمایش داده نمی‌شوند.

---

## 🛡️ مجوزها

مجوزهای پایه برنامه:

```text
INTERNET
ACCESS_NETWORK_STATE
```

WorkManager ممکن است مجوزهای استاندارد موردنیاز برای اجرای وظایف زمان‌بندی‌شده را به Manifest نهایی اضافه کند.

برنامه برای قابلیت‌های خود نیازی به موارد زیر ندارد:

```text
SMS
Contacts
Accessibility
Notification Listener
Storage
Overlay
VPN
Device Admin
Package Installation
```

---

## 📱 سازگاری

```text
Android 8.0+
API 26+
```

---

## 👨‍💻 About

| Label                 | URL                                                             |
| --------------------- | --------------------------------------------------------------- |
| 📱 Telegram           | [@Radar_Proxy](https://t.me/Radar_Proxy)                        |
| 💻 GitHub             | [KhodeJav/radar-proxy](https://github.com/KhodeJav/radar-proxy) |
| 💚 Donate With Crypto | [Payments.tipora.ir](https://payments.tipora.ir)                |

---

<p align="center">
  <strong>Radar Proxy</strong><br>
  <sub>Collect • Test • Organize • Connect</sub>
</p>

<p align="center">
  ⭐ Star the repository if you find it useful.
</p>
