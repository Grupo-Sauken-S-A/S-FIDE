@echo off
setlocal

REM ============================================================
REM  install.bat - Actualiza la carpeta de distribucion de S-FiDE
REM  con los jars recien compilados, mas los archivos que acompanan
REM  a la distribucion (launchers, Leeme, ejemplos, documentacion).
REM
REM  Requisito previo: correr "mvnw clean install" en la raiz del
REM  proyecto antes de ejecutar este script.
REM
REM  Este .bat vive en la raiz del repositorio fuente. La carpeta
REM  de DISTRIBUCION (DEPLOY) es una carpeta aparte, donde este script
REM  copia los .jar recompilados, los archivos versionados (launchers,
REM  Leeme, doc) y, si VENDOR esta configurado, tambien los runtimes
REM  embebidos (JDK + JavaFX de todas las plataformas) y las carpetas
REM  test/xsd - nada de esto vive en el repositorio git.
REM
REM  VENDOR es una carpeta "maestra" que se arma UNA SOLA VEZ (no cambia
REM  salvo que se actualice la version de Java o JavaFX) con esta forma:
REM    VENDOR\openjdk-23.0.1\windows-x64\...
REM    VENDOR\openjdk-23.0.1\linux-x64\...
REM    VENDOR\openjdk-23.0.1\macos\...
REM    VENDOR\javafx-sdk-23.0.1\windows-x64\...  (idem linux-x64, macos)
REM    VENDOR\test\...   (opcional, documentos de ejemplo)
REM    VENDOR\xsd\...    (opcional, esquemas de ejemplo)
REM  Si deja VENDOR vacio, siga copiando esos archivos a mano como hasta ahora.
REM
REM  Uso: install.bat [carpeta_destino] [carpeta_vendor]
REM ============================================================

REM Carpeta del repositorio fuente (donde esta este mismo .bat)
set SOURCE=%~dp0
if "%SOURCE:~-1%"=="\" set SOURCE=%SOURCE:~0,-1%

REM Carpeta de distribucion por defecto (cambiar si corresponde, o pasarla como parametro)
set DEPLOY=C:\s-fide
if not "%~1"=="" set DEPLOY=%~1

REM Carpeta "maestra" con runtimes embebidos y ejemplos (ver comentario arriba)
set VENDOR=
if not "%~2"=="" set VENDOR=%~2

if not exist "%DEPLOY%" (
    echo La carpeta de distribucion no existe: %DEPLOY%
    echo Cree la carpeta primero, o indique otra ya existente como parametro: install.bat D:\mi-carpeta
    pause
    exit /b 1
)

echo Repositorio fuente : %SOURCE%
echo Carpeta destino     : %DEPLOY%
if not "%VENDOR%"=="" echo Carpeta vendor      : %VENDOR%
echo.

del /q "%DEPLOY%\PKCS12CertificateExtractor.jar" 2>nul
for %%F in ("%SOURCE%\pkcs12_certificate_extractor\target\pkcs12_certificate_extractor-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\PKCS12CertificateExtractor.jar" >nul
if exist "%DEPLOY%\PKCS12CertificateExtractor.jar" (echo   OK  PKCS12CertificateExtractor.jar) else (echo   ADVERTENCIA: falta el jar de pkcs12_certificate_extractor)

del /q "%DEPLOY%\TokenCertificateExtractor.jar" 2>nul
for %%F in ("%SOURCE%\token_certificate_extractor\target\token_certificate_extractor-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\TokenCertificateExtractor.jar" >nul
if exist "%DEPLOY%\TokenCertificateExtractor.jar" (echo   OK  TokenCertificateExtractor.jar) else (echo   ADVERTENCIA: falta el jar de token_certificate_extractor)

del /q "%DEPLOY%\TokenSlotsView.jar" 2>nul
for %%F in ("%SOURCE%\token_slots_view\target\token_slots_view-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\TokenSlotsView.jar" >nul
if exist "%DEPLOY%\TokenSlotsView.jar" (echo   OK  TokenSlotsView.jar) else (echo   ADVERTENCIA: falta el jar de token_slots_view)

del /q "%DEPLOY%\XMLSignerPKCS11.jar" 2>nul
for %%F in ("%SOURCE%\xml_signer_pkcs11\target\xml_signer_pkcs11-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\XMLSignerPKCS11.jar" >nul
if exist "%DEPLOY%\XMLSignerPKCS11.jar" (echo   OK  XMLSignerPKCS11.jar) else (echo   ADVERTENCIA: falta el jar de xml_signer_pkcs11)

del /q "%DEPLOY%\XMLSignerPKCS12.jar" 2>nul
for %%F in ("%SOURCE%\xml_signer_pkcs12\target\xml_signer_pkcs12-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\XMLSignerPKCS12.jar" >nul
if exist "%DEPLOY%\XMLSignerPKCS12.jar" (echo   OK  XMLSignerPKCS12.jar) else (echo   ADVERTENCIA: falta el jar de xml_signer_pkcs12)

del /q "%DEPLOY%\XMLVerifySignatures.jar" 2>nul
for %%F in ("%SOURCE%\xml_verify_signatures\target\xml_verify_signatures-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\XMLVerifySignatures.jar" >nul
if exist "%DEPLOY%\XMLVerifySignatures.jar" (echo   OK  XMLVerifySignatures.jar) else (echo   ADVERTENCIA: falta el jar de xml_verify_signatures)

del /q "%DEPLOY%\XMLVerifyXSDStructure.jar" 2>nul
for %%F in ("%SOURCE%\xml_verify_xsd_structure\target\xml_verify_xsd_structure-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\XMLVerifyXSDStructure.jar" >nul
if exist "%DEPLOY%\XMLVerifyXSDStructure.jar" (echo   OK  XMLVerifyXSDStructure.jar) else (echo   ADVERTENCIA: falta el jar de xml_verify_xsd_structure)

del /q "%DEPLOY%\PDFSignerPKCS11.jar" 2>nul
for %%F in ("%SOURCE%\pdf_signer_pkcs11\target\pdf_signer_pkcs11-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\PDFSignerPKCS11.jar" >nul
if exist "%DEPLOY%\PDFSignerPKCS11.jar" (echo   OK  PDFSignerPKCS11.jar) else (echo   ADVERTENCIA: falta el jar de pdf_signer_pkcs11)

del /q "%DEPLOY%\PDFSignerPKCS12.jar" 2>nul
for %%F in ("%SOURCE%\pdf_signer_pkcs12\target\pdf_signer_pkcs12-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\PDFSignerPKCS12.jar" >nul
if exist "%DEPLOY%\PDFSignerPKCS12.jar" (echo   OK  PDFSignerPKCS12.jar) else (echo   ADVERTENCIA: falta el jar de pdf_signer_pkcs12)

del /q "%DEPLOY%\PDFVerifySignatures.jar" 2>nul
for %%F in ("%SOURCE%\pdf_verify_signatures\target\pdf_verify_signatures-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\PDFVerifySignatures.jar" >nul
if exist "%DEPLOY%\PDFVerifySignatures.jar" (echo   OK  PDFVerifySignatures.jar) else (echo   ADVERTENCIA: falta el jar de pdf_verify_signatures)

del /q "%DEPLOY%\XMLSignerWindowsCSP.jar" 2>nul
for %%F in ("%SOURCE%\xml_signer_windows_csp\target\xml_signer_windows_csp-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\XMLSignerWindowsCSP.jar" >nul
if exist "%DEPLOY%\XMLSignerWindowsCSP.jar" (echo   OK  XMLSignerWindowsCSP.jar) else (echo   ADVERTENCIA: falta el jar de xml_signer_windows_csp)

del /q "%DEPLOY%\PDFSignerWindowsCSP.jar" 2>nul
for %%F in ("%SOURCE%\pdf_signer_windows_csp\target\pdf_signer_windows_csp-*-jar-with-dependencies.jar") do copy /y "%%F" "%DEPLOY%\PDFSignerWindowsCSP.jar" >nul
if exist "%DEPLOY%\PDFSignerWindowsCSP.jar" (echo   OK  PDFSignerWindowsCSP.jar) else (echo   ADVERTENCIA: falta el jar de pdf_signer_windows_csp)

REM --- GUI: el artefacto shaded se llama s_fide_gui-<version>.jar (sin sufijo jar-with-dependencies) ---
del /q "%DEPLOY%\SFide-GUI.jar" 2>nul
for %%F in ("%SOURCE%\s_fide_gui\target\s_fide_gui-*.jar") do copy /y "%%F" "%DEPLOY%\SFide-GUI.jar" >nul
if exist "%DEPLOY%\SFide-GUI.jar" (echo   OK  SFide-GUI.jar) else (echo   ADVERTENCIA: falta el jar de s_fide_gui)

REM --- Archivos de distribucion que acompanan a los jars ---
copy /y "%SOURCE%\SFide-GUI.bat"                  "%DEPLOY%\" >nul
copy /y "%SOURCE%\SFide-GUI.sh"                   "%DEPLOY%\" >nul
copy /y "%SOURCE%\sfide-defaults.demo.properties" "%DEPLOY%\" >nul
copy /y "%SOURCE%\Leeme.txt"                      "%DEPLOY%\" >nul
if exist "%SOURCE%\doc"  xcopy /y /e /i /q "%SOURCE%\doc"  "%DEPLOY%\doc\"  >nul

REM --- Runtimes embebidos y ejemplos (no viven en el repo git; ver VENDOR arriba) ---
if not "%VENDOR%"=="" (
    if exist "%VENDOR%\openjdk-23.0.1" (
        xcopy /y /e /i /q "%VENDOR%\openjdk-23.0.1"    "%DEPLOY%\openjdk-23.0.1\"    >nul
        xcopy /y /e /i /q "%VENDOR%\javafx-sdk-23.0.1" "%DEPLOY%\javafx-sdk-23.0.1\" >nul
        echo   OK  runtimes embebidos (JDK + JavaFX, todas las plataformas^)
    ) else (
        echo   ADVERTENCIA: no se encontro "%VENDOR%\openjdk-23.0.1" - runtimes no copiados
    )
    if exist "%VENDOR%\test" xcopy /y /e /i /q "%VENDOR%\test" "%DEPLOY%\test\" >nul
    if exist "%VENDOR%\xsd"  xcopy /y /e /i /q "%VENDOR%\xsd"  "%DEPLOY%\xsd\"  >nul
) else (
    if not exist "%DEPLOY%\openjdk-23.0.1" (
        echo   Nota: no se indico carpeta VENDOR y no hay runtime embebido en el destino.
        echo         Copie a mano "openjdk-23.0.1" y "javafx-sdk-23.0.1" a %DEPLOY%, o
        echo         vuelva a correr: install.bat "%DEPLOY%" carpeta_vendor
    )
    if exist "%SOURCE%\test" xcopy /y /e /i /q "%SOURCE%\test" "%DEPLOY%\test\" >nul
    if exist "%SOURCE%\xsd"  xcopy /y /e /i /q "%SOURCE%\xsd"  "%DEPLOY%\xsd\"  >nul
)

echo.
echo Distribucion actualizada en: %DEPLOY%
pause
endlocal
