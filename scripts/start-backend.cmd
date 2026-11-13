@echo off
setlocal
set "SYNERA_ROOT=%~dp0.."
set "SYNERA_JAVA=java"
for /d %%J in ("%SYNERA_ROOT%\.tools\backend\jdk-25*") do set "SYNERA_JAVA=%%~fJ\bin\java.exe"
"%SYNERA_JAVA%" -XshowSettings:properties -version 2>&1 | findstr /c:"java.specification.version = 25" >nul
if errorlevel 1 (
  echo JDK 25 required. Run python scripts\bootstrap-backend.py or set Java 25 in PATH.
  exit /b 1
)
if not exist "%SYNERA_ROOT%\backend\target-verified\synera-backend-1.0.0-SNAPSHOT.jar" (
  echo Backend JAR not found. Run scripts\backend.cmd verify first.
  exit /b 1
)
pushd "%SYNERA_ROOT%\backend"
if not exist .data\runtime mkdir .data\runtime
copy /b /y target-verified\synera-backend-1.0.0-SNAPSHOT.jar .data\runtime\synera.jar >nul
if errorlevel 1 (
  echo Cannot prepare runtime JAR. Stop the previous backend before restarting.
  popd
  exit /b 1
)
"%SYNERA_JAVA%" -jar .data\runtime\synera.jar --spring.profiles.active=local %*
set "SYNERA_EXIT=%errorlevel%"
popd
exit /b %SYNERA_EXIT%
