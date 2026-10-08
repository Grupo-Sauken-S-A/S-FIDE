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
import java.util.Optional;
import java.util.Properties;

/**
 * Valores por defecto recordados entre sesiones de S-FiDE GUI, persistidos en
 * sfide-defaults.properties dentro de la carpeta PERSONAL del usuario (ver
 * {@link UserDataDirectory}) — no en la carpeta de instalación, para que cada
 * usuario de un equipo compartido tenga los suyos. Las rutas/valores expuestos
 * como Property son compartidos por todos los módulos que los usan: dos campos
 * distintos (en distintos módulos del panel lateral) atados a la misma
 * Property quedan sincronizados entre sí en vivo, no solo al reiniciar la
 * aplicación.
 * <p>
 * El archivo lleva dos claves de control: {@code config.schema} (entero, la
 * versión del FORMATO — es la que dispara migraciones) y {@code app.version}
 * (la versión de S-FiDE que lo escribió por última vez, informativa). Como el
 * archivo vive fuera de la instalación, actualizar S-FiDE nunca lo toca; es
 * este programa, al arrancar, quien lo adapta si lo encuentra más viejo.
 * <p>
 * Deliberadamente NUNCA se persiste ninguna contraseña.
 */
public class ConfigurationManager {
    private static final String CONFIG_FILE_NAME = "sfide-defaults.properties";

    /**
     * Versión actual del formato del archivo. Historial:
     * 1 = S-FiDE hasta 1.3.0 (sin claves de control, archivo junto a la instalación);
     * 2 = S-FiDE 1.4.0 (carpeta personal del usuario, claves de control, últimas carpetas usadas).
     * Al subir este número, agregar el paso correspondiente en {@link #upgradeSchema(int)}.
     */
    static final int CURRENT_SCHEMA = 2;
    private static final String SCHEMA_KEY = "config.schema";
    private static final String APP_VERSION_KEY = "app.version";
    /** Última versión cuyas novedades ya se le mostraron a este usuario. */
    private static final String WHATS_NEW_KEY = "novedades.vistas";

    /**
     * Claves del archivo de la instalación (versiones anteriores) que NO se
     * importan al migrar: son del usuario que las generó, no de quien migra —
     * un usuario que nunca recibió los accesos directos debe recibirlos igual.
     */
    private static final String[] NOT_MIGRATED_KEYS = {"desktop.shortcut.created", "doc.shortcuts.created"};

    private static ConfigurationManager instance;
    private final Path configFilePath;
    private final Properties properties;
    private String versionAlIniciar;
    private boolean usuarioNuevo;
    private boolean configuracionDeVersionMasNueva;
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
     * sfide-defaults.properties vive en la carpeta personal del usuario, no
     * junto a la instalación: con una instalación compartida por varios
     * usuarios (a la vez o por turnos), un archivo único pisaba los valores
     * de uno con los de otro, y exigía permiso de escritura sobre la carpeta
     * de instalación.
     */
    private static Path resolveConfigFilePath() {
        return UserDataDirectory.resolve(CONFIG_FILE_NAME);
    }

    /** El archivo que usaban las versiones anteriores, junto a la instalación (puede no existir). */
    private static Path legacyConfigFilePath() {
        Path installDir = AppInfo.installDir();
        return installDir == null ? null : installDir.resolve(CONFIG_FILE_NAME);
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
        boolean existed = Files.exists(configFilePath);
        boolean migratedFromInstallation = false;

        if (existed) {
            loadInto(properties, configFilePath);
            System.out.println("Configuración cargada exitosamente desde: " + configFilePath);
        } else {
            migratedFromInstallation = importLegacyConfiguration();
            if (!migratedFromInstallation) {
                System.out.println("Archivo de configuración no encontrado. Se utilizarán valores vacíos por defecto.");
            }
        }

        // Antes de que este arranque pise "app.version" con la versión actual, se recuerda con qué versión
        // se usó S-FiDE la vez anterior: es lo que permite avisarle al usuario qué cambió (ver novedadesPendientes).
        versionAlIniciar = properties.getProperty(APP_VERSION_KEY);
        usuarioNuevo = !existed && !migratedFromInstallation;

        // Un archivo sin config.schema es de una versión anterior a 1.4.0 (esquema 1).
        int storedSchema = parseSchema(properties.getProperty(SCHEMA_KEY), (existed || migratedFromInstallation) ? 1 : CURRENT_SCHEMA);
        boolean changed = false;
        if (storedSchema < CURRENT_SCHEMA) {
            upgradeSchema(storedSchema);
            changed = true;
        } else if (storedSchema > CURRENT_SCHEMA) {
            // Archivo escrito por una S-FiDE más nueva (el usuario volvió a una instalación
            // vieja): se respeta tal cual y no se rebaja el esquema, para no perder sus claves.
            System.out.println("Aviso: la configuración fue escrita por una versión más nueva de S-FiDE (esquema "
                    + storedSchema + "); se conserva sin cambios.");
            configuracionDeVersionMasNueva = true;
            return;
        }
        if (!AppInfo.version().equals(properties.getProperty(APP_VERSION_KEY))) {
            properties.setProperty(APP_VERSION_KEY, AppInfo.version());
            changed = true;
        }
        if (changed || migratedFromInstallation) {
            properties.setProperty(SCHEMA_KEY, String.valueOf(CURRENT_SCHEMA));
            saveConfiguration();
        }
    }

    /** Qué novedades hay que mostrar en este arranque: si corresponde avisar y desde qué versión viene el usuario. */
    public record NovedadesPendientes(boolean avisar, Optional<String> desde) {
    }

    /**
     * Decide si hay que avisarle al usuario que su versión cambió. Por usuario, no por instalación: cada
     * persona lo ve la primera vez que abre una versión nueva.
     * <ul>
     *   <li>Usuario nuevo (sin configuración propia ni heredada): no hay nada que contar.</li>
     *   <li>Si ya se le mostraron las novedades de alguna versión, se parte de esa.</li>
     *   <li>Si no, se parte de la versión con la que usó S-FiDE la última vez; y si no se sabe (viene de una
     *   versión anterior a 1.4.0, que no la anotaba), se parte de "desconocida": se le muestra todo el historial.</li>
     * </ul>
     * Quién decide qué novedades corresponden entre esas dos versiones, saltos incluidos, es {@code ReleaseNotes}.
     */
    static NovedadesPendientes decidirNovedades(boolean usuarioNuevo, String vistas, String versionAlIniciar,
                                                String versionActual) {
        if (versionActual == null || versionActual.isBlank() || "desconocida".equals(versionActual)) {
            return new NovedadesPendientes(false, Optional.empty());
        }
        Optional<String> desde;
        if (vistas != null && !vistas.isBlank()) {
            desde = Optional.of(vistas.trim());
        } else if (usuarioNuevo) {
            return new NovedadesPendientes(false, Optional.empty());
        } else {
            desde = versionAlIniciar == null || versionAlIniciar.isBlank()
                    ? Optional.empty() : Optional.of(versionAlIniciar.trim());
        }
        return new NovedadesPendientes(!versionActual.equals(desde.orElse(null)), desde);
    }

    public NovedadesPendientes novedadesPendientes() {
        return decidirNovedades(usuarioNuevo, properties.getProperty(WHATS_NEW_KEY), versionAlIniciar,
                AppInfo.version());
    }

    /** Anota que a este usuario ya se le mostraron las novedades de la versión instalada. */
    public synchronized void marcarNovedadesVistas() {
        String actual = AppInfo.version();
        if (configuracionDeVersionMasNueva || "desconocida".equals(actual)
                || actual.equals(properties.getProperty(WHATS_NEW_KEY))) {
            return;
        }
        properties.setProperty(WHATS_NEW_KEY, actual);
        saveConfiguration();
    }

    private static void loadInto(Properties target, Path file) {
        try {
            // Un archivo editado a mano con el Bloc de notas puede traer una marca de orden de bytes
            // (BOM) al inicio; sin quitarla, quedaría pegada al nombre de la primera clave.
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            target.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error al cargar la configuración (" + file + "): " + e.getMessage());
        }
    }

    /**
     * Primer arranque de un usuario con S-FiDE 1.4.0 o posterior: si la
     * instalación todavía tiene el sfide-defaults.properties de versiones
     * anteriores, se usa como punto de partida (rutas de biblioteca PKCS#11,
     * último módulo, etc.). El archivo original NO se borra ni se modifica:
     * otros usuarios del equipo todavía lo necesitan para migrar por su
     * cuenta. Los indicadores de accesos directos no se heredan (ver
     * {@link #NOT_MIGRATED_KEYS}).
     */
    private boolean importLegacyConfiguration() {
        Path legacy = legacyConfigFilePath();
        if (legacy == null || !Files.isRegularFile(legacy) || legacy.equals(configFilePath)) {
            return false;
        }
        loadInto(properties, legacy);
        for (String key : NOT_MIGRATED_KEYS) {
            properties.remove(key);
        }
        System.out.println("Se importó la configuración previa de la instalación (" + legacy
                + ") a la carpeta personal: " + configFilePath);
        return true;
    }

    private static int parseSchema(String value, int whenAbsent) {
        if (value == null || value.isBlank()) {
            return whenAbsent;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return whenAbsent;
        }
    }

    /**
     * Lleva la configuración almacenada, de a un paso por vez, hasta
     * {@link #CURRENT_SCHEMA}. Cada versión del formato que cambie el
     * significado o el nombre de una clave agrega acá su paso; las claves
     * nuevas que simplemente no existen todavía no necesitan paso (los
     * valores por defecto de cada Property ya cubren su ausencia).
     */
    private void upgradeSchema(int fromSchema) {
        int schema = fromSchema;
        while (schema < CURRENT_SCHEMA) {
            switch (schema) {
                case 1 -> {
                    // 1 -> 2: solo cambió DÓNDE vive el archivo y se sumaron claves de control y de
                    // últimas carpetas usadas; ninguna clave existente cambia de significado.
                }
                default -> {
                    // Sin paso definido: nada que transformar.
                }
            }
            schema++;
        }
        System.out.println("Configuración adaptada del esquema " + fromSchema + " al " + CURRENT_SCHEMA + ".");
    }

    /**
     * Guarda en disco de forma atómica: se escribe un archivo temporal en la
     * misma carpeta y recién entonces reemplaza al definitivo, así un corte
     * de luz o un cierre forzado a mitad de escritura nunca deja un archivo
     * truncado (que el siguiente arranque leería como "sin configuración").
     */
    public synchronized void saveConfiguration() {
        Path temp = configFilePath.resolveSibling(CONFIG_FILE_NAME + ".tmp");
        try {
            try (Writer output = new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                properties.store(output, "Configuración de S-FIDE GUI (propia de este usuario)");
            }
            try {
                Files.move(temp, configFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, configFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Configuración guardada exitosamente en: " + configFilePath);
        } catch (IOException e) {
            System.err.println("Error al guardar la configuración: " + e.getMessage());
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Nada más que hacer.
            }
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

    /**
     * Tipos de documento cuya última carpeta usada se recuerda. Se comparte
     * entre todas las pestañas del mismo tipo (por ejemplo, "Firmar XML con
     * token", "Verificar firmas en XML" y "Verificar XML con XSD" usan la
     * misma carpeta de XML): el documento firmado se guarda junto al
     * original, así que una sola carpeta por tipo alcanza para tomar y para
     * dejar los archivos.
     */
    public enum DirectoryKind {
        XML("last.dir.xml"),
        PDF("last.dir.pdf"),
        XSD("last.dir.xsd");

        private final String key;

        DirectoryKind(String key) {
            this.key = key;
        }
    }

    /**
     * La última carpeta usada para ese tipo de documento, o {@code null} si
     * nunca se usó o ya no existe (por ejemplo, un pendrive o una carpeta de
     * red que dejó de estar disponible) — en ese caso el selector de archivos
     * abre en su ubicación por defecto en vez de fallar.
     */
    public File getLastDirectory(DirectoryKind kind) {
        String value = properties.getProperty(kind.key, "");
        if (value.isBlank()) {
            return null;
        }
        File directory = new File(value);
        return directory.isDirectory() ? directory : null;
    }

    /**
     * Recuerda la carpeta de ese archivo (o la carpeta misma, si se pasa una
     * carpeta) como la última usada para ese tipo de documento. Se ignora si
     * no existe: nunca se guarda una ruta que no se pudo comprobar.
     */
    public void setLastDirectory(DirectoryKind kind, File location) {
        if (location == null) {
            return;
        }
        File directory = location.isDirectory() ? location : location.getAbsoluteFile().getParentFile();
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        String path = directory.getAbsolutePath();
        if (!path.equals(properties.getProperty(kind.key))) {
            properties.setProperty(kind.key, path);
            saveDebounce.playFromStart();
        }
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
