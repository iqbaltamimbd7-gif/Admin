@echo off
setlocal
if "%ANDROID_KEYSTORE_FILE%"=="" goto missing
if "%ANDROID_KEYSTORE_PASSWORD%"=="" goto missing
if "%ANDROID_KEY_ALIAS%"=="" goto missing
if "%ANDROID_KEY_PASSWORD%"=="" goto missing
echo SAYMA BUILDERS - Signed release APK + Play Store AAB
gradle --no-daemon assembleRelease bundleRelease
if errorlevel 1 (
  echo BUILD FAILED. Check signing values and Gradle errors.
  pause
  exit /b 1
)
echo Release APK:
echo app\build\outputs\apk\release\app-release.apk
echo Play Store AAB:
echo app\build\outputs\bundle\release\app-release.aab
pause
exit /b 0
:missing
echo Missing signing environment variables.
echo Set ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD first.
pause
exit /b 1
