# S-FiDE — Sistema de Firma Digital Extendido

[![Licencia: GPL v2 o posterior](https://img.shields.io/badge/Licencia-GPLv2%20o%20posterior-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://openjdk.org/)

Suite de programas Java independientes, de Grupo Sauken S.A., para firmar y verificar firmas digitales en documentos XML y PDF en Argentina, y para extraer/inspeccionar certificados digitales desde tokens criptográficos (PKCS#11), archivos PKCS#12 o el almacén de certificados de Windows. Trabaja con certificados de firma digital emitidos bajo la Ley 25.506 de Firma Digital argentina —la misma normativa detrás de los certificados de la Autoridad Certificante de la ONTI (AC-ONTI) y de trámites ante organismos como AFIP/ARCA— y con la especialización de comercio exterior ALADI/MERCOSUR. Incluye una interfaz gráfica JavaFX opcional (`s_fide_gui`) que orquesta esos mismos programas.

## Filosofía

Cada capacidad es un **programa independiente**, invocable por línea de comandos, que cualquier aplicación externa puede ejecutar como proceso hijo sin integrar ninguna librería Java. No existe una "librería S-FiDE" para enlazar — se distribuye como un conjunto de archivos `.jar` ejecutables. El contrato es siempre el mismo: `java -jar Modulo.jar <argumentos>`, código de salida `0` en éxito y `1` en error, salida normal por `stdout`, errores por `stderr` como texto simple en español — nunca un stack trace de Java. La GUI no es un atajo privilegiado: invoca los mismos jars, con los mismos argumentos, que usaría un integrador externo.

Detalle completo de esta filosofía y de cada módulo: **[Manual Técnico de Integración](doc/manual-tecnico-integracion.md)** ([versión HTML](doc/manual-tecnico-integracion.html)).

## Módulos

| Módulo | Qué hace |
|---|---|
| `token_slots_view` | Lista slots y certificados de un token PKCS#11 |
| `token_certificate_extractor` | Extrae un certificado de un token PKCS#11 a `.pem` (en la carpeta personal del usuario) e informa su estado de revocación |
| `pkcs12_certificate_extractor` | Extrae un certificado de un archivo PKCS#12 a `.pem` (en la carpeta personal del usuario) e informa su estado de revocación |
| `xml_signer_pkcs11` | Firma XML con token PKCS#11 |
| `xml_signer_pkcs12` | Firma XML con archivo PKCS#12 |
| `xml_signer_windows_csp` | Firma XML con el almacén de certificados de Windows (solo Windows) |
| `xml_verify_signatures` | Verifica firmas digitales de un XML |
| `xml_verify_xsd_structure` | Valida un XML contra su esquema XSD y verifica sus firmas |
| `pdf_signer_pkcs11` | Firma PDF con token PKCS#11 |
| `pdf_signer_pkcs12` | Firma PDF con archivo PKCS#12 |
| `pdf_signer_windows_csp` | Firma PDF con el almacén de certificados de Windows (solo Windows) |
| `pdf_verify_signatures` | Verifica firmas digitales de un PDF |
| `windows_certificate_store_view` | Lista los certificados del almacén de Windows (solo Windows) |
| `s_fide_updater` | Aplica una actualización de S-FiDE descargada desde GitHub, con respaldo y reversión automática |
| `s_fide_gui` | Interfaz gráfica JavaFX que invoca los módulos anteriores (y busca/instala actualizaciones desde Ayuda) |

S-FiDE también incluye soporte especializado para comercio exterior ALADI/MERCOSUR (Certificados de Origen Digital y Declaraciones Juradas de Origen) — ver la [sección 10 del manual técnico](doc/manual-tecnico-integracion.md#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh).

## Compilar

Requiere JDK 23 (el repositorio incluye Maven Wrapper, no hace falta tener Maven instalado aparte).

```bash
git clone https://github.com/Grupo-Sauken-S-A/S-FIDE.git
cd S-FIDE
./mvnw clean install
```

Cada uno de los 15 módulos genera su jar en su propia carpeta `target/`.

## Distribución portable

`install.bat` arma una carpeta de distribución autocontenida (jars con nombre sin versión, runtime de Java y JavaFX embebidos si se indica una carpeta "vendor") que puede ejecutarse desde cualquier ubicación, incluido un medio removible, en un equipo sin Java preinstalado — ver `Leeme.txt` y la [sección 12 del manual técnico](doc/manual-tecnico-integracion.md#12-distribución-y-despliegue).

**Firma de PDF en cadena (desde 1.5.0).** Varias personas pueden firmar el mismo PDF una después de otra, en computadoras distintas y con S-FiDE o Acrobat Reader en cualquier combinación. Los firmadores revisan el documento antes de firmar (si ya fue cerrado, y el estado de cada firma anterior, explicado en lenguaje simple); la última persona puede cerrarlo con una firma final que Acrobat reconoce como bloqueo; y la interfaz gráfica permite ubicar la firma sobre el propio documento. Ver la sección 7.7 del [Manual Técnico](doc/manual-tecnico-integracion.md). La GUI incluye Apache PDFBox (Apache 2.0) solo para mostrar las páginas.

**Visor interno de documentos (desde 1.5.0).** La interfaz gráfica puede mostrar un PDF o un XML sin modificarlo (menú Archivo): el XML como un árbol con colores y elementos plegables, con las firmas marcadas; para un Certificado de Origen o una Declaración Jurada de Origen, un botón abre el visor oficial en el navegador. Ver la sección 7.8 del manual técnico.

**Datos por usuario y actualizaciones (desde 1.4.0).** Lo que S-FiDE recuerda (`sfide-defaults.properties`) y los `.pem` extraídos se guardan en la carpeta personal de cada usuario (`~/S-FiDE`, `C:\Users\<usuario>\S-FiDE` en Windows), no en la carpeta de instalación, que puede ser compartida. **Ayuda → Buscar actualizaciones...** instala nuevas versiones desde GitHub, todo o nada y con la instalación compartida en mente; `crear-paquete-actualizacion.ps1` arma el paquete a adjuntar a cada release.

**macOS (desde 1.5.0).** Hay un paquete por procesador, `S-FiDE-<versión>-macos-aarch64.tar.gz` (Apple Silicon) y `S-FiDE-<versión>-macos-x64.tar.gz` (Intel), con su propio Java y JavaFX embebidos (macOS 12 o posterior). Se arman con `crear-distribucion-macos.ps1`/`.sh`; las instrucciones de uso están en `Leeme.txt`.

Los ZIP de las [releases](https://github.com/Grupo-Sauken-S-A/S-FIDE/releases) traen esa misma distribución ya armada. **Antes de descomprimir, cree una carpeta propia** (se recomienda `C:\S-FiDE` en Windows y `/opt/S-FiDE` en Linux y macOS) y descomprima el contenido del ZIP dentro de esa carpeta — no directamente en la raíz de una unidad, el Escritorio o Descargas. Se recomienda `C:S-FiDE` en Windows y `/opt/S-FiDE` en Linux y macOS.

## Documentación

- **[Guía de Usuario S-FiDE GUI](doc/manual-usuario-sfide-gui.html)** ([versión publicada](https://grupo-sauken-s-a.github.io/S-FIDE/manual-usuario-sfide-gui.html)) — cómo usar la interfaz gráfica pantalla por pantalla, con capturas reales, para quien firma y verifica haciendo clic en vez de por línea de comandos.
- **[Manual Técnico de Integración](doc/manual-tecnico-integracion.md)** ([versión publicada](https://grupo-sauken-s-a.github.io/S-FIDE/manual-tecnico-integracion.html)) — referencia completa: arquitectura, cada módulo con sus parámetros y mensajes de error, mecanismos de firma (PKCS#11/PKCS#12/Windows CSP-KSP), validación de revocación OCSP/CRL, especialización ALADI/MERCOSUR, y guía de migración desde 1.0.0.
- **[Leeme.txt](Leeme.txt)** — guía rápida para el usuario final de una distribución ya compilada.
- **[CHANGELOG.md](CHANGELOG.md)** — historial de versiones.

## Contribuir

Los aportes son bienvenidos. Antes de enviar un cambio, leé:

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — cómo compilar, convenciones del proyecto, cómo enviar un cambio.
- **[AGENTS.md](AGENTS.md)** — si vas a usar un asistente de IA (Claude Code, Cursor, Copilot u otro) para contribuir, esto le da el contexto técnico completo del proyecto.
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** — normas de convivencia.

## Seguridad

Si encontrás una vulnerabilidad de seguridad, no abras un issue público — seguí el proceso de **[SECURITY.md](SECURITY.md)**.

## Licencia

S-FiDE se distribuye bajo la **Licencia Pública General GNU (GPL), versión 2 o cualquier versión posterior**. Es software libre: se puede redistribuir y/o modificar bajo esos términos, distribuido con la intención de que sea útil pero **sin garantía** de ningún tipo. Texto completo en [`LICENSE`](LICENSE).

Copyright © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

**Componentes de terceros.** S-FiDE incluye bibliotecas de otros autores, cada una con su propia licencia libre: iText 8 (**AGPL v3**), Bouncy Castle (tipo MIT), Apache PDFBox, Apache Santuario, Commons y Woodstox (Apache 2.0), SLF4J (MIT), Logback (EPL 2.0 o LGPL 2.1), y el runtime OpenJDK y JavaFX (GPL v2 con *Classpath Exception*). La tabla completa —versión, módulo que la usa y qué obliga cada licencia a quien modifica o redistribuye— está en la [sección 3 del Manual Técnico](doc/manual-tecnico-integracion.md#3-software-de-terceros-y-dependencias).

## Soporte y contacto

**Grupo Sauken S.A.** — Córdoba, Argentina
Email: soporte@sauken.com.ar · Sitio: [www.sauken.com.ar](https://www.sauken.com.ar/)

El software se distribuye libremente bajo la licencia indicada. El soporte técnico comercial es un servicio aparte, independiente de la libertad de uso del software.
