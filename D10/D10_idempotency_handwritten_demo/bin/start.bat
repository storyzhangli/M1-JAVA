@echo off
chcp 65001 >nul
setlocal

rem ===== Locate module root (parent of bin) =====
set "APP_HOME=%~dp0.."
pushd "%APP_HOME%" >nul
set "APP_HOME=%CD%"
popd >nul

set "JVM_OPTIONS=%APP_HOME%\script\jvm.options"
set "JAR_FILE=%APP_HOME%\target\D10_idempotency_handwritten_demo-1.0-SNAPSHOT.jar"
set "LOG_DIR=%APP_HOME%\logs"
set "CONSOLE_LOG=%LOG_DIR%\console.out"

rem ===== Prepare log directories =====
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%LOG_DIR%\gc" mkdir "%LOG_DIR%\gc"
if not exist "%LOG_DIR%\heapdump" mkdir "%LOG_DIR%\heapdump"
if not exist "%LOG_DIR%\app" mkdir "%LOG_DIR%\app"

rem ===== Verify executable JAR =====
if not exist "%JAR_FILE%" (
    echo [ERROR] JAR not found: %JAR_FILE%
    echo         Please run: mvn clean package -DskipTests
    exit /b 1
)

rem ===== Simple duplicate-start guard (by window title) =====
tasklist /FI "WINDOWTITLE eq D10-IDEMP-APP*" 2>nul | find /I "java.exe" >nul
if not errorlevel 1 (
    echo [INFO] App may already be running - window title D10-IDEMP-APP. Run stop.bat first to restart.
    exit /b 1
)

echo [START] Work dir  : %APP_HOME%
echo [START] JVM options: %JVM_OPTIONS%
echo [START] Console log: %CONSOLE_LOG%

rem ===== Lock working dir to module root so relative paths in jvm.options resolve =====
cd /d "%APP_HOME%"

rem ===== Start in a new window titled D10-IDEMP-APP, redirect console output to file =====
start "D10-IDEMP-APP" cmd /c "java @%JVM_OPTIONS% -jar %JAR_FILE% > %CONSOLE_LOG% 2>&1"

echo [START] Launched in new window (title D10-IDEMP-APP). Use stop.bat to stop.
endlocal
