@echo off
cd /d "%~dp0"

set JAR_PATH=dist

if not exist "%JAR_PATH%\Heidelberg.jar" (
    echo Fehler: Heidelberg.jar nicht gefunden! Zuerst in NetBeans "Clean and Build" ausfuehren.
    timeout /t 5
    exit /b 1
)

start "" java -Xmx4g -jar "%JAR_PATH%\Heidelberg.jar"
exit
