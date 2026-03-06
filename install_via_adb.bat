@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "APK_PATH=%SCRIPT_DIR%app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE_NAME=com.example.litemediaplayer"
set "MAIN_ACTIVITY=com.example.litemediaplayer.MainActivity"

where adb >nul 2>&1
if errorlevel 1 (
    echo [ERROR] adb was not found in PATH.
    echo Please install Android Platform Tools and add adb to PATH.
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo [INFO] APK not found, running debug build.
    call "%SCRIPT_DIR%gradlew.bat" assembleDebug --stacktrace
    if errorlevel 1 (
        echo [ERROR] Build failed.
        exit /b 1
    )
)

echo [INFO] Checking connected devices...
set "DEVICE_FOUND="
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" set "DEVICE_FOUND=1"
)

if not defined DEVICE_FOUND (
    echo [ERROR] No connected device found.
    echo Connect a device with USB debugging enabled and try again.
    exit /b 1
)

echo [INFO] Installing: "%APK_PATH%"
adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo [ERROR] Install failed.
    exit /b 1
)

echo [INFO] Launching app...
adb shell am start -n "%PACKAGE_NAME%/%MAIN_ACTIVITY%"
if errorlevel 1 (
    echo [ERROR] App launch failed.
    exit /b 1
)

echo [OK] Install and launch completed.
exit /b 0
