@echo off
rem Run from the device's copied Northland-THOR directory, inside GameHub.
set "northland_steam=Z:\data\data\gamehub.lite\files\Steam\steamapps\common\Cultures Northland"
if not exist "%northland_steam%\Game.exe" (
    echo Steam game directory not found. No files changed.
    pause
    exit /b 1
)
dir "%northland_steam%" > "%~dp0steam-game-files.log"
if exist "%northland_steam%\game.ini" (
    if not exist "%~dp0steam-original-game.ini" (
        copy "%northland_steam%\game.ini" "%~dp0steam-original-game.ini"
        if errorlevel 1 exit /b 1
    )
)
if not exist "%~dp0game.ini" exit /b 1
copy /y "%~dp0game.ini" "%northland_steam%\game.ini"
if errorlevel 1 exit /b 1
if not exist "%~dp0steam-game-logs" mkdir "%~dp0steam-game-logs"
if exist "%northland_steam%\logs\*" copy /y "%northland_steam%\logs\*" "%~dp0steam-game-logs\"
echo Applied game.ini test profile to the device Steam copy.
pause
