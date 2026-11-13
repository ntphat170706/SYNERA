@echo off
setlocal
set "SYNERA_ROOT=%~dp0.."
for /d %%J in ("%SYNERA_ROOT%\.tools\backend\jdk-25*") do set "JAVA_HOME=%%~fJ"
if exist "%SYNERA_ROOT%\.tools\backend\apache-maven-3.9.9\bin\mvn.cmd" (
  call "%SYNERA_ROOT%\.tools\backend\apache-maven-3.9.9\bin\mvn.cmd" -f "%SYNERA_ROOT%\backend\pom.xml" "-Dmaven.repo.local=%SYNERA_ROOT%\.tools\m2" -B -ntp %*
) else (
  call mvn -f "%SYNERA_ROOT%\backend\pom.xml" -B -ntp %*
)
exit /b %errorlevel%
