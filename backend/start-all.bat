@echo off
REM ===================================================================
REM Double-click this file to start all PayRoute Hub backend services.
REM It just delegates to start-all.ps1 with execution-policy bypassed
REM so users don't need to fiddle with Set-ExecutionPolicy first.
REM ===================================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
