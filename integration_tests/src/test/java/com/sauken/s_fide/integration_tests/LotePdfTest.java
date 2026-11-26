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
import java.util.Locale;
import java.util.stream.Stream;

import static com.sauken.s_fide.integration_tests.ProcesoSupport.ejecutarModulo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Firma de PDF por lotes (PDFSignerPKCS12), contra el programa real como proceso. */
class LotePdfTest {

    private static final String FIRMADOR = "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12";
    private static final String VERIFICADOR = "com.sauken.s_fide.pdf_verify_signatures.PDFVerifySignatures";

    @TempDir
    Path dir;

    private String clave;

    @BeforeEach
    void certificado() throws Exception {
        clave = ProcesoSupport.generarP12(dir, dir.resolve("prueba.p12"), "S-FiDE Lote PDF");
    }

    // ------------------------------------------------------------------------------------------------ apoyo

    /** Un PDF válido de dos páginas A4, armado a mano. */
    private Path pdf(String nombre) throws IOException {
        List<String> objetos = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>");
        StringBuilder texto = new StringBuilder("%PDF-1.7\n");
        List<Integer> posiciones = new ArrayList<>();
        for (int i = 0; i < objetos.size(); i++) {
            posiciones.add(texto.length());
            texto.append(i + 1).append(" 0 obj\n").append(objetos.get(i)).append("\nendobj\n");
        }
        int xref = texto.length();
        texto.append("xref\n0 5\n0000000000 65535 f \n");
        for (int posicion : posiciones) {
            texto.append(String.format(Locale.ROOT, "%010d 00000 n \n", posicion));
        }
        texto.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        Path archivo = dir.resolve(nombre);
        Files.writeString(archivo, texto, StandardCharsets.US_ASCII);
        return archivo;
    }

    private Path lista(String... lineas) throws IOException {
        Path archivo = dir.resolve("lista.txt");
        Files.writeString(archivo, String.join("\n", lineas) + "\n", StandardCharsets.UTF_8);
        return archivo;
    }

    private Resultado lote(String password, Path lista, String... opciones) throws Exception {
        List<String> argumentos = new ArrayList<>(List.of("-lote", lista.toString(), "-c",
                dir.resolve("prueba.p12").toString(), "-p", password));
        argumentos.addAll(List.of(opciones));
        return ejecutarModulo(dir, FIRMADOR, argumentos.toArray(new String[0]));
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

    private List<String> firmados() throws IOException {
        try (Stream<Path> archivos = Files.list(dir)) {
            return archivos.map(p -> p.getFileName().toString())
                    .filter(n -> n.contains("-signed") && n.endsWith(".pdf")).sorted().toList();
        }
    }

    private Resultado verificar(String nombre) throws Exception {
        return ejecutarModulo(dir, VERIFICADOR, dir.resolve(nombre).toString(), "-simple");
    }

    // ---------------------------------------------------------------------------------------------- pruebas

    @Test
    void firmaElLoteVisibleYAdmiteUnaSegundaFirma() throws Exception {
        pdf("uno.pdf");
        pdf("Declaración dos.pdf");

        Resultado r = lote(clave, lista("uno.pdf", "Declaración dos.pdf"), "-x", "40", "-y", "55", "-pagina", "2",
                "-t", "-lote", "-log");

        assertEquals(0, r.exitCode(), r.salida());
        assertEquals(List.of("FIRMADO/OK", "FIRMADO/OK"), estados(r), r.salida());
        assertEquals(List.of("Declaración dos-signed.pdf", "uno-signed.pdf"), firmados());
        for (String nombre : firmados()) {
            assertEquals(0, verificar(nombre).exitCode(), nombre);
        }
        assertTrue(Files.readString(dir.resolve("uno.pdf.log")).contains("Resultado: FIRMADO"));
        assertFalse(r.salida().contains("guardado como"), r.salida());

        Resultado segunda = lote(clave, lista("uno-signed.pdf", "Declaración dos-signed.pdf"), "-l", "true", "-log");

        assertEquals(0, segunda.exitCode(), segunda.salida());
        assertEquals(List.of("FIRMADO/OK", "FIRMADO/OK"), estados(segunda), segunda.salida());
        assertEquals(0, verificar("uno-signed-signed.pdf").exitCode());
        // El detalle de la firma anterior queda en el registro, no en la salida del lote.
        assertTrue(Files.readString(dir.resolve("uno-signed.pdf.log")).contains("CANTIDAD_FIRMAS: 1"));
        assertFalse(segunda.salida().contains("CANTIDAD_FIRMAS"), segunda.salida());

        Resultado cerrado = lote(clave, lista("uno-signed-signed.pdf"));
        assertEquals(1, cerrado.exitCode(), cerrado.salida());
        assertEquals(List.of("ERROR/DOCUMENTO_CERRADO"), estados(cerrado), cerrado.salida());
    }

    @Test
    void elBloqueoCierraTodosLosDocumentos() throws Exception {
        pdf("a.pdf");
        pdf("b.pdf");

        Resultado r = lote(clave, lista("a.pdf", "b.pdf"), "-l", "true");

        assertEquals(0, r.exitCode(), r.salida());
        Resultado otraVez = lote(clave, lista("a-signed.pdf", "b-signed.pdf"));
        assertEquals(List.of("ERROR/DOCUMENTO_CERRADO", "ERROR/DOCUMENTO_CERRADO"), estados(otraVez),
                otraVez.salida());
        try (Stream<Path> archivos = Files.list(dir)) {
            assertTrue(archivos.noneMatch(p -> p.toString().endsWith(".tmp-sfide")));
        }
    }

    @Test
    void unaFirmaAnteriorInvalidaSeFirmaConAviso() throws Exception {
        pdf("a.pdf");
        assertEquals(0, lote(clave, lista("a.pdf")).exitCode());
        // Se altera una página dentro del contenido ya firmado, sin cambiar el tamaño del archivo.
        Path firmado = dir.resolve("a-signed.pdf");
        String bytes = Files.readString(firmado, StandardCharsets.ISO_8859_1);
        int donde = bytes.indexOf("/MediaBox [0 0 595 842]");
        assertTrue(donde > 0);
        Files.writeString(firmado, bytes.substring(0, donde) + "/MediaBox [0 0 595 841]"
                + bytes.substring(donde + "/MediaBox [0 0 595 842]".length()), StandardCharsets.ISO_8859_1);

        Resultado r = lote(clave, lista("a-signed.pdf"));

        assertEquals(0, r.exitCode(), r.salida());
        assertEquals(List.of("FIRMADO_CON_AVISOS/FIRMA_PREVIA_INVALIDA"), estados(r), r.salida());
        assertTrue(resumen(r).contains("\"con_avisos=1\""), resumen(r));
        assertTrue(Files.exists(dir.resolve("a-signed-signed.pdf")));
    }

    @Test
    void losErroresDeUnDocumentoNoFrenanAlResto() throws Exception {
        pdf("bueno.pdf");
        pdf("existente.pdf");
        Files.writeString(dir.resolve("existente-signed.pdf"), "anterior");
        Files.writeString(dir.resolve("roto.pdf"), "esto no es un PDF");

        Resultado r = lote(clave, lista("roto.pdf", "noesta.pdf", "existente.pdf", "bueno.pdf"));

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("ERROR/DOCUMENTO_MAL_FORMADO", "ERROR/ARCHIVO_NO_ENCONTRADO",
                "OMITIDO/SALIDA_YA_EXISTE", "FIRMADO/OK"), estados(r), r.salida());
        assertEquals("anterior", Files.readString(dir.resolve("existente-signed.pdf")));

        pdf("c.pdf");
        Resultado pagina = lote(clave, lista("c.pdf"), "-x", "40", "-y", "55", "-pagina", "9");
        assertEquals(List.of("ERROR/PAGINA_INEXISTENTE"), estados(pagina), pagina.salida());
        Resultado posicion = lote(clave, lista("c.pdf"), "-x", "590", "-y", "55");
        assertEquals(List.of("ERROR/FIRMA_NO_ENTRA"), estados(posicion), posicion.salida());
        Resultado proteccion = lote(clave, lista("bueno-signed.pdf"), "-proteger-contenido", "true");
        assertEquals(List.of("ERROR/PROTECCION_NO_APLICABLE"), estados(proteccion), proteccion.salida());
    }

    @Test
    void revisarNoFirmaYLaContrasenaIncorrectaCorta() throws Exception {
        pdf("a.pdf");
        pdf("b.pdf");

        Resultado revision = lote("no-se-usa", lista("a.pdf", "b.pdf"), "-revisar");
        assertEquals(0, revision.exitCode(), revision.salida());
        assertEquals(List.of("LISTO/OK", "LISTO/OK"), estados(revision), revision.salida());

        Resultado mala = lote("equivocada", lista("a.pdf", "b.pdf"));
        assertEquals(1, mala.exitCode(), mala.salida());
        assertEquals(List.of("NO_PROCESADO/CONTRASENA", "NO_PROCESADO/CONTRASENA"), estados(mala), mala.salida());
        assertTrue(firmados().isEmpty());
    }

    @Test
    void losArgumentosEnLoteSonEstrictosYRevisarSoloNuncaFirma() throws Exception {
        Path a = pdf("a.pdf");
        Path lista = lista("a.pdf");
        for (String[] opciones : List.of(new String[]{"-desconocido", "x"}, new String[]{"-campo", "Firma1"},
                new String[]{"-i", a.toString()}, new String[]{"-l", "si"}, new String[]{"-x", "abc"},
                new String[]{"-proteger-contenid", "true"}, new String[]{"-x"}, new String[]{"-p", "otra"},
                new String[]{"-l", "true", "-proteger-contenido", "true"})) {
            Resultado r = lote(clave, lista, opciones);
            assertEquals(1, r.exitCode(), r.salida());
            assertTrue(resumen(r).endsWith("\"corte=ARGUMENTOS\""), String.join(" ", opciones) + " -> " + r.salida());
        }
        Resultado sinClave = ejecutarModulo(dir, FIRMADOR, "-lote", lista.toString(), "-c", "prueba.p12");
        assertTrue(resumen(sinClave).endsWith("\"corte=ARGUMENTOS\""), sinClave.salida());

        String p12 = dir.resolve("prueba.p12").toString();
        Resultado revisarSolo = ejecutarModulo(dir, FIRMADOR, "-i", a.toString(), "-c", p12, "-p", clave, "-revisar");
        assertEquals(1, revisarSolo.exitCode(), revisarSolo.salida());
        Resultado revisarPrimero = ejecutarModulo(dir, FIRMADOR, "--revisar", "-i", a.toString(), "-c", p12, "-p",
                clave);
        assertEquals(1, revisarPrimero.exitCode(), revisarPrimero.salida());
        assertTrue(firmados().isEmpty());
    }

    /** Lo que se puede comprobar del firmador con token sin tener un token conectado. */
    @Test
    void firmadorConTokenSinTokenConectado() throws Exception {
        String conToken = "com.sauken.s_fide.pdf_signer_pkcs11.PDFSignerPKCS11";
        Path a = pdf("a.pdf");
        String lista = lista("a.pdf").toString();
        String inexistente = dir.resolve("noesta.dll").toString();

        Resultado revision = ejecutarModulo(dir, conToken, "-lote", lista, "-l", inexistente, "-p", "1",
                "-s", "0", "-revisar");
        assertEquals(0, revision.exitCode(), "Revisar no usa la biblioteca: " + revision.salida());
        assertEquals(List.of("LISTO/OK"), estados(revision), revision.salida());

        Resultado sinBiblioteca = ejecutarModulo(dir, conToken, "-lote", lista, "-l", inexistente, "-p", "1",
                "-s", "0");
        assertEquals(1, sinBiblioteca.exitCode(), sinBiblioteca.salida());
        assertEquals(List.of("NO_PROCESADO/BIBLIOTECA"), estados(sinBiblioteca), sinBiblioteca.salida());

        Resultado campo = ejecutarModulo(dir, conToken, "-lote", lista, "-l", inexistente, "-p", "1",
                "-s", "0", "-campo", "x");
        assertTrue(resumen(campo).endsWith("\"corte=ARGUMENTOS\""), campo.salida());
        Resultado revisarSolo = ejecutarModulo(dir, conToken, "-i", a.toString(), "-l", inexistente, "-p",
                "1", "-s", "0", "-revisar");
        assertEquals(1, revisarSolo.exitCode(), revisarSolo.salida());
        assertTrue(revisarSolo.salida().contains("solo se puede usar junto con -lote"), revisarSolo.salida());
        assertTrue(firmados().isEmpty());
    }
}
