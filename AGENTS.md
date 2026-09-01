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
| `token_certificate_extractor` | Extrae un certificado de un token PKCS#11 a `.pem` |
| `pkcs12_certificate_extractor` | Extrae un certificado de un archivo PKCS#12 a `.pem` |
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
7. **La GUI (`s_fide_gui`) nunca es un atajo privilegiado.** Invoca los mismos `.jar` con `ProcessBuilder`, con los mismos argumentos que usaría un integrador externo. Nunca reimplementa lógica de firma/verificación por su cuenta.
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
- **Los tres firmadores de PDF deben acoplar siempre los dos efectos de "bloquear" un documento** (certificación DocMDP + cifrado AES-256), sin importar si la firma es visible o invisible. Hubo un bug real donde uno de los tres solo aplicaba la certificación con firma visible — ver commit `9fd0828`.
- **Vocabulario de comandos especiales unificado.** Los 13 módulos CLI aceptan `-version`/`-v`/`--version`, `-ayuda`/`-h`/`--help`, `-licencia`/`--license` indistintamente. Si agregás un módulo nuevo o un comando especial nuevo, aceptá las tres formas desde el principio.
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
- **Los módulos exclusivos de Windows (`XMLSignerWindowsCSP`, `PDFSignerWindowsCSP`, `WindowsCertificateStoreView`) acceden al almacén de certificados en el mismo proceso JVM** (`KeyStore.getInstance("Windows-MY", "SunMSCAPI")`), sin invocar ningún ejecutable externo — por diseño quedan inmunes a la categoría de falla "PowerShell bloqueado o colgado por política corporativa" que sí puede afectar código que use `ProcessBuilder` (como la creación de accesos directos de `s_fide_gui`). Cualquier excepción real ahí (proveedor ausente, acceso denegado) ya cae en el mismo `catch (Exception)` de nivel superior que el resto de los módulos — no necesita manejo especial adicional.
- **Nunca borrar (`git tag -d` + `git push origin --delete`) un tag de git que tiene un GitHub Release adjunto.** GitHub ancla el Release al objeto de tag subyacente, no solo al nombre — borrar ese objeto borra el Release entero (notas y assets incluidos), confirmado empíricamente al mover el tag `v1.1.1` la primera vez. **La forma segura de mover un tag con Release adjunto a otro commit es `git tag -f -a <tag> -m "..."` seguido de `git push --force origin <tag>`** — un único *force-update* atómico del ref, nunca un delete-luego-create. Confirmado empíricamente una segunda vez: el mismo Release (mismo `id`, mismos assets) siguió existiendo intacto después de mover `v1.1.1` con este método. Si por algún motivo se necesita borrar el tag de todas formas, hacelo sabiendo que el Release se pierde con él — recrealo a propósito (mismas notas, misma versión, nuevos assets) como parte del mismo movimiento, nunca asumas que sobrevive solo.
- **`PdfSignatureAppearance` (`getSignatureAppearance()`, `setPageRect()`, `setPageNumber()`, `setRenderingMode()`, `setLayer2Text()`, `setLayer2FontSize()`) está deprecado desde iText 8, pero el reemplazo ya existe en la misma versión 8.0.5 que usa este proyecto — no hace falta esperar a un salto a iText 9.** El patrón vigente: armar un `SignerProperties` (`setFieldName()`, `setPageRect()`, `setPageNumber()`, `setCertificationLevel()`) y, si la firma es visible, un `SignatureFieldAppearance(fieldName).setContent(texto).setFontSize(tamaño)` asociado vía `signerProperties.setSignatureAppearance(...)` — y construir el `PdfSigner` con el constructor de 5 argumentos `(reader, outputStream, null, stampingProperties, signerProperties)` en vez del de 3 (el `null` es el parámetro de ruta de archivo temporal, seguro de pasar así — confirmado por bytecode que sin él ya se usaba un buffer en memoria). El modo `RenderingMode.DESCRIPTION` (solo texto, sin gráfico/nombre) ya no existe como tal: `setContent(String)` sin imagen produce el mismo resultado visual. Verificado firmando y verificando un PDF real que el resultado visual y la validez de la firma no cambiaron.

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
- [ ] Los mensajes de commit explican el *por qué* del cambio, en español, siguiendo el estilo del historial existente (`git log` para ver ejemplos).

---

Para todo lo demás — historial de versiones, glosario de términos (ALADI, MERCOSUR, DocMDP, PKCS#11, etc.), especificaciones técnicas completas — el documento de referencia es **[`doc/manual-tecnico-integracion.md`](doc/manual-tecnico-integracion.md)**.
