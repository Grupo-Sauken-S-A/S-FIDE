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

package com.sauken.s_fide.XMLVerifySignatures;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class XMLVerifySignatures {

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.err.println("Error: Uso incorrecto.");
                System.err.println("Uso: java -jar XMLVerifySignatures.jar <Archivo XML>");
                System.exit(1);
            }

            String xmlFile = args[0];

            if (!isInternetAvailable()) {
                System.err.println("Error: No hay conexión a Internet disponible.");
                System.exit(1);
            }

            File xmlFileObj = new File(xmlFile);
            if (!xmlFileObj.exists()) {
                System.err.println("Error: El archivo XML no existe: " + xmlFile);
                System.exit(1);
            }

            boolean hasSignatures = verifyXMLSignatures(xmlFile);
            if (!hasSignatures) {
                System.out.println("El documento XML no contiene firmas digitales.");
                System.exit(1);
            }
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static boolean isInternetAvailable() {
        try {
            InetAddress.getByName("www.google.com").isReachable(3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean verifyXMLSignatures(String xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.parse(new File(xmlFile));

        NodeList signatureNodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");

        if (signatureNodes.getLength() == 0) {
            return false;
        }

        for (int i = 0; i < signatureNodes.getLength(); i++) {
            Node signatureNode = signatureNodes.item(i);
            verifySignature(doc, signatureNode, i + 1);
        }

        return true;
    }

    private static void verifySignature(Document doc, Node signatureNode, int signatureNumber) throws Exception {
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext valContext = new DOMValidateContext(new X509KeySelector(), signatureNode);

        // Configurar un URIDereferencer personalizado
        valContext.setURIDereferencer(new URIDereferencer() {
            public Data dereference(URIReference uriReference, XMLCryptoContext context)
                    throws URIReferenceException {
                String uri = uriReference.getURI();
                if (uri.startsWith("#")) {
                    String id = uri.substring(1);
                    Element elem = getElementById(doc, id);
                    if (elem != null) {
                        return new NodeSetData() {
                            public Iterator<Node> iterator() {
                                return Collections.singletonList((Node)elem).iterator();
                            }
                        };
                    }
                }
                // Si no podemos manejar la referencia, delegamos al dereferencer por defecto
                return XMLSignatureFactory.getInstance().getURIDereferencer().dereference(uriReference, context);
            }
        });

        XMLSignature signature = fac.unmarshalXMLSignature(valContext);

        boolean coreValidity = signature.validate(valContext);
        boolean sv = signature.getSignatureValue().validate(valContext);

        System.out.println("Firma #" + signatureNumber + ":");

        boolean referencesValid = true;
        List<Reference> references = signature.getSignedInfo().getReferences();
        for (Reference ref : references) {
            boolean refValid = ref.validate(valContext);
            if (!refValid) {
                referencesValid = false;
                System.out.println("  Referencia inválida: " + ref.getURI());
                System.out.println("    Digest calculado: " + Base64.getEncoder().encodeToString(ref.getCalculatedDigestValue()));
                System.out.println("    Digest en el XML: " + Base64.getEncoder().encodeToString(ref.getDigestValue()));
            }
        }

        if (coreValidity && sv && referencesValid) {
            System.out.println("  La firma es válida.");
            System.out.println("  Integridad del contenido firmado: Intacto");
        } else {
            System.out.println("  La firma no es válida.");
            if (!coreValidity) {
                System.out.println("  La validación principal de la firma falló.");
            }
            if (!sv) {
                System.out.println("  La validación del valor de la firma falló.");
            }
            if (!referencesValid) {
                System.out.println("  La integridad del contenido firmado ha sido comprometida.");
            }
        }

        KeyInfo keyInfo = signature.getKeyInfo();
        if (keyInfo != null) {
            X509Data x509Data = null;
            for (Object o : keyInfo.getContent()) {
                if (o instanceof X509Data) {
                    x509Data = (X509Data) o;
                    break;
                }
            }
            if (x509Data != null) {
                for (Object o : x509Data.getContent()) {
                    if (o instanceof X509Certificate) {
                        X509Certificate cert = (X509Certificate) o;
                        printCertificateInfo(cert);
                        verifyCertificate(cert);
                        break;
                    }
                }
            }
        }

        printSignatureDetails(signature);
        System.out.println();
    }

    private static Element getElementById(Document doc, String id) {
        Element elem = doc.getElementById(id);
        if (elem != null) {
            return elem;
        }
        NodeList nodeList = doc.getElementsByTagName("*");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (id.equals(element.getAttribute("id")) || id.equals(element.getAttribute("Id"))) {
                    return element;
                }
            }
        }
        return null;
    }

    private static void verifyCertificate(X509Certificate cert) {
        try {
            cert.checkValidity();
            System.out.println("  Certificado válido en fecha actual.");

            // Aquí se puede agregar la lógica para verificación OCSP y CRL
            System.out.println("  Nota: Verificación OCSP y CRL no implementada en esta versión.");
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            System.out.println("  Certificado no válido en fecha actual: " + e.getMessage());
        }
    }

    private static void printCertificateInfo(X509Certificate cert) {
        System.out.println("  Información del Certificado:");
        System.out.println("    Sujeto: " + cert.getSubjectX500Principal());
        System.out.println("    Emisor: " + cert.getIssuerX500Principal());
        System.out.println("    Número de Serie: " + cert.getSerialNumber());
        System.out.println("    Válido desde: " + cert.getNotBefore());
        System.out.println("    Válido hasta: " + cert.getNotAfter());
    }

    private static void printSignatureDetails(XMLSignature signature) throws Exception {
        System.out.println("  Detalles de la Firma:");
        SignedInfo signedInfo = signature.getSignedInfo();

        System.out.println("    Método de Canonicalización: " + signedInfo.getCanonicalizationMethod().getAlgorithm());
        System.out.println("    Método de Firma: " + signedInfo.getSignatureMethod().getAlgorithm());

        List<?> references = signedInfo.getReferences();
        for (int i = 0; i < references.size(); i++) {
            Reference ref = (Reference) references.get(i);
            System.out.println("    Referencia #" + (i + 1) + ":");
            System.out.println("      URI: " + ref.getURI());
            System.out.println("      Método de Digest: " + ref.getDigestMethod().getAlgorithm());
            System.out.println("      Valor del Digest: " + Base64.getEncoder().encodeToString(ref.getDigestValue()));
        }

        System.out.println("    Valor de la Firma: " + Base64.getEncoder().encodeToString(signature.getSignatureValue().getValue()));
    }

    private static class X509KeySelector extends KeySelector {
        public KeySelectorResult select(KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Null KeyInfo object!");
            }
            for (Object o : keyInfo.getContent()) {
                if (o instanceof X509Data) {
                    X509Data x509Data = (X509Data) o;
                    for (Object x509Object : x509Data.getContent()) {
                        if (x509Object instanceof X509Certificate) {
                            final X509Certificate cert = (X509Certificate) x509Object;
                            return new KeySelectorResult() {
                                public Key getKey() { return cert.getPublicKey(); }
                            };
                        }
                    }
                }
            }
            throw new KeySelectorException("No X509Certificate found!");
        }
    }
}
