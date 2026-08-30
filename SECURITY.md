# Política de seguridad

S-FiDE es software de firma digital y verificación de firmas: un problema de seguridad acá puede significar que un documento inválido se acepte como válido, o que una clave privada quede expuesta. Por eso pedimos un canal de reporte responsable, separado de los issues públicos.

## Versiones soportadas

| Versión | Soportada |
|---|---|
| 1.1.x | ✅ |
| 1.0.x | ⚠️ Solo correcciones críticas de seguridad |
| Anteriores a 1.0.0 | ❌ |

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Envía un correo a **soporte@sauken.com.ar** con el asunto que empiece con `[SEGURIDAD]`, incluyendo:

- Descripción del problema y su impacto (por ejemplo: ¿permite que una firma inválida se reporte como válida? ¿expone material criptográfico? ¿permite ejecutar código arbitrario?).
- Módulo(s) afectado(s) y versión.
- Pasos para reproducirlo (idealmente con un archivo de ejemplo que no contenga datos reales ni claves privadas de producción).
- Si es posible, una sugerencia de corrección.

**No incluyas en el reporte** contraseñas, PINs de tokens reales, ni certificados o claves privadas de producción — usá siempre material de prueba.

## Qué podés esperar

- Confirmación de recepción a la brevedad.
- Evaluación del impacto y, si corresponde, una corrección — priorizada según la severidad (por ejemplo, un problema que afecte la validación de revocación o la integridad de una firma se trata como crítico).
- Coordinación sobre cuándo y cómo se hace pública la vulnerabilidad, una vez corregida, para dar tiempo a que quienes usan S-FiDE actualicen antes de la divulgación.
- Reconocimiento del reporte (si así lo preferís) en el historial de versiones, salvo que pidas lo contrario.

## Alcance

Se consideran problemas de seguridad, entre otros:

- Una firma inválida, revocada, o alterada que se reporte como válida (en cualquiera de los módulos de verificación).
- Exposición de una clave privada, PIN, o contraseña más allá de lo estrictamente necesario para la operación solicitada.
- Un stack trace de Java u otro detalle técnico interno expuesto al usuario final, si ese detalle revela información sensible (rutas de sistema, credenciales, etc.).
- Vulnerabilidades en las dependencias de terceros (BouncyCastle, iText, Apache Santuario) que afecten específicamente cómo S-FiDE las usa.

No se consideran problemas de seguridad los bugs funcionales sin impacto en la confidencialidad, integridad o disponibilidad — esos se reportan como issues normales.

## Buenas prácticas para quien integra S-FiDE

- Nunca hardcodees contraseñas de PKCS#12 ni PINs de token en código fuente versionado.
- Los argumentos de línea de comandos (incluida la contraseña del token/certificado) pueden quedar visibles en la lista de procesos del sistema operativo mientras el proceso corre — tenelo en cuenta en entornos multiusuario.
- Verificá siempre el **código de salida** del proceso (`0`/`1`), no el texto de la salida, para decidir si una operación fue exitosa — ver [AGENTS.md](AGENTS.md) y el [Manual Técnico de Integración](doc/manual-tecnico-integracion.md).
