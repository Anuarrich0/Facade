@echo off
setlocal
for %%I in ("%~dp0.") do set "PROJECT_DIR=%%~sI"
for %%I in ("%USERPROFILE%\.m2\repository") do set "MAVEN_REPO=%%~sI"
call mvn "-Dmaven.repo.local=%MAVEN_REPO%" -f "%PROJECT_DIR%\pom.xml" clean verify %*
exit /b %errorlevel%
