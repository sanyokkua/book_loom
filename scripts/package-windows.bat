@echo off
rem
rem Windows packaging: a portable app-image ONLY.
rem
rem There is deliberately no .msi and no .exe installer. Those need the WiX toolchain and per-machine install
rem plumbing this project avoids on purpose (03_PACKAGING_JPACKAGE.md#per-os-matrix): the user unzips the folder and
rem runs BookLoom.exe. The portable image is the universal fallback on every OS anyway.
rem
rem Requires Liberica 25 "Full" (jdk+fx). A plain JDK on Windows lacks the JavaFX jmods jpackage needs, and the fix
rem is always the JDK selection — never hand-copying jmods (EC-REL-4).
rem
rem Mirrors scripts/jpackage-common.sh; the two must be changed together.
rem
rem Usage: scripts\package-windows.bat

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"

set "APP_NAME=BookLoom"
set "APP_VENDOR=BookLoom"
set "APP_DESCRIPTION=Local-first offline book translation"
set "APP_COPYRIGHT=MIT licensed"

rem The bootstrap package, not ua.bookloom.app: it is the scope of the bootstrap-no-static-logger ArchUnit rule.
set "MAIN_CLASS=ua.bookloom.app.bootstrap.Launcher"
set "MAIN_JAR=app.jar"

rem modules\app\build\dist\libs, per ADR-0021, which supersedes the frozen spec's app\build\dist\libs.
set "INPUT_DIR=%REPO_ROOT%\modules\app\build\dist\libs"
set "OUTPUT_DIR=%REPO_ROOT%\build\package"
set "ICON=%REPO_ROOT%\docs\specification\assets\icon\dist\windows\BookLoom.ico"

if "%APP_VERSION%"=="" set "APP_VERSION=dev"

rem jpackage rejects any --app-version that is not strictly numeric, and also rejects a leading zero. A tagged
rem pre-release therefore contributes only its numeric part to the image while the full string names the artifact
rem (EC-REL-2); an un-tagged build gets the placeholder 1.0.0. That placeholder is OS metadata only — the version
rem the running application reports comes from the version resource and stays "dev" (EC-REL-7).
set "NUMERIC_VERSION=%APP_VERSION%"
for /f "tokens=1 delims=-+" %%V in ("%APP_VERSION%") do set "NUMERIC_VERSION=%%V"
echo %NUMERIC_VERSION%| findstr /r "^[1-9][0-9.]*$" >nul || set "NUMERIC_VERSION=1.0.0"

where jpackage >nul 2>&1
if errorlevel 1 (
    echo error: jpackage is not on PATH. Use Liberica 25 "Full" ^(jdk+fx^) on Windows. 1>&2
    exit /b 1
)

if not exist "%INPUT_DIR%\%MAIN_JAR%" (
    echo error: %INPUT_DIR%\%MAIN_JAR% not found. Run gradlew :app:collectDist first. 1>&2
    exit /b 1
)

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo ==^> %APP_NAME% %APP_VERSION% ^(jpackage --app-version %NUMERIC_VERSION%^)

rem The production build stamp is not optional: without it the launched image resolves the -Dev folder and works
rem perfectly against the wrong data (DD-39).
jpackage ^
    --name "%APP_NAME%" ^
    --app-version "%NUMERIC_VERSION%" ^
    --vendor "%APP_VENDOR%" ^
    --description "%APP_DESCRIPTION%" ^
    --copyright "%APP_COPYRIGHT%" ^
    --input "%INPUT_DIR%" ^
    --main-jar "%MAIN_JAR%" ^
    --main-class "%MAIN_CLASS%" ^
    --dest "%OUTPUT_DIR%" ^
    --icon "%ICON%" ^
    --type app-image ^
    --java-options "-Dbookloom.env=prod" ^
    --jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6"

if errorlevel 1 exit /b 1

if not exist "%OUTPUT_DIR%\%APP_NAME%\%APP_NAME%.exe" (
    echo error: jpackage reported success but %OUTPUT_DIR%\%APP_NAME%\%APP_NAME%.exe does not exist. 1>&2
    exit /b 1
)

echo ==^> done
dir /b "%OUTPUT_DIR%"
endlocal
