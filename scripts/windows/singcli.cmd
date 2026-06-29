@echo off
setlocal

rem Windows launcher for singcli.
rem Put this file in a directory included in PATH.
rem By default it runs singcli.jar next to this script.
rem You can override it by setting SINGCLI_JAR.

fltmc >nul 2>nul
if errorlevel 1 (
    set "SINGCLI_LAUNCHER=%~f0"
    set "SINGCLI_ARGS=%*"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "if ([string]::IsNullOrWhiteSpace($env:SINGCLI_ARGS)) { Start-Process -FilePath $env:SINGCLI_LAUNCHER -Verb RunAs } else { Start-Process -FilePath $env:SINGCLI_LAUNCHER -ArgumentList $env:SINGCLI_ARGS -Verb RunAs }"
    if errorlevel 1 exit /b 1
    exit /b 0
)

if not defined SINGCLI_JAR (
    set "SINGCLI_JAR=%~dp0singcli.jar"
)

where java >nul 2>nul
if errorlevel 1 (
    echo singcli: java was not found in PATH 1>&2
    exit /b 127
)

if not exist "%SINGCLI_JAR%" (
    echo singcli: jar file was not found: %SINGCLI_JAR% 1>&2
    exit /b 1
)

java -jar "%SINGCLI_JAR%" %*
exit /b %errorlevel%
