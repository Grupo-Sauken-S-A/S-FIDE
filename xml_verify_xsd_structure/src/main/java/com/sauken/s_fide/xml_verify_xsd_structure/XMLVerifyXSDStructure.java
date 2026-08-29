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

package com.sauken.s_fide.xml_verify_xsd_structure;

import com.sauken.s_fide.xml_verify_xsd_structure.handlers.XMLErrorHandler;
import com.sauken.s_fide.xml_verify_xsd_structure.utils.XSDDownloader;
import com.sauken.s_fide.xml_verify_xsd_structure.utils.X509KeySelector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.DigestMethod;
import java.util.List;
import javax.xml.crypto.dsig.SignatureMethod;

public class XMLVerifyXSDStructure {
    private static final String VERSION = "S-FIDE XMLVerifyXSDStructure v1.0.0 - Grupo Sauken S.A.";
    private static final String LICENSE_TEXT;
    private static final String HELP_TEXT;
    private static PrintStream errorStream;

    static {
        LICENSE_TEXT = readResourceFile("/LICENSE.txt");
        HELP_TEXT = readResourceFile("/HELP.txt");
        try {
            errorStream = new PrintStream(System.err, true, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error al configurar la codificación de errores");
        }
    }

    private static String readResourceFile(String resourcePath) {
        try (InputStream is = XMLVerifyXSDStructure.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                errorStream.println("No se pudo encontrar el recurso: " + resourcePath);
                return "Error: Archivo de recurso no encontrado";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            errorStream.println("Error al leer el archivo de recurso: " + resourcePath);
            return "Error al leer el archivo de recurso";
        }
    }

    private static void showHelp() {
        try {
            PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8.name());
            out.println(HELP_TEXT);
            out.flush();
        } catch (UnsupportedEncodingException e) {
            errorStream.println("Error mostrando ayuda: " + e.getMessage());
        }
    }

    private static String extractFileNameFromUrl(String url) {
        String fileName = url;
        int queryIndex = fileName.indexOf('?');
        if (queryIndex != -1) {
            fileName = fileName.substring(0, queryIndex);
        }
        int lastSlashIndex = fileName.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            fileName = fileName.substring(lastSlashIndex + 1);
        }
        return fileName;
    }

    private static void configureSystemStreams() throws UnsupportedEncodingException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
    }

    public static void main(String[] args) {
        try {
            configureSystemStreams();

            if (args.length == 0) {
                showHelp();
                System.exit(1);
            }

            System.setProperty("javax.xml.accessExternalSchema", "all");
            System.setProperty("javax.xml.accessExternalDTD", "all");
            System.setProperty("xml.catalog.files", "catalog.xml");

            if (args.length == 1) {
                switch (args[0].toLowerCase()) {
                    case "-version":
                        System.out.println(VERSION);
                        System.exit(0);
                        break;
                    case "-licencia":
                        System.out.println(LICENSE_TEXT);
                        System.exit(0);
                        break;
                    case "-ayuda":
                        showHelp();
                        System.exit(0);
                        break;
                }
            }

            if (args.length > 2) {
                errorStream.println("Error: Número incorrecto de argumentos.");
                System.exit(1);
            }

            String xmlFile = args[0];
            String xsdFile = null;

            File xmlFileObj = new File(xmlFile);
            if (!xmlFileObj.exists()) {
                errorStream.println("Error: El archivo XML no existe: " + xmlFile);
                System.exit(1);
            }

            Document doc = loadAndRegisterDocument(xmlFile);
            String xsdUrl = XSDDownloader.extractXSDUrlFromXML(doc);

            if (args.length == 2) {
                xsdFile = args[1];
                File xsdFileObj = new File(xsdFile);
                if (!xsdFileObj.exists()) {
                    errorStream.println("Error: El archivo XSD no existe: " + xsdFile);
                    System.exit(1);
                }

                if (xsdUrl != null) {
                    String urlXsdFileName = extractFileNameFromUrl(xsdUrl);
                    String inputXsdFileName = xsdFileObj.getName();

                    if (!urlXsdFileName.equals(inputXsdFileName)) {
                        System.out.println("\nNOTA: Diferencia en nombres de archivo XSD");
                        System.out.println("├─ XSD referenciado en XML: " + urlXsdFileName);
                        System.out.println("├─ XSD proporcionado: " + inputXsdFileName);
                        System.out.println("└─ Se utilizará el archivo proporcionado: " + xsdFile);
                        System.out.println();
                    }
                }
            } else if (xsdUrl != null) {
                System.out.println("Descargando XSD desde: " + xsdUrl);
                try {
                    File downloadedXsd = XSDDownloader.downloadXSD(xsdUrl);
                    xsdFile = downloadedXsd.getPath();
                } catch (IOException e) {
                    errorStream.println("Error al descargar el XSD: " + e.getMessage());
                    System.exit(1);
                }
            } else {
                errorStream.println("Error: No se encontró referencia a esquema XSD en el XML y no se proporcionó archivo XSD.");
                System.exit(1);
            }

            boolean isValid = true;

            System.out.println("\nValidando estructura XML contra XSD...");
            isValid &= validateXMLAgainstXSD(xmlFile, xsdFile);

            System.out.println("\nVerificando firmas digitales...");
            isValid &= verifyXMLSignatures(xmlFile);

            System.out.println("\nResultado final:");
            if (isValid) {
                System.out.println("El documento XML es válido y todas las firmas parecen correctas. Este proceso no realiza validación de revocación de las firmas digitales aplicadas.");
                System.exit(0);
            } else {
                errorStream.println("Se encontraron errores en la validación del documento XML.");
                System.exit(1);
            }

        } catch (Exception e) {
            errorStream.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean validateXMLAgainstXSD(String xmlFile, String xsdFile) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");

        Schema schema;
        try {
            Source schemaSource = new StreamSource(new File(xsdFile));
            schema = schemaFactory.newSchema(schemaSource);
        } catch (SAXException e) {
            errorStream.println("Error al procesar el archivo XSD: " + e.getMessage());
            return false;
        }

        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");

        XMLErrorHandler errorHandler = new XMLErrorHandler();
        validator.setErrorHandler(errorHandler);

        try {
            Source xmlSource = new StreamSource(new File(xmlFile));
            validator.validate(xmlSource);
        } catch (SAXException e) {
            errorStream.println("Error de validación XML: " + e.getMessage());
            return false;
        }

        errorHandler.printMessages();
        return !errorHandler.hasErrors();
    }

    private static Document loadAndRegisterDocument(String xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");
        dbf.setExpandEntityReferences(false);
        dbf.setValidating(false);
        dbf.setXIncludeAware(false);

        Document doc;
        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            InputSource is = new InputSource(new InputStreamReader(fis, StandardCharsets.UTF_8.name()));
            is.setEncoding(StandardCharsets.UTF_8.name());
            doc = dbf.newDocumentBuilder().parse(is);
        }

        registerIDAttributes(doc.getDocumentElement());
        return doc;
    }

    private static void registerIDAttributes(Element element) {
        if (element.hasAttribute("ID")) {
            element.setIdAttribute("ID", true);
        }
        if (element.hasAttribute("Id")) {
            element.setIdAttribute("Id", true);
        }
        if (element.hasAttribute("id")) {
            element.setIdAttribute("id", true);
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();
            if (attrName.toLowerCase().contains("id")) {
                try {
                    element.setIdAttribute(attrName, true);
                } catch (Exception ignored) {}
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                registerIDAttributes((Element) node);
            }
        }
    }

    private static boolean verifyXMLSignatures(String xmlFile) throws Exception {
        Document doc = loadAndRegisterDocument(xmlFile);
        NodeList signatureNodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");

        if (signatureNodes.getLength() == 0) {
            System.out.println("El documento XML no contiene firmas digitales.");
            return false;
        }

        boolean allValid = true;
        for (int i = 0; i < signatureNodes.getLength(); i++) {
            allValid &= verifySignature(doc, signatureNodes.item(i), i + 1);
        }

        return allValid;
    }

    private static boolean verifySignature(Document doc, Node signatureNode, int signatureNumber) {
        try {
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext valContext = new DOMValidateContext(
                    new X509KeySelector(), signatureNode);

            // Desactivar la validación segura para permitir SHA1
            valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

            valContext.setBaseURI(doc.getBaseURI());
            valContext.setURIDereferencer(fac.getURIDereferencer());

            XMLSignature signature = fac.unmarshalXMLSignature(valContext);

            // Verificar el algoritmo de firma y digest usado
            SignedInfo signedInfo = signature.getSignedInfo();
            String signatureMethod = signedInfo.getSignatureMethod().getAlgorithm();
            List<Reference> references = signedInfo.getReferences();

            // Mostrar información sobre el método de firma
            System.out.println("Firma #" + signatureNumber + ":");
            System.out.println("├─ Método de firma: " +
                    (signatureMethod.equals(SignatureMethod.RSA_SHA1) ? "RSA-SHA1" :
                            signatureMethod.equals(SignatureMethod.RSA_SHA256) ? "RSA-SHA256" : signatureMethod));

            for (Reference ref : references) {
                String digestMethod = ref.getDigestMethod().getAlgorithm();
                if (!digestMethod.equals(DigestMethod.SHA256) && !digestMethod.equals(DigestMethod.SHA1)) {
                    System.out.println("└─ Advertencia: Algoritmo de digest no soportado: " + digestMethod);
                }
            }

            boolean coreValidity = signature.validate(valContext);
            boolean sv = signature.getSignatureValue().validate(valContext);

            boolean referencesValid = true;
            for (Reference ref : references) {
                boolean refValid = ref.validate(valContext);
                if (!refValid) {
                    System.out.println("└─ Referencia inválida: " + ref.getURI());
                    System.out.println("   Algoritmo de digest usado: " + ref.getDigestMethod().getAlgorithm());
                    referencesValid = false;
                }
            }

            boolean isValid = coreValidity && sv && referencesValid;
            System.out.println("└─ Estado: " + (isValid ? "Válida" : "Inválida"));

            if (isValid) {
                // Mostrar información adicional sobre la firma válida
                for (Reference ref : references) {
                    String digestMethod = ref.getDigestMethod().getAlgorithm();
                    System.out.println("   ├─ URI: " + ref.getURI());
                    System.out.println("   └─ Algoritmo de digest: " +
                            (digestMethod.equals(DigestMethod.SHA256) ? "SHA256" :
                                    digestMethod.equals(DigestMethod.SHA1) ? "SHA1" : digestMethod));
                }
            }

            return isValid;

        } catch (Exception e) {
            System.out.println("Error al validar la firma #" + signatureNumber + ": " + e.getMessage());
            return false;
        }
    }

}