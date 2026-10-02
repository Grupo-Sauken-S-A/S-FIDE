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

import com.sauken.s_fide.s_fide_gui.utils.AppInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import javax.net.ssl.SSLException;

/**
 * Consulta, descarga y entrega a {@code SFideUpdater.jar} de las
 * actualizaciones de S-FiDE publicadas en GitHub.
 * <p>
 * Qué se garantiza (y qué no):
 * <ul>
 *   <li>Solo se habla con GitHub por HTTPS: cada salto de una redirección se
 *   valida contra una lista cerrada de dominios ({@link #isAllowedUrl(String)}), nunca
 *   se sigue ciegamente un {@code Location:}.</li>
 *   <li>El paquete descargado se verifica contra el SHA-256 publicado junto
 *   a él en la misma publicación. Protege contra descargas cortadas o
 *   corruptas; no contra quien pudiera alterar la publicación entera (para
 *   eso haría falta una firma del paquete con una clave aparte).</li>
 *   <li>Nada se descarga ni se instala sin que el usuario lo haya pedido
 *   expresamente en pantalla (eso lo garantiza quien llama).</li>
 * </ul>
 */
public final class UpdateService {
    static final String REPOSITORY = "Grupo-Sauken-S-A/S-FIDE";
    static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    public static final String RELEASES_PAGE = "https://github.com/" + REPOSITORY + "/releases";

    /**
     * Propiedad de sistema para apuntar la consulta a otro servidor (pruebas,
     * un espejo interno). Su dominio se agrega a los permitidos; no es una
     * puerta abierta: solo la fija quien lanza el programa.
     */
    static final String API_OVERRIDE_PROPERTY = "sfide.update.api";

    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "github.com", "githubusercontent.com");
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_PACKAGE_BYTES = 300L * 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** Un archivo adjunto de una publicación. */
    public record Asset(String name, long size, String url) {
    }

    /** Lo que importa de la última publicación. */
    public record ReleaseInfo(String tag, VersionNumber version, String name, String notes, String pageUrl,
                              Asset updatePackage, Asset checksum) {
        public boolean hasUpdatePackage() {
            return updatePackage != null && checksum != null;
        }

        public String versionText() {
            return version.toString();
        }
    }

    /** Resultado de correr SFideUpdater.jar en modo "verificar". */
    public record UpdaterRun(int exitCode, String output) {
    }

    /** Falla de la consulta/descarga, con un mensaje listo para mostrar. */
    public static final class UpdateCheckException extends Exception {
        private static final long serialVersionUID = 1L;

        UpdateCheckException(String message) {
            super(message);
        }

        UpdateCheckException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final HttpClient client;
    private final String apiUrl;

    public UpdateService() {
        // Que Java use la configuración de proxy del sistema (en entornos corporativos/estatales
        // es lo habitual). Debe fijarse antes de la primera consulta de red de este proceso.
        System.setProperty("java.net.useSystemProxies", "true");
        String override = System.getProperty(API_OVERRIDE_PROPERTY);
        this.apiUrl = (override != null && !override.isBlank()) ? override.trim() : LATEST_RELEASE_API;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    /** Consulta la última publicación (final, no pre-publicación) del repositorio. */
    public ReleaseInfo fetchLatest() throws UpdateCheckException {
        String json = new String(fetchBytes(apiUrl, 2L * 1024 * 1024, "application/vnd.github+json"), StandardCharsets.UTF_8);
        try {
            return parseRelease(json);
        } catch (IllegalArgumentException e) {
            throw new UpdateCheckException("La respuesta de GitHub no tiene el formato esperado.", e);
        }
    }

    /** Separado de la red para poder probarlo con respuestas de ejemplo. */
    static ReleaseInfo parseRelease(String json) {
        Map<String, Object> release = MiniJson.asObject(MiniJson.parse(json));
        String tag = string(release.get("tag_name"));
        VersionNumber version = VersionNumber.parse(tag)
                .orElseThrow(() -> new IllegalArgumentException("Etiqueta de versión no reconocida: " + tag));

        Asset update = null;
        Asset checksum = null;
        Object assets = release.get("assets");
        if (assets != null) {
            String wanted = "S-FiDE-" + version + "-actualizacion.zip";
            for (Object o : MiniJson.asArray(assets)) {
                Map<String, Object> asset = MiniJson.asObject(o);
                String name = string(asset.get("name"));
                long size = asset.get("size") instanceof Double d ? d.longValue() : -1;
                String url = string(asset.get("browser_download_url"));
                if (name.equals(wanted)) {
                    update = new Asset(name, size, url);
                } else if (name.equals(wanted + ".sha256")) {
                    checksum = new Asset(name, size, url);
                }
            }
        }
        String name = release.get("name") instanceof String s && !s.isBlank() ? s : "S-FiDE " + version;
        String notes = release.get("body") instanceof String s ? s : "";
        String page = release.get("html_url") instanceof String s ? s : RELEASES_PAGE;
        return new ReleaseInfo(tag, version, name, notes, page, update, checksum);
    }

    private static String string(Object value) {
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Falta un campo de texto en la respuesta");
    }

    /** {@code true} si {@code release} es más nueva que la instalada. */
    public static boolean isNewerThanInstalled(ReleaseInfo release) {
        Optional<VersionNumber> installed = VersionNumber.parse(AppInfo.version());
        // Si no se conoce la versión instalada, mejor ofrecer que callar.
        return installed.map(release.version()::isNewerThan).orElse(true);
    }

    // ------------------------------------------------------------------
    // Descarga y verificación
    // ------------------------------------------------------------------

    /** Lee el SHA-256 esperado del archivo {@code .sha256} publicado junto al paquete. */
    public String fetchExpectedSha256(Asset checksumAsset) throws UpdateCheckException {
        String text = new String(fetchBytes(checksumAsset.url(), 4096, "text/plain"), StandardCharsets.UTF_8);
        return parseExpectedSha256(text);
    }

    static String parseExpectedSha256(String text) throws UpdateCheckException {
        String first = text.trim().split("\\s+")[0];
        if (!first.matches("[0-9a-fA-F]{64}")) {
            throw new UpdateCheckException("El archivo de verificación (SHA-256) publicado no tiene el formato esperado.");
        }
        return first.toLowerCase(Locale.ROOT);
    }

    /**
     * Descarga el paquete a {@code destDir} y verifica su SHA-256 contra
     * {@code expectedSha256}. Si algo falla, no queda ningún archivo a medias.
     *
     * @param progress  recibe la fracción completada (0.0 a 1.0)
     * @param cancelled se consulta seguido: si devuelve {@code true}, se corta la descarga
     */
    public Path downloadAndVerify(Asset asset, String expectedSha256, Path destDir,
                                  DoubleConsumer progress, BooleanSupplier cancelled) throws UpdateCheckException {
        if (asset.size() > MAX_PACKAGE_BYTES) {
            throw new UpdateCheckException("El paquete de actualización publicado es demasiado grande para descargarlo automáticamente.");
        }
        Path part = destDir.resolve(asset.name() + ".part");
        Path target = destDir.resolve(asset.name());
        try {
            Files.createDirectories(destDir);
            HttpResponse<InputStream> response = open(asset.url(), "application/octet-stream");
            long total = asset.size() > 0 ? asset.size() : response.headers().firstValueAsLong("Content-Length").orElse(-1);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long received = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (cancelled.getAsBoolean()) {
                        throw new UpdateCheckException("La descarga fue cancelada.");
                    }
                    received += read;
                    if (received > MAX_PACKAGE_BYTES || (asset.size() > 0 && received > asset.size())) {
                        throw new UpdateCheckException("La descarga excede el tamaño publicado; se descartó.");
                    }
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    if (total > 0) {
                        progress.accept(Math.min(1.0, (double) received / total));
                    }
                }
            }
            if (asset.size() > 0 && received != asset.size()) {
                throw new UpdateCheckException("La descarga quedó incompleta (se recibieron " + received
                        + " de " + asset.size() + " bytes). Intente de nuevo.");
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                throw new UpdateCheckException("El paquete descargado no coincide con su verificación SHA-256: "
                        + "puede estar dañado. Se descartó; intente de nuevo más tarde.");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (UpdateCheckException e) {
            deleteQuietly(part);
            throw e;
        } catch (IOException | InterruptedException e) {
            deleteQuietly(part);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw translate(e);
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(part);
            throw new UpdateCheckException("Este Java no tiene SHA-256 disponible.", e);
        }
    }

    private byte[] fetchBytes(String url, long maxBytes, String accept) throws UpdateCheckException {
        try {
            HttpResponse<InputStream> response = open(url, accept);
            try (InputStream in = response.body()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > maxBytes) {
                        throw new UpdateCheckException("La respuesta de GitHub es más grande de lo esperado.");
                    }
                }
                return buffer.toByteArray();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw translate(e);
        }
    }

    /** GET con redirecciones manuales: cada salto se valida contra la lista de dominios permitidos. */
    private HttpResponse<InputStream> open(String url, String accept) throws IOException, InterruptedException, UpdateCheckException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isAllowedUrl(uri.toString())) {
                throw new UpdateCheckException("Dirección no permitida para descargar actualizaciones: " + uri.getHost());
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "S-FiDE/" + AppInfo.version() + " (+https://github.com/" + REPOSITORY + ")")
                    .header("Accept", accept)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                response.body().close();
                if (location == null) {
                    throw new UpdateCheckException("GitHub respondió una redirección sin destino.");
                }
                uri = uri.resolve(location);
                continue;
            }
            if (status == 200) {
                return response;
            }
            response.body().close();
            throw httpError(status);
        }
        throw new UpdateCheckException("GitHub redirigió la descarga demasiadas veces.");
    }

    private static UpdateCheckException httpError(int status) {
        return switch (status) {
            case 403, 429 -> new UpdateCheckException("GitHub rechazó la consulta por exceso de pedidos desde su red. Intente de nuevo en un rato.");
            case 404 -> new UpdateCheckException("GitHub no encontró lo pedido (¿todavía no hay publicaciones?).");
            case 407 -> new UpdateCheckException("Su servidor proxy pide autenticación y no se pudo completar la consulta.");
            default -> new UpdateCheckException("GitHub respondió con un error (código " + status + "). Intente de nuevo más tarde.");
        };
    }

    static UpdateCheckException translate(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (e instanceof HttpConnectTimeoutException || e instanceof ConnectException
                || root instanceof UnknownHostException || root instanceof ConnectException
                || root instanceof java.nio.channels.UnresolvedAddressException) {
            return new UpdateCheckException("No se pudo conectar con GitHub. Verifique su conexión a Internet "
                    + "o la configuración de proxy/firewall de su red.", e);
        }
        if (e instanceof HttpTimeoutException || root instanceof java.net.SocketTimeoutException) {
            return new UpdateCheckException("GitHub tardó demasiado en responder. Intente de nuevo en un rato.", e);
        }
        if (e instanceof SSLException || root instanceof SSLException) {
            return new UpdateCheckException("No se pudo establecer una conexión segura con GitHub (certificado o "
                    + "inspección de tráfico de su red).", e);
        }
        return new UpdateCheckException("Falló la comunicación con GitHub: " + root.getMessage(), e);
    }

    /**
     * {@code true} solo para HTTPS hacia GitHub (o su red de descargas) — o,
     * si quien lanzó el programa fijó {@value #API_OVERRIDE_PROPERTY}, hacia
     * el servidor indicado ahí.
     */
    static boolean isAllowedUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        String override = System.getProperty(API_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            try {
                URI o = URI.create(override.trim());
                if (o.getHost() != null && o.getHost().equalsIgnoreCase(host)
                        && Objects.equals(o.getScheme(), uri.getScheme())) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // se evalúa contra la lista normal
            }
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        for (String suffix : ALLOWED_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Se limpia en el próximo arranque.
        }
    }

    // ------------------------------------------------------------------
    // SFideUpdater.jar
    // ------------------------------------------------------------------

    public static Path installDir() {
        return AppInfo.installDir();
    }

    public static Path updaterJar() {
        Path dir = installDir();
        return dir == null ? null : dir.resolve("SFideUpdater.jar");
    }

    private static String javaExecutable(boolean preferWindowless) {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        if (preferWindowless) {
            Path javaw = bin.resolve("javaw.exe");
            if (Files.exists(javaw)) {
                return javaw.toString();
            }
        }
        return bin.resolve("java").toString();
    }

    /**
     * Corre {@code SFideUpdater.jar verificar}: valida el paquete y que la
     * instalación se pueda actualizar (permisos, archivos en uso), sin
     * modificar nada. El jar de la GUI y el del propio verificador se excluyen
     * de la prueba de "archivo en uso": los tiene abiertos esta misma consulta.
     */
    public UpdaterRun verifyWithUpdater(Path packageZip) throws UpdateCheckException {
        Path jar = updaterJar();
        Path install = installDir();
        if (jar == null || !Files.isRegularFile(jar)) {
            throw new UpdateCheckException("No se encuentra SFideUpdater.jar junto a S-FiDE: esta instalación no puede "
                    + "actualizarse automáticamente. Descargue la distribución completa desde GitHub.");
        }
        List<String> command = List.of(javaExecutable(false), "-Dfile.encoding=UTF-8", "-jar", jar.toString(),
                "verificar", packageZip.toString(), install.toString(),
                // Los dos jars que esta misma consulta tiene abiertos: la GUI y el propio verificador.
                "--ignorar", "SFide-GUI.jar", "--ignorar", "SFideUpdater.jar");
        try {
            return runAndCapture(command, Duration.ofSeconds(60));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new UpdateCheckException("No se pudo verificar la actualización: " + e.getMessage(), e);
        }
    }

    /**
     * Lanza {@code SFideUpdater.jar aplicar}, desacoplado de este proceso, y
     * devuelve enseguida. El actualizador espera a que esta aplicación
     * termine (por su pid) antes de tocar ningún archivo, y la vuelve a
     * abrir al final. Se ejecuta desde una COPIA del jar fuera de la
     * instalación, porque el propio SFideUpdater.jar forma parte de lo que
     * se reemplaza.
     */
    public void launchApply(Path packageZip, Path userDataDir) throws UpdateCheckException {
        Path jar = updaterJar();
        Path install = installDir();
        if (jar == null || !Files.isRegularFile(jar)) {
            throw new UpdateCheckException("No se encuentra SFideUpdater.jar junto a S-FiDE.");
        }
        try {
            Path runDir = Files.createDirectories(userDataDir.resolve("update"));
            Path runJar = runDir.resolve("SFideUpdater-run.jar");
            Files.copy(jar, runJar, StandardCopyOption.REPLACE_EXISTING);

            List<String> command = new ArrayList<>(List.of(javaExecutable(true), "-Dfile.encoding=UTF-8", "-jar",
                    runJar.toString(), "aplicar", packageZip.toString(), install.toString(),
                    "--esperar-pid", String.valueOf(ProcessHandle.current().pid()), "--relanzar",
                    "--resultado", userDataDir.resolve(RESULT_FILE_NAME).toString(),
                    "--log", userDataDir.resolve("actualizacion.log").toString()));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.start();
        } catch (IOException e) {
            throw new UpdateCheckException("No se pudo iniciar el actualizador: " + e.getMessage(), e);
        }
    }

    public static final String RESULT_FILE_NAME = "update-result.properties";

    /**
     * Ejecuta un proceso y devuelve su salida. Se lee en un hilo aparte EN
     * SIMULTÁNEO con la espera (nunca recién después): si el hijo escribe más
     * de lo que entra en el buffer del pipe y nadie lo lee, queda bloqueado
     * y el timeout nunca llegaría a aplicarse.
     */
    static UpdaterRun runAndCapture(List<String> command, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(captured);
            } catch (IOException ignored) {
                // El proceso se destruyó por timeout.
            }
        }, "lector-actualizador");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            reader.join(2000);
            return new UpdaterRun(1, "El actualizador no respondió a tiempo.");
        }
        reader.join(5000);
        return new UpdaterRun(process.exitValue(), captured.toString(StandardCharsets.UTF_8).trim());
    }
}
