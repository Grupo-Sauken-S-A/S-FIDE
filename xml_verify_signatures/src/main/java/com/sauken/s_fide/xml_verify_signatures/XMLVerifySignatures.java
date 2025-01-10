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

package com.sauken.s_fide.xml_verify_signatures;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import com.sauken.s_fide.xml_verify_signatures.utils.CertificateUtils;
import com.sauken.s_fide.xml_verify_signatures.validation.RevocationValidator;
import com.sauken.s_fide.xml_verify_signatures.validation.NetworkUtils;
import com.sauken.s_fide.xml_verify_signatures.timestamp.SignatureTimeExtractor;
import com.sauken.s_fide.xml_verify_signatures.validation.RevocationValidator.RevocationResult;
import com.sauken.s_fide.xml_verify_signatures.timestamp.SignatureTimeExtractor.SigningTimeResult;
import java.text.SimpleDateFormat;

public class XMLVerifySignatures {
    private static final String VERSION = "S-FIDE XMLVerifySignatures v1.0.0 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT;
    private static final String HELP_TEXT;
    private static final String TRUST_STORE_PATH = System.getProperty("java.home") +
            File.separator + "lib" + File.separator + "security" + File.separator + "cacerts";
    private static final String TRUST_STORE_PASSWORD = "changeit";
    private static boolean simpleOutput = false;

    static {
        LICENSE_TEXT = readResourceFile("/LICENSE.txt");
        HELP_TEXT = readResourceFile("/HELP.txt");
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = XMLVerifySignatures.class.getResourceAsStream(resourcePath)) {
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
            processArguments(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processArguments(String[] args) {
        if (args.length == 0) {
            showHelp();
            throw new IllegalArgumentException("No se proporcionaron argumentos.");
        }

        if (args.length == 1) {
            String argLower = args[0].toLowerCase();
            switch (argLower) {
                case "-version":
                    System.out.println(VERSION);
                    return;
                case "-licencia":
                    System.out.println(LICENSE_TEXT);
                    return;
                case "-ayuda":
                    showHelp();
                    return;
                default:
                    break;
            }
        }

        if (args.length > 2) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + HELP_TEXT);
        }

        String xmlFile = args[0];

        if (args.length == 2) {
            if (!"-simple".equalsIgnoreCase(args[1])) {
                throw new IllegalArgumentException("Argumento no válido: " + args[1] + "\n\n" + HELP_TEXT);
            }
            simpleOutput = true;
        }

        try {
            File xmlFileObj = new File(xmlFile);
            if (!xmlFileObj.exists()) {
                throw new IllegalArgumentException("El archivo XML no existe: " + xmlFile);
            }

            boolean hasSignatures = verifyXMLSignatures(xmlFile);
            if (!hasSignatures) {
                throw new IllegalArgumentException("El documento XML no contiene firmas digitales.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
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

    private static boolean verifyXMLSignatures(String xmlFile) throws IllegalArgumentException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            configureDocumentBuilderFactory(dbf);

            Document doc = loadXMLDocument(xmlFile, dbf);
            registerIdAttributes(doc.getDocumentElement());

            NodeList signatureNodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (signatureNodes.getLength() == 0) {
                return false;
            }

            boolean hasGraveErrors = false;
            for (int i = 0; i < signatureNodes.getLength(); i++) {
                if (i > 0) {
                    System.out.println("\n----------------------------------------\n");
                }

                Node signatureNode = signatureNodes.item(i);
                try {
                    boolean signatureValid = verifySignature(doc, signatureNode, i + 1);
                    if (!signatureValid) {
                        hasGraveErrors = true;
                    }
                } catch (Exception e) {
                    System.out.println("Error al validar la firma #" + (i + 1) + ": " + e.getMessage());
                    hasGraveErrors = true;
                }
            }

            System.out.println("\n========== RESULTADO FINAL ==========");
            if (hasGraveErrors) {
                System.out.println("DOCUMENTO INVÁLIDO: Una o más firmas contienen errores graves.");
                System.exit(1);
            } else {
                System.out.println("DOCUMENTO VÁLIDO: Todas las firmas son válidas.");
                System.exit(0);
            }

            return true;

        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar el archivo XML: " + e.getMessage());
        }
    }

    private static void configureDocumentBuilderFactory(DocumentBuilderFactory dbf) {
        dbf.setNamespaceAware(true);
        dbf.setCoalescing(false);
        dbf.setExpandEntityReferences(false);
        dbf.setValidating(false);
        dbf.setXIncludeAware(false);
        dbf.setIgnoringElementContentWhitespace(false);
        dbf.setIgnoringComments(false);
    }

    private static Document loadXMLDocument(String xmlFile, DocumentBuilderFactory dbf) throws Exception {
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            try (FileInputStream fis = new FileInputStream(xmlFile)) {
                InputSource is = new InputSource(new InputStreamReader(fis, "UTF-8"));
                is.setEncoding("UTF-8");
                return builder.parse(is);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al leer el archivo XML: " + e.getMessage());
        }
    }

    private static void registerIdAttributes(Element element) {
        if (element.hasAttribute("ID")) {
            element.setIdAttribute("ID", true);
        }
        if (element.hasAttribute("Id")) {
            element.setIdAttribute("Id", true);
        }
        if (element.hasAttribute("id")) {
            element.setIdAttribute("id", true);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                registerIdAttributes((Element) node);
            }
        }
    }

    private static boolean verifySignature(Document doc, Node signatureNode, int signatureNumber)
            throws IllegalArgumentException {
        try {
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext valContext = new DOMValidateContext(
                    new X509KeySelector(), signatureNode);

            valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
            valContext.setProperty("javax.xml.crypto.dsig.cacheReference", Boolean.TRUE);
            valContext.putNamespacePrefix(XMLSignature.XMLNS, "ds");

            XMLSignature signature = fac.unmarshalXMLSignature(valContext);
            Element signatureElement = (Element) signatureNode;

            System.out.println("Firma #" + signatureNumber + ":");

            String digestAlgorithm = getDigestAlgorithm(signature);
            System.out.println("  Algoritmo de Hash: " + digestAlgorithm);

            boolean sv = signature.getSignatureValue().validate(valContext);
            System.out.println("  Valor de firma: " + (sv ? "Válido" : "Inválido"));

            boolean referencesValid = validateReferences(signature, valContext);
            boolean coreValidity = signature.validate(valContext);
            System.out.println("  Validación principal: " + (coreValidity ? "Válida" : "Inválida"));

            boolean isRevoked = false;
            if (!simpleOutput) {
                printSignatureInfo(signature, signatureNumber, coreValidity && sv && referencesValid);
                isRevoked = validateRevocationStatus(signature, signatureElement);
            } else {
                KeyInfo keyInfo = signature.getKeyInfo();
                if (keyInfo != null) {
                    for (Object o : keyInfo.getContent()) {
                        if (o instanceof X509Data x509Data) {
                            for (Object x509Content : x509Data.getContent()) {
                                if (x509Content instanceof X509Certificate cert) {
                                    isRevoked = checkRevocationStatusSimple(cert, signatureElement);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            return coreValidity && sv && referencesValid && !isRevoked;

        } catch (Exception e) {
            throw new IllegalArgumentException("Error en la verificación de la firma #" +
                    signatureNumber + ": " + e.getMessage());
        }
    }

    private static String getDigestAlgorithm(XMLSignature signature) {
        try {
            List<Reference> references = signature.getSignedInfo().getReferences();
            if (!references.isEmpty()) {
                Reference firstRef = references.get(0);
                String digestMethod = firstRef.getDigestMethod().getAlgorithm();
                switch (digestMethod) {
                    case DigestMethod.SHA1:
                        return "SHA1";
                    case DigestMethod.SHA256:
                        return "SHA256";
                    default:
                        return digestMethod.substring(digestMethod.lastIndexOf('#') + 1);
                }
            }
        } catch (Exception e) {
            return "Desconocido";
        }
        return "No especificado";
    }

    private static boolean validateReferences(XMLSignature signature, DOMValidateContext valContext)
            throws XMLSignatureException {
        boolean referencesValid = true;
        List<Reference> references = signature.getSignedInfo().getReferences();

        for (int i = 0; i < references.size(); i++) {
            Reference ref = references.get(i);
            boolean refValid = ref.validate(valContext);
            System.out.println("  Referencia #" + (i + 1) + " (" + ref.getURI() + "):");
            System.out.println("    Válida: " + (refValid ? "Sí" : "No"));

            if (!refValid) {
                referencesValid = false;
                if (!simpleOutput) {
                    System.out.println("    Digest calculado: " +
                            Base64.getEncoder().encodeToString(ref.getCalculatedDigestValue()));
                    System.out.println("    Digest en XML: " +
                            Base64.getEncoder().encodeToString(ref.getDigestValue()));
                }
            }
        }
        return referencesValid;
    }

    private static void printSignatureInfo(XMLSignature signature, int signatureNumber, boolean isValid)
            throws Exception {
        System.out.println("\nDetalles de la Firma #" + signatureNumber + ":");
        System.out.println("  Estado: " + (isValid ? "VÁLIDA" : "INVÁLIDA"));

        SignedInfo signedInfo = signature.getSignedInfo();
        System.out.println("  Método de Canonicalización: " +
                signedInfo.getCanonicalizationMethod().getAlgorithm());
        System.out.println("  Método de Firma: " + signedInfo.getSignatureMethod().getAlgorithm());

        String signatureValue = Base64.getEncoder().encodeToString(
                signature.getSignatureValue().getValue());
        System.out.println("  Valor de la Firma: " + signatureValue);
    }

    private static void printCertificateDetails(X509Certificate cert) {
        System.out.println("\nInformación del Certificado:");
        System.out.println("  Sujeto: " + cert.getSubjectX500Principal().getName());
        System.out.println("  Emisor: " + cert.getIssuerX500Principal().getName());
        System.out.println("  Número de Serie: " + cert.getSerialNumber().toString(16));
        System.out.println("  Válido desde: " + cert.getNotBefore());
        System.out.println("  Válido hasta: " + cert.getNotAfter());
    }

    private static void verifyCertificate(X509Certificate cert) {
        try {
            cert.checkValidity();
            System.out.println("  Estado del Certificado: VÁLIDO");
        } catch (CertificateExpiredException e) {
            System.out.println("  Estado del Certificado: EXPIRADO");
        } catch (CertificateNotYetValidException e) {
            System.out.println("  Estado del Certificado: AÚN NO VÁLIDO");
        }
    }

    private static boolean validateRevocationStatus(XMLSignature signature, Element signatureElement) {
        try {
            KeyInfo keyInfo = signature.getKeyInfo();
            if (keyInfo != null) {
                for (Object o : keyInfo.getContent()) {
                    if (o instanceof X509Data x509Data) {
                        for (Object x509Content : x509Data.getContent()) {
                            if (x509Content instanceof X509Certificate cert) {
                                printCertificateDetails(cert);
                                verifyCertificate(cert);
                                return validateRevocationStatus(cert, signatureElement);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error al procesar información del certificado: " + e.getMessage());
        }
        return false;
    }

    private static boolean validateRevocationStatus(X509Certificate cert, Element signatureElement) {
        if (!NetworkUtils.isInternetAvailable()) {
            System.out.println("  No hay conexión a Internet. No se puede validar el estado de revocación.");
            return false;
        }

        try {
            List<String> ocspUrls = CertificateUtils.getOCSPUrls(cert);
            List<String> crlUrls = CertificateUtils.getCRLUrls(cert);

            if (ocspUrls.isEmpty() && crlUrls.isEmpty()) {
                System.out.println("  No se encontraron URLs de OCSP ni CRL en el certificado.");
                return false;
            }

            SigningTimeResult signingTimeResult = SignatureTimeExtractor.extractSigningTime(signatureElement);
            if (signingTimeResult.isUsingCurrentTime()) {
                System.out.println("  ADVERTENCIA: " + signingTimeResult.getErrorMessage());
            }

            X509Certificate issuerCert = CertificateUtils.getIssuerCertificate(cert, TRUST_STORE_PATH,
                    TRUST_STORE_PASSWORD);

            // Intentar OCSP primero
            for (String ocspUrl : ocspUrls) {
                try {
                    RevocationResult ocspResult = RevocationValidator.checkOCSPStatus(
                            cert, ocspUrl, issuerCert, signingTimeResult);

                    if (ocspResult.getStatus() != RevocationValidator.RevocationStatus.UNKNOWN) {
                        printRevocationResult(ocspResult);
                        return ocspResult.getStatus() == RevocationValidator.RevocationStatus.REVOKED;
                    }
                } catch (Exception e) {
                    System.out.println("  Error en validación OCSP (" + ocspUrl + "): " + e.getMessage());
                }
            }

            // Si OCSP falla, intentar CRL
            if (!crlUrls.isEmpty()) {
                for (String crlUrl : crlUrls) {
                    try {
                        RevocationResult crlResult = RevocationValidator.checkCRLStatus(
                                cert, crlUrl, signingTimeResult);

                        if (crlResult.getStatus() != RevocationValidator.RevocationStatus.UNKNOWN) {
                            printRevocationResult(crlResult);
                            return crlResult.getStatus() == RevocationValidator.RevocationStatus.REVOKED;
                        }
                    } catch (Exception e) {
                        System.out.println("  Error en validación CRL (" + crlUrl + "): " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("  Error al validar estado de revocación: " + e.getMessage());
        }
        return false;
    }

    private static void printRevocationResult(RevocationResult result) {
        System.out.println("  Estado de revocación (" + result.getMethod() + "): " +
                (result.getStatus() == RevocationValidator.RevocationStatus.GOOD ? "VÁLIDO" :
                        result.getStatus() == RevocationValidator.RevocationStatus.REVOKED ? "REVOCADO" : "DESCONOCIDO"));

        // Si está revocado, mostrar las fechas relevantes
        if (result.getStatus() == RevocationValidator.RevocationStatus.REVOKED) {
            if (result.getValidationDate() != null) {
                System.out.println("  Fecha de firma del documento XML: " +
                        formatDate(result.getValidationDate()));
            }
            if (result.getRevocationDate() != null) {
                System.out.println("  Fecha de revocación del certificado: " +
                        formatDate(result.getRevocationDate()));
            }
            if (result.getRevocationDate() != null && result.getValidationDate() != null) {
                long diffDays = (result.getValidationDate().getTime() - result.getRevocationDate().getTime())
                        / (24 * 60 * 60 * 1000);
                System.out.println("  El documento se firmó " + Math.abs(diffDays) +
                        " días " + (diffDays >= 0 ? "después" : "antes") + " de la revocación del certificado");
            }
        }

        if (result.getErrorMessage() != null) {
            System.out.println("  " + result.getErrorMessage());
        }

        if (result.isUsedCurrentTime()) {
            System.out.println("  ADVERTENCIA: La validación se realizó utilizando la fecha actual");
        }
    }

    private static boolean checkRevocationStatusSimple(X509Certificate cert, Element signatureElement) {
        if (!NetworkUtils.isInternetAvailable()) {
            System.out.println("  Estado de revocación: No verificable - Sin conexión a Internet");
            return false;
        }

        try {
            List<String> ocspUrls = CertificateUtils.getOCSPUrls(cert);
            List<String> crlUrls = CertificateUtils.getCRLUrls(cert);

            if (ocspUrls.isEmpty() && crlUrls.isEmpty()) {
                System.out.println("  Estado de revocación: No verificable - Sin URLs de OCSP/CRL");
                return false;
            }

            SigningTimeResult signingTimeResult = SignatureTimeExtractor.extractSigningTime(signatureElement);
            X509Certificate issuerCert = CertificateUtils.getIssuerCertificate(cert, TRUST_STORE_PATH,
                    TRUST_STORE_PASSWORD);

            // Intentar OCSP primero
            for (String ocspUrl : ocspUrls) {
                try {
                    RevocationResult result = RevocationValidator.checkOCSPStatus(
                            cert, ocspUrl, issuerCert, signingTimeResult);
                    if (result.getStatus() != RevocationValidator.RevocationStatus.UNKNOWN) {
                        printSimpleRevocationResult(result);
                        return result.getStatus() == RevocationValidator.RevocationStatus.REVOKED;
                    }
                } catch (Exception e) {
                    // Continuar con el siguiente URL
                }
            }

            // Si OCSP falla, intentar CRL
            for (String crlUrl : crlUrls) {
                try {
                    RevocationResult result = RevocationValidator.checkCRLStatus(
                            cert, crlUrl, signingTimeResult);
                    if (result.getStatus() != RevocationValidator.RevocationStatus.UNKNOWN) {
                        printSimpleRevocationResult(result);
                        return result.getStatus() == RevocationValidator.RevocationStatus.REVOKED;
                    }
                } catch (Exception e) {
                    // Continuar con el siguiente URL
                }
            }

            System.out.println("  Estado de revocación: No verificable - Servicios no disponibles");

        } catch (Exception e) {
            System.out.println("  Estado de revocación: No verificable - Error en la validación");
        }
        return false;
    }

    private static void printSimpleRevocationResult(RevocationResult result) {
        String status = switch (result.getStatus()) {
            case GOOD -> "Válido";
            case REVOKED -> "REVOCADO";
            case UNKNOWN -> "No verificable";
        };

        System.out.println("  Estado de revocación: " + status + " (verificado por " + result.getMethod() + ")");

        if (result.getStatus() == RevocationValidator.RevocationStatus.REVOKED) {
            if (result.getValidationDate() != null) {
                System.out.println("  Fecha del documento XML: " + formatDate(result.getValidationDate()));
            }
            if (result.getRevocationDate() != null) {
                System.out.println("  Certificado revocado desde: " + formatDate(result.getRevocationDate()));
            }
        }

        if (result.isUsedCurrentTime()) {
            System.out.println("  ADVERTENCIA: Validación realizada con fecha actual");
        }
    }

    private static String formatDate(Date date) {
        if (date == null) return "No disponible";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private static class X509KeySelector extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose,
                                        AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("No se encontró KeyInfo");
            }

            for (Object o : keyInfo.getContent()) {
                if (o instanceof X509Data x509Data) {
                    for (Object x509Content : x509Data.getContent()) {
                        if (x509Content instanceof X509Certificate cert) {
                            final PublicKey key = cert.getPublicKey();
                            if (isKeyValid(method, key)) {
                                return () -> key;
                            }
                        }
                    }
                }
            }
            throw new KeySelectorException("No se encontró una clave válida");
        }

        private boolean isKeyValid(AlgorithmMethod method, Key key) {
            String algorithm = method.getAlgorithm().toLowerCase();
            boolean isRSAKey = "RSA".equalsIgnoreCase(key.getAlgorithm());

            boolean isSupportedAlgorithm =
                    algorithm.contains("rsa-sha1") ||
                            algorithm.contains("rsa-sha256") ||
                            algorithm.contains("rsa-SHA1") ||
                            algorithm.contains("rsa-SHA256");

            return isRSAKey && isSupportedAlgorithm;
        }
    }
}