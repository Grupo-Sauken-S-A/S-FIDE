package com.sauken.s_fide.s_fide_gui.utils;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Duration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/**
 * Valores por defecto recordados entre sesiones de S-FiDE GUI, persistidos en
 * sfide-defaults.properties. Las rutas/valores expuestos como Property son
 * compartidos por todos los módulos que los usan: dos campos distintos
 * (en distintos módulos del panel lateral) atados a la misma Property quedan
 * sincronizados entre sí en vivo, no solo al reiniciar la aplicación.
 *
 * Deliberadamente NUNCA se persiste ninguna contraseña.
 */
public class ConfigurationManager {
    private static final String CONFIG_FILE_NAME = "sfide-defaults.properties";
    private static ConfigurationManager instance;
    private final Path configFilePath;
    private final Properties properties;
    private final PauseTransition saveDebounce;

    private final StringProperty pkcs11LibraryPath = new SimpleStringProperty("");
    private final StringProperty pkcs11SlotNumber = new SimpleStringProperty("");
    private final StringProperty pkcs12FilePath = new SimpleStringProperty("");
    private final StringProperty lastModule = new SimpleStringProperty("");
    private final StringProperty windowX = new SimpleStringProperty("");
    private final StringProperty windowY = new SimpleStringProperty("");
    private final StringProperty windowWidth = new SimpleStringProperty("");
    private final StringProperty windowHeight = new SimpleStringProperty("");
    private final BooleanProperty windowMaximized = new SimpleBooleanProperty(true);
    private final StringProperty windowsCertAlias = new SimpleStringProperty("");
    private final BooleanProperty simpleOutput = new SimpleBooleanProperty(true);

    private ConfigurationManager() {
        configFilePath = resolveConfigFilePath();
        properties = new Properties();
        saveDebounce = new PauseTransition(Duration.millis(400));
        saveDebounce.setOnFinished(e -> saveConfiguration());

        loadConfiguration();

        pkcs11LibraryPath.set(properties.getProperty("pkcs11.library.path", ""));
        pkcs11SlotNumber.set(properties.getProperty("pkcs11.slot.number", ""));
        pkcs12FilePath.set(properties.getProperty("pkcs12.file.path", ""));
        lastModule.set(properties.getProperty("last.module", ""));
        windowX.set(properties.getProperty("window.x", ""));
        windowY.set(properties.getProperty("window.y", ""));
        windowWidth.set(properties.getProperty("window.width", ""));
        windowHeight.set(properties.getProperty("window.height", ""));
        windowMaximized.set(Boolean.parseBoolean(properties.getProperty("window.maximized", "true")));
        windowsCertAlias.set(properties.getProperty("windows.cert.alias", ""));
        simpleOutput.set(Boolean.parseBoolean(properties.getProperty("simple.output", "true")));

        bindPersistence(pkcs11LibraryPath, "pkcs11.library.path");
        bindPersistence(pkcs11SlotNumber, "pkcs11.slot.number");
        bindPersistence(pkcs12FilePath, "pkcs12.file.path");
        bindPersistence(lastModule, "last.module");
        bindPersistence(windowX, "window.x");
        bindPersistence(windowY, "window.y");
        bindPersistence(windowWidth, "window.width");
        bindPersistence(windowHeight, "window.height");
        bindPersistence(windowMaximized, "window.maximized");
        bindPersistence(windowsCertAlias, "windows.cert.alias");
        bindPersistence(simpleOutput, "simple.output");
    }

    /**
     * Resuelve sfide-defaults.properties relativo a la carpeta de instalación
     * (la que contiene el jar en ejecución) — misma resolución ya usada por
     * los accesos directos y la apertura de documentación (ver SFideGUI:
     * createDocShortcuts()/openDocFile()) — en vez de relativo al directorio
     * de trabajo del proceso. SFide-GUI.bat/.sh y el acceso directo de
     * escritorio ya fijan el directorio de trabajo correcto, así que esto no
     * cambia el comportamiento en un uso normal; lo que corrige es el caso en
     * que la GUI se lance de otra forma (doble clic directo al jar, un acceso
     * directo mal armado, etc.), donde antes se leía/escribía el archivo en
     * el lugar equivocado en silencio.
     */
    private static Path resolveConfigFilePath() {
        try {
            Path installDir = Paths.get(
                    ConfigurationManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();
            if (installDir != null) {
                return installDir.resolve(CONFIG_FILE_NAME);
            }
        } catch (Exception ignored) {
            // Si no se puede determinar la carpeta de instalación, se cae al
            // directorio de trabajo actual (comportamiento anterior).
        }
        return Paths.get(CONFIG_FILE_NAME);
    }

    public static ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    private void bindPersistence(StringProperty property, String key) {
        property.addListener((observable, oldValue, newValue) -> {
            properties.setProperty(key, newValue == null ? "" : newValue);
            saveDebounce.playFromStart();
        });
    }

    private void bindPersistence(BooleanProperty property, String key) {
        property.addListener((observable, oldValue, newValue) -> {
            properties.setProperty(key, String.valueOf(newValue));
            saveDebounce.playFromStart();
        });
    }

    private void loadConfiguration() {
        if (Files.exists(configFilePath)) {
            try (Reader input = new InputStreamReader(Files.newInputStream(configFilePath), StandardCharsets.UTF_8)) {
                properties.load(input);
                System.out.println("Configuración cargada exitosamente desde: " + configFilePath);
            } catch (IOException e) {
                System.err.println("Error al cargar la configuración: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Archivo de configuración no encontrado. Se utilizarán valores vacíos por defecto.");
        }
    }

    public void saveConfiguration() {
        try (Writer output = new OutputStreamWriter(Files.newOutputStream(configFilePath), StandardCharsets.UTF_8)) {
            properties.store(output, "Configuración de S-FIDE GUI");
            System.out.println("Configuración guardada exitosamente en: " + configFilePath);
        } catch (IOException e) {
            System.err.println("Error al guardar la configuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public StringProperty pkcs11LibraryPathProperty() {
        return pkcs11LibraryPath;
    }

    public StringProperty pkcs11SlotNumberProperty() {
        return pkcs11SlotNumber;
    }

    public StringProperty pkcs12FilePathProperty() {
        return pkcs12FilePath;
    }

    public StringProperty lastModuleProperty() {
        return lastModule;
    }

    /**
     * "Salida simple" en los verificadores de XML/PDF — a diferencia de la
     * ruta de biblioteca/archivo o el alias, esta sí tiene sentido recordarla
     * entre sesiones: es una preferencia de cómo mostrar el resultado, no un
     * dato específico de una operación puntual (a diferencia de la posición
     * de firma o el bloqueo de documento, que el usuario siempre debe volver
     * a indicar — ver clearInputFields() en SFideGUI).
     */
    public BooleanProperty simpleOutputProperty() {
        return simpleOutput;
    }

    /**
     * Alias o fragmento del nombre (CN) del certificado del almacén de
     * Windows, compartido entre XMLSignerWindowsCSP y PDFSignerWindowsCSP —
     * mismo criterio que pkcs11LibraryPathProperty/pkcs12FilePathProperty:
     * un solo valor recordado y sincronizado en vivo entre ambas pestañas.
     */
    public StringProperty windowsCertAliasProperty() {
        return windowsCertAlias;
    }

    /**
     * Ruta de biblioteca PKCS#11 recordada específicamente para una marca/modelo
     * de token (independiente de la ruta "global" de pkcs11LibraryPathProperty).
     * Permite que, al volver a elegir ese mismo perfil en el combo, se recuerde
     * la ruta particular usada la última vez para él, en vez de siempre volver
     * a la ruta típica sugerida por el catálogo.
     */
    public String getLibraryPathForProfile(String profileKey) {
        return properties.getProperty("pkcs11.library.path.profile." + profileKey, "");
    }

    public void setLibraryPathForProfile(String profileKey, String path) {
        if (profileKey == null || profileKey.isBlank() || path == null || path.isBlank()) {
            return;
        }
        properties.setProperty("pkcs11.library.path.profile." + profileKey, path);
        saveDebounce.playFromStart();
    }

    public String getWindowX() {
        return windowX.get();
    }

    public String getWindowY() {
        return windowY.get();
    }

    public String getWindowWidth() {
        return windowWidth.get();
    }

    public String getWindowHeight() {
        return windowHeight.get();
    }

    public boolean isWindowMaximized() {
        return windowMaximized.get();
    }

    public void saveWindowBounds(double x, double y, double width, double height, boolean maximized) {
        windowX.set(String.valueOf(x));
        windowY.set(String.valueOf(y));
        windowWidth.set(String.valueOf(width));
        windowHeight.set(String.valueOf(height));
        windowMaximized.set(maximized);
        saveConfiguration();
    }

    // --- Compatibilidad con el nombrado anterior, usado en el resto de la GUI ---

    public String getDefaultPKCS11LibPath() {
        return pkcs11LibraryPath.get();
    }

    public String getDefaultSlotNumber() {
        return pkcs11SlotNumber.get();
    }

    public String getDefaultPKCS12Path() {
        return pkcs12FilePath.get();
    }

    public void setDefaultPKCS11LibPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            pkcs11LibraryPath.set(path);
        }
    }

    public void setDefaultSlotNumber(String number) {
        if (number != null && !number.trim().isEmpty()) {
            pkcs11SlotNumber.set(number);
        }
    }

    public void setDefaultPKCS12Path(String path) {
        if (path != null && !path.trim().isEmpty()) {
            pkcs12FilePath.set(path);
        }
    }

    /**
     * Recuerda si ya se intentó crear el acceso directo al escritorio (Windows)
     * en esta instalación de S-FiDE, para hacerlo una única vez — incluso si el
     * usuario borra el acceso directo después, no se vuelve a crear solo.
     */
    public boolean isDesktopShortcutCreated() {
        return Boolean.parseBoolean(properties.getProperty("desktop.shortcut.created", "false"));
    }

    public void setDesktopShortcutCreated(boolean created) {
        properties.setProperty("desktop.shortcut.created", String.valueOf(created));
        saveDebounce.playFromStart();
    }

    /**
     * Igual que isDesktopShortcutCreated()/setDesktopShortcutCreated(), pero
     * para los accesos directos a la documentación (guía de usuario y manual
     * técnico) — flag independiente porque una instalación que ya tenía
     * desktop.shortcut.created=true de antes de agregar esta función también
     * debe recibir estos accesos una vez.
     */
    public boolean isDocShortcutsCreated() {
        return Boolean.parseBoolean(properties.getProperty("doc.shortcuts.created", "false"));
    }

    public void setDocShortcutsCreated(boolean created) {
        properties.setProperty("doc.shortcuts.created", String.valueOf(created));
        saveDebounce.playFromStart();
    }
}
