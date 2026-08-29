# Cómo contribuir a S-FiDE

Gracias por tu interés en S-FiDE (Sistema de Firma Digital Extendido), de Grupo Sauken S.A. El proyecto se distribuye bajo la **GNU GPL v2 o cualquier versión posterior** — sos libre de descargar el código, hacer un fork, modificarlo y proponer cambios.

> **¿Estás usando un asistente de IA** (Claude Code, Cursor, GitHub Copilot, Codex CLI, o cualquier otro) **para contribuir?** Antes de nada, hacé que lea **[`AGENTS.md`](AGENTS.md)** — ahí está el detalle técnico completo (reglas del proyecto, trampas ya resueltas, plantilla de licencia) para que la asistencia parta del contexto correcto. Este documento (`CONTRIBUTING.md`) es la versión breve orientada a un colaborador humano; `AGENTS.md` es la versión completa.

## Antes de empezar

1. **Leé la filosofía del proyecto** en [`AGENTS.md`, sección 2](AGENTS.md#2-reglas-no-negociables). Las reglas ahí (contrato de línea de comandos, nunca exponer stack traces, un módulo = una capacidad, licencia GPLv2) no son negociables — un PR que las rompa se va a rechazar aunque el código funcione.
2. **Para cambios de firma/verificación digital o de la especialización ALADI/MERCOSUR (COD/CODEH/DJO/DJOEH):** leé primero [`doc/manual-tecnico-integracion.md`](doc/manual-tecnico-integracion.md), que es la referencia técnica completa y verificada del proyecto.

## Compilar el proyecto

Requisitos: JDK 23 (el repo incluye Maven Wrapper, no hace falta instalar Maven aparte).

```bash
git clone https://github.com/Grupo-Sauken-S-A/S-FIDE.git
cd S-FIDE
./mvnw clean install
```

Cada uno de los 13 módulos (ver tabla en `AGENTS.md`) genera su propio jar en su carpeta `target/`. Para armar una carpeta de distribución lista para ejecutar, usá `install.bat` (ver `Leeme.txt`).

## Estructura del repositorio

- Un `pom.xml` raíz de tipo agregador, y un módulo Maven por capacidad (firmar XML, firmar PDF, extraer certificados, etc. — cada uno independiente, sin dependencias Java entre sí en tiempo de ejecución).
- `doc/` — manual técnico de integración (fuente de verdad de la documentación).
- `shared-resources/` — único archivo de datos compartido entre módulos en tiempo de **build** (catálogo de drivers de tokens), no una dependencia en tiempo de ejecución.
- `s_fide_gui/` — interfaz gráfica JavaFX; invoca los demás módulos como procesos externos, nunca como librería.

## Convenciones de código

- **Español rioplatense** en todo texto visible para el usuario (mensajes de ayuda, mensajes de error). El código en sí puede tener identificadores en inglés, como ya es la convención existente.
- **Nunca** un stack trace de Java en la salida — todo error se traduce a un mensaje de texto simple antes de imprimirse.
- Cada módulo nuevo sigue el mismo patrón que los existentes: su propio `pom.xml`, `LICENSE.txt` y `HELP.txt` como recursos, y el bloque de licencia GPLv2 al inicio de cada archivo fuente (plantilla exacta en `AGENTS.md`, sección 5).
- Los jars de **distribución** nunca llevan la versión en el nombre (`XMLSignerPKCS11.jar`); los artefactos de Maven en `target/` sí la llevan — es el comportamiento esperado, no algo a corregir.

## Documentación

Si tu cambio afecta el comportamiento de un módulo (flags, mensajes de error, mecanismos de firma/verificación, tokens soportados), actualizá `doc/manual-tecnico-integracion.md` **y** `doc/manual-tecnico-integracion.html` en el mismo cambio — son el mismo contenido en dos formatos, no documentos independientes.

## Probar tus cambios

No alcanza con que compile. Si tocaste código de firma o verificación:
- Corré `./mvnw clean install` del reactor completo.
- Probá el módulo con un archivo real (XML o PDF de prueba, certificado PKCS#12 de prueba si hace falta) — varios bugs reales de este proyecto solo aparecieron contra archivos/hardware reales, nunca en una prueba puramente teórica.

## Enviar un cambio

1. Hacé un fork del repositorio y trabajá en una rama con nombre descriptivo.
2. Seguí las convenciones de esta guía y de `AGENTS.md`.
3. Escribí mensajes de commit en español, explicando el *por qué* del cambio (no solo el qué) — mirá `git log` para ver el estilo usado hasta ahora.
4. Abrí un Pull Request contra `main` describiendo el cambio y, si corresponde, cómo lo probaste.
5. Un PR que cambia comportamiento de un módulo sin actualizar `doc/manual-tecnico-integracion.md`/`.html` va a requerir esa actualización antes de poder mergearse.

## Reportar un problema

Abrí un issue en el repositorio describiendo el problema, los pasos para reproducirlo, y el módulo afectado. Si el problema es de seguridad (por ejemplo, una firma que debería rechazarse y no lo hace), indicalo claramente en el título.

## Licencia

Al contribuir, aceptás que tu contribución se distribuya bajo los mismos términos que el resto del proyecto: **GNU GPL v2 o cualquier versión posterior**. El texto completo está en `LICENSE.txt`.

## Soporte

**Grupo Sauken S.A.** — Córdoba, Argentina
Email: soporte@sauken.com.ar · Sitio: [www.sauken.com.ar](https://www.sauken.com.ar/)

El soporte técnico comercial es un servicio aparte, independiente de la libertad de uso y contribución del software.
