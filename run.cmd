@echo off
chcp 65001 >nul
cd /d "%~dp0"
call mvn -q -DskipTests package
if errorlevel 1 exit /b %errorlevel%
java -Dfile.encoding=UTF-8 -Dstdin.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar target\matchmaking-facade-1.0.0.jar
