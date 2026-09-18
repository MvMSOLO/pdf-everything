@echo off
setlocal EnableExtensions EnableDelayedExpansion

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME must point to JDK 17+.
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: JAVA_HOME is invalid: %JAVA_HOME%
    exit /b 1
)

for /f "tokens=3" %%V in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
if not defined JAVA_VERSION (
    echo ERROR: Could not detect Java version.
    exit /b 1
)
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if not "!JAVA_MAJOR!"=="17" if not "!JAVA_MAJOR!"=="18" if not "!JAVA_MAJOR!"=="19" if not "!JAVA_MAJOR!"=="20" if not "!JAVA_MAJOR!"=="21" if not "!JAVA_MAJOR!"=="22" if not "!JAVA_MAJOR!"=="23" if not "!JAVA_MAJOR!"=="24" if not "!JAVA_MAJOR!"=="25" if not "!JAVA_MAJOR!"=="26" (
    echo ERROR: This release pipeline requires JDK 17 or newer. Detected: !JAVA_VERSION!
    exit /b 1
)

if not exist "%~dp0gradlew.bat" (
    echo ERROR: Gradle wrapper not found.
    exit /b 1
)

pushd "%~dp0"

echo === PDF Everything release verification ===
echo Java: !JAVA_VERSION!
echo.

echo [1/4] Running shared tests...
call gradlew.bat :composeApp:allTests --stacktrace
if errorlevel 1 goto :fail

echo [2/4] Building signed-ready Android release APK...
call gradlew.bat :composeApp:assembleRelease --stacktrace
if errorlevel 1 goto :fail

if exist composeApp\build\outputs\apk\release\composeApp-release-unsigned.apk (
    copy /y composeApp\build\outputs\apk\release\composeApp-release-unsigned.apk composeApp\build\outputs\apk\release\pdf-everything-release-unsigned.apk >nul
)

echo [3/4] Building Windows EXE and MSI installers...
call gradlew.bat :composeApp:packageExe :composeApp:packageMsi --stacktrace
if errorlevel 1 goto :fail

echo [4/4] Checking artifacts...
set "APK_FOUND=0"
set "EXE_FOUND=0"
set "MSI_FOUND=0"
for %%F in (composeApp\build\outputs\apk\release\*.apk) do set "APK_FOUND=1"
for /r "composeApp\build\compose\binaries\main\app" %%F in (*.exe) do set "EXE_FOUND=1"
for /r "composeApp\build\compose\binaries\main\msi" %%F in (*.msi) do set "MSI_FOUND=1"
if "!APK_FOUND!"=="0" echo ERROR: Release APK was not generated.& exit /b 1
if "!EXE_FOUND!"=="0" echo ERROR: Windows EXE was not generated.& exit /b 1
if "!MSI_FOUND!"=="0" echo ERROR: Windows MSI was not generated.& exit /b 1

echo.
echo RELEASE PIPELINE PASS
echo APK: composeApp\build\outputs\apk\release\
echo EXE: composeApp\build\compose\binaries\main\app\
echo MSI: composeApp\build\compose\binaries\main\msi\
popd
exit /b 0

:fail
echo.
echo RELEASE PIPELINE FAILED with exit code %ERRORLEVEL%.
popd
exit /b %ERRORLEVEL%
