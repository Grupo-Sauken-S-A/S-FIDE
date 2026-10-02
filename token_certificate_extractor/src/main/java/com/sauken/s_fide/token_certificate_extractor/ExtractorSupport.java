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

package com.sauken.s_fide.token_certificate_extractor;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import javax.security.auth.x500.X500Principal;

/**
 * Lo que comparten los dos extractores de certificados: dónde se guarda el
 * archivo .pem, cómo se arma su nombre y cómo se informa el estado de
 * revocación del certificado.
 * <p>
 * El .pem se guarda en la carpeta PERSONAL del usuario
 * ({@code <carpeta personal>/S-FiDE}, la misma donde la interfaz gráfica
 * guarda sus valores recordados), no en la carpeta de instalación ni en el
 * directorio de trabajo: la instalación puede ser compartida por varios
 * usuarios y no siempre se puede escribir en ella. La propiedad de sistema
 * {@code sfide.data.dir} redirige esa carpeta (misma que respeta la GUI).
 * <p>
 * Esta clase se duplica en cada módulo que la necesita (misma política de
 * independencia de jars del resto del proyecto).
 */
final class ExtractorSupport {
    static final String FOLDER_NAME = "S-FiDE";
    static final String OVERRIDE_PROPERTY = "sfide.data.dir";

    private ExtractorSupport() {
    }

    /**
     * Carpeta de datos del usuario, creada si hace falta. Si no se puede
     * crear ni escribir en ella, se cae al directorio de trabajo actual
     * (comportamiento anterior) en vez de impedir la extracción.
     */
    static Path userFolder() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        Path candidate = (override != null && !override.isBlank())
                ? Paths.get(override.trim())
                : Paths.get(System.getProperty("user.home", "."), FOLDER_NAME);
        try {
            Files.createDirectories(candidate);
            if (Files.isWritable(candidate)) {
                return candidate.toAbsolutePath();
            }
        } catch (IOException | RuntimeException e) {
            // Se resuelve abajo con el directorio de trabajo.
        }
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
    }

    /** Nombre del .pem: el CN= del sujeto con todo lo que no sea letra, dígito, punto o guion cambiado por "_". */
    static String pemFileName(X509Certificate cert) {
        X500Principal subject = cert.getSubjectX500Principal();
        for (String part : subject.getName().split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3).replaceAll("[^a-zA-Z0-9.-]", "_") + ".pem";
            }
        }
        return "certificate.pem";
    }

    static String toPem(X509Certificate cert) throws IOException {
        try {
            Base64.Encoder encoder = Base64.getMimeEncoder(64, System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
            String certEncoded = encoder.encodeToString(cert.getEncoded());
            return String.format("-----BEGIN CERTIFICATE-----%n%s%n-----END CERTIFICATE-----", certEncoded);
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IOException("no se pudo codificar el certificado", e);
        }
    }

    /**
     * Guarda el certificado como .pem en la carpeta del usuario e informa,
     * en lenguaje llano, qué se generó y dónde quedó.
     */
    static Path exportPem(X509Certificate cert, PrintStream out) throws IOException {
        String fileName = pemFileName(cert);
        Path folder = userFolder();
        Path target = folder.resolve(fileName);
        Files.writeString(target, toPem(cert), StandardCharsets.UTF_8);

        out.println("Clave pública del certificado guardada en un archivo PEM (contiene solo el certificado con su "
                + "clave pública; no incluye la clave privada):");
        out.println("  Archivo: " + fileName);
        out.println("  Carpeta: " + folder);
        return target;
    }

    /**
     * Informa el estado de revocación del certificado. Es solo informativo:
     * nunca hace fallar la extracción, ni siquiera con un certificado
     * revocado — quien extrae un certificado para inspeccionarlo justamente
     * quiere verlo, sea cual sea su estado.
     *
     * @param posiblesEmisores otros certificados del mismo token/archivo, donde suele estar la AC emisora
     */
    static void printRevocationStatus(X509Certificate cert, List<X509Certificate> posiblesEmisores,
                                      boolean omitir, PrintStream out) {
        if (omitir) {
            out.println("Estado de revocación: no verificado (se indicó -omitir-revocacion true)");
            return;
        }
        RevocationValidator.Resultado r = RevocationValidator.validar(cert, posiblesEmisores);
        String via = r.getMetodo() != null ? " (consulta " + r.getMetodo() + ")" : "";
        switch (r.getEstado()) {
            case GOOD -> out.println("Estado de revocación: VIGENTE, el certificado no figura revocado" + via);
            case REVOKED -> out.println("Estado de revocación: REVOCADO, el certificado figura revocado por su "
                    + "autoridad certificante" + via);
            default -> out.println("Estado de revocación: NO SE PUDO VERIFICAR"
                    + (r.getDetalle() != null ? " (" + r.getDetalle() + ")" : ""));
        }
    }
}
