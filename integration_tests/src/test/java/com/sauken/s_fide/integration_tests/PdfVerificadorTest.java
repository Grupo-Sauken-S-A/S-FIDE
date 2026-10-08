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

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * El verificador que usa la organización destinataria debe dar el mismo veredicto que el análisis
 * previo a firmar, con mensajes comprensibles: lo benigno no alarma, lo roto sí.
 */
class PdfVerificadorTest {

    private static final String FIRMADOR = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";
    private static final String VERIFICADOR = "com.sauken.s_fide.pdf_verify_signatures.PDFVerifySignatures";

    @TempDir
    Path tempDir;

    private Path firmar(Path entrada, String cn, boolean cerrar) throws Exception {
        Path p12 = tempDir.resolve(cn.replace(' ', '_') + ".p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, cn);
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", entrada.toString(), "-c", p12.toString(), "-p", pass, "-l", String.valueOf(cerrar),
                "-x", cerrar ? "310" : "40", "-y", "55", "-t", "Firma de " + cn);
        assertEquals(0, r.exitCode(), r.salida());
        return entrada.resolveSibling(entrada.getFileName().toString().replace(".pdf", "-signed.pdf"));
    }

    private Path pdfBase() throws Exception {
        Path pdf = tempDir.resolve("doc.pdf");
        try (PdfDocument d = new PdfDocument(new PdfWriter(pdf.toString()))) {
            new Document(d).add(new Paragraph("Documento para el verificador"));
        }
        return pdf;
    }

    private ProcesoSupport.Resultado verificar(Path pdf) throws Exception {
        return ProcesoSupport.ejecutarModulo(tempDir, VERIFICADOR, pdf.toString(), "-simple");
    }

    private interface Cambio {
        void aplicar(PdfDocument documento) throws Exception;
    }

    private Path modificarDespues(Path firmado, String nombre, Cambio cambio) throws Exception {
        Path out = tempDir.resolve(nombre);
        try (InputStream in = Files.newInputStream(firmado);
             OutputStream os = Files.newOutputStream(out);
             PdfDocument d = new PdfDocument(new PdfReader(in), new PdfWriter(os),
                     new StampingProperties().useAppendMode())) {
            cambio.aplicar(d);
        }
        return out;
    }

    @Test
    void unaCadenaCerradaSeVerificaComoValidaYBloqueada() throws Exception {
        Path cerrado = firmar(firmar(pdfBase(), "Persona Dos", false), "Persona Tres", true);

        ProcesoSupport.Resultado r = verificar(cerrado);
        assertEquals(0, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("DOCUMENTO VÁLIDO"), r.salida());
        assertTrue(r.salida().contains("Documento bloqueado: Sí"), r.salida());
        assertTrue(r.salida().contains("bloqueado por Persona Tres"), r.salida());
        assertFalse(r.salida().contains("Cubre todo el documento"),
                "Esa línea confundía: en una cadena es normal que las firmas intermedias no cubran todo");
        assertFalse(r.salida().contains("Exception"), r.salida());
    }

    @Test
    void unDocumentoAlteradoSeInformaComoInvalido() throws Exception {
        Path firmado = firmar(pdfBase(), "Persona Dos", false);
        byte[] bytes = Files.readAllBytes(firmado);
        int pos = new String(bytes, StandardCharsets.ISO_8859_1).indexOf("/Type");
        bytes[pos + 1] = (byte) 't';
        Path alterado = tempDir.resolve("alterado.pdf");
        Files.write(alterado, bytes);

        ProcesoSupport.Resultado r = verificar(alterado);
        assertEquals(1, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("DOCUMENTO INVÁLIDO"), r.salida());
        assertTrue(r.salida().contains("NO es válida"), r.salida());
    }

    @Test
    void cambiarElContenidoDespuesDeLaUltimaFirmaEsInvalido() throws Exception {
        Path firmado = firmar(pdfBase(), "Persona Dos", false);
        Path alterado = modificarDespues(firmado, "contenido.pdf", d ->
                new PdfCanvas(d.getFirstPage()).rectangle(50, 500, 200, 100).fill());

        ProcesoSupport.Resultado r = verificar(alterado);
        assertEquals(1, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("modificado después de la última firma"), r.salida());
    }

    @Test
    void agregarInformacionDeValidacionNoInvalidaNiAvisa() throws Exception {
        Path firmado = firmar(pdfBase(), "Persona Dos", false);
        Path conDss = modificarDespues(firmado, "dss.pdf", d -> {
            d.getCatalog().put(PdfName.DSS, new PdfDictionary());
            d.getCatalog().setModified();
        });

        ProcesoSupport.Resultado r = verificar(conDss);
        assertEquals(0, r.exitCode(), r.salida());
        assertFalse(r.salida().contains("Aviso:"), r.salida());
    }

    @Test
    void unPdfSinFirmasSeRechazaConUnMensajeClaro() throws Exception {
        ProcesoSupport.Resultado r = verificar(pdfBase());
        assertEquals(1, r.exitCode());
        assertTrue(r.salida().contains("no contiene firmas digitales"), r.salida());
        assertFalse(r.salida().contains("Exception"), r.salida());
    }

    /**
     * Con un PDF real firmado con Acrobat (si se indica su ruta en la variable de entorno
     * SFIDE_PDF_REAL): no se guarda en el repositorio porque es un documento real con datos personales.
     */
    @Test
    void unPdfRealFirmadoConAcrobatSeVerificaSinFalsasAlarmas() throws Exception {
        String ruta = System.getenv("SFIDE_PDF_REAL");
        assumeTrue(ruta != null && Files.isRegularFile(Path.of(ruta)), "Sin SFIDE_PDF_REAL no se prueba");

        ProcesoSupport.Resultado r = verificar(Path.of(ruta));
        assertEquals(0, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("DOCUMENTO VÁLIDO"), r.salida());
        assertFalse(r.salida().contains("NO es válida"), r.salida());
    }
}
