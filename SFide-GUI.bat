@echo off
setlocal

REM Carpeta donde vive S-FiDE: se detecta automaticamente a partir de la
REM ubicacion de este mismo archivo, para que funcione sin editar nada sin
REM importar la letra de unidad (por ejemplo, al correr desde un pendrive).
set SFIDE=%~dp0
if "%SFIDE:~-1%"=="\" set SFIDE=%SFIDE:~0,-1%

REM Proceso ("cd /d" cambia tambien de unidad, no solo de carpeta)
cd /d "%SFIDE%"

REM Evita caracteres raros por acentos en la consola de Windows (ver Leeme.txt)
chcp 65001 >nul

set JAVA_HOME=%SFIDE%\openjdk-23.0.1\windows-x64
set JAVA_FX=%SFIDE%\javafx-sdk-23.0.1\windows-x64

set PATH=%JAVA_HOME%\bin;%PATH%

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo No se encontro Java en: %JAVA_HOME%
    echo La carpeta de S-FiDE parece incompleta o movida.
    pause
    exit /b 1
)

if not exist "%JAVA_FX%\lib" (
    echo No se encontro JavaFX en: %JAVA_FX%
    echo La carpeta de S-FiDE parece incompleta o movida.
    pause
    exit /b 1
)

if not exist "%SFIDE%\SFide-GUI.jar" (
    echo No se encontro SFide-GUI.jar en: %SFIDE%
    pause
    exit /b 1
)

%JAVA_HOME%\bin\java --module-path %JAVA_FX%\lib --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar SFide-GUI.jar

if errorlevel 1 (
    echo.
    echo S-FiDE finalizo con un error. Revise los mensajes anteriores.
    pause
)

endlocal
