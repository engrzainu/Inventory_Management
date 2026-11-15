@echo off
rem Starts the Inventory Management system on this computer. No internet is needed.
setlocal
cd /d "%~dp0"
title Inventory Management

set "JAR="
for %%f in ("..\target\inventory-management-*.jar" "inventory-management-*.jar") do if exist "%%~f" set "JAR=%%~f"
if not defined JAR goto nojar

where java >nul 2>nul
if errorlevel 1 goto nojava
java -version 2>&1 | findstr /r /c:"version .2[5-9]" >nul
if errorlevel 1 goto oldjava

if not exist inventory.env goto noenv
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("inventory.env") do set "%%a=%%b"

echo.
echo  Inventory Management is starting. Keep this window open while you use it.
echo  On this computer open:   http://localhost:8080
echo  From phones/other computers on the same Wi-Fi use one of these addresses with :8080
ipconfig | findstr /c:"IPv4"
echo.
start "" /min cmd /c "timeout /t 15 >nul & start http://localhost:8080"
java -jar "%JAR%"
echo.
echo  The program has stopped. Read the messages above if it was not expected.
pause
exit /b 0

:nojar
echo Could not find the program file inventory-management-*.jar
echo Open a terminal in the project folder and run:  mvn clean package -DskipTests
pause
exit /b 1

:nojava
echo Java is not installed. Install JDK 25 (for example from adoptium.net) and run this again.
pause
exit /b 1

:oldjava
echo Java 25 or newer is required. Install JDK 25 and run this again.
java -version
pause
exit /b 1

:noenv
copy inventory.env.example inventory.env >nul
echo A settings file was created: inventory.env
echo Set your MySQL password and admin password in it, save, then run this again.
notepad inventory.env
pause
exit /b 0
