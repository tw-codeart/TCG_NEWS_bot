@echo off
chcp 65001 >nul
title TCG Bot Supervisor - Keep open
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-bot.ps1"
pause
