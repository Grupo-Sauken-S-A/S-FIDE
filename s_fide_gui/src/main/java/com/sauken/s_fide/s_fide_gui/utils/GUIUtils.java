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

  Autores: Juan Carlos Ríos y Juan Ignacio Ríos con la asistencia de Claude AI 3.5 Sonnet
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

  Authors: Juan Carlos Ríos y Juan Ignacio Ríos with support of Claude AI 3.5 Sonnet
  E-mail: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Company: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

 */

package com.sauken.s_fide.s_fide_gui.utils;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public final class GUIUtils {
    // Generoso a propósito: XMLSignerWindowsCSP/PDFSignerWindowsCSP pueden quedar
    // esperando un diálogo nativo de PIN/token (interacción real del usuario), y
    // la verificación de revocación hace consultas OCSP/CRL por red. Solo debe
    // dispararse ante un colgado genuino, no ante una operación lenta pero normal.
    private static final int EXECUTION_TIMEOUT_MINUTES = 10;

    private static final ExecutorService executorService;
    private static final ScheduledExecutorService watchdogExecutor;
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String JAVA_EXECUTABLE = JAVA_HOME + File.separator + "bin" + File.separator + "java";

    static {
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        };
        executorService = Executors.newCachedThreadPool(threadFactory);
        watchdogExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    private GUIUtils() {
        // Constructor privado para evitar instanciación
    }

    public static void showError(String titulo, String mensaje) {
        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, titulo, mensaje));
    }

    public static void showCommandResult(int exitStatus) {
        String mensaje = exitStatus == 0
                ? "Proceso finalizado correctamente"
                : "El proceso finalizó con errores";

        Platform.runLater(() ->
                showAlert(
                        exitStatus == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                        "Estado del Proceso",
                        mensaje
                )
        );
    }

    private static void showAlert(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    public static String loadResourceFile(String rutaRecurso) {
        if (rutaRecurso == null || rutaRecurso.trim().isEmpty()) {
            return "Ruta de recurso no válida";
        }

        try (InputStream is = GUIUtils.class.getResourceAsStream(rutaRecurso)) {
            if (is == null) {
                return "No se pudo encontrar el recurso: " + rutaRecurso;
            }
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            return "Error al cargar el recurso: " + e.getMessage();
        }
    }

    public static void executeCommand(String jarName, String[] args, TextArea outputTextArea) {
        executeCommand(jarName, args, outputTextArea, null);
    }

    /**
     * Igual que {@link #executeCommand(String, String[], TextArea)}, pero
     * además notifica el código de salida real una vez terminado (0 =
     * éxito), en el hilo de la interfaz — usado por las pestañas de firma
     * para habilitar el botón "Abrir documento generado" solo ante un éxito
     * real, nunca ante un timeout, un error de arranque, o una validación de
     * revocación fallida.
     */
    public static void executeCommand(String jarName, String[] args, TextArea outputTextArea, IntConsumer onExit) {
        if (jarName == null || args == null || outputTextArea == null) {
            throw new IllegalArgumentException("Parámetros no válidos para la ejecución del comando");
        }

        CompletableFuture.runAsync(() -> {
            Path jarPath = Paths.get(System.getProperty("user.dir"), jarName + ".jar");

            if (!jarPath.toFile().exists()) {
                Platform.runLater(() -> {
                    outputTextArea.appendText("Error: No se encuentra el archivo " + jarPath + "\n");
                    showCommandResult(1);
                    if (onExit != null) onExit.accept(1);
                });
                return;
            }

            List<String> command = new ArrayList<>();
            command.add(JAVA_EXECUTABLE);
            command.add("-Dfile.encoding=UTF-8");
            command.add("-Dsun.jnu.encoding=UTF-8");
            command.add("-Dconsole.encoding=UTF-8");
            command.add("-jar");
            command.add(jarPath.toString());
            command.addAll(Arrays.asList(args));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put("LANG", "es_ES.UTF-8");
            processBuilder.environment().put("LC_ALL", "es_ES.UTF-8");

            Process process = null;
            ScheduledFuture<?> watchdog = null;
            try {
                process = processBuilder.start();
                final Process finalProcess = process;
                AtomicBoolean timedOut = new AtomicBoolean(false);

                watchdog = watchdogExecutor.schedule(() -> {
                    if (finalProcess.isAlive()) {
                        timedOut.set(true);
                        finalProcess.destroyForcibly();
                    }
                }, EXECUTION_TIMEOUT_MINUTES, TimeUnit.MINUTES);

                StringBuilder output = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String finalLine = line;
                        Platform.runLater(() -> outputTextArea.appendText(finalLine + "\n"));
                        output.append(line).append("\n");
                    }
                }

                int exitStatus = process.waitFor();
                watchdog.cancel(false);

                if (timedOut.get()) {
                    Platform.runLater(() -> {
                        outputTextArea.appendText("Error: la operación fue cancelada por superar el tiempo "
                                + "máximo de espera (" + EXECUTION_TIMEOUT_MINUTES + " minutos)\n");
                        showCommandResult(1);
                        if (onExit != null) onExit.accept(1);
                    });
                } else {
                    Platform.runLater(() -> {
                        if (output.isEmpty()) {
                            outputTextArea.appendText("El proceso no generó salida\n");
                        }
                        showCommandResult(exitStatus);
                        if (onExit != null) onExit.accept(exitStatus);
                    });
                }

            } catch (Exception e) {
                if (watchdog != null) {
                    watchdog.cancel(false);
                }
                final String errorMsg = "Error ejecutando " + jarPath + ": " + e.getMessage() + "\n";
                Platform.runLater(() -> {
                    outputTextArea.appendText(errorMsg);
                    showCommandResult(1);
                    if (onExit != null) onExit.accept(1);
                });
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }, executorService);
    }

    private record RevocationCheckOutcome(String estado, String detalle, String rawOutput, boolean ranOk) {}

    /**
     * Firma un documento, pero primero consulta el estado de revocación del
     * certificado (invocando el mismo jar con "-verificar-revocacion" y los
     * argumentos de credencial en {@code checkArgs}, sin tocar ningún
     * documento) y decide qué hacer según el resultado antes de disparar la
     * firma real ({@code signArgs}):
     * <p>
     * - GOOD: firma directo, sin interrumpir al usuario.
     * - REVOKED: nunca firma — el certificado está confirmado revocado, se
     * informa y se corta ahí. Coherente con que la GUI siempre interactúa
     * con una persona en tiempo real: no tiene sentido dejarla firmar con
     * un certificado que ya sabemos revocado.
     * - UNKNOWN (sin Internet, sin URL de OCSP/CRL, o falló la consulta):
     * pide confirmación explícita antes de firmar — nunca firma en
     * silencio cuando no se pudo confirmar el estado real.
     * <p>
     * Nota para módulos PKCS#11 (tokens físicos): este método hace un
     * segundo ingreso de PIN/contraseña independiente del que hace la firma
     * real (dos procesos separados, no se puede compartir una sesión
     * PKCS#11 entre ellos) — por eso NO se usa para
     * XMLSignerPKCS11/PDFSignerPKCS11 en esta GUI, solo para los módulos
     * PKCS#12 y Windows CSP/KSP, donde un segundo intento no arriesga
     * inhabilitar ningún token físico.
     */
    public static void executeSignCommandWithRevocationCheck(
            String jarName, String[] checkArgs, String[] signArgs, TextArea outputTextArea) {
        executeSignCommandWithRevocationCheck(jarName, checkArgs, signArgs, outputTextArea, null);
    }

    /**
     * Igual que {@link #executeSignCommandWithRevocationCheck(String, String[], String[], TextArea)},
     * pero además notifica el código de salida real una vez terminado (0 =
     * éxito) — ver {@link #executeCommand(String, String[], TextArea, IntConsumer)}.
     * Se notifica en los cuatro desenlaces posibles: verificación de
     * revocación fallida, certificado revocado, revocación cancelada por el
     * usuario, y el resultado real de la firma (GOOD o "UNKNOWN" con
     * confirmación aceptada).
     */
    public static void executeSignCommandWithRevocationCheck(
            String jarName, String[] checkArgs, String[] signArgs, TextArea outputTextArea, IntConsumer onExit) {
        if (jarName == null || checkArgs == null || signArgs == null || outputTextArea == null) {
            throw new IllegalArgumentException("Parámetros no válidos para la ejecución del comando");
        }

        CompletableFuture.runAsync(() -> {
            RevocationCheckOutcome outcome = runRevocationCheck(jarName, checkArgs);

            if (!outcome.ranOk()) {
                Platform.runLater(() -> {
                    outputTextArea.clear();
                    outputTextArea.appendText(outcome.rawOutput() + "\n");
                    showCommandResult(1);
                    if (onExit != null) onExit.accept(1);
                });
                return;
            }

            switch (outcome.estado()) {
                case "REVOKED" -> Platform.runLater(() -> {
                    outputTextArea.clear();
                    outputTextArea.appendText("El certificado de firma está revocado. No se firmó el documento.\n");
                    showAlert(Alert.AlertType.ERROR, "Certificado revocado",
                            "El certificado de firma está revocado. No se puede firmar desde la interfaz gráfica.");
                    if (onExit != null) onExit.accept(1);
                });
                case "UNKNOWN" -> Platform.runLater(() -> {
                    String detalle = outcome.detalle();
                    boolean continuar = confirmarSiNo(
                            "Revocación no verificable",
                            "No se pudo confirmar que el certificado no esté revocado"
                                    + (detalle != null ? " (" + detalle + ")" : "") + ".\n\n"
                                    + "¿Desea firmar de todas formas?");
                    if (continuar) {
                        executeCommand(jarName, signArgs, outputTextArea, onExit);
                    } else {
                        outputTextArea.clear();
                        outputTextArea.appendText("Firma cancelada: no se pudo confirmar el estado de revocación "
                                + "del certificado.\n");
                        if (onExit != null) onExit.accept(1);
                    }
                });
                default -> executeCommand(jarName, signArgs, outputTextArea, onExit);
            }
        }, executorService);
    }

    private static boolean confirmarSiNo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        return alert.showAndWait().filter(boton -> boton == ButtonType.OK).isPresent();
    }

    private static RevocationCheckOutcome runRevocationCheck(String jarName, String[] checkArgs) {
        Path jarPath = Paths.get(System.getProperty("user.dir"), jarName + ".jar");
        if (!jarPath.toFile().exists()) {
            return new RevocationCheckOutcome(null, null, "Error: No se encuentra el archivo " + jarPath, false);
        }

        List<String> command = new ArrayList<>();
        command.add(JAVA_EXECUTABLE);
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dsun.jnu.encoding=UTF-8");
        command.add("-Dconsole.encoding=UTF-8");
        command.add("-jar");
        command.add(jarPath.toString());
        command.addAll(Arrays.asList(checkArgs));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("LANG", "es_ES.UTF-8");
        processBuilder.environment().put("LC_ALL", "es_ES.UTF-8");

        StringBuilder output = new StringBuilder();
        String[] estado = new String[1];
        String[] detalle = new String[1];

        try {
            Process process = processBuilder.start();

            // Se drena en un hilo aparte, EN SIMULTÁNEO con la espera — nunca
            // recién después: si la salida supera el buffer del pipe del SO
            // y nadie la lee mientras tanto, el proceso hijo queda bloqueado
            // escribiendo y este método queda bloqueado en waitFor().
            Thread lector = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (line.startsWith("ESTADO_REVOCACION: ")) {
                            estado[0] = line.substring("ESTADO_REVOCACION: ".length()).trim();
                        } else if (line.startsWith("DETALLE: ")) {
                            detalle[0] = line.substring("DETALLE: ".length()).trim();
                        }
                    }
                } catch (IOException ignored) {
                    // Pasa si el proceso se destruye a la fuerza por timeout más abajo.
                }
            }, "lector-verificacion-revocacion");
            lector.setDaemon(true);
            lector.start();

            boolean terminoATiempo = process.waitFor(1, TimeUnit.MINUTES);
            if (!terminoATiempo) {
                process.destroyForcibly();
                lector.join(2000);
                return new RevocationCheckOutcome(null, null,
                        "Error: la verificación de revocación no respondió a tiempo", false);
            }

            lector.join(5000);
            int exitStatus = process.exitValue();

            if (exitStatus != 0 || estado[0] == null) {
                return new RevocationCheckOutcome(null, null, output.toString(), false);
            }

            return new RevocationCheckOutcome(estado[0], detalle[0], output.toString(), true);
        } catch (Exception e) {
            return new RevocationCheckOutcome(null, null,
                    "Error verificando revocación de " + jarName + ": " + e.getMessage(), false);
        }
    }

    public static void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (!watchdogExecutor.isShutdown()) {
            watchdogExecutor.shutdownNow();
        }
    }
}