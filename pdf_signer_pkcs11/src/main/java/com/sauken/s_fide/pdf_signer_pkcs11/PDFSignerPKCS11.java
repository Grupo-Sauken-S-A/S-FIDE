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

package com.sauken.s_fide.pdf_signer_pkcs11;

import com.itextpdf.forms.form.element.SignatureFieldAppearance;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PDFSignerPKCS11 {
    private static final Logger logger = Logger.getLogger(PDFSignerPKCS11.class.getName());
    private static final String OUTPUT_SUFFIX = "-signed";
    private static final String VERSION = "S-FIDE PDFSignerPKCS11 v1.1.1 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT = readResourceFile("/LICENSE.txt");
    private static final String HELP_TEXT = readResourceFile("/HELP.txt");
    private static PrintStream errorStream;

    static {
        Security.addProvider(new BouncyCastleProvider());
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            errorStream = new PrintStream(System.err, true, "UTF-8");
            System.setErr(errorStream);
        } catch (UnsupportedEncodingException e) {
            logger.log(Level.WARNING, "No se pudo configurar UTF-8 para la salida");
            System.exit(1);
        }
    }

    private record SignatureParameters(
            String pdfPath,
            String libraryPath,
            String password,
            int slotNumber,
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
            logger.log(Level.SEVERE, "Error en la ejecución: {0}", e.getMessage());
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processSpecialArgument(String arg) {
        String argLower = arg.toLowerCase();
        switch (argLower) {
            case "-v", "--version", "-version" -> System.out.println(VERSION);
            case "-h", "--help", "-ayuda" -> showHelp();
            case "--license", "-licencia" -> System.out.println(LICENSE_TEXT);
            case "--listar-drivers", "-listar-drivers" -> System.out.println(TokenProfileCatalog.formatTableForCurrentOs());
            default -> {
                errorStream.println("Error: Argumento no reconocido: " + arg);
                showHelp();
            }
        }
    }

    private static void showHelp() {
        System.out.println(HELP_TEXT);
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = PDFSignerPKCS11.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.log(Level.SEVERE, "No se pudo encontrar el recurso: {0}", resourcePath);
                return "Error: Archivo de recurso no encontrado";
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error al leer el archivo de recurso: {0}", e.getMessage());
            return "Error al leer el archivo de recurso";
        }
    }

    private static SignatureParameters parseArguments(String[] args) {
        if (args.length < 8) {
            logger.log(Level.SEVERE, "Número insuficiente de argumentos");
            return null;
        }

        String pdfPath = null;
        String libraryPath = null;
        String password = null;
        int slotNumber = -1;
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
                    case "-l", "--library" -> {
                        if (i + 1 < args.length) libraryPath = args[++i];
                    }
                    case "-p", "--password" -> {
                        if (i + 1 < args.length) password = args[++i];
                    }
                    case "-s", "--slot" -> {
                        if (i + 1 < args.length) slotNumber = Integer.parseInt(args[++i]);
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
                }
            }
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Error al parsear los argumentos numéricos");
            errorStream.println("Error: Los valores numéricos proporcionados no son válidos");
            return null;
        }

        if (pdfPath == null || libraryPath == null || password == null || slotNumber < 0) {
            logger.log(Level.SEVERE, "Faltan argumentos obligatorios");
            errorStream.println("Error: Faltan argumentos obligatorios");
            return null;
        }

        return new SignatureParameters(pdfPath, libraryPath, password, slotNumber, lock, xPos, yPos, customText);
    }

    private static boolean validateInputs(SignatureParameters params) {
        Path pdfPath = Paths.get(params.pdfPath());
        Path libraryPath = Paths.get(params.libraryPath());

        if (!Files.exists(pdfPath) || !Files.isRegularFile(pdfPath)) {
            errorStream.println("Error: El archivo PDF no existe o no es accesible: " + params.pdfPath());
            return false;
        }

        if (!Files.exists(libraryPath) || !Files.isRegularFile(libraryPath)) {
            errorStream.println("Error: La biblioteca PKCS#11 no existe o no es accesible: " + params.libraryPath());
            return false;
        }

        try {
            // Validar el PDF
            try (InputStream inputStream = Files.newInputStream(pdfPath);
                 PdfReader reader = new PdfReader(inputStream);
                 PdfDocument pdfDoc = new PdfDocument(reader)) {

                if (reader.isEncrypted()) {
                    errorStream.println("Error: El PDF está encriptado y no puede ser firmado");
                    return false;
                }

                // Verificar firmas existentes
                SignatureUtil signUtil = new SignatureUtil(pdfDoc);
                List<String> signatures = signUtil.getSignatureNames();

                if (!signatures.isEmpty()) {
                    System.out.println("Firmas existentes encontradas:");
                    for (String sigName : signatures) {
                        PdfPKCS7 pkcs7 = signUtil.readSignatureData(sigName);
                        if (!pkcs7.verifySignatureIntegrityAndAuthenticity()) {
                            errorStream.println("Error: La firma existente '" + sigName + "' no es válida");
                            return false;
                        }
                        System.out.println("- " + sigName + ": válida");
                    }
                }
            }

            // Validar el token PKCS11
            Provider provider = null;
            try {
                provider = configurePKCS11Provider(params.libraryPath(), params.slotNumber());
                Security.addProvider(provider);

                try {
                    KeyStore keyStore = loadKeyStore(params.password());
                    String alias = keyStore.aliases().nextElement();

                    if (!keyStore.isKeyEntry(alias)) {
                        errorStream.println("Error: El token no contiene una clave privada válida");
                        return false;
                    }

                    Certificate[] chain = keyStore.getCertificateChain(alias);
                    if (chain == null || chain.length == 0) {
                        errorStream.println("Error: No se encontró una cadena de certificados válida en el token");
                        return false;
                    }

                    try {
                        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, params.password().toCharArray());
                        if (privateKey == null) {
                            errorStream.println("Error: No se pudo obtener la clave privada del token");
                            return false;
                        }
                    } catch (UnrecoverableKeyException | NoSuchAlgorithmException e) {
                        errorStream.println("Error: Error al acceder a la clave privada del token");
                        return false;
                    }
                } catch (GeneralSecurityException e) {
                    errorStream.println("Error: Error al cargar el KeyStore");
                    return false;
                }

                return true;

            } finally {
                if (provider != null) {
                    Security.removeProvider(provider.getName());
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            errorStream.println("Error: " + e.getMessage());
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

    private static Provider configurePKCS11Provider(String libraryPath, int slotNumber) {
        String config = String.format(
                "--name=PDFSignerProvider\nlibrary=%s\nslotListIndex=%d",
                sanitizeLibraryPathForPkcs11Config(libraryPath),
                slotNumber);

        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new IllegalArgumentException("Error: Proveedor SunPKCS11 no disponible");
        }
        return provider.configure(config);
    }

    /**
     * El parser de configuración de SunPKCS11 trata la barra invertida como
     * carácter de escape, por lo que una ruta de Windows sin convertir (aun
     * entre comillas) falla al configurar el proveedor. Se reemplaza "\" por
     * "/" (aceptado igual por el cargador nativo de la biblioteca) y se
     * encierra el valor entre comillas para tolerar espacios en el path.
     */
    private static String sanitizeLibraryPathForPkcs11Config(String path) {
        return "\"" + path.replace('\\', '/') + "\"";
    }

    private static KeyStore loadKeyStore(String password) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, password.toCharArray());
            return keyStore;
        } catch (IOException e) {
            throw new GeneralSecurityException("Error: Contraseña incorrecta o error al acceder al token");
        } catch (Exception e) {
            throw new GeneralSecurityException("Error: Error al cargar el KeyStore");
        }
    }

    private static void signDocument(SignatureParameters params)
            throws GeneralSecurityException, IOException {
        Provider provider = null;
        try {
            provider = configurePKCS11Provider(params.libraryPath(), params.slotNumber());
            Security.addProvider(provider);

            Certificate[] chain;
            PrivateKey privateKey;
            String alias;
            X500Principal subjectDN;

            try {
                KeyStore keyStore = loadKeyStore(params.password());
                alias = keyStore.aliases().nextElement();
                privateKey = (PrivateKey) keyStore.getKey(alias, params.password().toCharArray());
                chain = keyStore.getCertificateChain(alias);
                X509Certificate cert = (X509Certificate) chain[0];
                subjectDN = cert.getSubjectX500Principal();
            } catch (Exception e) {
                throw new GeneralSecurityException("Error: Error al acceder al token");
            }

            Path finalOutputPath = Paths.get(createOutputPath(params.pdfPath()));
            Path encryptedIntermediate = null;

            try {
                Path sourceForSigning;
                byte[] ownerPassword = null;

                if (params.lock()) {
                    // Si hay que bloquear, primero se cifra el documento ORIGINAL (todavía
                    // sin firmar) y recién después se firma en modo append sobre ese archivo
                    // ya cifrado — es el orden que espera iText para combinar cifrado y
                    // firma. Hacerlo al revés (firmar primero y volver a serializar todo el
                    // documento con cifrado en una segunda pasada) corrompe el /Contents de
                    // la firma: esa segunda pasada no sabe que ese campo debe quedar exento
                    // de cifrado.
                    encryptedIntermediate = Files.createTempFile("enc", ".tmp");
                    ownerPassword = applyDocumentRestrictions(Paths.get(params.pdfPath()), encryptedIntermediate);
                    sourceForSigning = encryptedIntermediate;
                } else {
                    sourceForSigning = Paths.get(params.pdfPath());
                }

                try (InputStream inputStream = Files.newInputStream(sourceForSigning);
                     OutputStream outputStream = Files.newOutputStream(finalOutputPath)) {

                    // Al firmar el intermedio ya cifrado hace falta abrirlo con la contraseña
                    // de propietario: agregar una firma de certificación modifica los permisos
                    // del documento, y eso requiere acceso de propietario, no solo de lectura.
                    PdfReader reader = (ownerPassword != null)
                            ? new PdfReader(inputStream, new ReaderProperties().setPassword(ownerPassword))
                            : new PdfReader(inputStream);
                    StampingProperties stampingProperties = new StampingProperties();
                    stampingProperties.useAppendMode();

                    String fieldName = String.format("Signature_%s_%d",
                            getNameFromDN(subjectDN.getName()).replaceAll("[^a-zA-Z0-9]", "_"),
                            System.currentTimeMillis());

                    SignerProperties signerProperties = new SignerProperties().setFieldName(fieldName);

                    if (params.xPos() != 0 || params.yPos() != 0) {
                        Rectangle rect = new Rectangle(params.xPos(), params.yPos(), 160, 70);
                        String signatureText = buildSignatureText(
                                params.customText(),
                                subjectDN
                        );
                        SignatureFieldAppearance appearance = new SignatureFieldAppearance(fieldName)
                                .setContent(signatureText)
                                .setFontSize(8.0f);
                        signerProperties.setPageRect(rect)
                                .setPageNumber(1)
                                .setSignatureAppearance(appearance);
                    }

                    if (params.lock()) {
                        signerProperties.setCertificationLevel(PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);
                    }

                    PdfSigner signer = new PdfSigner(reader, outputStream, null, stampingProperties, signerProperties);

                    Pkcs11ExternalSignature signature = new Pkcs11ExternalSignature(privateKey, provider);

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

                    if (signature.lastSignUsedExternalHash()) {
                        System.out.println("Mecanismo de firma: hash SHA-256 externo (token sin CKM_SHA256_RSA_PKCS)");
                    }
                }
            } catch (Exception e) {
                Files.deleteIfExists(finalOutputPath);
                throw new IOException("Error: Error al firmar el documento");
            } finally {
                if (encryptedIntermediate != null) {
                    Files.deleteIfExists(encryptedIntermediate);
                }
            }

            System.out.println("Documento firmado exitosamente: " + finalOutputPath.toAbsolutePath());

        } finally {
            if (provider != null) {
                Security.removeProvider(provider.getName());
            }
        }
    }

    /**
     * Cifra el PDF de origen y devuelve la contraseña de propietario generada,
     * necesaria para poder reabrir el resultado con permisos plenos durante la
     * firma que sigue a continuación. Nunca se persiste ni se informa al
     * usuario: el PDF final queda con lectura libre (contraseña de usuario
     * vacía) pero con permisos de edición restringidos, sin que nadie —ni
     * siquiera S-FiDE— conserve la contraseña de propietario después de este
     * proceso.
     */
    private static byte[] applyDocumentRestrictions(Path sourcePath, Path targetPath) throws IOException {
        byte[] ownerPassword = new byte[16];
        new java.security.SecureRandom().nextBytes(ownerPassword);

        try (InputStream tempInputStream = Files.newInputStream(sourcePath);
             OutputStream finalOutputStream = Files.newOutputStream(targetPath)) {

            WriterProperties writerProps = new WriterProperties()
                    .addXmpMetadata()
                    .setCompressionLevel(CompressionConstants.BEST_COMPRESSION)
                    .setStandardEncryption(
                            null,
                            ownerPassword,
                            EncryptionConstants.ALLOW_PRINTING |
                                    EncryptionConstants.ALLOW_SCREENREADERS,
                            EncryptionConstants.ENCRYPTION_AES_256 |
                                    EncryptionConstants.DO_NOT_ENCRYPT_METADATA
                    );

            PdfReader reader = new PdfReader(tempInputStream);
            PdfWriter writer = new PdfWriter(finalOutputStream, writerProps);
            new PdfDocument(reader, writer).close();
        }

        return ownerPassword;
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