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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Un paquete de actualización de S-FiDE ({@code S-FiDE-<versión>-actualizacion.zip}):
 * los archivos que reemplazan a los de la instalación (jars de los módulos,
 * lanzadores, documentación), más dos archivos de control:
 * <ul>
 *   <li>{@code update-manifest.properties}: {@code version}, y qué runtimes
 *   embebidos exige ({@code requires.java}, {@code requires.javafx}, nombres de
 *   carpeta dentro de la instalación). El paquete NO incluye el runtime de
 *   Java ni de JavaFX: si una versión futura necesitara uno distinto, esta
 *   comprobación lo detecta y manda a descargar la distribución completa en
 *   vez de dejar una instalación a medio actualizar.</li>
 *   <li>{@code update-files.sha256}: una línea por archivo ({@code <sha256>  <ruta>}),
 *   verificada tras extraer y antes de tocar la instalación.</li>
 * </ul>
 * La validación es deliberadamente estricta: el paquete solo puede crear o
 * reemplazar archivos de una lista cerrada de nombres/ubicaciones ({@link #isInstallable(String)}),
 * nunca fuera de la carpeta de instalación, nunca los datos del usuario
 * ni los runtimes.
 */
final class UpdatePackage {
    static final String MANIFEST_NAME = "update-manifest.properties";
    static final String CHECKSUMS_NAME = "update-files.sha256";

    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_TOTAL_BYTES = 600L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 300L * 1024 * 1024;

    private final Path zipPath;
    private final String version;
    private final Properties manifest;
    private final Map<String, String> expectedSha256;
    private final List<String> installableEntries;

    private UpdatePackage(Path zipPath, String version, Properties manifest,
                          Map<String, String> expectedSha256, List<String> installableEntries) {
        this.zipPath = zipPath;
        this.version = version;
        this.manifest = manifest;
        this.expectedSha256 = expectedSha256;
        this.installableEntries = installableEntries;
    }

    String version() {
        return version;
    }

    Path zipPath() {
        return zipPath;
    }

    /** Rutas relativas (con "/") de los archivos que instala este paquete, ordenadas con los lanzadores al final. */
    List<String> entries() {
        return installableEntries;
    }

    String expectedSha256(String entry) {
        return expectedSha256.get(entry);
    }

    /** Nombre de carpeta del runtime que exige el paquete (p. ej. "openjdk-23.0.1"), o null si no exige ninguno. */
    String requiredFolder(String key) {
        String v = manifest.getProperty(key, "").trim();
        return v.isEmpty() ? null : v;
    }

    /**
     * Abre y valida el paquete (sin tocar nada en disco): estructura del zip,
     * manifiesto, lista de archivos permitidos, tamaños y existencia de
     * un hash por cada archivo instalable.
     */
    static UpdatePackage open(Path zipPath) throws UpdateException {
        if (!Files.isRegularFile(zipPath)) {
            throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                    "No se encuentra el paquete de actualización: " + zipPath);
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Properties manifest = readProperties(zip, MANIFEST_NAME);
            String version = manifest.getProperty("version", "").trim();
            if (version.isEmpty()) {
                throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                        "El paquete de actualización no indica a qué versión corresponde (falta " + MANIFEST_NAME + ").");
            }
            Map<String, String> sha = readChecksums(zip);

            List<String> installable = new ArrayList<>();
            long total = 0;
            int count = 0;
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_ENTRIES) {
                    throw invalid("tiene demasiados archivos");
                }
                String name = normalize(entry.getName());
                long size = entry.getSize();
                if (size > MAX_ENTRY_BYTES) {
                    throw invalid("contiene un archivo demasiado grande (" + name + ")");
                }
                total += Math.max(size, 0);
                if (total > MAX_TOTAL_BYTES) {
                    throw invalid("es demasiado grande");
                }
                if (name.equals(MANIFEST_NAME) || name.equals(CHECKSUMS_NAME)) {
                    continue;
                }
                if (!isInstallable(name)) {
                    throw invalid("contiene un archivo que una actualización no puede instalar: " + name);
                }
                if (!sha.containsKey(name)) {
                    throw invalid("no trae el hash de verificación de " + name);
                }
                installable.add(name);
            }
            if (installable.isEmpty()) {
                throw invalid("no contiene ningún archivo para instalar");
            }
            installable.sort((a, b) -> {
                int byLauncher = Boolean.compare(isLauncher(a), isLauncher(b));
                return byLauncher != 0 ? byLauncher : a.compareTo(b);
            });
            return new UpdatePackage(zipPath, version, manifest, sha, List.copyOf(installable));
        } catch (IOException | RuntimeException e) {
            if (e instanceof RuntimeException re && re.getCause() instanceof UpdateException ue) {
                throw ue;
            }
            throw new UpdateException(UpdateException.Kind.INVALID_PACKAGE,
                    "El paquete de actualización está dañado o no es un archivo ZIP válido.", e);
        }
    }

    private static UpdateException invalid(String why) {
        return new UpdateException(UpdateException.Kind.INVALID_PACKAGE, "El paquete de actualización " + why + ".");
    }

    /** Barras normales, sin "./" inicial. Rechaza rutas absolutas, con ".." o con letra de unidad. */
    static String normalize(String rawName) throws UpdateException {
        String name = rawName.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.isEmpty() || name.startsWith("/") || name.contains("//")
                || name.matches("^[A-Za-z]:.*")) {
            throw invalid("contiene una ruta no válida (" + rawName + ")");
        }
        for (String part : name.split("/")) {
            if (part.equals("..") || part.equals(".") || part.isEmpty()) {
                throw invalid("contiene una ruta no válida (" + rawName + ")");
            }
        }
        return name;
    }

    /**
     * Lista cerrada de lo que una actualización puede instalar. En la raíz:
     * los jars de los módulos, los lanzadores, el ícono, los textos de
     * distribución y la plantilla de configuración; en {@code doc/}: la
     * documentación. Nunca {@code sfide-defaults.properties} (es del
     * usuario), ni las carpetas de runtime, ni {@code test/} o {@code xsd/}.
     */
    static boolean isInstallable(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!name.contains("/")) {
            return lower.endsWith(".jar") || lower.endsWith(".bat") || lower.endsWith(".sh")
                    || lower.endsWith(".ico")
                    || name.equals("Leeme.txt") || name.equals("LICENSE")
                    || name.equals("sfide-defaults.demo.properties");
        }
        if (name.startsWith("doc/")) {
            return lower.endsWith(".html") || lower.endsWith(".md") || lower.endsWith(".png")
                    || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                    || lower.endsWith(".svg") || lower.endsWith(".css") || lower.endsWith(".ico");
        }
        return false;
    }

    static boolean isLauncher(String name) {
        return name.equals("SFide-GUI.bat") || name.equals("SFide-GUI.sh");
    }

    private static Properties readProperties(ZipFile zip, String name) throws IOException, UpdateException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw invalid("no trae " + name);
        }
        Properties p = new Properties();
        try (InputStream in = zip.getInputStream(entry); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    private static Map<String, String> readChecksums(ZipFile zip) throws IOException, UpdateException {
        ZipEntry entry = zip.getEntry(CHECKSUMS_NAME);
        if (entry == null) {
            throw invalid("no trae " + CHECKSUMS_NAME);
        }
        Map<String, String> result = new HashMap<>();
        try (InputStream in = zip.getInputStream(entry)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                // formato de sha256sum: "<hash>  <ruta>" (o "<hash> *<ruta>")
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length != 2 || !parts[0].matches("[0-9a-fA-F]{64}")) {
                    throw invalid("tiene una línea de verificación no válida");
                }
                String path = parts[1].startsWith("*") ? parts[1].substring(1) : parts[1];
                result.put(normalize(path), parts[0].toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 no disponible", e);
        }
    }
}
