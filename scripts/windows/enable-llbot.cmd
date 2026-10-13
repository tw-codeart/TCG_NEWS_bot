@echo off
chcp 65001 >nul
title TCG LLBot Setup
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable-llbot.ps1"
pause
