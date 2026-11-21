# SAYMA BUILDERS — নেটিভ অ্যান্ড্রয়েড অ্যাপ (Kotlin + Jetpack Compose)

এটা **WebView নয়, PWA নয়, "Add to Home screen" নয়।** প্রতিটি স্ক্রিন Kotlin/Compose দিয়ে অ্যাপের ভেতরেই আঁকা।
ওয়েবসাইটের HTML/CSS/JS অ্যাপে লোড হয় না। অ্যাপ শুধু আপনার `api.php` থেকে **ডেটা (JSON)** নেয় — ঠিক যেমন যেকোনো ব্যাংক বা শপিং অ্যাপ নিজের সার্ভার থেকে ডেটা নেয়।

> আপনি ডোমেনে ওয়েবসাইটের ডিজাইন/HTML বদলালে অ্যাপের চেহারা বদলাবে না। শুধু ডেটাবেসের তথ্য (ওয়ার্কার, পেমেন্ট, ডকুমেন্ট ইত্যাদি) দুই জায়গায় একই থাকবে — এটাই দরকার, কারণ একই ডেটাবেস।

## ১. সার্ভার ঠিকানা সেট করুন
`gradle.properties` ফাইলে এই লাইন বদলান (শেষে `/` থাকবে, `/portal/` ফোল্ডার পর্যন্ত):

    SAYMA_BASE_URL=https://yourdomain.com/portal/

অ্যাপের লগইন স্ক্রিনে "Server সেটিং" থেকেও পরে বদলানো যায়।

## ২. APK বানানো
**পদ্ধতি A — GitHub Actions (আপনার গাইডের মতোই):**
১. এই ফোল্ডারটি একটি GitHub রিপোজিটরিতে আপলোড করুন।
২. Actions → "Build SAYMA Android APK" → Run workflow।
৩. শেষ হলে Artifacts থেকে `SAYMA-debug-apk` ডাউনলোড করুন।

**পদ্ধতি B — Android Studio:** ফোল্ডারটি Open করুন → Sync → Build → Build APK(s)।

## ৩. এই ভার্সনে যা আছে

**Login:** Admin / Worker লগইন, Worker রেজিস্ট্রেশন, প্রথমবার নতুন পাসওয়ার্ড সেট, সেশন সংরক্ষণ।

**Admin:** Home ড্যাশবোর্ড (নেটিভ চার্ট), Workers + বিস্তারিত + ডকুমেন্ট, Projects + Claims/Salary শিট + Claim PDF + প্রজেক্ট ডকুমেন্ট,
Finance (Permit ledger), Company Documents, Attendance (রেকর্ড, ছবি, Leave অনুমোদন/বাতিল, সাইট লোকেশন),
Approval inbox (Worker registration / Payment / Bank অনুমোদন), Salary slips, Documentation tools, Expiry reminders।

**Worker:** Home (প্রোফাইল, Payment ledger), Payslips, Payment জমা + রসিদ, Leave + MC, GPS + ক্যামেরা ছবিসহ Clock-in / Clock-out, Bank account আবেদন।

**ফাইল:** PDF অ্যান্ড্রয়েডের নিজস্ব `PdfRenderer` দিয়ে অ্যাপের ভেতরে দেখা যায়; ছবি নেটিভভাবে; Download → ফোনের Downloads ফোল্ডার।

## ৪. এই ভার্সনে এখনো নেই (ইচ্ছা করেই / পরের ধাপ)
- Admin থেকে ডেটা **এডিট/সেভ** (ওয়ার্কার/প্রজেক্ট/গ্রিড)। ওয়েবসাইটের `save_state` পুরো ডেটাবেস স্টেট একসাথে ওভাররাইট করে, তাই ভুল হলে ডেটা হারানোর ঝুঁকি আছে — আগে ফোনে দেখা ও অনুমোদন কাজ নিশ্চিত করে তারপর যোগ করা নিরাপদ।
- Letters, Tax (AI), Password vault, Project Attendance (ম্যানুয়াল), ডকুমেন্ট আপলোড/ক্যাটাগরি এডিট।
- Offline ক্যাশ (বর্তমানে সব স্ক্রিনে ইন্টারনেট লাগে)।

## ৫. গুরুত্বপূর্ণ
- এই সোর্স আমি এখানে **কম্পাইল করে দেখতে পারিনি** (এই পরিবেশে Android SDK/Gradle নেই)। প্রথম বিল্ডে ছোটখাটো কম্পাইল ত্রুটি আসতে পারে — Actions-এর লগ বা Android Studio-র এরর আমাকে পাঠালে ঠিক করে দেব।
- সার্ভারে `api.php`-তে কোনো পরিবর্তন লাগবে না। সেশন কুকি (`SAYMA_PORTAL_SESSID`) অ্যাপ নিজেই সংরক্ষণ করে।
- `https://` ডোমেন ব্যবহার করুন (অ্যান্ড্রয়েড ডিফল্টভাবে http বন্ধ রাখে)।
