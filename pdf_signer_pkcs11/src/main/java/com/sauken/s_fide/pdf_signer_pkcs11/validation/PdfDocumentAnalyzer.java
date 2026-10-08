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


package com.sauken.s_fide.pdf_signer_pkcs11.validation;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.exceptions.BadPasswordException;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDate;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;

/**
 * Analiza un PDF antes de agregarle una firma: si todavía admite firmas y en qué
 * estado están las que ya tiene. Es la base de las reglas de firma en cadena
 * (varias personas, en computadoras distintas, firmando el mismo documento).
 * <p>
 * Los mensajes están pensados para personas sin conocimientos técnicos y siguen
 * tres niveles:
 * <ul>
 *   <li><b>Válida</b>: todo en orden. Un certificado que venció o fue dado de baja
 *   <i>después</i> de firmar no cambia eso; se menciona solo como nota informativa.</li>
 *   <li><b>No verificable</b>: no se pudo comprobar algo (por ejemplo, sin Internet).
 *   Es un aviso suave, no una alarma.</li>
 *   <li><b>Inválida</b>: lo único que se presenta como alerta firme — el documento se
 *   modificó después de firmarse, la firma está dañada, o el certificado ya no era
 *   válido en la fecha de la firma.</li>
 * </ul>
 * No depende de ningún otro módulo en tiempo de ejecución: esta clase se duplica igual
 * en cada uno de los firmadores PDF.
 */
public final class PdfDocumentAnalyzer {

    /** Texto que deja la firma final en el campo "Motivo"; los firmadores de S-FiDE lo respetan. */
    public static final String MARCA_CIERRE = "Firma final: documento cerrado";

    private static final long TOLERANCIA_RELOJ_MS = 5 * 60 * 1000L;

    public enum Estado {ABIERTO, CERRADO}

    public enum Veredicto {VALIDA, NO_VERIFICABLE, INVALIDA}

    /** Resultado del análisis de una firma ya presente en el documento. */
    public record FirmaPrevia(int numero, String campo, String firmante, Date fecha, Veredicto veredicto,
                              String mensaje, List<String> notas, boolean integra, boolean esCierre,
                              boolean esSelloDeTiempo, String detalleRevocacion) {

        public FirmaPrevia(int numero, String campo, String firmante, Date fecha, Veredicto veredicto,
                           String mensaje, List<String> notas, boolean integra, boolean esCierre) {
            this(numero, campo, firmante, fecha, veredicto, mensaje, notas, integra, esCierre, false, null);
        }

        public FirmaPrevia(int numero, String campo, String firmante, Date fecha, Veredicto veredicto,
                           String mensaje, List<String> notas, boolean integra, boolean esCierre,
                           boolean esSelloDeTiempo) {
            this(numero, campo, firmante, fecha, veredicto, mensaje, notas, integra, esCierre, esSelloDeTiempo, null);
        }
    }

    private static final List<String> SUBFILTROS_CONOCIDOS = List.of("adbe.pkcs7.detached", "adbe.pkcs7.sha1",
            "adbe.x509.rsa_sha1", "ETSI.CAdES.detached", "ETSI.RFC3161");

    /** Resultado del análisis del documento completo. */
    public record Analisis(Estado estado, String motivoCierre, List<FirmaPrevia> firmas,
                           CambiosPosteriores cambios, List<CampoVacio> camposVacios,
                           List<PaginaInfo> paginas, boolean contenidoProtegido) {

        /** Solo las alertas firmes cuentan como problema; los avisos suaves y las notas no. */
        public boolean hayProblemas() {
            return cambios.nivel() == Nivel.ALERTA
                    || firmas.stream().anyMatch(f -> f.veredicto() == Veredicto.INVALIDA);
        }
    }

    /**
     * Área visible de una página en puntos PDF (1/72 de pulgada), con origen en la esquina inferior
     * izquierda de esa área, y su rotación. Es el sistema de coordenadas de {@code -x} y {@code -y}.
     */
    public record PaginaInfo(float x, float y, float ancho, float alto, int rotacion) {
    }

    /** Campo de firma que el documento ya trae preparado y todavía nadie firmó. */
    public record CampoVacio(String nombre, int pagina) {
    }

    public enum Nivel {NINGUNO, INFORMATIVO, AVISO, ALERTA}

    /** Qué pasó con el archivo después de la última firma, ya explicado para el usuario. */
    public record CambiosPosteriores(Nivel nivel, String mensaje) {
        static final CambiosPosteriores NINGUNO = new CambiosPosteriores(Nivel.NINGUNO, null);
    }

    /** El documento no se pudo abrir; el mensaje ya está en lenguaje simple. */
    public static final class DocumentoIlegibleException extends Exception {
        public DocumentoIlegibleException(String mensaje) {
            super(mensaje);
        }
    }

    private PdfDocumentAnalyzer() {
    }

    /**
     * @param consultarRevocacion si es {@code false} no se usa la red: sirve para decidir rápido si el
     *                            documento está abierto o cerrado, o para comprobar la integridad después
     *                            de firmar.
     */
    public static Analisis analizar(Path pdf, boolean consultarRevocacion) throws DocumentoIlegibleException {
        try (PdfReader reader = new PdfReader(pdf.toString());
             PdfDocument doc = new PdfDocument(reader)) {

            SignatureUtil util = new SignatureUtil(doc);
            List<String> nombres = util.getSignatureNames();

            List<FirmaPrevia> firmas = new ArrayList<>();
            for (int i = 0; i < nombres.size(); i++) {
                firmas.add(analizarFirma(util, nombres.get(i), i + 1, consultarRevocacion));
            }

            Estado estado = Estado.ABIERTO;
            String motivo = null;

            Integer permisoDocMdp = permisoDocMdp(doc);
            if (permisoDocMdp != null && permisoDocMdp == 1) {
                estado = Estado.CERRADO;
                String quien = personas(firmas).isEmpty() ? "su autor" : personas(firmas).get(0).firmante();
                motivo = "El documento fue bloqueado por " + quien + " y no admite más firmas.";
            } else if (bloqueadaPorCampo(doc, nombres) != null) {
                // "Bloquear documento después de firmar" de Acrobat: un diccionario /Lock con
                // Action=All que declara que nada más debe cambiar en el documento.
                int indice = nombres.indexOf(bloqueadaPorCampo(doc, nombres));
                FirmaPrevia quien = firmas.get(indice);
                estado = Estado.CERRADO;
                motivo = "El documento fue bloqueado por " + quien.firmante() + " al firmarlo el "
                        + fecha(quien.fecha()) + " y no admite más firmas.";
            } else if (!personas(firmas).isEmpty() && ultimaPersona(firmas).esCierre()) {
                FirmaPrevia ultima = ultimaPersona(firmas);
                estado = Estado.CERRADO;
                motivo = "El documento fue cerrado por " + ultima.firmante() + " el " + fecha(ultima.fecha())
                        + " con su firma final y no admite más firmas.";
            }

            CambiosPosteriores cambios = CambiosPosteriores.NINGUNO;
            if (!nombres.isEmpty() && !util.signatureCoversWholeDocument(nombres.get(nombres.size() - 1))) {
                cambios = clasificarCambios(doc, util, nombres.get(nombres.size() - 1),
                        firmas.get(firmas.size() - 1).firmante(), estado == Estado.CERRADO);
            }

            boolean contenidoProtegido = permisoDocMdp != null && (permisoDocMdp == 2 || permisoDocMdp == 3);
            return new Analisis(estado, motivo, firmas, cambios, camposVacios(doc, util), paginas(doc),
                    contenidoProtegido);
        } catch (BadPasswordException e) {
            throw new DocumentoIlegibleException(
                    "El documento está protegido con una contraseña y no se puede abrir.");
        } catch (IOException | RuntimeException e) {
            throw new DocumentoIlegibleException(
                    "No se pudo leer el documento PDF. Verifique que el archivo no esté dañado.");
        }
    }

    /**
     * Clasifica lo que se agregó al archivo después de la última firma, comparándolo con el documento
     * tal como estaba cuando esa firma se aplicó. Lo habitual en la práctica (Acrobat agrega información
     * de validación, o alguien comenta o completa un formulario) no invalida nada y no debe asustar:
     * solo se presenta como alerta un cambio en el contenido de las páginas, o cualquier cambio sobre un
     * documento que su firmante bloqueó. Si no se puede comparar, es un aviso suave, nunca una alarma.
     */
    private static CambiosPosteriores clasificarCambios(PdfDocument actual, SignatureUtil util, String campo,
                                                        String ultimoFirmante, boolean bloqueado) {
        try (InputStream revision = util.extractRevision(campo);
             PdfDocument previo = new PdfDocument(new PdfReader(revision))) {

            boolean contenido = previo.getNumberOfPages() != actual.getNumberOfPages();
            boolean anotaciones = false;
            for (int i = 1; !contenido && i <= actual.getNumberOfPages(); i++) {
                PdfPage antes = previo.getPage(i);
                PdfPage ahora = actual.getPage(i);
                contenido = !Arrays.equals(antes.getContentBytes(), ahora.getContentBytes())
                        || !antes.getMediaBox().equalsWithEpsilon(ahora.getMediaBox())
                        || antes.getRotation() != ahora.getRotation();
                anotaciones |= antes.getAnnotsSize() != ahora.getAnnotsSize();
            }
            boolean formulario = !valoresDeFormulario(previo).equals(valoresDeFormulario(actual));
            boolean validacionNueva = actual.getCatalog().getPdfObject().containsKey(PdfName.DSS)
                    && !previo.getCatalog().getPdfObject().containsKey(PdfName.DSS);

            if (contenido) {
                return new CambiosPosteriores(Nivel.ALERTA, "El documento tiene cambios en su contenido posteriores "
                        + "a la última firma (" + ultimoFirmante + "). Esos cambios no están respaldados por ninguna firma.");
            }
            if (bloqueado && (anotaciones || formulario)) {
                return new CambiosPosteriores(Nivel.ALERTA, "El documento había sido bloqueado, pero se le hicieron "
                        + "cambios después de la última firma (" + ultimoFirmante + ").");
            }
            if (anotaciones || formulario) {
                return new CambiosPosteriores(Nivel.INFORMATIVO, "Después de la última firma (" + ultimoFirmante
                        + ") se agregaron comentarios o se completaron campos del formulario. Es habitual y no "
                        + "invalida la firma.");
            }
            if (validacionNueva) {
                return CambiosPosteriores.NINGUNO;
            }
            return new CambiosPosteriores(Nivel.INFORMATIVO, "Después de la última firma (" + ultimoFirmante
                    + ") el archivo se guardó nuevamente, sin cambios visibles en su contenido.");
        } catch (Exception e) {
            return new CambiosPosteriores(Nivel.AVISO, "No pudimos comprobar qué cambios se hicieron en el "
                    + "documento después de la última firma (" + ultimoFirmante + ").");
        }
    }

    private static List<PaginaInfo> paginas(PdfDocument doc) {
        List<PaginaInfo> paginas = new ArrayList<>();
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            PdfPage pagina = doc.getPage(i);
            Rectangle area = pagina.getCropBox();
            paginas.add(new PaginaInfo(area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                    pagina.getRotation()));
        }
        return paginas;
    }

    private static List<CampoVacio> camposVacios(PdfDocument doc, SignatureUtil util) {
        List<CampoVacio> vacios = new ArrayList<>();
        PdfAcroForm formulario = PdfAcroForm.getAcroForm(doc, false);
        for (String nombre : util.getBlankSignatureNames()) {
            PdfFormField campo = formulario == null ? null : formulario.getField(nombre);
            int pagina = 1;
            if (campo != null && !campo.getWidgets().isEmpty() && campo.getWidgets().get(0).getPage() != null) {
                pagina = doc.getPageNumber(campo.getWidgets().get(0).getPage());
            }
            vacios.add(new CampoVacio(nombre, pagina));
        }
        return vacios;
    }

    private static Map<String, String> valoresDeFormulario(PdfDocument doc) {
        Map<String, String> valores = new TreeMap<>();
        PdfAcroForm formulario = PdfAcroForm.getAcroForm(doc, false);
        if (formulario != null) {
            for (Map.Entry<String, PdfFormField> campo : formulario.getAllFormFields().entrySet()) {
                if (!PdfName.Sig.equals(campo.getValue().getFormType())) {
                    String valor = campo.getValue().getValueAsString();
                    valores.put(campo.getKey(), valor == null ? "" : valor);
                }
            }
        }
        return valores;
    }

    /**
     * Nombre del primer campo de firma ya firmado cuyo diccionario /Lock bloquea todo el documento
     * sin permitir ningún cambio posterior; null si no hay ninguno. Un bloqueo parcial (solo
     * ciertos campos) o que todavía permite firmar o completar formularios no cierra el documento.
     */
    private static String bloqueadaPorCampo(PdfDocument doc, List<String> firmados) {
        PdfAcroForm formulario = PdfAcroForm.getAcroForm(doc, false);
        if (formulario == null) {
            return null;
        }
        for (String nombre : firmados) {
            PdfFormField campo = formulario.getField(nombre);
            PdfDictionary bloqueo = campo == null ? null : campo.getPdfObject().getAsDictionary(PdfName.Lock);
            if (bloqueo == null || !PdfName.All.equals(bloqueo.getAsName(PdfName.Action))) {
                continue;
            }
            PdfNumber permiso = bloqueo.getAsNumber(PdfName.P);
            if (permiso == null || permiso.intValue() == 1) {
                return nombre;
            }
        }
        return null;
    }

    private static Integer permisoDocMdp(PdfDocument doc) {
        PdfDictionary perms = doc.getCatalog().getPdfObject().getAsDictionary(PdfName.Perms);
        PdfDictionary docMdp = perms == null ? null : perms.getAsDictionary(PdfName.DocMDP);
        PdfArray referencias = docMdp == null ? null : docMdp.getAsArray(PdfName.Reference);
        if (referencias == null) {
            return perms != null && docMdp != null ? 2 : null;
        }
        for (int i = 0; i < referencias.size(); i++) {
            PdfDictionary referencia = referencias.getAsDictionary(i);
            PdfDictionary parametros = referencia == null ? null : referencia.getAsDictionary(PdfName.TransformParams);
            PdfNumber p = parametros == null ? null : parametros.getAsNumber(PdfName.P);
            if (p != null) {
                return p.intValue();
            }
        }
        return 2;
    }

    private static List<FirmaPrevia> personas(List<FirmaPrevia> firmas) {
        return firmas.stream().filter(f -> !f.esSelloDeTiempo()).toList();
    }

    private static FirmaPrevia ultimaPersona(List<FirmaPrevia> firmas) {
        List<FirmaPrevia> personas = personas(firmas);
        return personas.get(personas.size() - 1);
    }

    private static FirmaPrevia analizarFirma(SignatureUtil util, String campo, int numero,
                                             boolean consultarRevocacion) {
        String subFiltro = subFiltroDeLaFirma(util, campo);
        PdfPKCS7 pkcs7;
        try {
            pkcs7 = util.readSignatureData(campo);
        } catch (RuntimeException e) {
            // Una firma de un tipo que no conocemos no está dañada: simplemente no la sabemos leer.
            return subFiltro != null && !SUBFILTROS_CONOCIDOS.contains(subFiltro)
                    ? firmaNoComprobable(numero, campo)
                    : firmaDanada(numero, campo);
        }

        if ("ETSI.RFC3161".equals(subFiltro)) {
            return analizarSelloDeTiempo(pkcs7, numero, campo);
        }

        X509Certificate cert = pkcs7.getSigningCertificate();
        if (cert == null) {
            return firmaDanada(numero, campo);
        }

        String firmante = nombreComun(cert);
        Date fechaFirma = fechaDeLaFirma(pkcs7, util, campo);
        boolean esCierre = MARCA_CIERRE.equals(motivoDeLaFirma(util, campo));
        List<String> notas = new ArrayList<>();

        boolean integra;
        try {
            integra = pkcs7.verifySignatureIntegrityAndAuthenticity();
        } catch (Exception e) {
            // Un error al PROCESAR la firma (algoritmo que este programa no soporta, por ejemplo) no
            // es lo mismo que una firma rota: solo se informa que no pudimos comprobarla.
            return new FirmaPrevia(numero, campo, firmante, fechaFirma, Veredicto.NO_VERIFICABLE,
                    "No pudimos comprobar la firma de " + firmante + " porque usa un método de firma que este "
                            + "programa no sabe verificar. Eso no significa que la firma sea inválida.",
                    notas, false, esCierre);
        }
        if (!integra) {
            return new FirmaPrevia(numero, campo, firmante, fechaFirma, Veredicto.INVALIDA,
                    "La firma de " + firmante + " NO es válida: el documento fue modificado después de que "
                            + firmante + " lo firmara.",
                    notas, false, esCierre);
        }

        if (fechaFirma != null && (fechaFirma.getTime() + TOLERANCIA_RELOJ_MS < cert.getNotBefore().getTime()
                || fechaFirma.getTime() - TOLERANCIA_RELOJ_MS > cert.getNotAfter().getTime())) {
            return new FirmaPrevia(numero, campo, firmante, fechaFirma, Veredicto.INVALIDA,
                    "La firma de " + firmante + " NO es válida: su certificado todavía no estaba vigente, o ya había "
                            + "vencido, en la fecha de la firma (" + fecha(fechaFirma) + ").",
                    notas, true, esCierre);
        }

        Date ahora = new Date();
        if (cert.getNotAfter().before(ahora) && fechaFirma != null) {
            notas.add("El certificado de " + firmante + " venció el " + fecha(cert.getNotAfter())
                    + ", pero estaba vigente cuando firmó el " + fecha(fechaFirma)
                    + ". Esto no afecta la validez de su firma.");
        }

        Veredicto veredicto = Veredicto.VALIDA;
        String mensaje = "La firma de " + firmante + " es válida.";
        String detalleRevocacion = null;

        if (consultarRevocacion && fechaFirma != null) {
            Certificate[] cadena = pkcs7.getSignCertificateChain();
            X509Certificate emisor = cadena != null && cadena.length > 1 && cadena[1] instanceof X509Certificate x
                    ? x : null;
            RevocationValidator.Resultado r = RevocationValidator.validarAntesDeFirmar(cert, emisor);
            detalleRevocacion = describirRevocacion(r);
            switch (r.getEstado()) {
                case GOOD -> {
                }
                case REVOKED -> {
                    Date baja = r.getFechaRevocacion();
                    if (baja == null) {
                        veredicto = Veredicto.NO_VERIFICABLE;
                        mensaje = "La firma de " + firmante + " está íntegra, pero no pudimos determinar si su "
                                + "certificado estaba dado de baja cuando firmó, porque la autoridad certificante "
                                + "no informa desde cuándo.";
                    } else if (baja.after(fechaFirma)) {
                        notas.add("El certificado de " + firmante + " fue dado de baja (revocado) por la autoridad "
                                + "certificante el " + fecha(baja) + ", después de que firmara el " + fecha(fechaFirma)
                                + ". Esto no afecta la validez de su firma.");
                    } else {
                        return new FirmaPrevia(numero, campo, firmante, fechaFirma, Veredicto.INVALIDA,
                                "La firma de " + firmante + " NO es válida: su certificado ya estaba dado de baja "
                                        + "(revocado por la autoridad certificante) en la fecha de la firma ("
                                        + fecha(fechaFirma) + ").",
                                notas, true, esCierre, false, detalleRevocacion);
                    }
                }
                case UNKNOWN -> {
                    veredicto = Veredicto.NO_VERIFICABLE;
                    mensaje = "La firma de " + firmante + " está íntegra, pero no pudimos comprobar si su "
                            + "certificado estaba vigente cuando firmó (" + razonLegible(r.getDetalle()) + ").";
                }
            }
        }

        return new FirmaPrevia(numero, campo, firmante, fechaFirma, veredicto, mensaje, notas, true, esCierre,
                false, detalleRevocacion);
    }

    /**
     * Fecha de la firma, en orden de confianza: sello de tiempo incluido en la firma, hora declarada por
     * el firmante en la propia firma, y como último recurso la fecha del campo de firma. Si ninguna
     * existe se devuelve null y no se controla la vigencia del certificado.
     */
    private static Date fechaDeLaFirma(PdfPKCS7 pkcs7, SignatureUtil util, String campo) {
        try {
            Calendar sello = pkcs7.getTimeStampDate();
            if (sello != null && pkcs7.verifyTimestampImprint()) {
                return sello.getTime();
            }
            Calendar declarada = pkcs7.getSignDate();
            if (declarada != null) {
                return declarada.getTime();
            }
            PdfDictionary dic = util.getSignatureDictionary(campo);
            PdfString m = dic == null ? null : dic.getAsString(PdfName.M);
            return m == null ? null : PdfDate.decode(m.toUnicodeString()).getTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static String subFiltroDeLaFirma(SignatureUtil util, String campo) {
        try {
            PdfDictionary dic = util.getSignatureDictionary(campo);
            PdfName subFiltro = dic == null ? null : dic.getAsName(PdfName.SubFilter);
            return subFiltro == null ? null : subFiltro.getValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Sello de tiempo del documento: no es la firma de una persona; respalda la fecha del contenido. */
    private static FirmaPrevia analizarSelloDeTiempo(PdfPKCS7 pkcs7, int numero, String campo) {
        List<String> notas = new ArrayList<>();
        Date fecha = null;
        try {
            fecha = pkcs7.getTimeStampDate() == null ? null : pkcs7.getTimeStampDate().getTime();
            if (!pkcs7.verifySignatureIntegrityAndAuthenticity()) {
                return new FirmaPrevia(numero, campo, "sello de tiempo", fecha, Veredicto.INVALIDA,
                        "El sello de tiempo del documento NO es válido: el documento fue modificado después de "
                                + "haber sido sellado.", notas, false, false, true);
            }
        } catch (Exception e) {
            return new FirmaPrevia(numero, campo, "sello de tiempo", fecha, Veredicto.NO_VERIFICABLE,
                    "No pudimos comprobar el sello de tiempo del documento.", notas, false, false, true);
        }
        return new FirmaPrevia(numero, campo, "sello de tiempo", fecha, Veredicto.VALIDA,
                "El documento tiene un sello de tiempo del " + fecha(fecha) + " que respalda su fecha.",
                notas, true, false, true);
    }

    private static FirmaPrevia firmaNoComprobable(int numero, String campo) {
        return new FirmaPrevia(numero, campo, "firma " + numero, null, Veredicto.NO_VERIFICABLE,
                "No pudimos comprobar la firma número " + numero + " del documento: es de un tipo que este "
                        + "programa no sabe leer. Eso no significa que sea inválida.",
                new ArrayList<>(), false, false);
    }

    private static FirmaPrevia firmaDanada(int numero, String campo) {
        return new FirmaPrevia(numero, campo, "firma " + numero, null, Veredicto.INVALIDA,
                "La firma número " + numero + " del documento está dañada y no se puede leer.",
                new ArrayList<>(), false, false);
    }

    private static String motivoDeLaFirma(SignatureUtil util, String campo) {
        try {
            PdfDictionary dic = util.getSignatureDictionary(campo);
            PdfString motivo = dic == null ? null : dic.getAsString(PdfName.Reason);
            return motivo == null ? null : motivo.toUnicodeString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Texto técnico-legible del estado de revocación, para el informe detallado (no para el resumen). */
    private static String describirRevocacion(RevocationValidator.Resultado r) {
        return switch (r.getEstado()) {
            case GOOD -> "No figura revocado" + (r.getMetodo() != null ? " (consulta " + r.getMetodo() + ")" : "");
            case REVOKED -> "Figura revocado"
                    + (r.getFechaRevocacion() != null ? " desde el " + fecha(r.getFechaRevocacion()) : "")
                    + (r.getMotivoRevocacion() != null ? "; motivo: " + r.getMotivoRevocacion() : "");
            case UNKNOWN -> "No se pudo comprobar (" + razonLegible(r.getDetalle()) + ")";
        };
    }

    private static String razonLegible(String detalle) {
        if (detalle != null && detalle.contains("sin conexión")) {
            return "no hay conexión a Internet";
        }
        if (detalle != null && detalle.contains("no publica")) {
            return "el certificado no indica dónde consultar su estado";
        }
        return "el servicio de consulta de la autoridad certificante no respondió";
    }

    static String nombreComun(X509Certificate cert) {
        try {
            for (Rdn rdn : new LdapName(cert.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return rdn.getValue().toString();
                }
            }
        } catch (Exception ignored) {
            // Se usa el nombre completo más abajo.
        }
        return cert.getSubjectX500Principal().getName();
    }

    private static String fecha(Date fecha) {
        return fecha == null ? "fecha desconocida"
                : new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-AR")).format(fecha);
    }

    /**
     * Imprime el informe en un formato estable pensado para que otro programa (la interfaz gráfica)
     * lo lea, con los textos ya redactados para el usuario final.
     */
    public static void imprimirInforme(Analisis analisis, PrintStream out) {
        out.println("ESTADO_DOCUMENTO: " + analisis.estado());
        if (analisis.motivoCierre() != null) {
            out.println("MOTIVO_CIERRE: " + analisis.motivoCierre());
        }
        out.println("CANTIDAD_FIRMAS: " + personas(analisis.firmas()).size());
        for (FirmaPrevia f : analisis.firmas()) {
            out.println((f.esSelloDeTiempo() ? "SELLO: " : "FIRMA: ") + f.numero() + " | " + f.veredicto()
                    + " | " + f.firmante());
            out.println("MENSAJE: " + f.mensaje());
            out.println("CAMPO: " + f.campo());
            if (f.detalleRevocacion() != null) {
                out.println("REVOCACION: " + f.detalleRevocacion());
            }
            for (String nota : f.notas()) {
                out.println("NOTA: " + nota);
            }
        }
        switch (analisis.cambios().nivel()) {
            case INFORMATIVO -> out.println("NOTA_DOCUMENTO: " + analisis.cambios().mensaje());
            case AVISO, ALERTA -> out.println("AVISO_DOCUMENTO: " + analisis.cambios().mensaje());
            case NINGUNO -> {
            }
        }
        out.println("PAGINAS: " + analisis.paginas().size());
        if (analisis.contenidoProtegido()) {
            out.println("CONTENIDO_PROTEGIDO: SI");
        }
        for (CampoVacio campo : analisis.camposVacios()) {
            out.println("CAMPO_VACIO: " + campo.nombre() + " | " + campo.pagina());
        }
        if (analisis.hayProblemas()) {
            out.println("RECOMENDACION: Le recomendamos consultar con quien le envió el documento antes de firmar. "
                    + "Si decide continuar, su firma se agregará, pero lo señalado arriba seguirá sin ser válido.");
        }
    }
}
