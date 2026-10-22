@echo off
chcp 65001 >nul
set "STOP_PS=%~dp0..\script\stop.ps1"

echo [停止] 正在查找并停止 D08_CONF_JVM_LLT 对应的 java 进程...
powershell -NoProfile -ExecutionPolicy Bypass -File "%STOP_PS%"
echo [停止] 操作完成。
