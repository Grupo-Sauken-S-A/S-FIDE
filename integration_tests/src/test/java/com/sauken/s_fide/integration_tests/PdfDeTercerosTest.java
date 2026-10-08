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
import com.itextpdf.forms.fields.SignatureFormFieldBuilder;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfTextAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer.Analisis;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer.Estado;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer.Nivel;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer.Veredicto;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El análisis previo a firmar debe entender PDF firmados por aplicaciones de terceros (Acrobat y
 * firmadores PAdES), no solo los que genera S-FiDE. Cada prueba arma con iText una variante típica
 * y comprueba que no haya falsas alarmas: solo una firma realmente rota o un contenido alterado
 * se presentan como alerta firme (ver la política de mensajes en {@link PdfDocumentAnalyzer}).
 */
class PdfDeTercerosTest {

    private static final String FIRMADOR = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";

    @TempDir
    Path tempDir;

    private record Credencial(PrivateKey clave, Certificate[] cadena) {
    }

    private Credencial credencial(String cn, String algoritmo, String extra) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Path p12 = tempDir.resolve(cn.replace(' ', '_') + ".p12");
        String pass = "Clave12345678";
        List<String> cmd = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "k", "-keyalg", algoritmo, "-keystore", p12.toString(),
                "-storetype", "PKCS12", "-storepass", pass, "-keypass", pass, "-validity", "30",
                "-dname", "CN=" + cn + ", O=Tercero, C=AR"));
        cmd.addAll(List.of(extra.isEmpty() ? new String[0] : extra.split(" ")));
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutar(cmd, tempDir);
        assertEquals(0, r.exitCode(), r.salida());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) {
            ks.load(in, pass.toCharArray());
        }
        return new Credencial((PrivateKey) ks.getKey("k", pass.toCharArray()), ks.getCertificateChain("k"));
    }

    private Path base(String nombre) throws Exception {
        Path pdf = tempDir.resolve(nombre);
        try (PdfDocument d = new PdfDocument(new PdfWriter(pdf.toString()))) {
            new Document(d).add(new Paragraph("Contrato de prueba"));
        }
        return pdf;
    }

    private Path firmar(Path entrada, String salida, Credencial c, PdfSigner.CryptoStandard estandar,
                        Integer certificacion) throws Exception {
        Path out = tempDir.resolve(salida);
        try (InputStream in = Files.newInputStream(entrada); OutputStream os = Files.newOutputStream(out)) {
            PdfSigner signer = new PdfSigner(new PdfReader(in), os, new StampingProperties().useAppendMode());
            if (certificacion != null) signer.setCertificationLevel(certificacion);
            IExternalSignature pks = new PrivateKeySignature(c.clave(), "SHA256", BouncyCastleProvider.PROVIDER_NAME);
            signer.signDetached(new BouncyCastleDigest(), pks, c.cadena(), null, null, null, 0, estandar);
        }
        return out;
    }

    private interface Modificacion {
        void aplicar(PdfDocument documento) throws Exception;
    }

    /** Actualización incremental posterior a la firma, como la que hace Acrobat al guardar. */
    private Path modificarDespues(Path firmado, String salida, Modificacion modificacion) throws Exception {
        Path out = tempDir.resolve(salida);
        try (InputStream in = Files.newInputStream(firmado);
             OutputStream os = Files.newOutputStream(out);
             PdfDocument d = new PdfDocument(new PdfReader(in), new PdfWriter(os),
                     new StampingProperties().useAppendMode())) {
            modificacion.aplicar(d);
        }
        return out;
    }

    private Analisis analizar(Path pdf) throws Exception {
        return PdfDocumentAnalyzer.analizar(pdf, false);
    }

    @Test
    void firmasPadesYEcdsaDeTercerosSeLeenSinProblemas() throws Exception {
        Path pades = firmar(base("a.pdf"), "a-pades.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CADES, null);
        Analisis a = analizar(pades);
        assertEquals(Estado.ABIERTO, a.estado());
        assertEquals(Veredicto.VALIDA, a.firmas().get(0).veredicto());
        assertFalse(a.hayProblemas());

        Path ecdsa = firmar(base("b.pdf"), "b-ecdsa.pdf", credencial("Tercero EC", "EC", "-groupname secp256r1"),
                PdfSigner.CryptoStandard.CMS, null);
        assertFalse(analizar(ecdsa).hayProblemas());
    }

    @Test
    void agregarInformacionDeValidacionDespuesDeFirmarNoDaAlarma() throws Exception {
        Path firmado = firmar(base("c0.pdf"), "c0-s.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CMS, null);
        Path conDss = modificarDespues(firmado, "c-dss.pdf", d -> {
            d.getCatalog().put(PdfName.DSS, new PdfDictionary());
            d.getCatalog().setModified();
        });

        Analisis a = analizar(conDss);
        assertEquals(Nivel.NINGUNO, a.cambios().nivel(), "La información de validación (LTV) es rutina de Acrobat");
        assertFalse(a.hayProblemas());
    }

    @Test
    void unComentarioPosteriorEsSoloInformativo() throws Exception {
        Path firmado = firmar(base("d0.pdf"), "d0-s.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CMS, null);
        Path conComentario = modificarDespues(firmado, "d-comentario.pdf", d ->
                d.getFirstPage().addAnnotation(new PdfTextAnnotation(new Rectangle(300, 700, 20, 20))
                        .setContents("Un comentario")));

        Analisis a = analizar(conComentario);
        assertEquals(Nivel.INFORMATIVO, a.cambios().nivel());
        assertTrue(a.cambios().mensaje().contains("no invalida la firma"), a.cambios().mensaje());
        assertFalse(a.hayProblemas());
    }

    @Test
    void cambiarElContenidoDeLaPaginaDespuesDeFirmarSiEsUnaAlertaFirme() throws Exception {
        Path firmado = firmar(base("g0.pdf"), "g0-s.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CMS, null);
        Path alterado = modificarDespues(firmado, "g-contenido.pdf", d -> {
            PdfCanvas canvas = new PdfCanvas(d.getFirstPage());
            canvas.rectangle(50, 500, 200, 100).fill();
        });

        Analisis a = analizar(alterado);
        assertEquals(Nivel.ALERTA, a.cambios().nivel());
        assertTrue(a.hayProblemas());
        assertTrue(a.cambios().mensaje().contains("Tercero RSA"), a.cambios().mensaje());
    }

    @Test
    void unCertificadoDeNivel2PermiteSeguirFirmando() throws Exception {
        Path nivel2 = firmar(base("f.pdf"), "f-cert2.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CMS, PdfSigner.CERTIFIED_FORM_FILLING);
        assertEquals(Estado.ABIERTO, analizar(nivel2).estado());

        Path p12 = tempDir.resolve("sfide.p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, "Firmante SFide");
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", nivel2.toString(), "-c", p12.toString(), "-p", pass, "-x", "40", "-y", "55");
        assertEquals(0, r.exitCode(), r.salida());
    }

    private TsaLocal sellador() throws Exception {
        Credencial tsa = credencial("Servicio de Sello de Tiempo", "RSA",
                "-keysize 2048 -ext ExtendedKeyUsage:critical=timeStamping");
        return new TsaLocal(tsa.clave(), (java.security.cert.X509Certificate) tsa.cadena()[0]);
    }

    @Test
    void unSelloDeTiempoDelDocumentoNoSeCuentaComoFirmaDePersona() throws Exception {
        Path firmado = firmar(base("s0.pdf"), "s0-s.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CADES, null);
        Path conSello = tempDir.resolve("s-sello.pdf");
        try (InputStream in = Files.newInputStream(firmado); OutputStream os = Files.newOutputStream(conSello)) {
            new PdfSigner(new PdfReader(in), os, new StampingProperties().useAppendMode())
                    .timestamp(sellador(), null);
        }

        Analisis a = analizar(conSello);
        assertEquals(2, a.firmas().size(), "La firma y el sello");
        assertTrue(a.firmas().get(1).esSelloDeTiempo());
        assertEquals(Veredicto.VALIDA, a.firmas().get(1).veredicto());
        assertEquals(Nivel.NINGUNO, a.cambios().nivel());
        assertFalse(a.hayProblemas());

        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        PdfDocumentAnalyzer.imprimirInforme(a, new java.io.PrintStream(bytes, true, "UTF-8"));
        String informe = bytes.toString("UTF-8");
        assertTrue(informe.contains("CANTIDAD_FIRMAS: 1"), informe);
        assertTrue(informe.contains("SELLO: 2 | VALIDA"), informe);
    }

    @Test
    void unSelloDeTiempoPosteriorAlCierreNoLoReabre() throws Exception {
        Path p12 = tempDir.resolve("sfide3.p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, "Firmante SFide");
        Path pdf = base("z.pdf");
        ProcesoSupport.Resultado r1 = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", pdf.toString(), "-c", p12.toString(), "-p", pass, "-x", "40", "-y", "55");
        assertEquals(0, r1.exitCode(), r1.salida());
        Path uno = tempDir.resolve("z-signed.pdf");
        Path p12b = tempDir.resolve("sfide4.p12");
        String passB = ProcesoSupport.generarP12(tempDir, p12b, "Otro Firmante");
        ProcesoSupport.Resultado r2 = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", uno.toString(), "-c", p12b.toString(), "-p", passB, "-x", "310", "-y", "55", "-l", "true");
        assertEquals(0, r2.exitCode(), r2.salida());
        Path cerrado = tempDir.resolve("z-signed-signed.pdf");

        Path sellado = tempDir.resolve("z-sellado.pdf");
        try (InputStream in = Files.newInputStream(cerrado); OutputStream os = Files.newOutputStream(sellado)) {
            new PdfSigner(new PdfReader(in), os, new StampingProperties().useAppendMode())
                    .timestamp(sellador(), null);
        }

        Analisis a = analizar(sellado);
        assertEquals(Estado.CERRADO, a.estado(),
                "Un sello de tiempo agregado después del cierre no debe hacer que el documento parezca abierto");
    }

    @Test
    void unCertificadoNivel1EsUnDocumentoCerrado() throws Exception {
        Path nivel1 = firmar(base("n1.pdf"), "n1-cert1.pdf", credencial("Tercero RSA", "RSA", "-keysize 2048"),
                PdfSigner.CryptoStandard.CMS, PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);
        Analisis a = analizar(nivel1);
        assertEquals(Estado.CERRADO, a.estado());
        assertTrue(a.motivoCierre().contains("no admite más firmas"), a.motivoCierre());
    }

    @Test
    void unCampoDeFirmaVacioNoCuentaComoFirma() throws Exception {
        Path vacio = tempDir.resolve("e-campo-vacio.pdf");
        try (PdfDocument d = new PdfDocument(new PdfWriter(vacio.toString()))) {
            new Document(d).add(new Paragraph("Formulario con campo de firma"));
            PdfAcroForm.getAcroForm(d, true).addField(
                    new SignatureFormFieldBuilder(d, "FirmaPendiente").setPage(1)
                            .setWidgetRectangle(new Rectangle(50, 100, 150, 50)).createSignature());
        }
        Analisis a = analizar(vacio);
        assertEquals(Estado.ABIERTO, a.estado());
        assertTrue(a.firmas().isEmpty());
        assertEquals(1, a.camposVacios().size());
        assertEquals("FirmaPendiente", a.camposVacios().get(0).nombre());
        assertEquals(1, a.camposVacios().get(0).pagina());

        // Firmar dentro del campo existente, como hace Acrobat: no se crea un campo nuevo al lado.
        Path p12 = tempDir.resolve("sfide-campo.p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, "Firmante SFide");
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", vacio.toString(), "-c", p12.toString(), "-p", pass, "-campo", "FirmaPendiente",
                "-t", "Aprobado");
        assertEquals(0, r.exitCode(), r.salida());

        Analisis despues = analizar(tempDir.resolve("e-campo-vacio-signed.pdf"));
        assertEquals(1, despues.firmas().size());
        assertEquals("FirmaPendiente", despues.firmas().get(0).campo(),
                "La firma debe quedar en el campo que el autor preparó");
        assertTrue(despues.camposVacios().isEmpty(), "No debe quedar ningún campo vacío ni crearse uno nuevo");

        ProcesoSupport.Resultado inexistente = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", vacio.toString(), "-c", p12.toString(), "-p", pass, "-campo", "NoExiste");
        assertEquals(1, inexistente.exitCode());
        assertTrue(inexistente.salida().contains("Campos de firma disponibles: FirmaPendiente"),
                inexistente.salida());
    }

    @Test
    void unDocumentoCifradoConRestriccionesSeRechazaConUnMensajeClaro() throws Exception {
        Path cifrado = tempDir.resolve("g-cifrado.pdf");
        try (PdfDocument d = new PdfDocument(new PdfWriter(cifrado.toString(),
                new com.itextpdf.kernel.pdf.WriterProperties().setStandardEncryption(null,
                        "propietario".getBytes(), com.itextpdf.kernel.pdf.EncryptionConstants.ALLOW_PRINTING,
                        com.itextpdf.kernel.pdf.EncryptionConstants.ENCRYPTION_AES_256)))) {
            new Document(d).add(new Paragraph("Documento cifrado con restricciones"));
        }
        Path p12 = tempDir.resolve("sfide2.p12");
        String pass = ProcesoSupport.generarP12(tempDir, p12, "Firmante SFide");
        ProcesoSupport.Resultado r = ProcesoSupport.ejecutarModulo(tempDir, FIRMADOR,
                "-i", cifrado.toString(), "-c", p12.toString(), "-p", pass, "-x", "40", "-y", "55");
        assertEquals(1, r.exitCode());
        assertFalse(r.salida().contains("Exception"), r.salida());
        assertTrue(r.salida().contains("restricciones de seguridad"), r.salida());
    }
}
