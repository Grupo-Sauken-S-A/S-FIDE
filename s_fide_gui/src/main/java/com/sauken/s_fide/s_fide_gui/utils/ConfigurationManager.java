package com.sauken.s_fide.s_fide_gui.utils;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ConfigurationManager {
    private static final String CONFIG_FILE = "sfide-defaults.properties";
    private static ConfigurationManager instance;
    private Properties properties;

    private ConfigurationManager() {
        properties = new Properties();
        loadConfiguration();
    }

    public static ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    private void loadConfiguration() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
                System.out.println("Configuración cargada exitosamente desde: " + CONFIG_FILE);
            } catch (IOException e) {
                System.err.println("Error al cargar la configuración: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Archivo de configuración no encontrado. Se utilizarán valores vacíos por defecto.");
            createDefaultConfiguration();
        }
    }

    private void createDefaultConfiguration() {
        try (OutputStream output = Files.newOutputStream(Paths.get(CONFIG_FILE))) {
            properties.setProperty("pkcs11.library.path", "");
            properties.setProperty("pkcs11.slot.number", "");
            properties.setProperty("pkcs12.file.path", "");
            properties.store(output, "Configuración por defecto de S-FIDE GUI");
            System.out.println("Archivo de configuración por defecto creado: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error al crear la configuración por defecto: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveConfiguration() {
        try (OutputStream output = Files.newOutputStream(Paths.get(CONFIG_FILE))) {
            properties.store(output, "Configuración de S-FIDE GUI");
            System.out.println("Configuración guardada exitosamente en: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error al guardar la configuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getDefaultPKCS11LibPath() {
        return properties.getProperty("pkcs11.library.path", "");
    }

    public String getDefaultSlotNumber() {
        return properties.getProperty("pkcs11.slot.number", "");
    }

    public String getDefaultPKCS12Path() {
        return properties.getProperty("pkcs12.file.path", "");
    }

    public void setDefaultPKCS11LibPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            properties.setProperty("pkcs11.library.path", path);
            saveConfiguration();
        }
    }

    public void setDefaultSlotNumber(String number) {
        if (number != null && !number.trim().isEmpty()) {
            properties.setProperty("pkcs11.slot.number", number);
            saveConfiguration();
        }
    }

    public void setDefaultPKCS12Path(String path) {
        if (path != null && !path.trim().isEmpty()) {
            properties.setProperty("pkcs12.file.path", path);
            saveConfiguration();
        }
    }
}