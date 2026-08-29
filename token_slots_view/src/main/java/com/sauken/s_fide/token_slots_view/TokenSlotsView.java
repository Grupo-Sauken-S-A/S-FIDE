/*
  Derechos Reservados © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

  Este es un Software Libre; como tal redistribuirlo y/o modificarlo está
  permitido, siempre y cuando se haga bajo los términos y condiciones de la
  Licencia Pública General GNU publicada por la Free Software Foundation,
  ya sea en su versión 2 ó cualquier otra de las posteriores a la misma.

  Este "Programa" se distribuye con la intención de que sea útil, sin
  embargo carece de garantía, ni siquiera tiene la garantía implícita de
  tipo comercial o inherente al propósito del mismo "Programa". Ver la
  Licencia Pública General GNU para más detalles.

  Se debe haber recibido una copia de la Licencia Pública General GNU con
  este "Programa", si este no fue el caso, favor de escribir a la Free
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

package com.sauken.s_fide.token_slots_view;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.stream.Collectors;

public class TokenSlotsView {
    private static final String VERSION = "S-FIDE TokenSlotsView v1.0.0 - Grupo Sauken S.A.";
    private static String LICENSE_TEXT;
    private static String HELP_TEXT;
    private static PrintStream errorOutput;
    private static PrintStream standardOutput;

    static {
        try {
            errorOutput = new PrintStream(System.err, true, StandardCharsets.UTF_8);
            standardOutput = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            LICENSE_TEXT = loadResourceFile("LICENSE.txt");
            HELP_TEXT = loadResourceFile("HELP.txt");
        } catch (IOException e) {
            System.err.println("Error crítico al inicializar: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String loadResourceFile(String resourceName) throws IOException {
        try (InputStream is = TokenSlotsView.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IOException("No se pudo encontrar el archivo de recursos: " + resourceName);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                showHelp();
                System.exit(1);
            }

            processArguments(args);
            System.exit(0);

        } catch (Exception e) {
            errorOutput.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processArguments(String[] args) {
        if (args.length == 1) {
            String argLower = args[0].toLowerCase();
            switch (argLower) {
                case "-version" -> {
                    standardOutput.println(VERSION);
                    System.exit(0);
                }
                case "-licencia" -> {
                    standardOutput.println(LICENSE_TEXT);
                    System.exit(0);
                }
                case "-ayuda" -> {
                    showHelp();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Opción no válida: " + args[0]);
            }
        }

        if (args.length != 2) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + HELP_TEXT);
        }

        try {
            validateAndProcessToken(args[0], args[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar el token: " + e.getMessage());
        }
    }

    private static void validateAndProcessToken(String pkcs11LibraryPath, String password) {
        Path libraryPath = Path.of(pkcs11LibraryPath);
        if (!Files.exists(libraryPath)) {
            throw new IllegalArgumentException("El archivo de la biblioteca PKCS#11 no existe: " + pkcs11LibraryPath);
        }

        Security.addProvider(new BouncyCastleProvider());

        String config = "--name=CustomProvider\nlibrary=" + pkcs11LibraryPath;
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new IllegalArgumentException("El proveedor SunPKCS11 no está disponible");
        }

        provider = provider.configure(config);
        Security.addProvider(provider);

        try {
            readToken(password);
        } finally {
            Security.removeProvider(provider.getName());
        }
    }

    private static void readToken(String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, password.toCharArray());
            displayTokenContents(keyStore);
        } catch (IOException e) {
            throw new IllegalArgumentException("Contraseña incorrecta o error al acceder al token");
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IllegalArgumentException("Error al leer el token");
        }
    }

    private static void displayTokenContents(KeyStore keyStore) throws KeyStoreException {
        var aliases = Collections.list(keyStore.aliases());
        if (aliases.isEmpty()) {
            standardOutput.println("No se encontraron certificados ni claves en el token.");
            return;
        }

        for (int i = 0; i < aliases.size(); i++) {
            String alias = aliases.get(i);
            displaySlotInfo(keyStore, alias, i);
        }
    }

    private static void displaySlotInfo(KeyStore keyStore, String alias, int slotNumber) throws KeyStoreException {
        standardOutput.println("  Slot: " + slotNumber);
        standardOutput.println(" Alias: " + alias);

        if (keyStore.isKeyEntry(alias)) {
            standardOutput.println("  Tipo: Clave Privada");
            displayCertificateInfo(keyStore, alias);
        } else if (keyStore.isCertificateEntry(alias)) {
            standardOutput.println("  Tipo: Certificado");
            displayCertificateInfo(keyStore, alias);
        }
        standardOutput.println();
    }

    private static void displayCertificateInfo(KeyStore keyStore, String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate x509Cert) {
                standardOutput.println(" Sujeto: " + x509Cert.getSubjectX500Principal().getName());
                standardOutput.println(" Emisor: " + x509Cert.getIssuerX500Principal().getName());
                standardOutput.println(" Válido desde: " + x509Cert.getNotBefore());
                standardOutput.println(" Válido hasta: " + x509Cert.getNotAfter());
                standardOutput.println(" Número de serie: " + x509Cert.getSerialNumber());
            } else {
                standardOutput.println(" Tipo de certificado: " + cert.getType());
            }
        } catch (KeyStoreException e) {
            standardOutput.println(" Error al leer el certificado");
        }
    }

    private static void showHelp() {
        standardOutput.println(HELP_TEXT);
    }
}