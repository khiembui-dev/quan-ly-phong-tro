@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script (Windows) â€” script-only.
@REM Bootstraps Maven from distributionUrl in .mvn\wrapper\maven-wrapper.properties.
@REM ----------------------------------------------------------------------------
@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "WRAPPER_PROPS=%SCRIPT_DIR%.mvn\wrapper\maven-wrapper.properties"
if not exist "%WRAPPER_PROPS%" (
    echo Missing %WRAPPER_PROPS% 1>&2
    exit /b 1
)

for /f "tokens=2 delims==" %%A in ('findstr /B "distributionUrl=" "%WRAPPER_PROPS%"') do set "DIST_URL=%%A"

for /f "tokens=2 delims=-" %%V in ("apache-maven-x") do rem
@REM Parse version (X.Y.Z) from URL using PowerShell for reliability:
for /f "delims=" %%V in ('powershell -NoProfile -Command "[regex]::Match('!DIST_URL!','apache-maven-([0-9\.]+)-bin\.zip').Groups[1].Value"') do set "DIST_VER=%%V"

if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MVN_DIR=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%DIST_VER%"
set "MVN_BIN=%MVN_DIR%\apache-maven-%DIST_VER%\bin\mvn.cmd"

if not exist "%MVN_BIN%" (
    echo Downloading Maven %DIST_VER% ^(one-time^)â€¦
    if not exist "%MVN_DIR%" mkdir "%MVN_DIR%"
    set "TMP_ZIP=%MVN_DIR%\maven.zip"
    powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing -Uri '!DIST_URL!' -OutFile '!TMP_ZIP!'"
    if errorlevel 1 (
        echo Failed to download Maven. 1>&2
        exit /b 1
    )
    powershell -NoProfile -Command "Expand-Archive -Path '!TMP_ZIP!' -DestinationPath '!MVN_DIR!' -Force"
    if errorlevel 1 (
        echo Failed to extract Maven. 1>&2
        exit /b 1
    )
    del /q "!TMP_ZIP!"
)

call "%MVN_BIN%" %*
exit /b %ERRORLEVEL%
