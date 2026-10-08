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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujo real de S-FiDE para PDF: varias personas, en computadoras distintas,
 * firman en cadena el mismo documento y la última lo cierra. Cada firma es una
 * ejecución separada del firmador, y la salida de una es la entrada de la
 * siguiente, igual que cuando el archivo viaja por correo.
 */
class PdfCadenaDeFirmasTest {

    private static final String FIRMADOR = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";
    private static final String VERIFICADOR = "com.sauken.s_fide.pdf_verify_signatures.PDFVerifySignatures";

    @TempDir
    Path tempDir;

    private Path crearPdfBase() throws Exception {
        Path pdf = tempDir.resolve("documento.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            new Document(pdfDoc).add(new Paragraph("Documento de prueba para la cadena de firmas"));
        }
        return pdf;
    }

    private ProcesoSupport.Resultado intentarFirma(Path entrada, String cn, boolean cerrar, float x, float y)
            throws Exception {
        Path p12 = tempDir.resolve(cn.replace(' ', '_') + ".p12");
        String password = ProcesoSupport.generarP12(tempDir, p12, cn);
        return ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", entrada.toString(), "-c", p12.toString(), "-p", password,
                "-l", String.valueOf(cerrar), "-x", String.valueOf(x), "-y", String.valueOf(y),
                "-t", "Firma de " + cn);
    }

    private Path firmar(Path entrada, String cn, boolean cerrar, float x, float y) throws Exception {
        ProcesoSupport.Resultado r = intentarFirma(entrada, cn, cerrar, x, y);
        assertEquals(0, r.exitCode(), "La firma de " + cn + " falló:\n" + r.salida());
        Path salida = entrada.resolveSibling(
                entrada.getFileName().toString().replace(".pdf", "-signed.pdf"));
        assertTrue(Files.exists(salida), "No se generó la salida de " + cn);
        return salida;
    }

    private void assertDocumentoValido(Path pdf, String contexto) throws Exception {
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, VERIFICADOR, pdf.toString());
        assertEquals(0, r.exitCode(), contexto + ": el verificador no dio válido el PDF:\n" + r.salida());
    }

    @Test
    void dosFirmantesSinBloquearDejanTodasLasFirmasValidas() throws Exception {
        Path pdf1 = firmar(crearPdfBase(), "Persona Dos", false, 40, 55);
        assertDocumentoValido(pdf1, "tras la 1.ª firma");
        Path pdf2 = firmar(pdf1, "Persona Tres", false, 310, 55);
        assertDocumentoValido(pdf2, "tras la 2.ª firma");
    }

    @Test
    void ultimaFirmaConBloqueoNoInvalidaLasAnteriores() throws Exception {
        Path pdf1 = firmar(crearPdfBase(), "Persona Dos", false, 40, 55);
        Path pdf2 = firmar(pdf1, "Persona Tres", true, 310, 55);
        assertDocumentoValido(pdf2, "tras la firma de cierre");
    }

    @Test
    void documentoCerradoPorLaFirmaFinalNoAdmiteMasFirmas() throws Exception {
        Path pdf1 = firmar(crearPdfBase(), "Persona Dos", false, 40, 55);
        Path cerrado = firmar(pdf1, "Persona Tres", true, 310, 55);

        ProcesoSupport.Resultado r = intentarFirma(cerrado, "Persona Cuatro", false, 40, 150);
        assertEquals(1, r.exitCode(), "Un documento cerrado no debería poder firmarse:\n" + r.salida());
        assertTrue(r.salida().contains("no admite más firmas"), "Mensaje inesperado:\n" + r.salida());
        assertTrue(!r.salida().contains("Exception"), "No debe exponerse texto técnico:\n" + r.salida());
    }

    @Test
    void elDocumentoSinFirmasEstaAbiertoYElInformeListaLasFirmasPrevias() throws Exception {
        Path pdf1 = firmar(crearPdfBase(), "Persona Dos", false, 40, 55);

        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-analizar-documento", pdf1.toString());
        assertEquals(0, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("ESTADO_DOCUMENTO: ABIERTO"), r.salida());
        assertTrue(r.salida().contains("CANTIDAD_FIRMAS: 1"), r.salida());
        // El certificado de prueba es autofirmado y no publica dónde consultar su estado: la firma
        // está íntegra, pero su vigencia no es comprobable (aviso suave, nunca una alerta).
        assertTrue(r.salida().contains("FIRMA: 1 | NO_VERIFICABLE | Persona Dos"), r.salida());
        assertTrue(r.salida().contains("el certificado no indica dónde consultar su estado"), r.salida());
        assertTrue(!r.salida().contains("NO es válida"), r.salida());
    }

    @Test
    void unDocumentoModificadoDespuesDeFirmarSeInformaPeroSePuedeFirmar() throws Exception {
        Path pdf1 = firmar(crearPdfBase(), "Persona Dos", false, 40, 55);

        // Se altera un byte del contenido ya firmado, como si alguien editara el archivo.
        byte[] bytes = Files.readAllBytes(pdf1);
        String texto = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        int pos = texto.indexOf("/Type");
        bytes[pos + 1] = (byte) 't';
        Path alterado = tempDir.resolve("alterado.pdf");
        Files.write(alterado, bytes);

        ProcesoSupport.Resultado informe = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-analizar-documento", alterado.toString());
        assertTrue(informe.salida().contains("INVALIDA") || informe.exitCode() == 1,
                "Una firma sobre un documento alterado debe informarse como inválida:\n" + informe.salida());

        // Informar y seguir: la persona decide; el programa no le impide firmar.
        ProcesoSupport.Resultado firma = intentarFirma(alterado, "Persona Tres", false, 310, 55);
        assertEquals(0, firma.exitCode(), "Con una firma previa inválida se debe informar y seguir:\n" + firma.salida());
        assertTrue(firma.salida().contains("NO es válida"), firma.salida());
    }

    @Test
    void elCierreDejaElMismoBloqueoQueAplicaAcrobat() throws Exception {
        Path pdf1 = firmar(crearPdfBase(), "Persona Dos", false, 40, 55);
        Path cerrado = firmar(pdf1, "Persona Tres", true, 310, 55);

        ProcesoSupport.Resultado informe = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-analizar-documento", cerrado.toString());
        assertTrue(informe.salida().contains("ESTADO_DOCUMENTO: CERRADO"), informe.salida());
        assertTrue(informe.salida().contains("bloqueado por Persona Tres"),
                "Debe reconocerse el bloqueo de campo que Acrobat también entiende:\n" + informe.salida());
    }
}
