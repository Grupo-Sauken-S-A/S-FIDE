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

package com.sauken.s_fide.windows_certificate_store_view;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Muestra el contenido del almacén "Personal" de certificados de Windows
 * (Windows-MY, vía el provider SunMSCAPI del JDK): mismo rol que
 * TokenSlotsView pero para el almacén de Windows en vez de un token PKCS#11.
 * No firma ni modifica nada — es solo un visor, pensado para saber de
 * antemano qué alias o nombre (CN) escribirle a XMLSignerWindowsCSP /
 * PDFSignerWindowsCSP, en vez de tener que adivinarlo. Solo funciona en
 * Windows.
 */
public class WindowsCertificateStoreView {
    private static final String VERSION = "S-FIDE WindowsCertificateStoreView v1.1.1 - Grupo Sauken S.A.";
    private static PrintStream outputStream;
    private static PrintStream errorStream;

    static {
        try {
            outputStream = new PrintStream(System.out, true, "UTF-8");
            errorStream = new PrintStream(System.err, true, "UTF-8");
            System.setOut(outputStream);
            System.setErr(errorStream);
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error configurando codificación UTF-8");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            if (!isWindows()) {
                throw new IllegalStateException(
                        "Este módulo solo funciona en Windows (usa el almacén de certificados CSP/KSP de Windows). "
                                + "En otros sistemas operativos use TokenSlotsView con el token PKCS#11 correspondiente.");
            }
            processArguments(args);
            System.exit(0);
        } catch (Exception e) {
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void processArguments(String[] args) throws Exception {
        if (args.length == 0) {
            listarCertificados();
            return;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "-version":
                case "-v":
                case "--version":
                    outputStream.println(VERSION);
                    return;
                case "-licencia":
                case "--license":
                    outputStream.println(loadResourceFile("/LICENSE.txt"));
                    return;
                case "-ayuda":
                case "-h":
                case "--help":
                    showHelp();
                    return;
                case "-listar-certificados":
                case "--listar-certificados":
                    listarCertificados();
                    return;
                default:
                    throw new IllegalArgumentException("Argumento no reconocido: " + args[0]
                            + "\n\n" + loadResourceFile("/HELP.txt"));
            }
        }

        throw new IllegalArgumentException("Este módulo no admite parámetros — ejecútelo sin argumentos "
                + "para ver los certificados disponibles.\n\n" + loadResourceFile("/HELP.txt"));
    }

    private static void showHelp() throws IOException {
        outputStream.println(loadResourceFile("/HELP.txt"));
    }

    private static String loadResourceFile(String resourcePath) throws IOException {
        try (InputStream is = WindowsCertificateStoreView.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("No se pudo encontrar el recurso: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void listarCertificados() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
        keyStore.load(null, null);

        List<String> aliases = Collections.list(keyStore.aliases());
        if (aliases.isEmpty()) {
            outputStream.println("No se encontraron certificados en el almacén de Windows (Windows-MY).");
            return;
        }

        for (String alias : aliases) {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            outputStream.println("Alias: " + alias);
            if (cert != null) {
                outputStream.println("  Sujeto: " + cert.getSubjectX500Principal().getName());
                outputStream.println("  Emisor: " + cert.getIssuerX500Principal().getName());
                outputStream.println("  Válido desde: " + cert.getNotBefore());
                outputStream.println("  Válido hasta: " + cert.getNotAfter());
                outputStream.println("  Número de serie: " + cert.getSerialNumber().toString(16));
                outputStream.println("  Tiene clave privada: " + keyStore.isKeyEntry(alias));
            } else {
                outputStream.println("  (entrada sin certificado X.509 asociado)");
            }
            outputStream.println();
        }

        outputStream.println("Use el alias exacto, o un fragmento del Sujeto (CN), en \"Alias / Nombre (CN)\" "
                + "de XMLSignerWindowsCSP o PDFSignerWindowsCSP. Solo son utilizables para firmar las entradas "
                + "marcadas \"Tiene clave privada: true\".");
    }
}
