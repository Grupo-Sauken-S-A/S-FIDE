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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TokenSlotsView {
    private static final String VERSION = "S-FIDE TokenSlotsView v1.3.0 - Grupo Sauken S.A.";
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
                case "-version", "-v", "--version" -> {
                    standardOutput.println(VERSION);
                    System.exit(0);
                }
                case "-licencia", "--license" -> {
                    standardOutput.println(LICENSE_TEXT);
                    System.exit(0);
                }
                case "-ayuda", "-h", "--help" -> {
                    showHelp();
                    System.exit(0);
                }
                case "-listar-drivers", "--listar-drivers" -> {
                    standardOutput.println(TokenProfileCatalog.formatTableForCurrentOs());
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Opción no válida: " + args[0]);
            }
        }

        if (args.length < 2 || args.length > 4) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + HELP_TEXT);
        }

        int requestedSlot = -1;
        boolean all = false;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.equalsIgnoreCase("-todos") || arg.equalsIgnoreCase("--todos")) {
                all = true;
            } else if (requestedSlot < 0) {
                try {
                    requestedSlot = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Argumento no reconocido: " + arg
                            + " (se esperaba un número de slot o -todos).");
                }
                if (requestedSlot < 0) {
                    throw new IllegalArgumentException("El número de slot no puede ser negativo.");
                }
            } else {
                throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + HELP_TEXT);
            }
        }
        if (all && requestedSlot >= 0) {
            throw new IllegalArgumentException("Use un número de slot o -todos, no ambos.");
        }

        try {
            validateAndProcessToken(args[0], args[1], requestedSlot, all);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar el token: " + e.getMessage());
        }
    }

    private static void validateAndProcessToken(String pkcs11LibraryPath, String password, int requestedSlot,
                                                boolean all) {
        Security.addProvider(new BouncyCastleProvider());

        // Inventario de la biblioteca: no necesita contraseña (saber si hay un token en un slot es
        // información pública), así que se muestra siempre y completo.
        Pkcs11Access.Inventory inventory = Pkcs11Access.inventory(pkcs11LibraryPath, "CustomProvider");
        printInventory(inventory);

        // Qué slots se leen. Leer el contenido exige iniciar sesión con la contraseña; con varios
        // tokens, probarla en uno que no es el suyo puede sumar un intento fallido a SU contador de
        // bloqueo. Por eso, por defecto, solo se lee uno; para leer todos hay que pedirlo (-todos).
        List<Pkcs11Access.TokenSlot> targets;
        if (all) {
            if (inventory.withToken().isEmpty()) {
                Pkcs11Access.resolve(pkcs11LibraryPath, -1, "CustomProvider", true); // lanza el mensaje de "sin token"
            }
            targets = inventory.withToken();
            if (targets.size() > 1) {
                standardOutput.println("Se leerán los " + targets.size() + " tokens con la misma contraseña "
                        + "(un intento por token).");
            }
        } else {
            Pkcs11Access.Resolution resolution =
                    Pkcs11Access.resolve(pkcs11LibraryPath, requestedSlot, "CustomProvider", requestedSlot < 0);
            if (resolution.differsFromRequested()) {
                standardOutput.println("Aviso: no había un token en el slot " + requestedSlot
                        + "; se usó el slot " + resolution.selected().slotIndex() + ", donde se detectó el token.");
            }
            targets = List.of(resolution.selected());
            if (!resolution.otherSlotsWithToken().isEmpty()) {
                standardOutput.println("Nota: solo se lee un token para no probar la contraseña en uno que quizá no "
                        + "sea el suyo. Hay tokens también en los slots " + resolution.otherSlotsWithToken()
                        .toString().replaceAll("[\\[\\]]", "") + ": indique un número de slot para leer uno "
                        + "puntual, o use -todos para leerlos a todos.");
            }
        }

        int readOk = 0;
        String lastError = null;
        for (Pkcs11Access.TokenSlot target : targets) {
            try {
                readToken(password, target, pkcs11LibraryPath);
                readOk++;
            } catch (IllegalArgumentException e) {
                if (targets.size() == 1) {
                    throw e;
                }
                lastError = e.getMessage();
                standardOutput.println("  Slot " + target.slotIndex() + ": no se pudo leer. " + e.getMessage());
                standardOutput.println();
            }
        }
        if (readOk == 0 && lastError != null) {
            throw new IllegalArgumentException(lastError);
        }
    }

    private static void printInventory(Pkcs11Access.Inventory inventory) {
        List<Integer> withToken = new ArrayList<>();
        for (Pkcs11Access.TokenSlot t : inventory.withToken()) {
            withToken.add(t.slotIndex());
        }
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < inventory.totalSlots(); i++) {
            if (!withToken.contains(i)) {
                empty.add(i);
            }
        }
        standardOutput.println("La biblioteca informa " + inventory.totalSlots() + " slot"
                + (inventory.totalSlots() == 1 ? "" : "s") + ".");
        standardOutput.println("  Con token: " + (withToken.isEmpty() ? "ninguno" : join(withToken)));
        standardOutput.println("  Sin token: " + (empty.isEmpty() ? "ninguno" : join(empty)));
        standardOutput.println();
    }

    private static String join(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static void readToken(String password, Pkcs11Access.TokenSlot slot, String libraryPath) {
        KeyStore keyStore;
        try {
            // Con el proveedor de ESE slot, sin registrarlo: así cada token se lee por separado.
            keyStore = KeyStore.getInstance("PKCS11", slot.provider());
            keyStore.load(null, password.toCharArray());
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IllegalArgumentException(Pkcs11Access.describeFailure(e, libraryPath));
        }
        try {
            displayTokenContents(keyStore, slot.slotIndex());
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException(Pkcs11Access.describeFailure(e, libraryPath));
        }
    }

    private static void displayTokenContents(KeyStore keyStore, int slotIndex) throws KeyStoreException {
        var aliases = Collections.list(keyStore.aliases());
        if (aliases.isEmpty()) {
            standardOutput.println("No se encontraron certificados ni claves en el token.");
            return;
        }

        standardOutput.println("  Slot: " + slotIndex);
        for (int i = 0; i < aliases.size(); i++) {
            String alias = aliases.get(i);
            if (aliases.size() > 1) {
                standardOutput.println("  Entrada: " + i);
            }
            displaySlotInfo(keyStore, alias);
        }
    }

    private static void displaySlotInfo(KeyStore keyStore, String alias) throws KeyStoreException {
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