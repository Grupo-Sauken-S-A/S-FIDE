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
import com.sauken.s_fide.pdf_verify_signatures.validation.PdfDocumentAnalyzer;
import java.io.*;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.logging.*;
import java.nio.charset.StandardCharsets;

public class PDFVerifySignatures {
    private static final Logger LOGGER = Logger.getLogger(PDFVerifySignatures.class.getName());
    private static final String VERSION = "S-FIDE PDFVerifySignatures v1.5.0 - Grupo Sauken S.A.";
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
        PdfDocumentAnalyzer.Analisis analisis;
        try {
            analisis = PdfDocumentAnalyzer.analizar(Paths.get(pdfPath), true);
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (analisis.firmas().isEmpty()) {
            throw new IllegalArgumentException("El documento no contiene firmas digitales.");
        }

        boolean hasErrors = analisis.hayProblemas();
        boolean firmasInvalidas = analisis.firmas().stream()
                .anyMatch(f -> f.veredicto() == PdfDocumentAnalyzer.Veredicto.INVALIDA);
        boolean comprobacionesIncompletas = analisis.firmas().stream()
                .anyMatch(f -> f.veredicto() == PdfDocumentAnalyzer.Veredicto.NO_VERIFICABLE);

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            SignatureUtil signUtil = new SignatureUtil(pdfDoc);

            for (PdfDocumentAnalyzer.FirmaPrevia firma : analisis.firmas()) {
                if (firma.numero() > 1) {
                    System.out.println(SEPARATOR);
                }
                System.out.println((firma.esSelloDeTiempo() ? "Verificando sello de tiempo #" : "Verificando firma #")
                        + firma.numero() + ":");
                System.out.println("Resultado: " + etiqueta(firma));
                System.out.println(firma.mensaje());
                for (String nota : firma.notas()) {
                    System.out.println("Nota: " + nota);
                }

                if (firma.esSelloDeTiempo()) {
                    System.out.println("Fecha del sello: " + fechaLegible(firma.fecha()));
                } else {
                    hasErrors |= imprimirDetalle(signUtil, firma);
                }
            }

            System.out.println("\n=== RESULTADO FINAL ===");
            switch (analisis.cambios().nivel()) {
                case INFORMATIVO -> System.out.println("Nota: " + analisis.cambios().mensaje());
                case AVISO, ALERTA -> System.out.println("Aviso: " + analisis.cambios().mensaje());
                case NINGUNO -> {
                }
            }

            if (hasErrors) {
                System.out.println(firmasInvalidas || !analisis.hayProblemas()
                        ? "DOCUMENTO INVÁLIDO: Una o más firmas no son válidas."
                        : "DOCUMENTO INVÁLIDO: El documento fue modificado después de la última firma.");
            } else {
                System.out.println("DOCUMENTO VÁLIDO: Todas las firmas son válidas.");
                if (comprobacionesIncompletas) {
                    System.out.println("Algunas comprobaciones no pudieron completarse (vea el detalle de cada firma). "
                            + "Eso no invalida el documento.");
                }
            }

            System.out.println("\nEstado del documento:");
            boolean cerrado = analisis.estado() == PdfDocumentAnalyzer.Estado.CERRADO;
            System.out.println("- Documento bloqueado: " + (cerrado ? "Sí" : "No"));
            if (cerrado) {
                System.out.println("  " + analisis.motivoCierre());
            }
            if (analisis.contenidoProtegido()) {
                System.out.println("- Contenido protegido contra cambios (admite más firmas): Sí");
            }
            System.out.println("- Documento encriptado: " + (reader.isEncrypted() ? "Sí" : "No"));
            if (!analisis.camposVacios().isEmpty()) {
                System.out.println("- Campos de firma todavía sin firmar: " + analisis.camposVacios().stream()
                        .map(PdfDocumentAnalyzer.CampoVacio::nombre).collect(java.util.stream.Collectors.joining(", ")));
            }

            System.exit(hasErrors ? 1 : 0);
        }
    }

    private static String etiqueta(PdfDocumentAnalyzer.FirmaPrevia firma) {
        return switch (firma.veredicto()) {
            case VALIDA -> "VÁLIDA";
            // Si la firma está íntegra solo falta una comprobación externa (vigencia, revocación): no es un problema.
            case NO_VERIFICABLE -> firma.integra() ? "VÁLIDA, CON COMPROBACIONES PENDIENTES"
                    : "NO SE PUDO COMPROBAR";
            case INVALIDA -> "NO VÁLIDA";
        };
    }

    private static String fechaLegible(Date fecha) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(fecha);
    }

    /**
     * Imprime los datos técnicos de una firma de persona. Devuelve {@code true} si el certificado debe
     * rechazarse por ser claramente de prueba o autofirmado (regla que ya tenía este verificador).
     */
    private static boolean imprimirDetalle(SignatureUtil signUtil, PdfDocumentAnalyzer.FirmaPrevia firma) {
        System.out.println("Integridad de firma: " + (firma.integra() ? "Válida"
                : firma.veredicto() == PdfDocumentAnalyzer.Veredicto.NO_VERIFICABLE ? "No comprobable" : "Inválida"));
        if (firma.fecha() != null) {
            System.out.println("Fecha de firma: " + fechaLegible(firma.fecha()));
        }
        if (firma.detalleRevocacion() != null) {
            System.out.println("Estado de revocación: " + firma.detalleRevocacion());
        }

        PdfPKCS7 pkcs7;
        try {
            pkcs7 = signUtil.readSignatureData(firma.campo());
        } catch (RuntimeException e) {
            return false;
        }

        boolean certificadoNoConfiable = false;
        X509Certificate cert = pkcs7.getSigningCertificate();
        if (firma.integra() && cert != null) {
            String issuerCN = extractCN(cert.getIssuerX500Principal().getName());
            if (issuerCN.isEmpty() || issuerCN.toLowerCase().contains("self signed")
                    || issuerCN.toLowerCase().contains("localhost")) {
                System.out.println("\nADVERTENCIA: Certificado no confiable o autofirmado");
                System.out.println("Este certificado podría haber sido generado para uso interno o para realizar pruebas");
                certificadoNoConfiable = true;
            }
        }

        if (!simpleOutput) {
            printSignatureInfo(pkcs7);
        }
        return certificadoNoConfiable;
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