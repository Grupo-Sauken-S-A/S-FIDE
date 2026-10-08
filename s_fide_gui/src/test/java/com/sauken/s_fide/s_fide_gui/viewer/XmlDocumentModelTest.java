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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlDocumentModelTest {

    /** Certificado de prueba con CN=Ana Pérez, generado solo para estas pruebas. */
    private static final String CERTIFICADO = "MIIDIDCCAgigAwIBAgIJAKTow2LJNnVLMA0GCSqGSIb3DQEBDAUAMD4xCzAJBgNVBAYTAkFSMRowGAYDVQQKExFFbXByZXNhIGRlIFBydWViYTETMBEGA1UEAwwKQW5hIFDDqXJlejAeFw0yNjEwMDgxNjQzMTVaFw0zNjEwMDUxNjQzMTVaMD4xCzAJBgNVBAYTAkFSMRowGAYDVQQKExFFbXByZXNhIGRlIFBydWViYTETMBEGA1UEAwwKQW5hIFDDqXJlejCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJwI++nF11jFIShM/N1jZi5TkZQs2kdlrm96O6uUpHyHl2RzVJkkeJxp8RDv3x6pIisRSitjjcOBPs6ovDfo1/UcoDD6MaaVBJ1jxH7dJddkds4WRv0VAj8PcoucDvK2YLK8blU1/8Q3istwz44kzF23FhNum5vFqSpThKvjCBQu+jp1B1C5wMV4k9B9jQbbl4qRiUe9yrYIa25FDLTyDCmlrPQ4tKbD6SFYvo3I2ztiQDa90FtlNLnTgoX4ys9LXL4q+KGICogbeICn1aB/ifAo43cGcU37uIIH09XlDYTzRWefbgeGcKcW+NQlPlo4BW+kPIy/pbvxVSdzM3HTmM8CAwEAAaMhMB8wHQYDVR0OBBYEFPwra+gRrEF2cJ6tb6aEt9TEC5PUMA0GCSqGSIb3DQEBDAUAA4IBAQCMK35ovTEdbCRqHMxQ0osxMTed6vJlN7ipl093ogyC74EqLk6jhpqBigWvD1qs/WEO/m3NX2KkOA559H2XW3HpU9boBM2O2nuc0FWyEALxl0yksdiJ4EqANQ1MJq1O8zX1c/5BXZR0HZskv7aAJedivb0KBn5l19g3teymv5WTTlQ11KOxSje8nrado2TO144PmDvAeZIwwzecd+A61UkbviVfdcqphUjJCkRjUEi+Y0n7uS8yCv4BXQ2ikZHHLr+a6cP8zdcxm3hfnTVi9Y4NhcUYL6+KLbgQENhHEVYCBAEBxtVeFJ2wR6B9DZVoPnoWjQc1D0E6YO6iDczR+Byp";

    private static String firma(String uri, String certificado) {
        return "<ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:SignedInfo>"
                + "<ds:Reference URI=\"" + uri + "\"/></ds:SignedInfo><ds:SignatureValue>AAAA</ds:SignatureValue>"
                + (certificado == null ? "" : "<ds:KeyInfo><ds:X509Data><ds:X509Certificate>" + certificado
                + "</ds:X509Certificate></ds:X509Data></ds:KeyInfo>")
                + "</ds:Signature>";
    }

    private static XmlDocumentModel modelo(String xml) throws Exception {
        return XmlDocumentModel.desdeBytes(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static Element elemento(XmlDocumentModel m, String nombre) {
        return (Element) m.documento().getElementsByTagName(nombre).item(0);
    }

    @Test
    void marcaComoFirmadoElElementoAlQueApuntaLaReferenciaConElNombreDelCertificado() throws Exception {
        XmlDocumentModel m = modelo("<Doc><COD Id=\"COD\"><x>1</x></COD><Otro Id=\"OTRO\"/>"
                + firma("#COD", CERTIFICADO) + "</Doc>");

        assertEquals(List.of("Ana Pérez"), m.firmantesDe(elemento(m, "COD")));
        assertTrue(m.firmantesDe(elemento(m, "Otro")).isEmpty(), "Lo que ninguna firma cubre no se marca");
        assertEquals(1, m.firmas().size());
    }

    @Test
    void laFirmaMismaSeIdentificaYConoceLoQueCubre() throws Exception {
        XmlDocumentModel m = modelo("<Doc><COD Id=\"COD\"/>" + firma("#COD", CERTIFICADO) + "</Doc>");

        Element signature = (Element) m.documento().getElementsByTagNameNS(XmlDocumentModel.NS_DSIG, "Signature").item(0);
        XmlDocumentModel.FirmaXml datos = m.firmaDe(signature);
        assertNotNull(datos);
        assertEquals("Ana Pérez", datos.firmante());
        assertEquals(1, datos.cubre().size());
        assertNull(m.firmaDe(elemento(m, "COD")));
    }

    @Test
    void unaReferenciaVaciaCubreTodoElDocumento() throws Exception {
        XmlDocumentModel m = modelo("<Doc><dato/>" + firma("", CERTIFICADO) + "</Doc>");

        assertEquals(List.of("Ana Pérez"), m.firmantesDe(m.documento().getDocumentElement()));
    }

    @Test
    void sinCertificadoLegibleElFirmanteEsDesconocidoPeroLaMarcaIgualAparece() throws Exception {
        XmlDocumentModel m = modelo("<Doc><COD Id=\"COD\"/>" + firma("#COD", null) + "</Doc>");

        assertEquals(List.of(XmlDocumentModel.FIRMANTE_DESCONOCIDO), m.firmantesDe(elemento(m, "COD")));
    }

    @Test
    void dosFirmantesSobreElMismoElementoSeListanSinRepetir() throws Exception {
        XmlDocumentModel m = modelo("<Doc><COD Id=\"COD\"/>" + firma("#COD", CERTIFICADO)
                + firma("#COD", CERTIFICADO) + firma("#COD", null) + "</Doc>");

        assertEquals(List.of("Ana Pérez", XmlDocumentModel.FIRMANTE_DESCONOCIDO), m.firmantesDe(elemento(m, "COD")));
        assertEquals(3, m.firmas().size());
    }

    @Test
    void unDocumentoQueDeclaraUnDtdSeRechazaPorSeguridad() {
        XmlDocumentModel.XmlViewException e = assertThrows(XmlDocumentModel.XmlViewException.class,
                () -> modelo("<?xml version=\"1.0\"?><!DOCTYPE Doc [<!ENTITY x SYSTEM \"file:///C:/Windows/win.ini\">]><Doc>&x;</Doc>"));

        assertTrue(e.getMessage().contains("DTD"), e.getMessage());
    }

    @Test
    void unXmlMalFormadoSeExplicaEnEspanolConLaPosicion() {
        XmlDocumentModel.XmlViewException e = assertThrows(XmlDocumentModel.XmlViewException.class,
                () -> modelo("<Doc>\n<abierto>\n</Doc>"));

        assertTrue(e.getMessage().startsWith("El archivo no es un XML bien formado"), e.getMessage());
        assertTrue(e.getMessage().contains("línea"), e.getMessage());
        assertTrue(!e.getMessage().contains("Exception"), e.getMessage());
    }

    @Test
    void cuentaLosElementosYArmaLaRuta() throws Exception {
        XmlDocumentModel m = modelo("<A><B/><B><C/></B></A>");

        assertEquals(4, m.cantidadDeElementos());
        Element c = elemento(m, "C");
        assertEquals("/A/B[2]/C", XmlDocumentModel.ruta(c));
    }

    @Test
    void elTextoCopiadoDeUnNodoEsXmlConSangria() throws Exception {
        XmlDocumentModel m = modelo("<A><B x=\"1\"><C>texto</C></B></A>");

        String texto = XmlDocumentModel.serializar(elemento(m, "B"));
        assertTrue(texto.contains("<C>texto</C>"), texto);
        assertTrue(texto.contains("x=\"1\""), texto);
    }
}
