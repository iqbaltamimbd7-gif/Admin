SAYMA BUILDERS — OFFLINE-FIRST UPDATE (R4)
==========================================

WHAT THIS UPDATE DOES
---------------------
1. After the first successful online login and data load, the app stores the latest state in IndexedDB:
   workers, projects, project finance grids, permit/payment records, attendance, tax records,
   document metadata, tools, categories, worker-document lists and administrator profile.
2. The app can reopen the bundled mobile UI without internet and show the last saved snapshot.
3. Supported edits made offline are saved in the app's IndexedDB and placed in a persistent sync queue.
4. When the internet returns while the app is open, or when the app is reopened/resumed online,
   it reconnects to the existing PHP API and synchronizes queued work.
5. Queue items remain on the device until the server confirms success.
6. If the same record was changed both on the server and on the phone while offline, the app asks
   before using the phone's conflicting version. Cancel keeps the queue pending.
7. Documents/PDFs already opened online are cached for offline viewing. Newly selected photos,
   receipts, claim PDFs and document uploads are kept locally until they can upload.

SUPPORTED OFFLINE QUEUE OPERATIONS
----------------------------------
- Worker/project/finance/attendance/tax and other main state updates via existing save_state action.
- Company document upload/edit/delete.
- Worker document upload/delete.
- Payment receipt upload.
- Claim PDF upload.
- Administrator profile details and profile photo.
- Document category add/rename.
- Documentation tool add/delete.
- Reminder preference save.

ONLINE-ONLY OPERATIONS
---------------------
- Password change.
- Credential vault operations.
- First-time login/authentication.
- A server document that has never been downloaded/viewed on this device cannot be previewed offline.
- Existing server-side documents are cached for offline preview after they have been opened once online.

HOW DATA IS STORED
------------------
- MySQL remains on the hosting server.
- The APK does NOT contain a MySQL database, DB username, password or SQL dump.
- Offline state and the upload queue are stored in IndexedDB within the Android app's private WebView storage.
- Uploaded file blobs are stored in a separate IndexedDB attachment store.
- The app uses the existing same-origin HTTPS API path ../api.php and existing profile.php/reminders.php.
- No PHP endpoint or database table name has been changed in this update.

SYNC TIMING
-----------
- Automatic sync when the device comes back online while the app is running.
- A sync check also runs when the app returns to the foreground.
- If the app is fully closed when internet returns, pending items sync the next time the app is opened online.
- This release does not run a permanent background worker while Android has force-stopped the app.
- If the PHP login session has expired, sign in again; the pending queue stays on the device.

IMPORTANT FIRST-TIME SETUP
--------------------------
1. Install/open the app while online.
2. Sign in using the existing company login.
3. Wait until the dashboard and required records have loaded and the status says Database connected.
4. Open important documents once online if you need them available offline.
5. After that, test Airplane Mode and check that cached data appears.
6. Make an offline test update and confirm Pending count increases.
7. Restore internet while the app is open or reopen it online.
8. Confirm Pending count returns to zero and verify the record in the Desktop portal/MySQL-backed site.

DATA PRIVACY
------------
The offline cache is kept in the Android app's private storage sandbox, not in a public folder.
It is not a substitute for Android device encryption or a device screen lock. Protect the phone
with a strong screen lock and do not share an unlocked device containing employee/passport records.
A local app PIN/biometric gate can be added as a separate security layer before public distribution.

INSTALL SERVER MOBILE SOURCE (if updating the PWA as well)
----------------------------------------------------------
- Back up public_html/mobile.
- Upload/extract the included mobile folder at public_html/mobile.
- Do not replace api.php, config.php, profile.php, reminders.php, login.html, Desktop files or database.
- Existing API path stays ../api.php.

BUILD ANDROID APK
-----------------
- Open the Android project folder in Android Studio with JDK 17.
- Allow Gradle sync; install Android SDK Platform 35 if prompted.
- Build > Build Bundle(s) / APK(s) > Build APK(s).
- Or push the project to GitHub and run Actions > Build SAYMA BUILDERS APK.
- Debug APK output: app/build/outputs/apk/debug/app-debug.apk.

LIMITATIONS / VERIFICATION
--------------------------
- JavaScript syntax and 3-way merge helper tests are run during packaging.
- Android SDK/Gradle are not installed in this environment, so the APK binary is not compiled here.
- Live hosting, actual PHP session, MySQL writes, real phone offline/online transition and file uploads
  must be verified on the actual server/device after build.
