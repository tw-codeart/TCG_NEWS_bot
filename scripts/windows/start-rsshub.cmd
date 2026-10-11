@echo off
chcp 65001 >nul
title TCG RSSHub - 1200 - Keep open
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-rsshub.ps1"
pause
