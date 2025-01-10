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

package com.sauken.s_fide.xml_signer_pkcs11;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import org.xml.sax.InputSource;
import java.io.UnsupportedEncodingException;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.xpath.*;

public class XMLSignerPKCS11 {
    private static final String VERSION = "S-FIDE XMLSignerPKCS11 v1.0.0 - Grupo Sauken S.A.";
    private static PrintStream errorStream;
    private static PrintStream outputStream;

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

    public XMLSignerPKCS11() {
        // Constructor vacío
    }

    public static void main(String[] args) {
        try {
            processArguments(args);
            System.exit(0);
        } catch (Exception e) {
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processArguments(String[] args) throws Exception {
        if (args.length == 0) {
            showHelp();
            throw new IllegalArgumentException("No se proporcionaron argumentos.");
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "-version":
                    outputStream.println(VERSION);
                    return;
                case "-licencia":
                    showLicense();
                    return;
                case "-ayuda":
                    showHelp();
                    return;
                default:
                    throw new IllegalArgumentException("Argumento no reconocido: " + args[0]);
            }
        }

        if (args.length != 5) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + loadResourceFile("/HELP.txt"));
        }

        validateAndProcessStandardArguments(args);
    }

    private static void validateAndProcessStandardArguments(String[] args) throws Exception {
        String pkcs11LibraryPath = args[0];
        String password = args[1];
        int slotNumber;
        String xmlFile = args[3];
        String uri = args[4];

        try {
            slotNumber = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error: El número de slot debe ser un valor numérico.");
        }

        validateFiles(pkcs11LibraryPath, xmlFile);
        signXML(pkcs11LibraryPath, password, slotNumber, xmlFile, uri);
    }

    private static void validateFiles(String pkcs11LibraryPath, String xmlFile) throws IllegalArgumentException {
        File pkcs11Library = new File(pkcs11LibraryPath);
        if (!pkcs11Library.exists()) {
            throw new IllegalArgumentException("El archivo de la biblioteca PKCS#11 no existe: " + pkcs11LibraryPath);
        }

        File xmlFileObj = new File(xmlFile);
        if (!xmlFileObj.exists()) {
            throw new IllegalArgumentException("El archivo XML no existe: " + xmlFile);
        }
    }

    private static void showHelp() throws IOException {
        try {
            outputStream.println(loadResourceFile("/HELP.txt"));
            outputStream.flush();
        } catch (IOException e) {
            throw new IOException("Error mostrando ayuda");
        }
    }

    private static void showLicense() throws IOException {
        try {
            outputStream.println(loadResourceFile("/LICENSE.txt"));
            outputStream.flush();
        } catch (IOException e) {
            throw new IOException("Error mostrando licencia");
        }
    }

    private static String loadResourceFile(String resourcePath) throws IOException {
        try (InputStream is = XMLSignerPKCS11.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("No se pudo encontrar el recurso: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
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

    private static String getOutputFileName(String inputFileName) {
        int dotIndex = inputFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return inputFileName.substring(0, dotIndex) + "-signed" + inputFileName.substring(dotIndex);
        } else {
            return inputFileName + "-signed";
        }
    }

    private static Provider configurePKCS11Provider(String pkcs11LibraryPath, int slotNumber) {
        String config = String.format(
                "--name=XMLSignerProvider\nlibrary=%s\nslot=%d",
                pkcs11LibraryPath,
                slotNumber
        );
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new IllegalArgumentException("Proveedor SunPKCS11 no disponible");
        }
        return provider.configure(config);
    }

    private static KeyStore loadKeyStore(String password) throws Exception {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, password.toCharArray());
            return keyStore;
        } catch (IOException e) {
            throw new IllegalArgumentException("Contraseña incorrecta");
        }
    }

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() throws ParserConfigurationException {
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

    private static void signXML(String pkcs11LibraryPath, String password, int slotNumber, String xmlFile, String uri) {
        Provider provider = null;
        try {
            provider = configurePKCS11Provider(pkcs11LibraryPath, slotNumber);
            Security.addProvider(provider);

            KeyStore keyStore = loadKeyStore(password);
            String alias = keyStore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

            processAndSignDocument(xmlFile, uri, privateKey, cert);

        } catch (Exception e) {
            String errorMessage = "Error en el proceso de firma";
            if (e instanceof IllegalArgumentException) {
                errorMessage = e.getMessage();
            } else if (e instanceof KeyStoreException || e instanceof NoSuchAlgorithmException ||
                    e instanceof UnrecoverableKeyException) {
                errorMessage = "Error en el acceso al token";
            } else if (e instanceof IOException) {
                errorMessage = "Error de E/S: " + e.getMessage();
            }
            throw new IllegalArgumentException(errorMessage);
        } finally {
            if (provider != null) {
                Security.removeProvider(provider.getName());
            }
        }
    }

    private static void processAndSignDocument(String xmlFile, String uri, PrivateKey privateKey, X509Certificate cert) {
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

            applySignature(doc, uri, privateKey, cert);
            saveSignedDocument(doc, xmlFile, xmlDeclaration);

        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar el documento XML: " + e.getMessage());
        }
    }

    private static Document parseXMLDocument(DocumentBuilderFactory dbf, String xmlFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            InputSource is = new InputSource(new InputStreamReader(fis, "UTF-8"));
            is.setEncoding("UTF-8");
            return builder.parse(is);
        }
    }

    private static void applySignature(Document doc, String uri, PrivateKey privateKey, X509Certificate cert)
            throws Exception {
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

    private static void registerDocumentIds(Document doc) {
        NodeList nodeList = doc.getElementsByTagName("*");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (element.hasAttribute("ID")) {
                    element.setIdAttribute("ID", true);
                }
                if (element.hasAttribute("Id")) {
                    element.setIdAttribute("Id", true);
                }
                if (element.hasAttribute("id")) {
                    element.setIdAttribute("id", true);
                }
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

    private static DOMSignContext createSignatureContext(Document doc, String uri, PrivateKey privateKey) {
        if (uri.isEmpty()) {
            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            dsc.setDefaultNamespacePrefix("ds");
            return dsc;
        } else {
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
    }

    private static void saveSignedDocument(Document doc, String xmlFile, String xmlDeclaration) throws Exception {
        String outputXmlFile = getOutputFileName(xmlFile);
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
}