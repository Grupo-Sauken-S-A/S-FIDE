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

package com.sauken.s_fide.pdf_verify_signatures;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.sauken.s_fide.pdf_verify_signatures.validation.RevocationValidator;
import com.sauken.s_fide.pdf_verify_signatures.validation.RevocationValidator.RevocationStatus;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.logging.*;
import java.nio.charset.StandardCharsets;

public class PDFVerifySignatures {
    private static final Logger LOGGER = Logger.getLogger(PDFVerifySignatures.class.getName());
    private static final String VERSION = "S-FIDE PDFVerifySignatures v1.3.0 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT;
    private static final String HELP_TEXT;
    private static final String SEPARATOR = "\n----------------------------------------\n";
    private static boolean simpleOutput = false;

    static {
        Security.addProvider(new BouncyCastleProvider());
        LICENSE_TEXT = readResourceFile("/LICENSE.txt");
        HELP_TEXT = readResourceFile("/HELP.txt");
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = PDFVerifySignatures.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("No se pudo encontrar el recurso: " + resourcePath);
                return "Error: Archivo de recurso no encontrado";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error al leer el archivo de recurso: " + resourcePath);
            return "Error al leer el archivo de recurso";
        }
    }

    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));

            if (args.length == 0) {
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
                    default:
                        break;
                }
            }

            String pdfPath = args[0];
            if (args.length > 1 && "-simple".equalsIgnoreCase(args[1])) {
                simpleOutput = true;
            }

            verifyPDFSignatures(pdfPath);
        } catch (Exception e) {
            LOGGER.severe("Error: " + e.getMessage());
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void showHelp() {
        try {
            PrintStream out = new PrintStream(System.out, true, "UTF-8");
            out.println(HELP_TEXT);
            out.flush();
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error mostrando ayuda: " + e.getMessage());
        }
    }

    private static void verifyPDFSignatures(String pdfPath) throws IOException {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            SignatureUtil signUtil = new SignatureUtil(pdfDoc);
            List<String> names = signUtil.getSignatureNames();

            if (names.isEmpty()) {
                throw new IllegalArgumentException("El documento no contiene firmas digitales.");
            }

            boolean hasErrors = false;
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    System.out.println(SEPARATOR);
                }

                String name = names.get(i);
                System.out.println("Verificando firma #" + (i + 1) + ":");

                try {
                    boolean isValid = verifySignature(signUtil, name);
                    hasErrors |= !isValid;
                } catch (Exception e) {
                    LOGGER.severe("Error verificando firma " + name + ": " + e.getMessage());
                    System.out.println("Error en firma " + name + ": " + e.getMessage());
                    hasErrors = true;
                }
            }

            System.out.println("\n=== RESULTADO FINAL ===");
            if (hasErrors) {
                System.out.println("DOCUMENTO INVÁLIDO: Una o más firmas no son válidas.");
            } else {
                System.out.println("DOCUMENTO VÁLIDO: Todas las firmas son válidas.");
            }

            System.out.println("\nEstado del documento:");
            System.out.println("- Documento bloqueado: " + (isCertified(pdfDoc) ? "Sí" : "No"));
            System.out.println("- Documento encriptado: " + (reader.isEncrypted() ? "Sí" : "No"));

            System.exit(hasErrors ? 1 : 0);
        }
    }

    /**
     * El documento está "bloqueado" cuando tiene una certificación DocMDP (la que aplican
     * PDFSignerPKCS11/PKCS12/WindowsCSP con "-l true"/"Bloquear documento"). Esa marca vive en
     * el diccionario /Perms/DocMDP del catálogo del PDF, no en si el PdfDocument se abrió para
     * escritura — comprobar pdfDoc.getWriter() (como se hacía antes) siempre daba "Sí" porque
     * este módulo solo abre el PDF en modo lectura.
     */
    private static boolean isCertified(PdfDocument pdfDoc) {
        PdfDictionary perms = pdfDoc.getCatalog().getPdfObject().getAsDictionary(PdfName.Perms);
        return perms != null && perms.getAsDictionary(PdfName.DocMDP) != null;
    }

    private static boolean verifySignature(SignatureUtil signUtil, String name)
            throws GeneralSecurityException, IOException {
        PdfPKCS7 pkcs7 = signUtil.readSignatureData(name);

        boolean coversWholeDoc = signUtil.signatureCoversWholeDocument(name);
        System.out.println("Cubre todo el documento: " + (coversWholeDoc ? "Sí" : "No"));

        boolean integrityValid = pkcs7.verifySignatureIntegrityAndAuthenticity();
        System.out.println("Integridad de firma: " + (integrityValid ? "Válida" : "Inválida"));
        if (!integrityValid) return false;

        X509Certificate signingCert = pkcs7.getSigningCertificate();
        if (signingCert == null) {
            System.out.println("Error: No se pudo obtener el certificado firmante");
            return false;
        }

        // Verificar emisor
        String issuerCN = extractCN(signingCert.getIssuerX500Principal().getName());
        if (issuerCN.isEmpty() || issuerCN.toLowerCase().contains("self signed") ||
                issuerCN.toLowerCase().contains("localhost")) {
            System.out.println("\nADVERTENCIA: Certificado no confiable o autofirmado");
            System.out.println("Este certificado podría haber sido generado para uso interno o para realizar pruebas");
            return false;
        }

        Date signDate = pkcs7.getSignDate().getTime();
        System.out.println("Fecha de firma: " + signDate);

        RevocationStatus revocationStatus = RevocationValidator.checkCertificateRevocation(signingCert, signDate);
        System.out.println("Estado de revocación: " + revocationStatus);
        if (revocationStatus == RevocationStatus.REVOKED) {
            System.out.println("Error: Certificado revocado al momento de la firma");
            return false;
        }

        if (!simpleOutput) {
            printSignatureInfo(pkcs7);
        }

        return true;
    }

    private static void printSignatureInfo(PdfPKCS7 pkcs7) {
        System.out.println("\nInformación adicional de la firma:");

        X509Certificate signingCert = pkcs7.getSigningCertificate();
        if (signingCert != null) {
            String dn = signingCert.getSubjectX500Principal().getName();
            System.out.println("Firmante: " + extractCN(dn));
            System.out.println("Organización: " + extractO(dn));
            System.out.println("Número de serie del certificado: " + signingCert.getSerialNumber().toString(16));
            System.out.println("Válido desde: " + signingCert.getNotBefore());
            System.out.println("Válido hasta: " + signingCert.getNotAfter());
            System.out.println("Emisor: " + extractCN(signingCert.getIssuerX500Principal().getName()));
        }

        Calendar signDate = pkcs7.getSignDate();
        if (signDate != null) {
            System.out.println("Fecha y hora de firma: " +
                    String.format("%1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS", signDate));
        }

        System.out.println("Tipo de firma: " + pkcs7.getFilterSubtype());
        System.out.println("Algoritmo de firma: " + getSignatureAlgorithmName(pkcs7));

        try {
            java.security.cert.Certificate[] certChain = pkcs7.getSignCertificateChain();
            if (certChain != null && certChain.length > 1) {
                System.out.println("\nCadena de certificación:");
                for (java.security.cert.Certificate cert : certChain) {
                    if (cert instanceof X509Certificate x509Cert) {
                        System.out.println(" - " + extractCN(x509Cert.getSubjectX500Principal().getName()));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error obteniendo cadena de certificación: " + e.getMessage());
        }
    }

    private static String extractCN(String dn) {
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return "";
    }

    private static String extractO(String dn) {
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("O=")) {
                return part.trim().substring(2);
            }
        }
        return "";
    }

    /**
     * Algoritmo con el que se calculó y firmó esta firma del documento —
     * no confundir con cert.getSigAlgName(), que informa con qué algoritmo la
     * autoridad certificante firmó el certificado del firmante (un dato
     * distinto que puede no coincidir con este). Se reconstruye con la misma
     * convención de nombre que usa la JCA (p. ej. "SHA256withRSA") a partir
     * del digest y el algoritmo de firma que iText ya calculó al leer la
     * firma real del PDF.
     */
    private static String getSignatureAlgorithmName(PdfPKCS7 pkcs7) {
        try {
            return pkcs7.getDigestAlgorithmName() + "with" + pkcs7.getSignatureAlgorithmName();
        } catch (Exception e) {
            return "Desconocido";
        }
    }
}