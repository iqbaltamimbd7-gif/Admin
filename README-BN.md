# SAYMA BUILDERS — নেটিভ অ্যান্ড্রয়েড অ্যাপ (Kotlin + Jetpack Compose)

এটা **WebView নয়, PWA নয়, "Add to Home screen" নয়।** প্রতিটি স্ক্রিন Kotlin/Compose দিয়ে অ্যাপের ভেতরেই আঁকা।
ওয়েবসাইটের HTML/CSS/JS অ্যাপে লোড হয় না। অ্যাপ শুধু আপনার `api.php` থেকে **ডেটা (JSON)** নেয় — ঠিক যেমন যেকোনো ব্যাংক বা শপিং অ্যাপ নিজের সার্ভার থেকে ডেটা নেয়।

> আপনি ডোমেনে ওয়েবসাইটের ডিজাইন/HTML বদলালে অ্যাপের চেহারা বদলাবে না। শুধু ডেটাবেসের তথ্য (ওয়ার্কার, পেমেন্ট, ডকুমেন্ট ইত্যাদি) দুই জায়গায় একই থাকবে — এটাই দরকার, কারণ একই ডেটাবেস।

## ১. সার্ভার ঠিকানা
এই অ্যাপের ডিফল্ট API ঠিকানা:
`https://saymabuilders.com/portal/api.php`

বেস URL:
`https://saymabuilders.com/portal/`

GitHub Actions build এই ঠিকানাটি `gradle.properties` থেকে নেবে। APK ইনস্টল করার পরে Login স্ক্রিনের **Server সেটিং** থেকেও ঠিকানা পরিবর্তন করা যায়।

## ২. APK বানানো
১. GitHub → Actions → "Build SAYMA Android APK"।
২. Build সফল হলে Artifacts থেকে `SAYMA-debug-apk` ডাউনলোড করুন।

## ৩. এই ভার্সনে যা আছে
**Login:** Admin / Worker লগইন, Worker রেজিস্ট্রেশন, প্রথমবার নতুন পাসওয়ার্ড সেট, সেশন সংরক্ষণ।

**Admin:** Home ড্যাশবোর্ড, Workers, Projects, Claims/Salary, Finance, Company Documents, Attendance, Approval inbox, Salary slips ও Expiry reminders।

**Worker:** Home, Payment ledger, Payslips, Payment জমা + রসিদ, Leave + MC, GPS + ক্যামেরাসহ Clock-in / Clock-out, Bank account আবেদন।

**ফাইল:** PDF অ্যাপের নিজস্ব `PdfRenderer` দিয়ে দেখা যায়; ছবি নেটিভভাবে; Download → ফোনের Downloads ফোল্ডার।

## ৪. গুরুত্বপূর্ণ
- Android app API থেকে JSON ডেটা নেয়; তাই server base URL অবশ্যই `/portal/` ফোল্ডার পর্যন্ত হতে হবে।
- API endpoint: `https://saymabuilders.com/portal/api.php`
- সার্ভারে `api.php`-এর response JSON হতে হবে।
- `https://` ডোমেন ব্যবহার করুন।
