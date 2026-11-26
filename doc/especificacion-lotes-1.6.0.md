# S-FiDE 1.6.0 — Especificación de las operaciones por lotes

**Estado:** decisiones cerradas con el dueño, sin implementar. Rama `v1.6.0`. Cuando se implemente, el contenido pasa al manual técnico (`manual-tecnico-integracion.md` y `.html`) y a la Guía de Usuario, y este archivo se elimina.

---

## 1. Alcance

| Con lote (7 módulos) | Sin cambios |
|---|---|
| `XMLSignerPKCS11`, `XMLSignerPKCS12` | `TokenSlotsView` |
| `PDFSignerPKCS11`, `PDFSignerPKCS12` | `TokenCertificateExtractor`, `PKCS12CertificateExtractor` |
| `XMLVerifySignatures`, `PDFVerifySignatures` | `WindowsCertificateStoreView`, `XMLSignerWindowsCSP`, `PDFSignerWindowsCSP` |
| `XMLVerifyXSDStructure` | `SFideUpdater` |

El lote va **dentro de cada `.jar`**: los integradores son los principales usuarios. No hay módulo nuevo ni dependencias entre módulos. La GUI usa la misma interfaz que un integrador. Los módulos sin cambios no admiten `-lote` ni `-revisar`.

**Regla del dueño: todas las validaciones están en los `.jar`, nunca en `SFide-GUI`.** La GUI puede elegir qué archivos pone en una lista (por su nombre), pedir confirmaciones a la persona y mostrar lo que informa un `.jar`. No decide por su cuenta si un documento, un certificado o una opción son válidos.

### 1.1 Regla: una sola acción por lote (firma)

Todo el lote recibe **exactamente la misma operación**. No se admiten comportamientos mezclados.

* **XML:** se firma una sola cosa: el documento completo o **un** elemento (`COD`, `DJO`, `CODEH`, `DJOEH` u otro identificador, como en el modo de un documento).
* **PDF:** la misma firma en todos: misma página, posición, tamaño, texto y opciones. Si se cierra, se cierran todos (según el documento tenga o no firmas previas, el cierre se aplica como firma de cierre o como certificación con cifrado; en ambos casos queda cerrado).
* **No se mezclan tipos de XML.** Todos los documentos del lote deben corresponder:

| Elemento pedido | Todos deben ser |
|---|---|
| `COD`, `CODEH` | Certificados de Origen Digital |
| `DJO`, `DJOEH` | Declaraciones Juradas de Origen |
| `""` (documento completo) | XML que no sean de comercio exterior |
| Otro identificador | Del mismo tipo que el primer documento legible de la lista |

Si un documento no corresponde, el firmador **rechaza el lote completo antes de acceder al certificado**: no se firma ninguno. Informa cada documento que no corresponde (`ERROR` / `TIPO_NO_CORRESPONDE`), deja el resto como `NO_PROCESADO` y corta con `LOTE_MEZCLADO`. Los documentos que no se pueden leer o interpretar conservan su propio error y no cuentan para esta regla.

Los demás errores propios de un documento (ya firmado, sin la firma previa, firma que no entra en la página, PDF cerrado) afectan solo a ese documento.

La regla **no** se aplica a la verificación ni a la validación XSD. El modo de un documento no cambia.

## 2. Compatibilidad con la 1.5.1

**Regla:** toda invocación que funciona con la 1.5.1 se comporta igual con la 1.6.0 (salida estándar, salida de error, código de salida y archivos generados).

**Excepciones declaradas:**

1. `PDFSignerPKCS12` informa el éxito y las advertencias por `stdout` (en la 1.5.1 salían por `stderr` y `stdout` quedaba vacío). El texto y el código de salida no cambian. Se destaca en la guía de migración, en las novedades y en las notas del Release.
2. `PDFVerifySignatures` y `PDFSignerPKCS11` dejan de escribir en `stderr` las líneas técnicas de Java (fecha y nombre de clase). Cada error queda en una sola línea en español.
3. `PDFSignerPKCS12`, ante una contraseña incorrecta, informa "Contraseña incorrecta o archivo PKCS#12 inválido" (hoy: "Error al validar los archivos").
4. `-version` informa 1.6.0.

**Cómo se comprueba:** antes de modificar código se graban `stdout`, `stderr`, código de salida y archivos de los `.jar` 1.5.1 publicados en una batería de casos (éxitos y errores). La 1.6.0 debe repetirlos. Solo se admiten las diferencias declaradas.

**Se conserva:** en el modo de un documento, la salida existente se sobrescribe y los firmadores y el verificador de PDF siguen ignorando argumentos desconocidos. `-informe` y `-log` solo tienen efecto en lote.

**Salvaguarda:** `-revisar` sin `-lote` es un **error** en los cuatro firmadores con lote. Nunca se ignora: quien pide revisar no debe terminar firmando.

## 3. Sintaxis

| Módulo | Invocación en lote |
|---|---|
| `XMLSignerPKCS11` | `-lote <lista> <biblioteca> <contraseña> <slot> <elemento> [-omitir-revocacion true\|false]` |
| `XMLSignerPKCS12` | `-lote <lista> <archivo.p12> <contraseña> <elemento> [-omitir-revocacion true\|false]` |
| `PDFSignerPKCS11`, `PDFSignerPKCS12` | Las opciones actuales, con `-lote <lista>` en lugar de `-i` (no se admiten juntas). **`-campo` no se admite** |
| `XMLVerifySignatures`, `PDFVerifySignatures` | `-lote <lista> [-simple]` |
| `XMLVerifyXSDStructure` | `-lote <lista> [<archivo.xsd>]` |

Todos admiten además `[-informe <archivo.csv>] [-log]`. Se aceptan `-lote`/`--lote`, `-informe`/`--informe`, `-log`/`--log` y `-revisar`/`--revisar`.

* En los módulos de argumentos posicionales, `-lote` es el **primer** argumento. En los firmadores de PDF va en cualquier posición de opción, pero **nunca se reconoce como valor** de otra (por ejemplo, el texto de `-t`).
* En lote, un argumento desconocido, repetido, mal formado o no admitido **corta antes de hacer nada** (`ARGUMENTOS`).
* El elemento XML y las opciones de PDF son **los mismos para todo el lote**.
* `-simple` en lote solo cambia el contenido del `.log`.

**Revisión sin firmar (`-revisar`, solo en los cuatro firmadores, junto con `-lote`):** ejecuta los pasos 1 a 5 y 8 de la sección 5, sin acceder al certificado ni escribir documentos. Usa los mismos argumentos que la firma; la contraseña se recibe pero **no se usa**, así que no gasta intentos de PIN. Informa cada documento como `LISTO`, `OMITIDO` o `ERROR` (o `NO_PROCESADO` si hay un corte). Admite `-informe`; no genera `.log`. Sirve a integradores y a la GUI.

## 4. Archivo de lista

* UTF-8, con o sin BOM. Si no es UTF-8 válido (por ejemplo UTF-16 o ANSI con acentos), se rechaza la lista completa.
* Una ruta por línea. Se quitan espacios de los extremos y las comillas que envuelven la ruta. Las líneas vacías se ignoran.
* El tabulador queda **reservado** para una ampliación futura: una línea que lo contenga es un error de ese documento.
* Las rutas relativas se resuelven desde la carpeta de la lista.
* Se procesa en el orden de la lista.
* Un documento repetido se informa como `OMITIDO` / `DUPLICADO` la segunda vez.
* Una extensión que no corresponde al módulo es un error de ese documento.

## 5. Ejecución

1. Analizar argumentos.
2. Leer la lista. Si está vacía, se corta.
3. Crear el informe, si se pidió. Si no se puede, se corta sin firmar nada.
4. Leer cada documento **sin tocar el certificado**: existencia, extensión, lectura y formato. En firma XML, comprobar la regla 1.1; si hay mezcla, cortar.
5. En firma, revisar cada documento, todavía sin certificado: salida existente; en XML, elemento presente, elemento ya firmado y firma previa obligatoria; en PDF, documento cerrado o restringido, página y posición.
6. En firma, si queda al menos un documento procesable, acceder al certificado **una sola vez**: vigencia, aptitud y revocación.
7. Procesar documento por documento. En firma, se controla la vigencia del certificado antes de cada uno.
8. Informar el resumen.

**Cortan todo el lote:** contraseña o PIN incorrecto (nunca se reintenta), token bloqueado o retirado, certificado no apto o revocado, y **3 errores seguidos** de acceso a la clave al firmar. Lo pendiente queda `NO_PROCESADO`.

**No cortan:** los errores propios de un documento, salvo la mezcla de tipos (1.1).

**Escritura de las salidas** (`<nombre>-signed.<extensión>`, en la misma carpeta):

* Se escribe en un temporal de nombre único que no termina en `.xml` ni `.pdf`.
* En PDF, el autocontrol de firmas se aplica sobre el temporal.
* El archivo final se crea con una operación que **falla si ya existe**. Nunca se pisa una salida, ni siquiera con dos lotes simultáneos.

No se admite usar el mismo token desde dos lotes a la vez.

## 6. Resultados

### 6.1 Estados y códigos

| Operación | Estado | Códigos |
|---|---|---|
| Revisión (`-revisar`) | `LISTO` | `OK` |
| Firma | `FIRMADO` | `OK` |
| Firma | `FIRMADO_CON_AVISOS` | `FIRMA_PREVIA_INVALIDA`, `MODIFICADO_DESPUES_DE_FIRMAR` |
| Firma | `OMITIDO` | `SALIDA_YA_EXISTE`, `DUPLICADO` |
| Verificación | `OMITIDO` | `DUPLICADO` |
| Verificación | `VALIDO` | `OK` |
| Verificación | `VALIDO_CON_OBSERVACIONES` | `COMPROBACION_PENDIENTE` |
| Verificación | `NO_VALIDO` | `FIRMA_NO_VALIDA`, `MODIFICADO_DESPUES_DE_FIRMAR`, `ESTRUCTURA_NO_VALIDA` |
| Verificación | `SIN_FIRMAS` | `SIN_FIRMAS` |
| Todas | `ERROR` | Ver abajo |
| Todas | `NO_PROCESADO` | La causa del corte |

* **PDF con una firma anterior inválida:** se firma igual (como hoy) y queda `FIRMADO_CON_AVISOS` con el motivo. Una comprobación que no se pudo hacer no es un aviso firme: queda `FIRMADO` con la explicación en el mensaje.
* **Verificación XML sin Internet** o con revocación no comprobable: `VALIDO_CON_OBSERVACIONES`.
* **Falla al procesar una firma XML:** `NO_VALIDO`, igual que hoy.
* **XSD:** estructura inválida es `NO_VALIDO` aunque no haya firmas; estructura válida sin firmas es `SIN_FIRMAS`; un problema del esquema es `ERROR`. El validador no comprueba revocación. Cada esquema se descarga una vez por lote.

**Códigos de `ERROR`:** `ARCHIVO_NO_ENCONTRADO`, `ARCHIVO_ILEGIBLE`, `EXTENSION_NO_CORRESPONDE`, `LINEA_NO_VALIDA`, `DOCUMENTO_MAL_FORMADO`, `TIPO_NO_CORRESPONDE`, `ELEMENTO_NO_ENCONTRADO`, `ELEMENTO_YA_FIRMADO`, `FALTA_FIRMA_PREVIA`, `DOCUMENTO_CERRADO`, `DOCUMENTO_RESTRINGIDO`, `PAGINA_INEXISTENTE`, `FIRMA_NO_ENTRA`, `PROTECCION_NO_APLICABLE`, `SALIDA_NO_ESCRIBIBLE`, `ERROR_AL_FIRMAR`, `XSD_NO_REFERENCIADO`, `XSD_NO_DESCARGADO`, `XSD_NO_VALIDO`, `ERROR_INESPERADO`.

**Causas de corte:** `ARGUMENTOS`, `LISTA_ILEGIBLE`, `LISTA_VACIA`, `INFORME_NO_CREADO`, `LOTE_MEZCLADO`, `BIBLIOTECA`, `CONTRASENA`, `TOKEN_BLOQUEADO`, `TOKEN_NO_DISPONIBLE`, `CERTIFICADO_NO_APTO`, `CERTIFICADO_REVOCADO`, `FALLAS_CONSECUTIVAS`, `XSD`.

Los códigos son estables entre versiones. Los mensajes (en español) pueden mejorar.

### 6.2 Código de salida

`0` solo si no hubo corte y **cada documento de la lista** terminó `LISTO` (revisión), `FIRMADO`, `FIRMADO_CON_AVISOS`, `VALIDO`, `VALIDO_CON_OBSERVACIONES` u `OMITIDO`/`DUPLICADO`.

En cualquier otro caso, `1`. En particular, **`OMITIDO`/`SALIDA_YA_EXISTE` da `1`**: el documento pedido no se firmó y el archivo existente puede ser de una versión anterior. En consecuencia, repetir una lista ya procesada en parte devuelve `1` aunque se firme todo lo que faltaba; para obtener `0`, la lista debe contener solo lo pendiente.

### 6.3 Salida estándar

En **UTF-8**, como todas las salidas de S-FiDE. Mismo formato que el informe CSV: separador `;`, campos entre comillas y una comilla dentro de un campo se duplica. La primera palabra de la línea va sin comillas.

```text
LOTE_INICIO;"1";"<programa>";"<versión>";"<total>"
LOTE_DOC;"<n>";"<total>";"<estado>";"<código>";"<entrada>";"<salida>";"<log>";"<mensaje>"
LOTE_RESUMEN;"total=…";"correctos=…";"con_avisos=…";"no_validos=…";"sin_firmas=…";"con_error=…";"omitidos=…";"no_procesados=…";"corte=…"
```

* El `1` de `LOTE_INICIO` es la versión del formato.
* `LOTE_INICIO` y `LOTE_RESUMEN` salen **siempre** que se reconoce `-lote`, aun ante un corte (con `total=0` si no se llegó a leer la lista).
* Leída la lista, **cada documento tiene exactamente una línea** `LOTE_DOC`, que se emite apenas se conoce su resultado.
* `correctos` cuenta `LISTO`, `FIRMADO` y `VALIDO`; `con_avisos`, `FIRMADO_CON_AVISOS` y `VALIDO_CON_OBSERVACIONES`. Los contadores suman `total`.
* `corte=NINGUNO` si no hubo corte.
* Las rutas son absolutas. Los campos pueden estar vacíos. Los saltos de línea dentro de un campo se reemplazan por un espacio.
* Puede haber otras líneas (avisos del lote: reloj, cadena, revocación). El integrador procesa **solo las que empiezan con `LOTE_`**. No se imprime detalle por documento.
* **Sin `LOTE_RESUMEN`, el lote está incompleto.**
* `stderr`: la causa de un corte y, por cada documento con problema, `archivo: mensaje`. El integrador debe leer ambas salidas a la vez.

### 6.4 Informe CSV (`-informe`, opcional)

* Columnas con encabezado: `n;total;estado;codigo;entrada;salida;log;mensaje`.
* Separador `;`, campos entre comillas (un `;` dentro de un mensaje no corre las columnas); una comilla dentro de un campo se duplica.
* **UTF-8 sin BOM** (el informe no está pensado para abrirse en Excel). Fin de línea del sistema operativo.
* Se escribe y se vuelca línea por línea. Si falla a mitad del lote, se avisa por `stderr`, el lote sigue y el código es `1`.
* El nombre no puede terminar en `.xml` ni `.pdf`, ni ser la propia lista. Un informe anterior se reemplaza.

### 6.5 Registro por documento (`-log`, opcional)

* `<documento>.log` conservando la extensión (`contrato.xml.log`), en la misma carpeta, en **UTF-8**. Si existe, se agrega al final.
* Contiene encabezado (fecha, programa, versión), la salida y los errores de ese documento, y el resultado.
* Nunca incluye contraseña, PIN, línea de comandos ni trazas técnicas.
* Si no se puede escribir, se avisa y no afecta el documento ni el código de salida.
* Sin `.log` para `NO_PROCESADO` ni para archivos inexistentes.

## 7. `SFide-GUI`

### 7.1 Pantalla

El menú lateral no cambia. En las siete opciones con lote, la pantalla tiene dos pestañas:

* **Un documento** (primera): la pantalla actual, sin cambios.
* **Por lote**: carpeta, resumen, botón "Revisar" (en las de firma), botón de ejecución con la cantidad ("Firmar 48 documentos") y tabla de resultados.

Los datos comunes (biblioteca, slot, contraseña, certificado, elemento, XSD y opciones de PDF) están vinculados entre ambas.

### 7.2 Exploración de la carpeta

* Solo la carpeta elegida, **sin subcarpetas**. Solo `.xml` o `.pdf` según la opción, sin distinguir mayúsculas, en orden alfabético.
* La GUI no descarta archivos por su contenido, tamaño ni atributos: eso lo informa el `.jar`.
* Si la carpeta cambió desde que se exploró, se avisa y no se agrega nada sin revisar.

### 7.3 Pestañas de firma

Casilla, desactivada por defecto: **"Incluir solo los archivos que terminan en -signed"**.

* Desactivada: entran solo los nombres que **no** terminan en `-signed`.
* Activada: entran solo los que **sí** terminan en `-signed` (para agregar otra firma).
* "Revisar" explica que la regla mira el **nombre**: un documento firmado por otro programa sin ese sufijo cuenta como sin sufijo.

**"Revisar"** invoca al firmador con `-revisar` sobre los archivos seleccionados por la casilla y muestra su respuesta:

| Estado en pantalla | Cuándo |
|---|---|
| Marcado | El `.jar` informa `LISTO` |
| Bloqueado (no se puede marcar), con el mensaje del `.jar` | El `.jar` informa `OMITIDO` o `ERROR` |
| Desmarcado (se puede marcar) | `LISTO`, casilla activada, el nombre termina en `-signed-signed` y su antecesor `-signed` está en la carpeta (criterio de selección por nombre) |

* Los archivos que la casilla deja fuera se ven aparte, con ese motivo.
* Si se ejecuta sin haber abierto "Revisar", la GUI hace la misma consulta; **si hay algo bloqueado, muestra "Revisar" antes de firmar**.
* Lo bloqueado y desmarcado **no se incluye en la lista** de firma, así que no afecta el código de salida. El resumen final dice cuántos quedaron fuera.
* Si el `.jar` informa `LOTE_MEZCLADO`, la GUI muestra qué documentos no corresponden y **no permite ejecutar**: hay que separarlos en otra carpeta o elegir el elemento correcto.

### 7.4 Verificación y XSD

Entran todos los archivos de la extensión, firmados o no.

### 7.5 PDF

* La posición de la firma se elige sobre un documento de la carpeta y se aplica a todos. En lote no se ofrece firmar en un campo existente. Se advierte que en páginas de otro tamaño o rotación puede tapar contenido; donde no entra, falla ese documento.
* Con bloqueo activado se pide confirmación indicando la cantidad: es irreversible.

### 7.6 Ejecución y resultados

* Con PKCS#12, la consulta de revocación y su confirmación se hacen **una vez** antes del lote, con las reglas actuales.
* Menú y pestañas bloqueados; "Cancelar" disponible. Al cancelar, lo no informado figura como no procesado; nunca queda una salida a medias, aunque el documento que se estaba procesando puede haber quedado firmado.
* El límite de 10 minutos pasa a contarse **sin novedades**, no sobre el total.
* `stdout` y `stderr` se leen por separado.
* La contraseña de sesión se recuerda si al menos un documento quedó firmado. Se olvida ante un corte por contraseña.
* Tabla en vivo con ícono, estado y mensaje; resumen único al final.
* Acciones: "Mostrar solo con problemas", "Ver detalle" (en verificación ejecuta ese archivo solo; en firma muestra el `.log` si se pidió), "Reintentar los que fallaron", "Abrir informe", "Abrir carpeta".
* Casilla "Guardar un registro por documento", desactivada por defecto.
* La lista y el informe se guardan siempre en `<carpeta del usuario>/S-FiDE/lotes/`.

## 8. Cambios internos necesarios

* Verificadores y validador XSD: dejar de llamar `System.exit` dentro de la lógica.
* Firmadores XML: conservar la causa original de los errores.
* `PDFSignerPKCS11`: un solo ingreso del PIN (hoy son dos por documento).
* Firmadores de token: configurar el proveedor y abrir el token una vez.
* Salida: encauzar los cuatro mecanismos actuales (flujos estáticos, `java.util.logging`, `System.out`, logback) para que el `.log` salga completo y `stdout` quede limpio en lote.
* Reiniciar por documento los avisos de una sola vez (BOM, slot).
* El código de lote duplicado entre módulos lleva una prueba que compruebe que las copias son idénticas.
* GUI: borrar el código de validación sin uso de `ModuleValidator` (`validatePKCS11`, `validatePKCS12`, `validateXML`, `validatePDF`, `confirmOverwrite`).

## 9. Pruebas

* Batería de compatibilidad contra la 1.5.1 (sección 2).
* Pruebas automáticas de lote con PKCS#12, incluidos los cortes, la mezcla de tipos, los omitidos y las salidas existentes.
* `-revisar` no firma, no accede al certificado y, sin `-lote`, termina con error.
* **Con tokens reales:** PIN pedido en cada firma, retiro del token a mitad de lote, causas de corte y tiempo por documento.
* Lote grande de verificación (revocación en línea).
* Lectura del CSV y de las líneas `LOTE_` desde OpenEdge, con rutas con acentos.
* PDF firmados en lote abiertos en Acrobat.

## 10. Orden de trabajo

1. Grabar el comportamiento de la 1.5.1.
2. Versión 1.6.0 y excepciones declaradas de la sección 2.
3. Lote en `XMLSignerPKCS12`; revisión del contrato por el dueño.
4. `XMLSignerPKCS11` con tokens.
5. Firmadores de PDF.
6. Verificadores y XSD.
7. GUI.
8. Manuales, `NOVEDADES.txt`, `CHANGELOG`, revisión de seguridad.

El Release se publica solo con permiso expreso del dueño.
