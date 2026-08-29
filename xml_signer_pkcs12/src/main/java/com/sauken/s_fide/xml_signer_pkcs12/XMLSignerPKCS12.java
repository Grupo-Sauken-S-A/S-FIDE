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

package com.sauken.s_fide.xml_signer_pkcs12;

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
import java.io.UnsupportedEncodingException;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import org.xml.sax.InputSource;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.xpath.*;

public class XMLSignerPKCS12 {
    private static final String VERSION = "S-FIDE XMLSignerPKCS12 1.0.0 - Grupo Sauken S.A.";
    private static PrintStream errorStream;
    private static PrintStream outputStream;

    static {
        try {
            outputStream = new PrintStream(System.out, true, StandardCharsets.UTF_8.name());
            errorStream = new PrintStream(System.err, true, StandardCharsets.UTF_8.name());
            System.setOut(outputStream);
            System.setErr(errorStream);
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error configurando codificación UTF-8");
            System.exit(1);
        }
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

        if (args.length != 4) {
            throw new IllegalArgumentException("Número incorrecto de argumentos.\n\n" + loadResourceFile("/HELP.txt"));
        }

        validateAndProcessStandardArguments(args);
    }

    private static void validateAndProcessStandardArguments(String[] args) throws Exception {
        String pkcs12File = args[0];
        String password = args[1];
        String xmlFile = args[2];
        String uri = args[3];

        validateFiles(pkcs12File, xmlFile);
        validatePKCS12(pkcs12File, password);
        signXML(pkcs12File, password, xmlFile, uri);
    }

    private static void validateFiles(String pkcs12File, String xmlFile) throws IllegalArgumentException {
        File p12File = new File(pkcs12File);
        if (!p12File.exists()) {
            throw new IllegalArgumentException("El archivo PKCS#12 no existe: " + pkcs12File);
        }

        File xmlFileObj = new File(xmlFile);
        if (!xmlFileObj.exists()) {
            throw new IllegalArgumentException("El archivo XML no existe: " + xmlFile);
        }
    }

    private static void validatePKCS12(String pkcs12File, String password) throws IllegalArgumentException {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(pkcs12File)) {
                ks.load(fis, password.toCharArray());
            }
            if (!ks.aliases().hasMoreElements()) {
                throw new IllegalArgumentException("El archivo PKCS#12 no contiene ningún certificado.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("El archivo no es un PKCS#12 válido o la contraseña es incorrecta");
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al validar el archivo PKCS#12: " + e.getMessage());
        }
    }

    private static void showHelp() throws IOException {
        outputStream.println(loadResourceFile("/HELP.txt"));
        outputStream.flush();
    }

    private static void showLicense() throws IOException {
        outputStream.println(loadResourceFile("/LICENSE.txt"));
        outputStream.flush();
    }

    private static String loadResourceFile(String resourcePath) throws IOException {
        try (InputStream is = XMLSignerPKCS12.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("No se pudo encontrar el recurso: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readFileToString(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static void signXML(String pkcs12File, String password, String xmlFile, String uri)
            throws Exception {
        try {
            // Cargar el KeyStore PKCS#12
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(pkcs12File)) {
                keyStore.load(fis, password.toCharArray());
            } catch (IOException e) {
                throw new IllegalArgumentException("Error al cargar el archivo PKCS#12: " + e.getMessage());
            }

            // Obtener la clave privada y el certificado
            String alias = keyStore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

            // Configurar el DocumentBuilderFactory
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setCoalescing(false);
            dbf.setExpandEntityReferences(false);
            dbf.setValidating(false);
            dbf.setXIncludeAware(false);
            dbf.setIgnoringElementContentWhitespace(false);
            dbf.setIgnoringComments(false);

            // Preservar declaración XML original
            String originalContent = readFileToString(xmlFile);
            String xmlDeclaration = null;
            if (originalContent.startsWith("<?xml")) {
                int endDecl = originalContent.indexOf("?>");
                if (endDecl != -1) {
                    xmlDeclaration = originalContent.substring(0, endDecl + 2);
                }
            }

            // Leer el documento
            Document doc;
            try (FileInputStream fis = new FileInputStream(xmlFile)) {
                DocumentBuilder builder = dbf.newDocumentBuilder();
                InputSource is = new InputSource(new InputStreamReader(fis, "UTF-8"));
                is.setEncoding("UTF-8");
                doc = builder.parse(is);

                // Registrar los IDs
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

                // Verificar URI
                if (!uri.isEmpty() && !uriExistsInXML(doc, uri)) {
                    throw new IllegalArgumentException("El elemento o párrafo XML especificado no existe en el documento XML.");
                }

                // Crear firma XML
                XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

                // Crear transformaciones
                List<Transform> transforms = new ArrayList<>();
                transforms.add(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null));
                transforms.add(fac.newTransform(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null));

                // Crear referencia
                Reference ref = fac.newReference(
                        uri.isEmpty() ? "" : "#" + uri,
                        fac.newDigestMethod(DigestMethod.SHA256, null),
                        transforms,
                        null,
                        null);

                // Crear SignedInfo
                SignedInfo si = fac.newSignedInfo(
                        fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                        fac.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                        Collections.singletonList(ref));

                // Crear KeyInfo
                KeyInfoFactory kif = fac.getKeyInfoFactory();
                KeyValue keyValue = kif.newKeyValue(cert.getPublicKey());

                List<Object> x509Content = new ArrayList<>();
                x509Content.add(cert.getSubjectX500Principal().getName());
                x509Content.add(cert);
                X509Data x509Data = kif.newX509Data(x509Content);

                List<XMLStructure> kiContent = new ArrayList<>();
                kiContent.add(keyValue);
                kiContent.add(x509Data);
                KeyInfo ki = kif.newKeyInfo(kiContent);

                // Crear XMLSignature
                XMLSignature signature = fac.newXMLSignature(si, ki);

                // Crear contexto y firmar
                DOMSignContext dsc = createSignatureContext(doc, uri, privateKey);
                signature.sign(dsc);

                // Guardar documento firmado
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
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
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

    private static String getOutputFileName(String inputFileName) {
        int dotIndex = inputFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return inputFileName.substring(0, dotIndex) + "-signed" + inputFileName.substring(dotIndex);
        } else {
            return inputFileName + "-signed";
        }
    }

    private static DOMSignContext createSignatureContext(Document doc, String uri, PrivateKey privateKey) {
        if (uri.isEmpty()) {
            // Cuando no hay URI, simplemente firmar el documento completo
            // y colocar la firma al final del elemento raíz
            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            dsc.setDefaultNamespacePrefix("ds");
            return dsc;
        } else {
            // Cuando hay URI específico, mantener la lógica existente
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
}