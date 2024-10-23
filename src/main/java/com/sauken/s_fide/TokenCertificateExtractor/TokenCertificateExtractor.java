/*
  Derechos Reservados © 2024 Juan Carlos Ríos, Grupo Sauken S.A.

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

  Autor: Juan Carlos Ríos
  Correo electrónico: mailto:jrios@sauken.com.ar,rios.juancarlos@gmail.com
  Empresa: Grupo Sauken S.A.
  WebSite: http://www.sauken.com.ar/

  <>

  Copyright © 2024 Juan Carlos Ríos, Grupo Sauken S.A.

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

  Author: Juan Carlos Ríos
  E-mail: mailto:jrios@sauken.com.ar,rios.juancarlos@gmail.com
  Company: Grupo Sauken S.A.
  WebSite: http://www.sauken.com.ar/

 */

package com.sauken.s_fide.TokenCertificateExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;

import javax.security.auth.x500.X500Principal;

public class TokenCertificateExtractor {

    public static void main(String[] args) {
        try {
            if (args.length != 3) {
                throw new IllegalArgumentException("Uso: java -jar TokenCertificateExtractor.jar <Ruta de la biblioteca PKCS#11> <Contraseña del token> <Número de slot>");
            }

            String pkcs11LibraryPath = args[0];
            String password = args[1];
            int slotNumber;

            try {
                slotNumber = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Error: El número de slot debe ser un número entero.");
            }

            // Verificar la existencia del archivo
            File pkcs11Library = new File(pkcs11LibraryPath);
            if (!pkcs11Library.exists()) {
                throw new IOException("El archivo de la biblioteca PKCS#11 no existe: " + pkcs11LibraryPath);
            }

            extractCertificate(pkcs11LibraryPath, password, slotNumber);

            // Si llegamos aquí, todo ha ido bien
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (!(e instanceof IllegalArgumentException)) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }

    private static void extractCertificate(String pkcs11LibraryPath, String password, int slotNumber)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        // Configurar el proveedor PKCS#11
        String config = String.format(
                "--name=CustomProvider\nlibrary=%s\nslot=%d",
                pkcs11LibraryPath,
                slotNumber
        );
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new RuntimeException("Proveedor SunPKCS11 no disponible");
        }
        provider = provider.configure(config);
        Security.addProvider(provider);

        // Abrir el KeyStore desde el token
        KeyStore keyStore = KeyStore.getInstance("PKCS11");
        try {
            keyStore.load(null, password.toCharArray());
        } catch (IOException e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("CKR_PIN_INCORRECT")) {
                throw new IOException("La contraseña del token es incorrecta.");
            }
            throw new IOException("Error al cargar el KeyStore: " + e.getMessage(), e);
        }

        // Buscar el certificado en el slot
        Enumeration<String> aliases = keyStore.aliases();
        if (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) cert;
                printCertificateInfo(x509Cert, slotNumber);
                exportToPEM(x509Cert);
            } else {
                System.out.println("El certificado en el slot " + slotNumber + " no es de tipo X.509");
            }
        } else {
            System.out.println("No se encontró ningún certificado en el slot " + slotNumber);
        }
    }

    private static void printCertificateInfo(X509Certificate cert, int slotNumber) {
        System.out.println("Información del Certificado en el slot " + slotNumber + ":");
        System.out.println("Sujeto: " + cert.getSubjectX500Principal());
        System.out.println("Emisor: " + cert.getIssuerX500Principal());
        System.out.println("Número de Serie: " + cert.getSerialNumber());
        System.out.println("Válido desde: " + cert.getNotBefore());
        System.out.println("Válido hasta: " + cert.getNotAfter());
        System.out.println("Algoritmo de Firma: " + cert.getSigAlgName());
    }

    private static void exportToPEM(X509Certificate cert) throws CertificateException, IOException {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, System.lineSeparator().getBytes());
        String certEncoded = encoder.encodeToString(cert.getEncoded());
        String certPEM = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                certEncoded + System.lineSeparator() +
                "-----END CERTIFICATE-----";

        String fileName = getFileNameFromSubject(cert.getSubjectX500Principal()) + ".pem";
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(fileName))) {
            writer.write(certPEM);
        }
        System.out.println("Certificado exportado como: " + fileName);
    }

    private static String getFileNameFromSubject(X500Principal subject) {
        String subjectString = subject.getName();
        String[] parts = subjectString.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3).replaceAll("[^a-zA-Z0-9.-]", "_");
            }
        }
        return "certificate";
    }
}
