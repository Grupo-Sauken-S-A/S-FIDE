/*
  Derechos Reservados © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

  Este es un Software Libre; como tal redistribuirlo y/o modificarlo está
  permitido, siempre y cuando se haga bajo los términos y condiciones de la
  Licencia Pública General GNU publicada por la Free Software Foundation,
  ya sea en su versión 2 ó cualquier otra de las posteriores a la misma.

  Este “Programa” se distribuye con la intención de que sea útil, sin
  embargo carece de garantía, ni siquiera tiene la garantía implícita de
  tipo comercial o inherente al propósito del mismo “Programa”. Ver la
  Licencia Pública General GNU para más detalles.

  Se debe haber recibido una copia de la Licencia Pública General GNU con
  este “Programa”, si este no fue el caso, favor de escribir a la Free
  Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
  MA 02110-1301 USA.

  Autores: Juan Carlos Ríos y Juan Ignacio Ríos con la asistencia de Claude Sonnet 5.5
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

  Authors: Juan Carlos Ríos y Juan Ignacio Ríos with support of Claude Sonnet 5.5
  E-mail: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Company: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

 */

package com.sauken.s_fide.s_fide_updater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Aplica un {@link UpdatePackage} sobre una carpeta de instalación de S-FiDE
 * con la garantía de "todo o nada": o quedan reemplazados todos los archivos
 * del paquete, o la instalación vuelve exactamente al estado anterior.
 * <p>
 * Cómo se logra, en orden:
 * <ol>
 *   <li><b>Extracción a una carpeta de trabajo</b> dentro de la propia instalación
 *   (mismo volumen, para que los reemplazos sean renombres atómicos) y
 *   verificación del SHA-256 de cada archivo extraído — antes de tocar nada.</li>
 *   <li><b>Reemplazo archivo por archivo con respaldo</b>: el archivo actual se
 *   MUEVE a una carpeta de respaldo y el nuevo se mueve a su lugar. Los archivos
 *   cuyo contenido ya es idéntico no se tocan.</li>
 *   <li><b>Reversión</b> ante cualquier falla: se devuelven los respaldos y se borran
 *   los archivos nuevos ya colocados.</li>
 * </ol>
 * <b>Instalación compartida por varios usuarios.</b> En Windows, un jar que otro
 * usuario tiene abierto (su S-FiDE en ejecución) no se puede mover ni
 * reemplazar. Eso no se trata de forzar: se reintenta durante un tiempo
 * (por si la otra persona está cerrando) y, si sigue en uso, se REVIERTE todo
 * y se informa con claridad. Mientras dura la actualización existe una marca
 * ({@link #BUSY_MARKER}) para que ningún usuario arranque una instalación a
 * medio actualizar.
 */
final class UpdateApplier {
    /** Archivo-marca en la carpeta de instalación mientras hay una actualización en curso. */
    static final String BUSY_MARKER = ".sfide-actualizando";
    private static final Duration MARKER_STALE_AFTER = Duration.ofMinutes(15);
    private static final String WORK_PREFIX = ".sfide-trabajo-";
    private static final String BACKUP_PREFIX = ".sfide-respaldo-";

    private final Path installDir;
    private final UpdatePackage pkg;
    private final Consumer<String> log;
    private final Duration lockRetryWindow;
    private final Duration lockRetryInterval;

    UpdateApplier(Path installDir, UpdatePackage pkg, Consumer<String> log,
                  Duration lockRetryWindow, Duration lockRetryInterval) {
        this.installDir = installDir.toAbsolutePath().normalize();
        this.pkg = pkg;
        this.log = log;
        this.lockRetryWindow = lockRetryWindow;
        this.lockRetryInterval = lockRetryInterval;
    }

    // ------------------------------------------------------------------
    // Verificación previa (no modifica nada)
    // ------------------------------------------------------------------

    /**
     * Comprueba que la actualización PODRÍA aplicarse: runtimes exigidos
     * presentes, permiso de escritura y archivos no bloqueados. Pensado para
     * correr ANTES de cerrar la aplicación, así un problema se informa sin
     * que el usuario pierda su sesión.
     *
     * @param ignored archivos que se sabe que están en uso por quien consulta (el propio jar de la GUI)
     */
    void preflight(Set<String> ignored) throws UpdateException {
        checkRuntimes();
        checkWritable();
        List<String> locked = new ArrayList<>();
        for (String entry : pkg.entries()) {
            if (ignored.contains(entry)) {
                continue;
            }
            Path target = installDir.resolve(entry);
            if (Files.exists(target) && isLocked(target)) {
                locked.add(entry);
            }
        }
        if (!locked.isEmpty()) {
            throw inUse(locked);
        }
    }

    private void checkRuntimes() throws UpdateException {
        for (String key : List.of("requires.java", "requires.javafx")) {
            String folder = pkg.requiredFolder(key);
            if (folder != null && !Files.isDirectory(installDir.resolve(folder))) {
                throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                        "La versión " + pkg.version() + " necesita " + folder + ", que esta instalación no tiene. "
                                + "Esta actualización automática no incluye ese componente: descargue la "
                                + "distribución completa de S-FiDE " + pkg.version() + " desde GitHub.");
            }
        }
    }

    private void checkWritable() throws UpdateException {
        Path probe = installDir.resolve(".sfide-prueba-escritura-" + ProcessHandle.current().pid());
        try {
            Files.writeString(probe, "x");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            throw new UpdateException(UpdateException.Kind.NO_PERMISSION,
                    "No hay permiso para escribir en la carpeta de instalación de S-FiDE (" + installDir + "). "
                            + "Ejecute S-FiDE como administrador (o con un usuario que pueda modificar esa carpeta) "
                            + "para actualizarlo.", e);
        }
    }

    /** {@code true} si el archivo está abierto por otro proceso de forma que no se lo puede reemplazar. */
    private static boolean isLocked(Path file) {
        // La prueba fiel es la misma operación que hará la actualización: mover el archivo de
        // lugar. En Windows eso falla si otro proceso lo tiene abierto (un jar cargado por la JVM
        // de otro usuario) aunque se pueda abrir para escribir — abrirlo no alcanza como prueba.
        // Se renombra a un nombre temporal y se devuelve enseguida; en Linux/macOS un archivo en
        // uso sí se puede reemplazar, y la prueba da "no bloqueado", que es lo correcto.
        Path probe = file.resolveSibling(file.getFileName() + ".sfide-prueba");
        try {
            Files.move(file, probe);
            Files.move(probe, file);
            return false;
        } catch (IOException e) {
            try {
                if (Files.exists(probe) && !Files.exists(file)) {
                    Files.move(probe, file); // no dejar el archivo con el nombre de prueba
                }
            } catch (IOException ignored) {
                // Nada más que hacer: se informa como en uso.
            }
            return true;
        }
    }

    private static UpdateException inUse(List<String> files) {
        return new UpdateException(UpdateException.Kind.FILES_IN_USE,
                "Hay archivos de S-FiDE en uso (" + String.join(", ", files.subList(0, Math.min(3, files.size())))
                        + (files.size() > 3 ? " y " + (files.size() - 3) + " más" : "") + "). "
                        + "Probablemente otro usuario de este equipo tiene S-FiDE abierto desde la misma carpeta. "
                        + "Pídale que lo cierre y vuelva a intentar la actualización.");
    }

    // ------------------------------------------------------------------
    // Aplicación
    // ------------------------------------------------------------------

    /**
     * Aplica la actualización completa. Si algo falla, la instalación queda
     * como estaba y se lanza {@link UpdateException}.
     */
    void apply() throws UpdateException {
        checkRuntimes();
        checkWritable();
        acquireMarker();
        Path work = installDir.resolve(WORK_PREFIX + pkg.version());
        Path backup = installDir.resolve(BACKUP_PREFIX + System.currentTimeMillis());
        List<String> applied = new ArrayList<>();
        boolean success = false;
        try {
            deleteTree(work);
            log.accept("Extrayendo y verificando los archivos de la versión " + pkg.version() + "...");
            extract(work);

            log.accept("Reemplazando archivos (con respaldo para poder revertir)...");
            replaceAll(work, backup, applied);
            success = true;
        } catch (UpdateException e) {
            log.accept("No se pudo completar la actualización: " + e.getMessage());
            rollback(backup, applied);
            throw e;
        } catch (IOException | RuntimeException e) {
            log.accept("Error inesperado durante la actualización: " + e);
            rollback(backup, applied);
            throw new UpdateException(UpdateException.Kind.OTHER,
                    "La actualización falló y se revirtió; S-FiDE quedó como estaba. Detalle: " + e.getMessage(), e);
        } finally {
            deleteTree(work);
            if (success) {
                deleteTree(backup);
            }
            releaseMarker();
        }
    }

    private void extract(Path work) throws UpdateException, IOException {
        try (ZipFile zip = new ZipFile(pkg.zipPath().toFile())) {
            for (String entryName : pkg.entries()) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                            "El paquete de actualización está incompleto (falta " + entryName + ").");
                }
                Path out = work.resolve(entryName).normalize();
                if (!out.startsWith(work)) {
                    throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                            "El paquete de actualización contiene una ruta no válida (" + entryName + ").");
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry); OutputStream os = Files.newOutputStream(out)) {
                    in.transferTo(os);
                }
                String actual = UpdatePackage.sha256(out);
                if (!actual.equals(pkg.expectedSha256(entryName))) {
                    throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                            "El paquete de actualización está dañado: el archivo " + entryName
                                    + " no coincide con su verificación (SHA-256).");
                }
            }
        }
    }

    private void replaceAll(Path work, Path backup, List<String> applied) throws UpdateException, IOException {
        for (String entry : pkg.entries()) {
            Path staged = work.resolve(entry);
            Path target = installDir.resolve(entry);

            if (Files.isRegularFile(target) && UpdatePackage.sha256(target).equals(pkg.expectedSha256(entry))) {
                continue; // ya es idéntico: no se toca
            }

            long deadline = System.nanoTime() + lockRetryWindow.toNanos();
            while (true) {
                try {
                    replaceOne(entry, staged, target, backup);
                    applied.add(entry);
                    break;
                } catch (IOException e) {
                    if (System.nanoTime() >= deadline) {
                        throw inUse(List.of(entry));
                    }
                    log.accept("El archivo " + entry + " está en uso; se reintenta...");
                    sleep(lockRetryInterval);
                }
            }
        }
    }

    private void replaceOne(String entry, Path staged, Path target, Path backup) throws IOException {
        Files.createDirectories(target.getParent());
        Path backupFile = backup.resolve(entry);
        boolean hadOriginal = Files.exists(target);
        if (hadOriginal) {
            Files.createDirectories(backupFile.getParent());
            Files.move(target, backupFile, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (hadOriginal) {
                Files.move(backupFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            throw e;
        }
    }

    private void rollback(Path backup, List<String> applied) {
        if (applied.isEmpty()) {
            return;
        }
        log.accept("Revirtiendo " + applied.size() + " archivo(s) al estado anterior...");
        boolean allRestored = true;
        for (int i = applied.size() - 1; i >= 0; i--) {
            String entry = applied.get(i);
            Path target = installDir.resolve(entry);
            Path backupFile = backup.resolve(entry);
            try {
                if (Files.exists(backupFile)) {
                    Files.move(backupFile, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(target); // era un archivo nuevo: no existía antes
                }
            } catch (IOException e) {
                allRestored = false;
                log.accept("No se pudo revertir " + entry + ": " + e.getMessage());
            }
        }
        if (allRestored) {
            deleteTree(backup);
        } else {
            log.accept("Los archivos originales que no se pudieron devolver quedaron en: " + backup);
        }
    }

    // ------------------------------------------------------------------
    // Marca de "actualización en curso"
    // ------------------------------------------------------------------

    private void acquireMarker() throws UpdateException {
        Path marker = installDir.resolve(BUSY_MARKER);
        try {
            if (Files.exists(marker) && isStale(marker)) {
                Files.deleteIfExists(marker); // quedó de una caída: no puede trabar para siempre
            }
            Files.writeString(marker, "pid=" + ProcessHandle.current().pid() + "\nversion=" + pkg.version() + "\n",
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new UpdateException(UpdateException.Kind.BUSY,
                    "Ya hay otra actualización de S-FiDE en curso en esta carpeta. Espere a que termine e intente de nuevo.");
        } catch (IOException e) {
            throw new UpdateException(UpdateException.Kind.NO_PERMISSION,
                    "No se pudo iniciar la actualización en " + installDir + ": " + e.getMessage(), e);
        }
    }

    private void releaseMarker() {
        try {
            Files.deleteIfExists(installDir.resolve(BUSY_MARKER));
        } catch (IOException e) {
            log.accept("No se pudo borrar la marca de actualización: " + e.getMessage());
        }
    }

    /** {@code true} si la marca es vieja: una actualización real dura segundos, no quince minutos. */
    static boolean isStale(Path marker) {
        try {
            return Duration.between(Files.getLastModifiedTime(marker).toInstant(), Instant.now())
                    .compareTo(MARKER_STALE_AFTER) > 0;
        } catch (IOException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (NoSuchFileException | DirectoryNotEmptyException ignored) {
                    // se limpia lo que se pueda
                } catch (IOException ignored) {
                    // idem
                }
            });
        } catch (IOException ignored) {
            // idem
        }
    }
}
