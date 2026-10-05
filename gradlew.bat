@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  echo Gradle not found. Install Gradle 8.7+ and JDK 17, then re-run.
  exit /b 1
)
gradle %*
