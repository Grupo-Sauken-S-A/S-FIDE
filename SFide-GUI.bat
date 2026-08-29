@echo off

REM Carpeta en donde se descomprime e instala S-FiDE (cambiar si es necesario)
set SFIDE=C:\S-FiDE

REM Unidad en donde se descomprime e instala S-FiDE (cambiar si es necesario)
C:

REM Proceso
cd %SFIDE%/

set JAVA_HOME=%SFIDE%/\openjdk-23.0.1\windows-x64
set JAVA_FX=%SFIDE%/\javafx-sdk-23.0.1\windows-x64

set PATH=%JAVA_HOME%\bin;%PATH%

%JAVA_HOME%\bin\java --module-path %JAVA_FX%\lib --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar SFide-GUI.jar