@echo off
REM Double-click to start the C2C Job Application Agent and open the dashboard.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1"
if errorlevel 1 pause
