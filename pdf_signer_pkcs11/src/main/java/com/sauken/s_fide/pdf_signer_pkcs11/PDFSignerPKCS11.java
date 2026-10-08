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

import com.itextpdf.forms.PdfSigFieldLock;
import com.itextpdf.forms.form.element.SignatureFieldAppearance;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.sauken.s_fide.pdf_signer_pkcs11.validation.PdfDocumentAnalyzer;
import com.sauken.s_fide.pdf_signer_pkcs11.validation.RevocationValidator;
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
    private static final String VERSION = "S-FIDE PDFSignerPKCS11 v1.5.0 - Grupo Sauken S.A.";
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

            if (args.length == 4 && isFlagVerificarRevocacion(args[0])) {
                verificarRevocacion(args[1], args[2], args[3]);
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
            errorStream.println("Error: Los valores numéricos proporcionados no son válidos");
            return null;
        }

        if (pdfPath == null || libraryPath == null || password == null || slotNumber < 0) {
            logger.log(Level.SEVERE, "Faltan argumentos obligatorios");
            errorStream.println("Error: Faltan argumentos obligatorios");
            return null;
        }

        return new SignatureParameters(pdfPath, libraryPath, password, slotNumber, lock, xPos, yPos, customText,
                omitirRevocacion, campo, pagina, ancho, alto, protegerContenido);
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
    private static void verificarRevocacion(String libraryPath, String password, String slotArg) throws Exception {
        File library = new File(libraryPath);
        if (!library.exists()) {
            throw new IllegalArgumentException("La biblioteca PKCS#11 no existe o no es accesible: " + libraryPath);
        }

        int slotNumber;
        try {
            slotNumber = Integer.parseInt(slotArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error: El número de slot debe ser un valor numérico.");
        }

        Provider provider = configurePKCS11Provider(libraryPath, slotNumber);
        Security.addProvider(provider);
        try {
            KeyStore keyStore = loadKeyStore(password, libraryPath);
            String alias = keyStore.aliases().nextElement();
            Certificate[] chain = keyStore.getCertificateChain(alias);
            X509Certificate cert = (X509Certificate) chain[0];

            RevocationValidator.Resultado resultado = RevocationValidator.validarAntesDeFirmar(cert);
            System.out.println("ESTADO_REVOCACION: " + resultado.getEstado());
            if (resultado.getDetalle() != null) {
                System.out.println("DETALLE: " + resultado.getDetalle());
            }
        } finally {
            Security.removeProvider(provider.getName());
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
                System.out.println("ADVERTENCIA: el certificado de firma está revocado, pero se continúa "
                        + "de todas formas porque se indicó -omitir-revocacion true.");
            }
            case UNKNOWN -> System.out.println("ADVERTENCIA: no se pudo verificar el estado de revocación del "
                    + "certificado de firma (" + resultado.getDetalle() + "). Se continúa con la firma.");
            case GOOD -> System.out.println("Certificado de firma verificado: no está revocado"
                    + (resultado.getMetodo() != null ? " (" + resultado.getMetodo() + ")" : "") + ".");
        }
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
                    errorStream.println("Error: " + motivoDelCifrado(pdfPath));
                    return false;
                }
            }

            // Un documento cerrado se rechaza ahora, antes de tocar el token: así no se gasta ningún
            // intento de PIN en una firma que de todos modos no se puede aplicar.
            PdfDocumentAnalyzer.Analisis analisisPrevio = analizarOFallar(pdfPath, false);
            if (analisisPrevio.estado() == PdfDocumentAnalyzer.Estado.CERRADO) {
                errorStream.println("Error: " + analisisPrevio.motivoCierre());
                return false;
            }
            validarCampo(params, analisisPrevio);
            validarPosicionYProteccion(params, analisisPrevio);

            // Validar el token PKCS11
            Provider provider = null;
            try {
                provider = configurePKCS11Provider(params.libraryPath(), params.slotNumber());
                Security.addProvider(provider);

                try {
                    KeyStore keyStore = loadKeyStore(params.password(), params.libraryPath());
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
                } catch (TokenAccessException e) {
                    errorStream.println(e.getMessage());
                    return false;
                } catch (GeneralSecurityException e) {
                    errorStream.println("Error: " + Pkcs11Access.describeFailure(e, params.libraryPath()));
                    return false;
                }

                return true;

            } finally {
                if (provider != null) {
                    Security.removeProvider(provider.getName());
                }
            }
        } catch (IOException e) {
            errorStream.println("Error: " + e.getMessage());
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

    private static PdfDocumentAnalyzer.Analisis analizarOFallar(Path pdf, boolean consultarRevocacion) {
        try {
            return PdfDocumentAnalyzer.analizar(pdf, consultarRevocacion);
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
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
     * La contraseña es opcional: los certificados de un token suelen poder leerse sin ella, y así la vista
     * previa no consume ningún intento de PIN. Si el token exige la contraseña para mostrar el certificado,
     * se informa y la interfaz usa un nombre genérico en la vista previa.
     */
    private static void vistaPreviaTexto(String[] args) throws Exception {
        String libraryPath = null;
        String password = null;
        String customText = null;
        int slotNumber = -1;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-l", "--library" -> {
                    if (i + 1 < args.length) libraryPath = args[++i];
                }
                case "-s", "--slot" -> {
                    if (i + 1 < args.length) slotNumber = Integer.parseInt(args[++i]);
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
        if (libraryPath == null || slotNumber < 0) {
            throw new IllegalArgumentException("Para la vista previa del texto hacen falta la biblioteca (-l) "
                    + "y el slot (-s).");
        }
        if (!Files.isRegularFile(Paths.get(libraryPath))) {
            throw new IllegalArgumentException("La biblioteca PKCS#11 no existe o no es accesible: " + libraryPath);
        }

        Provider provider = configurePKCS11Provider(libraryPath, slotNumber);
        Security.addProvider(provider);
        try {
            KeyStore keyStore;
            if (password != null) {
                keyStore = loadKeyStore(password, libraryPath);
            } else {
                try {
                    keyStore = KeyStore.getInstance("PKCS11");
                    keyStore.load(null, null);
                } catch (Exception e) {
                    throw new TokenAccessException("Error: No se pudo leer el certificado del token sin la "
                            + "contraseña. " + Pkcs11Access.describeFailure(e, libraryPath));
                }
            }
            String alias = keyStore.aliases().nextElement();
            X500Principal subjectDN = ((X509Certificate) keyStore.getCertificate(alias)).getSubjectX500Principal();

            System.out.println("TEXTO_FIRMA_INICIO");
            System.out.println(buildSignatureText(customText, subjectDN));
            System.out.println("TEXTO_FIRMA_FIN");
            System.out.println("TAMANO_LETRA: " + TAMANO_LETRA_FIRMA);
        } finally {
            Security.removeProvider(provider.getName());
        }
    }

    private static boolean isFlagAnalizarDocumento(String arg) {
        return "-analizar-documento".equalsIgnoreCase(arg) || "--analizar-documento".equalsIgnoreCase(arg);
    }

    /** Si se pidió firmar en un campo ya existente, comprueba que exista y esté vacío. */
    private static void validarCampo(SignatureParameters params, PdfDocumentAnalyzer.Analisis analisis) {
        if (params.campo() != null && analisis.camposVacios().stream()
                .noneMatch(c -> c.nombre().equals(params.campo()))) {
            String disponibles = analisis.camposVacios().isEmpty() ? "el documento no tiene ninguno"
                    : analisis.camposVacios().stream()
                    .map(PdfDocumentAnalyzer.CampoVacio::nombre).collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException("El campo de firma '" + params.campo() + "' no existe en el documento "
                    + "o ya está firmado. Campos de firma disponibles: " + disponibles + ".");
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

    // validateInputs() y signDocument() configuran el proveedor cada uno por su cuenta: el aviso de
    // "se usó otro slot" se imprime una sola vez por ejecución, no una por cada configuración.
    private static boolean slotNoticePrinted;

    /**
     * Configura el proveedor sobre el slot pedido o, si ese slot no tiene
     * token, sobre el slot donde se detecte uno (ver Pkcs11Access: el slot es
     * un índice sobre TODOS los slots de la biblioteca, y el token puede no
     * estar en el que se indicó).
     */
    private static Provider configurePKCS11Provider(String libraryPath, int slotNumber) {
        Pkcs11Access.Resolution resolution =
                Pkcs11Access.resolve(libraryPath, slotNumber, "PDFSignerProvider", false);
        if (resolution.differsFromRequested() && !slotNoticePrinted) {
            slotNoticePrinted = true;
            System.out.println("Aviso: no había un token en el slot " + slotNumber
                    + "; se usó el slot " + resolution.selected().slotIndex() + ", donde se detectó el token.");
        }
        return resolution.selected().provider();
    }

    /** Error al acceder al token cuyo mensaje ya está traducido para el usuario final. */
    private static final class TokenAccessException extends GeneralSecurityException {
        private static final long serialVersionUID = 1L;

        TokenAccessException(String message) {
            super(message);
        }
    }

    private static KeyStore loadKeyStore(String password, String libraryPath) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, password.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new TokenAccessException("Error: " + Pkcs11Access.describeFailure(e, libraryPath));
        }
    }

    private static void signDocument(SignatureParameters params)
            throws GeneralSecurityException, IOException {
        // Paso 1, sin red ni token: ¿el documento todavía admite firmas?
        PdfDocumentAnalyzer.Analisis analisisInicial = analizarOFallar(Paths.get(params.pdfPath()), false);
        if (analisisInicial.estado() == PdfDocumentAnalyzer.Estado.CERRADO) {
            throw new IllegalArgumentException(analisisInicial.motivoCierre());
        }
        validarCampo(params, analisisInicial);
        validarPosicionYProteccion(params, analisisInicial);
        boolean documentoYaFirmado = !analisisInicial.firmas().isEmpty();

        Provider provider = null;
        try {
            provider = configurePKCS11Provider(params.libraryPath(), params.slotNumber());
            Security.addProvider(provider);

            Certificate[] chain;
            PrivateKey privateKey;
            String alias;
            X500Principal subjectDN;

            try {
                KeyStore keyStore = loadKeyStore(params.password(), params.libraryPath());
                alias = keyStore.aliases().nextElement();
                privateKey = (PrivateKey) keyStore.getKey(alias, params.password().toCharArray());
                chain = keyStore.getCertificateChain(alias);
                X509Certificate cert = (X509Certificate) chain[0];
                subjectDN = cert.getSubjectX500Principal();
            } catch (TokenAccessException e) {
                throw e;
            } catch (Exception e) {
                throw new GeneralSecurityException("Error: " + Pkcs11Access.describeFailure(e, params.libraryPath()));
            }

            validarRevocacionAntesDeFirmar((X509Certificate) chain[0], params.omitirRevocacion());

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

            try {
                Path sourceForSigning;
                byte[] ownerPassword = null;

                if (bloquearConCifrado) {
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

                    if (params.campo() != null) {
                        // Firma dentro de un campo de firma vacío que ya trae el documento (como hace Acrobat).
                        signerProperties.setFieldName(params.campo())
                                .setSignatureAppearance(new SignatureFieldAppearance(params.campo())
                                        .setContent(buildSignatureText(params.customText(), subjectDN))
                                        .setFontSize(TAMANO_LETRA_FIRMA));
                    } else if (params.xPos() != 0 || params.yPos() != 0) {
                        Rectangle rect = new Rectangle(params.xPos(), params.yPos(), params.ancho(), params.alto());
                        String signatureText = buildSignatureText(
                                params.customText(),
                                subjectDN
                        );
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
                        // Mismo bloqueo que aplica Acrobat con "Bloquear documento después de firmar": así
                        // Acrobat también muestra el documento como bloqueado.
                        PdfSigFieldLock bloqueo = new PdfSigFieldLock()
                                .setFieldLock(PdfSigFieldLock.LockAction.ALL)
                                .setDocumentPermissions(PdfSigFieldLock.LockPermissions.NO_CHANGES_ALLOWED);
                        // Con la acción "todos" Acrobat no escribe la lista de campos; iText agrega una vacía
                        // y con ella Acrobat no reconoce el bloqueo.
                        bloqueo.getPdfObject().remove(PdfName.Fields);
                        signerProperties.setFieldLockDict(bloqueo);
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

            controlarResultado(finalOutputPath, analisisInicial);

            System.out.println("Documento firmado exitosamente: " + finalOutputPath.toAbsolutePath());
            if (modoCierre) {
                System.out.println("El documento quedó cerrado: no admite más firmas.");
            }

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