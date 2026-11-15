@echo off
rem Saves a copy of the whole database into the backups folder. Run it every day.
setlocal
cd /d "%~dp0"
if not exist inventory.env (echo inventory.env not found & pause & exit /b 1)
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("inventory.env") do set "%%a=%%b"
if not defined MYSQLDUMP set "MYSQLDUMP=mysqldump"
if not defined DB_USERNAME set "DB_USERNAME=root"
set "MYSQL_PWD=%DB_PASSWORD%"
for /f %%i in ('powershell -nologo -command "Get-Date -Format yyyyMMdd-HHmm"') do set "STAMP=%%i"
if not exist backups mkdir backups
"%MYSQLDUMP%" -u "%DB_USERNAME%" --single-transaction --routines inventory_db > "backups\inventory-%STAMP%.sql"
if errorlevel 1 (
  echo Backup FAILED. Check that MySQL is running and that mysqldump is found. See inventory.env.
  del "backups\inventory-%STAMP%.sql" >nul 2>nul
  pause & exit /b 1
)
echo Backup saved: %~dp0backups\inventory-%STAMP%.sql
echo Copy it to a flash drive or cloud storage too, in case this computer fails.
pause
