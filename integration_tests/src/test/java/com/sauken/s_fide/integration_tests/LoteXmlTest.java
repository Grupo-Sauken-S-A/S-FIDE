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
import java.util.stream.Stream;

import static com.sauken.s_fide.integration_tests.ProcesoSupport.ejecutarModulo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Firma de XML por lotes (XMLSignerPKCS12), contra el programa real como proceso: contrato de salida, informe,
 * registros, regla de no mezclar tipos de documento, cortes y garantía de no reemplazar archivos. Cubre lo
 * común a todos los firmadores por lotes; los de token comparten esas clases (ver {@link LoteCopiasTest}).
 */
class LoteXmlTest {

    private static final String FIRMADOR = "com.sauken.s_fide.xml_signer_pkcs12.XMLSignerPKCS12";
    private static final String VERIFICADOR = "com.sauken.s_fide.xml_verify_signatures.XMLVerifySignatures";

    @TempDir
    Path dir;

    private String clave;

    @BeforeEach
    void certificado() throws Exception {
        clave = ProcesoSupport.generarP12(dir, dir.resolve("prueba.p12"), "S-FiDE Lote");
    }

    // ------------------------------------------------------------------------------------------------ apoyo

    private Path cod(String nombre) throws IOException {
        return escribir(nombre, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Certificado>\n"
                + "<E><COD Id=\"COD\"><n>Ñandú</n></COD></E>\n<F><CODEH Id=\"CODEH\"><n>2</n></CODEH></F>\n"
                + "</Certificado>\n");
    }

    private Path djo(String nombre) throws IOException {
        return escribir(nombre, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Declaracion>\n"
                + "<E><DJO Id=\"DJO\"><n>1</n></DJO></E>\n</Declaracion>\n");
    }

    private Path escribir(String nombre, String contenido) throws IOException {
        Path archivo = dir.resolve(nombre);
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }

    private Path lista(String... lineas) throws IOException {
        return escribir("lista.txt", String.join("\r\n", lineas) + "\r\n");
    }

    private Resultado lote(Path lista, String password, String elemento, String... opciones) throws Exception {
        List<String> argumentos = new ArrayList<>(List.of("-lote", lista.toString(),
                dir.resolve("prueba.p12").toString(), password, elemento));
        argumentos.addAll(List.of(opciones));
        return ejecutarModulo(dir, FIRMADOR, argumentos.toArray(new String[0]));
    }

    /** Campos de las líneas LOTE_DOC, en orden. */
    private static List<List<String>> documentos(Resultado r) {
        return r.salida().lines().filter(l -> l.startsWith("LOTE_DOC;")).map(l -> {
            String cuerpo = l.substring("LOTE_DOC;\"".length(), l.length() - 1);
            return List.of(cuerpo.split("\";\"", -1));
        }).toList();
    }

    private static List<String> estados(Resultado r) {
        return documentos(r).stream().map(c -> c.get(2) + "/" + c.get(3)).toList();
    }

    private static String resumen(Resultado r) {
        List<String> lineas = r.salida().lines().filter(l -> l.startsWith("LOTE_RESUMEN;")).toList();
        assertEquals(1, lineas.size(), r.salida());
        return lineas.get(0);
    }

    private List<String> firmados() throws IOException {
        try (Stream<Path> archivos = Files.list(dir)) {
            return archivos.map(p -> p.getFileName().toString())
                    .filter(n -> n.contains("-signed") && n.endsWith(".xml")).sorted().toList();
        }
    }

    private void sinTemporales() throws IOException {
        try (Stream<Path> archivos = Files.list(dir)) {
            assertTrue(archivos.noneMatch(p -> p.toString().endsWith(".tmp-sfide")), "Quedaron temporales");
        }
    }

    // ---------------------------------------------------------------------------------------------- pruebas

    @Test
    void firmaTodoElLoteConInformeYRegistro() throws Exception {
        Path uno = cod("uno.xml");
        Path dos = cod("Declaración ñ; dos.xml");
        Path tres = dir.resolve("tres.xml");
        byte[] conBom = ("﻿" + Files.readString(uno)).getBytes(StandardCharsets.UTF_8);
        Files.write(tres, conBom);
        byte[] antes = Files.readAllBytes(uno);
        // Rutas relativas, con las comillas de "Copiar como ruta", líneas vacías y BOM en la lista.
        Path lista = lista("﻿uno.xml", "", "\"" + dos + "\"", "  tres.xml  ");
        Path informe = dir.resolve("informe.csv");

        Resultado r = lote(lista, clave, "COD", "-informe", informe.toString(), "-log");

        assertEquals(0, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("LOTE_INICIO;\"1\";\"XMLSignerPKCS12\";"), r.salida());
        assertEquals(List.of("FIRMADO/OK", "FIRMADO/OK", "FIRMADO/OK"), estados(r), r.salida());
        assertTrue(resumen(r).contains("\"total=3\";\"correctos=3\""), resumen(r));
        assertTrue(resumen(r).endsWith("\"corte=NINGUNO\""), resumen(r));
        assertEquals(List.of("Declaración ñ; dos-signed.xml", "tres-signed.xml", "uno-signed.xml"), firmados());
        for (String nombre : firmados()) {
            Resultado v = ejecutarModulo(dir, VERIFICADOR, dir.resolve(nombre).toString(), "-simple");
            assertEquals(0, v.exitCode(), v.salida());
        }
        assertArrayEquals(antes, Files.readAllBytes(uno), "El original no se modifica");

        byte[] csv = Files.readAllBytes(informe);
        assertEquals('"', csv[0], "El informe no lleva BOM");
        List<String> filas = Files.readAllLines(informe, StandardCharsets.UTF_8);
        assertEquals(4, filas.size());
        assertEquals("\"n\";\"total\";\"estado\";\"codigo\";\"entrada\";\"salida\";\"log\";\"mensaje\"", filas.get(0));
        assertTrue(filas.get(2).contains("Declaración ñ; dos.xml"), filas.get(2));

        String registro = Files.readString(dir.resolve("tres.xml.log"));
        assertTrue(registro.contains("Byte Order Mark"), registro);
        assertTrue(registro.contains("Resultado: FIRMADO (OK)"), registro);
        assertFalse(registro.contains(clave), "El registro no lleva la contraseña");
        assertFalse(Files.readString(dir.resolve("uno.xml.log")).contains("Byte Order Mark"));
        // Fuera de las líneas LOTE_ no se imprime detalle de los documentos.
        assertFalse(r.salida().contains("guardado como"), r.salida());
        sinTemporales();
    }

    @Test
    void noReemplazaSalidasYUnRepetidoNoCuentaComoError() throws Exception {
        cod("uno.xml");
        cod("dos.xml");
        Path existente = escribir("uno-signed.xml", "anterior");

        Resultado r = lote(lista("uno.xml", "dos.xml", "dos.xml"), clave, "COD");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("OMITIDO/SALIDA_YA_EXISTE", "FIRMADO/OK", "OMITIDO/DUPLICADO"), estados(r), r.salida());
        assertEquals("anterior", Files.readString(existente));
        assertTrue(resumen(r).contains("\"omitidos=2\""), resumen(r));

        Resultado soloRepetido = lote(lista("tres.xml", "tres.xml"), clave, "COD");
        assertEquals(1, soloRepetido.exitCode(), "El primero no existe");

        cod("cuatro.xml");
        Resultado repetido = lote(lista("cuatro.xml", "cuatro.xml"), clave, "COD");
        assertEquals(0, repetido.exitCode(), repetido.salida());
    }

    @Test
    void unLoteMezcladoNoFirmaNadaNiUsaElCertificado() throws Exception {
        cod("a.xml");
        djo("b.xml");
        cod("c.xml");
        escribir("d.xml", "<doc/>");

        Resultado r = lote(lista("a.xml", "b.xml", "c.xml", "d.xml"), "clave-equivocada", "COD");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("NO_PROCESADO/LOTE_MEZCLADO", "ERROR/TIPO_NO_CORRESPONDE",
                "NO_PROCESADO/LOTE_MEZCLADO", "ERROR/TIPO_NO_CORRESPONDE"), estados(r), r.salida());
        assertTrue(resumen(r).endsWith("\"corte=LOTE_MEZCLADO\""), resumen(r));
        assertTrue(firmados().isEmpty());

        Resultado completo = lote(lista("a.xml", "d.xml"), clave, "");
        assertEquals(List.of("ERROR/TIPO_NO_CORRESPONDE", "NO_PROCESADO/LOTE_MEZCLADO"), estados(completo),
                completo.salida());
        Resultado otroElemento = lote(lista("d.xml", "a.xml"), clave, "doc");
        assertTrue(resumen(otroElemento).endsWith("\"corte=LOTE_MEZCLADO\""), otroElemento.salida());
    }

    @Test
    void losErroresDeUnDocumentoNoFrenanAlResto() throws Exception {
        cod("sinfirma.xml");
        cod("bueno.xml");
        assertEquals(0, lote(lista("bueno.xml"), clave, "COD").exitCode());
        escribir("roto.xml", "<Certificado><COD Id=\"COD\"></Certificado>");
        escribir("texto.txt", "hola");
        Path utf16 = dir.resolve("utf16.xml");
        Files.write(utf16, "<doc/>".getBytes(StandardCharsets.UTF_16));

        Resultado r = lote(lista("noesta.xml", "roto.xml", "texto.txt", "utf16.xml", "sinfirma.xml",
                "bueno-signed.xml", "con\ttabulador.xml"), clave, "CODEH");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("ERROR/ARCHIVO_NO_ENCONTRADO", "ERROR/DOCUMENTO_MAL_FORMADO",
                "ERROR/EXTENSION_NO_CORRESPONDE", "ERROR/DOCUMENTO_MAL_FORMADO", "ERROR/FALTA_FIRMA_PREVIA",
                "FIRMADO/OK", "ERROR/LINEA_NO_VALIDA"), estados(r), r.salida());
        // La segunda firma deja el sufijo dos veces y el documento resultante es válido.
        Path doble = dir.resolve("bueno-signed-signed.xml");
        assertTrue(Files.exists(doble));
        assertEquals(0, ejecutarModulo(dir, VERIFICADOR, doble.toString(), "-simple").exitCode());

        Resultado yaFirmado = lote(lista("bueno-signed-signed.xml"), clave, "COD");
        assertEquals(List.of("ERROR/ELEMENTO_YA_FIRMADO"), estados(yaFirmado), yaFirmado.salida());
        Resultado sinElemento = lote(lista("sinfirma.xml"), clave, "NoEsta");
        assertEquals(List.of("ERROR/ELEMENTO_NO_ENCONTRADO"), estados(sinElemento), sinElemento.salida());
    }

    @Test
    void unaContrasenaIncorrectaCortaTodoElLote() throws Exception {
        cod("a.xml");
        cod("b.xml");

        Resultado r = lote(lista("a.xml", "noesta.xml", "b.xml"), "equivocada", "COD");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("NO_PROCESADO/CONTRASENA", "ERROR/ARCHIVO_NO_ENCONTRADO", "NO_PROCESADO/CONTRASENA"),
                estados(r), r.salida());
        assertTrue(resumen(r).endsWith("\"corte=CONTRASENA\""), resumen(r));
        assertTrue(firmados().isEmpty());
    }

    @Test
    void unCertificadoVencidoCortaElLote() throws Exception {
        Path vencido = dir.resolve("vencido.p12");
        Resultado keytool = ProcesoSupport.ejecutar(List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias", "v",
                "-keyalg", "RSA", "-keysize", "2048", "-startdate", "-30d", "-validity", "2", "-keystore",
                vencido.toString(), "-storetype", "PKCS12", "-storepass", "secreta1", "-keypass", "secreta1",
                "-dname", "CN=Vencido"), dir);
        assertEquals(0, keytool.exitCode(), keytool.salida());
        cod("a.xml");

        Resultado r = ejecutarModulo(dir, FIRMADOR, "-lote", lista("a.xml").toString(), vencido.toString(),
                "secreta1", "COD");

        assertEquals(1, r.exitCode(), r.salida());
        assertEquals(List.of("NO_PROCESADO/CERTIFICADO_NO_APTO"), estados(r), r.salida());
        assertTrue(firmados().isEmpty());
    }

    @Test
    void revisarNoFirmaNiAccedeAlCertificado() throws Exception {
        cod("a.xml");
        cod("b.xml");
        escribir("b-signed.xml", "anterior");

        Resultado r = lote(lista("a.xml"), "clave-que-no-se-usa", "COD", "-revisar", "-log");
        assertEquals(0, r.exitCode(), r.salida());
        assertEquals(List.of("LISTO/OK"), estados(r), r.salida());

        Resultado conOmitido = lote(lista("a.xml", "b.xml"), "x", "COD", "-revisar");
        assertEquals(1, conOmitido.exitCode(), conOmitido.salida());
        assertEquals(List.of("LISTO/OK", "OMITIDO/SALIDA_YA_EXISTE"), estados(conOmitido), conOmitido.salida());

        assertEquals(List.of("b-signed.xml"), firmados());
        assertFalse(Files.exists(dir.resolve("a.xml.log")));
    }

    @Test
    void revisarSinLoteNuncaFirma() throws Exception {
        Path a = cod("a.xml");
        String p12 = dir.resolve("prueba.p12").toString();

        assertEquals(1, ejecutarModulo(dir, FIRMADOR, p12, clave, a.toString(), "COD", "-revisar").exitCode());
        assertEquals(1, ejecutarModulo(dir, FIRMADOR, "-revisar", p12, clave, a.toString(), "COD").exitCode());
        assertTrue(firmados().isEmpty());
    }

    @Test
    void losArgumentosEnLoteSeControlanAntesDeHacerNada() throws Exception {
        cod("a.xml");
        Path lista = lista("a.xml");

        for (String[] opciones : List.of(new String[]{"-desconocido"}, new String[]{"-log", "-log"},
                new String[]{"-informe"}, new String[]{"-omitir-revocacion", "quizas"}, new String[]{"-omitir-revocacio", "true"})) {
            Resultado r = lote(lista, clave, "COD", opciones);
            assertEquals(1, r.exitCode(), r.salida());
            assertTrue(r.salida().contains("LOTE_INICIO;\"1\";\"XMLSignerPKCS12\""), r.salida());
            assertTrue(resumen(r).endsWith("\"corte=ARGUMENTOS\""), r.salida());
        }
        assertTrue(resumen(ejecutarModulo(dir, FIRMADOR, "-lote")).endsWith("\"corte=ARGUMENTOS\""));

        Resultado informeXml = lote(lista, clave, "COD", "-informe", dir.resolve("a.xml").toString());
        assertTrue(resumen(informeXml).endsWith("\"corte=INFORME_NO_CREADO\""), informeXml.salida());
        Resultado informeLista = lote(lista, clave, "COD", "-informe", lista.toString());
        assertTrue(resumen(informeLista).endsWith("\"corte=INFORME_NO_CREADO\""), informeLista.salida());
        assertTrue(Files.readString(lista).contains("a.xml"), "La lista no se pisa");
        assertTrue(firmados().isEmpty());
    }

    @Test
    void unaListaIlegibleOVaciaSeRechaza() throws Exception {
        cod("a.xml");
        Path utf16 = dir.resolve("utf16.txt");
        Files.write(utf16, "a.xml\r\n".getBytes(StandardCharsets.UTF_16LE));
        Path ansi = dir.resolve("ansi.txt");
        Files.write(ansi, "añadido.xml\r\n".getBytes(StandardCharsets.ISO_8859_1));

        for (Path mala : List.of(utf16, ansi, dir.resolve("noesta.txt"))) {
            Resultado r = lote(mala, clave, "COD");
            assertEquals(1, r.exitCode(), r.salida());
            assertTrue(resumen(r).endsWith("\"corte=LISTA_ILEGIBLE\""), r.salida());
        }
        Resultado vacia = lote(escribir("vacia.txt", "\r\n  \r\n"), clave, "COD");
        assertTrue(resumen(vacia).endsWith("\"corte=LISTA_VACIA\""), vacia.salida());
    }

    /** Lo que se puede comprobar del firmador con token sin tener un token conectado. */
    @Test
    void firmadorConTokenSinTokenConectado() throws Exception {
        String conToken = "com.sauken.s_fide.xml_signer_pkcs11.XMLSignerPKCS11";
        cod("a.xml");
        djo("b.xml");
        String lista = lista("a.xml").toString();
        String inexistente = dir.resolve("noesta.dll").toString();

        Resultado revision = ejecutarModulo(dir, conToken, "-lote", lista, inexistente, "1234", "0", "COD", "-revisar");
        assertEquals(0, revision.exitCode(), "Revisar no usa la biblioteca: " + revision.salida());
        assertEquals(List.of("LISTO/OK"), estados(revision), revision.salida());

        Resultado sinBiblioteca = ejecutarModulo(dir, conToken, "-lote", lista, inexistente, "1234", "0", "COD");
        assertEquals(1, sinBiblioteca.exitCode(), sinBiblioteca.salida());
        assertEquals(List.of("NO_PROCESADO/BIBLIOTECA"), estados(sinBiblioteca), sinBiblioteca.salida());

        Resultado noEsBiblioteca = ejecutarModulo(dir, conToken, "-lote", lista,
                dir.resolve("a.xml").toString(), "1234", "0", "COD");
        assertEquals(1, noEsBiblioteca.exitCode(), noEsBiblioteca.salida());
        assertEquals(1, documentos(noEsBiblioteca).size(), noEsBiblioteca.salida());
        assertTrue(estados(noEsBiblioteca).get(0).startsWith("NO_PROCESADO/"), noEsBiblioteca.salida());

        Resultado mezcla = ejecutarModulo(dir, conToken, "-lote", lista("a.xml", "b.xml").toString(), inexistente,
                "1234", "0", "COD");
        assertTrue(resumen(mezcla).endsWith("\"corte=LOTE_MEZCLADO\""), mezcla.salida());

        Resultado slot = ejecutarModulo(dir, conToken, "-lote", lista, inexistente, "1234", "cero", "COD");
        assertTrue(resumen(slot).endsWith("\"corte=ARGUMENTOS\""), slot.salida());
        assertTrue(firmados().isEmpty());
    }
}
