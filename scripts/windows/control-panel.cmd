@echo off
chcp 65001 >nul
cd /d "%~dp0"
start "TCG Bot Panel" powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%~dp0control-panel.ps1"
