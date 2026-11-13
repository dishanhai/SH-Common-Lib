@echo off
setlocal

set "build_result=1"
pushd "%~dp0forge" || goto directory_error
echo Building Forge...
call gradlew.bat build %*
if errorlevel 1 (
    popd
    echo Forge build failed.
    goto finish
)
popd

pushd "%~dp0neoforge-1.21.1" || goto directory_error
echo Building NeoForge...
call gradlew.bat build %*
if errorlevel 1 (
    popd
    echo NeoForge build failed.
    goto finish
)
popd

echo Building NeoForge 26.1.2...
call "%~dp0neoforge-1.21.1\gradlew.bat" -p "%~dp0neoforge-26.1.2" build %*
if errorlevel 1 (
    echo NeoForge 26.1.2 build failed.
    goto finish
)

echo All builds succeeded. JARs are in each target's build\libs directory.
set "build_result=0"
goto finish

:directory_error
echo Could not enter the build directory.

:finish
pause
exit /b %build_result%
