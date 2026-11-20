@echo off
setlocal
echo SAYMA BUILDERS - Debug APK build
echo Requires Android Studio, JDK 17, Android SDK 36 and Gradle 8.11.1.
gradle --no-daemon assembleDebug
if errorlevel 1 (
  echo BUILD FAILED. Check Android Studio / Gradle errors.
  pause
  exit /b 1
)
echo Debug APK:
echo app\build\outputs\apk\debug\app-debug.apk
pause
