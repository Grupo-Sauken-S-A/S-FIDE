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

package com.sauken.s_fide.s_fide_gui.update;

import com.sauken.s_fide.s_fide_gui.update.UpdateService.Asset;
import com.sauken.s_fide.s_fide_gui.update.UpdateService.ReleaseInfo;
import com.sauken.s_fide.s_fide_gui.update.UpdateService.UpdateCheckException;
import com.sauken.s_fide.s_fide_gui.update.UpdateService.UpdaterRun;
import com.sauken.s_fide.s_fide_gui.utils.AppInfo;
import com.sauken.s_fide.s_fide_gui.utils.UserDataDirectory;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.swing.JOptionPane;

/**
 * Pantallas y flujo de "Ayuda → Buscar actualizaciones": consultar GitHub,
 * pedir permiso al usuario, descargar y verificar, comprobar que la
 * instalación se puede actualizar y recién entonces cerrar S-FiDE y dejar
 * que {@code SFideUpdater.jar} reemplace los archivos.
 * <p>
 * Principio rector: el usuario siempre decide (nada se descarga ni instala
 * sin su confirmación) y nunca pierde la aplicación por una actualización que
 * no se puede aplicar — todo lo que puede fallar de forma previsible
 * (sin Internet, descarga dañada, sin permiso de escritura, archivos en uso
 * por otro usuario) se detecta ANTES de cerrar S-FiDE.
 */
public final class UpdateController {
    private static final String BUSY_MARKER = ".sfide-actualizando";
    private static final Duration BUSY_MARKER_STALE_AFTER = Duration.ofMinutes(15);

    private final Stage owner;
    private final ExecutorService executor;
    private final Consumer<String> openInBrowser;
    private final Runnable exitForUpdate;
    private final UpdateService service = new UpdateService();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * @param exitForUpdate cierra la aplicación sin pedir confirmación (el usuario ya la dio) y
     *                      guardando lo pendiente
     */
    public UpdateController(Stage owner, ExecutorService executor, Consumer<String> openInBrowser, Runnable exitForUpdate) {
        this.owner = owner;
        this.executor = executor;
        this.openInBrowser = openInBrowser;
        this.exitForUpdate = exitForUpdate;
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    /** Acción del menú: consulta GitHub y guía al usuario. Se invoca en el hilo de la interfaz. */
    public void checkInteractively() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        // "completing" distingue el cierre del diálogo por parte del programa (llegó la respuesta)
        // del cierre por parte del usuario (Cancelar o la X): en ese caso se descarta el resultado.
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean completing = new AtomicBoolean(false);
        Alert wait = new Alert(Alert.AlertType.NONE, "Consultando la última versión publicada en GitHub...", ButtonType.CANCEL);
        wait.initOwner(owner);
        wait.setTitle("Buscar actualizaciones");
        wait.setHeaderText(null);
        wait.setOnHidden(e -> {
            if (!completing.get()) {
                cancelled.set(true);
                busy.set(false);
            }
        });
        wait.show();

        executor.submit(() -> {
            try {
                ReleaseInfo release = service.fetchLatest();
                Platform.runLater(() -> {
                    completing.set(true);
                    wait.close();
                    if (!cancelled.get()) {
                        present(release);
                    }
                });
            } catch (UpdateCheckException e) {
                Platform.runLater(() -> {
                    completing.set(true);
                    wait.close();
                    if (!cancelled.get()) {
                        busy.set(false);
                        showCheckError(e.getMessage());
                    }
                });
            }
        });
    }

    private void showCheckError(String message) {
        ButtonType page = new ButtonType("Abrir la página de descargas", ButtonBar.ButtonData.OTHER);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, page, ButtonType.CLOSE);
        alert.initOwner(owner);
        alert.setTitle("Buscar actualizaciones");
        alert.setHeaderText("No se pudo consultar si hay una versión nueva");
        if (alert.showAndWait().filter(b -> b == page).isPresent()) {
            openInBrowser.accept(UpdateService.RELEASES_PAGE);
        }
    }

    private void present(ReleaseInfo release) {
        if (!UpdateService.isNewerThanInstalled(release)) {
            busy.set(false);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("Buscar actualizaciones");
            alert.setHeaderText("Su S-FiDE está al día");
            alert.setContentText("Versión instalada: " + AppInfo.version()
                    + "\nÚltima versión publicada: " + release.versionText());
            alert.showAndWait();
            return;
        }

        if (!release.hasUpdatePackage()) {
            busy.set(false);
            ButtonType page = new ButtonType("Abrir la página de descargas", ButtonBar.ButtonData.OTHER);
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Hay una versión nueva de S-FiDE: " + release.versionText() + " (usted tiene la " + AppInfo.version()
                            + ").\n\nEsta publicación no incluye un paquete para actualizar automáticamente, "
                            + "así que hay que descargar la distribución completa desde GitHub e instalarla a mano.",
                    page, ButtonType.CLOSE);
            alert.initOwner(owner);
            alert.setTitle("Buscar actualizaciones");
            alert.setHeaderText("Hay una versión nueva disponible");
            if (alert.showAndWait().filter(b -> b == page).isPresent()) {
                openInBrowser.accept(release.pageUrl());
            }
            return;
        }

        askToInstall(release);
    }

    /** Pide el permiso del usuario, mostrando qué va a pasar y las novedades de la versión. */
    private void askToInstall(ReleaseInfo release) {
        ButtonType install = new ButtonType("Instalar actualización", ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType("Ahora no", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType notes = new ButtonType("Ver novedades en GitHub", ButtonBar.ButtonData.OTHER);

        Path installDir = UpdateService.installDir();
        String size = release.updatePackage().size() > 0 ? " (descarga de " + megabytes(release.updatePackage().size()) + ")" : "";
        Label summary = new Label("Versión disponible: " + release.versionText() + size
                + "\nVersión instalada: " + AppInfo.version());
        summary.setWrapText(true);

        TextArea notesArea = new TextArea(shorten(release.notes(), 4000));
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(8);
        notesArea.setPrefWidth(560);

        Label what = new Label("Si acepta, S-FiDE descargará la actualización, comprobará su integridad, se cerrará, "
                + "reemplazará sus archivos en la carpeta de instalación (" + installDir + ") y volverá a abrirse "
                + "solo. Sus valores recordados (rutas, últimas carpetas) no se tocan: son de su usuario.\n\n"
                + "Si otro usuario de este equipo tiene S-FiDE abierto desde la misma carpeta, la actualización "
                + "no podrá completarse hasta que lo cierre; en ese caso se revierte todo y no se pierde nada.");
        what.setWrapText(true);
        what.setPrefWidth(560);

        VBox content = new VBox(10, summary, new Label("Novedades de esta versión:"), notesArea, what);

        while (true) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", install, later, notes);
            alert.initOwner(owner);
            alert.setTitle("Buscar actualizaciones");
            alert.setHeaderText("Hay una versión nueva de S-FiDE. ¿Desea instalarla?");
            alert.getDialogPane().setContent(content);
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isPresent() && choice.get() == notes) {
                openInBrowser.accept(release.pageUrl());
                continue;
            }
            if (choice.isPresent() && choice.get() == install) {
                startInstall(release);
            } else {
                busy.set(false);
            }
            return;
        }
    }

    // ------------------------------------------------------------------
    // Descarga e instalación
    // ------------------------------------------------------------------

    private void startInstall(ReleaseInfo release) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setPrefWidth(420);
        Label step = new Label("Preparando la descarga...");
        VBox box = new VBox(10, step, bar);

        Alert progress = new Alert(Alert.AlertType.NONE, "", ButtonType.CANCEL);
        progress.initOwner(owner);
        progress.setTitle("Actualizando S-FiDE");
        progress.setHeaderText("Descargando la versión " + release.versionText());
        progress.getDialogPane().setContent(box);
        progress.setOnHidden(e -> cancelled.set(true));
        progress.show();

        Path updateDir = UserDataDirectory.get().resolve("update");
        executor.submit(() -> {
            try {
                Asset pkg = release.updatePackage();
                String expected = service.fetchExpectedSha256(release.checksum());
                Path zip = service.downloadAndVerify(pkg, expected, updateDir,
                        fraction -> Platform.runLater(() -> bar.setProgress(fraction)), cancelled::get);

                Platform.runLater(() -> {
                    step.setText("Verificando que la instalación se pueda actualizar...");
                    bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                });
                UpdaterRun check = service.verifyWithUpdater(zip);
                if (check.exitCode() != 0) {
                    throw new UpdateCheckException(lastLines(check.output(), 6));
                }

                if (cancelled.get()) {
                    busy.set(false);
                    return;
                }
                service.launchApply(zip, UserDataDirectory.get());
                Platform.runLater(() -> {
                    progress.close();
                    exitForUpdate.run();
                });
            } catch (UpdateCheckException e) {
                Platform.runLater(() -> {
                    boolean wasCancelled = cancelled.get();
                    progress.close();
                    busy.set(false);
                    if (!wasCancelled) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
                        alert.initOwner(owner);
                        alert.setTitle("Actualizando S-FiDE");
                        alert.setHeaderText("No se pudo instalar la actualización. S-FiDE sigue como estaba.");
                        alert.showAndWait();
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // Al arrancar
    // ------------------------------------------------------------------

    /**
     * Si la actualización anterior dejó un resultado, se lo muestra una sola
     * vez: el actualizador corre después de que la aplicación se cerró, así
     * que esta es la primera oportunidad de contarle al usuario cómo salió.
     */
    public void showPendingResult(Runnable afterClosing) {
        Path file = UserDataDirectory.get().resolve(UpdateService.RESULT_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            afterClosing.run();
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            afterClosing.run();
            return;
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Se vuelve a intentar la próxima vez.
            }
        }
        String status = p.getProperty("status", "");
        String message = p.getProperty("message", "");
        String version = p.getProperty("version", "");
        Path log = UserDataDirectory.get().resolve("actualizacion.log");

        Alert alert;
        if ("ok".equals(status)) {
            alert = new Alert(Alert.AlertType.INFORMATION, message);
            alert.setHeaderText("S-FiDE se actualizó a la versión " + version);
        } else if ("revertido-en-uso".equals(status)) {
            alert = new Alert(Alert.AlertType.WARNING, message);
            alert.setHeaderText("No se pudo actualizar: hay archivos en uso");
        } else {
            alert = new Alert(Alert.AlertType.ERROR, message + "\n\nDetalle en: " + log);
            alert.setHeaderText("La actualización no se pudo completar");
        }
        alert.initOwner(owner);
        alert.setTitle("Actualización de S-FiDE");
        // Lo que sigue (el resumen de novedades) se muestra recién cuando la persona cierra este aviso, para
        // que no se apilen dos ventanas a la vez.
        alert.setOnHidden(e -> afterClosing.run());
        alert.show();
    }

    /** Borra lo que una actualización anterior pudo dejar en la carpeta del usuario (paquetes, descargas a medias). */
    public static void cleanLeftovers() {
        Path dir = UserDataDirectory.get().resolve("update");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".zip") || name.endsWith(".part") || name.equals("sfideupdater-run.jar")) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // Puede estar todavía en uso por un actualizador que recién termina.
                    }
                }
            }
        } catch (IOException ignored) {
            // Nada que limpiar.
        }
    }

    /**
     * {@code true} si el actualizador está reemplazando archivos en este
     * momento. Mientras tanto la instalación puede estar a medias, y arrancar
     * S-FiDE desde ella (por cualquier usuario del equipo) daría errores
     * confusos. La marca vieja (de una caída) se ignora: una actualización
     * real dura segundos.
     */
    public static boolean isInstallationBeingUpdated() {
        Path install = AppInfo.installDir();
        if (install == null) {
            return false;
        }
        Path marker = install.resolve(BUSY_MARKER);
        try {
            return Files.exists(marker)
                    && Duration.between(Files.getLastModifiedTime(marker).toInstant(), Instant.now())
                    .compareTo(BUSY_MARKER_STALE_AFTER) <= 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Mensaje de {@link #isInstallationBeingUpdated()}; Swing porque se muestra antes de iniciar JavaFX. */
    public static void showBeingUpdatedMessage() {
        JOptionPane.showMessageDialog(null,
                "S-FiDE se está actualizando en este momento.\nEspere unos instantes y vuelva a abrirlo.",
                "S-FIDE - Sistema de Firma Digital Extendido", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String shorten(String text, int max) {
        String t = text == null ? "" : text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "\n[...]";
    }

    private static String lastLines(String text, int count) {
        String[] lines = text.trim().split("\\R");
        int from = Math.max(0, lines.length - count);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }
}
