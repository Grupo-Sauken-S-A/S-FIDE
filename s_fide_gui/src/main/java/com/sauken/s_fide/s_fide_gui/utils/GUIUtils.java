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
import java.util.concurrent.ThreadFactory;

public final class GUIUtils {
    private static final ExecutorService executorService;
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String JAVA_EXECUTABLE = JAVA_HOME + File.separator + "bin" + File.separator + "java";

    static {
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        };
        executorService = Executors.newCachedThreadPool(threadFactory);
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
        if (jarName == null || args == null || outputTextArea == null) {
            throw new IllegalArgumentException("Parámetros no válidos para la ejecución del comando");
        }

        CompletableFuture.runAsync(() -> {
            Path jarPath = Paths.get(System.getProperty("user.dir"), jarName + ".jar");

            if (!jarPath.toFile().exists()) {
                Platform.runLater(() -> {
                    outputTextArea.appendText("Error: No se encuentra el archivo " + jarPath + "\n");
                    showCommandResult(1);
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

            try {
                Process process = processBuilder.start();
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

                Platform.runLater(() -> {
                    if (output.isEmpty()) {
                        outputTextArea.appendText("El proceso no generó salida\n");
                    }
                    showCommandResult(exitStatus);
                });

            } catch (Exception e) {
                final String errorMsg = "Error ejecutando " + jarPath + ": " + e.getMessage() + "\n";
                Platform.runLater(() -> {
                    outputTextArea.appendText(errorMsg);
                    showCommandResult(1);
                });
            }
        }, executorService);
    }

    public static void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}