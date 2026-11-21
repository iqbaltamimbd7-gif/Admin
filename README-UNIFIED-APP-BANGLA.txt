SAYMA BUILDERS — ONE UNIFIED ANDROID APP
========================================

এই Android project-এ একটি মাত্র APK থাকবে। একই Common Login Page থেকে:
- Admin ID/Password দিলে Mobile Admin Portal খুলবে।
- Worker Email/Mobile + Password দিলে Worker Portal খুলবে।
- App online অবস্থায় /portal/login.html খুলবে; PHP API role যাচাই করে সঠিক Portal-এ পাঠাবে।
- App offline হলে শেষ সফলভাবে ব্যবহৃত Role-এর bundled mobile screen খুলবে। Password APK বা local preference-এ রাখা হয় না।

Website/API address:
https://saymabuilders.com/portal/login.html

মূল App Package:
com.saymabuilders.mobile
Version: 1.2.0 (versionCode 4)
Target / Compile SDK: 36
JDK: 17
Gradle: 8.11.1
Android Gradle Plugin: 8.10.1

WHAT IS INCLUDED
----------------
1. Latest Mobile Admin UI, including Project Finance and in-portal document viewer.
2. Latest Worker Portal UI, including Leave, GPS Attendance, payslip archive and bank account.
3. One role-aware login entry point.
4. Native GPS permission handling (precise location is required for verified attendance).
5. Camera/file picker permissions for attendance proof and document uploads.
6. Native PDF preview inside the app using Android PdfRenderer.
7. DownloadManager downloads using the existing authenticated SAYMA session cookie.
8. Mobile website tools open inside an iframe in the portal where the external website allows embedding.
9. Bundled Admin offline data layer and Worker IndexedDB cache for previously loaded data/files.
10. No database password, API secret or signing keystore is included in the source ZIP.

IMPORTANT LIMITATIONS / EXPECTED BEHAVIOUR
------------------------------------------
- The user must sign in online at least once on that device before offline access can be remembered.
- Worker verified clock-in/clock-out still needs an internet connection so the server can verify the assigned site geofence and timestamp. GPS permission alone does not make an offline attendance event valid.
- Worker dashboard, previously loaded salary slips, MCs, receipts and attendance proof can be shown offline after they have been cached on that device. A file not opened/downloaded before going offline cannot be previewed offline.
- Some third-party websites block iframe embedding with X-Frame-Options or Content-Security-Policy. Those sites cannot be forced into an embedded frame; the tool viewer provides an “Open in browser” fallback.
- PDFs are opened in a native in-app viewer. Non-PDF files that the browser cannot preview can still be downloaded.
- The Android PDF viewer preview limit is 24 MB per file. The server upload limit for the current document features is lower (normally 15 MB).

BUILD THE TEST APK USING GITHUB ACTIONS
---------------------------------------
1. Keep your GitHub repository Private.
2. Back up the old Android source repository first.
3. Extract this ZIP. Upload the CONTENTS of this folder to the repository root (app/, build.gradle, settings.gradle, gradle.properties, .github/, etc.). Do not upload the outer folder as an extra nested directory.
4. Commit the files to the main branch.
5. Open GitHub > Actions > “SAYMA BUILDERS Unified Android App”.
6. Choose “Run workflow” if it has not started automatically.
7. Wait for “debug-apk” to finish successfully.
8. Open the workflow run > Artifacts > download SAYMA-BUILDERS-UNIFIED-DEBUG-APK.
9. Extract the APK and install it on the Android phone for testing.

The debug APK uses the .debug application ID suffix so it can be installed beside an existing release app. To replace the existing production app, use a signed release build with the SAME application ID and the original upload keystore.

OPTIONAL SIGNED RELEASE
-----------------------
If you already have your own Play upload keystore, add these GitHub repository Secrets:
- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Then add Repository Variable:
- ENABLE_SIGNED_RELEASE = true

The workflow will additionally build app-release.apk and app-release.aab. Never send your keystore or passwords to ChatGPT or commit them to GitHub.

PHONE TEST CHECKLIST
--------------------
[ ] Admin login opens Mobile Admin (not Desktop Admin).
[ ] Worker login opens Worker Portal.
[ ] Worker clock-in asks for precise location permission.
[ ] Worker clock-in opens camera/file capture for entry proof.
[ ] Worker outside the configured site radius is rejected by the API.
[ ] PDF preview opens in the in-app PDF viewer.
[ ] Downloaded documents appear in Android Downloads.
[ ] Plus Tools open inside the app when the third-party site permits iframe embedding.
[ ] Back button returns from PDF preview to the correct portal.
[ ] After one online login, disconnect internet and verify previously cached dashboard/documents.
[ ] Reconnect and verify the server data refreshes.

NOTE ABOUT BUILD STATUS
-----------------------
This delivery contains the complete Android Studio source project and a GitHub Actions build workflow. A compiled APK is not included because this working environment does not have Android SDK/Gradle installed. Run the workflow above to create the APK; then install and test it on a real phone before production use.
