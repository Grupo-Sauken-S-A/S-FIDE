# Manual Técnico de Integración — S-FiDE

**Sistema de Firma Digital Extendido**
Grupo Sauken S.A. — Córdoba, Argentina
Versión del documento: acompaña a S-FiDE v1.1.0-beta.1 — 29/08/2026

---

## Índice

1. [Introducción y filosofía](#1-introducción-y-filosofía)
2. [Especificaciones técnicas del producto](#2-especificaciones-técnicas-del-producto)
3. [Software de terceros y dependencias](#3-software-de-terceros-y-dependencias)
4. [Licencia de uso](#4-licencia-de-uso)
5. [Código fuente y repositorio](#5-código-fuente-y-repositorio)
6. [Arquitectura de integración](#6-arquitectura-de-integración)
7. [Mecanismos de firma digital: PKCS#11, PKCS#12 y Windows CSP/KSP](#7-mecanismos-de-firma-digital-pkcs11-pkcs12-y-windows-cspksp)
8. [Catálogo de tokens y drivers soportados](#8-catálogo-de-tokens-y-drivers-soportados)
9. [Catálogo de aplicaciones](#9-catálogo-de-aplicaciones)
   9.1. [TokenSlotsView](#91-tokenslotsview)
   9.2. [TokenCertificateExtractor](#92-tokencertificateextractor)
   9.3. [PKCS12CertificateExtractor](#93-pkcs12certificateextractor)
   9.4. [XMLSignerPKCS11](#94-xmlsignerpkcs11)
   9.5. [XMLSignerPKCS12](#95-xmlsignerpkcs12)
   9.6. [XMLVerifySignatures](#96-xmlverifysignatures)
   9.7. [XMLVerifyXSDStructure](#97-xmlverifyxsdstructure)
   9.8. [PDFSignerPKCS11](#98-pdfsignerpkcs11)
   9.9. [PDFSignerPKCS12](#99-pdfsignerpkcs12)
   9.10. [PDFVerifySignatures](#910-pdfverifysignatures)
   9.11. [XMLSignerWindowsCSP](#911-xmlsignerwindowscsp)
   9.12. [PDFSignerWindowsCSP](#912-pdfsignerwindowscsp)
   9.13. [S-FiDE GUI](#913-s-fide-gui)
10. [Especialización de comercio exterior ALADI/MERCOSUR: COD, CODEH, DJO y DJOEH](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)
11. [Integración desde otras aplicaciones](#11-integración-desde-otras-aplicaciones)
12. [Distribución y despliegue](#12-distribución-y-despliegue)
13. [Historial de versiones](#13-historial-de-versiones)
14. [Glosario](#14-glosario)
15. [Soporte y contacto](#15-soporte-y-contacto)

---

## 1. Introducción y filosofía

> **¿Ya tenías una integración funcionando contra S-FiDE 1.0.0?** Casi todo lo agregado en 1.1.0-beta.1 es aditivo y no requiere ningún cambio de tu lado — pero hay un puñado de casos puntuales donde cambió el comportamiento de un jar que ya usabas. Antes de actualizar, revisá la **[Guía de migración desde 1.0.0](#guía-de-migración-desde-100)** al final de la [sección 13](#13-historial-de-versiones).

S-FiDE (**Si**stema de **F**irma D**i**gital Extendido) es una suite de programas Java independientes para firmar y verificar firmas digitales en documentos XML y PDF, y para extraer/inspeccionar certificados digitales desde tokens criptográficos (PKCS#11), archivos PKCS#12 o el almacén de certificados de Windows.

El diseño responde a un principio central: **cada capacidad es un programa independiente**, invocable por línea de comandos, que cualquier aplicación externa puede ejecutar como proceso hijo sin integrar ninguna librería Java. No existe una "librería S-FiDE" para enlazar — se distribuye como un conjunto de archivos `.jar` ejecutables, más una interfaz gráfica opcional (S-FiDE GUI) que orquesta esos mismos programas para usuarios que prefieren no trabajar por línea de comandos.

### Principios de diseño que todo integrador debe conocer

- **Contrato uniforme de proceso.** Todos los programas se invocan como `java -jar Programa.jar <argumentos>`. El código de salida (`exit code`) es `0` si la operación fue exitosa y `1` si hubo un error — es el único valor indispensable para saber si la operación resultó bien.
- **Salidas separadas y limpias.** El resultado normal va a la salida estándar (`stdout`); los errores van a la salida de error (`stderr`), siempre como texto simple y controlado. **Nunca** se expone un stack trace de Java al usuario final — los mensajes de error están pensados para operadores no técnicos (personas gestionando certificados y firmas, no desarrolladores leyendo logs).
- **UTF-8 de punta a punta.** Toda entrada y salida de todos los programas está codificada en UTF-8, en cualquier sistema operativo. Un integrador que lea `stdout`/`stderr` con otra codificación va a ver caracteres incorrectos en nombres, rutas o mensajes con acentos.
- **Multiplataforma.** Los programas compilan y corren igual en Windows, GNU/Linux y macOS (con la única excepción de los dos módulos que usan el almacén de certificados de Windows, ver [sección 9.11](#911-xmlsignerwindowscsp) y [9.12](#912-pdfsignerwindowscsp), que son exclusivos de Windows por diseño).
- **La GUI no es un atajo privilegiado.** S-FiDE GUI invoca exactamente los mismos `.jar` con los mismos argumentos que usaría un integrador externo — no reimplementa ninguna lógica de firma por su cuenta. Cualquier cosa que la GUI pueda hacer, un integrador puede reproducirla por línea de comandos.
- **Vocabulario de comandos especiales unificado.** Todos los módulos aceptan las mismas tres familias de alias para las funciones de diagnóstico, sin importar el "dialecto" histórico de cada uno: `-version` / `-v` / `--version` (versión), `-ayuda` / `-h` / `--help` (ayuda) y `-licencia` / `--license` (licencia). Los módulos que exponen catálogos adicionales (`-listar-drivers`, `-listar-certificados`) aceptan tanto la forma de un guion como la de dos. Esto se unificó en la versión 1.1.0-beta.1 — ver [sección 13](#13-historial-de-versiones).

---

## 2. Especificaciones técnicas del producto

| Ítem | Detalle |
|---|---|
| Lenguaje / runtime | Java 23 (OpenJDK 23.0.1) |
| Interfaz gráfica | JavaFX 23.0.1 (solo para el módulo `s_fide_gui`) |
| Sistema de build | Apache Maven (multi-módulo, 13 módulos) |
| Empaquetado de distribución | Jars autocontenidos ("fat jars", vía `maven-shade-plugin`/`maven-assembly-plugin`) — no requieren classpath externo |
| Sistemas operativos soportados | Windows, GNU/Linux, macOS (64 bits) — CSP/KSP es exclusivo de Windows |
| Arquitectura de CPU | x86-64 (64 bits obligatorio; OpenJDK 23 no soporta sistemas de 32 bits) |
| Requisito mínimo de SO en Windows | Windows 10 de 64 bits o superior |
| Estándares de firma implementados | XMLDSig (XML), PAdES-equivalente vía CMS/PKCS#7 detached conforme ETSI EN 319 142 (PDF) — ver nota en [sección 9](#9-catálogo-de-aplicaciones) |
| Algoritmos de firma aplicados por S-FiDE | RSA 2048 bits, SHA-256, PKCS#1 v1.5 |
| Algoritmos aceptados al **verificar** | SHA-256 y **SHA-1** (compatibilidad con firmas de terceros más antiguas) — ver [sección 7.6](#76-compatibilidad-con-firmas-sha-1-de-aplicaciones-de-terceros) |
| Estándares de acceso a hardware | PKCS#11, PKCS#12, Microsoft CryptoAPI/CNG (vía CSP/KSP) |
| Validación de revocación | OCSP y CRL (requiere conexión a Internet para validación completa) |

La distribución final embebe su propio runtime de Java y su propio SDK de JavaFX (ver [sección 12](#12-distribución-y-despliegue)), por lo que **no requiere tener Java preinstalado** en el equipo destino — puede ejecutarse desde una carpeta portable, incluyendo un medio de almacenamiento removible (pendrive), en un Windows, Linux o macOS "limpios".

> **Nota sobre números de versión de estándares.** El código fuente de S-FiDE no declara ni verifica un número de versión específico de PKCS#11 o PKCS#12 (por ejemplo "v2.20" o "v1.1") — estos estándares se acceden a través de los proveedores criptográficos del JDK (`SunPKCS11`, `KeyStore.getInstance("PKCS12")`), cuya versión de conformidad depende del propio OpenJDK, no de S-FiDE. Documentación previa que citaba números de versión puntuales de estos estándares no estaba respaldada por el código y fue corregida.

---

## 3. Software de terceros y dependencias

| Componente | Versión (1.1.0-beta.1) | Uso | Licencia |
|---|---|---|---|
| BouncyCastle (`bcprov`/`bcpkix`/`bcutil`-jdk18on) | 1.85 | Primitivos criptográficos, ASN.1, construcción de `DigestInfo` | MIT (Bouncy Castle License) |
| iText (`kernel`/`io`/`commons`/`sign`/`bouncy-castle-adapter`) | 8.0.5 | Firma y verificación de documentos PDF | AGPL v3 / comercial (Apryse) |
| Apache Santuario (`xmlsec`) | 4.0.4 | Soporte adicional de firma XML en `xml_signer_pkcs11` | Apache License 2.0 |
| JavaFX (`javafx-controls`/`fxml`/`base`/`graphics`) | 23.0.1 | Interfaz gráfica de `s_fide_gui` únicamente | GPL v2 con Classpath Exception |
| SLF4J | 2.0.17 | Fachada de logging | MIT |
| Logback (`logback-classic`) | 1.5.18 | Implementación de logging | EPL 1.0 / LGPL 2.1 |
| Apache Maven | 3.9.x (via wrapper `mvnw`) | Build del proyecto | Apache License 2.0 |

**Nota sobre compatibilidad de licencias:** iText 8.x se distribuye bajo AGPL v3 (o licencia comercial de Apryse). Los archivos fuente de S-FiDE están licenciados bajo **GPLv2 "o cualquier versión posterior"** — esa cláusula "o posterior" es la que habilita la compatibilidad de combinación con AGPLv3 (§13 de la AGPLv3 permite explícitamente la combinación con código bajo GPLv3). No es necesario ningún trámite adicional para usar S-FiDE tal como se distribuye; un integrador que quiera **modificar y redistribuir** los módulos que usan iText debe tener en cuenta los términos de AGPL v3 para esa parte específica.

---

## 4. Licencia de uso

S-FiDE se distribuye bajo la **Licencia Pública General GNU (GNU GPL), versión 2 o cualquier versión posterior**, publicada por la Free Software Foundation.

- Es software libre: se puede redistribuir y/o modificar bajo los términos de esa licencia.
- Se distribuye con la intención de que sea útil, **sin garantía**, ni siquiera la garantía implícita de comercialización o idoneidad para un propósito particular.
- El texto completo de la licencia está disponible en el archivo `LICENSE.txt` que acompaña a cada módulo, y en [gnu.org/licenses/gpl-2.0.html](https://www.gnu.org/licenses/gpl-2.0.html).
- **Copyright** © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.
- El soporte técnico es un servicio con cargo, independiente de la libertad de uso del software (ver [sección 15](#15-soporte-y-contacto)).

---

## 5. Código fuente y repositorio

- **Repositorio:** [github.com/Grupo-Sauken-S-A/S-FIDE](https://github.com/Grupo-Sauken-S-A/S-FIDE)
- **Organización:** proyecto Maven multi-módulo (13 módulos) con un `pom.xml` raíz de tipo `pom` (agregador) y un módulo por capacidad.
- **Versionado:** [SemVer](https://semver.org/). Tags publicados: `v1.0.0` (primer release estable), `v1.1.0-beta.1` (versión actual, con QA de hardware y de código en curso).
- **Compilar desde el código fuente:**
  ```bash
  git clone https://github.com/Grupo-Sauken-S-A/S-FIDE.git
  cd S-FIDE
  ./mvnw clean install
  ```
  Requiere JDK 23 (el proyecto incluye Maven Wrapper, no hace falta tener Maven instalado aparte). Cada módulo genera su jar en su propia carpeta `target/`.
- El código fuente completo está disponible públicamente conforme a los términos de la GPLv2 — cualquier integrador puede auditar exactamente qué hace cada programa antes de confiar en él para procesar documentos con validez legal.

---

## 6. Arquitectura de integración

Tanto una **aplicación integradora externa** (en cualquier lenguaje) como la propia **S-FiDE GUI** invocan los módulos exactamente de la misma manera:

1. Lanzan `java -jar Modulo.jar <argumentos>` como un **proceso hijo independiente**.
2. El módulo hace su trabajo y escribe el resultado en `stdout`, los errores en `stderr`.
3. El proceso termina con código `0` (éxito) o `1` (error).
4. Quien lo invocó lee esa salida y ese código — no hay ningún otro canal de comunicación.

No hay una API interna distinta para "uso avanzado": el contrato de línea de comandos **es** la API. La GUI no tiene ningún atajo que un integrador externo no pueda reproducir.

Cada módulo:
1. Recibe sus parámetros como argumentos de línea de comandos (nunca por variables de entorno ni archivos de configuración, salvo la excepción documentada de `sfide-defaults.properties` que usa exclusivamente la GUI para recordar valores entre sesiones).
2. Realiza su tarea (firmar, verificar, extraer, listar).
3. Escribe su resultado en `stdout` y, si corresponde, genera un archivo de salida en disco.
4. Termina con código `0` (éxito) o `1` (error), habiendo escrito en `stderr` un mensaje de error breve y en español si algo falló.

---

## 7. Mecanismos de firma digital: PKCS#11, PKCS#12 y Windows CSP/KSP

S-FiDE soporta tres formas distintas de acceder a una clave privada para firmar. Elegir la correcta depende de dónde vive esa clave.

### 7.1 PKCS#11 (tokens criptográficos y HSM)

**PKCS#11** ("Cryptographic Token Interface Standard") es un estándar de la industria (originalmente de RSA Laboratories, hoy mantenido por OASIS) que define una API en lenguaje C para que cualquier aplicación hable con un dispositivo criptográfico — un token USB, una smart card, o un HSM (Hardware Security Module) — sin conocer los detalles internos del fabricante. El fabricante del token provee una **biblioteca dinámica** (`.dll` en Windows, `.so` en Linux, `.dylib` en macOS) que implementa esa API; la aplicación carga esa biblioteca en tiempo de ejecución.

En Java, esto se hace a través del proveedor `SunPKCS11`, que viene incluido en el JDK: se le indica la ruta de la biblioteca del fabricante y expone la clave privada del token como un objeto `PrivateKey` estándar de Java, utilizable con las APIs criptográficas normales (`Signature`, `KeyStore`) sin que el código de la aplicación necesite saber que la clave nunca sale del hardware.

**El detalle técnico que todo integrador de S-FiDE 1.1.0 debe conocer — mecanismos de hash interno vs. externo:**

Un token PKCS#11 firma un bloque de datos mediante un "mecanismo" (`CK_MECHANISM`). Para RSA-SHA256 existen dos mecanismos posibles:

- **`CKM_SHA256_RSA_PKCS`** (mecanismo combinado): el propio token calcula el hash SHA-256 del documento internamente y luego lo firma. Un solo llamado, más simple. Los tokens SafeNet/Thales lo soportan.
- **`CKM_RSA_PKCS`** (mecanismo puro): el token **solo** aplica el padding PKCS#1 v1.5 y la operación RSA — el hash SHA-256 debe calcularlo la aplicación *antes*, y envolverlo en una estructura ASN.1 llamada `DigestInfo` (que incluye el identificador del algoritmo de hash usado) antes de pasárselo al token. Tokens como el Feitian ePass2003 en modo FIPS 140-2 Nivel 3 **solo** exponen este mecanismo.

Desde la versión 1.1.0, `XMLSignerPKCS11` y `PDFSignerPKCS11` prueban automáticamente el mecanismo combinado primero y, si el token lo rechaza, calculan el hash por software, arman el `DigestInfo` y reintentan con el mecanismo puro — de forma completamente transparente para el integrador. No hay ningún parámetro para elegir el mecanismo: la detección es automática y ocurre en cada operación de firma.

**Ventajas de PKCS#11:** estándar abierto, multiplataforma, la clave privada nunca sale del hardware (alta seguridad). **Desventajas:** requiere que el integrador conozca la ruta exacta de la biblioteca del fabricante (ver [sección 8](#8-catálogo-de-tokens-y-drivers-soportados)) y el número de slot; requiere pasar el PIN/contraseña del token como argumento del programa.

### 7.2 PKCS#12 (archivos de certificado)

**PKCS#12** es un formato de contenedor de archivo (`.p12` o `.pfx`) que empaqueta, cifrado con una contraseña, un certificado X.509 junto con su clave privada. A diferencia de un token, la clave privada existe como datos dentro de un archivo — no hay hardware involucrado.

**Ventajas:** simple de usar y distribuir, no requiere instalar ningún driver, funciona igual en cualquier sistema operativo. **Desventajas:** la clave privada es un archivo que puede copiarse — la seguridad depende enteramente de proteger ese archivo y su contraseña; no es aceptable para todos los casos de uso regulados (la normativa AC-ONTI exige homologación FIPS 140-2 para ciertos trámites, lo cual solo aplica a dispositivos de hardware).

### 7.3 Windows CSP/KSP (almacén de certificados de Windows)

En Windows, además de PKCS#11, existe un mecanismo **nativo del sistema operativo**: CryptoAPI (CAPI, interfaz legada) y su sucesor CNG (Cryptography API: Next Generation), a través de **CSP** (Cryptographic Service Provider) o **KSP** (Key Storage Provider) respectivamente. Los fabricantes de tokens suelen instalar, además del módulo PKCS#11, un *minidriver* CSP/KSP certificado por Microsoft — esto hace que el certificado del token aparezca automáticamente en el Administrador de certificados de Windows, sin que ninguna aplicación deba configurar una ruta de biblioteca.

Este es, de hecho, el mecanismo que usa **Adobe Acrobat/Reader por defecto en Windows** — por eso un token puede "funcionar solo" en Acrobat sin ninguna configuración de PKCS#11: Acrobat lee el certificado directamente del almacén de Windows.

S-FiDE 1.1.0 incorpora esta alternativa a través de `XMLSignerWindowsCSP` y `PDFSignerWindowsCSP`, que usan el proveedor `SunMSCAPI` del JDK para acceder al almacén `Windows-MY` (Personal del usuario actual). **No se pasa contraseña**: el acceso a la clave privada lo administra el propio Windows (según el token, puede aparecer un diálogo nativo del sistema operativo pidiendo el PIN al momento de firmar).

**Ventajas:** no requiere configurar ninguna ruta de biblioteca, comportamiento idéntico al de Acrobat. **Desventajas:** exclusivo de Windows (no portable a Linux/macOS); `SunMSCAPI` habla el CryptoAPI **legado**, no CNG moderno directamente — funciona en la práctica porque Windows tiende un puente CAPI↔KSP automático a nivel de sistema operativo, pero ese puente podría dejar de cubrir algún token en el futuro (la actualización de Windows de octubre de 2025, KB5066835, empuja el ecosistema hacia KSP puro).

### 7.4 Comparación

| | PKCS#11 | PKCS#12 | Windows CSP/KSP |
|---|---|---|---|
| Dónde vive la clave | Hardware (token/HSM) | Archivo cifrado | Hardware o software, vía almacén de Windows |
| Multiplataforma | Sí | Sí | No (solo Windows) |
| Requiere configurar ruta de driver | Sí | No aplica | No |
| Contraseña pasada por el programa | Sí (PIN del token) | Sí (contraseña del archivo) | No (la administra Windows) |
| Nivel de seguridad típico | Alto (FIPS 140-2 Nivel 2-3) | Depende de la protección del archivo | Igual que el hardware subyacente |
| Recomendado para | Uso regulado (AC-ONTI), la mayoría de los casos | Pruebas, entornos sin hardware, automatización de servidor | Alternativa cuando el certificado ya está en el almacén y se prioriza simplicidad sobre portabilidad |

### 7.5 Validación de revocación: OCSP y CRL

Un certificado puede ser matemáticamente válido (no vencido, cadena de confianza correcta) y sin embargo haber sido **revocado** por su emisor — por ejemplo, porque el token fue robado o la clave se vio comprometida. La única forma de saberlo es consultar a la autoridad certificante; no hay manera de verificar esto localmente, sin conexión.

Al verificar una firma, `XMLVerifySignatures` y `PDFVerifySignatures` intentan dos mecanismos, en este orden:

1. **OCSP (Online Certificate Status Protocol):** consulta en tiempo real al "respondedor OCSP" de la autoridad certificante. La URL de ese respondedor está publicada **dentro del propio certificado**, en la extensión *Authority Information Access* (AIA, OID `1.3.6.1.5.5.7.1.1`). S-FiDE arma una solicitud identificando el certificado por su número de serie y los datos del emisor, la envía por HTTP, y la autoridad certificante responde con un mensaje indicando si el certificado está vigente o revocado. Es el mecanismo más preciso — refleja el estado en el instante exacto de la consulta.
2. **CRL (Certificate Revocation List):** si OCSP no está disponible, no responde, o el resultado es indeterminado, S-FiDE recurre a la Lista de Certificados Revocados — un archivo publicado periódicamente por la autoridad certificante (la URL también viene en el certificado, en la extensión *CRL Distribution Point*, OID `2.5.29.31`) con todos los números de serie revocados hasta la fecha de esa lista. Es menos preciso que OCSP (puede no reflejar una revocación muy reciente) pero funciona aunque el respondedor OCSP puntual esté caído.

**Tiempos de espera exactos** (verificados en el código de `XMLVerifySignatures`): la comprobación de conectividad a Internet usa un socket de prueba con **3 segundos** de tiempo de espera; las consultas HTTP a OCSP y CRL usan un cliente HTTP con **5 segundos** de tiempo de espera de conexión cada una.

Si **ninguno de los dos mecanismos** responde, o no se encuentra ninguna URL de OCSP/CRL en el certificado, el estado de revocación queda como **"No verificable"** — esto **no invalida la firma**: la integridad criptográfica (¿la firma corresponde exactamente a este documento y esta clave?) y el estado de revocación son dos verificaciones independientes. Un documento puede salir `DOCUMENTO VÁLIDO` con revocación "No verificable": la firma en sí es genuina, solo que no se pudo confirmar en ese momento que el certificado siga vigente según la autoridad certificante.

**Por qué hace falta Internet:** tanto la URL del respondedor OCSP como la del punto de distribución CRL son direcciones de la autoridad certificante en Internet. Sin conexión, ninguno de los dos mecanismos puede completarse y el resultado siempre va a ser "No verificable". Esto es exactamente lo que se observa al verificar una firma con un certificado de prueba autofirmado (sin una autoridad certificante real detrás): *"Estado de revocación: No verificable - Sin URLs de OCSP/CRL"*, porque ese certificado nunca declaró esas extensiones.

**Fecha usada para evaluar la revocación en XML.** Por defecto, `XMLVerifySignatures` usa la fecha y hora **actuales** del sistema para decidir si una revocación encontrada es anterior o posterior a la firma. Existe una excepción deliberada e importante para documentos de comercio exterior ALADI/MERCOSUR — ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh).

**Fecha usada para evaluar la revocación en PDF — de dónde sale exactamente, verificado contra el bytecode real de iText 8.0.5.** `PDFVerifySignatures` usa `PdfPKCS7.getSignDate()` como fecha de referencia. Esa función tiene dos fuentes posibles, en este orden de prioridad:

1. **Un sello de tiempo RFC 3161 (TSA) embebido en la firma**, si existe — una fecha certificada por una autoridad de sellado de tiempo independiente, no por el propio firmante.
2. **El campo `/M` del diccionario de firma del PDF**, si no hay sello de tiempo. Este valor lo escribe la propia aplicación firmante con la hora de su reloj de sistema al momento de firmar (`SignatureUtil.readSignatureData()` lo lee vía `PdfSignature.getDate()` y `PdfDate.decode(...)`). Queda dentro del rango de bytes cubierto por la firma — no se puede alterar después sin invalidar la firma — pero es un dato **autodeclarado por el firmante**, no verificado por ningún tercero.

**Ninguno de los tres firmadores de PDF de S-FiDE (`PDFSignerPKCS11`, `PDFSignerPKCS12`, `PDFSignerWindowsCSP`) solicita un sello de tiempo TSA al firmar.** En consecuencia, para cualquier PDF firmado por S-FiDE, `getSignDate()` siempre recae en la opción 2: la hora del reloj local del equipo que firmó, autodeclarada. Esto es una diferencia real de robustez frente a XML/COD/DJO: ahí la fecha de referencia sale de un dato del propio documento de negocio (sección 10), mientras que en PDF depende enteramente de que el reloj del equipo firmante haya estado bien configurado al momento de firmar.

**Por qué un certificado revocado invalida la firma, en términos simples:** la revocación significa que la autoridad certificante retiró la confianza en ese certificado — por ejemplo, porque la clave privada se filtró o el titular dejó de estar habilitado — **después** de haberlo emitido. Que la firma sea criptográficamente correcta solo demuestra que el documento no fue alterado y que fue producido con esa clave privada; no demuestra que esa clave siga siendo confiable. Por eso `XMLVerifySignatures` marca como INVÁLIDA cualquier firma cuyo certificado figure como revocado en el momento correspondiente (ver [sección 10.4](#104-validación-de-revocación-por-elemento--la-regla-completa-y-verificada) para qué fecha exacta se usa según el tipo de documento), y lo informa explícitamente en su salida: *"El certificado fue revocado por su autoridad certificante. Aunque la firma es criptográficamente correcta, esta firma se considera INVÁLIDA por ese motivo."*

### 7.6 Compatibilidad con firmas SHA-1 de aplicaciones de terceros

S-FiDE **firma** exclusivamente con SHA-256 (no ofrece SHA-1 como opción al firmar: es una decisión deliberada, SHA-1 se considera criptográficamente débil para firmar documentos nuevos). Sin embargo, sus verificadores (`XMLVerifySignatures`, `XMLVerifyXSDStructure`, `PDFVerifySignatures`) están diseñados para validar **cualquier firma digital conforme al estándar**, sin importar qué aplicación la haya generado ni con qué algoritmo de hash — incluyendo firmas **SHA-1**, habituales en documentos firmados años atrás con software de terceros ya discontinuado.

- En el lado XML, `XMLVerifySignatures` y `XMLVerifyXSDStructure` deshabilitan explícitamente el modo de "validación segura" de JSR-105 (`org.jcp.xml.dsig.secureValidation = false`) — ese modo, si estuviera activo, rechazaría de plano cualquier firma con SHA-1 o claves RSA cortas antes de siquiera evaluarlas. Ambos módulos reconocen y validan tanto `rsa-sha1` como `rsa-sha256` como métodos de firma.
- En el lado PDF, `PDFVerifySignatures` verifica la firma a través de `PdfPKCS7.verifySignatureIntegrityAndAuthenticity()` de iText, que no impone ninguna restricción de algoritmo — el algoritmo de firma se informa de manera descriptiva (`Algoritmo de firma: ...`) pero nunca se usa como criterio de rechazo.

Esta capacidad es intencional y debe mantenerse: la función de S-FiDE como verificador es validar lo que **ya fue firmado**, sin importar la antigüedad ni la herramienta de origen — es una propiedad distinta e independiente de qué algoritmos usan los propios firmadores de S-FiDE para producir firmas nuevas.

**Por qué esto es especialmente relevante para COD y DJO** (ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)): en la práctica, los elementos `COD`/`CODEH`/`DJO`/`DJOEH` de un mismo documento a veces se firman con software de distintas empresas — un exportador puede usar S-FiDE mientras que la Entidad Habilitada usa otra aplicación (o viceversa), y esas otras aplicaciones pueden seguir usando SHA-1. `XMLVerifySignatures` tiene que poder validar ambas firmas del mismo documento sin importar cuál de las dos aplicaciones las generó ni con qué algoritmo — es exactamente el escenario que este soporte de compatibilidad está pensado para cubrir.

---

## 8. Catálogo de tokens y drivers soportados

Cualquier token que cumpla el estándar PKCS#11 es utilizable con S-FiDE. La siguiente tabla —fuente única en el repositorio en `shared-resources/token-profiles.txt`— documenta los modelos efectivamente presentes en el ecosistema de firma digital argentino (AC-ONTI), con la ruta típica de su biblioteca por sistema operativo:

| Marca / Modelo | Windows | Linux | macOS | Hash | Estado (AIF/SCBA) |
|---|---|---|---|---|---|
| SafeNet/Thales eToken 5110 / 5110+ | `C:\Windows\System32\eTPKCS11.dll` | `/usr/lib/libeToken.so` | `/usr/local/lib/libeTPkcs11.dylib` | Interno | Vigente — recomendado por AIF (cert. NIST #4480 activo, Nivel 3) |
| Feitian ePass2003 / ePass2003Auto | `C:\Windows\System32\eps2003csp11.dll` | `/usr/lib/libcastle.so.1.0.0` | `/usr/local/lib/libcastle.1.0.0.dylib` | Externo | Vigente — reemplazo estándar SCBA |
| mToken CryptoID nueva (FIPS 140-3, cert. #4845 activo) | `...\LMCryptoIDE\lm_cryptoide_pkcs11.dll` | `/opt/CryptoIDFipsUser/x64/lib/liblm_cryptoide_pkcs11.so` | `/opt/CryptoIDE/lib/libcryptoide_pkcs11.dylib` | Externo (sin confirmar) | Válido solo en su variante 140-3 |
| mToken CryptoID vieja (FIPS 140-2, cert. #2626) | `C:\Windows\System32\CryptoIDA_pkcs11.dll` | `/usr/lib/libcryptoide_pkcs11.so` | `.../libcryptoide_pkcs11.dylib` | Externo (sin confirmar) | Discontinuado — no usar después de 2024-12-30 |
| Athena IDProtect / ASECard | `C:\Windows\System32\asepkcs.dll` | `/usr/lib/x64-athena/libASEP11.so` | `.../libASEP11.dylib` | Desconocido | Discontinuado — en retiro por Res. SC Nº 1682/24 |
| OpenSC (genérico, respaldo) | `C:\Windows\System32\opensc-pkcs11.dll` | `/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so` | `/Library/OpenSC/lib/opensc-pkcs11.so` | Depende del token | Driver de respaldo, no es una marca en sí misma |

**Estrategia de hash** hace referencia a lo explicado en la [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm): "Interno" son tokens con mecanismo combinado (SafeNet); "Externo" son tokens que requieren el cálculo de hash por software (ePass2003, confirmado con hardware real); "Externo (sin confirmar)" es la hipótesis de diseño para modelos que S-FiDE todavía no validó contra hardware físico.

### Ayuda de selección de driver

- **Desde la GUI:** las pestañas que piden una biblioteca PKCS#11 muestran un `ComboBox` con marca/modelo y un botón "Detectar automáticamente", que revisa cuáles de las rutas de la tabla existen realmente en el equipo.
- **Desde línea de comandos:** el comando `-listar-drivers` (también aceptado como `--listar-drivers`) imprime la misma tabla, filtrada por sistema operativo, indicando además si cada ruta existe en el equipo actual. Disponible en `TokenSlotsView`, `TokenCertificateExtractor`, `XMLSignerPKCS11` y `PDFSignerPKCS11`.

Esta detección es **solo una ayuda de UX** — nunca la única fuente de verdad. El nombre de archivo de una biblioteca puede cambiar entre versiones de middleware sin previo aviso; el código de firma siempre reintenta en tiempo de ejecución según lo que el token realmente responde (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)), sin depender de esta tabla para decidir el mecanismo.

---

## 9. Catálogo de aplicaciones

Convenciones comunes a todas las aplicaciones de esta sección:

- Los comandos de diagnóstico son **idénticos en todos los módulos** desde la 1.1.0-beta.1: `-version` / `-v` / `--version` (versión), `-ayuda` / `-h` / `--help` (ayuda), `-licencia` / `--license` (licencia). Cualquiera de las tres formas funciona en cualquier módulo.
- El código de salida es `0` en éxito y `1` en error, salvo aclaración en contrario (los verificadores usan el exit code para indicar además el *resultado* de la validación — ver cada módulo).
- Todas leen y escriben en UTF-8.
- Ningún módulo expone un stack trace de Java al usuario — todo error se traduce a un mensaje breve en español antes de imprimirse.

### 9.1 TokenSlotsView

**Qué hace:** visualiza los slots disponibles en un token PKCS#11 y la información de los certificados/claves almacenados en cada uno. Es la primera herramienta a usar frente a un token nuevo, para saber en qué slot está el certificado antes de firmar con él.

**Uso recomendado:** diagnóstico inicial de un token, o para confirmar el número de slot antes de invocar `XMLSignerPKCS11`/`PDFSignerPKCS11`.

**Sintaxis:**
```
java -jar TokenSlotsView.jar <Ruta de la biblioteca PKCS#11> <Contraseña del token>
java -jar TokenSlotsView.jar [-version | -ayuda | -licencia | -listar-drivers]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Ruta de biblioteca PKCS#11 | Sí | Ruta absoluta o relativa al `.dll`/`.so`/`.dylib` del fabricante |
| Contraseña del token | Sí | PIN de usuario del dispositivo |

**Validaciones y estándares:**
- Compatible con cualquier token conforme a PKCS#11.
- Distingue entradas de tipo "Clave Privada" (`KeyStore.isKeyEntry`) de entradas de tipo "Certificado" (`KeyStore.isCertificateEntry`).
- Reporta sujeto, emisor, período de validez y número de serie de cada certificado X.509 v3 encontrado.

**Salida:** por cada slot con contenido, imprime número de slot, alias, tipo (clave privada o certificado), sujeto, emisor, fechas de validez y número de serie.

**Mensajes de error posibles:** "El archivo de la biblioteca PKCS#11 no existe", "El proveedor SunPKCS11 no está disponible", "Contraseña incorrecta o error al acceder al token", "Error al leer el token". Si el token no tiene contenido, no es un error: se informa por salida estándar "No se encontraron certificados ni claves en el token."

**Nota sobre exit code:** invocarlo sin argumentos muestra la ayuda y termina con código `1` (a diferencia de invocarlo explícitamente con `-ayuda`, que termina con código `0`) — un integrador que dispare el proceso "sin querer" sin argumentos para inspeccionar la ayuda no debe interpretar el código `1` resultante como una falla real.

**Ejemplo:**
```
java -jar TokenSlotsView.jar C:\Windows\System32\eTPKCS11.dll "MiPIN123"
```

---

### 9.2 TokenCertificateExtractor

**Qué hace:** extrae el certificado digital almacenado en un slot específico de un token PKCS#11 y lo exporta como archivo `.pem`.

**Uso recomendado:** cuando se necesita el certificado público de un token por separado (por ejemplo, para registrarlo en un sistema externo), sin necesidad de firmar nada.

**Sintaxis:**
```
java -jar TokenCertificateExtractor.jar <Ruta de la biblioteca PKCS#11> <Contraseña del token> <Número de slot>
java -jar TokenCertificateExtractor.jar [-version | -ayuda | -licencia | -listar-drivers]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Ruta de biblioteca PKCS#11 | Sí | Ídem TokenSlotsView |
| Contraseña del token | Sí | PIN de usuario |
| Número de slot | Sí | Entero; ver `TokenSlotsView` para conocerlo |

**Validaciones y estándares:**
- Compatible con cualquier token conforme a PKCS#11; procesa certificados X.509.
- El nombre del archivo `.pem` de salida se deriva del componente `CN=` (Common Name) del sujeto del certificado, reemplazando cualquier carácter que no sea letra, dígito, punto o guion por `_`; si no hay `CN=`, usa el nombre literal `certificate.pem`. El archivo se escribe en el directorio de trabajo actual.

**Salida:** información del certificado por consola (`Sujeto:`, `Emisor:`, `Número de Serie:`, `Válido desde:`, `Válido hasta:`, `Algoritmo de Firma:`) y un archivo `.pem` con el certificado exportado.

**Mensajes de error posibles:** "El archivo de la biblioteca PKCS#11 no existe", "Proveedor SunPKCS11 no disponible", "Error al cargar el almacén de claves", "El número de slot debe ser un número entero", "No se encontró ningún certificado en el slot [número]", "Error al exportar el certificado".

**Ejemplo:**
```
java -jar TokenCertificateExtractor.jar C:\Windows\System32\eTPKCS11.dll "MiPIN123" 0
```

---

### 9.3 PKCS12CertificateExtractor

**Qué hace:** el equivalente de `TokenCertificateExtractor` pero para archivos PKCS#12, sin necesidad de hardware.

**Sintaxis:**
```
java -jar PKCS12CertificateExtractor.jar <archivo.p12> <password>
java -jar PKCS12CertificateExtractor.jar [-version | -ayuda | -licencia]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Archivo PKCS#12 | Sí | Ruta al archivo `.p12`/`.pfx` |
| Contraseña | Sí | Contraseña del archivo PKCS#12 |

No aplica `-listar-drivers` (no hay drivers involucrados con archivos PKCS#12).

**Validaciones y estándares:**
- Compatible con archivos PKCS#12 estándar; procesa certificados X.509.
- Misma lógica de nombre de archivo de salida que `TokenCertificateExtractor` (extracción del `CN=`, saneamiento de caracteres).

**Salida:** información del certificado por consola (mismos campos que `TokenCertificateExtractor`, sin número de slot) y un archivo `.pem` exportado.

**Mensajes de error posibles:** "El archivo PKCS#12 no existe", "El archivo no es un PKCS#12 válido o la contraseña es incorrecta", "El archivo PKCS#12 no contiene ningún certificado", "No se encontró ningún certificado X.509 en el archivo PKCS#12", "Error al exportar certificado".

**Ejemplo:**
```
java -jar PKCS12CertificateExtractor.jar C:\certificados\empresa.pfx "MiContraseña123"
```

---

### Reglas de firma comunes a los tres firmadores XML

`XMLSignerPKCS11`, `XMLSignerPKCS12` y `XMLSignerWindowsCSP` aplican, antes de firmar, dos controles obligatorios:

1. **Nunca firmar un elemento que ya tiene una firma digital aplicada.** Esto vale para cualquier XML genérico, no solo para los especializados de comercio exterior: si el elemento indicado (o el documento completo, si se pasó `""`) ya tiene una `<ds:Signature>` cuya `Reference` apunta a él, el programa rechaza la operación sin modificar el archivo.
2. **Regla de orden para `CODEH`/`DJOEH`** (ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)): no se puede firmar `CODEH` sin una firma previa sobre `COD`, ni firmar `DJOEH` sin una firma previa sobre `DJO`.

Ambos controles se implementan buscando `<ds:Signature>`/`<ds:Reference>` existentes en el documento — no requieren volver a verificar criptográficamente la firma previa, solo confirmar que existe.

**Mensajes de error de estos controles:**
- `"El elemento '[id]' ya tiene una firma digital aplicada. No se puede firmar el mismo elemento dos veces."`
- `"El documento ya tiene una firma digital aplicada sobre todo su contenido. No se puede firmar el mismo elemento dos veces."` (al firmar con elemento vacío `""`)
- `"No se puede firmar el elemento CODEH: no existe una firma digital previa sobre el elemento COD."`
- `"No se puede firmar el elemento DJOEH: no existe una firma digital previa sobre el elemento DJO."`

### 9.4 XMLSignerPKCS11

**Qué hace:** firma digitalmente un documento XML (completo, o un elemento/párrafo específico por su atributo `Id`) usando un token PKCS#11. Implementa XML-DSig (firma enveloped, canonicalización inclusiva), con el mecanismo de hash interno o externo resuelto automáticamente (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)).

**Uso recomendado:** firma de documentos XML (por ejemplo, Certificados de Origen digitales — ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)) con un token de hardware, en el flujo estándar de un exportador u organismo regulado.

**Sintaxis:**
```
java -jar XMLSignerPKCS11.jar <Biblioteca PKCS#11> <Contraseña> <Número de slot> <Archivo XML> <Elemento a firmar>
java -jar XMLSignerPKCS11.jar [-version | -ayuda | -licencia | -listar-drivers]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Biblioteca PKCS#11 | Sí | Ruta al driver del token |
| Contraseña | Sí | PIN del token |
| Número de slot | Sí | Entero |
| Archivo XML | Sí | Ruta al XML a firmar |
| Elemento a firmar | Sí (puede ser cadena vacía `""`) | Si está vacío, firma **todo el documento** (comportamiento estándar y abierto de XML-DSig — no es exclusivo de ningún elemento en particular). Si no está vacío, firma el elemento con ese atributo `Id`/`ID`/`id` (o ese nombre de tag), colocando la firma **embebida**, inmediatamente asociada a ese elemento — también estándar, aplicable a cualquier nombre de elemento. Los valores `COD`, `CODEH`, `DJO` y `DJOEH` tienen además un significado especializado — ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh) |

**Validaciones y estándares:**
- Firma XML-DSig estándar: canonicalización inclusiva (`http://www.w3.org/TR/2001/REC-xml-c14n-20010315`), método de digest SHA-256, método de firma RSA-SHA256 (`http://www.w3.org/2001/04/xmldsig-more#rsa-sha256`), transformación *enveloped signature*.
- `KeyInfo` incluye `KeyValue` y `X509Data` (cadena de certificación embebida en la firma).
- Soporta firmar el documento completo o un elemento puntual identificado por atributo `Id`/`id`/`ID`, con la firma resultante embebida en el propio XML.

**Salida:** archivo `<nombre>-signed.xml` en el mismo directorio que el original; mensaje de confirmación con la ruta de salida por `stdout`.

**Mensajes de error posibles:** "El archivo de la biblioteca PKCS#11 no existe", "El archivo XML no existe", "El elemento o párrafo XML especificado no existe en el documento XML", "Contraseña incorrecta", "Proveedor SunPKCS11 no disponible", "No se encontró el elemento XML con identificador [...]", "El token no admite ningún mecanismo de firma RSA-SHA256 compatible (ni interno ni externo)" (caso extremo, token no soportado), más las [reglas de firma comunes](#reglas-de-firma-comunes-a-los-tres-firmadores-xml) (elemento ya firmado, orden CODEH/DJOEH, obligatoriedad de indicar elemento en XML de comercio exterior — [sección 10.5](#105-reglas-de-firma-obligatorias)).

**Advertencia de seguridad:** un número elevado de intentos fallidos de contraseña puede dejar inutilizado el certificado del token, exigiendo tramitar uno nuevo ante la autoridad certificante.

**Ejemplo:**
```
java -jar XMLSignerPKCS11.jar C:\Windows\System32\eTPKCS11.dll "MiPIN123" 0 C:\docs\certificado-origen.xml COD
```

---

### 9.5 XMLSignerPKCS12

**Qué hace:** idéntico a `XMLSignerPKCS11` en funcionalidad de firma XML (misma configuración criptográfica: XML-DSig, canonicalización inclusiva, RSA-SHA256), pero usando un archivo PKCS#12 en lugar de un token.

**Sintaxis:**
```
java -jar XMLSignerPKCS12.jar <certificado.p12> <password> <archivo.xml> <elemento_xml>
java -jar XMLSignerPKCS12.jar [-version | -ayuda | -licencia]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Archivo PKCS#12 | Sí | Ruta al certificado `.p12`/`.pfx` |
| Contraseña | Sí | Contraseña del certificado |
| Archivo XML | Sí | Ruta al XML a firmar |
| Elemento a firmar | Sí (puede ser cadena vacía `""`) | Mismas reglas que `XMLSignerPKCS11` (documento completo si está vacío, elemento embebido por `Id`/`id`/`ID` si no; incluye la especialización ALADI/MERCOSUR de la [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)) |

No aplica `-listar-drivers` (no hay driver involucrado con archivos PKCS#12).

**Salida:** igual que `XMLSignerPKCS11` — archivo `-signed.xml`.

**Mensajes de error posibles:** "El archivo PKCS#12 no existe", "El archivo XML no existe", "El archivo no es un PKCS#12 válido o la contraseña es incorrecta", "El archivo PKCS#12 no contiene ningún certificado", "El elemento o párrafo XML especificado no existe en el documento XML", "No se encontró el elemento XML con identificador [...]", más las [reglas de firma comunes](#reglas-de-firma-comunes-a-los-tres-firmadores-xml) (elemento ya firmado, orden CODEH/DJOEH, obligatoriedad de indicar elemento en XML de comercio exterior — [sección 10.5](#105-reglas-de-firma-obligatorias)).

**Ejemplo:**
```
java -jar XMLSignerPKCS12.jar C:\certificados\empresa.pfx "MiContraseña123" C:\docs\declaracion.xml DJO
```

---

### 9.6 XMLVerifySignatures

**Qué hace:** verifica la validez de todas las firmas digitales presentes en un documento XML — integridad criptográfica, validez del certificado y estado de revocación (OCSP/CRL, requiere Internet para una validación completa). Acepta firmas RSA-SHA256 **y RSA-SHA1** (ver [sección 7.6](#76-compatibilidad-con-firmas-sha-1-de-aplicaciones-de-terceros)), generadas por S-FiDE o por cualquier otra aplicación conforme a XML-DSig.

**Sintaxis:**
```
java -jar XMLVerifySignatures.jar <archivo.xml> [-simple]
java -jar XMLVerifySignatures.jar [-version | -ayuda | -licencia]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Archivo XML | Sí | Ruta al XML firmado a verificar |
| `-simple` | No | Reduce el detalle de la salida |

**Validaciones y estándares:**
- Valida firmas XML-DSig sin restricción de algoritmo de hash (SHA-1 y SHA-256 ambos aceptados — validación segura de JSR-105 deliberadamente deshabilitada para permitir esto).
- Verifica integridad de cada `Reference`, valor de firma, y estado del certificado (vigente/expirado/aún no válido).
- Validación de revocación OCSP y CRL, en ese orden, con reintento automático (ver [sección 7.5](#75-validación-de-revocación-ocsp-y-crl)).
- Trata como no confiable cualquier certificado cuyo emisor sea vacío o contenga las palabras "self signed"/"localhost" (heurística orientada a detectar certificados de prueba).
- Para documentos de comercio exterior firmados sobre los elementos `COD`/`CODEH`, usa la fecha real del documento (no la fecha del sistema) para evaluar la revocación — ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh).

**Salida:** por cada firma, algoritmo de hash, método de canonicalización, método de firma, valor de la firma, un **"Estado (integridad criptográfica, sin considerar revocación)"** (etiquetado así explícitamente porque se calcula antes de chequear la revocación), información del certificado, estado de revocación y, al final de cada firma, un **"Estado final de la firma #N"** que sí combina integridad criptográfica y revocación — es ese estado final, no el criptográfico previo, el que determina el resultado consolidado `DOCUMENTO VÁLIDO`/`DOCUMENTO INVÁLIDO` de todo el documento. Si el motivo de invalidez es específicamente que el certificado fue revocado, el estado final lo indica explícitamente (`"INVÁLIDA (certificado revocado — ver detalle arriba)"`) y el detalle de revocación explica la causa en texto plano: *"El certificado fue revocado por su autoridad certificante. Aunque la firma es criptográficamente correcta, esta firma se considera INVÁLIDA por ese motivo."*

**Código de salida:** `0` si todas las firmas son válidas, `1` si alguna no lo es o si el proceso no pudo completarse (a diferencia de los firmadores, acá el exit code refleja el **resultado de la validación**, no solo si el proceso corrió sin errores; ambos casos usan el mismo código `1`, sin distinción entre "firma inválida" y "error de proceso").

**Mensajes de error posibles:** "El archivo XML no existe", "El documento XML no contiene firmas digitales", "Error al procesar el archivo XML", "Argumento no válido: [...]" (segundo argumento distinto de `-simple`).

**Ejemplo:**
```
java -jar XMLVerifySignatures.jar C:\docs\certificado-origen-signed.xml
java -jar XMLVerifySignatures.jar C:\docs\certificado-origen-signed.xml -simple
```

---

### 9.7 XMLVerifyXSDStructure

**Qué hace:** valida que un documento XML cumpla la estructura definida por un esquema XSD (tipos de dato, elementos obligatorios u opcionales, orden, restricciones de contenido) y además verifica sus firmas digitales (también aceptando SHA-1 y SHA-256, igual que `XMLVerifySignatures`).

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Archivo XML | Sí | Ruta al XML a validar |
| Archivo XSD | No | Esquema local; si se omite, se busca y descarga automáticamente el referenciado dentro del propio XML |

**¿Es obligatorio indicar un XSD externo? No.** El módulo busca automáticamente una referencia al esquema **dentro del propio XML**, revisando en este orden: el atributo `xsi:schemaLocation` (par namespace + URL, se toma el último token) y, si no está, `xsi:noNamespaceSchemaLocation`. Si encuentra una URL ahí, **la descarga automáticamente** (requiere Internet, con reintento automático alternando `http`↔`https` si el protocolo declarado falla) y valida contra ese esquema.

**¿Puedo indicar un XSD propio en vez del referenciado?** Sí, como segundo argumento — útil para validar contra una versión local sin depender de Internet, o cuando el XML no declara ningún esquema. Si el XML sí declara uno y el nombre de archivo no coincide con el indicado, el programa avisa (no es un error) y usa el que se le pasó:

```
NOTA: Diferencia en nombres de archivo XSD
├─ XSD referenciado en XML: esquema-v2.xsd
├─ XSD proporcionado: esquema-v1-local.xsd
└─ Se utilizará el archivo proporcionado: esquema-v1-local.xsd
```

**Qué se valida exactamente** (dos pasos independientes, ambos deben pasar):
1. Estructura del XML contra el XSD (tipos, elementos obligatorios, cardinalidad, restricciones), usando **XML Schema 1.0** (`http://www.w3.org/2001/XMLSchema`) — no 1.1.
2. Firmas digitales presentes en el documento (integridad y validez del certificado, sin restricción de algoritmo de hash).

**Importante — no valida revocación.** A diferencia de `XMLVerifySignatures`, este módulo lo indica explícitamente al finalizar: *"Este proceso no realiza validación de revocación de las firmas digitales aplicadas"*. Si hace falta confirmar que el certificado no fue revocado (sección 7.5), hay que correr además `XMLVerifySignatures` sobre el mismo archivo.

**Requiere Internet** solo si no se indicó un XSD local **y** el XML referencia uno por URL — en ese caso descarga el esquema. Sin conexión en ese escenario, falla con un error claro en vez de continuar sin validar.

**Dominio espejo para esquemas ALADI (COD/DJO) caídos.** El dominio oficial de ALADI (`https://www.codaladi.org/directorio/...`, usado en los `xsi:schemaLocation` de los Certificados de Origen y Declaraciones Juradas de Origen) suele estar inoperativo. Cuando el esquema se resuelve **automáticamente** desde el XML (nunca si se indicó un archivo XSD local como segundo argumento) y la descarga desde `codaladi.org` falla — ya sea por un error de red/TLS, o porque lo que devuelve no es un esquema XSD válido (por ejemplo, una redirección HTTP→HTTPS que Java no sigue automáticamente entre protocolos, y que termina descargando una página HTML de error en vez del esquema) — el módulo reintenta automáticamente contra un espejo secundario, **manteniendo el nombre de archivo del XSD intacto**: reemplaza `https://www.codaladi.org/directorio/` por `https://cod.certificadoorigen.com.ar/`. Antes de reintentar, informa por consola: *"El dominio ALADI https://www.codaladi.org/ no está operativo. Se usará el dominio secundario https://cod.certificadoorigen.com.ar/ para completar la operación."* Si el espejo tampoco responde, ahí sí se informa el error final. El resto del proceso de validación sigue el curso normal.

**Nota técnica sobre seguridad XML:** para poder descargar y resolver esquemas XSD remotos, este módulo habilita explícitamente el acceso externo a DTD y esquemas (`javax.xml.accessExternalSchema`/`accessExternalDTD = "all"`) tanto a nivel de fábrica XML como de propiedad de sistema del proceso Java. Esto es necesario para su función (validar contra esquemas publicados en Internet) pero implica que, a diferencia de un parser XML endurecido por defecto, este módulo no aplica el bloqueo estricto de entidades externas — se recomienda no usarlo para validar XML de origen no confiable sin las debidas precauciones de red.

**Sintaxis:**
```
java -jar XMLVerifyXSDStructure.jar <archivo.xml> [esquema.xsd]
java -jar XMLVerifyXSDStructure.jar [-version | -ayuda | -licencia]
```

**Mensajes de error posibles:** "El archivo XML no existe", "El archivo XSD no existe", "No se encontró referencia a esquema XSD en el XML y no se proporcionó archivo XSD", "Error al descargar el XSD", "El contenido descargado no es un esquema XSD válido (¿redirección, página de error, o dominio inoperativo?)" (por cada URL que falla, incluida la advertencia intermedia al detectar `codaladi.org` caído), "Error al procesar el archivo XSD", "Error de validación XML", "El documento XML no contiene firmas digitales" (informativo, no detiene el proceso), "Se encontraron errores en la validación del documento XML", "Error: No hay conexión a Internet disponible" (al intentar descargar un XSD referenciado).

**Ejemplo:**
```
java -jar XMLVerifyXSDStructure.jar C:\docs\certificado-origen.xml
java -jar XMLVerifyXSDStructure.jar C:\docs\certificado-origen.xml C:\xsd\esquema-v2.xsd
```

---

### Opciones específicas de los firmadores de PDF

`PDFSignerPKCS11`, `PDFSignerPKCS12` y `PDFSignerWindowsCSP` comparten estas opciones, que **no tienen equivalente en los firmadores de XML** — un XML no tiene "apariencia visual" ni concepto de página:

- **Firma visible vs. invisible.** Si no se indican `-x`/`-y` (o ambos quedan en `0`), la firma es criptográficamente válida pero no se dibuja nada en el documento — es una firma "invisible", tan válida como cualquier otra. Si se indican coordenadas, se dibuja un recuadro con el nombre del firmante y la fecha (más el texto de `-t`, si se indicó).
- **Sistema de coordenadas.** PDF usa el sistema estándar de PostScript: el origen `(0,0)` es la **esquina inferior izquierda** de la página, el eje X crece hacia la derecha y el eje Y crece **hacia arriba**. `-x`/`-y` ubican la esquina **inferior izquierda** del recuadro de firma (que mide 160×70 puntos, tamaño fijo) — no es "de abajo a la derecha hacia arriba", es de abajo a la **izquierda**. Un punto PDF equivale a 1/72 de pulgada. La firma siempre se coloca en la página 1.
- **Texto personalizado (`-t`).** Se agrega debajo del nombre del firmante y la fecha, dentro del mismo recuadro visible — útil para el cargo del firmante o el motivo de la firma. No tiene efecto si la firma es invisible.
- **Bloquear el documento (`-k`/`-l true`, según el módulo).** Hace dos cosas, siempre acopladas entre sí en los tres firmadores desde la 1.1.0-beta.1 (ver nota de corrección más abajo):
  1. Marca la firma como **firma certificante** (permiso PDF `DocMDP` = "no se permite ningún cambio"), no una firma de aprobación común — el documento queda declarado como no modificable ante cualquier lector conforme (Acrobat, etc.). Solo puede haber **una** firma certificante por documento, y debe ser la primera.
  2. Aplica cifrado estándar AES-256 al PDF resultante, restringiendo los permisos a solo impresión y uso con lectores de pantalla — no se permite copiar texto, editar, ni rellenar formularios.

  **Si se planea agregar más firmas al mismo documento más adelante, no usar el bloqueo en la primera.**

  > **Corrección de consistencia (1.1.0-beta.1):** hasta antes de esta corrección, `PDFSignerPKCS12` aplicaba la certificación DocMDP solo cuando además se pedía una firma visible (`-x`/`-y` distintos de `0`); si se pedía bloqueo con firma invisible, el documento quedaba cifrado pero sin la certificación "sin cambios permitidos". Se corrigió para que los tres firmadores de PDF (`PDFSignerPKCS11`, `PDFSignerPKCS12`, `PDFSignerWindowsCSP`) apliquen siempre los dos efectos juntos, sin importar si la firma es visible o invisible.

- **Validación de firmas preexistentes antes de firmar.** Los tres firmadores de PDF verifican, antes de agregar una nueva firma, que cualquier firma ya presente en el documento sea íntegra — si alguna no lo es, el proceso se detiene sin modificar el archivo. Esta validación se agregó a `PDFSignerWindowsCSP` en la 1.1.0-beta.1 para igualarlo con `PDFSignerPKCS11`/`PDFSignerPKCS12` (antes solo estos dos la hacían).

**Errores comunes a los tres firmadores de PDF:** "El archivo PDF no existe o no es accesible", "El PDF está encriptado y no puede ser firmado" (no se puede firmar un PDF que ya tiene una restricción de cifrado previa), "La firma existente '[nombre]' no es válida" (si el PDF ya tenía una firma corrupta, se rechaza antes de agregar una nueva).

### 9.8 PDFSignerPKCS11

**Qué hace:** firma digitalmente un documento PDF usando un token PKCS#11, con firma visible opcional (ver opciones arriba). Aplica una firma detached CMS/PKCS#7 sobre el hash del documento, con verificación previa de firmas existentes.

**Sintaxis:**
```
java -jar PDFSignerPKCS11.jar -i <archivo.pdf> -l <lib-pkcs11> -p <password> -s <slot> [-k true|false] [-x pos] [-y pos] [-t "texto"]
java -jar PDFSignerPKCS11.jar [-v | -h | --license | --listar-drivers]
```

**Parámetros:**

| Flag | Obligatorio | Descripción |
|---|---|---|
| `-i`, `--input` | Sí | Archivo PDF a firmar |
| `-l`, `--library` | Sí | Ruta a la biblioteca PKCS#11 |
| `-p`, `--password` | Sí | PIN del token |
| `-s`, `--slot` | Sí | Número de slot |
| `-k`, `--lock` | No (default `false`) | Bloquea el documento contra modificaciones posteriores a la firma (certificación DocMDP + cifrado, ver nota arriba) |
| `-x`, `--xpos` / `-y`, `--ypos` | No (default `0`) | Posición de una firma visible; si ambas quedan en `0`, la firma es invisible |
| `-t`, `--text` | No | Texto adicional a mostrar en la firma visible |

**Salida:** archivo `<nombre>-signed.pdf`. Si el token necesitó el mecanismo de hash externo (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)), se informa por consola: *"Mecanismo de firma: hash SHA-256 externo (token sin CKM_SHA256_RSA_PKCS)"*.

**Mensajes de error posibles:** "El archivo PDF no existe o no es accesible", "La biblioteca PKCS#11 no existe o no es accesible", "El PDF está encriptado y no puede ser firmado", "La firma existente '[nombre]' no es válida", "El token no contiene una clave privada válida", "No se encontró una cadena de certificados válida en el token", "Error al acceder a la clave privada del token", "El token no admite ningún mecanismo de firma RSA-SHA256 compatible (ni interno ni externo)".

**Posiciones útiles para Certificados de Origen no preferenciales** (convención Grupo Sauken): `-x 40 -y 55` para la firma del Exportador, `-x 310 -y 55` para la firma del Funcionario Habilitado.

**Ejemplo:**
```
java -jar PDFSignerPKCS11.jar -i C:\docs\certificado.pdf -l C:\Windows\System32\eTPKCS11.dll -p "MiPIN123" -s 0 -k true -x 40 -y 55 -t "Exportador"
```

---

### 9.9 PDFSignerPKCS12

**Qué hace:** idéntico a `PDFSignerPKCS11` pero usando un archivo PKCS#12.

**Sintaxis:**
```
java -jar PDFSignerPKCS12.jar -i <archivo.pdf> -c <certificado.p12> -p <password> [-l true|false] [-x pos] [-y pos] [-t "texto"]
java -jar PDFSignerPKCS12.jar [-v | -h | --license]
```

**Parámetros:**

| Flag | Obligatorio | Descripción |
|---|---|---|
| `-i`, `--input` | Sí | Archivo PDF a firmar |
| `-c`, `--certificate` | Sí | Ruta al archivo del certificado PKCS#12 |
| `-p`, `--password` | Sí | Contraseña del certificado |
| `-l`, `--lock` | No (default `false`) | Bloquea el documento contra modificaciones posteriores a la firma (certificación DocMDP + cifrado) |
| `-x`, `--xpos` / `-y`, `--ypos` | No (default `0`) | Posición de una firma visible; si ambas quedan en `0`, la firma es invisible |
| `-t`, `--text` | No | Texto adicional a mostrar en la firma visible |

**Atención:** en este módulo el flag de bloqueo es `-l`/`--lock` (no `-k`) — es una diferencia histórica de nomenclatura entre este módulo y los otros dos firmadores de PDF.

**Mensajes de error posibles:** "El archivo PDF no existe o no es accesible", "El archivo de certificado no existe o no es accesible", "El PDF está encriptado y no puede ser firmado", "La firma existente '[nombre]' no es válida", "El archivo de certificado no contiene certificados", "El certificado no contiene una clave privada", "No se encontró una cadena de certificados válida", "Error al acceder a la clave privada".

**Ejemplo:**
```
java -jar PDFSignerPKCS12.jar -i C:\docs\certificado.pdf -c C:\certificados\empresa.pfx -p "MiContraseña123" -l true -x 40 -y 55
```

---

### 9.10 PDFVerifySignatures

**Qué hace:** verifica la validez de las firmas digitales de un documento PDF — integridad, autenticidad, estado de revocación, y si el documento fue modificado luego de la última firma. Acepta firmas SHA-256 y SHA-1 de cualquier aplicación conforme (ver [sección 7.6](#76-compatibilidad-con-firmas-sha-1-de-aplicaciones-de-terceros)).

**Sintaxis:**
```
java -jar PDFVerifySignatures.jar <archivo.pdf> [-simple]
java -jar PDFVerifySignatures.jar [-version | -ayuda | -licencia]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Archivo PDF | Sí | Ruta al PDF firmado a verificar |
| `-simple` | No | Reduce el detalle de la salida |

**Salida:** por cada firma, cobertura del documento, integridad, fecha de firma, estado de revocación, firmante, organización, número de serie, período de validez, emisor, tipo y algoritmo de firma, y (si hay más de un certificado en la cadena) el listado completo de la cadena de certificación. Al final, estado consolidado del documento (bloqueado/encriptado).

**Código de salida:** `0` si todas las firmas son válidas, `1` si alguna no lo es (mismo criterio que `XMLVerifySignatures`).

**Mensajes de error posibles:** "El documento no contiene firmas digitales", "Error en firma [nombre]: [detalle]", "DOCUMENTO INVÁLIDO: Una o más firmas no son válidas", "Certificado revocado al momento de la firma", "Certificado no confiable o autofirmado", "No se pudo obtener el certificado firmante".

**Ejemplo:**
```
java -jar PDFVerifySignatures.jar C:\docs\certificado-signed.pdf
java -jar PDFVerifySignatures.jar C:\docs\certificado-signed.pdf -simple
```

---

### 9.11 XMLSignerWindowsCSP

**Qué hace:** firma XML usando un certificado ya presente en el almacén de certificados de Windows (CSP/KSP), como alternativa a `XMLSignerPKCS11` (ver [sección 7.3](#73-windows-cspksp-almacén-de-certificados-de-windows)). **Exclusivo de Windows.** Misma configuración criptográfica que los otros firmadores XML (XML-DSig, canonicalización inclusiva, RSA-SHA256).

**Sintaxis:**
```
java -jar XMLSignerWindowsCSP.jar <Alias o fragmento del Subject CN> <Archivo XML> <Elemento a firmar>
java -jar XMLSignerWindowsCSP.jar [-version | -ayuda | -licencia | -listar-certificados]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Alias o fragmento del CN | Sí | Alias exacto del certificado en el almacén, o un fragmento del nombre del titular que identifique un único certificado. Usar `-listar-certificados` para ver los disponibles |
| Archivo XML | Sí | — |
| Elemento a firmar | Sí (o `""`) | Mismas reglas que `XMLSignerPKCS11`, incluida la especialización ALADI/MERCOSUR (sección 10) |

**A diferencia de los módulos PKCS#11, no se pasa contraseña** — el acceso a la clave lo administra Windows (puede aparecer un diálogo nativo del sistema pidiendo el PIN).

**Mensajes de error posibles:** "Este módulo solo funciona en Windows [...]" (al ejecutarlo en otro SO), "No se encontró ningún certificado con clave privada que coincida con '[texto]'", "'[texto]' coincide con N certificados distintos. Sea más específico [...]", "El proveedor SunMSCAPI no está disponible en este JDK", más las [reglas de firma comunes](#reglas-de-firma-comunes-a-los-tres-firmadores-xml) (elemento ya firmado, orden CODEH/DJOEH, obligatoriedad de indicar elemento en XML de comercio exterior — [sección 10.5](#105-reglas-de-firma-obligatorias)).

**Ejemplo:**
```
java -jar XMLSignerWindowsCSP.jar "Juan Carlos Ríos" C:\docs\certificado-origen.xml COD
```

---

### 9.12 PDFSignerWindowsCSP

**Qué hace:** el equivalente de `XMLSignerWindowsCSP` para documentos PDF — mismas opciones de posición, texto y bloqueo que `PDFSignerPKCS11`, incluida la validación de firmas preexistentes (agregada en la 1.1.0-beta.1 para igualarlo con los otros dos firmadores de PDF). **Exclusivo de Windows.**

**Sintaxis:**
```
java -jar PDFSignerWindowsCSP.jar -i <archivo.pdf> -a <alias o fragmento CN> [-k true|false] [-x pos] [-y pos] [-t "texto"]
java -jar PDFSignerWindowsCSP.jar [-v | -h | --license | --listar-certificados]
```

**Parámetros:**

| Flag | Obligatorio | Descripción |
|---|---|---|
| `-i`, `--input` | Sí | Archivo PDF a firmar |
| `-a`, `--alias` | Sí | Alias exacto del certificado en el almacén de Windows, o un fragmento del CN que identifique uno solo. Usar `-listar-certificados` para ver los disponibles |
| `-k`, `--lock` | No (default `false`) | Bloquea el documento contra modificaciones posteriores a la firma (certificación DocMDP + cifrado) |
| `-x`, `--xpos` / `-y`, `--ypos` | No (default `0`) | Posición de una firma visible; si ambas quedan en `0`, la firma es invisible |
| `-t`, `--text` | No | Texto adicional a mostrar en la firma visible |

Tampoco pide contraseña — el acceso a la clave lo administra Windows.

**Mensajes de error posibles:** los mismos de acceso al almacén que `XMLSignerWindowsCSP`, más "El PDF está encriptado y no puede ser firmado" y "La firma existente '[nombre]' no es válida" (agregados en la 1.1.0-beta.1), más los propios de firma PDF ya listados en `PDFSignerPKCS11`.

**Ejemplo:**
```
java -jar PDFSignerWindowsCSP.jar -i C:\docs\certificado.pdf -a "Juan Carlos Ríos" -k true -x 40 -y 55
```

---

### 9.13 S-FiDE GUI

**Qué hace:** interfaz gráfica JavaFX que expone las 12 aplicaciones anteriores como pestañas, para usuarios que prefieren no operar por línea de comandos. Invoca los mismos `.jar` con `ProcessBuilder`, mostrando la salida combinada de `stdout`/`stderr` en un cuadro de texto y el código de resultado en un diálogo. El título de la ventana muestra la versión de S-FiDE en ejecución.

**No es una aplicación para integración por proceso** (no tiene un contrato de argumentos/exit-code pensado para ser invocada por otro programa) — se documenta acá por completitud del producto. Un integrador debe usar los módulos CLI individuales.

**Funciones adicionales relevantes para quien la use manualmente:**
- Recuerda la última ruta de biblioteca PKCS#11 / archivo PKCS#12 usada, en `sfide-defaults.properties`.
- Detección automática de driver PKCS#11 por marca/modelo (ver [sección 8](#8-catálogo-de-tokens-y-drivers-soportados)).
- Las pestañas de CSP/KSP (`XMLSignerWindowsCSP`/`PDFSignerWindowsCSP`) solo aparecen si la GUI corre en Windows.
- Validación de que todos los `.jar` necesarios estén presentes junto a `SFide-GUI.jar` antes de permitir su uso.

---

## 10. Especialización de comercio exterior ALADI/MERCOSUR: COD, CODEH, DJO y DJOEH

Esta sección documenta una funcionalidad **adicional** (add-on) que S-FiDE ofrece por encima del soporte estándar de firma XML, orientada específicamente al intercambio de documentación de comercio exterior entre países miembros de la ALADI (Asociación Latinoamericana de Integración), incluyendo el bloque MERCOSUR.

### 10.1 Contexto: por qué existe esta especialización

Los acuerdos comerciales entre países miembros de la ALADI (entre ellos los del MERCOSUR) obligan a las partes a intercambiar datos de comercio exterior mediante documentos XML normalizados. Dos tipos de documento son centrales en ese intercambio:

- **Certificado de Origen Digital**, cuyo contenido relevante se agrupa dentro de un elemento XML llamado **`COD`** (firma como Exportador) o **`CODEH`** (firma como Funcionario Habilitado del organismo emisor).
- **Declaración Jurada de Origen**, agrupada dentro de un elemento **`DJO`** (Exportador) o **`DJOEH`** (Funcionario Habilitado).

Estos documentos deben llevar **firmas digitales embebidas** exactamente sobre esos elementos — no sobre el documento completo — de modo que cada parte del documento (la declaración del exportador y la certificación del funcionario) quede firmada de manera independiente y verificable por separado, dentro del mismo archivo XML.

### 10.2 Estructura real de un COD/CODEH

Ejemplo real (esquema `codaladi.org/directorio/cod_ver_1.8.2.xsd`):

```
ns1:Envelope
  ns1:CertOrigin
    CODEH (id="CODEH")            ← elemento EXTERIOR, envuelve todo
      CODExporter
        COD (id="COD")            ← elemento INTERIOR, anidado dentro de CODEH vía CODExporter
          CODVer, CODSubmitterType, Agreement, FormA18...
        [firma del Exportador]    ← Reference="#COD", se agrega justo después de </COD>
      /CODExporter
      EH (EHId, EHCountry, EHName, EHAddress, EHCity, EHTelephone, EHFax, EHEmail, EHURL)
      CertificationEH (CertificateControlCode, CertificateDate, CertificateID)
    /CODEH
    [firma del Funcionario]       ← Reference="#CODEH", se agrega justo después de </CODEH>
  /ns1:CertOrigin
```

Cuatro etapas estrictamente secuenciales:
1. El XML se crea con `COD` completo (datos del exportador, acuerdo, formulario) dentro de `CODEH`/`CODExporter`. Sin firmas. En este momento **solo `COD` puede firmarse** — `CODEH` todavía no está "completo" (le faltan `EH`/`CertificationEH`).
2. El **Exportador** firma `COD`. La firma queda como hermana justo después de `</COD>`, dentro de `CODExporter`.
3. El sistema externo de gestión de certificados (no S-FiDE) agrega `<EH>` y `<CertificationEH>` como hijos nuevos de `CODEH`, después de `</CODExporter>` y antes de `</CODEH>`. `COD` y su firma quedan intactos.
4. Recién ahí — con `CertificationEH` ya presente — el **Funcionario Habilitado** puede firmar `CODEH`. Esa firma queda como hermana justo después de `</CODEH>`, dentro de `CertOrigin`. Su digest cubre todo el subárbol de `CODEH`, incluida la firma del Exportador ya embebida (protegiéndola también a ella de cualquier alteración posterior).

**Regla de orden** (no se puede firmar `CODEH` sin una firma válida en `COD`, ni sin que `CODEH` tenga los datos de certificación): la aplica y garantiza el sistema externo que orquesta las llamadas a S-FiDE — **S-FiDE no la conoce ni la valida**. Los firmadores solo firman el elemento por `Id` que se les indique, cuando se los invoque.

**Confirmado contra el código, sin ninguna lógica especial para COD/CODEH:** `XMLSignerPKCS11.createSignatureContext()` construye el `DOMSignContext` con el nodo padre y el hermano siguiente del elemento encontrado por `Id` (`new DOMSignContext(privateKey, elementToSign.getParentNode(), elementToSign.getNextSibling())`), lo que coloca la firma exactamente como hermana justo después del cierre del elemento firmado. Es una consecuencia genérica del mecanismo de firma-por-`Id` — no hay nada hardcodeado para `COD`/`CODEH`, y por eso funciona igual para `DJO`/`DJOEH` (sección 10.4) y para cualquier otro documento con esta estructura de dos etapas.

**Nombre de archivo de un COD:** el contenido de `<CertificateID>` más extensión `.xml` — p. ej. `AR001A18170000043000.xml`, donde `AR`=país, `001`=código de entidad ALADI (`EHId`), `A18`=código de acuerdo comercial, `17`=año, `00000430`=número de certificado, `00`=sin uso actual. Se recomienda enviar el COD al importador dentro de un ZIP, por cualquier medio digital.

### 10.3 Estructura real de un DJO/DJOEH

La misma mecánica de dos etapas, para una **Declaración Jurada de Origen** en vez de un Certificado de Origen. Estructura real observada:

```
ns1:Envelope
  ns1:Affidavit                   ← raíz distinta a la de COD (CertOrigin)
    DJOEH (id="DJOEH")
      DJOExporter
        DJO (id="DJO")
          DJOVer, DJOSubmitterType, Agreement, Exporter, Producer,
          Declaration (DeclarationDate), FormDJO...
        [firma del Exportador]    ← Reference="#DJO", justo después de </DJO>
      /DJOExporter
      EH (EHId, EHCountry, EHName, EHAddress, EHCity, EHTelephone, EHEmail, EHURL)
      ApprovalEH (ApprovalNumber, ApprovalDate, ROMCompliance)
    /DJOEH
    [firma del Funcionario]       ← Reference="#DJOEH", justo después de </DJOEH>
  /ns1:Affidavit
```

Mismas cuatro etapas que COD/CODEH (firmar `DJO` → agregar `EH`/`ApprovalEH` → firmar `DJOEH`), con nombres de elemento propios: el bloque de certificación del funcionario se llama **`ApprovalEH`** (no `CertificationEH`), con campos `ApprovalNumber`/`ApprovalDate`/`ROMCompliance` (una declaración de cumplimiento del Régimen de Origen Mercosur), no `CertificateControlCode`/`CertificateDate`/`CertificateID`.

### 10.4 Validación de revocación por elemento — la regla completa y verificada

Un documento completo (COD o DJO) contiene **dos firmas independientes**, producidas en momentos reales distintos por titulares distintos. `XMLVerifySignatures` extrae, para cada firma, la fecha correcta según a qué elemento apunta su `Reference`, en vez de usar la fecha actual del sistema:

| Referencia | Campo de fecha | Campo de país | Conversión horaria |
|---|---|---|---|
| `#COD` | `<DeclarationDate>` | `<ExporterCountry>` | Sí — hora local del país exportador → UTC, según tabla interna de husos horarios |
| `#CODEH` | `<CertificateDate>` | `<EHCountry>` | Sí — hora local del país de la **Entidad Habilitada** → UTC (no el del exportador) |
| `#DJO` | `<DeclarationDate>` | — (no aplica) | **No.** El valor se toma literalmente como UTC, sin ninguna conversión |
| `#DJOEH` | `<ApprovalDate>` | — (no aplica) | **No.** Misma regla que DJO: literal como UTC |

La tabla interna de husos horarios (`TimezoneConverter`) cubre: Argentina, Bolivia, Brasil, Chile, Colombia, Cuba, Ecuador, México, Panamá, Paraguay, Perú, Uruguay y Venezuela.

**Por qué DJO/DJOEH no convierten huso horario:** a diferencia de un Certificado de Origen (que involucra a un exportador y una autoridad certificante que pueden, en teoría, estar en países distintos del acuerdo), una Declaración Jurada de Origen se opera siempre dentro de un mismo país — el que emite el XML. No hay necesidad de resolver una zona horaria distinta: el valor de fecha ya representa el instante correcto tal cual está escrito.

**Por qué `CODEH` usa `EHCountry` y no `ExporterCountry`:** la firma sobre `CODEH` la aplica el **Funcionario Habilitado**, actuando en nombre de la Entidad Habilitada — su acto de firma ocurre en el país de esa entidad, no necesariamente en el país del exportador. Usar el país equivocado desplazaría la fecha de referencia por el offset horario incorrecto, pudiendo evaluar la revocación contra el instante equivocado. *(Esta distinción se corrigió el 2026-08-29: la implementación original usaba `ExporterCountry` también para `CODEH`.)*

Si la referencia de una firma no es ninguna de las cuatro anteriores, `XMLVerifySignatures` recurre a la fecha actual del sistema, con una advertencia informativa indicando que no reconoció ese identificador.

> **Este comportamiento es intencional y debe preservarse tal cual.** No es una limitación a "completar": es la especialización exacta que requiere el caso de uso de comercio exterior ALADI/MERCOSUR, verificada contra ejemplos reales de COD y DJO.

### 10.5 Reglas de firma obligatorias

Además de la regla genérica de no volver a firmar un elemento ya firmado (ver [reglas de firma comunes](#reglas-de-firma-comunes-a-los-tres-firmadores-xml), aplicable a cualquier XML), los tres firmadores XML aplican una regla de orden específica para esta especialización, reflejando exactamente la secuencia operativa real descrita en 10.2/10.3:

- **`CODEH` no puede firmarse si `COD` no tiene ya una firma digital aplicada.**
- **`DJOEH` no puede firmarse si `DJO` no tiene ya una firma digital aplicada.**

Ambas verificaciones son de **existencia**, no de validez criptográfica completa: el firmador confirma que hay una `<ds:Signature>` cuya `Reference` apunta al elemento requerido (`COD` o `DJO`), sin volver a verificar esa firma criptográficamente — mantiene la regla simple, consistente con que la disciplina de orden real (ver 10.2) la garantiza el sistema externo que orquesta las llamadas a S-FiDE, no S-FiDE mismo actuando como autoridad de validación completa en el momento de firmar.

**Mensajes de error:**
- `"No se puede firmar el elemento CODEH: no existe una firma digital previa sobre el elemento COD."`
- `"No se puede firmar el elemento DJOEH: no existe una firma digital previa sobre el elemento DJO."`

**Regla adicional — no se permite firmar el documento completo.** Los tres firmadores XML detectan automáticamente si el XML es un documento de comercio exterior: basta con que exista, en cualquier parte del documento, un elemento con `Id`/`id`/`ID` igual a `COD` o a `DJO` (no hace falta que sea justo el elemento que se está por firmar). Si se detecta esta condición y el elemento a firmar indicado es la cadena vacía `""` (equivalente a firmar todo el documento), la operación se rechaza — en un XML de comercio exterior siempre hay que indicar explícitamente qué elemento firmar (`COD`, `CODEH`, `DJO` o `DJOEH`, según corresponda a la etapa). Esta regla se suma a las anteriores, no las reemplaza.

**Mensaje de error:** `"Este XML corresponde a un documento de comercio exterior (un Certificado de Origen Digital / una Declaración Jurada de Origen). No se permite firmar el documento completo: debe indicarse un elemento específico a firmar."`

**Mensajes informativos al firmar un documento de comercio exterior.** Cuando la firma se aplica sobre un XML detectado como COD o DJO (nunca en un XML estándar, que no genera ningún mensaje de este tipo), el firmador informa por consola, antes del mensaje de éxito habitual:
- `"El XML a firmar es un Certificado de Origen Digital de ALADI (sin verificación de contenido)."` o `"El XML a firmar es una Declaración Jurada de Origen (sin verificación de contenido)."` — la aclaración "sin verificación de contenido" es intencional: S-FiDE no valida si el documento está completo o corresponde a la etapa operativa correcta (ver [sección 10.2](#102-estructura-real-de-un-codcodeh)/[10.3](#103-estructura-real-de-un-djodjoeh)) — eso es responsabilidad del sistema externo que orquesta la firma.
- `"Elemento firmado: " + <elemento>` — confirma exactamente qué elemento (`COD`, `CODEH`, `DJO` o `DJOEH`) recibió la firma en esa invocación.

### 10.6 Sensibilidad a mayúsculas/minúsculas

El nombre del elemento a firmar es sensible a mayúsculas y minúsculas. Para Certificados de Origen Digitales y Declaraciones Juradas de Origen, usar siempre los identificadores en mayúsculas (`COD`, `CODEH`, `DJO`, `DJOEH`) tal como los reconoce esta especialización.

---

## 11. Integración desde otras aplicaciones

El patrón recomendado por Grupo Sauken para invocar un módulo desde otra aplicación es: **redirigir `stdout` y `stderr` a archivos separados, y capturar el código de salida del proceso**, para que la aplicación integradora los lea una vez que el proceso terminó.

### Ejemplo en Windows (.bat)

```bat
@echo off
set SFIDE=C:\ruta\a\S-FiDE
C:
cd %SFIDE%
set JAVA_HOME=%SFIDE%\openjdk-23.0.1\windows-x64
set PATH=%JAVA_HOME%\bin;%PATH%

%JAVA_HOME%\bin\java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar PKCS12CertificateExtractor.jar "C:\ruta\al\certificado.pfx" "<Contraseña>" 1>"C:\ruta\temporal\salida.txt" 2>"C:\ruta\temporal\error.txt"

set RESULT=%ERRORLEVEL%
echo %RESULT% > "C:\ruta\temporal\result.txt"
exit /b %RESULT%
```

**Explicación línea por línea:**
- `set JAVA_HOME=...` / `set PATH=...`: apuntan al runtime de Java **embebido en la propia distribución de S-FiDE**, sin depender de que el sistema tenga Java instalado.
- `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`: fuerzan la codificación UTF-8 tanto de la salida como de argumentos y rutas con acentos o caracteres especiales — se recomienda incluir siempre estos dos parámetros al invocar cualquier módulo.
- `1>archivo` redirige `stdout`, `2>archivo` redirige `stderr`, a archivos separados que la aplicación integradora puede leer después de que el proceso termine.
- `%ERRORLEVEL%` es el código de salida del proceso de Java, sin transformación: `0` éxito, `1` error.
- Los archivos de salida quedan en UTF-8 — hay que leerlos como tales desde la aplicación integradora, o los acentos se van a ver incorrectos.

Este mismo patrón sirve para **cualquiera** de los 12 módulos — solo cambia el nombre del `.jar` y sus argumentos.

### Ejemplo equivalente en Linux/macOS (shell)

```bash
#!/bin/sh
SFIDE=/opt/S-FiDE
cd "$SFIDE"
JAVA_HOME="$SFIDE/openjdk-23.0.1/linux-x64"   # en macOS: "$SFIDE/openjdk-23.0.1/macos"
PATH="$JAVA_HOME/bin:$PATH"

"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 \
    -jar PKCS12CertificateExtractor.jar "/ruta/al/certificado.pfx" "<Contraseña>" \
    >"/tmp/salida.txt" 2>"/tmp/error.txt"

RESULT=$?
echo $RESULT > "/tmp/result.txt"
exit $RESULT
```

---

## 12. Distribución y despliegue

Una distribución de S-FiDE lista para usar es una carpeta autocontenida con esta estructura:

```
S-FiDE/
├── openjdk-23.0.1/          ← runtime de Java embebido (por plataforma)
├── javafx-sdk-23.0.1/       ← SDK de JavaFX embebido (por plataforma)
├── *.jar                    ← los 13 módulos, con nombre "amigable" sin versión (ver más abajo)
├── SFide-GUI.bat / .sh      ← launchers, se autodetectan solos (no dependen de una letra de unidad fija)
├── sfide-defaults.demo.properties
├── Leeme.txt
├── doc/                     ← este manual
├── test/                    ← documentos de ejemplo
└── xsd/                     ← esquemas de ejemplo
```

**No requiere instalación ni Java preinstalado** — puede copiarse a cualquier ubicación, incluido un medio removible, y ejecutarse desde ahí en un equipo "limpio" (Windows, Linux o macOS), gracias al runtime embebido y a que los launchers se autodetectan (no hay ninguna ruta ni letra de unidad hardcodeada).

**Convención de nombres de jar — importante para integradores:** el nombre del `.jar` de distribución (`XMLSignerPKCS11.jar`) **nunca** incluye el número de versión, a diferencia del artefacto crudo que genera Maven en `target/` (`xml_signer_pkcs11-1.1.0-beta.1-jar-with-dependencies.jar`). Esto es deliberado: un integrador que ya tiene el nombre del jar hardcodeado en su propio código no debe romperse cuando S-FiDE actualiza de versión.

El script `install.bat` (incluido en el repositorio) automatiza la generación de una carpeta de distribución completa a partir del código fuente compilado, incluyendo opcionalmente los runtimes embebidos si se le indica una carpeta "vendor" de referencia.

---

## 13. Historial de versiones

### v1.1.0-beta.1 (2026-08) — en QA

- Compatibilidad ampliada de tokens PKCS#11: soporte de mecanismo de hash externo (`CKM_RSA_PKCS`) para tokens que no exponen el mecanismo combinado (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)).
- Autodetección de marca/modelo de token y ayuda de selección de driver (GUI y `-listar-drivers`).
- Dos módulos nuevos: `XMLSignerWindowsCSP` y `PDFSignerWindowsCSP` (firma vía almacén de certificados de Windows).
- Actualización de dependencias criptográficas (BouncyCastle, iText, Apache Santuario) por alertas de seguridad.
- Corrección de dos errores encontrados durante la QA con hardware real (SafeNet 5110+ L3): fallo de firma XML con tokens reales (`Mechanism DOM not available`) y una recursión infinita relacionada.
- **Auditoría completa de código y corrección de inconsistencias entre módulos hermanos** (revisión posterior a la QA de hardware):
  - Se eliminó un stack trace de Java que quedaba expuesto en `XMLVerifySignatures` al fallar la búsqueda del certificado emisor.
  - `PDFSignerPKCS12` ahora aplica siempre la certificación DocMDP junto con el cifrado al bloquear el documento, sin importar si la firma es visible o invisible (antes solo la aplicaba con firma visible).
  - `PDFSignerWindowsCSP` ahora valida la integridad de las firmas preexistentes y rechaza firmar un PDF ya encriptado, igualándolo con `PDFSignerPKCS11`/`PDFSignerPKCS12`.
  - Se unificó el vocabulario de comandos especiales (versión/ayuda/licencia/catálogos) en los 12 módulos de línea de comandos — ver [sección 9](#9-catálogo-de-aplicaciones).
  - Se corrigieron afirmaciones de esta documentación no respaldadas por el código (versión de PKCS#11/PKCS#12, soporte de XAdES, versión de XML Schema, tiempos de espera de OCSP/CRL).
- **Especialización ALADI/MERCOSUR completada (COD/CODEH/DJO/DJOEH)** (ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)):
  - Se corrigió un error real en `XMLVerifySignatures`: la validación de revocación de la firma sobre `CODEH` usaba el país del exportador (`ExporterCountry`) para convertir la fecha a UTC; ahora usa correctamente el país de la Entidad Habilitada (`EHCountry`), que es quien realmente firma ese elemento.
  - Se agregó soporte de extracción de fecha para `DJO` (desde `DeclarationDate`) y `DJOEH` (desde `ApprovalDate`) — a diferencia de COD/CODEH, ambos se toman literalmente como UTC, sin conversión de huso horario, ya que una Declaración Jurada de Origen se opera siempre dentro de un mismo país.
  - Verificado end-to-end contra archivos DJO reales (etapas: sin firmar → firmado por Exportador → con datos de Entidad Habilitada → firmado por Funcionario Habilitado).
  - Se agregaron dos reglas de firma obligatorias a los tres firmadores XML: nunca firmar un elemento que ya tiene una firma digital aplicada (regla genérica, cualquier XML), y nunca firmar `CODEH`/`DJOEH` sin una firma previa sobre `COD`/`DJO` respectivamente — ver [sección 10.5](#105-reglas-de-firma-obligatorias).
  - Se revisaron y aclararon los mensajes de `XMLVerifySignatures` en torno a la revocación: el estado de integridad criptográfica ahora se etiqueta explícitamente como previo a la revocación, se agregó un "Estado final de la firma" por firma que sí combina ambos criterios, y un certificado revocado ahora explica en texto plano por qué invalida la firma.
  - `XMLVerifyXSDStructure` ahora reintenta automáticamente contra un dominio espejo (`cod.certificadoorigen.com.ar`) cuando el esquema referenciado es del dominio oficial de ALADI (`codaladi.org`, habitualmente inoperativo) y la descarga falla — incluyendo el caso de una redirección HTTP→HTTPS entre protocolos que Java no sigue automáticamente y que antes se descargaba como si fuera el XSD (produciendo un error de parseo confuso en vez de reintentar). Ver [sección 9.7](#97-xmlverifyxsdstructure).
  - Los tres firmadores XML ahora prohíben firmar el documento completo (elemento vacío `""`) cuando detectan un XML de comercio exterior (contiene un elemento `COD` o `DJO`) — debe indicarse explícitamente qué elemento firmar. Al firmar sobre uno de estos documentos, se informa además en consola de qué tipo de documento se trata ("sin verificación de contenido") y qué elemento recibió la firma. Ver [sección 10.5](#105-reglas-de-firma-obligatorias).
- **Validado de punta a punta** con token SafeNet real y con PKCS#12 — pendiente de validación con ePass2003 y mToken CryptoID.

### v1.0.0 (2024-12) — primer release estable

Suite inicial de 10 módulos (extracción de certificados, firma y verificación XML/PDF vía PKCS#11 y PKCS#12, validación de estructura XSD) más la GUI JavaFX.

### Guía de migración desde 1.0.0

Si ya tenías una integración funcionando contra los jars de S-FiDE 1.0.0, la gran mayoría de los cambios de 1.1.0-beta.1 son **aditivos** (flags opcionales nuevos, módulos nuevos) y no requieren ningún cambio de tu lado. Esta guía identifica puntualmente los pocos casos donde cambió el **comportamiento** de un jar que ya usabas en 1.0.0, para que sepas exactamente qué revisar antes de actualizar. No aplica a `XMLSignerWindowsCSP.jar`/`PDFSignerWindowsCSP.jar`: son módulos nuevos, no existían en 1.0.0, así que no hay nada que "migrar" ahí.

| Jar (ya existía en 1.0.0) | Qué cambió en 1.1.0-beta.1 | ¿Puede romper una integración existente? | Qué revisar |
|---|---|---|---|
| `XMLSignerPKCS11.jar`, `XMLSignerPKCS12.jar` | Tres reglas de firma nuevas (secciones ["reglas de firma comunes"](#reglas-de-firma-comunes-a-los-tres-firmadores-xml) y [10.5](#105-reglas-de-firma-obligatorias)): (1) ya no se puede volver a firmar un elemento que ya tiene una firma digital aplicada; (2) no se puede firmar `CODEH`/`DJOEH` sin una firma previa sobre `COD`/`DJO`; (3) si el XML contiene un elemento `COD` o `DJO`, ya no se admite `""` (documento completo) como elemento a firmar. | **Sí, pero solo si tu integración alguna vez firmaba dos veces el mismo elemento, firmaba `CODEH`/`DJOEH` fuera de orden, o firmaba con elemento vacío un XML que contiene `COD`/`DJO`.** Antes esas llamadas terminaban con éxito (código `0`), aunque el resultado no fuera el esperado; ahora terminan con código `1` y un mensaje de error explícito. | Si tu flujo siempre firmó el elemento correcto, una sola vez, en el orden correcto, no hay nada que cambiar — es exactamente lo que ya hacías. Si tenías alguna lógica de reintento que pudiera volver a invocar el firmador sobre el mismo archivo/elemento, revisala. |
| `PDFSignerPKCS12.jar` | Al usar `-l true` (bloquear) con una firma invisible (sin `-x`/`-y`), antes solo se aplicaba el cifrado AES-256; ahora también se aplica la certificación DocMDP ("sin cambios permitidos"), igual que ya hacía `PDFSignerPKCS11`. | **Sí, si tu flujo agrega más de una firma al mismo PDF y alguna de las que no era la última usaba `-l true` de forma invisible.** Una firma certificante (DocMDP) debe ser siempre la primera y única de su tipo — si tu proceso agregaba firmas posteriores a un PDF "bloqueado invisible" con `PDFSignerPKCS12`, eso ahora falla al llegar a la firma siguiente. | Si usás `-l true` únicamente en la **última** firma que aplicás a cada documento, no hay nada que cambiar. Si lo usabas antes esperando "solo cifrar, sin certificar, para poder seguir firmando después", movelo a la última firma del flujo. |
| `XMLVerifySignatures.jar` | Al verificar una firma sobre el elemento `CODEH` para revocación, antes se usaba el país del exportador (`ExporterCountry`) para convertir la fecha a UTC; ahora se usa correctamente el país de la Entidad Habilitada (`EHCountry`) — ver [sección 10.4](#104-validación-de-revocación-por-elemento--la-regla-completa-y-verificada). | **Solo si alguno de tus documentos COD tiene al exportador y a la Entidad Habilitada en países distintos**, y el certificado del funcionario tiene una revocación cerca de la fecha de certificación. En ese caso puntual, el resultado `VÁLIDO`/`REVOCADO` de esa firma puede diferir del que obtenías en 1.0.0 (que usaba el país incorrecto). | Si en tus documentos el exportador y la Entidad Habilitada están siempre en el mismo país, no hay diferencia observable. Si no, volvé a verificar los `CODEH` cercanos a una fecha de revocación conocida. |
| `XMLVerifySignatures.jar`, `PDFVerifySignatures.jar` | Cambió el texto exacto de algunas líneas de salida: la etiqueta "Estado: VÁLIDA/INVÁLIDA" ahora es "Estado (integridad criptográfica, sin considerar revocación): ..." y se agregó una línea nueva "Estado final de la firma #N: ...". **El código de salida (`0`/`1`) no cambió.** | **Solo si tu integración lee y compara texto de `stdout` en vez de usar el código de salida del proceso** — algo que este manual siempre desaconsejó (ver [sección 6](#6-arquitectura-de-integración): el exit code es lo único que hace falta chequear). | Si tu integración ya usaba el exit code como corresponde, no te afecta nada de esto. Si estabas buscando el texto literal "Estado: VÁLIDA" en la salida, actualizá esa búsqueda o —mejor— pasá a usar el exit code. |
| `TokenSlotsView.jar`, `TokenCertificateExtractor.jar`, `PKCS12CertificateExtractor.jar`, `XMLVerifyXSDStructure.jar`, `PDFSignerPKCS11.jar`, `PDFVerifySignatures.jar` | Sin cambios de comportamiento frente a 1.0.0, más allá de la unificación de vocabulario de comandos especiales (ver abajo). | No. | Ninguna acción necesaria. |

**Dos cosas que explícitamente NO cambiaron y no requieren ninguna acción:**
- **Los nombres de los jars** siguen siendo los mismos, sin versión en el nombre (`XMLSignerPKCS11.jar`, no `XMLSignerPKCS11-1.1.0.jar`) — ver [sección 12](#12-distribución-y-despliegue). Un integrador con el nombre hardcodeado no tiene nada que tocar.
- **Los flags que ya usabas siguen funcionando exactamente igual.** La unificación de vocabulario (ver [sección 9](#9-catálogo-de-aplicaciones)) fue puramente aditiva: se agregaron alias nuevos (`-v`, `-h`, `--version`, `--help`, `--license` a los módulos que antes solo aceptaban `-version`/`-ayuda`/`-licencia`, y viceversa) — ningún alias que existía en 1.0.0 se quitó ni cambió de significado.

---

## 14. Glosario

| Término | Significado |
|---|---|
| **AC-ONTI** | Autoridad Certificante de la Oficina Nacional de Tecnologías de Información (Argentina), emisora de certificados de firma digital con validez legal |
| **ALADI** | Asociación Latinoamericana de Integración — organismo intergubernamental que agrupa a países de Sudamérica, incluido el bloque MERCOSUR, para promover el comercio regional |
| **CAPI / CNG** | CryptoAPI / Cryptography API: Next Generation — las dos generaciones de la API criptográfica nativa de Windows |
| **COD / CODEH** | Elementos XML que agrupan un Certificado de Origen Digital, firmados respectivamente por el Exportador y por el Funcionario Habilitado — ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh) |
| **CRL** | Certificate Revocation List — lista de certificados revocados publicada por una autoridad certificante |
| **CSP / KSP** | Cryptographic Service Provider / Key Storage Provider — los proveedores que implementan CAPI/CNG respectivamente |
| **DigestInfo** | Estructura ASN.1 que envuelve un hash junto con el identificador del algoritmo usado, requerida por el mecanismo PKCS#11 `CKM_RSA_PKCS` |
| **DJO / DJOEH** | Elementos XML que agrupan una Declaración Jurada de Origen, firmados respectivamente por el Exportador y por el Funcionario Habilitado — ver [sección 10](#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh) |
| **DocMDP** | Document Modification Detection and Prevention — permiso PDF que, aplicado por una firma certificante, restringe qué cambios son válidos después de firmar |
| **FIPS 140-2/140-3** | Estándar de seguridad del NIST (EE.UU.) para módulos criptográficos, con niveles de 1 a 4 |
| **HSM** | Hardware Security Module — dispositivo dedicado al resguardo y uso de claves criptográficas |
| **MERCOSUR** | Mercado Común del Sur — bloque comercial de países sudamericanos, subconjunto de los miembros de ALADI |
| **OCSP** | Online Certificate Status Protocol — consulta en línea del estado de revocación de un certificado |
| **PKCS#11 / #12** | Estándares de la familia PKCS (Public-Key Cryptography Standards) para tokens criptográficos y contenedores de certificado+clave, respectivamente |
| **Slot** | En PKCS#11, cada "ranura" lógica de un token donde puede haber un certificado/clave |
| **XML-DSig** | XML Digital Signature — estándar W3C para firmar documentos XML; es el único estándar de firma XML que S-FiDE implementa (no XAdES) |

---

## 15. Soporte y contacto

**Grupo Sauken S.A.** — Córdoba, Argentina
Email de soporte: soporte@sauken.com.ar
Sitio web: [www.sauken.com.ar](https://www.sauken.com.ar/)
Repositorio: [github.com/Grupo-Sauken-S-A/S-FIDE](https://github.com/Grupo-Sauken-S-A/S-FIDE)

El software se distribuye libremente bajo licencia GNU GPLv2 o posterior. El servicio de soporte técnico es un servicio comercial, con cargo, independiente de la licencia de uso del software.
