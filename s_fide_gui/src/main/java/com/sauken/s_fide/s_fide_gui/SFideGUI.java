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
import java.util.Optional;
import javafx.scene.control.ButtonType;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.List;

public class SFideGUI extends Application {
    private static final String VERSION = "S-FIDE GUI v1.0.0 - Grupo Sauken S.A.";
    private static final String CSS_FILE = "css/styles.css";
    private static final String HELP_FILE = "text/HELP.txt";
    private static final String LICENSE_FILE = "text/LICENSE.txt";
    private static final String FAQ_FILE = "text/FAQ.txt";
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;
    private static final String ICON_FILE = "/images/sauken.png";

    private TextArea sharedOutputArea;
    private ExecutorService executorService;
    private Stage primaryStage;
    private String licenseText;
    private String helpText;
    private String faqText;
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

    private void clearInputFields(TextField... fields) {
        if (fields == null) return;

        Platform.runLater(() -> {
            for (TextField field : fields) {
                if (field != null) {
                    if (field.getText().equals(configManager.getDefaultPKCS11LibPath()) ||
                            field.getText().equals(configManager.getDefaultPKCS12Path()) ||
                            field.getText().equals(configManager.getDefaultSlotNumber())) {
                        continue;
                    }

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

        root.getChildren().addAll(
                createMenuBar(),
                createTabPane(),
                createOutputPane(),
                createControlBox()
        );

        Scene scene = createScene(root);
        configureStage(scene);

        System.out.println("Interfaz gráfica construida exitosamente");
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
            primaryStage.setTitle("S-FIDE - Sistema de Firma Digital Extendido");
            primaryStage.setScene(scene);

            try {
                InputStream iconStream = getClass().getResourceAsStream(ICON_FILE);
                if (iconStream == null) {
                    iconStream = getClass().getClassLoader().getResourceAsStream("/main/images/sauken.png");
                }
                if (iconStream != null) {
                    Image icon = new Image(iconStream);
                    primaryStage.getIcons().add(icon);
                    System.out.println("Ícono cargado exitosamente");
                } else {
                    System.err.println("No se pudo encontrar el archivo de ícono");
                }
            } catch (Exception e) {
                System.err.println("Error al cargar el ícono de la aplicación: " + e.getMessage());
            }

            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());
            primaryStage.setMaximized(true);

            primaryStage.setOnCloseRequest(windowEvent -> {
                windowEvent.consume();
                Platform.runLater(this::handleApplicationClose);
            });

            primaryStage.show();
        } catch (Exception e) {
            handleFatalError("Error al configurar la ventana principal", e);
        }
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

    private Button createBrowseButton() {
        Button button = new Button("Examinar...");
        button.getStyleClass().add("browse");
        button.setTooltip(new Tooltip("Seleccionar archivo"));
        return button;
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
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

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
            sharedOutputArea.setPrefRowCount(12);
        }
        else {
            sharedOutputArea.setPrefRowCount(15);
        }
        sharedOutputArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-control-inner-background: white;");
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");

        TitledPane outputPane = new TitledPane("Salida del Proceso", sharedOutputArea);
        outputPane.setCollapsible(false);
        if (isLowResolution()) {
            outputPane.setPrefHeight(200);
        }
        else {
            outputPane.setPrefHeight(250);
        }

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

    private void openWebsite() {
        try {
            Desktop.getDesktop().browse(new URI("https://www.sauken.com.ar/"));
            System.out.println("Sitio web abierto en el navegador");
        } catch (Exception e) {
            handleError("Error al abrir el sitio web", e);
        }
    }

    private Hyperlink createWebsiteLink() {
        Hyperlink link = new Hyperlink("https://www.sauken.com.ar");
        link.setOnAction(e -> Platform.runLater(this::openWebsite));
        return link;
    }

    private void configureInfoTextArea(TextArea textArea, int rows) {
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Segoe UI', sans-serif;");
        textArea.setPrefWidth(600);
        textArea.setPrefRowCount(rows);
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
                createWebsiteLink()
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
            TextArea quickGuideArea = new TextArea(helpText);
            configureInfoTextArea(quickGuideArea, 25);
            quickGuideTab.setContent(quickGuideArea);

            Tab faqTab = new Tab("Preguntas Frecuentes");
            TextArea faqArea = new TextArea(faqText);
            configureInfoTextArea(faqArea, 25);
            faqTab.setContent(faqArea);

            Tab contactTab = new Tab("Contacto");
            contactTab.setContent(createContactContent());

            tabPane.getTabs().addAll(quickGuideTab, faqTab, contactTab);

            alert.getDialogPane().setContent(tabPane);
            alert.getDialogPane().setPrefWidth(700);
            alert.getDialogPane().setPrefHeight(500);

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

    private TabPane createTabPane() {
        System.out.println("Creando panel de pestañas");
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabPane.getTabs().addAll(
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
        );

        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> {
                    if (newTab != null) {
                        System.out.println("Cambio a pestaña: " + newTab.getText());
                    }
                }
        );

        return tabPane;
    }

    private Tab createTokenSlotsViewTab() {
        Tab tab = new Tab("Ver Slots de Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField(
                "Ruta de la biblioteca PKCS#11",
                configManager.getDefaultPKCS11LibPath()
        );
        PasswordField password = createPasswordField("Contraseña del token");

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            executeTokenSlotsView(pkcs11LibPath.getText(), pass);
            clearInputFields(pkcs11LibPath, password);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addExecuteButton(grid, execute, 2);

        VBox content = createTabContent(
                "Este módulo permite visualizar los slots disponibles en un token criptográfico.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createTokenCertificateExtractorTab() {
        Tab tab = new Tab("Ver Certificado de Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField(
                "Ruta de la biblioteca PKCS#11",
                configManager.getDefaultPKCS11LibPath()
        );
        PasswordField password = createPasswordField("Contraseña del token");
        TextField slotNumber = createNumericTextField(
                "Número de slot",
                configManager.getDefaultSlotNumber()
        );

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            configManager.setDefaultSlotNumber(slotNumber.getText());
            executeTokenCertExtractor(pkcs11LibPath.getText(), pass, slotNumber.getText());
            clearInputFields(pkcs11LibPath, password, slotNumber);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Número de Slot:", slotNumber, null);
        addExecuteButton(grid, execute, 3);

        VBox content = createTabContent(
                "Este módulo permite extraer certificados digitales almacenados en un token criptográfico y generar un archivo .PEM con los mismos.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPKCS12CertificateExtractorTab() {
        Tab tab = new Tab("Ver Certificado de PKCS#12");
        GridPane grid = createStandardGridPane();

        TextField pkcs12Path = createTextField(
                "Ruta del archivo PKCS#12",
                configManager.getDefaultPKCS12Path()
        );
        PasswordField password = createPasswordField("Contraseña del archivo");

        Button browsePKCS12 = createBrowseButton();
        browsePKCS12.setOnAction(e -> Platform.runLater(() -> selectPKCS12File(pkcs12Path)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            executePKCS12CertExtractor(pkcs12Path.getText(), pass);
            clearInputFields(pkcs12Path, password);
        }));

        addToGrid(grid, 0, "Archivo PKCS#12:", pkcs12Path, browsePKCS12);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addExecuteButton(grid, execute, 2);

        VBox content = createTabContent(
                "Este módulo permite extraer certificados desde archivos PKCS#12 (.p12 o .pfx) y generar un archivo .PEM con los mismos.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLSignerPKCS11Tab() {
        Tab tab = new Tab("Firmar XML con Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField(
                "Ruta de la biblioteca PKCS#11",
                configManager.getDefaultPKCS11LibPath()
        );
        PasswordField password = createPasswordField("Contraseña del token");
        TextField slotNumber = createNumericTextField(
                "Número de slot",
                configManager.getDefaultSlotNumber()
        );
        TextField xmlPath = createTextField("Ruta del archivo XML");
        TextField uri = createTextField("Párrafo o elemento XML con ID (opcional)");

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            configManager.setDefaultSlotNumber(slotNumber.getText());
            String uriValue = uri.getText() != null ? uri.getText().trim() : "";
            executeXMLSignerPKCS11(pkcs11LibPath.getText(), pass, slotNumber.getText(), xmlPath.getText(), uriValue);
            clearInputFields(pkcs11LibPath, password, slotNumber, xmlPath, uri);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Número de Slot:", slotNumber, null);
        addToGrid(grid, 3, "Archivo XML:", xmlPath, browseXML);
        addToGrid(grid, 4, "Elemento XML (ID) a Firmar:", uri, null);
        addExecuteButton(grid, execute, 5);

        VBox content = createTabContent(
                "Este módulo permite firmar documentos XML usando un token criptográfico. Permite firmar un párrafo o elemento XML con un ID específico o bien todo el XML.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLSignerPKCS12Tab() {
        Tab tab = new Tab("Firmar XML con PKCS#12");
        GridPane grid = createStandardGridPane();

        TextField pkcs12Path = createTextField(
                "Ruta del archivo PKCS#12",
                configManager.getDefaultPKCS12Path()
        );
        PasswordField password = createPasswordField("Contraseña del archivo");
        TextField xmlPath = createTextField("Ruta del archivo XML");
        TextField uri = createTextField("Párrafo o elemento XML con ID (opcional)");

        Button browsePKCS12 = createBrowseButton();
        browsePKCS12.setOnAction(e -> Platform.runLater(() -> selectPKCS12File(pkcs12Path)));

        Button browseXML = createBrowseButton();
        browseXML.setOnAction(e -> Platform.runLater(() -> selectXMLFile(xmlPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            String uriValue = uri.getText() != null ? uri.getText().trim() : "";
            executeXMLSignerPKCS12(pkcs12Path.getText(), pass, xmlPath.getText(), uriValue);
            clearInputFields(pkcs12Path, password, xmlPath, uri);
        }));

        addToGrid(grid, 0, "Archivo PKCS#12:", pkcs12Path, browsePKCS12);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Archivo XML:", xmlPath, browseXML);
        addToGrid(grid, 3, "Elemento XML (ID) a Firmar:", uri, null);
        addExecuteButton(grid, execute, 4);

        VBox content = createTabContent(
                "Este módulo permite firmar documentos XML usando un archivo PKCS#12. Permite firmar un párrafo o elemento XML con un ID específico o bien todo el XML.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLVerifySignaturesTab() {
        Tab tab = new Tab("Verificar Firmas en XML");
        GridPane grid = createStandardGridPane();

        TextField xmlPath = createTextField("Ruta del archivo XML");
        CheckBox simpleOutput = new CheckBox("Salida simple");
        simpleOutput.setSelected(true);
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
                "Este módulo permite verificar validez de las firmas digitales en documentos XML.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createXMLVerifyXSDStructureTab() {
        Tab tab = new Tab("Verificar XML con XSD");
        GridPane grid = createStandardGridPane();

        TextField xmlPath = createTextField("Ruta del archivo XML");
        TextField xsdPath = createTextField("Ruta del archivo XSD (opcional)");

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
                "Este módulo permite verificar firmas digitales y la estructura de documentos XML contra un esquema XSD. Puede utilizar un esquema XSD externo o el referenciado por el mismo XML.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFSignerPKCS11Tab() {
        Tab tab = new Tab("Firmar PDF con Token");
        GridPane grid = createStandardGridPane();

        TextField pkcs11LibPath = createTextField(
                "Ruta de la biblioteca PKCS#11",
                configManager.getDefaultPKCS11LibPath()
        );
        PasswordField password = createPasswordField("Contraseña del token");
        TextField slotNumber = createNumericTextField(
                "Número de slot",
                configManager.getDefaultSlotNumber()
        );
        TextField pdfPath = createTextField("Ruta del archivo PDF");
        TextField xPos = createNumericTextField("X");
        TextField yPos = createNumericTextField("Y");
        xPos.setPrefWidth(190);
        yPos.setPrefWidth(190);
        HBox positionBox = new HBox(20);
        positionBox.getChildren().addAll(xPos, yPos);
        TextField customText = createTextField("Texto personalizado (opcional)");
        CheckBox lockDocument = new CheckBox("Bloquear documento después de firmar");

        Button browseLib = createBrowseButton();
        browseLib.setOnAction(e -> Platform.runLater(() -> selectLibraryFile(pkcs11LibPath)));

        Button browsePDF = createBrowseButton();
        browsePDF.setOnAction(e -> Platform.runLater(() -> selectPDFFile(pdfPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            executePDFSignerPKCS11(
                    pkcs11LibPath.getText(),
                    pass,
                    slotNumber.getText(),
                    pdfPath.getText(),
                    xPos.getText(),
                    yPos.getText(),
                    customText.getText(),
                    lockDocument.isSelected()
            );
            clearInputFields(pkcs11LibPath, password, slotNumber, pdfPath, xPos, yPos, customText);
        }));

        addToGrid(grid, 0, "Biblioteca PKCS#11:", pkcs11LibPath, browseLib);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Número de Slot:", slotNumber, null);
        addToGrid(grid, 3, "Archivo PDF:", pdfPath, browsePDF);
        addToGrid(grid, 4, "Posición (X,Y):", positionBox, null);
        addToGrid(grid, 5, "Texto personalizado:", customText, null);
        grid.add(lockDocument, 1, 6);
        addExecuteButton(grid, execute, 7);

        VBox content = createTabContent(
                "Este módulo permite firmar documentos PDF usando un token criptográfico. Permite establecer una firma visible en coordenadas específicas.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFSignerPKCS12Tab() {
        Tab tab = new Tab("Firmar PDF con PKCS#12");
        GridPane grid = createStandardGridPane();

        TextField pkcs12Path = createTextField(
                "Ruta del archivo PKCS#12",
                configManager.getDefaultPKCS12Path()
        );
        PasswordField password = createPasswordField("Contraseña del archivo");
        TextField pdfPath = createTextField("Ruta del archivo PDF");
        TextField xPos = createNumericTextField("X");
        TextField yPos = createNumericTextField("Y");
        xPos.setPrefWidth(190);
        yPos.setPrefWidth(190);
        HBox positionBox = new HBox(20);
        positionBox.getChildren().addAll(xPos, yPos);
        TextField customText = createTextField("Texto personalizado (opcional)");
        CheckBox lockDocument = new CheckBox("Bloquear documento después de firmar");

        Button browsePKCS12 = createBrowseButton();
        browsePKCS12.setOnAction(e -> Platform.runLater(() -> selectPKCS12File(pkcs12Path)));

        Button browsePDF = createBrowseButton();
        browsePDF.setOnAction(e -> Platform.runLater(() -> selectPDFFile(pdfPath)));

        Button execute = createExecuteButton();
        execute.setOnAction(e -> Platform.runLater(() -> {
            String pass = password.getText();
            executePDFSignerPKCS12(
                    pkcs12Path.getText(),
                    pass,
                    pdfPath.getText(),
                    xPos.getText(),
                    yPos.getText(),
                    customText.getText(),
                    lockDocument.isSelected()
            );
            clearInputFields(pkcs12Path, password, pdfPath, xPos, yPos, customText);
        }));

        addToGrid(grid, 0, "Archivo PKCS#12:", pkcs12Path, browsePKCS12);
        addToGrid(grid, 1, "Contraseña:", password, null);
        addToGrid(grid, 2, "Archivo PDF:", pdfPath, browsePDF);
        addToGrid(grid, 3, "Posición (X,Y):", positionBox, null);
        addToGrid(grid, 4, "Texto personalizado:", customText, null);
        grid.add(lockDocument, 1, 5);
        addExecuteButton(grid, execute, 6);

        VBox content = createTabContent(
                "Este módulo permite firmar documentos PDF usando un archivo PKCS#12. Permite establecer una firma visible en coordenadas específicas.",
                grid
        );

        tab.setContent(content);
        return tab;
    }

    private Tab createPDFVerifySignaturesTab() {
        Tab tab = new Tab("Verificar Firmas en PDF");
        GridPane grid = createStandardGridPane();

        TextField pdfPath = createTextField("Ruta del archivo PDF");
        CheckBox simpleOutput = new CheckBox("Salida simple");
        simpleOutput.setSelected(true);
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
                "Este módulo permite verificar validez de las firmas digitales en documentos PDF.",
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
                                        String slotNumber, String xmlPath, String uri) {
        try {
            validateRequiredField("biblioteca PKCS#11", libPath);
            validateRequiredField("número de slot", slotNumber);
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLSignerPKCS11");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {libPath, password, slotNumber, xmlPath, uri};
                GUIUtils.executeCommand("XMLSignerPKCS11", args, sharedOutputArea);
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

    private void executeXMLSignerPKCS12(String pkcs12Path, String password, String xmlPath, String uri) {
        try {
            validateRequiredField("archivo PKCS#12", pkcs12Path);
            validateRequiredField("archivo XML", xmlPath);

            ModuleValidator.ValidationResult result = ModuleValidator.validateJarFile("XMLSignerPKCS12");
            if (result.valid()) {
                Platform.runLater(() -> sharedOutputArea.clear());
                String[] args = {pkcs12Path, password, xmlPath, uri};
                GUIUtils.executeCommand("XMLSignerPKCS12", args, sharedOutputArea);
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
            boolean lock) {
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

                GUIUtils.executeCommand("PDFSignerPKCS11", args, sharedOutputArea);
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
            boolean lock) {
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
                        "-k", String.valueOf(lock)
                };

                if (customText != null && !customText.trim().isEmpty()) {
                    args = Arrays.copyOf(args, args.length + 2);
                    args[args.length - 2] = "-t";
                    args[args.length - 1] = customText;
                }

                GUIUtils.executeCommand("PDFSignerPKCS12", args, sharedOutputArea);
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

}