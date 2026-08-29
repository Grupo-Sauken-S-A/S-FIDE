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
10. [Integración desde otras aplicaciones](#10-integración-desde-otras-aplicaciones)
11. [Distribución y despliegue](#11-distribución-y-despliegue)
12. [Historial de versiones](#12-historial-de-versiones)
13. [Glosario](#13-glosario)
14. [Soporte y contacto](#14-soporte-y-contacto)

---

## 1. Introducción y filosofía

S-FiDE (**Si**stema de **F**irma D**i**gital Extendido) es una suite de programas Java independientes para firmar y verificar firmas digitales en documentos XML y PDF, y para extraer/inspeccionar certificados digitales desde tokens criptográficos (PKCS#11), archivos PKCS#12 o el almacén de certificados de Windows.

El diseño responde a un principio central: **cada capacidad es un programa independiente**, invocable por línea de comandos, que cualquier aplicación externa puede ejecutar como proceso hijo sin integrar ninguna librería Java. No existe una "librería S-FiDE" para enlazar — se distribuye como un conjunto de archivos `.jar` ejecutables, más una interfaz gráfica opcional (S-FiDE GUI) que orquesta esos mismos programas para usuarios que prefieren no trabajar por línea de comandos.

### Principios de diseño que todo integrador debe conocer

- **Contrato uniforme de proceso.** Todos los programas se invocan como `java -jar Programa.jar <argumentos>`. El código de salida (`exit code`) es `0` si la operación fue exitosa y `1` si hubo un error — es el único valor indispensable para saber si la operación resultó bien.
- **Salidas separadas y limpias.** El resultado normal va a la salida estándar (`stdout`); los errores van a la salida de error (`stderr`), siempre como texto simple y controlado. **Nunca** se expone un stack trace de Java al usuario final — los mensajes de error están pensados para operadores no técnicos (personas gestionando certificados y firmas, no desarrolladores leyendo logs).
- **UTF-8 de punta a punta.** Toda entrada y salida de todos los programas está codificada en UTF-8, en cualquier sistema operativo. Un integrador que lea `stdout`/`stderr` con otra codificación va a ver caracteres incorrectos en nombres, rutas o mensajes con acentos.
- **Multiplataforma.** Los programas compilan y corren igual en Windows, GNU/Linux y macOS (con la única excepción de los dos módulos que usan el almacén de certificados de Windows, ver [sección 9.11](#911-xmlsignerwindowscsp) y [9.12](#912-pdfsignerwindowscsp), que son exclusivos de Windows por diseño).
- **La GUI no es un atajo privilegiado.** S-FiDE GUI invoca exactamente los mismos `.jar` con los mismos argumentos que usaría un integrador externo — no reimplementa ninguna lógica de firma por su cuenta. Cualquier cosa que la GUI pueda hacer, un integrador puede reproducirla por línea de comandos.

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
| Estándares de firma implementados | XMLDSig / XAdES (XML), PAdES conforme ETSI EN 319 142 (PDF) |
| Algoritmos criptográficos | RSA 2048 bits, SHA-256, PKCS#1 v1.5 |
| Estándares de acceso a hardware | PKCS#11 v2.20, PKCS#12 v1.1, Microsoft CryptoAPI/CNG (vía CSP/KSP) |
| Validación de revocación | OCSP y CRL (requiere conexión a Internet para validación completa) |

La distribución final embebe su propio runtime de Java y su propio SDK de JavaFX (ver [sección 11](#11-distribución-y-despliegue)), por lo que **no requiere tener Java preinstalado** en el equipo destino — puede ejecutarse desde una carpeta portable, incluyendo un medio de almacenamiento removible (pendrive), en un Windows, Linux o macOS "limpios".

---

## 3. Software de terceros y dependencias

| Componente | Versión (1.1.0-beta.1) | Uso | Licencia |
|---|---|---|---|
| BouncyCastle (`bcprov`/`bcpkix`/`bcutil`-jdk18on) | 1.85 | Primitivos criptográficos, ASN.1, construcción de `DigestInfo` | MIT (Bouncy Castle License) |
| iText (`kernel`/`io`/`commons`/`sign`/`bouncy-castle-adapter`) | 8.0.5 | Firma y verificación de documentos PDF (PAdES) | AGPL v3 / comercial (Apryse) |
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
- El soporte técnico es un servicio con cargo, independiente de la libertad de uso del software (ver [sección 14](#14-soporte-y-contacto)).

---

## 5. Código fuente y repositorio

- **Repositorio:** [github.com/Grupo-Sauken-S-A/S-FIDE](https://github.com/Grupo-Sauken-S-A/S-FIDE)
- **Organización:** proyecto Maven multi-módulo (13 módulos) con un `pom.xml` raíz de tipo `pom` (agregador) y un módulo por capacidad.
- **Versionado:** [SemVer](https://semver.org/). Tags publicados: `v1.0.0` (primer release estable), `v1.1.0-beta.1` (versión actual, con QA de hardware en curso).
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

1. **OCSP (Online Certificate Status Protocol):** consulta en tiempo real al "respondedor OCSP" de la autoridad certificante. La URL de ese respondedor está publicada **dentro del propio certificado**, en la extensión *Authority Information Access* (AIA). S-FiDE arma una solicitud identificando el certificado por su número de serie y los datos del emisor, la envía por HTTP, y la autoridad certificante responde con un mensaje firmado indicando `GOOD` (válido) o `REVOKED` (revocado). Es el mecanismo más preciso — refleja el estado en el instante exacto de la consulta.
2. **CRL (Certificate Revocation List):** si OCSP no está disponible o no responde, S-FiDE recurre a la Lista de Certificados Revocados — un archivo publicado periódicamente por la autoridad certificante (la URL también viene en el certificado, en la extensión *CRL Distribution Point*) con todos los números de serie revocados hasta la fecha de esa lista. Es menos preciso que OCSP (puede no reflejar una revocación muy reciente) pero funciona aunque el respondedor OCSP puntual esté caído.

Cada intento tiene un tiempo de espera de 10 segundos. Si **ninguno de los dos** responde, el estado de revocación queda como **"No verificable" / `UNKNOWN`** — esto **no invalida la firma**: la integridad criptográfica (¿la firma corresponde exactamente a este documento y esta clave?) y el estado de revocación son dos verificaciones independientes. Un documento puede salir `DOCUMENTO VÁLIDO` con revocación "No verificable": la firma en sí es genuina, solo que no se pudo confirmar en ese momento que el certificado siga vigente según la autoridad certificante.

**Por qué hace falta Internet:** tanto la URL del respondedor OCSP como la del punto de distribución CRL son direcciones de la autoridad certificante en Internet. Sin conexión, ninguno de los dos mecanismos puede completarse y el resultado siempre va a ser "No verificable". Esto es exactamente lo que se observa al verificar una firma con un certificado de prueba autofirmado (sin una autoridad certificante real detrás): *"Estado de revocación: No verificable — Sin URLs de OCSP/CRL"*, porque ese certificado nunca declaró esas extensiones.

---

## 8. Catálogo de tokens y drivers soportados

Cualquier token que cumpla el estándar PKCS#11 es utilizable con S-FiDE. La siguiente tabla —fuente única en el repositorio en `shared-resources/token-profiles.txt`— documenta los modelos efectivamente presentes en el ecosistema de firma digital argentino (AC-ONTI), con la ruta típica de su biblioteca por sistema operativo:

| Marca / Modelo | Windows | Linux | macOS | Hash | Estado (AIF/SCBA) |
|---|---|---|---|---|---|
| SafeNet/Thales eToken 5110 / 5110+ | `C:\Windows\System32\eTPKCS11.dll` | `/usr/lib/libeToken.so` | `/usr/local/lib/libeTPkcs11.dylib` | Interno | Vigente — recomendado |
| Feitian ePass2003 | `C:\Windows\System32\eps2003csp11.dll` | `/usr/lib/libcastle.so.1.0.0` | `/usr/local/lib/libcastle.1.0.0.dylib` | Externo | Vigente — reemplazo estándar SCBA |
| mToken CryptoID nueva (FIPS 140-3) | `...\LMCryptoIDE\lm_cryptoide_pkcs11.dll` | `.../liblm_cryptoide_pkcs11.so` | `.../libcryptoide_pkcs11.dylib` | Externo (sin confirmar) | Válido solo en su variante 140-3 |
| mToken CryptoID vieja (FIPS 140-2) | `C:\Windows\System32\CryptoIDA_pkcs11.dll` | `/usr/lib/libcryptoide_pkcs11.so` | `.../libcryptoide_pkcs11.dylib` | Externo (sin confirmar) | Discontinuado — no recomendado |
| Athena IDProtect / ASECard | `C:\Windows\System32\asepkcs.dll` | `/usr/lib/x64-athena/libASEP11.so` | `.../libASEP11.dylib` | Desconocido | Discontinuado, en retiro |
| OpenSC (genérico) | `opensc-pkcs11.dll` | `opensc-pkcs11.so` | `opensc-pkcs11.so` | Depende del token | Driver de respaldo |

**Estrategia de hash** hace referencia a lo explicado en la [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm): "Interno" son tokens con mecanismo combinado (SafeNet); "Externo" son tokens que requieren el cálculo de hash por software (ePass2003, confirmado con hardware real); "Externo (sin confirmar)" es la hipótesis de diseño para modelos que S-FiDE todavía no validó contra hardware físico.

### Ayuda de selección de driver

- **Desde la GUI:** las pestañas que piden una biblioteca PKCS#11 muestran un `ComboBox` con marca/modelo y un botón "Detectar automáticamente", que revisa cuáles de las rutas de la tabla existen realmente en el equipo.
- **Desde línea de comandos:** el parámetro `-listar-drivers` (`--listar-drivers` en los módulos PDF, que usan flags con doble guion) imprime la misma tabla, filtrada por sistema operativo, indicando además si cada ruta existe en el equipo actual. Disponible en `TokenSlotsView`, `TokenCertificateExtractor`, `XMLSignerPKCS11` y `PDFSignerPKCS11`.

Esta detección es **solo una ayuda de UX** — nunca la única fuente de verdad. El nombre de archivo de una biblioteca puede cambiar entre versiones de middleware sin previo aviso; el código de firma siempre reintenta en tiempo de ejecución según lo que el token realmente responde (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)), sin depender de esta tabla para decidir el mecanismo.

---

## 9. Catálogo de aplicaciones

Convenciones comunes a todas las aplicaciones de esta sección:

- `-version`/`-v`/`--version`, `-licencia`/`--license` y `-ayuda`/`-h`/`--help` están disponibles en todas (el nombre exacto de la opción varía levemente entre módulos por motivos históricos; se indica en cada uno).
- El código de salida es `0` en éxito y `1` en error, salvo aclaración en contrario.
- Todas leen y escriben en UTF-8.

### 9.1 TokenSlotsView

**Qué hace:** visualiza los slots disponibles en un token PKCS#11 y la información de los certificados/claves almacenados en cada uno. Es la primera herramienta a usar frente a un token nuevo, para saber en qué slot está el certificado antes de firmar con él.

**Uso recomendado:** diagnóstico inicial de un token, o para confirmar el número de slot antes de invocar `XMLSignerPKCS11`/`PDFSignerPKCS11`.

**Sintaxis:**
```
java -jar TokenSlotsView.jar <Ruta de la biblioteca PKCS#11> <Contraseña del token>
java -jar TokenSlotsView.jar [-version | -licencia | -ayuda | -listar-drivers]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Ruta de biblioteca PKCS#11 | Sí | Ruta absoluta o relativa al `.dll`/`.so`/`.dylib` del fabricante |
| Contraseña del token | Sí | PIN de usuario del dispositivo |

**Salida:** por cada slot con contenido, imprime número de slot, alias, tipo (clave privada o certificado), sujeto, emisor, período de validez y número de serie.

**Mensajes de error posibles:** "El archivo de la biblioteca PKCS#11 no existe", "El proveedor SunPKCS11 no está disponible", "Contraseña incorrecta o error al acceder al token", "Error al leer el token", "No se encontraron certificados ni claves en el token".

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
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Ruta de biblioteca PKCS#11 | Sí | Ídem TokenSlotsView |
| Contraseña del token | Sí | PIN de usuario |
| Número de slot | Sí | Entero; ver `TokenSlotsView` para conocerlo |

**Salida:** información del certificado por consola (sujeto, emisor, número de serie, validez, algoritmo de firma) y un archivo `<NombreDelTitular>.pem` en el directorio de trabajo actual.

**Mensajes de error posibles:** "El archivo de la biblioteca PKCS#11 no existe", "Proveedor SunPKCS11 no disponible", "Error al cargar el almacén de claves", "No se encontró ningún certificado en el slot [número]", "Error al exportar el certificado".

---

### 9.3 PKCS12CertificateExtractor

**Qué hace:** el equivalente de `TokenCertificateExtractor` pero para archivos PKCS#12, sin necesidad de hardware.

**Sintaxis:**
```
java -jar PKCS12CertificateExtractor.jar <archivo.p12> <password>
```

**Parámetros:** ruta al archivo PKCS#12 (`.p12`/`.pfx`) y su contraseña, ambos obligatorios y posicionales.

**Salida:** información del certificado por consola y un archivo `.pem` exportado.

**Mensajes de error posibles:** "El archivo PKCS#12 no existe", "El archivo no es un PKCS#12 válido o la contraseña es incorrecta", "No se encontró ningún certificado X.509 en el archivo PKCS#12", "Error al exportar certificado".

---

### 9.4 XMLSignerPKCS11

**Qué hace:** firma digitalmente un documento XML (completo, o un elemento/párrafo específico por su atributo `Id`) usando un token PKCS#11. Implementa XMLDSig/XAdES, con el mecanismo de hash interno o externo resuelto automáticamente (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)).

**Uso recomendado:** firma de documentos XML (por ejemplo, Certificados de Origen digitales) con un token de hardware, en el flujo estándar de un exportador u organismo regulado.

**Sintaxis:**
```
java -jar XMLSignerPKCS11.jar <Biblioteca PKCS#11> <Contraseña> <Número de slot> <Archivo XML> <Elemento a firmar>
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Biblioteca PKCS#11 | Sí | Ruta al driver del token |
| Contraseña | Sí | PIN del token |
| Número de slot | Sí | Entero |
| Archivo XML | Sí | Ruta al XML a firmar |
| Elemento a firmar | Sí (puede ser cadena vacía `""`) | Si está vacío, firma todo el documento; si no, firma el elemento/párrafo con ese atributo `Id`/`ID`/`id`. Valores especiales conocidos: `COD` (Certificado de Origen, Exportador), `CODEH` (Certificado de Origen, Funcionario), `DJO`/`DJOEH` (Declaraciones Juradas de Origen) |

**Salida:** archivo `<nombre>-signed.xml` en el mismo directorio que el original; mensaje de confirmación con la ruta de salida por `stdout`.

**Mensajes de error posibles:** "El archivo de la biblioteca PKCS#11 no existe", "El archivo XML no existe", "El elemento o párrafo XML especificado no existe en el documento XML", "Error en el acceso al token", "Proveedor SunPKCS11 no disponible", "Contraseña incorrecta", "No se encontró el elemento XML con identificador [...]", "El token no admite ningún mecanismo de firma RSA-SHA256 compatible (ni interno ni externo)" (caso extremo, token no soportado).

**Advertencia de seguridad:** un número elevado de intentos fallidos de contraseña puede dejar inutilizado el certificado del token, exigiendo tramitar uno nuevo ante la autoridad certificante.

---

### 9.5 XMLSignerPKCS12

**Qué hace:** idéntico a `XMLSignerPKCS11` en funcionalidad de firma XML, pero usando un archivo PKCS#12 en lugar de un token.

**Sintaxis:**
```
java -jar XMLSignerPKCS12.jar <certificado.p12> <password> <archivo.xml> <elemento_xml>
```

**Parámetros:** archivo PKCS#12, contraseña, archivo XML, elemento a firmar (mismas reglas de `""`/`COD`/etc. que `XMLSignerPKCS11`).

**Salida:** igual que `XMLSignerPKCS11` — archivo `-signed.xml`.

**Mensajes de error posibles:** "El archivo PKCS#12 no existe", "El archivo XML no existe", "El archivo no es un PKCS#12 válido o la contraseña es incorrecta", "El archivo PKCS#12 no contiene ningún certificado", "El elemento o párrafo XML especificado no existe en el documento XML".

---

### 9.6 XMLVerifySignatures

**Qué hace:** verifica la validez de todas las firmas digitales presentes en un documento XML — integridad criptográfica, validez del certificado y estado de revocación (OCSP/CRL, requiere Internet para una validación completa).

**Sintaxis:**
```
java -jar XMLVerifySignatures.jar <archivo.xml> [-simple]
```

**Parámetros:** ruta al XML firmado (obligatorio); `-simple` (opcional) reduce el detalle de la salida.

**Salida:** por cada firma, estado, método de canonicalización, método de firma, valor de la firma, información del certificado y estado de revocación; al final, un resultado consolidado `DOCUMENTO VÁLIDO` o `DOCUMENTO INVÁLIDO`.

**Código de salida:** `0` si todas las firmas son válidas, `1` si alguna no lo es (a diferencia de los firmadores, acá el exit code refleja el **resultado de la validación**, no solo si el proceso corrió sin errores).

**Mensajes de error posibles:** "El archivo XML no existe", "El documento XML no contiene firmas digitales", "Error al procesar el archivo XML", "No se encontró KeyInfo", "No se encontró una clave válida".

---

### 9.7 XMLVerifyXSDStructure

**Qué hace:** valida que un documento XML cumpla la estructura definida por un esquema XSD (tipos de dato, elementos obligatorios u opcionales, orden, restricciones de contenido) y además verifica sus firmas digitales.

**¿Es obligatorio indicar un XSD externo? No.** El módulo busca automáticamente una referencia al esquema **dentro del propio XML**, revisando en este orden: el atributo `xsi:schemaLocation` (par namespace + URL) y, si no está, `xsi:noNamespaceSchemaLocation`. Si encuentra una URL ahí, **la descarga automáticamente** (requiere Internet) y valida contra ese esquema.

**¿Puedo indicar un XSD propio en vez del referenciado?** Sí, como segundo argumento — útil para validar contra una versión local sin depender de Internet, o cuando el XML no declara ningún esquema. Si el XML sí declara uno y el nombre de archivo no coincide con el indicado, el programa avisa (no es un error) y usa el que se le pasó:

```
NOTA: Diferencia en nombres de archivo XSD
├─ XSD referenciado en XML: esquema-v2.xsd
├─ XSD proporcionado: esquema-v1-local.xsd
└─ Se utilizará el archivo proporcionado: esquema-v1-local.xsd
```

**Qué se valida exactamente** (dos pasos independientes, ambos deben pasar):
1. Estructura del XML contra el XSD (tipos, elementos obligatorios, cardinalidad, restricciones).
2. Firmas digitales presentes en el documento (integridad y validez del certificado).

**Importante — no valida revocación.** A diferencia de `XMLVerifySignatures`, este módulo lo indica explícitamente al finalizar: *"Este proceso no realiza validación de revocación de las firmas digitales aplicadas"*. Si hace falta confirmar que el certificado no fue revocado (sección 7.5), hay que correr además `XMLVerifySignatures` sobre el mismo archivo.

**Requiere Internet** solo si no se indicó un XSD local **y** el XML referencia uno por URL — en ese caso descarga el esquema (probando `https`↔`http` como alternativa si el protocolo declarado falla). Sin conexión en ese escenario, falla con un error claro en vez de continuar sin validar.

**Sintaxis:**
```
java -jar XMLVerifyXSDStructure.jar <archivo.xml> [esquema.xsd]
```

**Mensajes de error posibles:** "El archivo XML no existe", "El archivo XSD no existe", "No se encontró referencia a esquema XSD en el XML y no se proporcionó archivo XSD", "Error al procesar el archivo XSD", "Error de validación XML", "El documento XML no contiene firmas digitales", "Se encontraron errores en la validación del documento XML".

---

### Opciones específicas de los firmadores de PDF

`PDFSignerPKCS11`, `PDFSignerPKCS12` y `PDFSignerWindowsCSP` comparten estas opciones, que **no tienen equivalente en los firmadores de XML** — un XML no tiene "apariencia visual" ni concepto de página:

- **Firma visible vs. invisible.** Si no se indican `-x`/`-y` (o ambos quedan en `0`), la firma es criptográficamente válida pero no se dibuja nada en el documento — es una firma "invisible", tan válida como cualquier otra. Si se indican coordenadas, se dibuja un recuadro con el nombre del firmante y la fecha (más el texto de `-t`, si se indicó).
- **Sistema de coordenadas.** PDF usa el sistema estándar de PostScript: el origen `(0,0)` es la **esquina inferior izquierda** de la página, el eje X crece hacia la derecha y el eje Y crece **hacia arriba**. `-x`/`-y` ubican la esquina **inferior izquierda** del recuadro de firma (que mide 160×70 puntos, tamaño fijo) — no es "de abajo a la derecha hacia arriba", es de abajo a la **izquierda**. Un punto PDF equivale a 1/72 de pulgada. La firma siempre se coloca en la página 1.
- **Texto personalizado (`-t`).** Se agrega debajo del nombre del firmante y la fecha, dentro del mismo recuadro visible — útil para el cargo del firmante o el motivo de la firma. No tiene efecto si la firma es invisible.
- **Bloquear el documento (`-k true`).** Hace dos cosas a la vez:
  1. Marca la firma como **firma certificante** (permiso PDF `DocMDP` = "no se permite ningún cambio"), no una firma de aprobación común — el documento queda declarado como no modificable ante cualquier lector conforme (Acrobat, etc.). Solo puede haber **una** firma certificante por documento, y debe ser la primera.
  2. Aplica cifrado estándar AES-256 al PDF resultante, restringiendo los permisos a solo impresión y uso con lectores de pantalla — no se permite copiar texto, editar, ni rellenar formularios.

  **Si se planea agregar más firmas al mismo documento más adelante, no usar `-k true` en la primera.**

**Errores comunes a los tres firmadores de PDF:** "El archivo PDF no existe o no es accesible", "El PDF está encriptado y no puede ser firmado" (no se puede firmar un PDF que ya tiene una restricción de cifrado previa), "La firma existente '[nombre]' no es válida" (si el PDF ya tenía una firma corrupta, se rechaza antes de agregar una nueva).

### 9.8 PDFSignerPKCS11

**Qué hace:** firma digitalmente un documento PDF usando un token PKCS#11, con firma visible opcional (ver opciones arriba). Implementa PAdES conforme ETSI EN 319 142, con timestamp y verificación OCSP de la cadena de confianza.

**Sintaxis:**
```
java -jar PDFSignerPKCS11.jar -i <archivo.pdf> -l <lib-pkcs11> -p <password> -s <slot> [-k true|false] [-x pos] [-y pos] [-t "texto"]
```

**Parámetros:**

| Flag | Obligatorio | Descripción |
|---|---|---|
| `-i`, `--input` | Sí | Archivo PDF a firmar |
| `-l`, `--library` | Sí | Ruta a la biblioteca PKCS#11 |
| `-p`, `--password` | Sí | PIN del token |
| `-s`, `--slot` | Sí | Número de slot |
| `-k`, `--lock` | No (default `false`) | Bloquea el documento contra modificaciones posteriores a la firma |
| `-x`, `--xpos` / `-y`, `--ypos` | No (default `0`) | Posición de una firma visible; si ambas quedan en `0`, la firma es invisible |
| `-t`, `--text` | No | Texto adicional a mostrar en la firma visible |

**Salida:** archivo `<nombre>-signed.pdf`.

**Mensajes de error posibles:** "El archivo PDF no existe o no es accesible", "La biblioteca PKCS#11 no existe o no es accesible", "El PDF está encriptado y no puede ser firmado", "La firma existente '[nombre]' no es válida", "El token no contiene una clave privada válida", "No se encontró una cadena de certificados válida en el token", "Error al acceder a la clave privada del token".

**Posiciones útiles para Certificados de Origen no preferenciales** (convención Grupo Sauken): `-x 40 -y 55` para la firma del Exportador, `-x 310 -y 55` para la firma del Funcionario Habilitado.

---

### 9.9 PDFSignerPKCS12

**Qué hace:** idéntico a `PDFSignerPKCS11` pero usando un archivo PKCS#12.

**Sintaxis:**
```
java -jar PDFSignerPKCS12.jar -i <archivo.pdf> -c <certificado.p12> -p <password> [-k true|false] [-x pos] [-y pos] [-t "texto"]
```

**Parámetros:** iguales a `PDFSignerPKCS11` reemplazando `-l`/`-s` (biblioteca/slot) por `-c` (archivo PKCS#12).

**Mensajes de error posibles:** "El archivo PDF no existe o no es accesible", "El archivo de certificado no existe o no es accesible", "El PDF está encriptado y no puede ser firmado", "La firma existente '[nombre]' no es válida", "El certificado no contiene una clave privada", "No se encontró una cadena de certificados válida", "Error al acceder a la clave privada".

---

### 9.10 PDFVerifySignatures

**Qué hace:** verifica la validez de las firmas digitales de un documento PDF — integridad, autenticidad, estado de revocación, y si el documento fue modificado luego de la última firma.

**Sintaxis:**
```
java -jar PDFVerifySignatures.jar <archivo.pdf> [-simple]
```

**Salida:** por cada firma, cobertura del documento, integridad, fecha de firma, estado de revocación, firmante, organización, número de serie, período de validez, emisor, tipo y algoritmo de firma, y (si hay más de un certificado en la cadena) el listado completo de la cadena de certificación. Al final, estado consolidado del documento (bloqueado/encriptado).

**Código de salida:** `0` si todas las firmas son válidas, `1` si alguna no lo es.

**Mensajes de error posibles:** "El documento no contiene firmas digitales", "Error verificando firma [nombre]", "DOCUMENTO INVÁLIDO: una o más firmas no son válidas", "Certificado revocado al momento de la firma", "Certificado no confiable o autofirmado".

---

### 9.11 XMLSignerWindowsCSP

**Qué hace:** firma XML usando un certificado ya presente en el almacén de certificados de Windows (CSP/KSP), como alternativa a `XMLSignerPKCS11` (ver [sección 7.3](#73-windows-cspksp-almacén-de-certificados-de-windows)). **Exclusivo de Windows.**

**Sintaxis:**
```
java -jar XMLSignerWindowsCSP.jar <Alias o fragmento del Subject CN> <Archivo XML> <Elemento a firmar>
java -jar XMLSignerWindowsCSP.jar [-version | -licencia | -ayuda | -listar-certificados]
```

**Parámetros:**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| Alias o fragmento del CN | Sí | Alias exacto del certificado en el almacén, o un fragmento del nombre del titular que identifique un único certificado. Usar `-listar-certificados` para ver los disponibles |
| Archivo XML | Sí | — |
| Elemento a firmar | Sí (o `""`) | Mismas reglas que `XMLSignerPKCS11` |

**A diferencia de los módulos PKCS#11, no se pasa contraseña** — el acceso a la clave lo administra Windows (puede aparecer un diálogo nativo del sistema pidiendo el PIN).

**Mensajes de error posibles:** "Este módulo solo funciona en Windows [...]" (al ejecutarlo en otro SO), "No se encontró ningún certificado con clave privada que coincida con '[texto]'", "'[texto]' coincide con N certificados distintos. Sea más específico [...]", "El proveedor SunMSCAPI no está disponible en este JDK".

---

### 9.12 PDFSignerWindowsCSP

**Qué hace:** el equivalente de `PDFSignerWindowsCSP` para documentos PDF — mismas opciones de posición, texto y bloqueo que `PDFSignerPKCS11`. **Exclusivo de Windows.**

**Sintaxis:**
```
java -jar pdf_signer_windows_csp.jar -i <archivo.pdf> -a <alias o fragmento CN> [-k true|false] [-x pos] [-y pos] [-t "texto"]
java -jar pdf_signer_windows_csp.jar [-v | -h | --license | --listar-certificados]
```

**Parámetros:** `-i`/`--input` (PDF, obligatorio), `-a`/`--alias` (alias o fragmento del CN, obligatorio), más `-k`/`-x`/`-y`/`-t` iguales a `PDFSignerPKCS11`. Tampoco pide contraseña.

**Mensajes de error posibles:** los mismos de acceso al almacén que `XMLSignerWindowsCSP`, más los propios de firma PDF ya listados en `PDFSignerPKCS11`.

---

### 9.13 S-FiDE GUI

**Qué hace:** interfaz gráfica JavaFX que expone las 12 aplicaciones anteriores como pestañas, para usuarios que prefieren no operar por línea de comandos. Invoca los mismos `.jar` con `ProcessBuilder`, mostrando la salida combinada de `stdout`/`stderr` en un cuadro de texto y el código de resultado en un diálogo.

**No es una aplicación para integración por proceso** (no tiene un contrato de argumentos/exit-code pensado para ser invocada por otro programa) — se documenta acá por completitud del producto. Un integrador debe usar los módulos CLI individuales.

**Funciones adicionales relevantes para quien la use manualmente:**
- Recuerda la última ruta de biblioteca PKCS#11 / archivo PKCS#12 usada, en `sfide-defaults.properties`.
- Detección automática de driver PKCS#11 por marca/modelo (ver [sección 8](#8-catálogo-de-tokens-y-drivers-soportados)).
- Las pestañas de CSP/KSP (`XMLSignerWindowsCSP`/`PDFSignerWindowsCSP`) solo aparecen si la GUI corre en Windows.
- Validación de que todos los `.jar` necesarios estén presentes junto a `SFide-GUI.jar` antes de permitir su uso.

---

## 10. Integración desde otras aplicaciones

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

## 11. Distribución y despliegue

Una distribución de S-FiDE lista para usar es una carpeta autocontenida con esta estructura:

```
S-FiDE/
├── openjdk-23.0.1/          ← runtime de Java embebido (por plataforma)
├── javafx-sdk-23.0.1/       ← SDK de JavaFX embebido (por plataforma)
├── *.jar                    ← los 13 módulos, con nombre "amigable" sin versión (ver más abajo)
├── SFide-GUI.bat / .sh      ← launchers, se autodetectan solos (no dependen de una letra de unidad fija)
├── sfide-defaults.demo.properties
├── Leeme.txt
├── doc/                     ← este manual y la guía de uso
├── test/                    ← documentos de ejemplo
└── xsd/                     ← esquemas de ejemplo
```

**No requiere instalación ni Java preinstalado** — puede copiarse a cualquier ubicación, incluido un medio removible, y ejecutarse desde ahí en un equipo "limpio" (Windows, Linux o macOS), gracias al runtime embebido y a que los launchers se autodetectan (no hay ninguna ruta ni letra de unidad hardcodeada).

**Convención de nombres de jar — importante para integradores:** el nombre del `.jar` de distribución (`XMLSignerPKCS11.jar`) **nunca** incluye el número de versión, a diferencia del artefacto crudo que genera Maven en `target/` (`xml_signer_pkcs11-1.1.0-beta.1-jar-with-dependencies.jar`). Esto es deliberado: un integrador que ya tiene el nombre del jar hardcodeado en su propio código no debe romperse cuando S-FiDE actualiza de versión.

El script `install.bat` (incluido en el repositorio) automatiza la generación de una carpeta de distribución completa a partir del código fuente compilado, incluyendo opcionalmente los runtimes embebidos si se le indica una carpeta "vendor" de referencia.

---

## 12. Historial de versiones

### v1.1.0-beta.1 (2026-08) — en QA

- Compatibilidad ampliada de tokens PKCS#11: soporte de mecanismo de hash externo (`CKM_RSA_PKCS`) para tokens que no exponen el mecanismo combinado (ver [sección 7.1](#71-pkcs11-tokens-criptográficos-y-hsm)).
- Autodetección de marca/modelo de token y ayuda de selección de driver (GUI y `-listar-drivers`).
- Dos módulos nuevos: `XMLSignerWindowsCSP` y `PDFSignerWindowsCSP` (firma vía almacén de certificados de Windows).
- Actualización de dependencias criptográficas (BouncyCastle, iText, Apache Santuario) por alertas de seguridad.
- Corrección de dos errores encontrados durante la QA con hardware real (SafeNet 5110+ L3): fallo de firma XML con tokens reales (`Mechanism DOM not available`) y una recursión infinita relacionada. **Validado de punta a punta** con token SafeNet real y con PKCS#12 — pendiente de validación con ePass2003 y mToken CryptoID.

### v1.0.0 (2024-12) — primer release estable

Suite inicial de 10 módulos (extracción de certificados, firma y verificación XML/PDF vía PKCS#11 y PKCS#12, validación de estructura XSD) más la GUI JavaFX.

---

## 13. Glosario

| Término | Significado |
|---|---|
| **AC-ONTI** | Autoridad Certificante de la Oficina Nacional de Tecnologías de Información (Argentina), emisora de certificados de firma digital con validez legal |
| **CAPI / CNG** | CryptoAPI / Cryptography API: Next Generation — las dos generaciones de la API criptográfica nativa de Windows |
| **CRL** | Certificate Revocation List — lista de certificados revocados publicada por una autoridad certificante |
| **CSP / KSP** | Cryptographic Service Provider / Key Storage Provider — los proveedores que implementan CAPI/CNG respectivamente |
| **DigestInfo** | Estructura ASN.1 que envuelve un hash junto con el identificador del algoritmo usado, requerida por el mecanismo PKCS#11 `CKM_RSA_PKCS` |
| **FIPS 140-2/140-3** | Estándar de seguridad del NIST (EE.UU.) para módulos criptográficos, con niveles de 1 a 4 |
| **HSM** | Hardware Security Module — dispositivo dedicado al resguardo y uso de claves criptográficas |
| **OCSP** | Online Certificate Status Protocol — consulta en línea del estado de revocación de un certificado |
| **PAdES** | PDF Advanced Electronic Signatures — estándar ETSI para firmas digitales en documentos PDF |
| **PKCS#11 / #12** | Estándares de la familia PKCS (Public-Key Cryptography Standards) para tokens criptográficos y contenedores de certificado+clave, respectivamente |
| **Slot** | En PKCS#11, cada "ranura" lógica de un token donde puede haber un certificado/clave |
| **XAdES** | XML Advanced Electronic Signatures — perfil de XMLDSig con metadatos adicionales de firma |
| **XMLDSig** | XML Digital Signature — estándar W3C para firmar documentos XML |

---

## 14. Soporte y contacto

**Grupo Sauken S.A.** — Córdoba, Argentina
Email de soporte: soporte@sauken.com.ar
Sitio web: [www.sauken.com.ar](https://www.sauken.com.ar/)
Repositorio: [github.com/Grupo-Sauken-S-A/S-FIDE](https://github.com/Grupo-Sauken-S-A/S-FIDE)

El software se distribuye libremente bajo licencia GNU GPLv2 o posterior. El servicio de soporte técnico es un servicio comercial, con cargo, independiente de la licencia de uso del software.
