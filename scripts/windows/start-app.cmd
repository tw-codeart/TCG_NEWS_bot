@echo off
chcp 65001 >nul
title TCG Java API - 8080 - Keep open
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-app.ps1"
pause
