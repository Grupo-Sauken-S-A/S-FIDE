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

package com.sauken.s_fide;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class TokenSlotsView {

    public static void main(String[] args) {
        try {
            if (args.length != 2) {
                throw new IllegalArgumentException("Uso: java -jar TokenSlotsView.jar <Ruta de la biblioteca PKCS#11> <Contraseña del token>");
            }

            String pkcs11LibraryPath = args[0];
            String password = args[1];

            // Verificar la existencia del archivo
            File pkcs11Library = new File(pkcs11LibraryPath);
            if (!pkcs11Library.exists()) {
                throw new IOException("El archivo de la biblioteca PKCS#11 no existe: " + pkcs11LibraryPath);
            }

            readToken(pkcs11LibraryPath, password);

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

    private static void readToken(String pkcs11LibraryPath, String password)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        // Configurar el proveedor PKCS#11
        String config = "--name=CustomProvider\nlibrary=" + pkcs11LibraryPath;
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new RuntimeException("El proveedor SunPKCS11 no está disponible");
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

        // Enumerar y mostrar el contenido de los slots
        int aliasNumber = 0;
        for (String alias : Collections.list(keyStore.aliases())) {
            System.out.println("  Slot: " + aliasNumber);
            System.out.println(" Alias: " + alias);

            if (keyStore.isKeyEntry(alias)) {
                System.out.println("  Tipo: Clave Privada");
            } else if (keyStore.isCertificateEntry(alias)) {
                System.out.println("  Tipo: Certificado");
                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    System.out.println(" Sujeto: " + x509Cert.getSubjectDN());
                } else {
                    System.out.println(" Tipo de certificado: " + cert.getType());
                }
            }
            System.out.println();
            aliasNumber++;
        }
    }
}
