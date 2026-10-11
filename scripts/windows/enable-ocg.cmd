@echo off
chcp 65001 >nul
title TCG Enable OCG RSSHub
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable-ocg.ps1"
pause
