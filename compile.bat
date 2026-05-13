@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM WoodJsonJack Kompilierungsskript
REM Benötigt: git_jsonCasted Projekt in D:\git_jsonCasted
REM ============================================================================

set PROJECT_DIR=D:\git_WoodJsonJack
set JSONCASTED_DIR=D:\git_jsonCasted

REM Classpath aufbauen
set CP=%PROJECT_DIR%\src
set CP=%CP%;%JSONCASTED_DIR%\src
set CP=%CP%;%JSONCASTED_DIR%\target\classes

REM Zielverzeichnis für Klassen
set BIN_DIR=%PROJECT_DIR%\bin

REM Bin-Verzeichnis erstellen
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

REM Alle Java-Dateien finden und kompilieren
echo Kompiliere WoodJsonJack mit Abhängigkeit zu git_jsonCasted...
echo Classpath: %CP%

javac -cp "%CP%" -d "%BIN_DIR%" -sourcepath "%PROJECT_DIR%\src" %PROJECT_DIR%\src\de\jare\tree\control\*.java %PROJECT_DIR%\src\de\jare\tree\control\commands\*.java %PROJECT_DIR%\src\de\jare\tree\control\listeners\*.java %PROJECT_DIR%\src\de\jare\tree\ui\*.java %PROJECT_DIR%\src\de\jare\jsoncasted\editor\swing\*.java %PROJECT_DIR%\src\de\jare\jsoncasted\editor\swing\command\*.java 2>&1

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✓ Kompilierung erfolgreich!
    echo Klassen in: %BIN_DIR%
) else (
    echo.
    echo ✗ Kompilierungsfehler - sieh oben
)

endlocal
pause
