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

package com.sauken.s_fide.xml_signer_windows_csp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.XMLStructure;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Firma XML usando un certificado del almacén "Personal" de Windows
 * (Windows-MY vía el provider SunMSCAPI del JDK), como alternativa a
 * XMLSignerPKCS11 cuando el certificado ya está disponible ahí (driver CSP
 * legado o KSP/CNG moderno con puente CSP). Solo funciona en Windows.
 */
public class XMLSignerWindowsCSP {
    private static final String VERSION = "S-FIDE XMLSignerWindowsCSP v1.1.1 - Grupo Sauken S.A.";
    private static PrintStream outputStream;
    private static PrintStream errorStream;

    static {
        try {
            outputStream = new PrintStream(System.out, true, "UTF-8");
            errorStream = new PrintStream(System.err, true, "UTF-8");
            System.setOut(outputStream);
            System.setErr(errorStream);
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error configurando codificación UTF-8");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            if (!isWindows()) {
                throw new IllegalStateException(
                        "Este módulo solo funciona en Windows (usa el almacén de certificados CSP/KSP de Windows). "
                                + "En otros sistemas operativos use XMLSignerPKCS11.");
            }
            processArguments(args);
            System.exit(0);
        } catch (Exception e) {
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void processArguments(String[] args) throws Exception {
        if (args.length == 0) {
            showHelp();
            throw new IllegalArgumentException("No se proporcionaron argumentos.");
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "-version":
                case "-v":
                case "--version":
                    outputStream.println(VERSION);
                    return;
                case "-licencia":
                case "--license":
                    outputStream.println(loadResourceFile("/LICENSE.txt"));
                    return;
                case "-ayuda":
                case "-h":
                case "--help":
                    showHelp();
                    return;
                case "-listar-certificados":
                case "--listar-certificados":
                    listarCertificados();
                    return;
                default:
                    throw new IllegalArgumentException("Argumento no reconocido: " + args[0]);
            }
        }

        if (args.length != 3) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + loadResourceFile("/HELP.txt"));
        }

        signXML(args[0], args[1], args[2]);
    }

    private static void showHelp() throws IOException {
        outputStream.println(loadResourceFile("/HELP.txt"));
    }

    private static String loadResourceFile(String resourcePath) throws IOException {
        try (InputStream is = XMLSignerWindowsCSP.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("No se pudo encontrar el recurso: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
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
            outputStream.println("No se encontraron certificados en el almacén de Windows (Windows-MY).");
            return;
        }
        for (String alias : aliases) {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            outputStream.println("Alias: " + alias);
            if (cert != null) {
                outputStream.println("  Subject: " + cert.getSubjectX500Principal().getName());
                outputStream.println("  Válido hasta: " + cert.getNotAfter());
                outputStream.println("  Tiene clave privada: " + keyStore.isKeyEntry(alias));
            }
            outputStream.println();
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
                            + "\". Use -listar-certificados para ver los disponibles.");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "\"" + aliasOrFragment + "\" coincide con " + matches.size()
                            + " certificados distintos. Sea más específico o use el alias exacto "
                            + "(vea -listar-certificados).");
        }
        return matches.get(0);
    }

    private static void signXML(String aliasOrFragment, String xmlFile, String uri) throws Exception {
        File xmlFileObj = new File(xmlFile);
        if (!xmlFileObj.exists()) {
            throw new IllegalArgumentException("El archivo XML no existe: " + xmlFile);
        }

        KeyStore keyStore = loadWindowsKeyStore();
        String alias = resolveAlias(keyStore, aliasOrFragment);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

        processAndSignDocument(xmlFile, uri, privateKey, cert);
    }

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setCoalescing(false);
        dbf.setExpandEntityReferences(false);
        dbf.setValidating(false);
        dbf.setXIncludeAware(false);
        dbf.setIgnoringElementContentWhitespace(false);
        dbf.setIgnoringComments(false);
        return dbf;
    }

    private static void processAndSignDocument(String xmlFile, String uri, PrivateKey privateKey, X509Certificate cert)
            throws Exception {
        try {
            DocumentBuilderFactory dbf = createSecureDocumentBuilderFactory();
            String xmlDeclaration = null;
            String originalContent = readFileToString(xmlFile);

            if (originalContent.startsWith("<?xml")) {
                int endDecl = originalContent.indexOf("?>");
                if (endDecl != -1) {
                    xmlDeclaration = originalContent.substring(0, endDecl + 2);
                }
            }

            Document doc = parseXMLDocument(dbf, xmlFile);
            registerDocumentIds(doc);

            if (!uri.isEmpty() && !uriExistsInXML(doc, uri)) {
                throw new IllegalArgumentException("El elemento o párrafo XML especificado no existe en el documento XML.");
            }

            String comexType = detectForeignTradeDocumentType(doc);
            if (comexType != null && uri.isEmpty()) {
                throw new IllegalArgumentException("Este XML corresponde a un documento de comercio exterior ("
                        + comexTypeExceptionPhrase(comexType) + "). No se permite firmar el documento completo: "
                        + "debe indicarse un elemento específico a firmar.");
            }

            validateSignatureRules(doc, uri);

            if (comexType != null) {
                outputStream.println(comexTypeInfoMessage(comexType));
                outputStream.println("Elemento firmado: " + uri);
            }

            applySignature(doc, uri, privateKey, cert);
            saveSignedDocument(doc, xmlFile, xmlDeclaration);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar el documento XML: " + e.getMessage());
        }
    }

    private static String readFileToString(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    private static Document parseXMLDocument(DocumentBuilderFactory dbf, String xmlFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            InputSource is = new InputSource(new InputStreamReader(fis, "UTF-8"));
            is.setEncoding("UTF-8");
            return dbf.newDocumentBuilder().parse(is);
        }
    }

    private static void registerDocumentIds(Document doc) {
        NodeList nodeList = doc.getElementsByTagName("*");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element element) {
                if (element.hasAttribute("ID")) element.setIdAttribute("ID", true);
                if (element.hasAttribute("Id")) element.setIdAttribute("Id", true);
                if (element.hasAttribute("id")) element.setIdAttribute("id", true);
            }
        }
    }

    private static boolean uriExistsInXML(Document doc, String uri) throws XPathExpressionException {
        if (uri.isEmpty()) {
            return true;
        }
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        String cleanUri = uri.startsWith("#") ? uri.substring(1) : uri;
        XPathExpression expr = xpath.compile(
                String.format("//*[@Id='%s' or @id='%s' or @ID='%s' or local-name()='%s']",
                        cleanUri, cleanUri, cleanUri, cleanUri));
        NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        return nl.getLength() > 0;
    }

    private static boolean hasExistingSignatureForUri(Document doc, String targetUri) {
        String cleanTarget = targetUri.startsWith("#") ? targetUri.substring(1) : targetUri;
        NodeList refs = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Reference");
        for (int i = 0; i < refs.getLength(); i++) {
            Element ref = (Element) refs.item(i);
            String refUri = ref.getAttribute("URI");
            String cleanRefUri = refUri.startsWith("#") ? refUri.substring(1) : refUri;
            if (cleanRefUri.equals(cleanTarget)) {
                return true;
            }
        }
        return false;
    }

    private static void validateSignatureRules(Document doc, String uri) {
        if (hasExistingSignatureForUri(doc, uri)) {
            throw new IllegalArgumentException(uri.isEmpty()
                    ? "El documento ya tiene una firma digital aplicada sobre todo su contenido. No se puede firmar el mismo elemento dos veces."
                    : "El elemento '" + uri + "' ya tiene una firma digital aplicada. No se puede firmar el mismo elemento dos veces.");
        }
        if ("CODEH".equals(uri) && !hasExistingSignatureForUri(doc, "COD")) {
            throw new IllegalArgumentException(
                    "No se puede firmar el elemento CODEH: no existe una firma digital previa sobre el elemento COD.");
        }
        if ("DJOEH".equals(uri) && !hasExistingSignatureForUri(doc, "DJO")) {
            throw new IllegalArgumentException(
                    "No se puede firmar el elemento DJOEH: no existe una firma digital previa sobre el elemento DJO.");
        }
    }

    private static String detectForeignTradeDocumentType(Document doc) {
        if (elementExistsWithId(doc, "COD")) {
            return "COD";
        }
        if (elementExistsWithId(doc, "DJO")) {
            return "DJO";
        }
        return null;
    }

    private static boolean elementExistsWithId(Document doc, String id) {
        try {
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile(
                    String.format("//*[@Id='%s' or @id='%s' or @ID='%s']", id, id, id));
            NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            return nl.getLength() > 0;
        } catch (XPathExpressionException e) {
            return false;
        }
    }

    private static String comexTypeExceptionPhrase(String comexType) {
        return "COD".equals(comexType) ? "un Certificado de Origen Digital" : "una Declaración Jurada de Origen";
    }

    private static String comexTypeInfoMessage(String comexType) {
        return "COD".equals(comexType)
                ? "El XML a firmar es un Certificado de Origen Digital de ALADI (sin verificación de contenido)."
                : "El XML a firmar es una Declaración Jurada de Origen (sin verificación de contenido).";
    }

    private static void applySignature(Document doc, String uri, PrivateKey privateKey, X509Certificate cert)
            throws Exception {
        // XMLSignatureFactory.getInstance(mechanismType, provider) exige que el provider
        // indicado implemente el mecanismo "DOM" en sí mismo — SunMSCAPI no lo hace, solo
        // provee primitivos criptográficos (Signature, KeyStore). Por eso se usa la fábrica
        // DOM estándar sin indicar provider: la clave de SunMSCAPI hace que
        // Signature.getInstance("SHA256withRSA") (sin provider) descarte los providers de
        // software por InvalidKeyException y la JCA reintente hasta llegar a SunMSCAPI, que
        // sí la acepta - el mismo mecanismo que ya usa XMLSignerPKCS11 con SunPKCS11.
        if (Security.getProvider("SunMSCAPI") == null) {
            throw new IllegalStateException("El proveedor SunMSCAPI no está disponible en este JDK");
        }
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        List<Transform> transforms = new ArrayList<>();
        transforms.add(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null));
        transforms.add(fac.newTransform(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null));

        Reference ref = fac.newReference(
                uri.isEmpty() ? "" : "#" + uri,
                fac.newDigestMethod(DigestMethod.SHA256, null),
                transforms,
                null,
                null);

        SignedInfo si = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                Collections.singletonList(ref));

        KeyInfo ki = createKeyInfo(fac, cert);
        XMLSignature signature = fac.newXMLSignature(si, ki);
        DOMSignContext dsc = createSignatureContext(doc, uri, privateKey);
        signature.sign(dsc);
    }

    private static KeyInfo createKeyInfo(XMLSignatureFactory fac, X509Certificate cert) throws Exception {
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        KeyValue keyValue = kif.newKeyValue(cert.getPublicKey());

        List<Object> x509Content = new ArrayList<>();
        x509Content.add(cert.getSubjectX500Principal().getName());
        x509Content.add(cert);
        X509Data x509Data = kif.newX509Data(x509Content);

        List<XMLStructure> kiContent = new ArrayList<>();
        kiContent.add(keyValue);
        kiContent.add(x509Data);

        return kif.newKeyInfo(kiContent);
    }

    private static DOMSignContext createSignatureContext(Document doc, String uri, PrivateKey privateKey) {
        if (uri.isEmpty()) {
            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            dsc.setDefaultNamespacePrefix("ds");
            return dsc;
        }
        Node elementToSign = findElementByAttributeId(doc, uri);
        if (elementToSign == null) {
            throw new IllegalArgumentException("No se encontró el elemento XML con identificador: " + uri);
        }
        Node parentNode = elementToSign.getParentNode();
        Node nextSibling = elementToSign.getNextSibling();
        DOMSignContext dsc = new DOMSignContext(privateKey, parentNode, nextSibling);
        dsc.setDefaultNamespacePrefix("ds");
        return dsc;
    }

    private static Node findElementByAttributeId(Document doc, String id) {
        Element elem = doc.getElementById(id);
        if (elem != null) {
            return elem;
        }
        NodeList nodeList = doc.getElementsByTagName("*");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (id.equals(element.getAttribute("Id")) ||
                        id.equals(element.getAttribute("id")) ||
                        id.equals(element.getAttribute("ID")) ||
                        id.equals(element.getTagName())) {
                    return element;
                }
            }
        }
        return null;
    }

    private static void saveSignedDocument(Document doc, String xmlFile, String xmlDeclaration) throws Exception {
        int dotIndex = xmlFile.lastIndexOf('.');
        String outputXmlFile = dotIndex > 0
                ? xmlFile.substring(0, dotIndex) + "-signed" + xmlFile.substring(dotIndex)
                : xmlFile + "-signed";

        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        Transformer trans = tf.newTransformer();
        if (xmlDeclaration != null) {
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        trans.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        trans.setOutputProperty(OutputKeys.INDENT, "no");

        try (StringWriter sw = new StringWriter()) {
            trans.transform(new DOMSource(doc), new StreamResult(sw));
            String output = sw.toString().replace("&#13;", "");

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputXmlFile), "UTF-8")) {
                if (xmlDeclaration != null) {
                    writer.write(xmlDeclaration + "\n");
                }
                writer.write(output);
            }
        }

        outputStream.println("Documento XML firmado guardado como: " + outputXmlFile);
    }
}
