# Historial de cambios

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato sigue las convenciones de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/), y el versionado sigue [SemVer](https://semver.org/lang/es/).

## [1.1.0-beta.1] — 2026-08 (en QA)

### Agregado
- Compatibilidad ampliada de tokens PKCS#11: mecanismo de hash externo (`CKM_RSA_PKCS`) para tokens que no exponen el mecanismo combinado habitual (p. ej. Feitian ePass2003).
- Autodetección de marca/modelo de token por nombre de driver, y comando `-listar-drivers` en los módulos PKCS#11.
- Dos módulos nuevos, exclusivos de Windows: `xml_signer_windows_csp` y `pdf_signer_windows_csp`, que firman contra el almacén de certificados de Windows (CSP/KSP) como alternativa a PKCS#11, con comando `-listar-certificados`.
- Especialización completa de comercio exterior ALADI/MERCOSUR: estructura de firma en dos etapas para Certificados de Origen Digital (`COD`/`CODEH`) y Declaraciones Juradas de Origen (`DJO`/`DJOEH`), extracción de fecha de revocación específica por elemento (con y sin conversión de huso horario según corresponda), y tres reglas de firma obligatorias (no volver a firmar un elemento ya firmado, no firmar `CODEH`/`DJOEH` sin firma previa en `COD`/`DJO`, no firmar el documento completo en un XML de comercio exterior).
- Mensajes informativos al firmar un documento de comercio exterior, indicando el tipo de documento y qué elemento se firmó.
- Dominio espejo automático (`cod.certificadoorigen.com.ar`) en `XMLVerifyXSDStructure` cuando el dominio oficial de ALADI (`codaladi.org`) está inoperativo.
- Manual Técnico de Integración completo (`doc/manual-tecnico-integracion.md` y `.html`), con arquitectura, catálogo de aplicaciones, mecanismos de firma, validación de revocación, especialización ALADI/MERCOSUR, y guía de migración desde 1.0.0.
- Documentación de proyecto: `AGENTS.md`, `CONTRIBUTING.md`, `README.md`, `LICENSE`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, este `CHANGELOG.md`.
- Iconos de aplicación propios para S-FiDE GUI, y versión visible en el título de la ventana.

### Cambiado
- Dependencias criptográficas actualizadas por alertas de seguridad: BouncyCastle 1.85, iText 8.0.5, Apache Santuario (xmlsec) 4.0.4, SLF4J 2.0.17, Logback 1.5.18.
- Vocabulario de comandos especiales de diagnóstico (`-version`, `-ayuda`, `-licencia`, `-v`, `-h`, `--version`, `--help`, `--license`) unificado en los 12 módulos de línea de comandos — cambio aditivo, ningún alias existente en 1.0.0 se quitó.
- `install.bat` modernizado: autolocalización, soporte de carpeta "vendor" para armar una distribución completa en un solo paso. `SFide-GUI.bat`/`.sh` autolocalizables (no dependen de una letra de unidad fija; corren desde un pendrive).
- `PDFSignerPKCS12`: al bloquear el documento (`-l true`) ahora se aplica siempre la certificación DocMDP junto con el cifrado, sin importar si la firma es visible o invisible (antes solo se aplicaba con firma visible).
- `XMLVerifySignatures`: la validación de revocación de la firma sobre `CODEH` ahora usa el país de la Entidad Habilitada (`EHCountry`) en vez del país del exportador (`ExporterCountry`).
- Mensajes de revocación de `XMLVerifySignatures` aclarados: se distingue explícitamente el estado de integridad criptográfica del estado final de la firma, y se explica en texto plano por qué un certificado revocado invalida una firma.
- `doc/guia-uso-sfide.md` fusionado dentro del manual técnico de integración y eliminado como documento separado.

### Corregido
- Fallo real de firma XML contra hardware real (`Mechanism DOM not available from SFideXMLDSigFallback`) y una recursión infinita relacionada, encontrados en QA con un token SafeNet 5110+ L3 real.
- Stack trace de Java expuesto en `XMLVerifySignatures` al fallar la búsqueda del certificado emisor.
- `PDFSignerWindowsCSP` no validaba firmas preexistentes ni rechazaba PDFs ya encriptados antes de firmar; ahora lo hace, igual que los otros dos firmadores de PDF.
- Regresión de iText 8.0.5 en `PDFVerifySignatures` (cast inseguro de un arreglo de `Certificate`).
- `XMLVerifyXSDStructure` aceptaba como esquema válido el contenido de una redirección HTTP→HTTPS entre protocolos que Java no sigue automáticamente; ahora detecta que no es un XSD válido y reintenta.

### Eliminado
- `doc/guia-uso-sfide.pdf` (queda solo la guía fusionada en el manual técnico).
- Descriptor de assembly huérfano de `s_fide_gui` (hardcodeaba un nombre de jar versionado, violando la política de nombres de jar de distribución).

## [1.0.0] — 2024-12

### Agregado
- Primer release estable: 10 módulos (extracción de certificados desde token o PKCS#12, firma y verificación de XML/PDF vía PKCS#11 y PKCS#12, validación de estructura XSD) más la interfaz gráfica JavaFX.

[1.1.0-beta.1]: https://github.com/Grupo-Sauken-S-A/S-FIDE/compare/v1.0.0...v1.1.0-beta.1
[1.0.0]: https://github.com/Grupo-Sauken-S-A/S-FIDE/releases/tag/v1.0.0
