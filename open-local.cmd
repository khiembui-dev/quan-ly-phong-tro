@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0open-local.ps1"
if errorlevel 1 (
  echo.
  echo Open local failed. See the message above.
  pause
)

