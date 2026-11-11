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

pushd "%~dp0neoforge" || goto directory_error
echo Building NeoForge...
call gradlew.bat build %*
if errorlevel 1 (
    popd
    echo NeoForge build failed.
    goto finish
)
popd

echo Both builds succeeded. JARs are in forge\build\libs and neoforge\build\libs.
set "build_result=0"
goto finish

:directory_error
echo Could not enter the build directory.

:finish
pause
exit /b %build_result%
