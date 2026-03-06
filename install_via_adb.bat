@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "APK_PATH=%SCRIPT_DIR%app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE_NAME=com.example.litemediaplayer"
set "MAIN_ACTIVITY=.MainActivity"

set "SKIP_BUILD=0"
set "CLEAR_DATA=1"
set "FORCE_REINSTALL=0"

if /I "%~1"=="--help" goto :usage

for %%I in (%*) do (
    if /I "%%~I"=="--no-build" set "SKIP_BUILD=1"
    if /I "%%~I"=="--keep-data" set "CLEAR_DATA=0"
    if /I "%%~I"=="--reinstall" set "FORCE_REINSTALL=1"
)

set "ADB_CMD="
where adb >nul 2>&1
if not errorlevel 1 (
    set "ADB_CMD=adb"
) else if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set "ADB_CMD=%ANDROID_HOME%\platform-tools\adb.exe"
) else if exist "%SCRIPT_DIR%platform-tools\adb.exe" (
    set "ADB_CMD=%SCRIPT_DIR%platform-tools\adb.exe"
)

if not defined ADB_CMD (
    echo [ERROR] adb was not found.
    echo Add Android Platform Tools to PATH or set ANDROID_HOME.
    exit /b 1
)

echo [INFO] Using adb: "%ADB_CMD%"
call "%ADB_CMD%" start-server >nul 2>&1

echo [INFO] Checking connected devices...
set "DEVICE_SERIAL="
set /a DEVICE_COUNT=0
set /a UNAUTHORIZED_COUNT=0

for /f "skip=1 tokens=1,2" %%A in ('"%ADB_CMD%" devices') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        if not defined DEVICE_SERIAL set "DEVICE_SERIAL=%%A"
    )
    if "%%B"=="unauthorized" (
        set /a UNAUTHORIZED_COUNT+=1
    )
)

echo [INFO] Device count: !DEVICE_COUNT!, unauthorized: !UNAUTHORIZED_COUNT!

if "!DEVICE_COUNT!"=="0" (
    if not "!UNAUTHORIZED_COUNT!"=="0" (
        echo [ERROR] Device is unauthorized.
        echo Accept the USB debugging prompt on the device and run again.
    ) else (
        echo [ERROR] No connected device found.
        echo Connect a device with USB debugging enabled and run again.
    )
    exit /b 1
)

if not "!DEVICE_COUNT!"=="1" (
    if not "!DEVICE_COUNT!"=="0" (
        echo [WARN] Multiple devices detected. Using first device: !DEVICE_SERIAL!
    )
)

set "ADB=%ADB_CMD% -s !DEVICE_SERIAL!"
echo [INFO] Target device: !DEVICE_SERIAL!

if "!SKIP_BUILD!"=="0" (
    echo [INFO] Building debug APK...
    call "%SCRIPT_DIR%gradlew.bat" assembleDebug --stacktrace
    if errorlevel 1 (
        echo [ERROR] Build failed.
        exit /b 1
    )
)

if not "!SKIP_BUILD!"=="0" echo [INFO] Build skipped (--no-build).

if not exist "%APK_PATH%" (
    echo [ERROR] APK not found: "%APK_PATH%"
    exit /b 1
)

echo [INFO] Stopping running app process...
call %ADB% shell am force-stop "%PACKAGE_NAME%" >nul 2>&1

if "!CLEAR_DATA!"=="1" (
    echo [INFO] Clearing app data...
    call %ADB% shell pm clear "%PACKAGE_NAME%" >nul 2>&1
    if errorlevel 1 (
        echo [INFO] Skip clear: package data was not found.
    )
)

if not "!CLEAR_DATA!"=="1" echo [INFO] Keeping app data (--keep-data).

if "!FORCE_REINSTALL!"=="1" (
    echo [INFO] Force reinstall requested. Uninstalling existing app...
    call %ADB% uninstall "%PACKAGE_NAME%" >nul 2>&1
)

echo [INFO] Installing: "%APK_PATH%"
call %ADB% install -r -d "%APK_PATH%"
if errorlevel 1 (
    echo [WARN] Install failed. Trying uninstall + clean install...
    call %ADB% uninstall "%PACKAGE_NAME%" >nul 2>&1
    call %ADB% install "%APK_PATH%"
    if errorlevel 1 (
        echo [ERROR] Install failed.
        exit /b 1
    )
)

echo [INFO] Launching app...
call %ADB% shell am start -n "%PACKAGE_NAME%/%MAIN_ACTIVITY%"
if errorlevel 1 (
    echo [ERROR] App launch failed.
    exit /b 1
)

set "APP_PID="
for /f "tokens=*" %%P in ('%ADB% shell pidof "%PACKAGE_NAME%"') do set "APP_PID=%%P"
if defined APP_PID echo [OK] App running with PID !APP_PID!
if not defined APP_PID echo [WARN] App process not detected after launch.

echo [OK] Install and launch completed.
exit /b 0

:usage
echo Usage: install_via_adb.bat [--no-build] [--keep-data] [--reinstall]
echo   --no-build   Skip Gradle assembleDebug
echo   --keep-data  Do not clear app data before install
echo   --reinstall  Uninstall app before install
exit /b 0
