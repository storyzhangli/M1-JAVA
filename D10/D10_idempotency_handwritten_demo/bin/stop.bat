@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0..\script\stop.ps1"
endlocal
