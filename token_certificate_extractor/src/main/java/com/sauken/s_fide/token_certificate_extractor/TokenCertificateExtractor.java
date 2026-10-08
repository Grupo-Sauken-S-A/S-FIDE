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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TokenCertificateExtractor {
    private static final String VERSION = "S-FIDE TokenCertificateExtractor v1.5.0 - Grupo Sauken S.A.";
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
                case "-v":
                case "--version":
                    System.out.println(VERSION);
                    return;
                case "-licencia":
                case "--license":
                    System.out.println(LICENSE_TEXT);
                    return;
                case "-ayuda":
                case "-h":
                case "--help":
                    showHelp();
                    return;
                case "-listar-drivers":
                case "--listar-drivers":
                    System.out.println(TokenProfileCatalog.formatTableForCurrentOs());
                    return;
                default:
                    throw new IllegalArgumentException("Argumento no reconocido: " + args[0]);
            }
        }

        // <biblioteca> <contraseña> <slot> [-omitir-revocacion true|false]
        if (args.length != 3 && args.length != 5) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n" + HELP_TEXT);
        }

        boolean omitirRevocacion = false;
        if (args.length == 5) {
            if (!"-omitir-revocacion".equalsIgnoreCase(args[3]) && !"--omitir-revocacion".equalsIgnoreCase(args[3])) {
                throw new IllegalArgumentException("Argumento no reconocido: " + args[3]);
            }
            omitirRevocacion = Boolean.parseBoolean(args[4]);
        }

        processStandardArguments(args[0], args[1], args[2], omitirRevocacion);
    }

    private static void processStandardArguments(String libraryPath, String password, String slotArg,
                                                  boolean omitirRevocacion) throws Exception {
        int slotNumber;
        try {
            slotNumber = Integer.parseInt(slotArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El número de slot debe ser un número entero.");
        }

        validatePKCS11Library(libraryPath);
        extractCertificate(libraryPath, password, slotNumber, omitirRevocacion);
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

    private static void extractCertificate(String pkcs11LibraryPath, String password, int slotNumber,
                                            boolean omitirRevocacion) throws Exception {
        Provider provider = null;
        try {
            // Se prueba primero el slot pedido; si no tiene token se busca el token en los demás
            // slots (ver Pkcs11Access: el slot pedido es un índice sobre TODOS los slots de la
            // biblioteca, y el token puede no estar en el que se indicó).
            Pkcs11Access.Resolution resolution =
                    Pkcs11Access.resolve(pkcs11LibraryPath, slotNumber, "CustomProvider", false);
            provider = resolution.selected().provider();
            Security.addProvider(provider);
            int usedSlot = resolution.selected().slotIndex();
            if (resolution.differsFromRequested()) {
                System.out.println("Aviso: no había un token en el slot " + slotNumber
                        + "; se usó el slot " + usedSlot + ", donde se detectó el token.");
            }
            KeyStore keyStore = loadKeyStore(password, pkcs11LibraryPath);
            processCertificates(keyStore, usedSlot, omitirRevocacion);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error en la extracción del certificado: "
                    + Pkcs11Access.describeFailure(e, pkcs11LibraryPath));
        } finally {
            if (provider != null) {
                Security.removeProvider(provider.getName());
            }
        }
    }

    private static KeyStore loadKeyStore(String password, String pkcs11LibraryPath) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, password.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalArgumentException(Pkcs11Access.describeFailure(e, pkcs11LibraryPath));
        }
    }

    private static void processCertificates(KeyStore keyStore, int slotNumber, boolean omitirRevocacion) throws Exception {
        // Primero se juntan todos los certificados del token: los demás suelen incluir a la AC
        // emisora, necesaria para la consulta OCSP del estado de revocación.
        Map<String, X509Certificate> found = new LinkedHashMap<>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate x509) {
                found.put(alias, x509);
            }
        }

        if (found.isEmpty()) {
            System.out.println("No se encontró ningún certificado en el slot " + slotNumber);
            return;
        }

        List<X509Certificate> todos = new ArrayList<>(found.values());
        for (Map.Entry<String, X509Certificate> entry : found.entrySet()) {
            X509Certificate cert = entry.getValue();
            printCertificateInfo(cert, entry.getKey(), slotNumber);
            ExtractorSupport.printRevocationStatus(cert, todos, omitirRevocacion, System.out);
            try {
                ExtractorSupport.exportPem(cert, System.out);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error al exportar el certificado: " + e.getMessage());
            }
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

}