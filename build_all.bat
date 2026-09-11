@echo off
setlocal

if not defined JAVA_HOME (
    echo JAVA_HOME must point to a JDK 17 installation.
    exit /b 1
)

for /f "tokens=3" %%V in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
if not "%JAVA_VERSION:~0,3%"=="17." (
    echo This project requires JDK 17 for reliable Windows jpackage builds.
    echo Current JAVA_HOME: %JAVA_HOME%
    exit /b 1
)

echo Building Android debug APK...
call gradlew.bat :composeApp:assembleDebug
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Building Windows executable package...
call gradlew.bat :composeApp:createDistributable
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Build completed successfully.
echo APK: composeApp\build\outputs\apk\debug\composeApp-debug.apk
echo EXE: composeApp\build\compose\binaries\main\app\pdf-everything\pdf-everything.exe
