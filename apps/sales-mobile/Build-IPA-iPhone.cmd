@echo off
setlocal
cd /d "%~dp0"
if exist "..\..\..\_tools\node-v24.22.1-win-x64\node.exe" set "PATH=%~dp0..\..\..\_tools\node-v24.22.1-win-x64;%PATH%"
echo SYNERA Sales - Build unsigned IPA for Sideloadly. No Apple login required.
call npm run build:ios
if errorlevel 1 goto failed
echo Download the IPA from Expo and sign/install using Sideloadly.
pause
exit /b 0
:failed
echo Setup or build was not completed. Keep this window open to read the message.
pause
exit /b 1
