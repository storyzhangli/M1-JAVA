@echo off
chcp 65001 >nul
setlocal

rem ===== 定位模块根目录（bin 的上一级）=====
set "APP_HOME=%~dp0.."
pushd "%APP_HOME%" >nul
set "APP_HOME=%CD%"
popd >nul

set "JVM_OPTIONS=%APP_HOME%\script\jvm.options"
set "JAR_FILE=%APP_HOME%\target\D08_CONF_JVM_LLT-1.0-SNAPSHOT.jar"
set "LOG_DIR=%APP_HOME%\logs"
set "CONSOLE_LOG=%LOG_DIR%\console.out"

rem ===== 准备日志目录 =====
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%LOG_DIR%\gc" mkdir "%LOG_DIR%\gc"
if not exist "%LOG_DIR%\heapdump" mkdir "%LOG_DIR%\heapdump"
if not exist "%LOG_DIR%\app" mkdir "%LOG_DIR%\app"

rem ===== 校验可执行 JAR =====
if not exist "%JAR_FILE%" (
    echo [错误] 找不到可执行 JAR：%JAR_FILE%
    echo        请先在模块根目录执行： mvn clean package -DskipTests
    exit /b 1
)

rem ===== 简单防重复启动（按窗口标题）=====
tasklist /FI "WINDOWTITLE eq D08-APP*" 2>nul | find /I "java.exe" >nul
if not errorlevel 1 (
    echo [提示] 应用可能已在运行（窗口标题 D08-APP），如需重启请先执行 stop.bat
    exit /b 1
)

echo [启动] 工作目录 : %APP_HOME%
echo [启动] JVM 参数 : %JVM_OPTIONS%
echo [启动] 控制台日志: %CONSOLE_LOG%

rem ===== 锁定工作目录到模块根目录，确保 jvm.options 中相对路径（logs/...）正确解析 =====
cd /d "%APP_HOME%"

rem ===== 在新窗口启动，窗口标题 D08-APP，控制台输出重定向到文件 =====
start "D08-APP" cmd /c "java @%JVM_OPTIONS% -jar %JAR_FILE% > %CONSOLE_LOG% 2>&1"

echo [启动] 已在新窗口启动（标题 D08-APP）。可通过 stop.bat 停止。
endlocal
