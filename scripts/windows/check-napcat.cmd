@echo off
chcp 65001 >nul
title TCG NapCat Read-Only Check
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-napcat.ps1"
pause
