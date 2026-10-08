## Qué cambia y por qué

<!-- Explique el *por qué*, no solo el qué. -->

## Cómo se probó

<!-- Si toca firma o verificación, indique con qué archivo real lo probó (no alcanza con que compile). -->

## Lista de control

- [ ] `./mvnw clean install` del reactor completo pasa sin errores.
- [ ] Si toca firma/verificación, se probó contra un archivo real.
- [ ] Ningún mensaje nuevo expone una traza de Java ni texto técnico sin traducir; todo texto de usuario está en español.
- [ ] Si cambia un módulo o su línea de comandos, se actualizaron `doc/manual-tecnico-integracion.md` **y** `.html`.
- [ ] Todo archivo `.java` nuevo lleva el bloque de licencia GPLv2 completo (ver `AGENTS.md`, sección 5).
- [ ] No se agregó ninguna dependencia Java entre módulos.
- [ ] Si se cambió una dependencia: se revisó su seguridad (ver `AGENTS.md`, sección 6) y se actualizó la sección 3 del manual técnico.
