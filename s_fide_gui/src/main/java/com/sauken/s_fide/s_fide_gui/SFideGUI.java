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

package com.sauken.s_fide.s_fide_gui;

import com.sauken.s_fide.s_fide_gui.utils.GUIUtils;
import com.sauken.s_fide.s_fide_gui.validators.ModuleValidator;
import com.sauken.s_fide.s_fide_gui.utils.ConfigurationManager;
import com.sauken.s_fide.s_fide_gui.utils.SingleInstanceGuard;
import com.sauken.s_fide.s_fide_gui.utils.TokenProfileCatalog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;
import java.util.Optional;
import javafx.scene.control.ButtonType;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

public class SFideGUI extends Application {
    private static final String VERSION_NUMBER = "1.2.0";
    private static final String VERSION = "S-FIDE GUI v" + VERSION_NUMBER + " - Grupo Sauken S.A.";
    private static final String CSS_FILE = "css/styles.css";
    private static final String HELP_FILE = "text/HELP.txt";
    private static final String LICENSE_FILE = "text/LICENSE.txt";
    private static final String FAQ_FILE = "text/FAQ.txt";
    private static final String GLOSARIO_FILE = "text/GLOSARIO.txt";
    private static final String COMEX_FILE = "text/COMEX.txt";
    private static final String XML_ELEMENT_HELP_FILE = "text/AYUDA_ELEMENTO_XML.txt";
    private static final String PDF_POSITION_HELP_FILE = "text/AYUDA_POSICION_PDF.txt";
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;
    private static final String[] ICON_FILES = {
            "/images/sfide-icon-16.png",
            "/images/sfide-icon-32.png",
            "/images/sfide-icon-48.png",
            "/images/sfide-icon-64.png",
            "/images/sfide-icon-128.png",
            "/images/sfide-icon-256.png",
            "/images/sfide-icon-512.png"
    };

    private TextArea sharedOutputArea;
    private ExecutorService executorService;
    private Stage primaryStage;
    private String licenseText;
    private String helpText;
    private String faqText;
    private String glosarioText;
    private String comexText;
    private String xmlElementHelpText;
    private String pdfPositionHelpText;
    private ConfigurationManager configManager;

    @Override
    public void init() throws Exception {
        super.init();
        try {
            System.out.println("Iniciando S-FIDE GUI...");

            configManager = ConfigurationManager.getInstance();

            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "S-FIDE-Worker");
                thread.setDaemon(true);
                return thread;
            });

            helpText = loadTextResource(HELP_FILE, "Archivo de ayuda");
            faqText = loadTextResource(FAQ_FILE, "Archivo de FAQ");
            licenseText = loadTextResource(LICENSE_FILE, "Archivo de licencia");
            glosarioText = loadTextResource(GLOSARIO_FILE, "Archivo de glosario");
            comexText = loadTextResource(COMEX_FILE, "Archivo de comercio exterior");
            xmlElementHelpText = loadTextResource(XML_ELEMENT_HELP_FILE, "Archivo de ayuda de elemento XML");
            pdfPositionHelpText = loadTextResource(PDF_POSITION_HELP_FILE, "Archivo de ayuda de posición PDF");

            System.out.println("Inicialización completada correctamente");
        } catch (Exception e) {
            System.err.println("Error durante la inicialización: " + e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("Error no capturado en el thread: " + thread.getName());
            throwable.printStackTrace(System.err);
        });

        if (!SingleInstanceGuard.tryAcquire()) {
            SingleInstanceGuard.showAlreadyRunningMessage();
            // Salida explícita: el diálogo se muestra con Swing, cuyo hilo
            // del toolkit AWT no es daemon — sin este System.exit(), nada
            // garantiza que el proceso termine solo después de cerrar el
            // diálogo, y quedaría un java.exe zombie sin ninguna ventana.
            System.exit(0);
        }

        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            System.out.println("Configurando ventana principal...");
            this.primaryStage = stage;

            Platform.runLater(() -> {
                try {
                    initializeGUI();
                } catch (Exception e) {
                    handleFatalError("Error al inicializar la interfaz gráfica", e);
                }
            });

        } catch (Exception e) {
            handleFatalError("Error al iniciar la aplicación", e);
        }
    }

    @Override
    public void stop() {
        try {
            System.out.println("Cerrando aplicación...");
            // Red de seguridad: handleApplicationClose() ya fuerza un guardado
            // (vía saveWindowBounds()) en los tres caminos normales de salida
            // (botón X, menú Salir, botón Salir), pero un cierre por error
            // fatal (handleFatalError(), o el diálogo de módulos faltantes)
            // llama a Platform.exit() directo sin pasar por ahí. Este guardado
            // incondicional asegura que cualquier cambio pendiente en memoria
            // (ya escrito en el Properties interno, solo con el volcado a
            // disco demorado por el debounce) no se pierda pase lo que pase.
            if (configManager != null) {
                configManager.saveConfiguration();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            GUIUtils.shutdown();
            System.out.println("Aplicación cerrada correctamente");
        } catch (Exception e) {
            System.err.println("Error durante el cierre de la aplicación: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    /**
     * Limpia los campos pasados después de ejecutar un módulo. Los campos
     * ligados a un valor por defecto recordado (bindBidirectional con una
     * Property de ConfigurationManager) nunca deben pasarse acá — que no se
     * limpien es consecuencia de no incluirlos en la llamada, no de un chequeo
     * de valor acá adentro (antes se comparaba el texto contra cada default
     * conocido uno por uno, lo que además de requerir acordarse de sumar cada
     * Property nueva a esa lista, corría el riesgo remoto de que un campo NO
     * ligado que coincidiera por casualidad con el valor de un default se
     * saltara la limpieza).
     */
    private void clearInputFields(TextField... fields) {
        if (fields == null) return;

        Platform.runLater(() -> {
            for (TextField field : fields) {
                if (field != null) {
                    if (field instanceof PasswordField) {
                        field.setText("");
                        System.gc();
                    } else {
                        field.clear();
                    }
                }
            }
        });
    }

    private String loadTextResource(String resourcePath, String resourceName) {
        String[] pathVariations = {
                resourcePath,
                "/" + resourcePath,
                "/main/resources/" + resourcePath,
                resourcePath.replaceFirst("text/", ""),
                "/text/" + resourcePath.replaceFirst("text/", ""),
                "/main/resources/text/" + resourcePath.replaceFirst("text/", "")
        };

        for (String path : pathVariations) {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
                if (inputStream != null) {
                    System.out.println(resourceName + " cargado exitosamente desde: " + path);
                    return new String(inputStream.readAllBytes());
                }
            } catch (Exception e) {
                System.out.println("No se pudo cargar " + resourceName.toLowerCase() +
                        " desde: " + path + " (" + e.getMessage() + ")");
            }
        }

        for (String path : pathVariations) {
            try {
                Path filePath = Paths.get(path);
                if (Files.exists(filePath)) {
                    String content = Files.readString(filePath);
                    System.out.println(resourceName + " cargado exitosamente desde archivo: " + path);
                    return content;
                }
            } catch (Exception e) {
                System.out.println("No se pudo cargar " + resourceName.toLowerCase() +
                        " desde archivo: " + path + " (" + e.getMessage() + ")");
            }
        }

        System.err.println(resourceName + " no encontrado en ninguna ubicación");
        return resourceName + " no disponible";
    }

    private String loadResource(String resourcePath) throws IOException {
        List<String> pathVariations = Arrays.asList(
                resourcePath,
                "/" + resourcePath,
                "/main/resources/" + resourcePath,
                resourcePath.replaceFirst("text/", ""),
                "/text/" + resourcePath.replaceFirst("text/", ""),
                "/main/resources/text/" + resourcePath.replaceFirst("text/", "")
        );

        for (String path : pathVariations) {
            URL resourceUrl = null;

            resourceUrl = getClass().getClassLoader().getResource(path);
            if (resourceUrl == null) {
                resourceUrl = getClass().getResource(path);
            }
            if (resourceUrl == null) {
                resourceUrl = getClass().getResource("/" + path);
            }

            if (resourceUrl != null) {
                System.out.println("Recurso encontrado en: " + path);
                return resourceUrl.toExternalForm();
            }
        }

        throw new IOException("No se pudo encontrar el recurso: " + resourcePath +
                "\nRutas intentadas:\n- " + String.join("\n- ", pathVariations));
    }

    private void handleFatalError(String message, Exception e) {
        String fullMessage = message + ": " + e.getMessage();
        System.err.println(fullMessage);
        e.printStackTrace(System.err);
        Platform.runLater(() -> {
            GUIUtils.showError("Error Fatal", fullMessage);
            Platform.exit();
        });
    }

    private void handleError(String message, Exception e) {
        String fullMessage = message + ": " + e.getMessage();
        System.err.println(fullMessage);
        e.printStackTrace(System.err);
        Platform.runLater(() -> {
            GUIUtils.showError("Error", fullMessage);
            sharedOutputArea.appendText("\nError: " + fullMessage);
        });
    }

    private boolean checkModuleAvailability() {
        return validateModules(false);
    }

    private boolean validateModules(boolean showSuccessMessage) {
        String[] modules = {
                "TokenSlotsView",
                "TokenCertificateExtractor",
                "PKCS12CertificateExtractor",
                "XMLSignerPKCS11",
                "XMLSignerPKCS12",
                "XMLVerifySignatures",
                "XMLVerifyXSDStructure",
                "PDFSignerPKCS11",
                "PDFSignerPKCS12"
        };

        StringBuilder errorMessage = new StringBuilder();
        boolean allAvailable = true;

        for (String module : modules) {
            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile(module);
            if (!result.valid()) {
                allAvailable = false;
                errorMessage.append("- ").append(result.errorMessage()).append("\n");
            }
        }

        if (!allAvailable) {
            Platform.runLater(() -> showModuleErrorDialog(errorMessage.toString()));
        } else if (showSuccessMessage) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Validación de Módulos");
                alert.setHeaderText("Validación Exitosa");
                alert.setContentText("Todos los módulos están correctamente habilitados");
                alert.showAndWait();
            });
        }

        return allAvailable;
    }

    private void showModuleErrorDialog(String errorMessage) {
        String fullMessage = errorMessage + "\n" +
                "Posibles soluciones:\n" +
                "1. Verifique que todos los archivos JAR necesarios estén en el mismo directorio\n" +
                "2. Asegúrese de que los nombres de los archivos JAR sean correctos\n" +
                "3. Compruebe los permisos de acceso a los archivos\n";

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error de Carga de Módulos");
        alert.setHeaderText("No se pudieron cargar todos los módulos necesarios");

        TextArea textArea = new TextArea(fullMessage);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(10);
        textArea.setPrefColumnCount(50);

        alert.getDialogPane().setContent(new VBox(textArea));
        alert.showAndWait();
        Platform.exit();
    }

    private void handleApplicationClose() {
        try {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmar Salida");
            alert.setHeaderText("¿Está seguro que desea salir?");
            alert.setContentText("Se cerrarán todas las operaciones en curso.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                System.out.println("Cerrando aplicación por solicitud del usuario");
                configManager.saveWindowBounds(
                        primaryStage.getX(),
                        primaryStage.getY(),
                        primaryStage.getWidth(),
                        primaryStage.getHeight(),
                        primaryStage.isMaximized()
                );
                Platform.runLater(() -> {
                    try {
                        if (executorService != null) {
                            executorService.shutdown();
                        }
                        Platform.exit();
                    } catch (Exception e) {
                        System.err.println("Error durante el cierre: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error al mostrar diálogo de cierre: " + e.getMessage());
            Platform.exit();
        }
    }

    private boolean isLowResolution() {
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        return bounds.getWidth() <= 1366 && bounds.getHeight() <= 768;
    }

    private void initializeGUI() throws IOException {
        System.out.println("Iniciando construcción de la interfaz gráfica");

        if (!checkModuleAvailability()) {
            return;
        }

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Node mainLayout = createMainLayout();
        VBox.setVgrow(mainLayout, Priority.ALWAYS);

        root.getChildren().addAll(
                createMenuBar(),
                mainLayout,
                createOutputPane(),
                createControlBox()
        );

        Scene scene = createScene(root);
        configureStage(scene);

        System.out.println("Interfaz gráfica construida exitosamente");

        executorService.submit(this::createDesktopShortcutIfNeeded);
        executorService.submit(this::createDocShortcutsIfNeeded);
    }

    /**
     * Crea accesos directos en el escritorio y en el menú inicio (carpeta
     * "S-FiDE") la primera vez que se ejecuta esta instalación de S-FiDE
     * (Windows únicamente) — queda registrado en sfide-defaults.properties
     * para no repetirlo en próximas ejecuciones, ni siquiera si el usuario los
     * borra después. Corre en el hilo de fondo existente (no bloquea la
     * interfaz); si falla, no interrumpe el arranque y solo se informa por
     * consola en ese caso — nada se imprime cuando sale bien.
     */
    private void createDesktopShortcutIfNeeded() {
        if (configManager.isDesktopShortcutCreated()) {
            return;
        }
        createDesktopShortcut();
    }

    /**
     * Handler del ítem de menú "Herramientas &gt; Recrear accesos directos".
     * A diferencia de la creación automática al primer arranque, esto lo pide
     * el usuario explícitamente — pensado sobre todo para después de
     * actualizar S-FiDE a una carpeta nueva (el acceso directo, sin versión
     * en el nombre desde esta función, queda apuntando a la instalación
     * vieja hasta que algo lo sobrescriba) o para recrear uno que se borró
     * sin querer. Por eso, a diferencia de las versiones automáticas, sí
     * informa el resultado al usuario. Corre en el hilo de fondo existente
     * (ya se invoca vía executorService.submit desde el menú).
     */
    private void recreateShortcuts() {
        boolean appOk = createDesktopShortcut();
        boolean docsOk = createDocShortcuts();
        boolean allOk = appOk && docsOk;

        Platform.runLater(() -> {
            Alert alert = new Alert(allOk ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
            alert.setTitle("Accesos directos");
            if (allOk) {
                alert.setHeaderText(null);
                alert.setContentText("Se recrearon los accesos directos en el escritorio y el menú inicio, "
                        + "apuntando a esta instalación.");
            } else {
                alert.setHeaderText("No se pudieron recrear todos los accesos directos");
                alert.setContentText("Revise la consola para más detalle. Esto no afecta el funcionamiento "
                        + "normal de S-FiDE.");
            }
            alert.showAndWait();
        });
    }

    /**
     * La acción en sí, sin el chequeo de "primera vez" — reutilizada por
     * createDesktopShortcutIfNeeded() (arranque automático) y por el ítem de
     * menú "Recrear accesos directos" (disparado a mano por el usuario, por
     * ejemplo después de actualizar de versión a una carpeta nueva, para que
     * el acceso directo apunte a esta instalación). Devuelve true si terminó
     * sin errores conocidos (o directamente no aplica, ej. no-Windows), false
     * si algo falló — el ítem de menú usa esto para informar el resultado; el
     * arranque automático lo ignora a propósito, nunca debe interrumpir ni
     * informar nada por su cuenta.
     */
    private boolean createDesktopShortcut() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            markDesktopShortcutCreated();
            return true;
        }

        try {
            Path sfideDir = Paths.get(
                    SFideGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();

            if (sfideDir == null) {
                markDesktopShortcutCreated();
                return false;
            }

            Path batPath = sfideDir.resolve("SFide-GUI.bat");
            Path iconPath = sfideDir.resolve("S-FiDE.ico");

            if (!Files.exists(batPath)) {
                markDesktopShortcutCreated();
                return false;
            }

            // Sin número de versión a propósito: así, al actualizar S-FiDE a una
            // carpeta nueva, el primer arranque de la versión nueva sobrescribe
            // este mismo acceso directo (mismo nombre, mismo path en Escritorio y
            // Menú Inicio) en vez de sumar un ícono más al lado del de la versión
            // vieja. Antes incluía la versión en el nombre — se sacó porque dejaba
            // íconos viejos acumulados y, si el usuario borraba la carpeta
            // anterior a mano, quedaban apuntando a una ubicación inexistente.
            String shortcutFile = "S-FiDE.lnk";
            String iconPathQuoted = Files.exists(iconPath) ? psQuote(iconPath) : null;

            // Ojo: nada de comillas dobles en este script. Se lo pasa entero como
            // valor de "powershell -Command", y las comillas dobles internas no
            // sobreviven ese paso (el parser de argumentos de Windows las consume
            // al re-tokenizar la línea completa) — el propio $Shortcut.CreateShortcut
            // terminaba recibiendo "$desktop\S-FiDE" como token suelto en vez de un
            // string. Comillas simples y concatenación con "+" evitan el problema
            // por completo. Confirmado reproduciendo el error exacto y su fix antes
            // de aplicarlo acá.
            List<String> lines = new ArrayList<>();
            lines.add("$WshShell = New-Object -ComObject WScript.Shell");
            lines.add("$startMenuDir = [Environment]::GetFolderPath('Programs') + '\\S-FiDE'");
            lines.add("New-Item -ItemType Directory -Force -Path $startMenuDir | Out-Null");
            lines.addAll(shortcutScript("Desktop", "[Environment]::GetFolderPath('Desktop')",
                    shortcutFile, batPath, sfideDir, iconPathQuoted));
            lines.addAll(shortcutScript("StartMenu", "$startMenuDir",
                    shortcutFile, batPath, sfideDir, iconPathQuoted));
            String script = String.join("; ", lines);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-Command", script
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Importante: esperar (con límite de tiempo) ANTES de leer la salida, no
            // al revés. Si algo bloquea a powershell.exe indefinidamente (una política
            // de seguridad corporativa que abre un diálogo, un antivirus interceptando
            // la ejecución, etc.), leer con readAllBytes() antes del waitFor() se
            // queda esperando para siempre — el límite de tiempo del waitFor() nunca
            // llegaría a aplicarse, porque nunca se alcanzaría esa línea. Solo se
            // intenta leer la salida DESPUÉS de confirmar que el proceso ya terminó
            // (es un script corto con poquísima salida, muy por debajo del tamaño del
            // buffer del pipe del SO, así que no hay riesgo de que él mismo se bloquee
            // esperando que alguien lea mientras tanto).
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                System.out.println("No se pudieron crear los accesos directos (escritorio/menú inicio): "
                        + "tiempo de espera agotado.");
                return false;
            }

            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("No se pudieron crear los accesos directos (escritorio/menú inicio)"
                        + (output.isBlank() ? "" : ": " + output.trim()));
                return false;
            }
            return true;
        } catch (Exception e) {
            System.out.println("No se pudieron crear los accesos directos (escritorio/menú inicio): " + e.getMessage());
            return false;
        } finally {
            // Se marca como "creado" pase lo que pase en el bloque de arriba —
            // falta de permisos, políticas de seguridad corporativas que bloqueen
            // PowerShell o WScript.Shell, ausencia de powershell.exe, lo que sea:
            // esto nunca debe hacer que el programa se cuelgue, muestre un error al
            // usuario, ni vuelva a intentarlo en cada arranque. Es una comodidad
            // opcional, no una función crítica.
            markDesktopShortcutCreated();
        }
    }

    /**
     * Arma las líneas de PowerShell para un acceso directo en una carpeta dada
     * (escritorio o menú inicio) — reutilizado dos veces desde
     * createDesktopShortcutIfNeeded(). Igual que el resto del script generado,
     * usa solo comillas simples y concatenación, nunca comillas dobles.
     */
    private static List<String> shortcutScript(String varSuffix, String folderExpr, String fileName,
                                                Path batPath, Path sfideDir, String iconPathQuotedOrNull) {
        String var = "$Shortcut" + varSuffix;
        List<String> lines = new ArrayList<>();
        lines.add(var + " = $WshShell.CreateShortcut(" + folderExpr + " + '\\" + fileName + "')");
        lines.add(var + ".TargetPath = '" + psQuote(batPath) + "'");
        lines.add(var + ".WorkingDirectory = '" + psQuote(sfideDir) + "'");
        if (iconPathQuotedOrNull != null) {
            lines.add(var + ".IconLocation = '" + iconPathQuotedOrNull + "'");
        }
        lines.add(var + ".Description = 'S-FiDE - Sistema de Firma Digital Extendido'");
        lines.add(var + ".Save()");
        return lines;
    }

    /**
     * Crea accesos directos al escritorio y al menú inicio (carpeta "S-FiDE")
     * hacia la guía de usuario y el manual técnico en doc/, la primera vez
     * que corre esta instalación (Windows únicamente) — mismo patrón que
     * createDesktopShortcutIfNeeded() de arriba (proceso PowerShell con
     * timeout+destroyForcibly, comillas simples únicamente, se marca como
     * "hecho" pase lo que pase). Flag independiente del acceso a la GUI: ver
     * ConfigurationManager.isDocShortcutsCreated().
     * <p>
     * A diferencia del acceso a SFide-GUI.bat (un .lnk que ejecuta un
     * programa), estos son archivos .url ("acceso directo a Internet") que
     * apuntan a una URL file:// del documento HTML — Windows los abre con el
     * navegador predeterminado del usuario, igual que hacer doble clic en el
     * archivo. No requieren WScript.Shell: son un archivo de texto plano con
     * formato INI, así que el script de PowerShell solo necesita escribirlos
     * con Set-Content, sin crear ningún objeto COM.
     */
    private void createDocShortcutsIfNeeded() {
        if (configManager.isDocShortcutsCreated()) {
            return;
        }
        createDocShortcuts();
    }

    /**
     * La acción en sí, sin el chequeo de "primera vez" — mismo motivo que
     * createDesktopShortcut() de arriba: reutilizada por
     * createDocShortcutsIfNeeded() y por el ítem de menú manual. Devuelve
     * true si terminó sin errores conocidos.
     */
    private boolean createDocShortcuts() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            markDocShortcutsCreated();
            return true;
        }

        try {
            Path sfideDir = Paths.get(
                    SFideGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();

            if (sfideDir == null) {
                markDocShortcutsCreated();
                return false;
            }

            Path userGuidePath = sfideDir.resolve("doc").resolve("manual-usuario-sfide-gui.html");
            Path techManualPath = sfideDir.resolve("doc").resolve("manual-tecnico-integracion.html");
            Path iconPath = sfideDir.resolve("S-FiDE.ico");
            String iconPathQuoted = Files.exists(iconPath) ? psQuote(iconPath) : null;

            List<String> lines = new ArrayList<>();
            lines.add("$startMenuDir = [Environment]::GetFolderPath('Programs') + '\\S-FiDE'");
            lines.add("New-Item -ItemType Directory -Force -Path $startMenuDir | Out-Null");
            lines.add("$desktopDir = [Environment]::GetFolderPath('Desktop')");

            boolean anyDoc = false;
            if (Files.exists(userGuidePath)) {
                anyDoc = true;
                lines.addAll(urlShortcutScript("Guía de Usuario S-FiDE GUI.url", userGuidePath, iconPathQuoted));
            }
            if (Files.exists(techManualPath)) {
                anyDoc = true;
                lines.addAll(urlShortcutScript("Manual Técnico S-FiDE.url", techManualPath, iconPathQuoted));
            }

            if (!anyDoc) {
                markDocShortcutsCreated();
                return false;
            }

            String script = String.join("; ", lines);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-Command", script
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Mismo orden esperar-antes-de-leer que createDesktopShortcutIfNeeded()
            // — ver el comentario detallado ahí, aplica igual acá.
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                System.out.println("No se pudieron crear los accesos directos a la documentación: "
                        + "tiempo de espera agotado.");
                return false;
            }

            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("No se pudieron crear los accesos directos a la documentación"
                        + (output.isBlank() ? "" : ": " + output.trim()));
                return false;
            }
            return true;
        } catch (Exception e) {
            System.out.println("No se pudieron crear los accesos directos a la documentación: " + e.getMessage());
            return false;
        } finally {
            markDocShortcutsCreated();
        }
    }

    /**
     * Arma las líneas de PowerShell que escriben un archivo .url (Desktop y
     * Menú Inicio) apuntando a la URI file:// de un documento HTML. La URI se
     * calcula en Java (Path.toUri()), no en PowerShell — así el saneamiento de
     * espacios/acentos/caracteres especiales en la ruta lo hace la biblioteca
     * estándar de Java, en vez de reconstruirlo a mano en el script.
     */
    private static List<String> urlShortcutScript(String fileName, Path docPath, String iconPathQuotedOrNull) {
        String fileUrl = docPath.toUri().toString();
        List<String> contentParts = new ArrayList<>();
        contentParts.add("'[InternetShortcut]'");
        contentParts.add("'URL=" + fileUrl.replace("'", "''") + "'");
        if (iconPathQuotedOrNull != null) {
            contentParts.add("'IconFile=" + iconPathQuotedOrNull + "'");
            contentParts.add("'IconIndex=0'");
        }
        String contentExpr = String.join(" + [Environment]::NewLine + ", contentParts);

        List<String> lines = new ArrayList<>();
        lines.add("Set-Content -LiteralPath ($desktopDir + '\\" + fileName + "') -Value (" + contentExpr
                + ") -Encoding UTF8");
        lines.add("Set-Content -LiteralPath ($startMenuDir + '\\" + fileName + "') -Value (" + contentExpr
                + ") -Encoding UTF8");
        return lines;
    }

    /**
     * Mismo motivo que markDesktopShortcutCreated(): ConfigurationManager solo
     * se puede tocar desde el hilo de la interfaz gráfica.
     */
    private void markDocShortcutsCreated() {
        try {
            Platform.runLater(() -> {
                try {
                    configManager.setDocShortcutsCreated(true);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    /**
     * ConfigurationManager persiste sus cambios con un PauseTransition, que
     * como toda Animation de JavaFX solo puede controlarse desde el hilo de
     * la interfaz gráfica — llamarlo directamente desde este hilo de fondo
     * lanza IllegalStateException (silenciosa, porque el Runnable se envía
     * con executorService.submit() sin revisar el Future).
     */
    private void markDesktopShortcutCreated() {
        try {
            Platform.runLater(() -> {
                try {
                    configManager.setDesktopShortcutCreated(true);
                } catch (Exception ignored) {
                    // Ver el comentario en createDesktopShortcutIfNeeded(): esto nunca
                    // debe poder colgar ni interrumpir la aplicación.
                }
            });
        } catch (Exception ignored) {
            // Idem, por si Platform.runLater() mismo llegara a fallar.
        }
    }

    private static String psQuote(Path path) {
        return path.toString().replace("'", "''");
    }

    private Scene createScene(VBox root) {
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            String cssPath = loadResource(CSS_FILE);
            System.out.println("Cargando CSS desde: " + cssPath);
            scene.getStylesheets().add(cssPath);
        } catch (IOException e) {
            System.err.println("Error al cargar el archivo CSS: " + e.getMessage());
        }
        return scene;
    }

    private void configureStage(Scene scene) {
        try {
            Platform.setImplicitExit(true);
            primaryStage.setTitle("S-FIDE - Sistema de Firma Digital Extendido - v" + VERSION_NUMBER);
            primaryStage.setScene(scene);

            try {
                int cargados = 0;
                for (String iconFile : ICON_FILES) {
                    try (InputStream iconStream = getClass().getResourceAsStream(iconFile)) {
                        if (iconStream != null) {
                            primaryStage.getIcons().add(new Image(iconStream));
                            cargados++;
                        }
                    }
                }
                if (cargados > 0) {
                    System.out.println("Ícono cargado exitosamente (" + cargados + " resoluciones)");
                } else {
                    System.err.println("No se pudo encontrar el archivo de ícono");
                }
            } catch (Exception e) {
                System.err.println("Error al cargar el ícono de la aplicación: " + e.getMessage());
            }

            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            applyRememberedWindowBounds(bounds);

            primaryStage.setOnCloseRequest(windowEvent -> {
                windowEvent.consume();
                Platform.runLater(this::handleApplicationClose);
            });

            primaryStage.show();
        } catch (Exception e) {
            handleFatalError("Error al configurar la ventana principal", e);
        }
    }

    private void applyRememberedWindowBounds(Rectangle2D screenBounds) {
        try {
            String savedWidth = configManager.getWindowWidth();
            String savedHeight = configManager.getWindowHeight();
            if (savedWidth != null && !savedWidth.isBlank() && savedHeight != null && !savedHeight.isBlank()) {
                primaryStage.setX(Double.parseDouble(configManager.getWindowX()));
                primaryStage.setY(Double.parseDouble(configManager.getWindowY()));
                primaryStage.setWidth(Double.parseDouble(savedWidth));
                primaryStage.setHeight(Double.parseDouble(savedHeight));
                primaryStage.setMaximized(configManager.isWindowMaximized());
                return;
            }
        } catch (NumberFormatException e) {
            System.err.println("Tamaño/posición de ventana guardados inválidos, se usa el valor por defecto: " + e.getMessage());
        }

        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setMaximized(true);
    }

    private void copyToClipboardWithConfirmation(String text, Node source, String confirmationMessage) {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            showTemporaryTooltip(source, confirmationMessage);
        } catch (Exception e) {
            handleError("Error al copiar al portapapeles", e);
        }
    }

    private void showTemporaryTooltip(Node node, String message) {
        try {
            Tooltip tooltip = new Tooltip(message);
            Point2D p = node.localToScene(0.0, 0.0);
            Window window = node.getScene().getWindow();

            tooltip.show(
                    node,
                    p.getX() + window.getX() + 5,
                    p.getY() + window.getY() + node.getBoundsInLocal().getHeight() + 5
            );

            PauseTransition pt = new PauseTransition(Duration.seconds(2));
            pt.setOnFinished(e -> tooltip.hide());
            pt.play();
        } catch (Exception e) {
            System.err.println("Error al mostrar tooltip: " + e.getMessage());
        }
    }

    private GridPane createStandardGridPane() {
        GridPane grid = new GridPane();
        if (isLowResolution()) {
          grid.setHgap(9);
          grid.setVgap(9);
          grid.setPadding(new Insets(16));
        }
        else {
          grid.setHgap(10);
          grid.setVgap(10);
          grid.setPadding(new Insets(20));
        }
        return grid;
    }

    private TextField createTextField(String promptText, String defaultValue) {
        TextField textField = new TextField(defaultValue);
        textField.setPromptText(promptText);
        textField.setPrefWidth(400);
        return textField;
    }

    private TextField createTextField(String promptText) {
        return createTextField(promptText, "");
    }

    private TextField createNumericTextField(String promptText, String defaultValue) {
        TextField textField = createTextField(promptText, defaultValue);
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                Platform.runLater(() -> textField.setText(newValue.replaceAll("[^\\d]", "")));
            }
        });
        return textField;
    }

    private TextField createNumericTextField(String promptText) {
        return createNumericTextField(promptText, "");
    }

    private PasswordField createPasswordField(String promptText) {
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(promptText);
        passwordField.setPrefWidth(400);
        return passwordField;
    }

    /**
     * Botón de ayuda contextual ("?") para un campo puntual — junto a
     * "Elemento XML (ID) a Firmar" en las tres pestañas de firma XML, y
     * junto a la posición X/Y en las tres pestañas de firma PDF. Muestra el
     * mismo tipo de diálogo con formato enriquecido que Guía Rápida/FAQ/
     * Glosario/Comercio Exterior en la ventana de Ayuda (createRichTextView()),
     * no un simple texto corrido.
     */
    private Button createFieldHelpButton(String dialogTitle, String helpText) {
        Button helpButton = new Button("?");
        helpButton.getStyleClass().add("field-help");
        helpButton.setTooltip(new Tooltip("Ayuda sobre este campo"));
        helpButton.setOnAction(e -> Platform.runLater(() -> showFieldHelpDialog(dialogTitle, helpText)));
        return helpButton;
    }

    private void showFieldHelpDialog(String dialogTitle, String helpText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ayuda");
        alert.setHeaderText(dialogTitle);
        alert.getDialogPane().setContent(createRichTextView(helpText));
        alert.getDialogPane().setPrefWidth(620);
        alert.getDialogPane().setPrefHeight(480);
        alert.showAndWait();
    }

    private Button createBrowseButton() {
        Button button = new Button("Examinar...");
        button.getStyleClass().add("browse");
        button.setTooltip(new Tooltip("Seleccionar archivo"));
        return button;
    }

    private HBox createDriverSelectorBox(TextField pkcs11LibPathField) {
        ComboBox<TokenProfileCatalog.TokenProfile> combo = new ComboBox<>();
        combo.setPromptText("Marca/modelo de token...");
        combo.setPrefWidth(280);
        combo.getItems().addAll(TokenProfileCatalog.getProfiles());
        combo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TokenProfileCatalog.TokenProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.descripcion());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(TokenProfileCatalog.TokenProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.descripcion());
            }
        });
        combo.setOnAction(e -> {
            TokenProfileCatalog.TokenProfile selected = combo.getValue();
            if (selected != null) {
                String remembered = configManager.getLibraryPathForProfile(selected.descripcion());
                String path = (remembered != null && !remembered.isBlank())
                        ? remembered
                        : selected.rutaParaSistemaActual();
                if (path != null && !path.isBlank()) {
                    Platform.runLater(() -> pkcs11LibPathField.setText(path));
                    configManager.setDefaultPKCS11LibPath(path);
                }
            }
        });

        pkcs11LibPathField.textProperty().addListener((observable, oldValue, newValue) -> {
            TokenProfileCatalog.TokenProfile selected = combo.getValue();
            if (selected != null && newValue != null && !newValue.isBlank()) {
                configManager.setLibraryPathForProfile(selected.descripcion(), newValue);
            }
        });

        Button detectButton = new Button("Detectar automáticamente");
        detectButton.setTooltip(new Tooltip(
                "Busca en las rutas típicas cuál driver PKCS#11 está instalado en este equipo"));
        detectButton.setOnAction(e -> Platform.runLater(() -> detectDriver(pkcs11LibPathField, combo)));

        HBox box = new HBox(10, combo, detectButton);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void detectDriver(TextField pkcs11LibPathField, ComboBox<TokenProfileCatalog.TokenProfile> combo) {
        List<TokenProfileCatalog.TokenProfile> found = new java.util.ArrayList<>();
        for (TokenProfileCatalog.TokenProfile profile : TokenProfileCatalog.getProfiles()) {
            String path = profile.rutaParaSistemaActual();
            if (path != null && !path.isBlank() && new File(path).exists()) {
                found.add(profile);
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Detección automática de driver");

        if (found.isEmpty()) {
            alert.setHeaderText("No se encontró ningún driver conocido en su ruta típica");
            alert.setContentText("Puede seleccionar la marca/modelo manualmente en la lista, "
                    + "o usar \"Examinar...\" para indicar la ruta.");
        } else if (found.size() == 1) {
            TokenProfileCatalog.TokenProfile profile = found.get(0);
            combo.setValue(profile);
            String remembered = configManager.getLibraryPathForProfile(profile.descripcion());
            String path = (remembered != null && !remembered.isBlank())
                    ? remembered
                    : profile.rutaParaSistemaActual();
            pkcs11LibPathField.setText(path);
            configManager.setDefaultPKCS11LibPath(path);
            alert.setHeaderText("Detectado: " + profile.descripcion());
            alert.setContentText("Estado: " + profile.estado()
                    + "\nEstrategia de hash: " + profile.estrategiaHash());
        } else {
            StringBuilder sb = new StringBuilder("Se encontraron varios drivers instalados en este equipo:\n\n");
            for (TokenProfileCatalog.TokenProfile p : found) {
                sb.append("- ").append(p.descripcion()).append("\n");
            }
            sb.append("\nSeleccione manualmente cuál usar en la lista desplegable.");
            alert.setHeaderText("Se encontró más de un driver instalado");
            alert.setContentText(sb.toString());
        }
        alert.showAndWait();
    }

    private Button createExecuteButton() {
        Button button = new Button("Ejecutar");
        button.setDefaultButton(true);
        button.getStyleClass().add("execute");
        button.setTooltip(new Tooltip("Ejecutar operación"));
        return button;
    }

    private void addToGrid(GridPane grid, int row, String labelText, Node field, Button browseButton) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-weight: normal;");
        grid.add(label, 0, row);
        grid.add(field, 1, row);

        if (browseButton != null) {
            HBox buttonBox = new HBox(10);
            buttonBox.getChildren().add(browseButton);
            grid.add(buttonBox, 2, row);
        }
    }

    /**
     * Suma un botón extra a la caja de botones ya existente en la columna 2
     * de una fila (la misma que arma addToGrid() para "Examinar..."), o crea
     * una si esa fila todavía no tenía ninguno — usado para agregar "Abrir
     * documento generado" junto al campo del documento de entrada sin
     * duplicar la fila completa.
     */
    private void addButtonToRow(GridPane grid, int row, Button button) {
        Node buttonBox = grid.getChildren().stream()
                .filter(node -> GridPane.getRowIndex(node) == row && GridPane.getColumnIndex(node) == 2)
                .findFirst()
                .orElse(null);

        if (buttonBox instanceof HBox) {
            ((HBox) buttonBox).getChildren().add(button);
        } else {
            grid.add(new HBox(10, button), 2, row);
        }
    }

    /**
     * Replica exactamente la convención de nombre de archivo de salida que
     * usan los seis módulos de firma (createOutputPath()/getOutputFileName()
     * en cada uno): mismo nombre + "-signed" + misma extensión, en la misma
     * carpeta del documento de entrada. No hay forma de que el módulo CLI
     * informe esta ruta de vuelta (cada uno corre en su propio proceso), así
     * que se recalcula acá con la misma lógica.
     */
    private static Path computeSignedOutputPath(String inputPath) {
        Path path = Paths.get(inputPath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String outputName = (dotIndex < 0)
                ? fileName + "-signed"
                : fileName.substring(0, dotIndex) + "-signed" + fileName.substring(dotIndex);
        return path.resolveSibling(outputName);
    }

    /**
     * Botón "Abrir documento generado", junto al campo del documento de
     * entrada en las seis pestañas de firma (XML/PDF × PKCS#11/PKCS#12/
     * Windows CSP-KSP). Arranca deshabilitado; se habilita solo cuando
     * onExecutionResult() recibe un código de salida 0 Y el archivo
     * "-signed" correspondiente realmente existe en disco — un código 0
     * nunca se da por sentado a ciegas. Se deshabilita solo si el usuario
     * edita el campo del documento de entrada después: el resultado ya no
     * corresponde necesariamente a lo que se ve en pantalla.
     */
    private final class OpenGeneratedDocumentButton {
        final Button button = new Button("Abrir documento generado");
        private Path generatedPath;

        OpenGeneratedDocumentButton(TextField inputPathField) {
            button.setDisable(true);
            button.setTooltip(new Tooltip(
                    "Abre el documento firmado (-signed) en el navegador predeterminado del sistema"));
            button.setOnAction(e -> Platform.runLater(() -> {
                if (generatedPath != null) {
                    openInBrowser(generatedPath.toUri().toString());
                }
            }));
            inputPathField.textProperty().addListener((obs, oldValue, newValue) -> disable());
        }

        private void disable() {
            generatedPath = null;
            button.setDisable(true);
        }

        void onExecutionResult(String inputPathAtExecution, int exitCode) {
            Platform.runLater(() -> {
                if (exitCode == 0) {
                    Path candidate = computeSignedOutputPath(inputPathAtExecution);
                    if (Files.exists(candidate)) {
                        generatedPath = candidate;
                        button.setDisable(false);
                        return;
                    }
                }
                disable();
            });
        }
    }

    private void addExecuteButton(GridPane grid, Button execute, int row) {
        Node buttonBox = grid.getChildren().stream()
                .filter(node -> GridPane.getRowIndex(node) == 0 && GridPane.getColumnIndex(node) == 2)
                .findFirst()
                .orElse(null);

        if (buttonBox instanceof HBox) {
            ((HBox) buttonBox).getChildren().add(execute);
        }
    }

    private VBox createTabContent(String description, GridPane grid) {
        VBox content = new VBox(10);
        content.getStyleClass().add("content-card");
        content.setPadding(new Insets(12));

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        Separator separator = new Separator();

        content.getChildren().addAll(descLabel, separator, grid);
        return content;
    }

    private void selectFile(TextField field, String title, String description, String... extensions) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(title);

            if (extensions != null && extensions.length > 0) {
                FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(
                        description,
                        extensions
                );
                fileChooser.getExtensionFilters().add(filter);
            }

            String currentPath = field.getText();
            if (currentPath != null && !currentPath.isEmpty()) {
                File currentFile = new File(currentPath);
                if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                    fileChooser.setInitialDirectory(currentFile.getParentFile());
                }
            }

            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                Platform.runLater(() -> field.setText(file.getAbsolutePath()));
            }
        } catch (Exception e) {
            handleError("Error al seleccionar archivo", e);
        }
    }

    private void selectLibraryFile(TextField field) {
        selectFile(field,
                "Seleccionar Biblioteca PKCS#11",
                "Bibliotecas",
                "*.dll", "*.so"
        );
        configManager.setDefaultPKCS11LibPath(field.getText());
    }

    private void selectPKCS12File(TextField field) {
        selectFile(field,
                "Seleccionar Archivo PKCS#12",
                "Archivos PKCS#12",
                "*.p12", "*.pfx"
        );
        configManager.setDefaultPKCS12Path(field.getText());
    }

    private void selectXMLFile(TextField field) {
        selectFile(field,
                "Seleccionar Archivo XML",
                "Archivos XML",
                "*.xml"
        );
    }

    private void selectXSDFile(TextField field) {
        selectFile(field,
                "Seleccionar Archivo XSD",
                "Archivos XSD",
                "*.xsd"
        );
    }

    private void selectPDFFile(TextField field) {
        selectFile(field,
                "Seleccionar Archivo PDF",
                "Archivos PDF",
                "*.pdf"
        );
    }

    private void validateRequiredField(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("El campo '%s' es requerido", fieldName)
            );
        }
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("Archivo");
        MenuItem exitMenuItem = new MenuItem("Salir");
        exitMenuItem.setOnAction(e -> Platform.runLater(this::handleApplicationClose));
        fileMenu.getItems().add(exitMenuItem);

        Menu toolsMenu = new Menu("Herramientas");
        MenuItem clearLogsMenuItem = new MenuItem("Limpiar Logs");
        clearLogsMenuItem.setOnAction(e -> Platform.runLater(this::clearOutput));
        MenuItem validateMenuItem = new MenuItem("Validar Módulos");
        validateMenuItem.setOnAction(e -> Platform.runLater(() -> validateModules(true)));
        toolsMenu.getItems().addAll(clearLogsMenuItem, validateMenuItem);

        if (isWindowsOS()) {
            MenuItem recreateShortcutsMenuItem = new MenuItem("Recrear accesos directos");
            recreateShortcutsMenuItem.setOnAction(e -> executorService.submit(this::recreateShortcuts));
            toolsMenu.getItems().addAll(new SeparatorMenuItem(), recreateShortcutsMenuItem);
        }

        Menu helpMenu = new Menu("Ayuda");
        MenuItem versionMenuItem = new MenuItem("Versión");
        MenuItem licenseMenuItem = new MenuItem("Licencia");
        MenuItem helpMenuItem = new MenuItem("Ayuda");
        MenuItem aboutMenuItem = new MenuItem("Acerca de");

        versionMenuItem.setOnAction(e -> Platform.runLater(this::showVersionDialog));
        licenseMenuItem.setOnAction(e -> Platform.runLater(this::showLicenseDialog));
        helpMenuItem.setOnAction(e -> Platform.runLater(this::showHelpDialog));
        aboutMenuItem.setOnAction(e -> Platform.runLater(this::showAboutDialog));

        helpMenu.getItems().addAll(
                versionMenuItem,
                licenseMenuItem,
                helpMenuItem,
                new SeparatorMenuItem(),
                aboutMenuItem
        );

        menuBar.getMenus().addAll(fileMenu, toolsMenu, helpMenu);
        return menuBar;
    }

    private TitledPane createOutputPane() {
        sharedOutputArea = new TextArea();
        sharedOutputArea.setEditable(false);
        sharedOutputArea.setWrapText(true);
        if (isLowResolution()) {
            sharedOutputArea.setPrefRowCount(8);
        }
        else {
            sharedOutputArea.setPrefRowCount(10);
        }
        sharedOutputArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-control-inner-background: white;");
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");

        TitledPane outputPane = new TitledPane("Salida del Proceso", sharedOutputArea);
        outputPane.setCollapsible(true);
        outputPane.setExpanded(false);
        if (isLowResolution()) {
            outputPane.setPrefHeight(150);
        }
        else {
            outputPane.setPrefHeight(190);
        }

        sharedOutputArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty() && !outputPane.isExpanded()) {
                outputPane.setExpanded(true);
            }
        });

        return outputPane;
    }

    private HBox createControlBox() {
        HBox controlBox = new HBox(10);
        controlBox.setPadding(new Insets(5));
        controlBox.setAlignment(Pos.CENTER);

        Button clearButton = new Button("Limpiar Salida");
        clearButton.setOnAction(e -> Platform.runLater(this::clearOutput));
        clearButton.setTooltip(new Tooltip("Limpiar el área de salida"));

        Button helpButton = new Button("Ayuda");
        helpButton.setOnAction(e -> Platform.runLater(this::showHelpDialog));
        helpButton.setTooltip(new Tooltip("Mostrar la ayuda"));

        Button exitButton = new Button("Salir");
        exitButton.setOnAction(e -> Platform.runLater(this::handleApplicationClose));
        exitButton.setTooltip(new Tooltip("Cerrar la aplicación"));

        controlBox.getChildren().addAll(clearButton, helpButton, exitButton);
        return controlBox;
    }

    private void clearOutput() {
        try {
            sharedOutputArea.clear();
            System.out.println("Área de salida limpiada");
        } catch (Exception e) {
            System.err.println("Error al limpiar el área de salida: " + e.getMessage());
        }
    }

    /**
     * Abre una URL en el navegador web predeterminado del sistema operativo
     * (Windows, Linux o macOS) vía la API estándar de Java (java.awt.Desktop) —
     * funciona igual para una URL http(s) que para una URL file:// de un
     * documento local.
     */
    private void openInBrowser(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
            System.out.println("Enlace abierto en el navegador: " + url);
        } catch (Exception e) {
            handleError("Error al abrir el enlace en el navegador", e);
        }
    }

    private void openWebsite() {
        openInBrowser("https://www.sauken.com.ar/");
    }

    private Hyperlink createWebsiteLink() {
        Hyperlink link = new Hyperlink("https://www.sauken.com.ar");
        link.setOnAction(e -> Platform.runLater(this::openWebsite));
        return link;
    }

    private Hyperlink createGithubLink() {
        Hyperlink link = new Hyperlink("https://github.com/Grupo-Sauken-S-A/S-FIDE");
        link.setOnAction(e -> Platform.runLater(
                () -> openInBrowser("https://github.com/Grupo-Sauken-S-A/S-FIDE")));
        return link;
    }

    /**
     * Abre en el navegador predeterminado un documento HTML de la carpeta
     * "doc" de esta instalación, como URL file:// — misma resolución de la
     * carpeta de instalación (a partir de la ubicación del propio jar en
     * ejecución) ya usada por createDocShortcuts().
     */
    private void openDocFile(String fileName) {
        try {
            Path sfideDir = Paths.get(
                    SFideGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();

            if (sfideDir == null) {
                throw new IllegalStateException("No se pudo determinar la carpeta de instalación");
            }

            Path docPath = sfideDir.resolve("doc").resolve(fileName);
            if (!Files.exists(docPath)) {
                throw new IllegalStateException("No se encontró el archivo de documentación: " + docPath);
            }

            openInBrowser(docPath.toUri().toString());
        } catch (Exception e) {
            handleError("Error al abrir la documentación", e);
        }
    }

    private void configureInfoTextArea(TextArea textArea, int rows) {
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Segoe UI', sans-serif;");
        textArea.setPrefWidth(600);
        textArea.setPrefRowCount(rows);
    }

    /**
     * Convierte texto plano con una convención mínima de marcado ("# " título,
     * "## " subtítulo, "- " viñeta, "**negrita**" en línea) en un panel con
     * jerarquía visual real, en vez de un TextArea de texto corrido. Sigue
     * siendo un archivo .txt fácil de editar a mano — no requiere HTML ni un
     * motor de render nuevo (WebView).
     */
    private ScrollPane createRichTextView(String content) {
        VBox container = new VBox(8);
        container.setPadding(new Insets(14, 18, 14, 18));
        container.setMaxWidth(Double.MAX_VALUE);

        StringBuilder paragraphBuffer = new StringBuilder();
        StringBuilder[] bulletBuffer = {null};

        for (String rawLine : content.split("\n", -1)) {
            String trimmed = rawLine.replace("\r", "").trim();

            if (trimmed.isEmpty()) {
                flushParagraph(container, paragraphBuffer);
                flushBullet(container, bulletBuffer);
                continue;
            }

            if (trimmed.startsWith("# ")) {
                flushParagraph(container, paragraphBuffer);
                flushBullet(container, bulletBuffer);
                container.getChildren().add(createHeading(trimmed.substring(2), 16));
                continue;
            }

            if (trimmed.startsWith("## ")) {
                flushParagraph(container, paragraphBuffer);
                flushBullet(container, bulletBuffer);
                container.getChildren().add(createHeading(trimmed.substring(3), 13.5));
                continue;
            }

            if (trimmed.startsWith("- ")) {
                flushParagraph(container, paragraphBuffer);
                flushBullet(container, bulletBuffer);
                bulletBuffer[0] = new StringBuilder(trimmed.substring(2));
                continue;
            }

            if (bulletBuffer[0] != null) {
                bulletBuffer[0].append(' ').append(trimmed);
            } else {
                if (paragraphBuffer.length() > 0) {
                    paragraphBuffer.append(' ');
                }
                paragraphBuffer.append(trimmed);
            }
        }
        flushParagraph(container, paragraphBuffer);
        flushBullet(container, bulletBuffer);

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white;");
        return scrollPane;
    }

    private void flushParagraph(VBox container, StringBuilder buffer) {
        if (buffer.length() > 0) {
            container.getChildren().add(createInlineFlow(buffer.toString(), 12.5));
            buffer.setLength(0);
        }
    }

    private void flushBullet(VBox container, StringBuilder[] buffer) {
        if (buffer[0] != null && buffer[0].length() > 0) {
            TextFlow flow = createInlineFlow(buffer[0].toString(), 12.5);
            Label bulletMark = new Label("•");
            bulletMark.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #2F7774; -fx-font-weight: bold;");
            HBox row = new HBox(8, bulletMark, flow);
            HBox.setHgrow(flow, Priority.ALWAYS);
            row.setAlignment(Pos.TOP_LEFT);
            row.setPadding(new Insets(0, 0, 0, 6));
            container.getChildren().add(row);
        }
        buffer[0] = null;
    }

    private Label createHeading(String text, double fontSize) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(String.format(
                "-fx-font-size: %.1fpx; -fx-font-weight: bold; -fx-text-fill: #063C3C;",
                fontSize));
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private TextFlow createInlineFlow(String text, double fontSize) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(Double.MAX_VALUE);
        String[] parts = text.split("\\*\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            boolean bold = (i % 2 == 1);
            Text run = new Text(parts[i]);
            run.setStyle(String.format(
                    "-fx-font-size: %.1fpx; -fx-fill: #1a1a1a;%s",
                    fontSize, bold ? " -fx-font-weight: bold;" : ""));
            flow.getChildren().add(run);
        }
        return flow;
    }

    /**
     * Contenido de la pestaña "Documentación" de la ventana de Ayuda: enlaces
     * para abrir, en el navegador web predeterminado del sistema, los
     * documentos HTML de la carpeta "doc" de esta instalación — los mismos
     * dos documentos para los que ya se ofrecen accesos directos de
     * escritorio/menú inicio en Windows (ver createDocShortcuts()), pero acá
     * disponibles con un clic y en cualquier sistema operativo.
     */
    private VBox createDocumentationContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label introLabel = new Label(
                "Documentación HTML incluida en la carpeta \"doc\" de esta instalación. "
                        + "Se abre en el navegador web predeterminado del sistema "
                        + "(Windows, Linux o macOS)."
        );
        introLabel.setWrapText(true);

        content.getChildren().addAll(
                introLabel,
                new Separator(),
                createDocLinkEntry(
                        "Guía de Usuario S-FiDE GUI",
                        "manual-usuario-sfide-gui.html",
                        "Recorrido completo de las pantallas de la interfaz gráfica, pensado "
                                + "para quien firma y verifica documentos haciendo clic, no por "
                                + "línea de comandos."
                ),
                createDocLinkEntry(
                        "Manual Técnico de Integración",
                        "manual-tecnico-integracion.html",
                        "Arquitectura, catálogo de todos los módulos con sus parámetros, "
                                + "mecanismos de firma (PKCS#11/PKCS#12/Windows CSP-KSP) y "
                                + "especialización de comercio exterior ALADI/MERCOSUR — para "
                                + "integradores por línea de comandos."
                )
        );

        return content;
    }

    private VBox createDocLinkEntry(String title, String fileName, String description) {
        Hyperlink link = new Hyperlink(title);
        link.setOnAction(e -> Platform.runLater(() -> openDocFile(fileName)));

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #52607a;");

        return new VBox(3, link, descLabel);
    }

    private VBox createContactContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label supportLabel = new Label("Soporte Técnico");
        supportLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        VBox supportInfo = new VBox(5);
        supportInfo.getChildren().addAll(
                new Label("Email: soporte@sauken.com.ar"),
                new Label("Teléfono: +54 9 351 519-1003"),
                new Label("Horario: Lunes a Viernes 9:00 - 17:00 (GMT-3)"),
                new Label("Este software se licencia bajo GNU GPLv2. El servico de soporte es con cargo")
        );

        Label companyLabel = new Label("Empresa");
        companyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        VBox companyInfo = new VBox(5);
        companyInfo.getChildren().addAll(
                new Label("Grupo Sauken S.A."),
                new Label("Córdoba, Argentina"),
                createWebsiteLink(),
                createGithubLink()
        );

        content.getChildren().addAll(
                supportLabel,
                supportInfo,
                new Separator(),
                companyLabel,
                companyInfo
        );

        return content;
    }

    private void showVersionDialog() {
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Versión");
            alert.setHeaderText(VERSION);

            String systemInfo = String.format("""
                Java Version: %s
                JavaFX Version: %s
                Sistema Operativo: %s %s
                Arquitectura: %s
                Memoria Máxima: %dMB""",
                    System.getProperty("java.version"),
                    System.getProperty("javafx.version"),
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("os.arch"),
                    Runtime.getRuntime().maxMemory() / 1024 / 1024
            );

            TextArea textArea = new TextArea(systemInfo);
            configureInfoTextArea(textArea, 6);

            VBox content = new VBox(10);
            content.getChildren().addAll(
                    new Label("Información del Sistema:"),
                    textArea
            );

            alert.getDialogPane().setExpandableContent(content);
            alert.showAndWait();

            System.out.println("Diálogo de versión mostrado");
        } catch (Exception e) {
            handleError("Error al mostrar información de versión", e);
        }
    }

    private void showLicenseDialog() {
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Licencia");
            alert.setHeaderText("Información de Licencia");

            TextArea textArea = new TextArea(licenseText);
            configureInfoTextArea(textArea, 20);

            VBox content = new VBox(10);
            content.getChildren().addAll(
                    new Label("GNU General Public License v2.0"),
                    textArea
            );

            Button copyButton = new Button("Copiar Licencia");
            copyButton.setOnAction(e -> copyToClipboardWithConfirmation(
                    licenseText,
                    copyButton,
                    "Licencia copiada al portapapeles"
            ));
            content.getChildren().add(copyButton);

            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(620);
            alert.showAndWait();

            System.out.println("Diálogo de licencia mostrado");
        } catch (Exception e) {
            handleError("Error al mostrar licencia", e);
        }
    }

    private void showHelpDialog() {
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Ayuda");
            alert.setHeaderText("Manual de Usuario");

            TabPane tabPane = new TabPane();
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

            Tab quickGuideTab = new Tab("Guía Rápida");
            quickGuideTab.setContent(createRichTextView(helpText));

            Tab faqTab = new Tab("Preguntas Frecuentes");
            faqTab.setContent(createRichTextView(faqText));

            Tab glosarioTab = new Tab("Glosario");
            glosarioTab.setContent(createRichTextView(glosarioText));

            Tab comexTab = new Tab("Comercio Exterior");
            comexTab.setContent(createRichTextView(comexText));

            Tab docsTab = new Tab("Documentación");
            docsTab.setContent(createDocumentationContent());

            Tab contactTab = new Tab("Contacto");
            contactTab.setContent(createContactContent());

            tabPane.getTabs().addAll(quickGuideTab, faqTab, glosarioTab, comexTab, docsTab, contactTab);

            alert.getDialogPane().setContent(tabPane);
            alert.getDialogPane().setPrefWidth(760);
            alert.getDialogPane().setPrefHeight(520);

            alert.showAndWait();

            System.out.println("Diálogo de ayuda mostrado");
        } catch (Exception e) {
            handleError("Error al mostrar ayuda", e);
        }
    }

    private void showAboutDialog() {
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Acerca de");
            alert.setHeaderText("S-FIDE");

            VBox content = new VBox(10);
            content.setPadding(new Insets(10));

            Label titleLabel = new Label("Sistema de Firma Digital Extendido");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            Label versionLabel = new Label(VERSION);
            versionLabel.setStyle("-fx-font-size: 14px;");

            Label descriptionLabel = new Label(
                    "S-FIDE es una herramienta profesional para la gestión de firmas " +
                            "digitales en entornos empresariales, desarrollada por Grupo Sauken S.A."
            );
            descriptionLabel.setWrapText(true);

            content.getChildren().addAll(
                    titleLabel,
                    versionLabel,
                    new Separator(),
                    descriptionLabel,
                    new Separator(),
                    createWebsiteLink()
            );

            alert.getDialogPane().setContent(content);
            alert.showAndWait();

            System.out.println("Diálogo Acerca de mostrado");
        } catch (Exception e) {
            handleError("Error al mostrar información sobre la aplicación", e);
        }
    }

    private BorderPane createMainLayout() {
        System.out.println("Creando panel de navegación y contenido");

        List<Tab> modules = new java.util.ArrayList<>(List.of(
                createTokenSlotsViewTab(),
                createTokenCertificateExtractorTab(),
                createPKCS12CertificateExtractorTab(),
                createXMLSignerPKCS11Tab(),
                createXMLSignerPKCS12Tab(),
                createXMLVerifySignaturesTab(),
                createXMLVerifyXSDStructureTab(),
                createPDFSignerPKCS11Tab(),
                createPDFSignerPKCS12Tab(),
                createPDFVerifySignaturesTab()
        ));

        if (isWindowsOS()) {
            modules.add(createWindowsCertificateStoreViewTab());
            modules.add(createXMLSignerWindowsCSPTab());
            modules.add(createPDFSignerWindowsCSPTab());
        }

        ScrollPane contentScroll = new ScrollPane();
        contentScroll.getStyleClass().add("content-scroll");
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        ListView<Tab> nav = new ListView<>();
        nav.getStyleClass().add("sidebar-nav");
        nav.getItems().addAll(modules);
        nav.setCellFactory(lv -> createModuleNavCell(nav));
        nav.setPrefWidth(250);
        nav.setMinWidth(200);

        nav.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> {
                    if (newTab != null) {
                        System.out.println("Cambio a módulo: " + newTab.getText());
                        contentScroll.setContent(newTab.getContent());
                        configManager.lastModuleProperty().set(newTab.getText());
                    }
                }
        );

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("main-layout");
        layout.setLeft(nav);
        layout.setCenter(contentScroll);

        String rememberedModule = configManager.lastModuleProperty().get();
        Tab moduleToSelect = modules.stream()
                .filter(t -> t.getText().equals(rememberedModule))
                .findFirst()
                .orElse(modules.get(0));
        nav.getSelectionModel().select(moduleToSelect);
        nav.scrollTo(moduleToSelect);

        return layout;
    }

    private ListCell<Tab> createModuleNavCell(ListView<Tab> nav) {
        Label icon = new Label();
        icon.getStyleClass().add("nav-icon");
        Label text = new Label();
        text.getStyleClass().add("nav-text");
        text.maxWidthProperty().bind(nav.widthProperty().subtract(70));

        HBox box = new HBox(10, icon, text);
        box.setAlignment(Pos.CENTER_LEFT);

        ListCell<Tab> cell = new ListCell<>() {
            @Override
            protected void updateItem(Tab item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    icon.setText(iconForModule(item.getText()));
                    text.setText(item.getText());
                    setText(null);
                    setGraphic(box);
                }
            }
        };
        cell.setPrefWidth(0);
        return cell;
    }

    private String iconForModule(String title) {
        if (title.contains("Slots")) return "⊙";
        if (title.contains("Certificado")) return "▤";
        if (title.contains("Windows")) return "⧉";
        if (title.contains("Firmar")) return "✎";
        if (title.contains("Verificar")) return "✓";
        return "●";
    }

    private Tab createTokenSlotsViewTab() {
        Tab tab = new Tab("Ver Slots de Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField("C:\\Windows\\System32\\eTPKCS11.dll");
        pkcs11LibPath.textProperty().bindBidirectional(configManager.pkcs11LibraryPathProperty());
        PasswordField password = createPasswordField("Contraseña del token");

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            executeTokenSlotsView(pkcs11LibPath.getText(), pass);
            clearInputFields(password);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        grid.add(createDriverSelectorBox(pkcs11LibPath), 1, 1);
        addToGrid(grid, 2, "Contraseña:", password, null);
        addExecuteButton(grid, execute, 3);

        VBox content = createTabContent(
                "Muestra el contenido de cada entrada de un token PKCS#11: el número de slot que el resto de "
                        + "los módulos necesita como parámetro, y si hay un certificado presente, de quién es y "
                        + "su vigencia. Es la manera de distinguir, en tokens donde conviven un certificado "
                        + "vencido y su renovación en entradas distintas del mismo dispositivo —algo frecuente "
                        + "con las autoridades certificantes—, cuál es cuál antes de usarlo para firmar.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createTokenCertificateExtractorTab() {
        Tab tab = new Tab("Ver Certificado de Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField("C:\\Windows\\System32\\eTPKCS11.dll");
        pkcs11LibPath.textProperty().bindBidirectional(configManager.pkcs11LibraryPathProperty());
        PasswordField password = createPasswordField("Contraseña del token");
        TextField slotNumber = createNumericTextField("0");
        slotNumber.textProperty().bindBidirectional(configManager.pkcs11SlotNumberProperty());

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            configManager.setDefaultSlotNumber(slotNumber.getText());
            executeTokenCertExtractor(pkcs11LibPath.getText(), pass, slotNumber.getText());
            clearInputFields(password);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        grid.add(createDriverSelectorBox(pkcs11LibPath), 1, 1);
        addToGrid(grid, 2, "Contraseña:", password, null);
        addToGrid(grid, 3, "Número de Slot:", slotNumber, null);
        addExecuteButton(grid, execute, 4);

        VBox content = createTabContent(
                "Extrae el certificado digital presente en un slot de un token PKCS#11: muestra en pantalla sus "
                        + "datos completos (sujeto, emisor, vigencia, número de serie) y además guarda una copia "
                        + "real en un archivo .PEM en disco — útil para conservarlo fuera del token, inspeccionarlo, "
                        + "o cargarlo en otra herramienta.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPKCS12CertificateExtractorTab() {
        Tab tab = new Tab("Ver Certificado de PKCS#12");
        GridPane grid = createStandardGridPane();

        TextField pkcs12Path = createTextField("C:\\Certificados\\certificado.pfx");
        pkcs12Path.textProperty().bindBidirectional(configManager.pkcs12FilePathProperty());
        PasswordField password = createPasswordField("Contraseña del archivo");

        Button browsePKCS12 = createBrowseButton();
        browsePKCS12.setOnAction(e -> Platform.runLater(() -> selectPKCS12File(pkcs12Path)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            executePKCS12CertExtractor(pkcs12Path.getText(), pass);
            clearInputFields(password);
        }));

        addToGrid(grid, 0, "Archivo PKCS#12:", pkcs12Path, browsePKCS12);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addExecuteButton(grid, execute, 2);

        VBox content = createTabContent(
                "Extrae el certificado digital de un archivo PKCS#12 (.p12 o .pfx): muestra en pantalla sus "
                        + "datos completos (sujeto, emisor, vigencia, número de serie) y además guarda una copia "
                        + "real en un archivo .PEM en disco. Misma utilidad que el extractor de tokens, pero para "
                        + "certificados que ya existen como archivo.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLSignerPKCS11Tab() {
        Tab tab = new Tab("Firmar XML con Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField("C:\\Windows\\System32\\eTPKCS11.dll");
        pkcs11LibPath.textProperty().bindBidirectional(configManager.pkcs11LibraryPathProperty());
        PasswordField password = createPasswordField("Contraseña del token");
        TextField slotNumber = createNumericTextField("0");
        slotNumber.textProperty().bindBidirectional(configManager.pkcs11SlotNumberProperty());
        TextField xmlPath = createTextField("C:\\Documentos\\factura.xml");
        TextField uri = createTextField("Opcional, por ejemplo: COD");
        OpenGeneratedDocumentButton opener = new OpenGeneratedDocumentButton(xmlPath);

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            configManager.setDefaultSlotNumber(slotNumber.getText());
            String uriValue = uri.getText() != null ? uri.getText().trim() : "";
            String xmlPathValue = xmlPath.getText();
            executeXMLSignerPKCS11(pkcs11LibPath.getText(), pass, slotNumber.getText(), xmlPathValue, uriValue,
                    exitCode -> opener.onExecutionResult(xmlPathValue, exitCode));
            clearInputFields(password, xmlPath, uri);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        grid.add(createDriverSelectorBox(pkcs11LibPath), 1, 1);
        addToGrid(grid, 2, "Contraseña:", password, null);
        addToGrid(grid, 3, "Número de Slot:", slotNumber, null);
        addToGrid(grid, 4, "Archivo XML:", xmlPath, browseXML);
        addButtonToRow(grid, 4, opener.button);
        addToGrid(grid, 5, "Elemento XML (ID) a Firmar:", uri, null);
        addButtonToRow(grid, 5, createFieldHelpButton("Elemento XML (ID) a Firmar", xmlElementHelpText));
        addExecuteButton(grid, execute, 6);

        VBox content = createTabContent(
                "Firma digitalmente un documento XML con la clave privada de un token criptográfico, usando "
                        + "SHA-256. Puede firmar el documento completo, o solo un elemento/párrafo específico "
                        + "identificado por su atributo Id= —una posibilidad general, válida para cualquier "
                        + "XML— que además es la que usan los documentos de comercio exterior ALADI/MERCOSUR "
                        + "(Certificados de Origen Digital, Declaraciones Juradas de Origen), cuyas firmas van "
                        + "embebidas sobre elementos puntuales del documento y no sobre el XML completo.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLSignerPKCS12Tab() {
        Tab tab = new Tab("Firmar XML con PKCS#12");
        GridPane grid = createStandardGridPane();

        TextField pkcs12Path = createTextField("C:\\Certificados\\certificado.pfx");
        pkcs12Path.textProperty().bindBidirectional(configManager.pkcs12FilePathProperty());
        PasswordField password = createPasswordField("Contraseña del archivo");
        TextField xmlPath = createTextField("C:\\Documentos\\factura.xml");
        TextField uri = createTextField("Opcional, por ejemplo: COD");
        OpenGeneratedDocumentButton opener = new OpenGeneratedDocumentButton(xmlPath);

        Button browsePKCS12 = createBrowseButton();
        browsePKCS12.setOnAction(e -> Platform.runLater(() -> selectPKCS12File(pkcs12Path)));

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            String uriValue = uri.getText() != null ? uri.getText().trim() : "";
            String xmlPathValue = xmlPath.getText();
            executeXMLSignerPKCS12(pkcs12Path.getText(), pass, xmlPathValue, uriValue,
                    exitCode -> opener.onExecutionResult(xmlPathValue, exitCode));
            clearInputFields(password, xmlPath, uri);
        }));

        addToGrid(grid, 0, "Archivo PKCS#12:", pkcs12Path, browsePKCS12);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Archivo XML:", xmlPath, browseXML);
        addButtonToRow(grid, 2, opener.button);
        addToGrid(grid, 3, "Elemento XML (ID) a Firmar:", uri, null);
        addButtonToRow(grid, 3, createFieldHelpButton("Elemento XML (ID) a Firmar", xmlElementHelpText));
        addExecuteButton(grid, execute, 4);

        VBox content = createTabContent(
                "Firma digitalmente un documento XML con SHA-256, igual que la versión con token, pero usando "
                        + "un archivo de certificado PKCS#12 (.p12/.pfx) en lugar de hardware — ideal para "
                        + "pruebas, automatización de servidor, o certificados que no requieren token físico. "
                        + "Admite la misma firma por elemento vía Id= que la versión con token.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLVerifySignaturesTab() {
        Tab tab = new Tab("Verificar Firmas en XML");
        GridPane grid = createStandardGridPane();

        TextField xmlPath = createTextField("C:\\Documentos\\factura.xml");
        CheckBox simpleOutput = new CheckBox("Salida simple");
        simpleOutput.selectedProperty().bindBidirectional(configManager.simpleOutputProperty());
        simpleOutput.setTooltip(new Tooltip("Mostrar salida simplificada del proceso de verificación"));

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            executeXMLVerifySignatures(xmlPath.getText(), simpleOutput.isSelected());
            clearInputFields(xmlPath);
        }));

        addToGrid(grid, 0, "Archivo XML:", xmlPath, browseXML);
        grid.add(simpleOutput, 1, 1);
        addExecuteButton(grid, execute, 2);

        VBox content = createTabContent(
                "Verifica la integridad criptográfica de las firmas de un XML y consulta el estado de "
                        + "revocación del certificado firmante (OCSP, con reintento por CRL) contra la fecha de "
                        + "firma — no la fecha actual, para no invalidar firmas antiguas hechas con un "
                        + "certificado hoy ya vencido. Admite firmas hechas con SHA-256 y también SHA-1 "
                        + "(compatibilidad con documentos antiguos).",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLVerifyXSDStructureTab() {
        Tab tab = new Tab("Verificar XML con XSD");
        GridPane grid = createStandardGridPane();

        TextField xmlPath = createTextField("C:\\Documentos\\factura.xml");
        TextField xsdPath = createTextField("Opcional: C:\\Esquemas\\esquema.xsd");

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button browseXSD = createBrowseButton();
        browseXSD.setOnAction(e -> Platform.runLater(() -> selectXSDFile(xsdPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            executeXMLVerifyXSDStructure(xmlPath.getText(), xsdPath.getText());
            clearInputFields(xmlPath, xsdPath);
        }));

        addToGrid(grid, 0, "Archivo XML:", xmlPath, browseXML);
        addToGrid(grid, 1, "Archivo XSD:", xsdPath, browseXSD);
        addExecuteButton(grid, execute, 2);

        VBox content = createTabContent(
                "Valida que un XML cumpla la estructura de su esquema XSD —externo o el indicado por el propio "
                        + "documento— y de paso verifica también sus firmas digitales. Para documentos "
                        + "ALADI/MERCOSUR usa el esquema oficial, con reintento automático por un dominio espejo "
                        + "si el sitio de ALADI no responde.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFSignerPKCS11Tab() {
        Tab tab = new Tab("Firmar PDF con Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField("C:\\Windows\\System32\\eTPKCS11.dll");
        pkcs11LibPath.textProperty().bindBidirectional(configManager.pkcs11LibraryPathProperty());
        PasswordField password = createPasswordField("Contraseña del token");
        TextField slotNumber = createNumericTextField("0");
        slotNumber.textProperty().bindBidirectional(configManager.pkcs11SlotNumberProperty());
        TextField pdfPath = createTextField("C:\\Documentos\\factura.pdf");
        TextField xPos = createNumericTextField("40");
        TextField yPos = createNumericTextField("55");
        xPos.setPrefWidth(190);
        yPos.setPrefWidth(190);
        HBox positionBox = new HBox(20);
        positionBox.getChildren().addAll(xPos, yPos);
        TextField customText = createTextField("Certificado de Origen");
        CheckBox lockDocument = new CheckBox("Bloquear documento después de firmar");
        OpenGeneratedDocumentButton opener = new OpenGeneratedDocumentButton(pdfPath);

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button browsePDF = createBrowseButton();
        browsePDF.setOnAction(e -> Platform.runLater(() -> selectPDFFile(pdfPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            String pdfPathValue = pdfPath.getText();
            executePDFSignerPKCS11(
                    pkcs11LibPath.getText(),
                    pass,
                    slotNumber.getText(),
                    pdfPathValue,
                    xPos.getText(),
                    yPos.getText(),
                    customText.getText(),
                    lockDocument.isSelected(),
                    exitCode -> opener.onExecutionResult(pdfPathValue, exitCode)
            );
            clearInputFields(password, pdfPath, xPos, yPos, customText);
            lockDocument.setSelected(false);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        grid.add(createDriverSelectorBox(pkcs11LibPath), 1, 1);
        addToGrid(grid, 2, "Contraseña:", password, null);
        addToGrid(grid, 3, "Número de Slot:", slotNumber, null);
        addToGrid(grid, 4, "Archivo PDF:", pdfPath, browsePDF);
        addButtonToRow(grid, 4, opener.button);
        addToGrid(grid, 5, "Posición (X,Y):", positionBox, null);
        addButtonToRow(grid, 5, createFieldHelpButton("Posición de la firma visible (X, Y)", pdfPositionHelpText));
        addToGrid(grid, 6, "Texto personalizado:", customText, null);
        grid.add(lockDocument, 1, 7);
        addExecuteButton(grid, execute, 8);

        VBox content = createTabContent(
                "Firma digitalmente un documento PDF con la clave privada de un token criptográfico, usando "
                        + "SHA-256. La firma puede ser invisible o mostrarse en un recuadro con firmante, fecha "
                        + "y texto personalizado en coordenadas específicas de la primera página, y opcionalmente "
                        + "puede bloquear el documento contra modificaciones posteriores (certificación + cifrado).",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFSignerPKCS12Tab() {
        Tab tab = new Tab("Firmar PDF con PKCS#12");
        GridPane grid = createStandardGridPane();

        TextField pkcs12Path = createTextField("C:\\Certificados\\certificado.pfx");
        pkcs12Path.textProperty().bindBidirectional(configManager.pkcs12FilePathProperty());
        PasswordField password = createPasswordField("Contraseña del archivo");
        TextField pdfPath = createTextField("C:\\Documentos\\factura.pdf");
        TextField xPos = createNumericTextField("40");
        TextField yPos = createNumericTextField("55");
        xPos.setPrefWidth(190);
        yPos.setPrefWidth(190);
        HBox positionBox = new HBox(20);
        positionBox.getChildren().addAll(xPos, yPos);
        TextField customText = createTextField("Certificado de Origen");
        CheckBox lockDocument = new CheckBox("Bloquear documento después de firmar");
        OpenGeneratedDocumentButton opener = new OpenGeneratedDocumentButton(pdfPath);

        Button browsePKCS12 = createBrowseButton();
        browsePKCS12.setOnAction(e -> Platform.runLater(() -> selectPKCS12File(pkcs12Path)));

        Button browsePDF = createBrowseButton();
        browsePDF.setOnAction(e -> Platform.runLater(() -> selectPDFFile(pdfPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            String pdfPathValue = pdfPath.getText();
            executePDFSignerPKCS12(
                    pkcs12Path.getText(),
                    pass,
                    pdfPathValue,
                    xPos.getText(),
                    yPos.getText(),
                    customText.getText(),
                    lockDocument.isSelected(),
                    exitCode -> opener.onExecutionResult(pdfPathValue, exitCode)
            );
            clearInputFields(password, pdfPath, xPos, yPos, customText);
            lockDocument.setSelected(false);
        }));

        addToGrid(grid, 0, "Archivo PKCS#12:", pkcs12Path, browsePKCS12);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Archivo PDF:", pdfPath, browsePDF);
        addButtonToRow(grid, 2, opener.button);
        addToGrid(grid, 3, "Posición (X,Y):", positionBox, null);
        addButtonToRow(grid, 3, createFieldHelpButton("Posición de la firma visible (X, Y)", pdfPositionHelpText));
        addToGrid(grid, 4, "Texto personalizado:", customText, null);
        grid.add(lockDocument, 1, 5);
        addExecuteButton(grid, execute, 6);

        VBox content = createTabContent(
                "Firma digitalmente un documento PDF con SHA-256, igual que la versión con token, pero usando "
                        + "un archivo de certificado PKCS#12 (.p12/.pfx) — ideal para pruebas, automatización de "
                        + "servidor, o certificados que no requieren hardware.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFVerifySignaturesTab() {
        Tab tab = new Tab("Verificar Firmas en PDF");
        GridPane grid = createStandardGridPane();

        TextField pdfPath = createTextField("C:\\Documentos\\factura.pdf");
        CheckBox simpleOutput = new CheckBox("Salida simple");
        simpleOutput.selectedProperty().bindBidirectional(configManager.simpleOutputProperty());
        simpleOutput.setTooltip(new Tooltip("Mostrar salida simplificada del proceso de verificación"));

        Button browsePDF = createBrowseButton();
        browsePDF.setOnAction(e -> Platform.runLater(() -> selectPDFFile(pdfPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            executePDFVerifySignatures(pdfPath.getText(), simpleOutput.isSelected());
            clearInputFields(pdfPath);
        }));

        addToGrid(grid, 0, "Archivo PDF:", pdfPath, browsePDF);
        grid.add(simpleOutput, 1, 1);
        addExecuteButton(grid, execute, 2);

        VBox content = createTabContent(
                "Verifica la integridad criptográfica de cada firma de un PDF, si cubre todo el documento o "
                        + "solo una versión anterior, si está bloqueado/cifrado, y el estado de revocación del "
                        + "certificado firmante (OCSP con reintento por CRL) contra la fecha de firma. Admite "
                        + "firmas hechas con SHA-256 y también SHA-1 (compatibilidad con documentos antiguos).",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private void executeTokenSlotsView(String libPath, String password) {
        try {
            validateRequiredField("biblioteca PKCS#11", libPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("TokenSlotsView");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {libPath, password};
                GUIUtils.executeCommand("TokenSlotsView", args, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al ejecutar el visor de slots", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executeTokenCertExtractor(String libPath, String password, String slotNumber) {
        try {
            validateRequiredField("biblioteca PKCS#11", libPath);
            validateRequiredField("número de slot", slotNumber);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("TokenCertificateExtractor");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {libPath, password, slotNumber};
                GUIUtils.executeCommand("TokenCertificateExtractor", args, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al extraer certificados del token", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executePKCS12CertExtractor(String pkcs12Path, String password) {
        try {
            validateRequiredField("archivo PKCS#12", pkcs12Path);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("PKCS12CertificateExtractor");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {pkcs12Path, password};
                GUIUtils.executeCommand("PKCS12CertificateExtractor", args, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al extraer certificados PKCS#12", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executeXMLSignerPKCS11(String libPath, String password,
                                        String slotNumber, String xmlPath, String uri,
                                        IntConsumer onExit) {
        try {
            validateRequiredField("biblioteca PKCS#11", libPath);
            validateRequiredField("número de slot", slotNumber);
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLSignerPKCS11");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {libPath, password, slotNumber, xmlPath, uri};
                GUIUtils.executeCommand("XMLSignerPKCS11", args, sharedOutputArea, onExit);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al firmar XML con Token", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executeXMLSignerPKCS12(String pkcs12Path, String password, String xmlPath, String uri,
                                         IntConsumer onExit) {
        try {
            validateRequiredField("archivo PKCS#12", pkcs12Path);
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLSignerPKCS12");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] checkArgs = {"-verificar-revocacion", pkcs12Path, password};
                String[] signArgs = {pkcs12Path, password, xmlPath, uri};
                GUIUtils.executeSignCommandWithRevocationCheck("XMLSignerPKCS12", checkArgs, signArgs, sharedOutputArea, onExit);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al firmar XML con PKCS#12", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executeXMLVerifySignatures(String xmlPath, boolean simpleOutput) {
        try {
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLVerifySignatures");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = simpleOutput ?
                        new String[]{xmlPath, "-simple"} :
                        new String[]{xmlPath};
                GUIUtils.executeCommand("XMLVerifySignatures", args, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al verificar firmas XML", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executeXMLVerifyXSDStructure(String xmlPath, String xsdPath) {
        try {
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLVerifyXSDStructure");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args;
                if (xsdPath != null && !xsdPath.trim().isEmpty()) {
                    args = new String[]{xmlPath, xsdPath};
                } else {
                    args = new String[]{xmlPath};
                }
                GUIUtils.executeCommand("XMLVerifyXSDStructure", args, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al verificar estructura XSD", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executePDFSignerPKCS11(
            String libPath,
            String password,
            String slotNumber,
            String pdfPath,
            String xPos,
            String yPos,
            String customText,
            boolean lock,
            IntConsumer onExit) {
        try {
            validateRequiredField("biblioteca PKCS#11", libPath);
            validateRequiredField("número de slot", slotNumber);
            validateRequiredField("archivo PDF", pdfPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("PDFSignerPKCS11");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {
                        "-i", pdfPath,
                        "-l", libPath,
                        "-p", password,
                        "-s", slotNumber,
                        "-x", xPos,
                        "-y", yPos,
                        "-k", String.valueOf(lock)
                };

                if (customText != null && !customText.trim().isEmpty()) {
                    args = Arrays.copyOf(args, args.length + 2);
                    args[args.length - 2] = "-t";
                    args[args.length - 1] = customText;
                }

                GUIUtils.executeCommand("PDFSignerPKCS11", args, sharedOutputArea, onExit);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al firmar PDF con Token", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executePDFSignerPKCS12(
            String pkcs12Path,
            String password,
            String pdfPath,
            String xPos,
            String yPos,
            String customText,
            boolean lock,
            IntConsumer onExit) {
        try {
            validateRequiredField("archivo PKCS#12", pkcs12Path);
            validateRequiredField("archivo PDF", pdfPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("PDFSignerPKCS12");

            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {
                        "-i", pdfPath,
                        "-c", pkcs12Path,
                        "-p", password,
                        "-x", xPos,
                        "-y", yPos,
                        "-l", String.valueOf(lock)
                };

                if (customText != null && !customText.trim().isEmpty()) {
                    args = Arrays.copyOf(args, args.length + 2);
                    args[args.length - 2] = "-t";
                    args[args.length - 1] = customText;
                }

                String[] checkArgs = {"-verificar-revocacion", pkcs12Path, password};
                GUIUtils.executeSignCommandWithRevocationCheck("PDFSignerPKCS12", checkArgs, args, sharedOutputArea, onExit);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al firmar PDF con PKCS#12", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executePDFVerifySignatures(String pdfPath, boolean simpleOutput) {
        try {
            validateRequiredField("archivo PDF", pdfPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("PDFVerifySignatures");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = simpleOutput ?
                        new String[]{pdfPath, "-simple"} :
                        new String[]{pdfPath};
                GUIUtils.executeCommand("PDFVerifySignatures", args, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al verificar firmas PDF", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private boolean isWindowsOS() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private Tab createWindowsCertificateStoreViewTab() {
        Tab tab = new Tab("Ver Certificados de Windows");
        GridPane grid = createStandardGridPane();

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(this::executeWindowsCertificateStoreView));

        // No usa addToGrid()/addExecuteButton(): esas dos trabajan juntas asumiendo
        // que ya existe un campo con botón "Examinar..." en la fila 0 (ahí es donde
        // addExecuteButton() en realidad agrega el botón "Ejecutar", sin importar
        // qué número de fila se le pase). Esta pestaña no tiene ningún campo, así
        // que se agrega el botón directo al grid.
        grid.add(execute, 0, 0);

        VBox content = createTabContent(
                "Muestra el contenido del almacén \"Personal\" de certificados de Windows: alias, sujeto (CN), "
                        + "emisor, vigencia, número de serie y si tiene clave privada — el mismo rol que "
                        + "\"Ver Slots de Token\" cumple para un token PKCS#11, pero para el almacén de Windows. "
                        + "Úselo antes de firmar con \"Firmar XML/PDF con Windows CSP/KSP\" si no conoce con "
                        + "exactitud el alias o el nombre (CN) del certificado que necesita. No requiere "
                        + "contraseña ni configuración alguna.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private void executeWindowsCertificateStoreView() {
        try {
            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("WindowsCertificateStoreView");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                GUIUtils.executeCommand("WindowsCertificateStoreView", new String[0], sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (Exception e) {
            handleError("Error al listar el almacén de certificados de Windows", e);
        }
    }

    private Tab createXMLSignerWindowsCSPTab() {
        Tab tab = new Tab("Firmar XML con Windows CSP/KSP");
        GridPane grid = createStandardGridPane();

        TextField aliasField = createTextField("Juan Pérez");
        aliasField.textProperty().bindBidirectional(configManager.windowsCertAliasProperty());
        TextField xmlPath = createTextField("C:\\Documentos\\factura.xml");
        TextField uri = createTextField("Opcional, por ejemplo: COD");
        OpenGeneratedDocumentButton opener = new OpenGeneratedDocumentButton(xmlPath);

        Button listCerts = new Button("Ver certificados");
        listCerts.setTooltip(new Tooltip("Lista los certificados disponibles en el almacén de Windows"));
        listCerts.setOnAction(e -> Platform.runLater(() -> executeListCertificadosWindows("XMLSignerWindowsCSP")));

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String uriValue = uri.getText() != null ? uri.getText().trim() : "";
            String xmlPathValue = xmlPath.getText();
            executeXMLSignerWindowsCSP(aliasField.getText(), xmlPathValue, uriValue,
                    exitCode -> opener.onExecutionResult(xmlPathValue, exitCode));
            clearInputFields(xmlPath, uri);
        }));

        addToGrid(grid, 0, "Alias / Nombre (CN):", aliasField, listCerts);
        addToGrid(grid, 1, "Archivo XML:", xmlPath, browseXML);
        addButtonToRow(grid, 1, opener.button);
        addToGrid(grid, 2, "Elemento XML (ID) a Firmar:", uri, null);
        addButtonToRow(grid, 2, createFieldHelpButton("Elemento XML (ID) a Firmar", xmlElementHelpText));
        addExecuteButton(grid, execute, 3);

        VBox content = createTabContent(
                "Firma usando un certificado ya presente en el almacén de certificados de Windows (CSP/KSP), sin "
                        + "necesitar configurar una librería PKCS#11. Es más simple si el certificado ya aparece en "
                        + "el Administrador de certificados de Windows, pero queda atado a Windows (no es portable "
                        + "a Linux/macOS). Para la mayoría de los casos, PKCS#11 (pestaña \"Firmar XML con Token\") "
                        + "sigue siendo la opción recomendada por ser estándar y multiplataforma; use esta "
                        + "alternativa si prefiere la integración nativa de Windows.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFSignerWindowsCSPTab() {
        Tab tab = new Tab("Firmar PDF con Windows CSP/KSP");
        GridPane grid = createStandardGridPane();

        TextField aliasField = createTextField("Juan Pérez");
        aliasField.textProperty().bindBidirectional(configManager.windowsCertAliasProperty());
        TextField pdfPath = createTextField("C:\\Documentos\\factura.pdf");
        TextField xPos = createNumericTextField("40");
        TextField yPos = createNumericTextField("55");
        xPos.setPrefWidth(190);
        yPos.setPrefWidth(190);
        HBox positionBox = new HBox(20);
        positionBox.getChildren().addAll(xPos, yPos);
        TextField customText = createTextField("Certificado de Origen");
        CheckBox lockDocument = new CheckBox("Bloquear documento después de firmar");
        OpenGeneratedDocumentButton opener = new OpenGeneratedDocumentButton(pdfPath);

        Button listCerts = new Button("Ver certificados");
        listCerts.setTooltip(new Tooltip("Lista los certificados disponibles en el almacén de Windows"));
        listCerts.setOnAction(e -> Platform.runLater(() -> executeListCertificadosWindows("PDFSignerWindowsCSP")));

        Button browsePDF = createBrowseButton();
        browsePDF.setOnAction(e -> Platform.runLater(() -> selectPDFFile(pdfPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pdfPathValue = pdfPath.getText();
            executePDFSignerWindowsCSP(
                    aliasField.getText(),
                    pdfPathValue,
                    xPos.getText(),
                    yPos.getText(),
                    customText.getText(),
                    lockDocument.isSelected(),
                    exitCode -> opener.onExecutionResult(pdfPathValue, exitCode)
            );
            clearInputFields(pdfPath, xPos, yPos, customText);
            lockDocument.setSelected(false);
        }));

        addToGrid(grid, 0, "Alias / Nombre (CN):", aliasField, listCerts);
        addToGrid(grid, 1, "Archivo PDF:", pdfPath, browsePDF);
        addButtonToRow(grid, 1, opener.button);
        addToGrid(grid, 2, "Posición (X,Y):", positionBox, null);
        addButtonToRow(grid, 2, createFieldHelpButton("Posición de la firma visible (X, Y)", pdfPositionHelpText));
        addToGrid(grid, 3, "Texto personalizado:", customText, null);
        grid.add(lockDocument, 1, 4);
        addExecuteButton(grid, execute, 5);

        VBox content = createTabContent(
                "Firma usando un certificado ya presente en el almacén de certificados de Windows (CSP/KSP), sin "
                        + "necesitar configurar una librería PKCS#11. Es más simple si el certificado ya aparece en "
                        + "el Administrador de certificados de Windows, pero queda atado a Windows (no es portable "
                        + "a Linux/macOS). Para la mayoría de los casos, PKCS#11 (pestaña \"Firmar PDF con Token\") "
                        + "sigue siendo la opción recomendada por ser estándar y multiplataforma; use esta "
                        + "alternativa si prefiere la integración nativa de Windows.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private void executeListCertificadosWindows(String jarName) {
        try {
            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile(jarName);
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String flag = "PDFSignerWindowsCSP".equals(jarName) ? "--listar-certificados" : "-listar-certificados";
                GUIUtils.executeCommand(jarName, new String[]{flag}, sharedOutputArea);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (Exception e) {
            handleError("Error al listar certificados del almacén de Windows", e);
        }
    }

    private void executeXMLSignerWindowsCSP(String alias, String xmlPath, String uri, IntConsumer onExit) {
        try {
            validateRequiredField("alias o nombre del certificado", alias);
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLSignerWindowsCSP");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] checkArgs = {"-verificar-revocacion", alias};
                String[] signArgs = {alias, xmlPath, uri};
                GUIUtils.executeSignCommandWithRevocationCheck("XMLSignerWindowsCSP", checkArgs, signArgs, sharedOutputArea, onExit);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al firmar XML con el almacén de Windows", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

    private void executePDFSignerWindowsCSP(
            String alias,
            String pdfPath,
            String xPos,
            String yPos,
            String customText,
            boolean lock,
            IntConsumer onExit) {
        try {
            validateRequiredField("alias o nombre del certificado", alias);
            validateRequiredField("archivo PDF", pdfPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("PDFSignerWindowsCSP");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {
                        "-i", pdfPath,
                        "-a", alias,
                        "-x", xPos,
                        "-y", yPos,
                        "-k", String.valueOf(lock)
                };

                if (customText != null && !customText.trim().isEmpty()) {
                    args = Arrays.copyOf(args, args.length + 2);
                    args[args.length - 2] = "-t";
                    args[args.length - 1] = customText;
                }

                String[] checkArgs = {"-verificar-revocacion", alias};
                GUIUtils.executeSignCommandWithRevocationCheck("PDFSignerWindowsCSP", checkArgs, args, sharedOutputArea, onExit);
            } else {
                Platform.runLater(() -> ModuleValidator.showValidationError(result));
            }
        } catch (IllegalArgumentException e) {
            handleError(e.getMessage(), e);
        } catch (Exception e) {
            handleError("Error al firmar PDF con el almacén de Windows", e);
            Platform.runLater(() -> sharedOutputArea.appendText("\nError: " + e.getMessage()));
        }
    }

}