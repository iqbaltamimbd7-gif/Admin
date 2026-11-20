# SAYMA BUILDERS Android Cloud Build\n\nNo Android Studio is required on your computer. Follow `README-BANGLA-GITHUB-CLOUD-BUILD-R4.3.txt`. Use a PRIVATE GitHub repository. Keep the upload keystore and passwords private.\n\nSAYMA BUILDERS — OFFLINE-FIRST R4.1
===================================

এই প্যাকেজে তোমার আপলোড করা বাস্তব Backend-এর সঙ্গে মিলিয়ে Mobile Source এবং Android Studio Project আপডেট করা হয়েছে।

সবচেয়ে গুরুত্বপূর্ণ: API URL নতুন করে বানাতে হবে না। /mobile/ থেকে ../api.php স্বয়ংক্রিয়ভাবে
https://saymabuilders.com/portal/api.php-তে resolve হবে। Login-এর PHP session cookie একই HTTPS origin-এ
থাকবে, তাই আলাদা MySQL connection APK-তে লাগবে না।

১) Hosting-এ Mobile Source বসানো
-------------------------------
1. cPanel > File Manager খুলো।
2. saymabuilders.com domain-এর Document Root-এ যাও।
3. বর্তমান mobile ফোল্ডারের একটি backup ZIP রাখো।
4. SAYMA-BUILDERS-MOBILE-OFFLINE-FIRST-R4.1.zip extract করো।
5. ZIP-এর mobile/ ফোল্ডারের ভিতরের সব ফাইল তোমার hosting-এর বিদ্যমান mobile/ ফোল্ডারে upload/replace করো।
   অর্থাৎ index.html যেন /mobile/index.html হয়; /mobile/mobile/index.html নয়।
6. api.php, config.php, profile.php, reminders.php, login.html, desktop বা database কোনোটি replace করবে না।

২) প্রথমবার ব্যবহার
-------------------
1. ফোনে internet চালু করো।
2. https://saymabuilders.com/portal/login.html?next=mobile খুলে আগের Admin login দিয়ে sign in করো।
3. Mobile dashboard পুরো load হওয়া পর্যন্ত অপেক্ষা করো; Database connected দেখাবে।
4. যেসব Documents/PDF offline-এ দরকার, সেগুলো অন্তত একবার online-এ খুলে নাও।
5. এরপর Airplane Mode দিয়ে cached data পরীক্ষা করো।
6. Offline-এ একটি test edit বা upload করলে Pending count বাড়বে।
7. Internet ফিরিয়ে app খোলা রাখো বা আবার app চালু করো; Pending count sync হয়ে কমে যাবে।
8. Desktop portal-এ গিয়ে test record সত্যিই server-এ পৌঁছেছে কি না যাচাই করো।

৩) Offline behaviour
--------------------
- সর্বশেষ সফল online snapshot IndexedDB-তে থাকবে।
- Worker, Projects, Finance, Attendance, Tax এবং cached metadata offline দেখা যাবে।
- Project claim/salary grid আলাদা API table-এ যায়, তাই সেটির জন্য আলাদা persistent sync queue আছে।
- Worker payment ledger-এর offline পরিবর্তন sync-এর আগে local change হিসেবে ধরে রাখা হবে।
- Document/worker document/receipt/claim PDF upload queue হবে।
- Online-এ একবার খোলা বা upload করা file পরে offline-এ preview করা যাবে।
- Password change এবং encrypted password vault online + authenticated session ছাড়া কাজ করবে না।
- Session expire হলে queued change ফোনে থাকবে; আবার login করলে sync হবে।
- App পুরোপুরি বন্ধ থাকলে background sync চলবে না; পরেরবার online-এ খুললে sync হবে।

৪) Android APK build
--------------------
1. SAYMA-BUILDERS-ANDROID-OFFLINE-FIRST-R4.1-PROJECT.zip extract করো।
2. Android Studio-তে project folder open করো।
3. JDK 17 এবং Android SDK Platform 35 install/sync করো।
4. Build > Build Bundle(s) / APK(s) > Build APK(s) নির্বাচন করো।
5. Debug APK পাওয়া যাবে app/build/outputs/apk/debug/app-debug.apk-এ।

GitHub ব্যবহার করলে project-এর .github/workflows/build-apk.yml workflow থেকে Actions > Build SAYMA BUILDERS APK চালিয়ে
APK artifact পাওয়া যাবে। এই environment-এ Android SDK/Gradle নেই, তাই এখানে APK binary compile করা হয়নি।

৫) Database এবং নিরাপত্তা
-------------------------
- MySQL server-এই থাকবে; APK-তে database password বা SQL dump নেই।
- Existing api.php এবং existing database/table names অপরিবর্তিত।
- Employee/passport data cache হয়; ফোনে strong screen lock ব্যবহার করো।
- তোমার আপলোড করা server ZIP-এ config.php credentials, tmp/session-like files এবং error_log ছিল।
  MySQL password rotate করো; tmp/session files বা error_log public folder-এ প্রকাশ কোরো না।
- api.php-তে public worker verification এবং public document-view action আছে; এগুলো broad public release-এর আগে
  privacy/security review করা দরকার। এই update-এ existing verification feature পরিবর্তন করা হয়নি।

৬) Validation সীমাবদ্ধতা
------------------------
PHP/JavaScript syntax, API action matching এবং merge helper static/unit checks pass করেছে।
Live hosting login, real MySQL read/write, upload এবং actual phone offline/online test এখনো করতে হবে।
