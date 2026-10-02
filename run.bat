@echo off
setlocal

echo ============================================
echo  TCSMS - Build and Run
echo ============================================

cd /d "%~dp0"

if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        if not "%%A"=="" if not "%%A:~0,1%%"=="#" set "%%A=%%B"
    )
)

set SRC_DIR=src\main\java
set RES_DIR=src\main\resources
set OUT_DIR=target\classes
set LIB_DIR=lib

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
if not exist "%LIB_DIR%" mkdir "%LIB_DIR%"

if not exist "%LIB_DIR%\h2-2.2.224.jar" (
    echo Downloading H2 Database...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar' -OutFile '%LIB_DIR%\h2-2.2.224.jar'"
)

if not exist "%LIB_DIR%\mysql-connector-j-8.3.0.jar" (
    echo Downloading MySQL Connector...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar' -OutFile '%LIB_DIR%\mysql-connector-j-8.3.0.jar'"
)

if not exist "%LIB_DIR%\jbcrypt-0.4.jar" (
    echo Downloading jBCrypt...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar' -OutFile '%LIB_DIR%\jbcrypt-0.4.jar'"
)

echo.
echo Compiling Java sources...
set CLASSPATH=%LIB_DIR%\h2-2.2.224.jar;%LIB_DIR%\mysql-connector-j-8.3.0.jar;%LIB_DIR%\jbcrypt-0.4.jar

dir /s /b "%SRC_DIR%\*.java" > sources.txt
javac -encoding UTF-8 -cp "%CLASSPATH%" -d "%OUT_DIR%" @sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation FAILED!
    pause
    exit /b 1
)

echo Compilation successful!
echo.

xcopy /Y /Q "%RES_DIR%\*.*" "%OUT_DIR%\" >nul 2>&1

chcp 65001 >nul
echo Running TCSMS Application...
echo ============================================
echo.
java -Dfile.encoding=UTF-8 -cp "%OUT_DIR%;%CLASSPATH%" com.amdocs.telecom.main.MainApplication

pause
