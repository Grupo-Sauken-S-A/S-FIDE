/*
  Derechos Reservados © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

  Este es un Software Libre; como tal redistribuirlo y/o modificarlo está
  permitido, siempre y cuando se haga bajo los términos y condiciones de la
  Licencia Pública General GNU publicada por la Free Software Foundation,
  ya sea en su versión 2 ó cualquier otra de las posteriores a la misma.

  Este "Programa" se distribuye con la intención de que sea útil, sin
  embargo carece de garantía, ni siquiera tiene la garantía implícita de
  tipo comercial o inherente al propósito del mismo "Programa". Ver la
  Licencia Pública General GNU para más detalles.

  Se debe haber recibido una copia de la Licencia Pública General GNU con
  este "Programa", si este no fue el caso, favor de escribir a la Free
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

package com.sauken.s_fide.integration_tests;

import com.sauken.s_fide.integration_tests.ProcesoSupport.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.sauken.s_fide.integration_tests.ProcesoSupport.ejecutarModulo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verificación y validación XSD por lotes, contra los programas reales como proceso. */
class LoteVerificacionTest {

    private static final String XML = "com.sauken.s_fide.xml_verify_signatures.XMLVerifySignatures";
    private static final String XSD = "com.sauken.s_fide.xml_verify_xsd_structure.XMLVerifyXSDStructure";
    private static final String PDF = "com.sauken.s_fide.pdf_verify_signatures.PDFVerifySignatures";
    private static final String FIRMA_XML = "com.sauken.s_fide.xml_signer_pkcs12.XMLSignerPKCS12";
    private static final String FIRMA_PDF = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";

    private static final String ESQUEMA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="doc">
                <xs:complexType><xs:sequence>
                  <xs:element name="item" type="xs:string"/>
                  <xs:any namespace="##other" processContents="skip" minOccurs="0" maxOccurs="unbounded"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    private String clave;

    @BeforeEach
    void certificado() throws Exception {
        clave = ProcesoSupport.generarP12(dir, dir.resolve("prueba.p12"), "S-FiDE Lote Verificación");
    }

    private Path escribir(String nombre, String contenido) throws IOException {
        Path archivo = dir.resolve(nombre);
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }

    private Path lista(String... lineas) throws IOException {
        return escribir("lista.txt", String.join("\n", lineas) + "\n");
    }

    private Resultado lote(String programa, Path lista, String... opciones) throws Exception {
        List<String> argumentos = new ArrayList<>(List.of("-lote", lista.toString()));
        argumentos.addAll(List.of(opciones));
        return ejecutarModulo(dir, programa, argumentos.toArray(new String[0]));
    }

    private static List<String> estados(Resultado r) {
        return r.salida().lines().filter(l -> l.startsWith("LOTE_DOC;")).map(l -> {
            String[] campos = l.substring("LOTE_DOC;\"".length(), l.length() - 1).split("\";\"", -1);
            return campos[2] + "/" + campos[3];
        }).toList();
    }

    private static String resumen(Resultado r) {
        return r.salida().lines().filter(l -> l.startsWith("LOTE_RESUMEN;")).findFirst().orElse("(sin resumen) "
                + r.salida());
    }

    private void firmarXml(String nombre) throws Exception {
        Resultado r = ejecutarModulo(dir, FIRMA_XML, dir.resolve("prueba.p12").toString(), clave,
                dir.resolve(nombre).toString(), "");
        assertEquals(0, r.exitCode(), r.salida());
    }

    @Test
    void verificaUnLoteDeXml() throws Exception {
        escribir("bueno.xml", "<doc>\n  <item>ñandú</item>\n</doc>\n");
        firmarXml("bueno.xml");
        escribir("alterado.xml", Files.readString(dir.resolve("bueno-signed.xml")).replace("ñandú", "otro"));
        escribir("sinfirma.xml", "<doc><item>1</item></doc>");
        escribir("roto.xml", "<doc>");

        Resultado r = lote(XML, lista("bueno-signed.xml", "alterado.xml", "sinfirma.xml", "roto.xml", "noesta.xml"),
                "-simple", "-log");

        assertEquals(1, r.exitCode(), r.salida());
        // El certificado de prueba no publica cómo consultar su revocación: la firma es válida, con la observación.
        assertEquals(List.of("VALIDO_CON_OBSERVACIONES/COMPROBACION_PENDIENTE", "NO_VALIDO/FIRMA_NO_VALIDA",
                "SIN_FIRMAS/SIN_FIRMAS", "ERROR/DOCUMENTO_MAL_FORMADO", "ERROR/ARCHIVO_NO_ENCONTRADO"), estados(r),
                r.salida());
        assertTrue(resumen(r).contains("\"con_avisos=1\";\"no_validos=1\";\"sin_firmas=1\";\"con_error=2\""),
                resumen(r));
        assertTrue(Files.readString(dir.resolve("alterado.xml.log")).contains("DOCUMENTO INVÁLIDO"));
        assertFalse(r.salida().contains("RESULTADO FINAL"), r.salida());

        Resultado bien = lote(XML, lista("bueno-signed.xml", "bueno-signed.xml"));
        assertEquals(0, bien.exitCode(), bien.salida());

        Resultado revisar = lote(XML, lista("bueno-signed.xml"), "-revisar");
        assertTrue(resumen(revisar).endsWith("\"corte=ARGUMENTOS\""), revisar.salida());
    }

    @Test
    void validaUnLoteContraElEsquema() throws Exception {
        escribir("esquema.xsd", ESQUEMA);
        escribir("bueno.xml", "<doc>\n  <item>1</item>\n</doc>\n");
        firmarXml("bueno.xml");
        escribir("sinfirma.xml", "<doc><item>1</item></doc>");
        escribir("otraforma.xml", "<otra/>");
        String xsd = dir.resolve("esquema.xsd").toString();

        Resultado r = lote(XSD, lista("bueno-signed.xml", "sinfirma.xml", "otraforma.xml"), xsd, "-log");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("VALIDO/OK", "SIN_FIRMAS/SIN_FIRMAS", "NO_VALIDO/ESTRUCTURA_NO_VALIDA"), estados(r),
                r.salida());
        assertTrue(Files.readString(dir.resolve("otraforma.xml.log")).contains("Error"), "El detalle va al registro");

        assertEquals(0, lote(XSD, lista("bueno-signed.xml"), xsd).exitCode());
        Resultado sinReferencia = lote(XSD, lista("bueno-signed.xml"));
        assertEquals(List.of("ERROR/XSD_NO_REFERENCIADO"), estados(sinReferencia), sinReferencia.salida());
        Resultado sinEsquema = lote(XSD, lista("bueno-signed.xml"), dir.resolve("noesta.xsd").toString());
        assertTrue(resumen(sinEsquema).endsWith("\"corte=XSD\""), sinEsquema.salida());
        escribir("roto.xsd", "no es un esquema");
        Resultado esquemaRoto = lote(XSD, lista("bueno-signed.xml"), dir.resolve("roto.xsd").toString());
        assertEquals(List.of("ERROR/XSD_NO_VALIDO"), estados(esquemaRoto), esquemaRoto.salida());
    }

    @Test
    void verificaUnLoteDePdf() throws Exception {
        Path p = dir.resolve("a.pdf");
        Files.writeString(p, PdfMinimo.contenido(), StandardCharsets.US_ASCII);
        Resultado firma = ejecutarModulo(dir, FIRMA_PDF, "-i", p.toString(), "-c",
                dir.resolve("prueba.p12").toString(), "-p", clave);
        assertEquals(0, firma.exitCode(), firma.salida());
        escribir("roto.pdf", "esto no es un PDF");

        Resultado r = lote(PDF, lista("a-signed.pdf", "a.pdf", "roto.pdf"), "-log");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("VALIDO_CON_OBSERVACIONES/COMPROBACION_PENDIENTE", "SIN_FIRMAS/SIN_FIRMAS",
                "ERROR/DOCUMENTO_MAL_FORMADO"), estados(r), r.salida());
        assertTrue(Files.readString(dir.resolve("a-signed.pdf.log")).contains("Verificando firma #1"));
        assertFalse(r.salida().contains("Verificando firma"), r.salida());
        assertEquals(0, lote(PDF, lista("a-signed.pdf"), "-simple").exitCode());
    }
}
