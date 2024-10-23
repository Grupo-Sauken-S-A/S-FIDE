/*
  Derechos Reservados © 2024 Juan Carlos Ríos, Grupo Sauken S.A.

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

  Autor: Juan Carlos Ríos
  Correo electrónico: mailto:jrios@sauken.com.ar,rios.juancarlos@gmail.com
  Empresa: Grupo Sauken S.A.
  WebSite: http://www.sauken.com.ar/

  <>

  Copyright © 2024 Juan Carlos Ríos, Grupo Sauken S.A.

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

  Author: Juan Carlos Ríos
  E-mail: mailto:jrios@sauken.com.ar,rios.juancarlos@gmail.com
  Company: Grupo Sauken S.A.
  WebSite: http://www.sauken.com.ar/

 */

package com.sauken.s_fide.XMLSignerPKCS11;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Iterator;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.xpath.*;

public class XMLSignerPKCS11 {

    public static void main(String[] args) {
        try {
            if (args.length != 5) {
                throw new IllegalArgumentException("Uso: java XMLSignerPKCS11 <Ruta de la biblioteca PKCS#11> <Contraseña del token> <Número de slot> <Archivo XML> <URI>");
            }

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

            // Verificar la existencia del archivo PKCS#11
            File pkcs11Library = new File(pkcs11LibraryPath);
            if (!pkcs11Library.exists()) {
                throw new FileNotFoundException("El archivo de la biblioteca PKCS#11 no existe: " + pkcs11LibraryPath);
            }

            // Verificar la existencia del archivo XML
            File xmlFileObj = new File(xmlFile);
            if (!xmlFileObj.exists()) {
                throw new FileNotFoundException("El archivo XML no existe: " + xmlFile);
            }

            boolean result = signXML(pkcs11LibraryPath, password, slotNumber, xmlFile, uri);
            System.out.println(result);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (!(e instanceof IllegalArgumentException)) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }

    private static boolean signXML(String pkcs11LibraryPath, String password, int slotNumber, String xmlFile, String uri)
            throws Exception {
        // Configurar el proveedor PKCS#11
        String config = String.format(
                "--name=XMLSignerProvider\nlibrary=%s\nslot=%d",
                pkcs11LibraryPath,
                slotNumber
        );
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new RuntimeException("Proveedor SunPKCS11 no disponible");
        }
        provider = provider.configure(config);
        Security.addProvider(provider);

        // Cargar el KeyStore desde el token
        KeyStore keyStore = KeyStore.getInstance("PKCS11");
        try {
            keyStore.load(null, password.toCharArray());
        } catch (IOException e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("CKR_PIN_INCORRECT")) {
                throw new IOException("La contraseña del token es incorrecta.");
            }
            throw new IOException("Error al cargar el KeyStore: " + e.getMessage(), e);
        }

        // Obtener la clave privada y el certificado
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

        // Cargar el documento XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        final Document doc = dbf.newDocumentBuilder().parse(new File(xmlFile));

        // Verificar la existencia del URI en el XML si no está vacío
        if (!uri.isEmpty() && !uriExistsInXML(doc, uri)) {
            throw new Exception("El URI especificado no existe en el documento XML.");
        }

        // Crear el objeto XMLSignatureFactory
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        // Crear la referencia
        Reference ref;
        if (uri.isEmpty()) {
            ref = fac.newReference(
                    "",
                    fac.newDigestMethod(DigestMethod.SHA256, null));
        } else {
            ref = fac.newReference(
                    "#" + uri,
                    fac.newDigestMethod(DigestMethod.SHA256, null));
        }

        // Crear el SignedInfo
        SignedInfo si = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                Collections.singletonList(ref));

        // Crear el KeyInfo
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        X509Data x509Data = kif.newX509Data(Collections.singletonList(cert));
        KeyInfo ki = kif.newKeyInfo(Collections.singletonList(x509Data));

        // Crear el objeto XMLSignature
        XMLSignature signature = fac.newXMLSignature(si, ki);

        // Crear un URIDereferencer personalizado
        URIDereferencer customURIDereferencer = new URIDereferencer() {
            public Data dereference(final URIReference uriReference, final XMLCryptoContext context)
                    throws URIReferenceException {
                String uri = uriReference.getURI();
                if (uri.isEmpty()) {
                    return new NodeSetData() {
                        @SuppressWarnings("unchecked")
                        public Iterator<Node> iterator() {
                            return (Iterator<Node>) (Iterator<?>) Collections.singletonList(doc.getDocumentElement()).iterator();
                        }
                    };
                } else if (uri.startsWith("#")) {
                    String id = uri.substring(1);
                    final Node node = findElementByAttributeId(doc, id);
                    if (node != null) {
                        return new NodeSetData() {
                            public Iterator<Node> iterator() {
                                return Collections.singletonList(node).iterator();
                            }
                        };
                    }
                }
                // Si no podemos manejar la referencia, delegamos al dereferencer por defecto
                return XMLSignatureFactory.getInstance().getURIDereferencer().dereference(uriReference, context);
            }
        };

        // Determinar dónde insertar la firma
        Node parentNode;
        Node nextSibling;
        if (uri.isEmpty()) {
            parentNode = doc.getDocumentElement();
            nextSibling = null;
        } else {
            Node elementToSign = findElementByAttributeId(doc, uri);
            if (elementToSign == null) {
                throw new Exception("No se encontró el elemento con URI: " + uri);
            }
            parentNode = elementToSign.getParentNode();
            nextSibling = elementToSign.getNextSibling();
        }

        // Crear el contexto de firma con el URIDereferencer personalizado
        DOMSignContext dsc = new DOMSignContext(privateKey, parentNode);
        dsc.setURIDereferencer(customURIDereferencer);

        // Configurar la posición de la firma
        if (nextSibling != null) {
            dsc.setNextSibling(nextSibling);
        }

        // Firmar el documento
        signature.sign(dsc);

        // Guardar el documento firmado
        String outputXmlFile = getOutputFileName(xmlFile);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer();
        trans.setOutputProperty(OutputKeys.INDENT, "no");
        trans.setOutputProperty(OutputKeys.METHOD, "xml");
        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        trans.transform(new DOMSource(doc), new StreamResult(new File(outputXmlFile)));

        System.out.println("Documento XML firmado guardado como: " + outputXmlFile);
        return true;
    }

    private static boolean uriExistsInXML(Document doc, String uri) throws XPathExpressionException {
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();

        // Eliminar el "#" inicial del URI si está presente
        String cleanUri = uri.startsWith("#") ? uri.substring(1) : uri;

        // Buscar elementos con atributo 'Id' o 'id' que coincidan con el URI
        XPathExpression expr = xpath.compile("//*[@Id='" + cleanUri + "' or @id='" + cleanUri + "' or local-name()='" + cleanUri + "']");
        NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        return nl.getLength() > 0;
    }

    private static Node findElementByAttributeId(Document doc, String id) {
        NodeList nodeList = doc.getElementsByTagName("*");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (id.equals(element.getAttribute("Id")) || id.equals(element.getAttribute("id")) || id.equals(element.getTagName())) {
                    return element;
                }
            }
        }
        return null;
    }

    private static String getOutputFileName(String inputFileName) {
        int dotIndex = inputFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return inputFileName.substring(0, dotIndex) + "-ds" + inputFileName.substring(dotIndex);
        } else {
            return inputFileName + "-ds";
        }
    }
}
