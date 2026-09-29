@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo  TCSMS - Database Visual Browser
echo ============================================
echo.
echo Opening H2 Database Console in your browser...
echo.
echo Connection settings (auto-filled):
echo   JDBC URL : jdbc:h2:./data/tcsms_db;AUTO_SERVER=TRUE
echo   User     : sa
echo   Password : (leave blank)
echo.
echo Press Ctrl+C in this window to stop the console.
echo ============================================
echo.

java -cp lib/h2-2.2.224.jar org.h2.tools.Console -web -browser -url "jdbc:h2:./data/tcsms_db;AUTO_SERVER=TRUE" -user sa -password ""

pause
