@echo off
rem Builds the GhidraThemes extension. Output goes to build.log; the zip lands in dist\.
setlocal
cd /d "%~dp0"
if "%GHIDRA_INSTALL_DIR%"=="" set "GHIDRA_INSTALL_DIR=D:\ghidra_12.1.4_PUBLIC"
if "%GRADLE_BIN%"=="" set "GRADLE_BIN=D:\gradle-9.5.0\bin"

> build.log (
  echo === GhidraThemes build %DATE% %TIME%
  echo GHIDRA_INSTALL_DIR=%GHIDRA_INSTALL_DIR%
  echo JAVA_HOME=%JAVA_HOME%
  where java 2>&1
)
java -version >> build.log 2>&1

call "%GRADLE_BIN%\gradle.bat" --no-daemon -PGHIDRA_INSTALL_DIR="%GHIDRA_INSTALL_DIR%" >> build.log 2>&1
set RC=%ERRORLEVEL%
echo === EXIT CODE %RC% >> build.log
dir /b dist >> build.log 2>&1
echo.
echo Build finished with exit code %RC%. See build.log.
timeout /t 5 >nul
exit /b %RC%
