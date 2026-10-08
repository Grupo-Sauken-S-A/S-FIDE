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

import com.itextpdf.forms.PdfSigFieldLock;
import com.itextpdf.forms.form.element.SignatureFieldAppearance;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.RevocationValidator;
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
    private static final String VERSION = "S-FIDE PDFSignerPKCS12 v1.5.0 - Grupo Sauken S.A.";
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
            String customText,
            boolean omitirRevocacion,
            String campo,
            int pagina,
            float ancho,
            float alto,
            boolean protegerContenido
    ) {}

    /** Tamaño por defecto del recuadro de la firma visible, en puntos PDF. */
    private static final float ANCHO_FIRMA_POR_DEFECTO = 160f;
    private static final float ALTO_FIRMA_POR_DEFECTO = 70f;
    /** Tamaño de la letra del texto de la firma visible. La vista previa informa este mismo valor. */
    private static final float TAMANO_LETRA_FIRMA = 8.0f;

    public static void main(String[] args) {
        try {
            if (args.length > 1 && isFlagVistaPreviaTexto(args[0])) {
                vistaPreviaTexto(args);
                System.exit(0);
                return;
            }

            if (args.length == 1) {
                processSpecialArgument(args[0]);
                System.exit(0);
                return;
            }

            if (args.length == 3 && isFlagVerificarRevocacion(args[0])) {
                verificarRevocacion(args[1], args[2]);
                System.exit(0);
                return;
            }

            if (args.length == 2 && isFlagAnalizarDocumento(args[0])) {
                PdfDocumentAnalyzer.imprimirInforme(analizarOFallar(Paths.get(args[1]), true), System.out);
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
            case "-v", "--version", "-version" -> System.out.println(VERSION);
            case "-h", "--help", "-ayuda" -> showHelp();
            case "--license", "-licencia" -> System.out.println(LICENSE_TEXT);
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
        boolean omitirRevocacion = false;
        String campo = null;
        int pagina = 1;
        float ancho = ANCHO_FIRMA_POR_DEFECTO;
        float alto = ALTO_FIRMA_POR_DEFECTO;
        boolean protegerContenido = false;

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
                    case "-campo", "--campo" -> {
                        if (i + 1 < args.length) campo = args[++i];
                    }
                    case "-pagina", "--pagina" -> {
                        if (i + 1 < args.length) pagina = Integer.parseInt(args[++i]);
                    }
                    case "-ancho", "--ancho" -> {
                        if (i + 1 < args.length) ancho = Float.parseFloat(args[++i]);
                    }
                    case "-alto", "--alto" -> {
                        if (i + 1 < args.length) alto = Float.parseFloat(args[++i]);
                    }
                    case "-proteger-contenido", "--proteger-contenido" -> {
                        if (i + 1 < args.length) protegerContenido = Boolean.parseBoolean(args[++i]);
                    }
                    case "-omitir-revocacion", "--omitir-revocacion" -> {
                        if (i + 1 < args.length) omitirRevocacion = Boolean.parseBoolean(args[++i]);
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

        return new SignatureParameters(pdfPath, certPath, password, lock, xPos, yPos, customText, omitirRevocacion,
                campo, pagina, ancho, alto, protegerContenido);
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
                    logger.log(Level.SEVERE, motivoDelCifrado(pdfPath));
                    return false;
                }

                // Las firmas previas no se validan acá: eso lo hace PdfDocumentAnalyzer al firmar, que
                // informa lo que encuentra y deja la decisión a la persona en vez de impedir la firma.
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

    /**
     * Un documento cifrado puede estar así porque una versión anterior de S-FiDE (o Acrobat) lo bloqueó
     * al cerrarlo, o porque alguien le puso restricciones de seguridad. Se le explica cuál de los dos.
     */
    private static String motivoDelCifrado(Path pdf) {
        try {
            PdfDocumentAnalyzer.Analisis analisis = PdfDocumentAnalyzer.analizar(pdf, false);
            if (analisis.estado() == PdfDocumentAnalyzer.Estado.CERRADO) {
                return analisis.motivoCierre();
            }
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException ignorada) {
            // Se usa el mensaje general más abajo.
        }
        return "Este documento tiene restricciones de seguridad (está protegido con una contraseña de propietario) "
                + "que impiden agregarle firmas. Pida a quien se lo envió una versión sin restricciones.";
    }

    /**
     * Comprueba, antes de pedir ningún certificado, que lo pedido tenga sentido para este documento:
     * que la página exista, que el recuadro de la firma entre en ella y que la protección del
     * contenido se aplique solo a la primera firma. Los mensajes dicen qué corregir.
     */
    private static void validarPosicionYProteccion(SignatureParameters params,
                                                   PdfDocumentAnalyzer.Analisis analisis) {
        if (params.protegerContenido() && params.lock()) {
            throw new IllegalArgumentException("Elija una sola opción: bloquear el documento (-l true) o proteger "
                    + "su contenido permitiendo firmas posteriores (-proteger-contenido true).");
        }
        if (params.protegerContenido() && !analisis.firmas().isEmpty()) {
            throw new IllegalArgumentException("La protección del contenido solo puede aplicarse con la primera "
                    + "firma del documento, y este ya tiene firmas.");
        }
        if (params.campo() != null || (params.xPos() == 0 && params.yPos() == 0)) {
            return;
        }

        int paginas = analisis.paginas().size();
        if (params.pagina() < 1 || params.pagina() > paginas) {
            throw new IllegalArgumentException("El documento tiene " + paginas + (paginas == 1 ? " página" : " páginas")
                    + " y la página " + params.pagina() + " no existe.");
        }
        if (params.ancho() <= 0 || params.alto() <= 0) {
            throw new IllegalArgumentException("El ancho y el alto del recuadro de la firma deben ser mayores que cero.");
        }

        PdfDocumentAnalyzer.PaginaInfo pagina = analisis.paginas().get(params.pagina() - 1);
        float margen = 0.5f;
        if (params.xPos() < pagina.x() - margen || params.yPos() < pagina.y() - margen
                || params.xPos() + params.ancho() > pagina.x() + pagina.ancho() + margen
                || params.yPos() + params.alto() > pagina.y() + pagina.alto() + margen) {
            throw new IllegalArgumentException("El recuadro de la firma no entra en la página " + params.pagina()
                    + ", que mide " + Math.round(pagina.ancho()) + " x " + Math.round(pagina.alto())
                    + " puntos. Con X=" + params.xPos() + " e Y=" + params.yPos() + " el recuadro de "
                    + params.ancho() + " x " + params.alto() + " se sale de la hoja.");
        }
    }

    private static boolean isFlagVistaPreviaTexto(String arg) {
        return "-vista-previa-texto".equalsIgnoreCase(arg) || "--vista-previa-texto".equalsIgnoreCase(arg);
    }

    /**
     * Modo de solo lectura para la interfaz gráfica: imprime el texto exacto que llevaría la firma visible
     * (con el nombre real del certificado) y el tamaño de letra, sin firmar nada ni tocar ningún documento.
     * Así la vista previa muestra lo que realmente va a quedar, calculado por este mismo programa.
     */
    private static void vistaPreviaTexto(String[] args) throws Exception {
        String certPath = null;
        String password = null;
        String customText = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-c", "--certificate" -> {
                    if (i + 1 < args.length) certPath = args[++i];
                }
                case "-p", "--password" -> {
                    if (i + 1 < args.length) password = args[++i];
                }
                case "-t", "--text" -> {
                    if (i + 1 < args.length) customText = args[++i];
                }
                default -> {
                }
            }
        }
        if (certPath == null || password == null) {
            throw new IllegalArgumentException("Para la vista previa del texto hacen falta el certificado (-c) "
                    + "y su contraseña (-p).");
        }
        Path path = Paths.get(certPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("El archivo de certificado no existe o no es accesible: " + certPath);
        }

        try (InputStream certStream = Files.newInputStream(path)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(certStream, password.toCharArray());
            String alias = ks.aliases().nextElement();
            X500Principal subjectDN = ((X509Certificate) ks.getCertificateChain(alias)[0]).getSubjectX500Principal();

            System.out.println("TEXTO_FIRMA_INICIO");
            System.out.println(buildSignatureText(customText, subjectDN));
            System.out.println("TEXTO_FIRMA_FIN");
            System.out.println("TAMANO_LETRA: " + TAMANO_LETRA_FIRMA);
        }
    }

    private static boolean isFlagAnalizarDocumento(String arg) {
        return "-analizar-documento".equalsIgnoreCase(arg) || "--analizar-documento".equalsIgnoreCase(arg);
    }

    private static boolean isFlagVerificarRevocacion(String arg) {
        return "-verificar-revocacion".equalsIgnoreCase(arg) || "--verificar-revocacion".equalsIgnoreCase(arg);
    }

    /**
     * Consulta el estado de revocación del certificado sin firmar nada — pensado
     * para que la GUI decida, antes de invocar la firma real, si debe pedir
     * confirmación al usuario. Imprime "ESTADO_REVOCACION: GOOD|UNKNOWN|REVOKED"
     * (y "DETALLE: ..." si corresponde) en un formato estable pensado para ser
     * parseado por otro programa, no solo leído por una persona.
     */
    private static void verificarRevocacion(String certPath, String password) throws Exception {
        Path path = Paths.get(certPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("El archivo de certificado no existe o no es accesible: " + certPath);
        }

        try (InputStream certStream = Files.newInputStream(path)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(certStream, password.toCharArray());

            String alias = ks.aliases().nextElement();
            Certificate[] chain = ks.getCertificateChain(alias);
            X509Certificate cert = (X509Certificate) chain[0];

            RevocationValidator.Resultado resultado = RevocationValidator.validarAntesDeFirmar(cert);
            System.out.println("ESTADO_REVOCACION: " + resultado.getEstado());
            if (resultado.getDetalle() != null) {
                System.out.println("DETALLE: " + resultado.getDetalle());
            }
        }
    }

    private static void validarRevocacionAntesDeFirmar(X509Certificate cert, boolean omitirRevocacion) {
        RevocationValidator.Resultado resultado = RevocationValidator.validarAntesDeFirmar(cert);

        switch (resultado.getEstado()) {
            case REVOKED -> {
                if (!omitirRevocacion) {
                    throw new IllegalArgumentException("El certificado de firma está revocado. No se puede firmar. "
                            + "Si necesita forzar la firma de todas formas, use -omitir-revocacion true.");
                }
                logger.log(Level.WARNING, "El certificado de firma está revocado, pero se continúa de todas "
                        + "formas porque se indicó -omitir-revocacion true.");
            }
            case UNKNOWN -> logger.log(Level.WARNING, "No se pudo verificar el estado de revocación del "
                    + "certificado de firma (" + resultado.getDetalle() + "). Se continúa con la firma.");
            case GOOD -> logger.log(Level.INFO, "Certificado de firma verificado: no está revocado"
                    + (resultado.getMetodo() != null ? " (" + resultado.getMetodo() + ")" : "") + ".");
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

        // Paso 1, sin red: ¿el documento todavía admite firmas? Un documento cerrado se rechaza acá,
        // antes de pedir ningún certificado ni hacer ninguna consulta.
        PdfDocumentAnalyzer.Analisis analisisInicial = analizarOFallar(Paths.get(params.pdfPath()), false);
        if (analisisInicial.estado() == PdfDocumentAnalyzer.Estado.CERRADO) {
            throw new IllegalArgumentException(analisisInicial.motivoCierre());
        }
        boolean documentoYaFirmado = !analisisInicial.firmas().isEmpty();
        validarPosicionYProteccion(params, analisisInicial);

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

            validarRevocacionAntesDeFirmar(cert, params.omitirRevocacion());
        }

        // Paso 2: validar una por una las firmas previas. Si alguna no es válida se informa y se
        // continúa: la decisión de firmar de todas formas es de la persona, no del programa.
        if (documentoYaFirmado) {
            PdfDocumentAnalyzer.imprimirInforme(analizarOFallar(Paths.get(params.pdfPath()), true), System.out);
        }

        // Si el documento ya tiene firmas, "bloquear" se transforma en una firma de cierre común:
        // cifrar o certificar exigiría reescribir el archivo (invalidando las firmas previas) o ya
        // no sería posible (la certificación debe ser la primera firma del documento).
        boolean modoCierre = params.lock() && documentoYaFirmado;
        boolean bloquearConCifrado = params.lock() && !documentoYaFirmado;

        Path finalOutputPath = Paths.get(createOutputPath(params.pdfPath()));
        Path encryptedIntermediate = null;

        String signatureText = null;

        // Firmar dentro de un campo de firma vacío que ya trae el documento (como hace Acrobat) tiene
        // prioridad sobre las coordenadas: el campo ya define página y lugar.
        if (params.campo() != null && !analisisInicial.camposVacios().stream()
                .anyMatch(c -> c.nombre().equals(params.campo()))) {
            String disponibles = analisisInicial.camposVacios().isEmpty() ? "el documento no tiene ninguno"
                    : analisisInicial.camposVacios().stream()
                    .map(PdfDocumentAnalyzer.CampoVacio::nombre).collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException("El campo de firma '" + params.campo() + "' no existe en el documento "
                    + "o ya está firmado. Campos de firma disponibles: " + disponibles + ".");
        }

        if (params.campo() != null || params.xPos() != 0 || params.yPos() != 0) {
            signatureText = buildSignatureText(
                    params.customText(),
                    subjectDN
            );
        }

        try {
            Path sourceForSigning;
            byte[] ownerPassword = null;

            if (bloquearConCifrado) {
                // Si hay que bloquear, primero se cifra el documento ORIGINAL (todavía sin
                // firmar) y recién después se firma en modo append sobre ese archivo ya
                // cifrado — es el orden que espera iText para combinar cifrado y firma.
                // Hacerlo al revés (firmar primero y volver a serializar todo el documento
                // con cifrado en una segunda pasada) corrompe el /Contents de la firma: esa
                // segunda pasada no sabe que ese campo debe quedar exento de cifrado, y el
                // PKCS#7 queda ilegible al verificarlo ("Cannot decode PKCS#7 SignedData
                // object").
                encryptedIntermediate = Files.createTempFile("enc", ".tmp");
                ownerPassword = generateRandomOwnerPassword();

                try (InputStream inputStream = Files.newInputStream(Paths.get(params.pdfPath()));
                     OutputStream outputStream = Files.newOutputStream(encryptedIntermediate)) {

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

                    PdfReader reader = new PdfReader(inputStream);
                    PdfWriter writer = new PdfWriter(outputStream, writerProps);
                    new PdfDocument(reader, writer).close();
                }

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

                if (params.campo() != null) {
                    signerProperties.setFieldName(params.campo())
                            .setSignatureAppearance(new SignatureFieldAppearance(params.campo())
                                    .setContent(signatureText)
                                    .setFontSize(TAMANO_LETRA_FIRMA));
                } else if (params.xPos() != 0 || params.yPos() != 0) {
                    Rectangle rect = new Rectangle(params.xPos(), params.yPos(), params.ancho(), params.alto());

                    SignatureFieldAppearance appearance = new SignatureFieldAppearance(fieldName)
                            .setContent(signatureText)
                            .setFontSize(TAMANO_LETRA_FIRMA);
                    signerProperties.setPageRect(rect)
                            .setPageNumber(params.pagina())
                            .setSignatureAppearance(appearance);
                }

                if (bloquearConCifrado) {
                    signerProperties.setCertificationLevel(PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);
                }
                if (params.protegerContenido()) {
                    // Certificación que protege el contenido pero admite más firmas (nivel 2 de DocMDP): no
                    // se cifra ni se bloquea, así las personas siguientes pueden seguir firmando.
                    signerProperties.setCertificationLevel(PdfSigner.CERTIFIED_FORM_FILLING);
                }
                if (modoCierre) {
                    signerProperties.setReason(PdfDocumentAnalyzer.MARCA_CIERRE);
                    // Mismo bloqueo que aplica Acrobat con "Bloquear documento después de firmar": así Acrobat
                    // también muestra el documento como bloqueado, y no depende de que quien lo abra use S-FiDE.
                    PdfSigFieldLock bloqueo = new PdfSigFieldLock()
                            .setFieldLock(PdfSigFieldLock.LockAction.ALL)
                            .setDocumentPermissions(PdfSigFieldLock.LockPermissions.NO_CHANGES_ALLOWED);
                    // Con la acción "todos" Acrobat no escribe la lista de campos; iText agrega una vacía
                    // y con ella Acrobat no reconoce el bloqueo.
                    bloqueo.getPdfObject().remove(PdfName.Fields);
                    signerProperties.setFieldLockDict(bloqueo);
                }

                PdfSigner signer = new PdfSigner(reader, outputStream, null, stampingProperties, signerProperties);

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
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error al firmar el documento");
            throw e;
        } finally {
            if (encryptedIntermediate != null) {
                Files.deleteIfExists(encryptedIntermediate);
            }
        }

        controlarResultado(finalOutputPath, analisisInicial);

        logger.log(Level.INFO, "Documento firmado exitosamente: " + finalOutputPath.toAbsolutePath());
        if (modoCierre) {
            logger.log(Level.INFO, "El documento quedó cerrado: no admite más firmas.");
        }
    }

    private static PdfDocumentAnalyzer.Analisis analizarOFallar(Path pdf, boolean consultarRevocacion) {
        try {
            return PdfDocumentAnalyzer.analizar(pdf, consultarRevocacion);
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Autocontrol después de firmar: relee el archivo generado y comprueba que se agregó
     * exactamente una firma, que es íntegra, y que ninguna de las firmas que estaban intactas
     * antes dejó de estarlo. Si algo no cuadra se borra el resultado: es preferible no entregar
     * nada a entregar un documento con firmas previas dañadas sin que nadie lo advierta.
     */
    private static void controlarResultado(Path salida, PdfDocumentAnalyzer.Analisis antes) throws IOException {
        List<PdfDocumentAnalyzer.FirmaPrevia> firmasAntes = antes.firmas();
        List<PdfDocumentAnalyzer.FirmaPrevia> firmasDespues;
        try {
            firmasDespues = PdfDocumentAnalyzer.analizar(salida, false).firmas();
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            firmasDespues = List.of();
        }

        boolean correcto = firmasDespues.size() == firmasAntes.size() + 1
                && firmasDespues.get(firmasDespues.size() - 1).integra();
        for (int i = 0; correcto && i < firmasAntes.size(); i++) {
            correcto = !firmasAntes.get(i).integra() || firmasDespues.get(i).integra();
        }

        if (!correcto) {
            Files.deleteIfExists(salida);
            throw new IllegalStateException("No se pudo agregar la firma sin dañar el documento. "
                    + "No se generó ningún archivo; el documento original no fue modificado.");
        }
    }

    /**
     * Genera una contraseña de propietario aleatoria, usada únicamente en memoria
     * durante el proceso de firma para poder reabrir con permisos plenos el PDF
     * intermedio ya cifrado. Nunca se persiste ni se informa al usuario: el PDF
     * final queda con lectura libre (contraseña de usuario vacía) pero con
     * permisos de edición restringidos, sin que nadie —ni siquiera S-FiDE—
     * conserve la contraseña de propietario después de este proceso.
     */
    private static byte[] generateRandomOwnerPassword() {
        byte[] password = new byte[16];
        new java.security.SecureRandom().nextBytes(password);
        return password;
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