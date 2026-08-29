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

package com.sauken.s_fide.token_certificate_extractor;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;
import javax.security.auth.x500.X500Principal;

public class TokenCertificateExtractor {
    private static final String VERSION = "S-FIDE TokenCertificateExtractor v1.0.0 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT;
    private static final String HELP_TEXT;

    static {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error configurando codificación: " + e.getMessage());
            System.exit(1);
        }

        LICENSE_TEXT = readResourceFile("/LICENSE.txt");
        HELP_TEXT = readResourceFile("/HELP.txt");
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = TokenCertificateExtractor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "Error: Archivo de recurso no encontrado: " + resourcePath;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error al leer el archivo de recurso: " + resourcePath;
        }
    }

    public static void main(String[] args) {
        try {
            processArguments(args);
            System.exit(0);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processArguments(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            showHelp();
            throw new IllegalArgumentException("No se proporcionaron argumentos.");
        }

        if (args.length == 1) {
            String argLower = args[0].toLowerCase();
            switch (argLower) {
                case "-version":
                    System.out.println(VERSION);
                    return;
                case "-licencia":
                    System.out.println(LICENSE_TEXT);
                    return;
                case "-ayuda":
                    showHelp();
                    return;
                default:
                    throw new IllegalArgumentException("Argumento no reconocido: " + args[0]);
            }
        }

        if (args.length != 3) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n" + HELP_TEXT);
        }

        processStandardArguments(args[0], args[1], args[2]);
    }

    private static void processStandardArguments(String libraryPath, String password, String slotArg) throws Exception {
        int slotNumber;
        try {
            slotNumber = Integer.parseInt(slotArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El número de slot debe ser un número entero.");
        }

        validatePKCS11Library(libraryPath);
        extractCertificate(libraryPath, password, slotNumber);
    }

    private static void validatePKCS11Library(String pkcs11LibraryPath) throws IOException {
        Path libraryPath = Paths.get(pkcs11LibraryPath);
        if (!Files.exists(libraryPath)) {
            throw new IOException("El archivo de la biblioteca PKCS#11 no existe: " + pkcs11LibraryPath);
        }
    }

    private static void showHelp() {
        System.out.println(HELP_TEXT);
    }

    private static void extractCertificate(String pkcs11LibraryPath, String password, int slotNumber) throws Exception {
        Provider provider = null;
        try {
            provider = configurePKCS11Provider(pkcs11LibraryPath, slotNumber);
            Security.addProvider(provider);
            KeyStore keyStore = loadKeyStore(password);
            processCertificates(keyStore, slotNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error en la extracción del certificado: " + e.getMessage());
        } finally {
            if (provider != null) {
                Security.removeProvider(provider.getName());
            }
        }
    }

    private static Provider configurePKCS11Provider(String pkcs11LibraryPath, int slotNumber) throws IllegalArgumentException {
        String config = String.format(
                "--name=CustomProvider%nlibrary=%s%nslot=%d",
                pkcs11LibraryPath,
                slotNumber
        );
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new IllegalArgumentException("Proveedor SunPKCS11 no disponible");
        }
        return provider.configure(config);
    }

    private static KeyStore loadKeyStore(String password) throws Exception {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, password.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al cargar el almacén de claves: " + e.getMessage());
        }
    }

    private static void processCertificates(KeyStore keyStore, int slotNumber) throws Exception {
        Enumeration<String> aliases = keyStore.aliases();
        boolean foundCertificate = false;

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                foundCertificate = true;
                printCertificateInfo((X509Certificate) cert, alias, slotNumber);
                exportToPEM((X509Certificate) cert);
            }
        }

        if (!foundCertificate) {
            System.out.println("No se encontró ningún certificado en el slot " + slotNumber);
        }
    }

    private static void printCertificateInfo(X509Certificate cert, String alias, int slotNumber) {
        StringBuilder info = new StringBuilder();
        info.append("Información del Certificado en el slot ").append(slotNumber)
                .append(" con alias '").append(alias).append("':\n")
                .append("Sujeto: ").append(cert.getSubjectX500Principal()).append("\n")
                .append("Emisor: ").append(cert.getIssuerX500Principal()).append("\n")
                .append("Número de Serie: ").append(cert.getSerialNumber()).append("\n")
                .append("Válido desde: ").append(cert.getNotBefore()).append("\n")
                .append("Válido hasta: ").append(cert.getNotAfter()).append("\n")
                .append("Algoritmo de Firma: ").append(cert.getSigAlgName());

        System.out.println(info.toString());
    }

    private static void exportToPEM(X509Certificate cert) throws Exception {
        try {
            String fileName = getFileNameFromSubject(cert.getSubjectX500Principal()) + ".pem";
            Path outputPath = Paths.get(fileName);

            Base64.Encoder encoder = Base64.getMimeEncoder(64, System.lineSeparator().getBytes());
            String certEncoded = encoder.encodeToString(cert.getEncoded());
            String pemContent = "-----BEGIN CERTIFICATE-----\n" +
                    certEncoded + "\n" +
                    "-----END CERTIFICATE-----";

            Files.writeString(outputPath, pemContent, StandardCharsets.UTF_8);
            System.out.println("Certificado exportado como: " + fileName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al exportar el certificado: " + e.getMessage());
        }
    }

    private static String getFileNameFromSubject(X500Principal subject) {
        String name = subject.getName();
        String[] parts = name.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3).replaceAll("[^a-zA-Z0-9.-]", "_");
            }
        }
        return "certificate";
    }
}