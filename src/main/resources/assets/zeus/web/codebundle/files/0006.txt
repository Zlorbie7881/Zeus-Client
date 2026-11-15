@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo Building in: %CD%
echo.

set "JDK="
call :find

if not defined JDK (
  echo Java 21 was not found on this PC. Minecraft 1.21.11 mods need it to be built.
  echo.
  echo Install it automatically now? This uses Windows Package Manager and may ask for permission.
  set "ANS="
  set /p "ANS=Type Y and press Enter to install, or just press Enter to skip: "
  if /i "!ANS!"=="Y" (
    winget install -e --id EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements
    call :find
  )
)

if not defined JDK (
  echo.
  echo Still no Java 21. Install it by hand, then run this file again:
  echo   1. Open https://adoptium.net/temurin/releases/?version=21
  echo   2. Pick Windows, x64, JDK, .msi and run it
  echo   3. On the "Custom Setup" page turn on "Set JAVA_HOME variable"
  echo.
  pause
  exit /b 1
)

set "JAVA_HOME=!JDK!"
set "PATH=!JAVA_HOME!\bin;!PATH!"
set "GRADLE_USER_HOME=C:\gradle-home"
echo Using Java 21 from: !JAVA_HOME!
"!JAVA_HOME!\bin\java" -version
echo.

call "%~dp0gradlew.bat" build
echo.
if exist build\libs (
  echo Done. Opening the folder with your mod. Use the jar WITHOUT -sources in its name.
  start "" "%~dp0build\libs"
) else (
  echo Build did not finish. Scroll up for the lines with "error:" or "What went wrong:".
)
pause
exit /b 0

:find
for %%P in ("%ProgramFiles%\Eclipse Adoptium" "%LOCALAPPDATA%\Programs\Eclipse Adoptium" "%ProgramFiles%\Java" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Amazon Corretto" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft") do (
  for /d %%D in ("%%~P\jdk-21*" "%%~P\jdk21*" "%%~P\*jdk*21*") do (
    if exist "%%~D\bin\javac.exe" set "JDK=%%~D"
  )
)
if not defined JDK if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
  "%JAVA_HOME%\bin\java" -version 2>&1 | findstr /c:"21." >nul && set "JDK=%JAVA_HOME%"
)
goto :eof
