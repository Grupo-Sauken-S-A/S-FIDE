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


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Herramienta de empaquetado (no forma parte de S-FiDE ni se distribuye): arma el paquete de macOS de una
 * versión, un .tar.gz por procesador (Apple Silicon o Intel), a partir de:
 * <ul>
 *   <li>la carpeta de distribución que genera {@code install.bat} (jars, lanzadores, documentación);</li>
 *   <li>el OpenJDK para macOS tal como lo publica Oracle (.tar.gz) y el SDK de JavaFX para macOS (.zip).</li>
 * </ul>
 * Se escribe un .tar.gz y no un .zip porque el formato tar guarda los permisos de Unix (el lanzador y los
 * ejecutables del Java embebido tienen que quedar ejecutables) y un zip armado en Windows no los guarda.
 * Los originales se leen directamente, sin extraerlos a disco: así tampoco se pierden los permisos ni los
 * enlaces simbólicos de la carpeta {@code legal} del JDK (se convierten en archivos comunes).
 * <p>
 * Uso: {@code java CrearDistribucionMacOS.java --dist <carpeta> --jdk <openjdk.tar.gz> --javafx <sdk.zip>
 * --plataforma macos-aarch64|macos-x64 --version <x.y.z> --salida <carpeta>}
 */
public final class CrearDistribucionMacOS {

    private static final String JDK_FOLDER = "openjdk-23.0.1";
    private static final String FX_FOLDER = "javafx-sdk-23.0.1";
    /** Carpeta raíz del runtime dentro del tar de Oracle. */
    private static final String JDK_HOME_MARKER = "/Contents/Home/";
    private static final Set<String> NO_VA = Set.of(JDK_FOLDER, FX_FOLDER, "logs", "sfide-defaults.properties");

    private static final String LANZADOR_MAC = """
            #!/bin/sh
            # Lanzador para hacer doble clic desde el Finder: abre S-FiDE con el lanzador de siempre.
            # La primera vez, macOS puede pedir confirmar: clic derecho sobre este archivo y "Abrir".
            DIR="$(cd "$(dirname "$0")" && pwd)"
            exec "$DIR/SFide-GUI.sh"
            """;

    private CrearDistribucionMacOS() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> p = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            p.put(args[i], args[i + 1]);
        }
        for (String k : List.of("--dist", "--jdk", "--javafx", "--plataforma", "--version", "--salida")) {
            if (!p.containsKey(k)) {
                System.err.println("Falta el argumento " + k);
                System.err.println("Uso: java CrearDistribucionMacOS.java --dist <carpeta> --jdk <openjdk.tar.gz> "
                        + "--javafx <sdk.zip> --plataforma macos-aarch64|macos-x64 --version <x.y.z> --salida <carpeta>");
                System.exit(1);
            }
        }
        String plataforma = p.get("--plataforma");
        if (!plataforma.equals("macos-aarch64") && !plataforma.equals("macos-x64")) {
            System.err.println("La plataforma debe ser macos-aarch64 o macos-x64.");
            System.exit(1);
        }
        Path dist = Paths.get(p.get("--dist"));
        Path jdk = Paths.get(p.get("--jdk"));
        Path fx = Paths.get(p.get("--javafx"));
        Path salida = Paths.get(p.get("--salida"));
        Files.createDirectories(salida);

        verificarSha256(jdk);

        Path tarGz = salida.resolve("S-FiDE-" + p.get("--version") + "-" + plataforma + ".tar.gz");
        try (TarWriter tar = new TarWriter(new BufferedOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tarGz)), 1 << 16), 1 << 16))) {
            int[] conteo = new int[3];
            agregarDistribucion(tar, dist, conteo);
            tar.file("SFide-GUI.command", LANZADOR_MAC.getBytes(StandardCharsets.UTF_8), 0755, System.currentTimeMillis() / 1000);
            agregarJdk(tar, jdk, JDK_FOLDER + "/" + plataforma, conteo);
            agregarJavaFx(tar, fx, FX_FOLDER + "/" + plataforma, conteo);
            System.out.println("Archivos de la distribución: " + conteo[0] + ", del JDK: " + conteo[1]
                    + ", de JavaFX: " + conteo[2]);
        }
        String hash = sha256(tarGz);
        Files.writeString(Paths.get(tarGz + ".sha256"), hash + "  " + tarGz.getFileName() + "\n", StandardCharsets.UTF_8);
        System.out.println("Listo: " + tarGz + " (" + Files.size(tarGz) / (1024 * 1024) + " MB)");
        System.out.println("SHA-256: " + hash);
    }

    // ---------------------------------------------------------------------------------------------

    private static void agregarDistribucion(TarWriter tar, Path dist, int[] conteo) throws IOException {
        List<Path> archivos = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dist)) {
            s.sorted().forEach(archivos::add);
        }
        for (Path f : archivos) {
            Path rel = dist.relativize(f);
            if (rel.toString().isEmpty()) {
                continue;
            }
            String nombre = rel.toString().replace('\\', '/');
            String primero = nombre.contains("/") ? nombre.substring(0, nombre.indexOf('/')) : nombre;
            String lower = primero.toLowerCase(Locale.ROOT);
            if (NO_VA.contains(primero) || lower.endsWith(".lock") || lower.startsWith("hs_err") || lower.startsWith(".")) {
                continue;
            }
            long mtime = Files.getLastModifiedTime(f).toMillis() / 1000;
            if (Files.isDirectory(f)) {
                tar.dir(nombre, 0755, mtime);
            } else {
                int modo = lower.endsWith(".sh") ? 0755 : 0644;
                tar.file(nombre, Files.readAllBytes(f), modo, mtime);
                conteo[0]++;
            }
        }
    }

    /** Copia lo que hay bajo Contents/Home del tar de Oracle. Los enlaces simbólicos se vuelven archivos comunes. */
    private static void agregarJdk(TarWriter tar, Path jdkTarGz, String destino, int[] conteo) throws IOException {
        // Pasada 1: contenido de los archivos chicos de legal/ (los enlaces apuntan siempre ahí).
        Map<String, byte[]> legal = new HashMap<>();
        try (TarReader r = abrirTar(jdkTarGz)) {
            TarReader.Entrada e;
            while ((e = r.siguiente()) != null) {
                String home = bajoHome(e.nombre);
                if (home != null && e.tipo == '0' && home.startsWith("legal/")) {
                    legal.put(home, r.leerTodo(e));
                }
            }
        }
        // Pasada 2: escritura.
        tar.dir(JDK_FOLDER, 0755, System.currentTimeMillis() / 1000);
        tar.dir(destino, 0755, System.currentTimeMillis() / 1000);
        try (TarReader r = abrirTar(jdkTarGz)) {
            TarReader.Entrada e;
            while ((e = r.siguiente()) != null) {
                String home = bajoHome(e.nombre);
                if (home == null || home.isEmpty()) {
                    continue;
                }
                // jmods/ e include/ solo sirven para compilar o armar runtimes con jlink, no para ejecutar.
                if (home.startsWith("jmods") || home.startsWith("include")) {
                    continue;
                }
                String ruta = destino + "/" + sinBarraFinal(home);
                if (e.tipo == '5') {
                    tar.dir(ruta, 0755, e.mtime);
                } else if (e.tipo == '2') {
                    // Enlace simbólico (relativo, dentro de legal/): se copia el contenido del destino.
                    String objetivo = normalizar(home.substring(0, Math.max(0, home.lastIndexOf('/') + 1)) + e.destinoEnlace);
                    byte[] datos = legal.get(objetivo);
                    if (datos == null) {
                        throw new IOException("Un enlace del JDK apunta a algo que no se pudo resolver: " + home + " -> " + e.destinoEnlace);
                    }
                    tar.file(ruta, datos, 0644, e.mtime);
                    conteo[1]++;
                } else if (e.tipo == '0') {
                    int modo = (e.modo & 0111) != 0 ? 0755 : 0644;
                    tar.file(ruta, r.leerTodo(e), modo, e.mtime);
                    conteo[1]++;
                }
            }
        }
    }

    /** Copia lib/ y legal/ del SDK de JavaFX (el resto del SDK no hace falta para ejecutar). */
    private static void agregarJavaFx(TarWriter tar, Path fxZip, String destino, int[] conteo) throws IOException {
        long ahora = System.currentTimeMillis() / 1000;
        tar.dir(FX_FOLDER, 0755, ahora);
        tar.dir(destino, 0755, ahora);
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(Files.newInputStream(fxZip), 1 << 16))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String nombre = e.getName();
                int corte = nombre.indexOf('/');
                if (corte < 0) {
                    continue;
                }
                String bajo = nombre.substring(corte + 1);
                if (!(bajo.startsWith("lib/") || bajo.startsWith("legal/") || bajo.equals("lib") || bajo.equals("legal"))) {
                    continue;
                }
                String ruta = destino + "/" + sinBarraFinal(bajo);
                long mtime = e.getTime() > 0 ? e.getTime() / 1000 : ahora;
                if (e.isDirectory()) {
                    tar.dir(ruta, 0755, mtime);
                } else {
                    byte[] datos = z.readAllBytes();
                    int modo = bajo.endsWith(".dylib") ? 0755 : 0644;
                    tar.file(ruta, datos, modo, mtime);
                    conteo[2]++;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Ruta relativa a Contents/Home, o {@code null} si la entrada no está bajo esa carpeta. */
    private static String bajoHome(String nombre) {
        String n = nombre.startsWith("./") ? nombre.substring(2) : nombre;
        int i = n.indexOf(JDK_HOME_MARKER);
        if (i < 0) {
            return n.endsWith("/Contents/Home") ? "" : null;
        }
        return n.substring(i + JDK_HOME_MARKER.length());
    }

    private static String sinBarraFinal(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String normalizar(String ruta) {
        List<String> partes = new ArrayList<>();
        for (String parte : ruta.split("/")) {
            if (parte.isEmpty() || parte.equals(".")) {
                continue;
            }
            if (parte.equals("..")) {
                if (!partes.isEmpty()) {
                    partes.remove(partes.size() - 1);
                }
            } else {
                partes.add(parte);
            }
        }
        return String.join("/", partes);
    }

    private static TarReader abrirTar(Path tarGz) throws IOException {
        return new TarReader(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(tarGz), 1 << 16), 1 << 16));
    }

    private static void verificarSha256(Path archivo) throws Exception {
        Path hashFile = Paths.get(archivo + ".sha256");
        if (!Files.isRegularFile(hashFile)) {
            System.out.println("Aviso: no hay " + hashFile.getFileName() + " junto al JDK; no se verificó su suma.");
            return;
        }
        String esperado = Files.readString(hashFile).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        String real = sha256(archivo);
        if (!esperado.equals(real)) {
            System.err.println("La suma SHA-256 del JDK no coincide con la publicada por Oracle: el archivo está dañado "
                    + "o fue alterado. No se arma el paquete.");
            System.exit(1);
        }
        System.out.println("SHA-256 del JDK verificado.");
    }

    private static String sha256(Path archivo) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(archivo), 1 << 16)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    // ---------------------------------------------------------------------------------------------
    //  Lector y escritor de tar (solo lo necesario: ustar + extensiones PAX/GNU de nombre largo)
    // ---------------------------------------------------------------------------------------------

    private static final class TarReader implements AutoCloseable {
        static final class Entrada {
            String nombre;
            char tipo;
            int modo;
            long tamano;
            long mtime;
            String destinoEnlace;
        }

        private final DataInputStream in;
        private long pendienteRelleno;

        TarReader(InputStream in) {
            this.in = new DataInputStream(in);
        }

        Entrada siguiente() throws IOException {
            saltarRelleno();
            String nombreLargo = null;
            String enlaceLargo = null;
            while (true) {
                byte[] h = new byte[512];
                try {
                    in.readFully(h);
                } catch (EOFException e) {
                    return null;
                }
                if (esCeros(h)) {
                    return null;
                }
                Entrada e = new Entrada();
                e.nombre = texto(h, 0, 100);
                e.modo = (int) octal(h, 100, 8);
                e.tamano = octal(h, 124, 12);
                e.mtime = octal(h, 136, 12);
                e.tipo = (char) (h[156] == 0 ? '0' : h[156]);
                e.destinoEnlace = texto(h, 157, 100);
                String prefijo = texto(h, 345, 155);
                if (!prefijo.isEmpty()) {
                    e.nombre = prefijo + "/" + e.nombre;
                }
                if (e.tipo == 'x' || e.tipo == 'g') {
                    byte[] datos = leerBytes(e.tamano);
                    for (String linea : new String(datos, StandardCharsets.UTF_8).split("\n")) {
                        int sp = linea.indexOf(' ');
                        int eq = linea.indexOf('=');
                        if (sp > 0 && eq > sp) {
                            String clave = linea.substring(sp + 1, eq);
                            String valor = linea.substring(eq + 1);
                            if (clave.equals("path")) {
                                nombreLargo = valor;
                            } else if (clave.equals("linkpath")) {
                                enlaceLargo = valor;
                            }
                        }
                    }
                    pendienteRelleno = 0;
                    continue;
                }
                if (e.tipo == 'L' || e.tipo == 'K') {
                    String valor = new String(leerBytes(e.tamano), StandardCharsets.UTF_8).replace("\0", "");
                    if (e.tipo == 'L') {
                        nombreLargo = valor;
                    } else {
                        enlaceLargo = valor;
                    }
                    continue;
                }
                if (nombreLargo != null) {
                    e.nombre = nombreLargo;
                }
                if (enlaceLargo != null) {
                    e.destinoEnlace = enlaceLargo;
                }
                pendienteRelleno = (512 - (e.tamano % 512)) % 512;
                pendienteDatos = e.tamano;
                return e;
            }
        }

        private long pendienteDatos;

        byte[] leerTodo(Entrada e) throws IOException {
            byte[] datos = new byte[(int) e.tamano];
            in.readFully(datos);
            pendienteDatos = 0;
            return datos;
        }

        private byte[] leerBytes(long n) throws IOException {
            byte[] datos = new byte[(int) n];
            in.readFully(datos);
            long relleno = (512 - (n % 512)) % 512;
            in.skipBytes((int) relleno);
            return datos;
        }

        private void saltarRelleno() throws IOException {
            long total = pendienteDatos + pendienteRelleno;
            while (total > 0) {
                long n = in.skip(total);
                if (n <= 0) {
                    if (in.read() < 0) {
                        break;
                    }
                    n = 1;
                }
                total -= n;
            }
            pendienteDatos = 0;
            pendienteRelleno = 0;
        }

        private static boolean esCeros(byte[] b) {
            for (byte x : b) {
                if (x != 0) {
                    return false;
                }
            }
            return true;
        }

        private static String texto(byte[] b, int desde, int largo) {
            int fin = desde;
            while (fin < desde + largo && b[fin] != 0) {
                fin++;
            }
            return new String(b, desde, fin - desde, StandardCharsets.UTF_8);
        }

        private static long octal(byte[] b, int desde, int largo) {
            if ((b[desde] & 0x80) != 0) {
                long v = 0;
                for (int i = 1; i < largo; i++) {
                    v = (v << 8) | (b[desde + i] & 0xff);
                }
                return v;
            }
            String s = texto(b, desde, largo).trim();
            return s.isEmpty() ? 0 : Long.parseLong(s, 8);
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static final class TarWriter implements AutoCloseable {
        private final OutputStream out;
        private final java.util.Set<String> directorios = new java.util.HashSet<>();

        TarWriter(OutputStream out) {
            this.out = out;
        }

        void dir(String nombre, int modo, long mtime) throws IOException {
            String n = nombre.endsWith("/") ? nombre : nombre + "/";
            if (directorios.add(n)) {
                escribir(n, '5', modo, 0, mtime);
            }
        }

        void file(String nombre, byte[] datos, int modo, long mtime) throws IOException {
            asegurarPadres(nombre, mtime);
            escribir(nombre, '0', modo, datos.length, mtime);
            out.write(datos);
            int relleno = (int) ((512 - (datos.length % 512)) % 512);
            out.write(new byte[relleno]);
        }

        private void asegurarPadres(String nombre, long mtime) throws IOException {
            int i = nombre.indexOf('/');
            while (i >= 0) {
                dir(nombre.substring(0, i), 0755, mtime);
                i = nombre.indexOf('/', i + 1);
            }
        }

        private void escribir(String nombre, char tipo, int modo, long tamano, long mtime) throws IOException {
            byte[] nb = nombre.getBytes(StandardCharsets.UTF_8);
            boolean ascii = nb.length == nombre.length();
            if (nb.length > 100 || !ascii) {
                // Extensión PAX con el nombre completo.
                String registro = " path=" + nombre + "\n";
                int largo = registro.getBytes(StandardCharsets.UTF_8).length;
                int total = largo + String.valueOf(largo).length();
                total = largo + String.valueOf(total).length();
                byte[] datos = (total + registro).getBytes(StandardCharsets.UTF_8);
                cabecera("PaxHeader/" + abreviar(nombre), 'x', 0644, datos.length, mtime);
                out.write(datos);
                out.write(new byte[(int) ((512 - (datos.length % 512)) % 512)]);
            }
            cabecera(nb.length > 100 || !ascii ? abreviar(nombre) : nombre, tipo, modo, tamano, mtime);
        }

        private static String abreviar(String nombre) {
            String n = nombre.endsWith("/") ? nombre.substring(0, nombre.length() - 1) : nombre;
            n = n.substring(n.lastIndexOf('/') + 1);
            StringBuilder sb = new StringBuilder();
            for (char c : n.toCharArray()) {
                sb.append(c < 128 ? c : '_');
            }
            String s = sb.toString();
            return s.length() > 90 ? s.substring(0, 90) : s;
        }

        private void cabecera(String nombre, char tipo, int modo, long tamano, long mtime) throws IOException {
            byte[] h = new byte[512];
            byte[] nb = nombre.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(nb, 0, h, 0, Math.min(nb.length, 100));
            poner(h, 100, 8, String.format("%07o", modo & 07777));
            poner(h, 108, 8, String.format("%07o", 0));
            poner(h, 116, 8, String.format("%07o", 0));
            poner(h, 124, 12, String.format("%011o", tamano));
            poner(h, 136, 12, String.format("%011o", Math.max(0, mtime)));
            Arrays.fill(h, 148, 156, (byte) ' ');
            h[156] = (byte) tipo;
            poner(h, 257, 6, "ustar");
            h[262] = 0;
            poner(h, 263, 2, "00");
            long suma = 0;
            for (byte b : h) {
                suma += b & 0xff;
            }
            poner(h, 148, 7, String.format("%06o", suma));
            h[154] = 0;
            h[155] = ' ';
            out.write(h);
        }

        private static void poner(byte[] h, int desde, int largo, String valor) {
            byte[] v = valor.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(v, 0, h, desde, Math.min(v.length, largo));
        }

        @Override
        public void close() throws IOException {
            out.write(new byte[1024]);
            out.close();
        }
    }
}
