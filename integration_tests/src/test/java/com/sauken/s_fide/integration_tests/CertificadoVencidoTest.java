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

package com.sauken.s_fide.integration_tests;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Un certificado vencido, o que todavía no empezó a valer, no puede firmar:
 * los firmadores deben negarse con un mensaje claro y sin dejar ningún
 * archivo firmado. Se prueba con los firmadores PKCS#12 (los de token y de
 * Windows aplican la misma comprobación, con la misma clase duplicada, pero
 * necesitan hardware para una prueba de punta a punta).
 */
class CertificadoVencidoTest {

    private static final String XML_FIRMADOR = "com.sauken.s_fide.xml_signer_pkcs12.XMLSignerPKCS12";
    private static final String PDF_FIRMADOR = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";

    @TempDir
    Path tempDir;

    @Test
    void certificadoVencidoNoFirmaXmlNiPdf() throws Exception {
        verificarRechazo(generarP12("vencido.p12", "2020/01/01 00:00:00"), "venció el");
    }

    @Test
    void certificadoFuturoNoFirmaXmlNiPdf() throws Exception {
        verificarRechazo(generarP12("futuro.p12", "2099/01/01 00:00:00"), "todavía no es válido");
    }

    @Test
    void certificadoSoloParaCifrarNoFirma() throws Exception {
        verificarRechazo(generarP12("cifrado.p12", "2026/01/01 00:00:00", "-validity", "3650", "-ext", "KeyUsage=keyEncipherment"),
                "no está habilitado para firmar");
    }

    @Test
    void certificadoDeAutoridadCertificanteNoFirma() throws Exception {
        verificarRechazo(generarP12("ac.p12", "2026/01/01 00:00:00", "-validity", "3650", "-ext", "BasicConstraints:critical=ca:true"),
                "autoridad certificante");
    }

    @Test
    void certificadoConUsoDeFirmaSiFirmaYUnaClaveCortaAdvierte() throws Exception {
        Datos cert = generarP12("firma.p12", "2026/01/01 00:00:00", "-validity", "3650", "-ext", "KeyUsage=digitalSignature,nonRepudiation");
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, XML_FIRMADOR,
                cert.archivo().toString(), cert.password(), escribirXml().toString(), "");
        assertEquals(0, r.exitCode(), r.salida());
        assertFalse(r.salida().contains("ADVERTENCIA: la clave"), r.salida());

        Datos corta = generarP12("corta.p12", "2026/01/01 00:00:00", "-validity", "3650", "-keysize", "1024");
        ProcesoSupport.Resultado r2 = ProcesoSupport.ejecutarModulo(tempDir, XML_FIRMADOR,
                corta.archivo().toString(), corta.password(), escribirXml().toString(), "");
        assertEquals(0, r2.exitCode(), r2.salida());
        assertTrue(r2.salida().contains("RSA de 1024 bits"), r2.salida());
    }

    @Test
    void certificadoVigenteSiFirma() throws Exception {
        String password = ProcesoSupport.generarP12(tempDir, tempDir.resolve("vigente.p12"), "Vigente");
        Path xml = escribirXml();
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, XML_FIRMADOR,
                tempDir.resolve("vigente.p12").toString(), password, xml.toString(), "");
        assertEquals(0, r.exitCode(), r.salida());
        assertTrue(Files.exists(tempDir.resolve("documento-signed.xml")));
    }

    private void verificarRechazo(Datos cert, String fragmentoDelMensaje) throws Exception {
        Path xml = escribirXml();
        ProcesoSupport.Resultado rXml = ProcesoSupport.ejecutarModulo(tempDir, XML_FIRMADOR,
                cert.archivo().toString(), cert.password(), xml.toString(), "");
        assertEquals(1, rXml.exitCode(), rXml.salida());
        assertTrue(rXml.salida().contains(fragmentoDelMensaje), rXml.salida());
        assertFalse(Files.exists(tempDir.resolve("documento-signed.xml")), "No debe generarse un XML firmado");

        Path pdf = tempDir.resolve("documento.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            new Document(pdfDoc).add(new Paragraph("Documento de prueba"));
        }
        ProcesoSupport.Resultado rPdf = ProcesoSupport.ejecutarModulo(tempDir, PDF_FIRMADOR,
                "-i", pdf.toString(), "-c", cert.archivo().toString(), "-p", cert.password());
        assertEquals(1, rPdf.exitCode(), rPdf.salida());
        assertTrue(rPdf.salida().contains(fragmentoDelMensaje), rPdf.salida());
        assertFalse(Files.exists(tempDir.resolve("documento-signed.pdf")), "No debe generarse un PDF firmado");

        // La consulta previa que hace la interfaz gráfica antes de firmar también debe avisar.
        ProcesoSupport.Resultado rCheck = ProcesoSupport.ejecutarModulo(tempDir, XML_FIRMADOR,
                "-verificar-revocacion", cert.archivo().toString(), cert.password());
        assertEquals(1, rCheck.exitCode(), rCheck.salida());
        assertTrue(rCheck.salida().contains(fragmentoDelMensaje), rCheck.salida());
    }

    private Path escribirXml() throws IOException {
        Path xml = tempDir.resolve("documento.xml");
        Files.writeString(xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<documento><dato>x</dato></documento>\n",
                StandardCharsets.UTF_8);
        return xml;
    }

    private record Datos(Path archivo, String password) {
    }

    private Datos generarP12(String nombre, String inicio, String... extra) throws Exception {
        Path destino = tempDir.resolve(nombre);
        String password = "Pw" + Long.toHexString(System.nanoTime());
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        List<String> comando = new java.util.ArrayList<>(List.of(
                keytool.toString(), "-genkeypair", "-alias", "sfide-test", "-keyalg", "RSA",
                "-startdate", inicio, "-keystore", destino.toString(),
                "-storetype", "PKCS12", "-storepass", password, "-keypass", password,
                "-dname", "CN=Prueba de vigencia, O=Grupo Sauken S.A., C=AR"));
        comando.addAll(List.of(extra));
        if (!comando.contains("-validity")) {
            comando.addAll(List.of("-validity", "365"));
        }
        if (!comando.contains("-keysize")) {
            comando.addAll(List.of("-keysize", "2048"));
        }
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutar(comando, tempDir);
        if (r.exitCode() != 0) {
            fail("keytool no pudo generar el certificado de prueba: " + r.salida());
        }
        return new Datos(destino, password);
    }
}
