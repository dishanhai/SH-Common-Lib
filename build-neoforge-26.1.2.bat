@echo off
setlocal

echo Building NeoForge 26.1.2...
call "%~dp0neoforge-1.21.1\gradlew.bat" -p "%~dp0neoforge-26.1.2" build %*
set "build_result=%errorlevel%"

if not "%build_result%"=="0" (
    echo NeoForge 26.1.2 build failed.
) else (
    echo Build succeeded. JARs are in neoforge-26.1.2\build\libs.
)

pause
exit /b %build_result%
