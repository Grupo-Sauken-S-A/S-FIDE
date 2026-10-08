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


package com.sauken.s_fide.integration_tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utilidades compartidas por las pruebas de extremo a extremo que lanzan los
 * módulos como procesos separados (ver {@link Pkcs12RoundTripTest} para el
 * porqué de cada decisión: drenaje de la salida en un hilo aparte, modo
 * headless, timeout con destroyForcibly).
 */
final class ProcesoSupport {

    private static final int TIMEOUT_SECONDS = 120;

    private ProcesoSupport() {
    }

    /** Resultado de correr un proceso: código de salida y toda su salida (stdout+stderr). */
    record Resultado(int exitCode, String salida) {
    }

    static Resultado ejecutarModulo(Path directorio, String claseFqcn, String... argumentos)
            throws IOException, InterruptedException {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> comando = new ArrayList<>(List.of(
                javaBin, "-cp", System.getProperty("java.class.path"),
                "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", claseFqcn));
        comando.addAll(List.of(argumentos));
        return ejecutar(comando, directorio);
    }

    static Resultado ejecutar(List<String> comando, Path directorio) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.directory(directorio.toFile());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        StringBuilder salida = new StringBuilder();
        Thread lector = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    synchronized (salida) {
                        salida.append(linea).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // Se destruye el proceso por timeout más abajo.
            }
        }, "lector-salida-proceso");
        lector.setDaemon(true);
        lector.start();

        if (!proceso.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
            lector.join(2000);
            fail("El proceso no terminó dentro de " + TIMEOUT_SECONDS + " segundos: " + comando);
        }
        lector.join(5000);
        synchronized (salida) {
            return new Resultado(proceso.exitValue(), salida.toString());
        }
    }

    /** Genera un .p12 autofirmado con keytool; devuelve la contraseña (aleatoria, nunca se persiste). */
    static String generarP12(Path directorio, Path destino, String cn) throws IOException, InterruptedException {
        String password = passwordAleatoria();
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        Resultado r = ejecutar(List.of(
                keytool.toString(), "-genkeypair", "-alias", "sfide-test", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-keystore", destino.toString(),
                "-storetype", "PKCS12", "-storepass", password, "-keypass", password,
                "-dname", "CN=" + cn + ", O=Grupo Sauken S.A., C=AR"), directorio);
        if (r.exitCode() != 0) {
            fail("keytool no pudo generar el certificado de prueba: " + r.salida());
        }
        return password;
    }

    private static String passwordAleatoria() {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(alfabeto.charAt(random.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }
}
