@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean prepareLoaderClient
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  pause
  exit /b 1
)
echo.
echo READY: launcher-dist\prostovisuals-client.jar
echo Push this file to GitHub, then the loader will update users automatically.
pause
