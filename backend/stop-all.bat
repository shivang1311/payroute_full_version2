@echo off
REM Double-click to stop all PayRoute Hub backend services.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-all.ps1"
