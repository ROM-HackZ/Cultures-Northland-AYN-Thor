@echo off
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (
  py -3 scripts\setup-ui.py
) else (
  python scripts\setup-ui.py
)
if errorlevel 1 pause
