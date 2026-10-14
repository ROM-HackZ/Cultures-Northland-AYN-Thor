@echo off
rem Run from the copied game directory inside the Windows container.
cd /d "%~dp0"
echo Launching Northland from %CD%
dir /b C:\windows\syswow64 > northland-syswow64.log 2>&1
dir /b C:\windows\system32 > northland-system32.log 2>&1
set WINEDEBUG=+module,+loaddll,+seh
if exist probe-runtime.exe probe-runtime.exe
Game.exe > northland-launch.log 2>&1
set "northland_exit=%ERRORLEVEL%"
echo Exit code: %northland_exit%
echo Exit code: %northland_exit%>> northland-launch.log
pause
