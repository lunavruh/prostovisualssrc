@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean build
set EXITCODE=%ERRORLEVEL%
echo.
if %EXITCODE%==0 (
  echo Build complete. JAR: build\libs\
) else (
  echo Build failed with exit code %EXITCODE%.
)
pause
exit /b %EXITCODE%
