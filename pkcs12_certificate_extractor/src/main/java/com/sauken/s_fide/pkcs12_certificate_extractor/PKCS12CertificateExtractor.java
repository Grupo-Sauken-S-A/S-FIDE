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

package com.sauken.s_fide.pkcs12_certificate_extractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Optional;
import javax.security.auth.x500.X500Principal;

public class PKCS12CertificateExtractor {
    private static final String VERSION = "S-FIDE PKCS12CertificateExtractor v1.0.0 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT = readResourceFile("/LICENSE.txt");
    private static final String HELP_TEXT = readResourceFile("/HELP.txt");
    private static PrintStream errorStream;

    public static void main(String[] args) {
        try {
            configureOutputStreams();
            processArguments(args);
            System.exit(0);
        } catch (Exception e) {
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void configureOutputStreams() throws UnsupportedEncodingException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        errorStream = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.setErr(errorStream);
    }

    private static void processArguments(String[] args) {
        if (args == null || args.length == 0) {
            showHelp();
            throw new CustomException("No se proporcionaron argumentos.");
        }

        if (args.length == 1) {
            processSpecialArgument(args[0]);
            return;
        }

        if (args.length != 2) {
            throw new CustomException("Número incorrecto de argumentos.\n\n" + HELP_TEXT);
        }

        processStandardArguments(args[0], args[1]);
    }

    private static void processSpecialArgument(String arg) {
        String argLower = arg.toLowerCase();
        switch (argLower) {
            case "-version" -> System.out.println(VERSION);
            case "-licencia" -> System.out.println(LICENSE_TEXT);
            case "-ayuda" -> showHelp();
            default -> throw new CustomException("Argumento no reconocido: " + arg);
        }
    }

    private static void processStandardArguments(String pkcs12File, String password) {
        validatePKCS12File(pkcs12File, password);
        extractCertificates(pkcs12File, password);
    }

    private static void validatePKCS12File(String pkcs12File, String password) {
        Path p12Path = Paths.get(pkcs12File);
        if (!Files.exists(p12Path)) {
            throw new CustomException("El archivo PKCS#12 no existe: " + pkcs12File);
        }

        try (InputStream is = Files.newInputStream(p12Path)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(is, password.toCharArray());
            if (!ks.aliases().hasMoreElements()) {
                throw new CustomException("El archivo PKCS#12 no contiene ningún certificado.");
            }
        } catch (IOException e) {
            throw new CustomException("El archivo no es un PKCS#12 válido o la contraseña es incorrecta");
        } catch (Exception e) {
            throw new CustomException("Error al validar el archivo PKCS#12: " + e.getMessage());
        }
    }

    private static void extractCertificates(String pkcs12File, String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = Files.newInputStream(Paths.get(pkcs12File))) {
                keyStore.load(is, password.toCharArray());
            }

            boolean foundCertificate = false;
            Enumeration<String> aliases = keyStore.aliases();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = keyStore.getCertificate(alias);

                if (cert instanceof X509Certificate x509Cert) {
                    foundCertificate = true;
                    printCertificateInfo(x509Cert, alias);
                    exportToPEM(x509Cert);
                }
            }

            if (!foundCertificate) {
                throw new CustomException("No se encontró ningún certificado X.509 en el archivo PKCS#12");
            }
        } catch (Exception e) {
            throw new CustomException("Error al procesar certificados: " + e.getMessage());
        }
    }

    private static void printCertificateInfo(X509Certificate cert, String alias) {
        CertificateInfo info = new CertificateInfo(cert, alias);
        System.out.println(info);
    }

    private static void exportToPEM(X509Certificate cert) {
        try {
            String fileName = getFileNameFromSubject(cert.getSubjectX500Principal()) + ".pem";
            Path outputPath = Paths.get(fileName);

            String pemContent = convertToPEM(cert);
            Files.writeString(outputPath, pemContent, StandardCharsets.UTF_8);

            System.out.println("Certificado exportado como: " + fileName);
        } catch (Exception e) {
            throw new CustomException("Error al exportar certificado: " + e.getMessage());
        }
    }

    private static String convertToPEM(X509Certificate cert) {
        try {
            Base64.Encoder encoder = Base64.getMimeEncoder(64, System.lineSeparator().getBytes());
            String certEncoded = encoder.encodeToString(cert.getEncoded());
            return String.format("-----BEGIN CERTIFICATE-----%n%s%n-----END CERTIFICATE-----", certEncoded);
        } catch (Exception e) {
            throw new CustomException("Error al convertir certificado a formato PEM: " + e.getMessage());
        }
    }

    private static String getFileNameFromSubject(X500Principal subject) {
        return Optional.of(subject.getName())
                .map(name -> name.split(","))
                .flatMap(parts -> java.util.Arrays.stream(parts)
                        .map(String::trim)
                        .filter(part -> part.startsWith("CN="))
                        .map(part -> part.substring(3))
                        .findFirst())
                .map(cn -> cn.replaceAll("[^a-zA-Z0-9.-]", "_"))
                .orElse("certificate");
    }

    private static void showHelp() {
        System.out.println(HELP_TEXT);
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = PKCS12CertificateExtractor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "Error: Archivo de recurso no encontrado";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error al leer el archivo de recurso";
        }
    }

    private record CertificateInfo(X509Certificate certificate, String alias) {
        @Override
        public String toString() {
            return """
                    Información del Certificado con alias '%s':
                    Sujeto: %s
                    Emisor: %s
                    Número de Serie: %s
                    Válido desde: %s
                    Válido hasta: %s
                    Algoritmo de Firma: %s""".formatted(
                    alias,
                    certificate.getSubjectX500Principal(),
                    certificate.getIssuerX500Principal(),
                    certificate.getSerialNumber(),
                    certificate.getNotBefore(),
                    certificate.getNotAfter(),
                    certificate.getSigAlgName());
        }
    }

    private static class CustomException extends RuntimeException {
        public CustomException(String message) {
            super(message);
        }
    }
}