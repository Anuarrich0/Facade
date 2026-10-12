@echo off
cd /d "%~dp0"
call mvn -q -DskipTests package
if errorlevel 1 exit /b %errorlevel%
java -jar target\matchmaking-facade-1.0.0.jar
