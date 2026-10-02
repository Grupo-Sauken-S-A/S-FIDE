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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Aplica una actualización de S-FiDE ya descargada y verificada por la
 * interfaz gráfica: reemplaza los archivos de la carpeta de instalación con
 * respaldo y reversión automática (ver {@link UpdateApplier}).
 * <p>
 * Es un módulo más del proyecto, con el mismo contrato de línea de comandos:
 * {@code java -jar SFideUpdater.jar <argumentos>}; código de salida 0 = éxito,
 * 1 = error; el resultado va a stdout y los errores a stderr, en español y
 * sin trazas de Java.
 * <p>
 * Dos modos:
 * <ul>
 *   <li>{@code verificar}: valida el paquete y comprueba que la actualización
 *   podría aplicarse (permisos, archivos en uso), SIN modificar nada. La GUI lo
 *   corre antes de cerrarse.</li>
 *   <li>{@code aplicar}: espera a que termine la GUI, aplica y, opcionalmente, vuelve a
 *   abrir S-FiDE. Escribe el resultado en un archivo para que la GUI lo muestre
 *   al arrancar.</li>
 * </ul>
 */
public final class SFideUpdater {
    private static final String VERSION = "S-FIDE SFideUpdater v" + resolveVersion() + " - Grupo Sauken S.A.";

    /** Margen tras la salida de la GUI: el .bat/.sh que la lanzó todavía puede estar leyendo sus últimas líneas. */
    private static final Duration LAUNCHER_GRACE = Duration.ofSeconds(4);
    private static final Duration WAIT_FOR_GUI_MAX = Duration.ofSeconds(120);
    private static final Duration LOCK_RETRY_WINDOW = Duration.ofSeconds(30);
    private static final Duration LOCK_RETRY_INTERVAL = Duration.ofSeconds(2);

    private static PrintStream out;
    private static PrintStream err;
    private static Path logFile;

    private SFideUpdater() {
    }

    private static String resolveVersion() {
        String v = SFideUpdater.class.getPackage() != null
                ? SFideUpdater.class.getPackage().getImplementationVersion() : null;
        return v != null ? v : "1.4.0";
    }

    public static void main(String[] args) {
        out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int code;
        try {
            code = run(args);
        } catch (UpdateException e) {
            err.println(e.getMessage());
            code = 1;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            code = 1;
        } catch (Exception e) {
            err.println("Error inesperado: " + e.getMessage());
            code = 1;
        }
        System.exit(code);
    }

    static int run(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            out.println(readResource("/HELP.txt"));
            throw new IllegalArgumentException("No se proporcionaron argumentos.");
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "-version", "-v", "--version" -> out.println(VERSION);
            case "-licencia", "--license" -> out.println(readResource("/LICENSE.txt"));
            case "-ayuda", "-h", "--help" -> out.println(readResource("/HELP.txt"));
            case "verificar" -> verify(args);
            case "aplicar" -> apply(args);
            default -> throw new IllegalArgumentException("Argumento no reconocido: " + args[0]);
        }
        return 0;
    }

    // ------------------------------------------------------------------

    private static void verify(String[] args) throws UpdateException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Uso: verificar <paquete.zip> <carpeta de instalación> [--ignorar <archivo>]...");
        }
        Path zip = Paths.get(args[1]);
        Path installDir = Paths.get(args[2]);
        Set<String> ignored = new HashSet<>();
        for (int i = 3; i + 1 < args.length; i += 2) {
            if ("--ignorar".equals(args[i])) {
                ignored.add(args[i + 1]);
            } else {
                throw new IllegalArgumentException("Argumento no reconocido: " + args[i]);
            }
        }
        requireInstallDir(installDir);
        UpdatePackage pkg = UpdatePackage.open(zip);
        new UpdateApplier(installDir, pkg, s -> { }, Duration.ZERO, Duration.ZERO).preflight(ignored);
        out.println("La actualización a la versión " + pkg.version() + " puede aplicarse (" + pkg.entries().size() + " archivos).");
    }

    private static void apply(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Uso: aplicar <paquete.zip> <carpeta de instalación> "
                    + "[--esperar-pid <n>] [--relanzar] [--resultado <archivo>] [--log <archivo>]");
        }
        Path zip = Paths.get(args[1]);
        Path installDir = Paths.get(args[2]).toAbsolutePath().normalize();
        long waitPid = -1;
        boolean relaunch = false;
        Path resultFile = null;
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--esperar-pid" -> waitPid = Long.parseLong(requireValue(args, ++i));
                case "--relanzar" -> relaunch = true;
                case "--resultado" -> resultFile = Paths.get(requireValue(args, ++i));
                case "--log" -> logFile = Paths.get(requireValue(args, ++i));
                default -> throw new IllegalArgumentException("Argumento no reconocido: " + args[i]);
            }
        }
        requireInstallDir(installDir);

        String status = "error";
        String version = "";
        String message;
        UpdatePackage pkg = null;
        try {
            pkg = UpdatePackage.open(zip);
            version = pkg.version();
            if (waitPid > 0) {
                log("Esperando a que S-FiDE se cierre (proceso " + waitPid + ")...");
                waitForExit(waitPid);
                // El lanzador (.bat/.sh) que abrió la GUI puede seguir leyendo sus últimas líneas
                // un instante después de que Java termina: se espera antes de tocar los lanzadores.
                sleep(LAUNCHER_GRACE);
            }
            new UpdateApplier(installDir, pkg, SFideUpdater::log, LOCK_RETRY_WINDOW, LOCK_RETRY_INTERVAL).apply();
            status = "ok";
            message = "S-FiDE se actualizó correctamente a la versión " + pkg.version() + ".";
            log(message);
        } catch (UpdateException e) {
            status = e.kind() == UpdateException.Kind.FILES_IN_USE ? "revertido-en-uso" : "revertido";
            message = e.getMessage() + " No se hizo ningún cambio: S-FiDE sigue en su versión anterior.";
            if (e.kind() == UpdateException.Kind.INVALID_PACKAGE || e.kind() == UpdateException.Kind.NO_PERMISSION
                    || e.kind() == UpdateException.Kind.BUSY) {
                message = e.getMessage();
            }
            log("ERROR: " + message);
        }

        writeResult(resultFile, status, version, message);
        if (relaunch) {
            relaunch(installDir, pkg);
        }
        if (!"ok".equals(status)) {
            throw new UpdateException(UpdateException.Kind.OTHER, message);
        }
        // El paquete descargado ya cumplió su función.
        try {
            Files.deleteIfExists(zip);
        } catch (IOException ignored) {
            // Se limpia la próxima vez que arranque la GUI.
        }
    }

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Falta el valor de " + args[index - 1]);
        }
        return args[index];
    }

    private static void requireInstallDir(Path installDir) {
        if (!Files.isDirectory(installDir)) {
            throw new IllegalArgumentException("La carpeta de instalación no existe: " + installDir);
        }
    }

    // ------------------------------------------------------------------

    private static void waitForExit(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return;
        }
        long deadline = System.nanoTime() + WAIT_FOR_GUI_MAX.toNanos();
        while (handle.get().isAlive() && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(300));
        }
    }

    private static void writeResult(Path resultFile, String status, String version, String message) {
        if (resultFile == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("status", status);
        p.setProperty("version", version);
        p.setProperty("message", message);
        try {
            Files.createDirectories(resultFile.toAbsolutePath().getParent());
            try (var w = Files.newBufferedWriter(resultFile, StandardCharsets.UTF_8)) {
                p.store(w, "Resultado de la ultima actualizacion de S-FiDE");
            }
        } catch (IOException e) {
            log("No se pudo escribir el resultado: " + e.getMessage());
        }
    }

    /**
     * Vuelve a abrir S-FiDE, desacoplado de este proceso. Se hace tanto si la
     * actualización salió bien como si se revirtió: en ambos casos el usuario
     * tenía S-FiDE abierto y espera encontrarlo.
     * <p>
     * Se prefiere lanzar Java directamente con el runtime embebido (los
     * nombres de carpeta los dice el propio paquete): así no queda abierta una
     * ventana de consola del lanzador {@code .bat}. Si eso no es posible, se usa
     * el lanzador.
     */
    private static void relaunch(Path installDir, UpdatePackage pkg) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean windows = os.contains("win");
            boolean mac = os.contains("mac");
            ProcessBuilder pb = directLaunch(installDir, pkg, windows, mac);
            if (pb == null) {
                Path bat = installDir.resolve("SFide-GUI.bat");
                Path sh = installDir.resolve("SFide-GUI.sh");
                if (windows && Files.exists(bat)) {
                    pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", bat.toString());
                } else if (Files.exists(sh)) {
                    pb = new ProcessBuilder("sh", sh.toString());
                } else {
                    log("No se encontró el lanzador de S-FiDE; ábralo manualmente.");
                    return;
                }
            }
            pb.directory(installDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            log("S-FiDE se volvió a abrir.");
        } catch (IOException e) {
            log("No se pudo volver a abrir S-FiDE automáticamente: " + e.getMessage());
        }
    }

    /** El mismo comando que arma SFide-GUI.bat/.sh, o {@code null} si no se pueden ubicar los runtimes. */
    private static ProcessBuilder directLaunch(Path installDir, UpdatePackage pkg, boolean windows, boolean mac) {
        if (pkg == null) {
            return null;
        }
        String jdkFolder = pkg.requiredFolder("requires.java");
        String fxFolder = pkg.requiredFolder("requires.javafx");
        Path gui = installDir.resolve("SFide-GUI.jar");
        if (jdkFolder == null || fxFolder == null || !Files.exists(gui)) {
            return null;
        }
        String platform = windows ? "windows-x64" : (mac ? "macos" : "linux-x64");
        // javaw.exe en Windows: no abre una ventana de consola.
        Path java = installDir.resolve(jdkFolder).resolve(platform).resolve("bin").resolve(windows ? "javaw.exe" : "java");
        Path fxLib = installDir.resolve(fxFolder).resolve(platform).resolve("lib");
        if (!Files.exists(java) || !Files.isDirectory(fxLib)) {
            return null;
        }
        return new ProcessBuilder(java.toString(), "--module-path", fxLib.toString(),
                "--add-modules", "javafx.controls,javafx.fxml",
                "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", gui.toString());
    }

    // ------------------------------------------------------------------

    private static void log(String line) {
        out.println(line);
        if (logFile != null) {
            try {
                Files.createDirectories(logFile.toAbsolutePath().getParent());
                Files.writeString(logFile, java.time.LocalDateTime.now().withNano(0) + " " + line + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // El registro es una ayuda: no debe impedir actualizar.
            }
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = SFideUpdater.class.getResourceAsStream(name)) {
            if (in == null) {
                return "Archivo de recurso no encontrado: " + name;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
