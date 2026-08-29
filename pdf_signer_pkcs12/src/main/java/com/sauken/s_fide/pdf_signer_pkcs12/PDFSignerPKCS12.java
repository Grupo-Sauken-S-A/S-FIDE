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

package com.sauken.s_fide.pdf_signer_pkcs12;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class PDFSignerPKCS12 {
    private static final Logger logger = Logger.getLogger(PDFSignerPKCS12.class.getName());
    private static final String OUTPUT_SUFFIX = "-signed";
    private static final String VERSION = "S-FIDE PDFSignerPKCS12 v1.0.0 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT = readResourceFile("/LICENSE.txt");
    private static final String HELP_TEXT = readResourceFile("/HELP.txt");

    static {
        Security.addProvider(new BouncyCastleProvider());
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));

            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);
            for (Handler handler : rootLogger.getHandlers()) {
                handler.setFormatter(new SimpleFormatter() {
                    @Override
                    public String format(LogRecord record) {
                        if (record.getLevel() == Level.SEVERE) {
                            return "Error: " + record.getMessage() + "\n";
                        }
                        return record.getMessage() + "\n";
                    }
                });
            }
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error: No se pudo configurar la codificación UTF-8");
            System.exit(1);
        }
    }

    private record SignatureParameters(
            String pdfPath,
            String certPath,
            String password,
            boolean lock,
            float xPos,
            float yPos,
            String customText
    ) {}

    public static void main(String[] args) {
        try {
            if (args.length == 1) {
                processSpecialArgument(args[0]);
                System.exit(0);
                return;
            }

            SignatureParameters params = parseArguments(args);
            if (params == null) {
                showHelp();
                System.exit(1);
                return;
            }

            if (!validateInputs(params)) {
                System.exit(1);
                return;
            }

            signDocument(params);
            System.exit(0);

        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            System.exit(1);
        }
    }

    private static void processSpecialArgument(String arg) {
        String argLower = arg.toLowerCase();
        switch (argLower) {
            case "-v", "--version" -> System.out.println(VERSION);
            case "-h", "--help" -> showHelp();
            default -> {
                logger.log(Level.SEVERE, "Argumento no reconocido: " + arg);
                showHelp();
            }
        }
    }

    private static void showHelp() {
        try {
            PrintStream out = new PrintStream(System.out, true, "UTF-8");
            out.println(HELP_TEXT);
            out.flush();
        } catch (UnsupportedEncodingException e) {
            logger.log(Level.SEVERE, "Error mostrando ayuda: " + e.getMessage());
        }
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = PDFSignerPKCS12.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.log(Level.SEVERE, "No se pudo encontrar el recurso: " + resourcePath);
                return "Error: Archivo de recurso no encontrado";
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error al leer el archivo de recurso: " + resourcePath);
            return "Error al leer el archivo de recurso";
        }
    }

    private static SignatureParameters parseArguments(String[] args) {
        if (args.length < 6) {
            logger.log(Level.SEVERE, "Número insuficiente de argumentos");
            return null;
        }

        String pdfPath = null;
        String certPath = null;
        String password = null;
        boolean lock = false;
        float xPos = 0;
        float yPos = 0;
        String customText = null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-i", "--input" -> {
                        if (i + 1 < args.length) pdfPath = args[++i];
                    }
                    case "-c", "--certificate" -> {
                        if (i + 1 < args.length) certPath = args[++i];
                    }
                    case "-p", "--password" -> {
                        if (i + 1 < args.length) password = args[++i];
                    }
                    case "-l", "--lock" -> {
                        if (i + 1 < args.length) lock = Boolean.parseBoolean(args[++i]);
                    }
                    case "-x", "--xpos" -> {
                        if (i + 1 < args.length) xPos = Float.parseFloat(args[++i]);
                    }
                    case "-y", "--ypos" -> {
                        if (i + 1 < args.length) yPos = Float.parseFloat(args[++i]);
                    }
                    case "-t", "--text" -> {
                        if (i + 1 < args.length) customText = args[++i];
                    }
                    case "-h", "--help" -> {
                        return null;
                    }
                }
            }
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Error al parsear los argumentos numéricos");
            return null;
        }

        if (pdfPath == null || certPath == null || password == null) {
            logger.log(Level.SEVERE, "Faltan argumentos obligatorios");
            return null;
        }

        return new SignatureParameters(pdfPath, certPath, password, lock, xPos, yPos, customText);
    }

    private static boolean validateInputs(SignatureParameters params) {
        Path pdfPath = Paths.get(params.pdfPath());
        Path certPath = Paths.get(params.certPath());

        if (!Files.exists(pdfPath) || !Files.isRegularFile(pdfPath)) {
            logger.log(Level.SEVERE, "El archivo PDF no existe o no es accesible: " + params.pdfPath());
            return false;
        }

        if (!Files.exists(certPath) || !Files.isRegularFile(certPath)) {
            logger.log(Level.SEVERE, "El archivo de certificado no existe o no es accesible: " + params.certPath());
            return false;
        }

        try {
            try (InputStream inputStream = Files.newInputStream(pdfPath);
                 PdfReader reader = new PdfReader(inputStream);
                 PdfDocument pdfDoc = new PdfDocument(reader)) {

                if (reader.isEncrypted()) {
                    logger.log(Level.SEVERE, "El PDF está encriptado y no puede ser firmado");
                    return false;
                }

                SignatureUtil signUtil = new SignatureUtil(pdfDoc);
                List<String> signatures = signUtil.getSignatureNames();

                if (!signatures.isEmpty()) {
                    logger.log(Level.INFO, "Firmas existentes encontradas:");
                    for (String sigName : signatures) {
                        PdfPKCS7 pkcs7 = signUtil.readSignatureData(sigName);
                        if (!pkcs7.verifySignatureIntegrityAndAuthenticity()) {
                            logger.log(Level.SEVERE, "La firma existente '" + sigName + "' no es válida");
                            return false;
                        }
                        logger.log(Level.INFO, "- " + sigName + ": válida");
                    }
                }
            }

            try (InputStream certStream = Files.newInputStream(certPath)) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(certStream, params.password().toCharArray());

                if (!ks.aliases().hasMoreElements()) {
                    logger.log(Level.SEVERE, "El archivo de certificado no contiene certificados");
                    return false;
                }

                String alias = ks.aliases().nextElement();
                if (!ks.isKeyEntry(alias)) {
                    logger.log(Level.SEVERE, "El certificado no contiene una clave privada");
                    return false;
                }

                Certificate[] chain = ks.getCertificateChain(alias);
                if (chain == null || chain.length == 0) {
                    logger.log(Level.SEVERE, "No se encontró una cadena de certificados válida");
                    return false;
                }

                try {
                    PrivateKey privateKey = (PrivateKey) ks.getKey(alias, params.password().toCharArray());
                    if (privateKey == null) {
                        logger.log(Level.SEVERE, "No se pudo obtener la clave privada del certificado");
                        return false;
                    }
                } catch (GeneralSecurityException e) {
                    logger.log(Level.SEVERE, "Error al acceder a la clave privada");
                    return false;
                }
            }

            return true;
        } catch (IOException | GeneralSecurityException e) {
            logger.log(Level.SEVERE, "Error al validar los archivos");
            return false;
        }
    }

    private static String createOutputPath(String inputPath) {
        Path path = Paths.get(inputPath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex < 0) {
            return path.resolveSibling(fileName + OUTPUT_SUFFIX).toString();
        }

        String baseName = fileName.substring(0, dotIndex);
        String extension = fileName.substring(dotIndex);
        return path.resolveSibling(baseName + OUTPUT_SUFFIX + extension).toString();
    }

    private static void signDocument(SignatureParameters params)
            throws GeneralSecurityException, IOException {

        Certificate[] chain;
        PrivateKey privateKey;
        String alias;
        X500Principal subjectDN;

        try (InputStream certStream = Files.newInputStream(Paths.get(params.certPath()))) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(certStream, params.password().toCharArray());

            alias = ks.aliases().nextElement();
            privateKey = (PrivateKey) ks.getKey(alias, params.password().toCharArray());
            chain = ks.getCertificateChain(alias);

            X509Certificate cert = (X509Certificate) chain[0];
            subjectDN = cert.getSubjectX500Principal();
        }

        Path tempPath = Files.createTempFile("sig", ".tmp");
        Path finalOutputPath = Paths.get(createOutputPath(params.pdfPath()));

        PdfSignatureAppearance appearance = null;
        String signatureText = null;

        if (params.xPos() != 0 || params.yPos() != 0) {
            signatureText = buildSignatureText(
                    params.customText(),
                    subjectDN
            );
        }

        try (InputStream inputStream = Files.newInputStream(Paths.get(params.pdfPath()));
             OutputStream outputStream = Files.newOutputStream(tempPath)) {

            PdfReader reader = new PdfReader(inputStream);
            StampingProperties stampingProperties = new StampingProperties();
            stampingProperties.useAppendMode();

            PdfSigner signer = new PdfSigner(reader, outputStream, stampingProperties);

            String fieldName = String.format("Signature_%s_%d",
                    getNameFromDN(subjectDN.getName()).replaceAll("[^a-zA-Z0-9]", "_"),
                    System.currentTimeMillis());
            signer.setFieldName(fieldName);

            if (params.xPos() != 0 || params.yPos() != 0) {
                Rectangle rect = new Rectangle(params.xPos(), params.yPos(), 160, 70);

                appearance = signer.getSignatureAppearance();
                appearance.setPageRect(rect)
                        .setPageNumber(1)
                        .setLayer2FontSize(8.0f)
                        .setLayer2Text(signatureText);

                if (params.lock()) {
                    signer.setCertificationLevel(PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);
                }
            }

            IExternalSignature signature = new PrivateKeySignature(
                    privateKey,
                    DigestAlgorithms.SHA256,
                    BouncyCastleProvider.PROVIDER_NAME
            );

            signer.signDetached(
                    new BouncyCastleDigest(),
                    signature,
                    chain,
                    null,
                    null,
                    null,
                    0,
                    PdfSigner.CryptoStandard.CMS
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error al firmar el documento");
            throw e;
        }

        if (params.lock()) {
            Path finalTempPath = Files.createTempFile("sig_final", ".tmp");

            try (InputStream tempInputStream = Files.newInputStream(tempPath);
                 OutputStream finalOutputStream = Files.newOutputStream(finalTempPath)) {

                WriterProperties writerProps = new WriterProperties()
                        .addXmpMetadata()
                        .setCompressionLevel(CompressionConstants.BEST_COMPRESSION)
                        .setStandardEncryption(
                                null,
                                null,
                                EncryptionConstants.ALLOW_PRINTING |
                                        EncryptionConstants.ALLOW_SCREENREADERS,
                                EncryptionConstants.ENCRYPTION_AES_256 |
                                        EncryptionConstants.DO_NOT_ENCRYPT_METADATA
                        );

                PdfReader reader = new PdfReader(tempInputStream);
                PdfWriter writer = new PdfWriter(finalOutputStream, writerProps);
                new PdfDocument(reader, writer).close();
                reader.close();
                writer.close();

                Files.move(finalTempPath, finalOutputPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tempPath);
                Files.deleteIfExists(finalTempPath);
            }
        } else {
            Files.move(tempPath, finalOutputPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        logger.log(Level.INFO, "Documento firmado exitosamente: " + finalOutputPath.toAbsolutePath());
    }

    private static String buildSignatureText(
            String customText,
            X500Principal subjectDN) {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        StringBuilder text = new StringBuilder();

        if (customText != null && !customText.trim().isEmpty()) {
            text.append(customText).append("\n\n");
        }

        text.append("Firmado digitalmente por:\n")
                .append(getNameFromDN(subjectDN.getName()))
                .append("\nFecha: ")
                .append(timestamp);

        return text.toString();
    }

    private static String getNameFromDN(String dn) {
        return java.util.Arrays.stream(dn.split(","))
                .map(String::trim)
                .filter(part -> part.startsWith("CN="))
                .map(part -> part.substring(3))
                .findFirst()
                .orElse(dn);
    }
}