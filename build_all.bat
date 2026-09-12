@echo off
setlocal

if not defined JAVA_HOME (
    echo JAVA_HOME must point to a JDK installation.
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
