@echo off
setlocal
set ADB=F:\Android\Sdk\platform-tools\adb.exe
set APK=%~dp0app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK%" (
  echo APK missing. Build first: gradlew.bat :app:assembleDebug
  exit /b 1
)
"%ADB%" devices
"%ADB%" install -r "%APK%"
"%ADB%" shell am start -n com.halehoundforge.fire.debug/com.halehoundforge.fire.ui.ValhallaGateActivity
echo Done.
