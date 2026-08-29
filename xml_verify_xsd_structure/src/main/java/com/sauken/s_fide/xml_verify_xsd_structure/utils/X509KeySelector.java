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

package com.sauken.s_fide.xml_verify_xsd_structure.utils;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.keyinfo.*;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.cert.X509Certificate;

public class X509KeySelector extends KeySelector {
    private static final PrintStream errorStream;

    static {
        try {
            errorStream = new PrintStream(System.err, true, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error al configurar la codificación de errores");
        }
    }

    @Override
    public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose,
                                    AlgorithmMethod method, XMLCryptoContext context)
            throws KeySelectorException {

        if (keyInfo == null) {
            errorStream.println("Error: No se encontró información de clave (KeyInfo)");
            throw new KeySelectorException("No se encontró KeyInfo");
        }

        // Primero intentamos con X509Data
        for (Object o : keyInfo.getContent()) {
            if (o instanceof X509Data) {
                X509Data x509Data = (X509Data) o;
                for (Object x509Object : x509Data.getContent()) {
                    if (x509Object instanceof X509Certificate) {
                        final PublicKey key = ((X509Certificate) x509Object).getPublicKey();
                        if (isKeyValid(method, key)) {
                            return new KeySelectorResult() {
                                public Key getKey() { return key; }
                            };
                        }
                    }
                }
            }
        }

        // Si no encontramos en X509Data, intentamos con KeyValue
        for (Object o : keyInfo.getContent()) {
            if (o instanceof KeyValue) {
                try {
                    final PublicKey key = ((KeyValue) o).getPublicKey();
                    if (isKeyValid(method, key)) {
                        return new KeySelectorResult() {
                            public Key getKey() { return key; }
                        };
                    }
                } catch (KeyException e) {
                    errorStream.println("Error al obtener la clave pública del KeyValue: " + e.getMessage());
                }
            }
        }

        errorStream.println("Error: No se encontró una clave válida para el algoritmo: " + method.getAlgorithm());
        throw new KeySelectorException("No se encontró una clave válida");
    }

    private boolean isKeyValid(AlgorithmMethod method, Key key) {
        if (key == null) {
            return false;
        }

        String algorithm = method.getAlgorithm();
        String keyAlgorithm = key.getAlgorithm();

        // Verificamos que sea una clave RSA
        if (!"RSA".equalsIgnoreCase(keyAlgorithm)) {
            return false;
        }

        // Aceptamos tanto SHA1 como SHA256
        boolean isValidAlgorithm = algorithm.toLowerCase().contains("rsa-sha1") ||
                algorithm.toLowerCase().contains("rsa-sha256");

        if (!isValidAlgorithm) {
            errorStream.println("Advertencia: Algoritmo no soportado: " + algorithm);
            return false;
        }

        return true;
    }

    public static boolean isSupportedAlgorithm(String algorithm) {
        if (algorithm == null) {
            return false;
        }

        algorithm = algorithm.toLowerCase();
        return algorithm.contains("rsa-sha1") ||
                algorithm.contains("rsa-sha256");
    }
}