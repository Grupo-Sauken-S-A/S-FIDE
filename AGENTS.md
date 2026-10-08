# AGENTS.md — Instrucciones para asistentes de IA en S-FiDE

Este archivo está pensado para **cualquier asistente de IA** (Claude Code, Cursor, GitHub Copilot, Codex CLI, o cualquier otro) que trabaje sobre este repositorio, en cualquier sesión, de cualquier persona. S-FiDE se distribuye bajo GPLv2 o posterior: cualquiera puede clonarlo, hacer un fork y proponer cambios, y es razonable asumir que muchas de esas contribuciones se van a hacer con asistencia de IA. El objetivo de este documento es que esa asistencia parta con el contexto correcto, en vez de tener que redescubrirlo o — peor — romper convenciones que no son obvias mirando solo el código.

Si sos un contribuyente humano, este archivo también te sirve — es la misma información que le daríamos a una IA, y aplica igual.

---

## 1. Qué es este proyecto

**S-FiDE** (Sistema de Firma Digital Extendido) es una suite de programas Java 23 independientes de Grupo Sauken S.A. para firmar y verificar firmas digitales en documentos XML y PDF, y para extraer/inspeccionar certificados digitales desde tokens PKCS#11, archivos PKCS#12 o el almacén de certificados de Windows. Incluye una interfaz gráfica JavaFX opcional (`s_fide_gui`) que orquesta esos mismos programas.

Documentación técnica completa (arquitectura, cada módulo, mecanismos de firma, especialización de comercio exterior ALADI/MERCOSUR): **[`doc/manual-tecnico-integracion.md`](doc/manual-tecnico-integracion.md)**. Léelo antes de tocar cualquier módulo relacionado con firma/verificación — este archivo resume las reglas de más alto nivel, el manual tiene el detalle técnico verificado.

Módulos del repositorio (cada uno es un módulo Maven independiente, un `pom.xml` raíz de tipo `pom` los agrupa):

| Módulo | Qué hace |
|---|---|
| `token_slots_view` | Lista slots y certificados de un token PKCS#11 |
| `token_certificate_extractor` | Extrae un certificado de un token PKCS#11 a `.pem` (carpeta personal del usuario) e informa su revocación |
| `pkcs12_certificate_extractor` | Extrae un certificado de un archivo PKCS#12 a `.pem` (carpeta personal del usuario) e informa su revocación |
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
| `s_fide_updater` | Aplica una actualización descargada con respaldo y reversión (lo invoca la GUI; también usable por un integrador) |
| `s_fide_gui` | Interfaz gráfica JavaFX que invoca los módulos anteriores como procesos externos |

---

## 2. Reglas no negociables

Estas reglas fueron confirmadas explícitamente por el dueño del proyecto (Juan Carlos Ríos). No son sugerencias de estilo — un cambio que las rompa debe rechazarse aunque compile y funcione.

1. **Una capacidad = un módulo.** Nunca combines dos capacidades en un módulo, nunca agregues una dependencia Java entre módulos en tiempo de ejecución (cada jar debe seguir siendo standalone). Un módulo nuevo sigue el mismo patrón de `pom.xml`/`LICENSE.txt`/`HELP.txt` que los existentes.
2. **Contrato de línea de comandos:** cada módulo se invoca como `java -jar Modulo.jar <argumentos>`. Código de salida `0` = éxito, `1` = error — es lo único que un integrador necesita chequear. El resultado normal va a `stdout`; los errores van a `stderr`.
3. **Nunca exponer un stack trace de Java al usuario final.** Todo error se traduce a un mensaje de texto simple, en español, antes de imprimirse. El público de estos programas incluye personas no técnicas gestionando certificados y firmas, no desarrolladores leyendo logs.
4. **UTF-8 de punta a punta**, en toda entrada/salida, en cualquier sistema operativo.
5. **Español (rioplatense)** en todo texto orientado al usuario: mensajes de ayuda, mensajes de error, documentación. El código puede tener nombres de variables/métodos en inglés (así está hoy), pero cualquier string que un usuario vea debe estar en español.
6. **Licencia GNU GPL v2 o cualquier versión posterior.** Todo archivo fuente nuevo lleva el bloque de licencia completo al inicio (ver plantilla en la sección 5) y cada módulo incluye `LICENSE.txt` como recurso. La cláusula "o posterior" es la que habilita legalmente combinar con dependencias AGPLv3 como iText — no se puede quitar esa cláusula sin romper esa compatibilidad.
7. **La GUI (`s_fide_gui`) nunca es un atajo privilegiado.** Invoca los mismos `.jar` con `ProcessBuilder`, con los mismos argumentos que usaría un integrador externo. Nunca reimplementa lógica de firma/verificación por su cuenta. **Excepción explícita, no una violación de esta regla:** en las pestañas de `XMLSignerPKCS12`/`PDFSignerPKCS12`/`XMLSignerWindowsCSP`/`PDFSignerWindowsCSP`, `GUIUtils.executeSignCommandWithRevocationCheck` invoca el jar **dos veces** (una con `-verificar-revocacion`, de solo lectura, y otra para firmar de verdad) para poder mostrar un diálogo de confirmación entre medio — pero ambas invocaciones son la misma interfaz pública que cualquier integrador externo podría usar por su cuenta; la GUI no calcula el estado de revocación por sí misma, solo orquesta dos llamadas al mismo binario. Ver sección 7.5.1 del manual técnico. **Lo mismo vale desde 1.5.0 para los firmadores de PDF:** la ventana de ubicación de la firma y la revisión previa (`PdfPreflight`) solo orquestan los modos de solo lectura `-analizar-documento` y `-vista-previa-texto` de los mismos jars; Apache PDFBox se usa únicamente para **mostrar** la página, nunca para firmar ni para decidir si un documento es válido.
8. **Los jars de distribución nunca llevan versión en el nombre** (`XMLSignerPKCS11.jar`, no `XMLSignerPKCS11-1.1.1.jar`) — ver sección 4. Los artefactos crudos de Maven en `target/` sí la llevan, eso es normal y no se debe "corregir".

---

## 3. Cómo compilar y verificar

```bash
git clone https://github.com/Grupo-Sauken-S-A/S-FIDE.git
cd S-FIDE
./mvnw clean install
```

Requiere JDK 23 (el repo incluye Maven Wrapper — no hace falta tener Maven instalado). Cada módulo genera su jar en su propia carpeta `target/`.

**Antes de dar por terminado cualquier cambio:**
- Corré `./mvnw clean install` del reactor completo, no solo del módulo que tocaste — los módulos son independientes en runtime pero comparten el `pom.xml` padre y `shared-resources/`.
- Si el cambio toca firma, verificación, o cualquier código criptográfico: probalo con archivos reales, no asumas que compila = funciona. Un bug real de esta suite (`Mechanism DOM not available`, ver sección 6) solo se manifestó contra hardware real — una prueba puramente en software con un provider simulado no lo detectó.
- Si agregaste o cambiaste un mensaje de error, comando especial, o comportamiento de un módulo: actualizá `doc/manual-tecnico-integracion.md` **y** su gemelo `doc/manual-tecnico-integracion.html` en el mismo cambio — ver sección 4.

---

## 4. Documentación: una sola fuente, dos formatos

`doc/manual-tecnico-integracion.md` y `doc/manual-tecnico-integracion.html` son el mismo contenido en dos formatos (Markdown fuente, HTML para publicar). **Tratalos como un único artefacto.** Cualquier cambio que afecte lo que el manual documenta —flags o comportamiento de un módulo nuevo o existente, un mensaje de error nuevo, un mecanismo de firma/verificación cambiado, tokens soportados, versiones— se refleja en **ambos** archivos en el mismo cambio, no como una tarea aparte para "después".

No existe un `doc/guia-uso-sfide.md` separado — existió en versiones anteriores y se fusionó dentro del manual técnico; no lo recrees.

---

## 5. Licencia: plantilla exacta para archivos nuevos

Todo archivo fuente `.java` nuevo empieza con este bloque (ajustar el nombre del archivo/módulo donde corresponda, mantener el resto igual):

```java
/*
  Derechos Reservados © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

  Este es un Software Libre; como tal redistribuirlo y/o modificarlo está
  permitido, siempre y cuando se haga bajo los términos y condiciones de la
  Licencia Pública General GNU publicada por la Free Software Foundation,
  ya sea en su versión 2 ó cualquier otra de las posteriores a la misma.

  Este "Programa" se distribuye con la intención de que sea útil, sin
  embargo carece de garantía, ni siquiera tiene la garantía implícita de
  tipo comercial o inherente al propósito del mismo "Programa". Ver la
  Licencia Pública General GNU para más detalles.

  Se debe haber recibido una copia de la Licencia Pública General GNU con
  este "Programa", si este no fue el caso, favor de escribir a la Free
  Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
  MA 02110-1301 USA.

  Autores: Juan Carlos Ríos y Juan Ignacio Ríos
  Correo electrónico: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Empresa: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

  <>

  Copyright © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.,
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

  Authors: Juan Carlos Ríos y Juan Ignacio Ríos
  E-mail: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Company: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

 */
```

Si una IA asistió en escribir el archivo, es convención del proyecto (no obligatoria, pero usada consistentemente hasta ahora) agregar `"con la asistencia de <nombre del modelo>"` después de los autores, en ambos bloques (español e inglés) — mirá cualquier archivo existente para ver el formato exacto.

---

## 6. Trampas técnicas ya resueltas — no las repitas

Estas son fallas reales que ya se encontraron y corrigieron en este proyecto. Si tu cambio toca algo relacionado, revisá esto primero.

- **`XMLSignatureFactory.getInstance(mecanismo, provider)` no es un gancho genérico para primitivos criptográficos.** El segundo argumento debe ser un provider que implemente el mecanismo (`"DOM"`) en sí mismo — no sirve para forzar qué `Signature`/`MessageDigest` interno usa JSR-105. Para enrutar un provider PKCS#11/CSP específico, registralo como provider **global** de máxima prioridad (`Security.insertProviderAt(provider, 1)`) y confiá en el reintento automático de la JCA cuando `initSign()`/`initVerify()` rechaza la clave. Ver `xml_signer_pkcs11/.../Pkcs11FallbackProvider.java` para la implementación de referencia.
- **Riesgo de recursión infinita al implementar un `Provider`/`MessageDigest` propio insertado en prioridad 1.** Si tu implementación pide un algoritmo genérico (`MessageDigest.getInstance("SHA-256")`) sin especificar provider, puede terminar auto-referenciándose. Pedí siempre un provider concreto y de bajo nivel (p. ej. `"SUN"`) dentro de esas implementaciones.
- **Un token PKCS#11 puede firmar con hash interno (`CKM_SHA256_RSA_PKCS`) o externo (`CKM_RSA_PKCS`, requiere armar el `DigestInfo` ASN.1 vos mismo).** No asumas que todos los tokens soportan el mecanismo combinado — probá el combinado primero, con fallback automático al externo (ver `Pkcs11ExternalSignature`/`Pkcs11FallbackSignature`).
- **Los tres firmadores de PDF deben acoplar siempre los dos efectos de "bloquear" un documento** (certificación DocMDP + cifrado AES-256), sin importar si la firma es visible o invisible — **en un documento sin firmas previas**: si ya tiene firmas, "bloquear" es una firma de cierre (ver la regla siguiente sobre no reescribir un PDF ya firmado). Hubo un bug real donde uno de los tres solo aplicaba la certificación con firma visible — ver commit `9fd0828`.
- **Vocabulario de comandos especiales unificado.** Los 14 módulos CLI (los 13 originales y `SFideUpdater`) aceptan `-version`/`-v`/`--version`, `-ayuda`/`-h`/`--help`, `-licencia`/`--license` indistintamente. Si agregás un módulo nuevo o un comando especial nuevo, aceptá las tres formas desde el principio.
- **Nunca exponer `e.printStackTrace()`.** Se encontró y corrigió una instancia real de esto en `XMLVerifySignatures` — cualquier `catch` debe traducir la excepción a un mensaje controlado, nunca volcar la traza.
- **La verificación de firmas debe aceptar SHA-1 además de SHA-256**, aunque S-FiDE nunca firme con SHA-1. Es intencional: los verificadores deben validar firmas de cualquier aplicación de terceros, incluidas las antiguas. No "endurezcas" esto sin que te lo pidan explícitamente — rompería la compatibilidad con documentos reales de comercio exterior firmados en parte por otro software (ver sección 7).
- **Antes de extender lógica de negocio específica de un dominio** (por ejemplo, la especialización de comercio exterior ALADI/MERCOSUR — sección 7), verificá la especificación operativa real contra ejemplos concretos o una fuente autorizada. No infieras el comportamiento de un caso nuevo por analogía con uno similar ya implementado sin confirmarlo — dos casos que parecen simétricos pueden no serlo (ejemplo real: se asumió que la fecha de revocación de `CODEH` usaba el mismo campo de país que `COD`, y era un campo distinto).
- **Todo `ProcessBuilder` de este proyecto (jars de módulo, PowerShell, lo que sea) debe llamar `process.waitFor(timeout, unit)` antes de leer sus streams, nunca después.** Leer con `readAllBytes()`/`BufferedReader.readLine()` antes de esperar el timeout bloquea indefinidamente si el proceso nunca cierra su salida — el timeout nunca llega a aplicarse porque la ejecución no pasa de esa lectura. Si el `waitFor` vence, llamar `destroyForcibly()`. Ver `GUIUtils.executeCommand` (watchdog de 10 minutos, generoso a propósito para no cortar un diálogo nativo de PIN de Windows CSP/KSP ni una consulta OCSP/CRL) y `SFideGUI.createDesktopShortcutIfNeeded`.
- **Nunca asumas que la salida de un proceso hijo es "poca" para justificar leerla recién después de `waitFor()` — si hay alguna duda, drenala en un hilo aparte en simultáneo con la espera.** Si el hijo escribe más de lo que entra en el buffer del pipe del sistema operativo (unos 64KB típicamente) y nadie lo está leyendo mientras tanto, el hijo queda bloqueado escribiendo y el padre bloqueado en `waitFor()`: un interbloqueo, no un timeout que eventualmente se resuelve solo. Encontrado con un thread dump real (`jstack`) en `integration_tests/Pkcs12RoundTripTest`: al combinar el classpath de varios módulos de producción en un mismo subproceso de prueba, Logback detectaba varios `logback.xml` duplicados y tiraba bastante diagnóstico por `stdout` — muchísimo más de lo que cualquiera de esos módulos genera normalmente corriendo solo (que sí es genuinamente poco, como en el caso de `SFideGUI.createDesktopShortcutIfNeeded` de arriba). La solución general y correcta es un hilo lector dedicado que drena el stream mientras el hilo principal espera con `waitFor(timeout, unit)` — ver `Pkcs12RoundTripTest.ejecutar` para la implementación de referencia.
- **Para probar `destroyForcibly()` contra un proceso colgado, lanzá el ejecutable real directamente — nunca a través de `cmd /c <algo>`.** Un `cmd.exe` intermedio hereda el pipe de salida a su propio hijo (p. ej. `ping.exe`); `destroyForcibly()` mata a `cmd.exe` pero el hijo sigue vivo y con el pipe abierto, así que la prueba da falsos positivos de "colgado" mucho más allá del timeout real. Comprobado empíricamente: `cmd /c ping -n 60 ...` tardó ~59s en liberar el pipe pese a un timeout de 3s; lanzando `java.exe` directo (la forma real en que `GUIUtils` invoca los módulos) el corte fue inmediato.
- **Un script de PowerShell generado desde Java (o cualquier otro proceso) no puede llevar comillas dobles embebidas** si se pasa como `-Command "<script>"` a través de `ProcessBuilder` — el re-tokenizado de la línea de comandos de Windows las consume y corrompe el script. Usá solo comillas simples y concatenación con `+` en cualquier script de PowerShell generado por este proyecto. Ver `SFideGUI.shortcutScript`.
- **Cualquier tarea en segundo plano que toque `ConfigurationManager` debe envolver la llamada en `Platform.runLater(...)`.** El guardado con debounce usa una `PauseTransition` (JavaFX `Animation`), que solo se puede controlar desde el hilo de la aplicación FX — invocarla desde un hilo de fondo lanza `IllegalStateException`, y si la tarea se lanzó con `executorService.submit(Runnable)` sin revisar el `Future`, la excepción desaparece en silencio.
- **`LICENSE` debe quedar siempre idéntico byte a byte al texto canónico de la GPLv2** (el de gnu.org/SPDX), sin ningún encabezado propio del proyecto antes del cuerpo. El detector de licencias de GitHub (`licensee`) compara similitud del archivo completo — cualquier texto propio agregado (nombre del proyecto, copyright, cláusula "o posterior") diluye esa similitud por debajo del umbral de detección y el repo queda marcado `"Other"` en vez de `"GPL-2.0"`. El nombre del proyecto y el copyright ya están en el README y en el encabezado de cada archivo fuente — no hace falta repetirlos en `LICENSE`.
- **Para un acceso directo de Windows que solo debe abrir un archivo o URL con la aplicación predeterminada (no ejecutar un programa con argumentos), un archivo `.url` es más simple y robusto que un `.lnk` vía `WScript.Shell`.** Un `.url` es texto plano con formato INI (`[InternetShortcut]` / `URL=file:///...` / `IconFile=...` / `IconIndex=0`) — no requiere crear ningún objeto COM, alcanza con `Set-Content` desde PowerShell (o incluso escribirlo directo en Java, sin PowerShell). Calculá la URL `file://` con `Path.toUri()` de Java, nunca armándola a mano en el script — así el saneamiento de espacios/acentos/caracteres especiales en la ruta lo hace la biblioteca estándar. Ver `SFideGUI.createDocShortcutsIfNeeded`/`urlShortcutScript`.
- **Los datos del usuario NUNCA van en la carpeta de instalación.** `sfide-defaults.properties`, el candado de instancia única (`sfide-gui-<marca>.lock`) y los `.pem` de los extractores viven en `<user.home>/S-FiDE` (`UserDataDirectory` en la GUI; `ExtractorSupport` en los dos extractores — misma carpeta y misma propiedad `-Dsfide.data.dir`, duplicado a propósito para que cada jar siga siendo independiente). La instalación puede ser compartida por varios usuarios y de solo lectura. `sfide-defaults.properties` lleva `config.schema` (entero que dispara migraciones en `ConfigurationManager.upgradeSchema`) y `app.version`: si cambiás el significado o el nombre de una clave, subí `CURRENT_SCHEMA` y agregá el paso de migración — nunca cambies una clave sin migrarla.
- **Un `slotListIndex` de SunPKCS11 es un índice sobre TODOS los slots de la biblioteca, no sobre los que tienen token.** Un slot vacío deja al proveedor sin el servicio `KeyStore.PKCS11` y la falla aparece como un error genérico lejos de la causa. Los cuatro módulos de token resuelven el slot con `Pkcs11Access` (prueba el pedido; si está vacío, busca el token; con varios, falla sin probar la contraseña en ninguno) y traducen los errores desde TODA la cadena de causas. `Pkcs11Access.java` está duplicada en `token_slots_view`, `token_certificate_extractor`, `xml_signer_pkcs11` y `pdf_signer_pkcs11`: si la tocás, actualizá las cuatro copias (solo cambia la línea `package`).
- **Contraseñas en la GUI: solo en memoria, nunca en disco.** `SessionPasswordStore` las ata a la credencial (biblioteca+slot, o archivo) y solo se recuerdan tras un código de salida `0`; una recordada que falla se olvida y no se reenvía sola (con un token cada intento fallido cuenta para el bloqueo). Nada de eso debe llegar a `ConfigurationManager`.
- **En `doc/` solo pueden ir `.html`, `.md` e imágenes.** El actualizador rechaza el paquete ENTERO si trae un archivo que no está en `UpdatePackage.isInstallable` (probado: un `.txt` en `doc/` hacía fallar la actualización de 1.4.0 a 1.5.0). Todo otro documento (por ejemplo el cuerpo del Release) va en `release-notes/`; los dos `crear-paquete-actualizacion` lo controlan antes de armar el zip.
- **Actualizador (`s_fide_updater` + `update/` de la GUI):** el paquete solo puede instalar la lista cerrada de `UpdatePackage.isInstallable` (nunca `sfide-defaults.properties`, runtimes, `test/`, `xsd/`); toda descarga valida cada salto de redirección contra la lista de dominios de `UpdateService.isAllowedUrl` y verifica SHA-256; el reemplazo es todo-o-nada con respaldo (`UpdateApplier`). En Windows, la forma fiel de detectar un archivo en uso es **moverlo de lugar**, no abrirlo para escribir (la JVM de otro usuario puede tener el jar abierto con permiso de escritura compartido pero sin permiso de renombrado). El actualizador se corre desde una COPIA del jar fuera de la instalación. En Linux/macOS un archivo recién extraído no hereda los permisos del que reemplaza y un zip armado en Windows no los guarda: `UpdateApplier.applyPermissions` los copia del anterior y fuerza el permiso de ejecución de todo `.sh` (sin eso, `SFide-GUI.sh` deja de poder lanzarse tras actualizar). Cambiar el formato del paquete exige actualizar `crear-paquete-actualizacion.ps1` y `crear-paquete-actualizacion.sh` (mismo manifiesto y mismo formato de hashes), `UpdatePackage` y la sección 9.15 del manual.
- **Los scripts `.ps1` con tildes deben guardarse CON BOM** (Windows PowerShell 5.1 lee un `.ps1` sin BOM como ANSI y los textos en español salen corruptos); `crear-paquete-actualizacion.ps1` es solo ASCII por esa razón.
- **Los módulos exclusivos de Windows (`XMLSignerWindowsCSP`, `PDFSignerWindowsCSP`, `WindowsCertificateStoreView`) acceden al almacén de certificados en el mismo proceso JVM** (`KeyStore.getInstance("Windows-MY", "SunMSCAPI")`), sin invocar ningún ejecutable externo — por diseño quedan inmunes a la categoría de falla "PowerShell bloqueado o colgado por política corporativa" que sí puede afectar código que use `ProcessBuilder` (como la creación de accesos directos de `s_fide_gui`). Cualquier excepción real ahí (proveedor ausente, acceso denegado) ya cae en el mismo `catch (Exception)` de nivel superior que el resto de los módulos — no necesita manejo especial adicional.
- **Nunca borrar (`git tag -d` + `git push origin --delete`) un tag de git que tiene un GitHub Release adjunto.** GitHub ancla el Release al objeto de tag subyacente, no solo al nombre — borrar ese objeto borra el Release entero (notas y assets incluidos), confirmado empíricamente al mover el tag `v1.1.1` la primera vez. **La forma segura de mover un tag con Release adjunto a otro commit es `git tag -f -a <tag> -m "..."` seguido de `git push --force origin <tag>`** — un único *force-update* atómico del ref, nunca un delete-luego-create. Confirmado empíricamente una segunda vez: el mismo Release (mismo `id`, mismos assets) siguió existiendo intacto después de mover `v1.1.1` con este método. Si por algún motivo se necesita borrar el tag de todas formas, hacelo sabiendo que el Release se pierde con él — recrealo a propósito (mismas notas, misma versión, nuevos assets) como parte del mismo movimiento, nunca asumas que sobrevive solo.
- **`PdfSignatureAppearance` (`getSignatureAppearance()`, `setPageRect()`, `setPageNumber()`, `setRenderingMode()`, `setLayer2Text()`, `setLayer2FontSize()`) está deprecado desde iText 8, pero el reemplazo ya existe en la misma versión 8.0.5 que usa este proyecto — no hace falta esperar a un salto a iText 9.** El patrón vigente: armar un `SignerProperties` (`setFieldName()`, `setPageRect()`, `setPageNumber()`, `setCertificationLevel()`) y, si la firma es visible, un `SignatureFieldAppearance(fieldName).setContent(texto).setFontSize(tamaño)` asociado vía `signerProperties.setSignatureAppearance(...)` — y construir el `PdfSigner` con el constructor de 5 argumentos `(reader, outputStream, null, stampingProperties, signerProperties)` en vez del de 3 (el `null` es el parámetro de ruta de archivo temporal, seguro de pasar así — confirmado por bytecode que sin él ya se usaba un buffer en memoria). El modo `RenderingMode.DESCRIPTION` (solo texto, sin gráfico/nombre) ya no existe como tal: `setContent(String)` sin imagen produce el mismo resultado visual. Verificado firmando y verificando un PDF real que el resultado visual y la validez de la firma no cambiaron.
- **Firma de PDF en cadena (1.5.0): nunca reescribir un PDF que ya tiene firmas.** Cifrar, certificar o abrir el documento con un `PdfWriter` que lo serialice de nuevo invalida las firmas anteriores (la primera quedaba ilegible: `Unknown PdfException`), y una certificación DocMDP solo vale como **primera** firma. Por eso, `-l`/`-k true` sobre un documento que ya tiene firmas es una **firma de cierre** (firma de aprobación en modo *append*, motivo "Firma final: documento cerrado", más `/Lock {Action All, P 1}` **sin** lista `/Fields`: con `Fields []` Acrobat no lo reconoce). Un documento cerrado (DocMDP nivel 1, bloqueo de Acrobat o firma de cierre) nunca se firma encima: invalida la firma de quien lo cerró. Ver `PdfDocumentAnalyzer` y la sección 7.7 del manual técnico.
- **`PdfDocumentAnalyzer` y `RevocationValidator` están duplicados en cuatro módulos** (`pdf_signer_pkcs11`, `pdf_signer_pkcs12`, `pdf_signer_windows_csp`, `pdf_verify_signatures`), idénticos salvo la línea `package` y el nombre del paquete en los `import` (cada jar debe seguir siendo independiente). **La copia de referencia es la de `pdf_signer_pkcs12`**: si tocás una, copiala a las otras tres con `sed 's/pdf_signer_pkcs12/<modulo>/g'`. Las pruebas de `integration_tests` ejercitan las tres firmadoras y el verificador, pero no detectan una copia desactualizada.
- **Política de mensajes para quien no es técnico (1.5.0).** Los mensajes sobre firmas anteriores tienen tres niveles y solo el último alarma: *informativo* (un certificado vencido o dado de baja **después** de firmar, notas), *aviso suave* (no se pudo comprobar algo, sin Internet, algoritmo desconocido) y *alerta firme* (firma realmente inválida: documento modificado, firma dañada, certificado no vigente o ya dado de baja **al firmar**, contenido alterado tras la última firma). Un error al **procesar** una firma no es una firma inválida. Nada de OCSP/CRL/PKCS#7/DocMDP en un texto que ve el usuario (hay una prueba que lo controla en los textos de la GUI).
- **La verdad de la interoperabilidad con PDF es Acrobat, no iText.** Antes de dar por bueno un cambio en cómo se firma, un PDF firmado con S-FiDE tiene que abrirse en Acrobat Reader con las firmas "sin modificaciones", y lo que Acrobat firma con bloqueo tiene que rechazarse. **No agregar al repositorio PDF reales** (datos personales; el repositorio es público): las pruebas con un PDF real leen su ruta de la variable de entorno `SFIDE_PDF_REAL` y se saltean si falta.
- **Cada versión nueva agrega su sección a `s_fide_gui/src/main/resources/text/NOVEDADES.txt`** (`## versión | título`, escrita para personas no técnicas, conservando todo el historial): es lo que ve cada usuario en su primer arranque con la versión nueva, y quien salta versiones ve las de todas las intermedias (`ReleaseNotes`). Los recursos `text/**` de la GUI los **filtra Maven**: no escribir `${...}` ni `@...@` en ellos. Y el cuerpo del Release de GitHub es lo que el actualizador de la versión anterior muestra al pedir autorización: texto plano; el actualizador muestra solo los primeros 4000 caracteres (y agrega `[...]`), así que **lo esencial va al principio** (ver `release-notes/notas-de-la-version-1.5.0.txt`; después de esos 4000 pueden seguir las secciones de descargas, que se ven completas en la página del Release).
- **Antes de publicar una versión, probar la actualización con el `SFideUpdater.jar` de la versión anterior, no con el nuevo.** El actualizador que corre es el ya instalado (se ejecuta desde una copia fuera de la instalación). Procedimiento usado en 1.5.0: armar el paquete con `crear-paquete-actualizacion.ps1`, copiar la instalación anterior a una carpeta de prueba, y correr `java -jar <copia de SFideUpdater.jar> verificar|aplicar <paquete.zip> <instalación>`; no correrlo desde el propio jar de la instalación (los archivos en uso lo harían fallar a propósito).
- **Una sola operación a la vez en la GUI.** Todo botón "Ejecutar" se deshabilita con `GUIUtils.busyProperty()`; cualquier camino nuevo que lance un proceso debe pasar por `GUIUtils.executeCommand`/`executeSignCommandWithRevocationCheck` o envolverse en `beginBusy`/`endBusy`. Las consultas de solo lectura de la GUI a los jars (`-analizar-documento`, `-vista-previa-texto`) usan `GUIUtils.ejecutarYCapturar`. Para el texto de la firma, la GUI **nunca** manda la contraseña de un token (un intento de PIN gastado cuenta para el bloqueo).
- **En la GUI, abrir enlaces, documentos o carpetas con el sistema se hace con `ExternalOpener` (JavaFX `HostServices`), nunca con `java.awt.Desktop`.** `PdfDocumentView` activa `java.awt.headless=true` para dibujar PDF y, en ese modo, `Desktop.getDesktop()` lanza `HeadlessException` (comprobado): los botones habrían funcionado hasta abrir el primer PDF y fallado después. Las fuentes de la GUI deben tener alternativa en Windows, macOS y Linux (ver `XmlViewerWindow.elegirFamiliaDeCodigo`; `Consolas` sola no existe fuera de Windows).
- **Ningún diálogo de la GUI queda sin ventana propietaria.** Un `Alert`/`Dialog` sin `initOwner` es una ventana suelta (puede quedar detrás de otra y no se cierra con la que lo abrió). Nuevo código: `initOwner(...)` explícito, o `DialogOwner.conPropietario(alert).showAndWait()`. Los visores (`viewer`) son ventanas con la principal como propietaria.
- **Armado de los paquetes de un release (resumen).** `./mvnw clean install` completo → `install.bat <carpeta> <vendor>` → `crear-paquete-actualizacion.ps1` (paquete de actualización), `crear-distribucion-zip.ps1` (ZIP de Windows y Linux) y `crear-distribucion-macos.ps1` (dos `.tar.gz`), todos hacia `dist-release/` (ignorada por git). Del JDK se omiten `jmods`, `include` y `lib/src.zip`. Antes de publicar, probar la actualización con el `SFideUpdater.jar` REAL de la versión anterior (descomprimido de su ZIP publicado), no con el actual.
- **macOS (1.5.0): un runtime por procesador y paquete propio.** Las carpetas de runtime son `macos-aarch64` (Apple Silicon) y `macos-x64` (Intel), igual que `windows-x64`/`linux-x64`: `SFide-GUI.sh` las elige con `uname -m` y `SFideUpdater.platformFolder` con `os.arch` — si cambiás una, cambiá la otra. El paquete de macOS es un `.tar.gz` (un zip armado en Windows pierde los permisos de ejecución del JDK) y lo arma `herramientas/CrearDistribucionMacOS.java` (vía `crear-distribucion-macos.ps1`/`.sh`) leyendo los originales de Oracle y Gluon **sin extraerlos a Windows** (se perderían permisos y los enlaces simbólicos de `legal`). `SFide-GUI.command` solo va en ese paquete y **nunca** en el de actualización: el actualizador rechaza el paquete entero si trae un archivo que no está en `isInstallable`. **Nada de esto se probó en un Mac real**: antes de publicar el paquete, alguien con un Mac debe arrancarlo.
- **Revisión de seguridad ANTES de cada release (obligatoria).** Procedimiento usado en 1.5.0 (resultado y método en la sección 3.1 del manual técnico, que se actualiza en cada release): (1) listar lo que realmente se distribuye (`./mvnw dependency:list -DincludeScope=runtime` por módulo, más los runtimes embebidos); (2) consultar OSV (`https://api.osv.dev/v1/querybatch`, ecosistema `Maven`) para cada artefacto y versión, **con un control positivo** (una versión vieja conocida debe devolver avisos: si no, la consulta no sirve); (3) comparar con las últimas versiones de Maven Central y actualizar lo seguro; las dependencias que llegan transitivamente se fijan en `dependencyManagement` del `pom.xml` padre; (4) inspeccionar los jars generados (no hay bibliotecas que no deberían estar, como Jackson) y verificar **en ejecución** qué versión se carga; (5) revisar si el JDK y JavaFX embebidos siguen con soporte; (6) correr el reactor completo con las pruebas de integración; (7) documentar el resultado y los pendientes de decisión.
- **`XmlBom` está duplicada en cinco módulos** (`xml_signer_pkcs11`, `xml_signer_pkcs12`, `xml_signer_windows_csp`, `xml_verify_signatures`, `xml_verify_xsd_structure`), idéntica salvo la línea `package`: si se modifica, hay que actualizar las cinco copias. Todo XML que lea un módulo nuevo debe pasar por `XmlBom.leer(...)` y no abrir el archivo con `InputStreamReader`: el lector de Java **no** descarta el BOM de UTF-8 y el analizador falla con `Content is not allowed in prolog`. Quitar el BOM nunca altera una firma (C14N no lo incluye); se hace en memoria y jamás se modifica el archivo de entrada. `XmlBomTest` (en `integration_tests`) lo comprueba con firmas reales.
- **Fines de línea en la GUI.** Algunos archivos de `s_fide_gui` (por ejemplo `GUIUtils.java` y `css/styles.css`) tienen CRLF en el árbol de trabajo (`core.autocrlf`): al editarlos con una herramienta que escriba LF se mezclan los finales de línea. Respetar el formato del archivo.

---

## 7. Especialización de comercio exterior ALADI/MERCOSUR (COD/CODEH/DJO/DJOEH)

S-FiDE tiene soporte especializado para Certificados de Origen Digital (`COD`/`CODEH`) y Declaraciones Juradas de Origen (`DJO`/`DJOEH`) usados en acuerdos de comercio exterior entre países ALADI/MERCOSUR — estructura de firma en dos etapas, reglas de orden obligatorias, y reglas de fecha/huso horario distintas por tipo de elemento para la validación de revocación. Está completamente documentado en **[`doc/manual-tecnico-integracion.md`, sección 10](doc/manual-tecnico-integracion.md#10-especialización-de-comercio-exterior-aladimercosur-cod-codeh-djo-y-djoeh)**. Leé esa sección completa antes de tocar `SignatureTimeExtractor`, `TimezoneConverter`, o la lógica de firma de `XMLSignerPKCS11`/`PKCS12`/`WindowsCSP` relacionada con estos elementos.

---

## 8. Antes de abrir un PR

- [ ] `./mvnw clean install` del reactor completo pasa sin errores.
- [ ] Si tocaste código de firma/verificación, lo probaste contra un archivo real (no solo compiló).
- [ ] Ningún mensaje de error nuevo expone una traza de Java o texto técnico no traducido.
- [ ] Todo string orientado al usuario está en español.
- [ ] Si agregaste un módulo o cambiaste un contrato de CLI, actualizaste `doc/manual-tecnico-integracion.md` y `.html`.
- [ ] El archivo nuevo (si corresponde) lleva el bloque de licencia GPLv2 completo (sección 5).
- [ ] No agregaste una dependencia Java entre módulos ni rompiste la independencia de alguno.
- [ ] Si tocaste `PdfDocumentAnalyzer` o `RevocationValidator`, actualizaste las cuatro copias (ver sección 6).
- [ ] Si es una versión nueva: agregaste su sección a `NOVEDADES.txt`, redactaste el cuerpo del Release (texto plano, menos de 4000 caracteres) y probaste la actualización desde la versión anterior con **su** `SFideUpdater.jar`.
- [ ] Si es una versión nueva: hiciste la revisión de seguridad de dependencias (ver sección 6) y actualizaste la sección 3.1 del manual técnico.
- [ ] Los mensajes de commit explican el *por qué* del cambio, en español, siguiendo el estilo del historial existente (`git log` para ver ejemplos).

---

Para todo lo demás — historial de versiones, glosario de términos (ALADI, MERCOSUR, DocMDP, PKCS#11, etc.), especificaciones técnicas completas — el documento de referencia es **[`doc/manual-tecnico-integracion.md`](doc/manual-tecnico-integracion.md)**.
