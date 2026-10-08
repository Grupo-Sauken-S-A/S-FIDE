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

  Autores: Juan Carlos Ríos y Juan Ignacio Ríos con la asistencia de Claude Sonnet 5.5
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

  Authors: Juan Carlos Ríos y Juan Ignacio Ríos with support of Claude Sonnet 5.5
  E-mail: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Company: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

 */


package com.sauken.s_fide.s_fide_gui.viewer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un documento XML listo para mostrarse, sin ninguna dependencia de interfaz gráfica: se lee de forma segura,
 * y se identifican las firmas digitales que contiene y qué elementos firmó cada una.
 * <p>
 * El marcado de firmas es <b>informativo</b>: sale de los datos de la propia firma (la referencia a cada
 * elemento y el nombre del certificado que trae adentro). No verifica nada: si una firma es válida lo dice
 * el verificador de S-FiDE, no este visor.
 * <p>
 * Seguridad: los XML pueden venir de terceros, así que no se resuelven entidades externas ni DTD (un
 * documento que declare un DTD no se abre) y se limita el tamaño.
 */
public final class XmlDocumentModel {

    public static final String NS_DSIG = "http://www.w3.org/2000/09/xmldsig#";
    static final long TAMANO_MAXIMO = 60L * 1024 * 1024;
    static final String FIRMANTE_DESCONOCIDO = "firmante no identificado";

    /** El documento no se puede mostrar; el mensaje ya está en lenguaje simple. */
    public static final class XmlViewException extends Exception {
        public XmlViewException(String mensaje) {
            super(mensaje);
        }
    }

    /** Una firma digital del documento: quién la hizo y qué elementos cubre. */
    public record FirmaXml(Element elemento, String firmante, List<Element> cubre) {
    }

    private final Document documento;
    private final List<FirmaXml> firmas = new ArrayList<>();
    private final Map<Element, Set<String>> firmantesPorElemento = new IdentityHashMap<>();
    private final Map<Element, FirmaXml> firmaPorElemento = new IdentityHashMap<>();
    private final int cantidadDeElementos;

    private XmlDocumentModel(Document documento) {
        this.documento = documento;
        Map<String, Element> porId = new HashMap<>();
        this.cantidadDeElementos = indexar(documento.getDocumentElement(), porId);
        identificarFirmas(porId);
    }

    public static XmlDocumentModel abrir(Path archivo) throws XmlViewException {
        try {
            long tamano = Files.size(archivo);
            if (tamano > TAMANO_MAXIMO) {
                throw new XmlViewException("El archivo es demasiado grande para mostrarlo (" + (tamano / (1024 * 1024))
                        + " MB). El visor admite hasta " + (TAMANO_MAXIMO / (1024 * 1024)) + " MB.");
            }
            return desdeBytes(Files.readAllBytes(archivo));
        } catch (IOException e) {
            throw new XmlViewException("No se pudo leer el archivo. Verifique que exista y que tenga permiso para abrirlo.");
        }
    }

    static XmlDocumentModel desdeBytes(byte[] bytes) throws XmlViewException {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder constructor = fabrica.newDocumentBuilder();
            // Sin esto el analizador escribe el error en la consola: el mensaje ya lo armamos nosotros.
            constructor.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return new XmlDocumentModel(constructor.parse(new ByteArrayInputStream(bytes)));
        } catch (SAXParseException e) {
            String detalle = e.getMessage() == null ? "" : e.getMessage();
            if (detalle.contains("DOCTYPE")) {
                throw new XmlViewException("Este XML declara un DTD (<!DOCTYPE …>), que S-FiDE no abre por seguridad.");
            }
            throw new XmlViewException("El archivo no es un XML bien formado (línea " + e.getLineNumber()
                    + ", columna " + e.getColumnNumber() + ").");
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new XmlViewException("El archivo no es un XML que se pueda mostrar.");
        }
    }

    public Document documento() {
        return documento;
    }

    public List<FirmaXml> firmas() {
        return Collections.unmodifiableList(firmas);
    }

    public int cantidadDeElementos() {
        return cantidadDeElementos;
    }

    /** Nombres de quienes firmaron este elemento (por referencia directa o porque la firma cubre todo el documento). */
    public List<String> firmantesDe(Element elemento) {
        Set<String> nombres = firmantesPorElemento.get(elemento);
        return nombres == null ? List.of() : List.copyOf(nombres);
    }

    /** La firma que representa este elemento, si es un {@code <ds:Signature>}. */
    public FirmaXml firmaDe(Element elemento) {
        return firmaPorElemento.get(elemento);
    }

    // ---------------------------------------------------------------------------------------------

    private static int indexar(Element elemento, Map<String, Element> porId) {
        int cuenta = 1;
        NamedNodeMap atributos = elemento.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Node atributo = atributos.item(i);
            String nombre = atributo.getLocalName() != null ? atributo.getLocalName() : atributo.getNodeName();
            if (nombre.equals("Id") || nombre.equals("ID") || nombre.equals("id")) {
                porId.putIfAbsent(atributo.getNodeValue(), elemento);
            }
        }
        for (Node hijo = elemento.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            if (hijo instanceof Element e) {
                cuenta += indexar(e, porId);
            }
        }
        return cuenta;
    }

    private void identificarFirmas(Map<String, Element> porId) {
        NodeList lista = documento.getElementsByTagNameNS(NS_DSIG, "Signature");
        for (int i = 0; i < lista.getLength(); i++) {
            Element firma = (Element) lista.item(i);
            String firmante = nombreDelFirmante(firma);
            List<Element> cubre = new ArrayList<>();

            NodeList referencias = firma.getElementsByTagNameNS(NS_DSIG, "Reference");
            for (int j = 0; j < referencias.getLength(); j++) {
                String uri = ((Element) referencias.item(j)).getAttribute("URI");
                Element firmado = null;
                if (uri.isEmpty()) {
                    firmado = documento.getDocumentElement();
                } else if (uri.startsWith("#")) {
                    firmado = porId.get(uri.substring(1));
                }
                if (firmado != null && !cubre.contains(firmado)) {
                    cubre.add(firmado);
                    firmantesPorElemento.computeIfAbsent(firmado, k -> new LinkedHashSet<>())
                            .add(firmante == null ? FIRMANTE_DESCONOCIDO : firmante);
                }
            }
            FirmaXml datos = new FirmaXml(firma, firmante, List.copyOf(cubre));
            firmas.add(datos);
            firmaPorElemento.put(firma, datos);
        }
    }

    /** CN del primer certificado incluido en la firma, o {@code null} si no trae ninguno legible. */
    private static String nombreDelFirmante(Element firma) {
        NodeList certificados = firma.getElementsByTagNameNS(NS_DSIG, "X509Certificate");
        if (certificados.getLength() == 0) {
            return null;
        }
        try {
            byte[] der = Base64.getMimeDecoder().decode(certificados.item(0).getTextContent().trim());
            X509Certificate certificado = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
            for (Rdn rdn : new LdapName(certificado.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return rdn.getValue().toString();
                }
            }
            return certificado.getSubjectX500Principal().getName();
        } catch (CertificateException | IllegalArgumentException | javax.naming.InvalidNameException e) {
            return null;
        }
    }

    /** El nodo como texto XML con sangría, para copiarlo. */
    public static String serializar(Node nodo) {
        try {
            TransformerFactory fabrica = TransformerFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformador = fabrica.newTransformer();
            transformador.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformador.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter salida = new StringWriter();
            transformador.transform(new DOMSource(nodo), new StreamResult(salida));
            return salida.toString();
        } catch (TransformerException e) {
            return nodo.getTextContent();
        }
    }

    /** Ruta del elemento, por ejemplo {@code /Certificado/COD[2]/Exportador}. */
    public static String ruta(Node nodo) {
        StringBuilder ruta = new StringBuilder();
        for (Node actual = nodo; actual != null && actual.getNodeType() != Node.DOCUMENT_NODE;
             actual = actual.getParentNode()) {
            if (actual instanceof Element e) {
                int posicion = 1;
                int mismos = 0;
                for (Node hermano = e.getParentNode() == null ? null : e.getParentNode().getFirstChild();
                     hermano != null; hermano = hermano.getNextSibling()) {
                    if (hermano instanceof Element h && h.getNodeName().equals(e.getNodeName())) {
                        mismos++;
                        if (hermano == e) {
                            posicion = mismos;
                        }
                    }
                }
                ruta.insert(0, "/" + e.getNodeName() + (mismos > 1 ? "[" + posicion + "]" : ""));
            }
        }
        return ruta.toString();
    }
}
