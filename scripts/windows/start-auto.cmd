@echo off
chcp 65001 >nul
title TCG Automatic QQ Delivery - 8080 - Keep open
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable-auto.ps1"
if errorlevel 1 goto done
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-app.ps1"
:done
pause
