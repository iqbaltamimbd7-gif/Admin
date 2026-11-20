SAYMA BUILDERS — ANDROID APK + GOOGLE PLAY AAB BUILD PACKAGE (R4.2)
======================================================================

এই প্যাকেজে কী আছে
------------------
1) Android Studio project (আগের R4.1 offline-first source ও app assets সহ)
2) Android 16 / API 36 target configuration
3) Debug APK build workflow
4) Signed release APK + Play Store AAB build workflow
5) Windows build helper scripts

গুরুত্বপূর্ণ: এই ZIP-এ এখনো compiled APK বা AAB binary নেই।
এই environment-এ Android SDK/Gradle নেই এবং external dependency download-এর network access নেই।
তাই build workflow প্রস্তুত করা হয়েছে; cloud runner বা Android Studio-তে build চালাতে হবে।

Google Play target API
----------------------
Google Play-এর official policy অনুযায়ী 31 August 2026 থেকে নতুন app এবং app update-এর জন্য
Android 16 / API 36 বা তার বেশি target করতে হবে। তাই এই project-এ:
- compileSdk = 36
- targetSdk = 36
- Android Gradle Plugin = 8.10.1
- Gradle = 8.11.1
- JDK = 17

এই target level Play submission-এর একটি শর্ত মাত্র; Play review বা approval নিশ্চিত করে না।

Package identity
----------------
Application ID: com.saymabuilders.mobile
Debug application ID: com.saymabuilders.mobile.debug
Version name: 1.1.1
Version code: 3

যদি আগে Play Store-এ app publish করা হয়ে থাকে
---------------------------------------------
আগের Play Store listing-এর একই application ID এবং existing upload key/keystore লাগবে।
নতুন keystore বানালে existing app update হিসেবে upload নাও হতে পারে।
আগের upload key হারিয়ে গেলে Play Console-এর upload key reset process অনুসরণ করতে হবে।

যদি এটি প্রথম Play Store release হয়
----------------------------------
একটি upload keystore তৈরি করতে হবে। নিজের PC-তে JDK keytool ব্যবহার করে:
keytool -genkeypair -v -keystore sayma-upload-key.jks -keyalg RSA -keysize 4096 -validity 10000 -alias sayma-upload

Keystore ও password নিরাপদে নিজের কাছে রাখুন। Keystore/password কাউকে public chat বা repository-তে দেবেন না।
এই project-এ signing secret source code-এ রাখা হয়নি।

GitHub Actions দিয়ে APK ও AAB build
-----------------------------------
1) একটি PRIVATE GitHub repository তৈরি করুন।
2) এই ZIP extract করে project-এর সব file/folder repository root-এ upload করুন।
3) GitHub repository > Settings > Secrets and variables > Actions-এ চারটি Repository Secret যোগ করুন:

ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD

ANDROID_KEYSTORE_BASE64 হলো আপনার .jks file-এর Base64 text।
Windows PowerShell-এ Base64 তৈরি করতে:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sayma-upload-key.jks"))

4) main branch-এ push করুন অথবা Actions > SAYMA BUILDERS Android APK + Play Store AAB > Run workflow চালান।
5) Workflow শেষ হলে Artifacts থেকে:
   - SAYMA-BUILDERS-debug-APK (ফোনে test install)
   - SAYMA-BUILDERS-release-APK-and-Play-Store-AAB (signed APK ও AAB)
   download করুন।

Local Android Studio build
--------------------------
প্রয়োজন:
- Android Studio version supporting API 36
- JDK 17
- Android SDK Platform 36
- Gradle 8.11.1

Debug APK:
gradle --no-daemon assembleDebug
Output: app/build/outputs/apk/debug/app-debug.apk

Signed Release APK + AAB:
Environment variables সেট করে চালান:
gradle --no-daemon assembleRelease bundleRelease

Outputs:
- app/build/outputs/apk/release/app-release.apk
- app/build/outputs/bundle/release/app-release.aab

Google Play upload
------------------
Google Play Console-এ app create করুন, App signing onboarding সম্পন্ন করুন এবং
signed app-release.aab upload করুন। Play Console-এর Data safety, Privacy policy,
App access/login instructions, content rating, store listing, screenshots এবং review declarations
আলাদাভাবে সম্পূর্ণ করতে হবে। শুধু AAB থাকলেই app auto-publish হয় না।

Security
--------
- MySQL database APK-তে embed করা হয়নি।
- Existing PHP API/domain architecture বজায় আছে।
- Keystore, database credentials, PHP sessions বা passwords ZIP/repository-তে রাখা হয়নি।
- Employee/passport/financial data sensitive; device screen lock এবং access controls ব্যবহার করুন।

Limitations
-----------
- APK/AAB binaries এই environment-এ compile করা হয়নি।
- Live PHP/MySQL, physical Android device, upload queue এবং Play Console review পরীক্ষা করা হয়নি।
- API 36 target compatibility Android 16 behaviour changes-এর জন্য real-device testing প্রয়োজন।
