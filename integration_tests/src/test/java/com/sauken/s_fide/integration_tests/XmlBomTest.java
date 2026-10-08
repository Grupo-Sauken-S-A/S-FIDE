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

import com.sauken.s_fide.integration_tests.ProcesoSupport.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sauken.s_fide.integration_tests.ProcesoSupport.ejecutarModulo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un XML con la marca BOM de UTF-8 (tres bytes invisibles que agrega, por ejemplo, el Bloc de notas de Windows)
 * antes fallaba con "Content is not allowed in prolog". Ahora los firmadores y los verificadores la ignoran,
 * lo informan en lenguaje simple y el documento firmado se genera sin ella; el archivo original no se toca.
 * Se prueba contra los módulos reales, como proceso, igual que {@link Pkcs12RoundTripTest}.
 */
class XmlBomTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String FIRMADOR = "com.sauken.s_fide.xml_signer_pkcs12.XMLSignerPKCS12";
    private static final String VERIFICADOR = "com.sauken.s_fide.xml_verify_signatures.XMLVerifySignatures";

    /** XML compacto (sin saltos de línea): COD y CODEH son hermanos y CODEH es el último de su padre. */
    private static final String COD_COMPACTO = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Certificado><COD Id=\"COD\"><Pais>AR</Pais><Nombre>Ñandú</Nombre></COD>"
            + "<CODEH Id=\"CODEH\"><Pais>BR</Pais></CODEH></Certificado>";

    @TempDir
    Path tempDir;

    private Path p12;
    private String clave;

    @BeforeEach
    void certificado() throws Exception {
        p12 = tempDir.resolve("prueba.p12");
        clave = ProcesoSupport.generarP12(tempDir, p12, "S-FiDE BOM Test");
    }

    private Path escribir(String nombre, byte[] prefijo, String xml) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(prefijo);
        bytes.write(xml.getBytes(StandardCharsets.UTF_8));
        Path archivo = tempDir.resolve(nombre);
        Files.write(archivo, bytes.toByteArray());
        return archivo;
    }

    private Resultado firmar(Path xml, String uri) throws Exception {
        return ejecutarModulo(tempDir, FIRMADOR, p12.toString(), clave, xml.toString(), uri);
    }

    private static boolean empiezaConBom(Path archivo) throws IOException {
        byte[] b = Files.readAllBytes(archivo);
        return b.length >= 3 && b[0] == BOM[0] && b[1] == BOM[1] && b[2] == BOM[2];
    }

    @Test
    void firmarUnXmlConBomLoInformaYGeneraElFirmadoSinBom() throws Exception {
        Path original = escribir("conbom.xml", BOM, COD_COMPACTO);
        byte[] antes = Files.readAllBytes(original);

        Resultado r = firmar(original, "COD");

        assertEquals(0, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("BOM"), "Debe informar la marca BOM: " + r.salida());
        assertTrue(r.salida().contains("Byte Order Mark"), "Debe explicar qué es el BOM: " + r.salida());
        assertFalse(r.salida().contains("prolog"), "No debe mostrar el error técnico: " + r.salida());
        Path firmado = tempDir.resolve("conbom-signed.xml");
        assertTrue(Files.exists(firmado));
        assertFalse(empiezaConBom(firmado), "El documento firmado no debe llevar BOM");
        assertArrayEquals(antes, Files.readAllBytes(original), "El archivo original no se debe modificar");
        assertEquals(0, ejecutarModulo(tempDir, VERIFICADOR, firmado.toString()).exitCode());
    }

    @Test
    void unXmlSinBomNoMuestraNingunAviso() throws Exception {
        Path original = escribir("sinbom.xml", new byte[0], COD_COMPACTO);

        Resultado r = firmar(original, "COD");

        assertEquals(0, r.exitCode(), r.salida());
        assertFalse(r.salida().contains("BOM"), r.salida());
    }

    @Test
    void laMarcaBomNoAfectaLasFirmasQueElDocumentoYaTenia() throws Exception {
        Path sinBom = escribir("doc.xml", new byte[0], COD_COMPACTO);
        assertEquals(0, firmar(sinBom, "COD").exitCode());
        Path firmado = tempDir.resolve("doc-signed.xml");

        // El mismo documento firmado, pero ahora con BOM por delante (como lo dejaría un editor).
        Path conBom = escribir("firmado-conbom.xml", BOM, Files.readString(firmado, StandardCharsets.UTF_8));

        Resultado verificacion = ejecutarModulo(tempDir, VERIFICADOR, conBom.toString());
        assertEquals(0, verificacion.exitCode(), verificacion.salida());
        assertTrue(verificacion.salida().contains("BOM"), verificacion.salida());
        assertTrue(verificacion.salida().contains("DOCUMENTO VÁLIDO"), verificacion.salida());

        // Y se puede agregar la segunda firma (CODEH, último elemento del padre) sobre ese mismo archivo.
        Resultado segunda = firmar(conBom, "CODEH");
        assertEquals(0, segunda.exitCode(), segunda.salida());
        Path firmadoDos = tempDir.resolve("firmado-conbom-signed.xml");
        assertFalse(empiezaConBom(firmadoDos));
        Resultado verificacionDos = ejecutarModulo(tempDir, VERIFICADOR, firmadoDos.toString());
        assertEquals(0, verificacionDos.exitCode(), verificacionDos.salida());
        assertTrue(verificacionDos.salida().contains("Estado final de la firma #2: VÁLIDA"), verificacionDos.salida());
    }

    @Test
    void unXmlEnUtf16SeRechazaConUnMensajeClaro() throws Exception {
        Path utf16 = tempDir.resolve("utf16.xml");
        Files.write(utf16, COD_COMPACTO.getBytes(StandardCharsets.UTF_16));

        Resultado r = firmar(utf16, "COD");

        assertEquals(1, r.exitCode(), r.salida());
        assertTrue(r.salida().contains("UTF-16"), r.salida());
        assertFalse(r.salida().contains("prolog"), r.salida());
    }
}
