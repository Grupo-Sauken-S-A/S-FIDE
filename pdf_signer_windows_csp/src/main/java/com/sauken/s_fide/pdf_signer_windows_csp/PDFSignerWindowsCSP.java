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

package com.sauken.s_fide.pdf_signer_windows_csp;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.security.auth.x500.X500Principal;

/**
 * Firma PDF usando un certificado del almacén "Personal" de Windows
 * (Windows-MY vía el provider SunMSCAPI del JDK), como alternativa a
 * PDFSignerPKCS11 cuando el certificado ya está disponible ahí. Solo
 * funciona en Windows.
 */
public class PDFSignerWindowsCSP {
    private static final String OUTPUT_SUFFIX = "-signed";
    private static final String VERSION = "S-FIDE PDFSignerWindowsCSP v1.1.0 - Grupo Sauken S.A.";
    private static PrintStream errorStream;

    static {
        Security.addProvider(new BouncyCastleProvider());
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            errorStream = new PrintStream(System.err, true, "UTF-8");
            System.setErr(errorStream);
        } catch (UnsupportedEncodingException e) {
            System.exit(1);
        }
    }

    private record SignatureParameters(
            String pdfPath,
            String alias,
            boolean lock,
            float xPos,
            float yPos,
            String customText) {
    }

    public static void main(String[] args) {
        try {
            if (!isWindows()) {
                throw new IllegalStateException(
                        "Este módulo solo funciona en Windows (usa el almacén de certificados CSP/KSP de Windows). "
                                + "En otros sistemas operativos use PDFSignerPKCS11.");
            }

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

            signDocument(params);
            System.exit(0);
        } catch (Exception e) {
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void processSpecialArgument(String arg) throws Exception {
        switch (arg.toLowerCase(Locale.ROOT)) {
            case "-v", "--version", "-version" -> System.out.println(VERSION);
            case "-h", "--help", "-ayuda" -> showHelp();
            case "--license", "-licencia" -> System.out.println(readResourceFile("/LICENSE.txt"));
            case "--listar-certificados", "-listar-certificados" -> listarCertificados();
            default -> {
                errorStream.println("Error: Argumento no reconocido: " + arg);
                showHelp();
            }
        }
    }

    private static void showHelp() throws IOException {
        System.out.println(readResourceFile("/HELP.txt"));
    }

    private static String readResourceFile(String resourcePath) throws IOException {
        try (InputStream is = PDFSignerWindowsCSP.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "Error: Archivo de recurso no encontrado";
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static KeyStore loadWindowsKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
        keyStore.load(null, null);
        return keyStore;
    }

    private static void listarCertificados() throws Exception {
        KeyStore keyStore = loadWindowsKeyStore();
        var aliases = Collections.list(keyStore.aliases());
        if (aliases.isEmpty()) {
            System.out.println("No se encontraron certificados en el almacén de Windows (Windows-MY).");
            return;
        }
        for (String alias : aliases) {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            System.out.println("Alias: " + alias);
            if (cert != null) {
                System.out.println("  Subject: " + cert.getSubjectX500Principal().getName());
                System.out.println("  Válido hasta: " + cert.getNotAfter());
                System.out.println("  Tiene clave privada: " + keyStore.isKeyEntry(alias));
            }
            System.out.println();
        }
    }

    private static String resolveAlias(KeyStore keyStore, String aliasOrFragment) throws Exception {
        var aliases = Collections.list(keyStore.aliases());
        if (aliases.contains(aliasOrFragment)) {
            return aliasOrFragment;
        }

        String fragmentLower = aliasOrFragment.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String alias : aliases) {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            if (cert != null && keyStore.isKeyEntry(alias)
                    && cert.getSubjectX500Principal().getName().toLowerCase(Locale.ROOT).contains(fragmentLower)) {
                matches.add(alias);
            }
        }

        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontró ningún certificado con clave privada que coincida con \"" + aliasOrFragment
                            + "\". Use --listar-certificados para ver los disponibles.");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "\"" + aliasOrFragment + "\" coincide con " + matches.size()
                            + " certificados distintos. Sea más específico o use el alias exacto.");
        }
        return matches.get(0);
    }

    private static SignatureParameters parseArguments(String[] args) {
        String pdfPath = null;
        String alias = null;
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
                    case "-a", "--alias" -> {
                        if (i + 1 < args.length) alias = args[++i];
                    }
                    case "-k", "--lock" -> {
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
                    default -> {
                    }
                }
            }
        } catch (NumberFormatException e) {
            errorStream.println("Error: Los valores numéricos proporcionados no son válidos");
            return null;
        }

        if (pdfPath == null || alias == null) {
            errorStream.println("Error: Faltan argumentos obligatorios (-i y -a)");
            return null;
        }

        return new SignatureParameters(pdfPath, alias, lock, xPos, yPos, customText);
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

    private static void signDocument(SignatureParameters params) throws Exception {
        Path pdfPath = Paths.get(params.pdfPath());
        if (!Files.exists(pdfPath) || !Files.isRegularFile(pdfPath)) {
            throw new IllegalArgumentException("El archivo PDF no existe o no es accesible: " + params.pdfPath());
        }

        try (InputStream checkStream = Files.newInputStream(pdfPath);
             PdfReader checkReader = new PdfReader(checkStream);
             PdfDocument checkDoc = new PdfDocument(checkReader)) {

            if (checkReader.isEncrypted()) {
                throw new IllegalArgumentException("El PDF está encriptado y no puede ser firmado");
            }

            SignatureUtil signUtil = new SignatureUtil(checkDoc);
            List<String> existingSignatures = signUtil.getSignatureNames();
            if (!existingSignatures.isEmpty()) {
                System.out.println("Firmas existentes encontradas:");
                for (String sigName : existingSignatures) {
                    PdfPKCS7 pkcs7 = signUtil.readSignatureData(sigName);
                    if (!pkcs7.verifySignatureIntegrityAndAuthenticity()) {
                        throw new IllegalArgumentException("La firma existente '" + sigName + "' no es válida");
                    }
                    System.out.println("- " + sigName + ": válida");
                }
            }
        }

        KeyStore keyStore = loadWindowsKeyStore();
        String alias = resolveAlias(keyStore, params.alias());
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (privateKey == null || chain == null || chain.length == 0) {
            throw new IllegalArgumentException("No se pudo obtener la clave privada o el certificado para: " + alias);
        }
        X509Certificate cert = (X509Certificate) chain[0];
        X500Principal subjectDN = cert.getSubjectX500Principal();

        Path tempPath = Files.createTempFile("sig", ".tmp");
        Path finalOutputPath = Paths.get(createOutputPath(params.pdfPath()));

        try (InputStream inputStream = Files.newInputStream(pdfPath);
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
                PdfSignatureAppearance appearance = signer.getSignatureAppearance();
                Rectangle rect = new Rectangle(params.xPos(), params.yPos(), 160, 70);
                appearance.setPageRect(rect).setPageNumber(1);
                appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);
                appearance.setLayer2Text(buildSignatureText(params.customText(), subjectDN)).setLayer2FontSize(8.0f);
            }

            if (params.lock()) {
                signer.setCertificationLevel(PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);
            }

            IExternalSignature signature = new PrivateKeySignature(
                    privateKey, DigestAlgorithms.SHA256, "SunMSCAPI");

            signer.signDetached(
                    new BouncyCastleDigest(),
                    signature,
                    chain,
                    null, null, null,
                    0,
                    PdfSigner.CryptoStandard.CMS);
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw new IOException("Error al firmar el documento: " + e.getMessage());
        }

        if (params.lock()) {
            Path finalTempPath = Files.createTempFile("sig_final", ".tmp");
            try {
                applyDocumentRestrictions(tempPath, finalTempPath);
                Files.move(finalTempPath, finalOutputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tempPath);
                Files.deleteIfExists(finalTempPath);
            }
        } else {
            Files.move(tempPath, finalOutputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Documento firmado exitosamente: " + finalOutputPath.toAbsolutePath());
    }

    private static void applyDocumentRestrictions(Path sourcePath, Path targetPath) throws IOException {
        try (InputStream tempInputStream = Files.newInputStream(sourcePath);
             OutputStream finalOutputStream = Files.newOutputStream(targetPath)) {

            WriterProperties writerProps = new WriterProperties()
                    .addXmpMetadata()
                    .setCompressionLevel(CompressionConstants.BEST_COMPRESSION)
                    .setStandardEncryption(
                            null, null,
                            EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_SCREENREADERS,
                            EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA);

            PdfReader reader = new PdfReader(tempInputStream);
            PdfWriter writer = new PdfWriter(finalOutputStream, writerProps);
            new PdfDocument(reader, writer).close();
        }
    }

    private static String buildSignatureText(String customText, X500Principal subjectDN) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        StringBuilder text = new StringBuilder();
        if (customText != null && !customText.trim().isEmpty()) {
            text.append(customText).append("\n\n");
        }
        text.append("Firmado digitalmente por:\n")
                .append(getNameFromDN(subjectDN.getName()))
                .append("\nFecha: ").append(timestamp);
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
