SAYMA WORKER ANDROID APP
========================

Worker app login:
https://saymabuilders.com/portal/worker/login.html

Worker dashboard:
https://saymabuilders.com/portal/worker/index.html

Shared API:
https://saymabuilders.com/portal/api.php

The Worker app is a separate Android application:
- Application ID: com.saymabuilders.worker
- It can be installed alongside the SAYMA Admin app.
- It uses the existing Worker Portal login and PHP session.
- It uses the same server API/database as the Worker Portal.
- Its bundled HTML/CSS/JS/assets are from the current Worker Portal source.

OFFLINE
-------
- The native app bundles Worker login/dashboard shell assets.
- After the worker signs in online once, the Android app remembers that login state
  locally (never the password) and can open the bundled Worker dashboard offline.
- After the first successful dashboard load, worker profile, payment ledger,
  payment confirmations, salary slip amounts/metadata and bank-account information
  are cached in IndexedDB on the device.
- A salary slip PDF/image can be opened offline after it has been opened once online.
- Worker cache is cleared when the worker signs out.
- New payment-proof submissions and bank-account requests still require a connection
  to reach the shared API; this version does not queue those writes offline.
- Use a personal/locked device because worker data is cached locally.

BUILD
----
Open this folder in Android Studio (JDK 17, Android SDK 36).
Build > Build Bundle(s) / APK(s) > Build APK(s).
The debug APK is at app/build/outputs/apk/debug/app-debug.apk.

Or use the root .github/workflows/build-two-apps.yml workflow to build both APKs
and download them as GitHub Actions artifacts.

The current environment has no Android SDK or Gradle installation, so APK binaries
are not compiled here. This ZIP contains both complete Android Studio source projects.
