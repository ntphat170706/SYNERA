@echo off
setlocal
cd /d "%~dp0"
set "SYNERA_NODE=..\..\..\_tools\node-v24.22.1-win-x64\node.exe"
if exist "%SYNERA_NODE%" (
  "%SYNERA_NODE%" scripts\iphone.mjs
) else (
  node scripts\iphone.mjs
)
pause
