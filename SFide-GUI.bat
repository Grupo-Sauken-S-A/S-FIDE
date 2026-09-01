@echo off
setlocal

REM Carpeta donde vive S-FiDE: se detecta automaticamente a partir de la
REM ubicacion de este mismo archivo, para que funcione sin editar nada sin
REM importar la letra de unidad (por ejemplo, al correr desde un pendrive).
REM IMPORTANTE: %SFIDE% se deja SIEMPRE terminado en "\" (incluido el caso
REM "raiz de unidad", p. ej. "C:\") y todas las rutas de abajo se arman sin
REM agregar otra "\" despues. Recortar esa barra puede parecer prolijo, pero
REM en la raiz de una unidad convierte "C:\" en "C:" -que en Windows NO es
REM lo mismo: "cd /d C:" no garantiza ir a la raiz, sino al ultimo directorio
REM recordado para esa unidad- y el proceso podria arrancar en un directorio
REM de trabajo distinto al de S-FiDE, con lo que sfide-defaults.properties
REM se leeria/guardaria en el lugar equivocado.
set SFIDE=%~dp0

REM Proceso ("cd /d" cambia tambien de unidad, no solo de carpeta)
cd /d "%SFIDE%"

REM Evita caracteres raros por acentos en la consola de Windows (ver Leeme.txt)
chcp 65001 >nul

set JAVA_HOME=%SFIDE%openjdk-23.0.1\windows-x64
set JAVA_FX=%SFIDE%javafx-sdk-23.0.1\windows-x64

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

if not exist "%SFIDE%SFide-GUI.jar" (
    echo No se encontro SFide-GUI.jar en: %SFIDE%
    pause
    exit /b 1
)

REM Aviso amistoso (no bloquea) si S-FiDE se descomprimio directo en la raiz
REM de una unidad en vez de dentro de una carpeta propia -algo que paso con
REM varios usuarios del ZIP de distribucion y no es una buena practica,
REM aunque a partir de esta version ya funciona correctamente igual.
if "%SFIDE:~1,2%"==":\" if "%SFIDE:~3%"=="" (
    echo NOTA: S-FiDE esta corriendo directo desde la raiz de la unidad %SFIDE%
    echo Se recomienda moverlo a una carpeta propia, por ejemplo %SFIDE%S-FiDE\
    echo para no mezclarlo con otros archivos de la unidad. No es obligatorio,
    echo el programa funciona igual, pero es mas prolijo y ordenado.
    echo.
)

"%JAVA_HOME%\bin\java" --module-path "%JAVA_FX%\lib" --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "%SFIDE%SFide-GUI.jar"

if errorlevel 1 (
    echo.
    echo S-FiDE finalizo con un error. Revise los mensajes anteriores.
    pause
)

endlocal
