SAYMA BUILDERS — ANDROID APK + PLAY STORE AAB
================================================

This is the Android Studio SOURCE PROJECT, not compiled APK/AAB binaries.

Package:
- Release applicationId: com.saymabuilders.mobile
- Debug applicationId: com.saymabuilders.mobile.debug
- Version: 1.1.1 (versionCode 3)
- compileSdk / targetSdk: 36
- AGP: 8.10.1 / Gradle: 8.11.1 / JDK: 17

Build debug APK:
  gradle --no-daemon assembleDebug
  app/build/outputs/apk/debug/app-debug.apk

Build signed release APK and Play Store AAB:
  Set ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS,
  ANDROID_KEY_PASSWORD, then run:
  gradle --no-daemon assembleRelease bundleRelease

Outputs:
  app/build/outputs/apk/release/app-release.apk
  app/build/outputs/bundle/release/app-release.aab

GitHub Actions:
- Workflow: SAYMA BUILDERS Android APK + Play Store AAB
- Build debug APK always.
- To build signed APK/AAB, add repository secrets:
  ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS
  ANDROID_KEY_PASSWORD

IMPORTANT:
- Existing Play Store app: use the same upload key as the existing listing.
- First Play Store release: create and securely keep an upload keystore.
- Do not commit keystore/passwords into Git.
- No APK/AAB binary is included in this ZIP.
