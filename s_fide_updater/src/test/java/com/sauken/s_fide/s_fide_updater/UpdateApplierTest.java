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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateApplierTest {

    @TempDir
    Path tmp;

    Path install;
    List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        install = Files.createDirectory(tmp.resolve("S-FiDE"));
        Files.createDirectories(install.resolve("openjdk-23.0.1"));
        Files.createDirectories(install.resolve("javafx-sdk-23.0.1"));
        write(install.resolve("Aaa.jar"), "aaa-vieja");
        write(install.resolve("Zzz.jar"), "zzz-vieja");
        write(install.resolve("SFide-GUI.bat"), "launcher-viejo");
        write(install.resolve("sfide-defaults.properties"), "del-usuario");
    }

    private static void write(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    private static String sha(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Arma un paquete de actualización; {@code files} es ruta -> contenido. */
    private Path zip(String name, Map<String, String> files, Map<String, String> manifestExtras,
                     Map<String, String> hashOverrides) throws Exception {
        Path zip = tmp.resolve(name);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            StringBuilder manifest = new StringBuilder("version=9.9.9\n");
            manifestExtras.forEach((k, v) -> manifest.append(k).append('=').append(v).append('\n'));
            put(z, UpdatePackage.MANIFEST_NAME, manifest.toString());
            StringBuilder sums = new StringBuilder();
            for (Map.Entry<String, String> e : files.entrySet()) {
                put(z, e.getKey(), e.getValue());
                String h = hashOverrides.containsKey(e.getKey()) ? hashOverrides.get(e.getKey()) : sha(e.getValue());
                if (e.getKey().startsWith("..") || e.getKey().startsWith("/")) {
                    // no se pueden declarar rutas inválidas en el archivo de hashes: se agregan igual
                }
                sums.append(h).append("  ").append(e.getKey()).append('\n');
            }
            put(z, UpdatePackage.CHECKSUMS_NAME, sums.toString());
        }
        return zip;
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private Path simpleZip() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Aaa.jar", "aaa-nueva");
        files.put("Zzz.jar", "zzz-nueva");
        files.put("Nueva.jar", "modulo-nuevo");
        files.put("SFide-GUI.bat", "launcher-nuevo");
        files.put("doc/manual.html", "<html>nuevo</html>");
        return zip("update.zip", files, Map.of("requires.java", "openjdk-23.0.1"), Map.of());
    }

    private UpdateApplier applier(UpdatePackage pkg) {
        return new UpdateApplier(install, pkg, log::add, Duration.ofMillis(300), Duration.ofMillis(50));
    }

    private void assertNoLeftovers() throws IOException {
        try (Stream<Path> s = Files.list(install)) {
            List<String> names = s.map(p -> p.getFileName().toString()).toList();
            assertTrue(names.stream().noneMatch(n -> n.startsWith(".sfide-")), "quedaron restos: " + names);
        }
    }

    @Test
    void reemplazaAgregaYNoTocaLosDatosDelUsuario() throws Exception {
        applier(UpdatePackage.open(simpleZip())).apply();

        assertEquals("aaa-nueva", Files.readString(install.resolve("Aaa.jar")));
        assertEquals("zzz-nueva", Files.readString(install.resolve("Zzz.jar")));
        assertEquals("modulo-nuevo", Files.readString(install.resolve("Nueva.jar")));
        assertEquals("launcher-nuevo", Files.readString(install.resolve("SFide-GUI.bat")));
        assertEquals("<html>nuevo</html>", Files.readString(install.resolve("doc/manual.html")));
        assertEquals("del-usuario", Files.readString(install.resolve("sfide-defaults.properties")));
        assertNoLeftovers();
    }

    @Test
    void unArchivoIdenticoNoSeToca() throws Exception {
        FileTime marca = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(install.resolve("Aaa.jar"), marca);
        write(install.resolve("Aaa.jar"), "aaa-nueva");
        Files.setLastModifiedTime(install.resolve("Aaa.jar"), marca);

        applier(UpdatePackage.open(simpleZip())).apply();

        assertEquals(marca, Files.getLastModifiedTime(install.resolve("Aaa.jar")));
    }

    @Test
    void rutasQueSalenDeLaInstalacionSeRechazan() throws Exception {
        Path zip = zip("slip.zip", Map.of("../evil.jar", "x"), Map.of(), Map.of());
        UpdateException e = assertThrows(UpdateException.class, () -> UpdatePackage.open(zip));
        assertEquals(UpdateException.Kind.INVALID_PACKAGE, e.kind());
    }

    @Test
    void soloSeInstalaLoDeLaListaCerrada() throws Exception {
        for (String name : List.of("sfide-defaults.properties", "openjdk-23.0.1/bin/java.exe", "otra/carpeta/x.jar",
                "test/test.xml", "malware.exe", "xsd/esquema.xsd")) {
            Path zip = zip("malo.zip", Map.of(name, "x"), Map.of(), Map.of());
            UpdateException e = assertThrows(UpdateException.class, () -> UpdatePackage.open(zip), name);
            assertEquals(UpdateException.Kind.INVALID_PACKAGE, e.kind(), name);
        }
    }

    @Test
    void unHashQueNoCoincideCancelaSinTocarNada() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Aaa.jar", "aaa-nueva");
        files.put("Zzz.jar", "zzz-nueva");
        Path zip = zip("hash.zip", files, Map.of(), Map.of("Zzz.jar", "0".repeat(64)));

        UpdateException e = assertThrows(UpdateException.class, () -> applier(UpdatePackage.open(zip)).apply());

        assertEquals(UpdateException.Kind.INVALID_PACKAGE, e.kind());
        assertEquals("aaa-vieja", Files.readString(install.resolve("Aaa.jar")));
        assertEquals("zzz-vieja", Files.readString(install.resolve("Zzz.jar")));
        assertNoLeftovers();
    }

    @Test
    void siFaltaUnRuntimeQueLaVersionExigeSeAvisaAntesDeEmpezar() throws Exception {
        Path zip = zip("rt.zip", Map.of("Aaa.jar", "aaa-nueva"), Map.of("requires.java", "openjdk-99.0.0"), Map.of());
        UpdatePackage pkg = UpdatePackage.open(zip);

        UpdateException e = assertThrows(UpdateException.class, () -> applier(pkg).preflight(Set.of()));
        assertEquals(UpdateException.Kind.INVALID_PACKAGE, e.kind());
        assertTrue(e.getMessage().contains("openjdk-99.0.0"));
        assertEquals("aaa-vieja", Files.readString(install.resolve("Aaa.jar")));
    }

    @Test
    void unaMarcaViejaNoTrabaParaSiempreYUnaVigenteSi() throws Exception {
        Path marca = install.resolve(UpdateApplier.BUSY_MARKER);
        write(marca, "pid=1");
        UpdatePackage pkg = UpdatePackage.open(simpleZip());

        UpdateException busy = assertThrows(UpdateException.class, () -> applier(pkg).apply());
        assertEquals(UpdateException.Kind.BUSY, busy.kind());
        assertTrue(Files.exists(marca), "no debe borrar la marca de otra actualización en curso");

        Files.setLastModifiedTime(marca, FileTime.from(Instant.now().minus(Duration.ofHours(1))));
        applier(pkg).apply();
        assertEquals("aaa-nueva", Files.readString(install.resolve("Aaa.jar")));
        assertNoLeftovers();
    }

    @Test
    void conUnArchivoEnUsoPorOtraSesionSeRevierteTodo() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"),
                "El bloqueo de archivos abiertos es propio de Windows");
        UpdatePackage pkg = UpdatePackage.open(simpleZip());

        // Zzz.jar se aplica DESPUÉS de Aaa.jar y de los archivos nuevos: simula la JVM de otro
        // usuario con ese jar abierto (java.io, sin permiso de compartir para borrar/renombrar).
        try (RandomAccessFile enUso = new RandomAccessFile(install.resolve("Zzz.jar").toFile(), "r")) {
            assertTrue(enUso.length() > 0);

            UpdateException e = assertThrows(UpdateException.class, () -> applier(pkg).apply());
            assertEquals(UpdateException.Kind.FILES_IN_USE, e.kind());
            assertTrue(e.getMessage().contains("otro usuario"));
        }

        assertEquals("aaa-vieja", Files.readString(install.resolve("Aaa.jar")), "debe volver a la versión anterior");
        assertEquals("zzz-vieja", Files.readString(install.resolve("Zzz.jar")));
        assertEquals("launcher-viejo", Files.readString(install.resolve("SFide-GUI.bat")));
        assertFalse(Files.exists(install.resolve("Nueva.jar")), "el archivo nuevo ya colocado debe quitarse");
        assertFalse(Files.exists(install.resolve("doc/manual.html")));
        assertNoLeftovers();
    }

    @Test
    void laVerificacionPreviaDetectaElArchivoEnUsoSinTocarNada() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"),
                "El bloqueo de archivos abiertos es propio de Windows");
        UpdatePackage pkg = UpdatePackage.open(simpleZip());

        try (RandomAccessFile enUso = new RandomAccessFile(install.resolve("Zzz.jar").toFile(), "r")) {
            assertTrue(enUso.length() > 0);
            UpdateException e = assertThrows(UpdateException.class, () -> applier(pkg).preflight(Set.of()));
            assertEquals(UpdateException.Kind.FILES_IN_USE, e.kind());
            // ...salvo que sea el propio jar de quien consulta
            applier(pkg).preflight(Set.of("Zzz.jar"));
        }
        assertEquals("zzz-vieja", Files.readString(install.resolve("Zzz.jar")));
        assertNoLeftovers();
    }

    @Test
    void elHashDelPaqueteSeVerificaConElMismoFormatoQueSha256sum() throws Exception {
        UpdatePackage pkg = UpdatePackage.open(simpleZip());
        assertEquals(sha("aaa-nueva"), pkg.expectedSha256("Aaa.jar"));
        // los lanzadores van al final: son lo último que se reemplaza
        assertEquals("SFide-GUI.bat", pkg.entries().get(pkg.entries().size() - 1));
    }

    @Test
    void enLinuxLosLanzadoresShConservanOGananElPermisoDeEjecucion() throws Exception {
        assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "Los permisos de Unix no existen en Windows");
        Path launcher = install.resolve("SFide-GUI.sh");
        write(launcher, "viejo");
        // el lanzador anterior ya había perdido el permiso de ejecución; el jar tiene permisos propios
        Files.setPosixFilePermissions(launcher, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        Files.setPosixFilePermissions(install.resolve("Aaa.jar"), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));

        Map<String, String> files = new LinkedHashMap<>();
        files.put("SFide-GUI.sh", "nuevo");
        files.put("Otro.sh", "nuevo-lanzador");
        files.put("Aaa.jar", "aaa-nueva");
        files.put("Datos.ico", "icono");
        Path zip = zip("perm.zip", files, Map.of(), Map.of());
        applier(UpdatePackage.open(zip)).apply();

        assertEquals("rwxr-xr-x", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(launcher)));
        assertEquals("rwxr-xr-x", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(install.resolve("Otro.sh"))),
                "un .sh nuevo debe ser ejecutable");
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(install.resolve("Aaa.jar"))),
                "se conservan los permisos del archivo que se reemplaza");
        assertFalse(Files.isExecutable(install.resolve("Datos.ico")), "un archivo común nuevo no debe ser ejecutable");
    }
}
