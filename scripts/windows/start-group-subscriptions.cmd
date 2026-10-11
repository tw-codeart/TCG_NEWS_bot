@echo off
chcp 65001 >nul
title TCG Group Subscriptions - 8080 - Keep open
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable-group-subscriptions.ps1"
if errorlevel 1 goto done
call "%~dp0start-auto.cmd"
:done
pause
