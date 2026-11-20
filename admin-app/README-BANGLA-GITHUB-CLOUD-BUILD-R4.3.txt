SAYMA BUILDERS — সহজ Cloud Build (Android Studio ছাড়াই)
========================================================

আপনার PC-তে Android Studio লাগবে না। GitHub-এর Cloud Runner APK/AAB build করবে।

প্রস্তুত Project:
- Package ID: com.saymabuilders.mobile
- Version: 1.1.1 (versionCode 3)
- compileSdk / targetSdk: 36
- JDK 17, AGP 8.10.1, Gradle 8.11.1
- Existing mobile assets ও PHP API origin আগের মতো রাখা হয়েছে।

ধাপ ১ — GitHub account ও Private Repository
-------------------------------------------
1. https://github.com এ account খুলুন বা sign in করুন।
2. New repository চাপুন।
3. Repository name দিন: sayma-builders-android
4. অবশ্যই Private নির্বাচন করুন।
5. Create repository করুন।
6. এই ZIP extract করে সব ফাইল/ফোল্ডার repository root-এ upload করুন।
   .github folder-টিও upload করতে হবে।
7. Commit changes করুন।

ধাপ ২ — প্রথমে APK বানান (Debug)
--------------------------------
1. Repository-এর Actions tab খুলুন।
2. "SAYMA BUILDERS Android APK + Play Store AAB" workflow নির্বাচন করুন।
3. Run workflow চাপুন।
4. Build শেষ হলে নিচে Artifacts-এ:
   SAYMA-BUILDERS-debug-APK
   download করুন।
5. ZIP extract করে app-debug.apk Android phone-এ পাঠিয়ে install করুন।
   Android-এ "Install unknown apps" অনুমতি চাইতে পারে।
6. Install করে login, file picker, API, offline cache, sync এবং documents পরীক্ষা করুন।

Debug APK-তে application ID suffix `.debug` থাকে; এটি release app-এর পাশাপাশি আলাদা app হিসেবে install হবে।

ধাপ ৩ — Play Store upload key তৈরি (PC-তে JDK লাগবে না)
-------------------------------------------------------
1. GitHub Actions tab খুলুন।
2. "SAYMA BUILDERS - Generate Play Upload Key" workflow নির্বাচন করুন।
3. Run workflow চাপুন।
4. Build শেষ হলে Artifact:
   SAYMA-BUILDERS-PRIVATE-UPLOAD-KEY
   download করুন।
5. Artifact ZIP-এর মধ্যে থাকবে:
   - sayma-upload-key.jks
   - SIGNING-INFO-KEEP-PRIVATE.txt
   - ANDROID_KEYSTORE_BASE64.txt

SECURITY:
- Repository Private রাখুন।
- JKS ও password-গুলো নিজের কাছে নিরাপদে রাখুন এবং দুইটি private backup রাখুন।
- এগুলো ChatGPT, email, public repository বা অন্য কারও কাছে পাঠাবেন না।
- Artifact 1 দিনের মধ্যে expire হবে; তার আগেই download করুন।
- Download ও backup নিশ্চিত হওয়ার পরে GitHub Actions artifact delete করুন।

ধাপ ৪ — GitHub Secrets যোগ
--------------------------
Repository > Settings > Secrets and variables > Actions > New repository secret

এই ৪টি secret যোগ করুন:

1. ANDROID_KEYSTORE_BASE64
   Value = ANDROID_KEYSTORE_BASE64.txt ফাইলের সম্পূর্ণ এক লাইন

2. ANDROID_KEYSTORE_PASSWORD
   Value = SIGNING-INFO-KEEP-PRIVATE.txt-এ দেওয়া Keystore password

3. ANDROID_KEY_ALIAS
   Value = sayma-upload

4. ANDROID_KEY_PASSWORD
   Value = SIGNING-INFO-KEEP-PRIVATE.txt-এ দেওয়া Key password

কোনো secret এখানে ChatGPT-কে পাঠাবেন না।

ধাপ ৫ — Signed APK ও Play Store AAB build
-----------------------------------------
1. Actions tab খুলুন।
2. "SAYMA BUILDERS Android APK + Play Store AAB" workflow নির্বাচন করুন।
3. Run workflow চাপুন।
4. Workflow এখন signed release তৈরি করবে।
5. Artifacts থেকে:
   SAYMA-BUILDERS-release-APK-and-Play-Store-AAB
   download করুন।

এর মধ্যে থাকবে:
- app-release.apk (সরাসরি install/test)
- app-release.aab (Google Play Console-এ upload)

এই JKS ও ৪টি Secret ভবিষ্যতের সব update-এর জন্য সংরক্ষণ করুন। হারাবেন না।

ধাপ ৬ — Play Console
--------------------
1. Google Play Console developer account খুলুন।
2. Create app নির্বাচন করুন।
3. App name: SAYMA BUILDERS
4. Application ID / package: com.saymabuilders.mobile
5. App type: App; category: Business (যদি উপযুক্ত হয়)।
6. App access, Data safety, Privacy policy, Content rating, Store listing ও screenshots সম্পূর্ণ করুন।
7. Testing track-এ release তৈরি করে app-release.aab upload করুন।
8. Play App Signing enrollment সম্পূর্ণ করুন।
9. Internal/closed testing করে তারপর Production release-এর জন্য submit করুন।

Google Play-এর 31 August 2026 থেকে নতুন app submission-এর target API requirement Android 16 / API 36। এই project-এ targetSdk 36 সেট করা আছে। Play approval নিশ্চিত নয়; Play Console-এর সব policy declaration পূরণ করতে হবে।

ফাইল তৈরি হয়েছে মানেই Live app সম্পূর্ণ tested নয়:
- Live PHP/MySQL ও real phone test করুন।
- APK install, login, file upload, offline cache, sync, profile photo পরীক্ষা করুন।
- Play Console review/approval Google-এর সিদ্ধান্ত।

গুরুত্বপূর্ণ
-----------
এই Cloud Build ZIP-এ APK/AAB binary আগে থেকে নেই। GitHub Actions workflow চালালে তৈরি হবে।
