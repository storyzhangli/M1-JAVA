@echo off
chcp 65001 >nul
set "STOP_PS=%~dp0..\script\stop.ps1"

echo [STOP] Finding and stopping java process for D10_IARL ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%STOP_PS%"
echo [STOP] Done.
