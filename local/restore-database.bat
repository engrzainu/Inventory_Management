@echo off
rem Restores a backup made by backup-database.bat.  Usage: restore-database.bat backups\inventory-YYYYMMDD-HHMM.sql
rem WARNING: this replaces the current data. Stop the program first.
setlocal
cd /d "%~dp0"
if "%~1"=="" (echo Usage: restore-database.bat backups\your-backup.sql & pause & exit /b 1)
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("inventory.env") do set "%%a=%%b"
if not defined MYSQL set "MYSQL=mysql"
if not defined DB_USERNAME set "DB_USERNAME=root"
set "MYSQL_PWD=%DB_PASSWORD%"
set /p OK=This REPLACES all current data with the backup. Type YES to continue: 
if /i not "%OK%"=="YES" (echo Cancelled. & exit /b 1)
"%MYSQL%" -u "%DB_USERNAME%" -e "DROP DATABASE IF EXISTS inventory_db; CREATE DATABASE inventory_db;"
"%MYSQL%" -u "%DB_USERNAME%" inventory_db < "%~1"
if errorlevel 1 (echo Restore FAILED. & pause & exit /b 1)
echo Restore finished. Start the program again.
pause
