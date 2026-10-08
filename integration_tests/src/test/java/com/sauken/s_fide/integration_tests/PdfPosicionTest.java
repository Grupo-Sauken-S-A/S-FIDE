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

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.signatures.SignatureUtil;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dónde y con qué tamaño queda la firma visible, y la protección del contenido para la primera firma.
 * Los controles de este módulo son los que garantizan que lo que la persona marcó en pantalla sea
 * exactamente lo que queda en el documento.
 */
class PdfPosicionTest {

    private static final String FIRMADOR = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";

    @TempDir
    Path tempDir;

    private Path pdfDeDosPaginas() throws Exception {
        Path pdf = tempDir.resolve("dos.pdf");
        try (PdfDocument d = new PdfDocument(new PdfWriter(pdf.toString()))) {
            Document doc = new Document(d);
            doc.add(new Paragraph("Primera página"));
            doc.add(new AreaBreak());
            doc.add(new Paragraph("Segunda página"));
        }
        return pdf;
    }

    private ProcesoSupport.Resultado firmar(Path pdf, String cn, String... extra) throws Exception {
        Path p12 = tempDir.resolve(cn.replace(' ', '_') + "-" + System.nanoTime() + ".p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, cn);
        java.util.List<String> args = new java.util.ArrayList<>(List.of(
                "-i", pdf.toString(), "-c", p12.toString(), "-p", pass));
        args.addAll(List.of(extra));
        return ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR, args.toArray(new String[0]));
    }

    /** Página (1..n) y rectángulo del único campo de firma del documento. */
    private record Ubicacion(int pagina, Rectangle rectangulo) {
    }

    private Ubicacion ubicacionDeLaFirma(Path pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf.toString()))) {
            String nombre = new SignatureUtil(doc).getSignatureNames().get(0);
            PdfFormField campo = PdfAcroForm.getAcroForm(doc, false).getField(nombre);
            var widget = campo.getWidgets().get(0);
            return new Ubicacion(doc.getPageNumber(widget.getPage()), widget.getRectangle().toRectangle());
        }
    }

    @Test
    void laFirmaQuedaEnLaPaginaIndicada() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Firmante Uno",
                "-x", "50", "-y", "100", "-pagina", "2");
        assertEquals(0, r.exitCode(), r.salida());

        Ubicacion u = ubicacionDeLaFirma(tempDir.resolve("dos-signed.pdf"));
        assertEquals(2, u.pagina());
        assertEquals(50f, u.rectangulo().getX(), 0.01);
        assertEquals(100f, u.rectangulo().getY(), 0.01);
    }

    @Test
    void sinIndicarPaginaSigueSiendoLaPrimera() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Firmante Uno", "-x", "40", "-y", "55");
        assertEquals(0, r.exitCode(), r.salida());
        assertEquals(1, ubicacionDeLaFirma(tempDir.resolve("dos-signed.pdf")).pagina());
    }

    @Test
    void elTamanoDelRecuadroSeRespeta() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Firmante Uno",
                "-x", "40", "-y", "55", "-ancho", "220", "-alto", "95");
        assertEquals(0, r.exitCode(), r.salida());

        Rectangle rect = ubicacionDeLaFirma(tempDir.resolve("dos-signed.pdf")).rectangulo();
        assertEquals(220f, rect.getWidth(), 0.01);
        assertEquals(95f, rect.getHeight(), 0.01);
    }

    @Test
    void unaPaginaInexistenteSeRechazaConUnMensajeClaro() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Firmante Uno",
                "-x", "40", "-y", "55", "-pagina", "5");
        assertEquals(1, r.exitCode());
        assertTrue(r.salida().contains("El documento tiene 2 páginas y la página 5 no existe."), r.salida());
        assertFalse(r.salida().contains("Exception"), r.salida());
    }

    @Test
    void unRecuadroQueSeSaleDeLaHojaSeRechaza() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Firmante Uno",
                "-x", "500", "-y", "55", "-ancho", "160");
        assertEquals(1, r.exitCode());
        assertTrue(r.salida().contains("no entra en la página 1"), r.salida());
    }

    @Test
    void lasMedidasInvalidasSeRechazan() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Firmante Uno",
                "-x", "40", "-y", "55", "-ancho", "0");
        assertEquals(1, r.exitCode());
        assertTrue(r.salida().contains("mayores que cero"), r.salida());
    }

    @Test
    void laVistaPreviaDelTextoEsElTextoRealDeLaFirma() throws Exception {
        Path p12 = tempDir.resolve("preview.p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, "Ana Perez");
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-vista-previa-texto", "-c", p12.toString(), "-p", pass, "-t", "Certificado de Origen");

        assertEquals(0, r.exitCode(), r.salida());
        String salida = r.salida();
        assertTrue(salida.contains("TEXTO_FIRMA_INICIO"), salida);
        assertTrue(salida.contains("Certificado de Origen"), salida);
        assertTrue(salida.contains("Firmado digitalmente por:"), salida);
        assertTrue(salida.contains("Ana Perez"), "Debe mostrar el nombre real del certificado:\n" + salida);
        assertTrue(salida.contains("TAMANO_LETRA: 8.0"), salida);
    }

    @Test
    void protegerElContenidoDejaSeguirFirmandoYDetectaCambios() throws Exception {
        Path pdf = pdfDeDosPaginas();
        ProcesoSupport.Resultado primera = firmar(pdf, "Autor", "-x", "40", "-y", "55", "-proteger-contenido", "true");
        assertEquals(0, primera.exitCode(), primera.salida());
        Path protegido = tempDir.resolve("dos-signed.pdf");

        PdfDocumentAnalyzer.Analisis a = PdfDocumentAnalyzer.analizar(protegido, false);
        assertEquals(PdfDocumentAnalyzer.Estado.ABIERTO, a.estado(), "Debe admitir más firmas");
        assertTrue(a.contenidoProtegido(), "El contenido debe figurar protegido");

        ProcesoSupport.Resultado segunda = firmar(protegido, "Segundo Firmante", "-x", "310", "-y", "55");
        assertEquals(0, segunda.exitCode(), segunda.salida());

        ProcesoSupport.Resultado verificacion = ProcesoSupport.ejecutarModulo(tempDir,
                "com.sauken.s_fide.pdf_verify_signatures.PDFVerifySignatures",
                tempDir.resolve("dos-signed-signed.pdf").toString(), "-simple");
        assertEquals(0, verificacion.exitCode(), verificacion.salida());
        assertTrue(verificacion.salida().contains("Contenido protegido contra cambios (admite más firmas): Sí"),
                verificacion.salida());
        assertTrue(verificacion.salida().contains("Documento bloqueado: No"), verificacion.salida());
    }

    @Test
    void protegerElContenidoSoloVaConLaPrimeraFirma() throws Exception {
        ProcesoSupport.Resultado primera = firmar(pdfDeDosPaginas(), "Autor", "-x", "40", "-y", "55");
        assertEquals(0, primera.exitCode(), primera.salida());

        ProcesoSupport.Resultado segunda = firmar(tempDir.resolve("dos-signed.pdf"), "Otro", "-x", "310", "-y", "55",
                "-proteger-contenido", "true");
        assertEquals(1, segunda.exitCode());
        assertTrue(segunda.salida().contains("solo puede aplicarse con la primera firma"), segunda.salida());
    }

    @Test
    void protegerYBloquearAlMismoTiempoSeRechaza() throws Exception {
        ProcesoSupport.Resultado r = firmar(pdfDeDosPaginas(), "Autor", "-x", "40", "-y", "55",
                "-proteger-contenido", "true", "-l", "true");
        assertEquals(1, r.exitCode());
        assertTrue(r.salida().contains("Elija una sola opción"), r.salida());
    }
}
