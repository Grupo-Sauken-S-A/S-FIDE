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


package com.sauken.s_fide.s_fide_gui.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los textos de las confirmaciones: claros, sin jerga, y sin mencionar lo que no es un problema. */
class PdfPreflightTest {

    private static PdfAnalysisReport informe(String... lineas) {
        return PdfAnalysisReport.parse(List.of(lineas));
    }

    @Test
    void elTextoDeProblemasSoloListaLoQueEsRealmenteInvalido() {
        PdfAnalysisReport r = informe(
                "ESTADO_DOCUMENTO: ABIERTO",
                "FIRMA: 1 | VALIDA | Ana",
                "MENSAJE: La firma de Ana es válida.",
                "NOTA: El certificado de Ana venció después de firmar.",
                "FIRMA: 2 | INVALIDA | Luis",
                "MENSAJE: La firma de Luis NO es válida: el documento fue modificado después de que Luis lo firmara.",
                "RECOMENDACION: Le recomendamos consultar con quien le envió el documento antes de firmar.");

        String texto = PdfPreflight.textoDeProblemas(r);

        assertTrue(texto.contains("La firma de Luis NO es válida"), texto);
        assertTrue(texto.contains("Le recomendamos consultar"), texto);
        assertFalse(texto.contains("Ana"), "Una firma válida no debe aparecer entre los problemas:\n" + texto);
        assertFalse(texto.contains("venció"), texto);
    }

    @Test
    void elTextoDeCierreExplicaQueSeraLaFirmaFinal() {
        PdfAnalysisReport conFirmas = informe("ESTADO_DOCUMENTO: ABIERTO", "FIRMA: 1 | VALIDA | Ana", "MENSAJE: ok");

        String texto = PdfPreflight.textoDeCierre(conFirmas);

        assertTrue(texto.contains("no se podrán agregar más firmas"), texto);
        assertTrue(texto.contains("desmarque"), "Debe indicar cómo evitarlo: " + texto);
    }

    @Test
    void elTextoDeBloqueoSinFirmasOfreceProtegerElContenido() {
        PdfAnalysisReport sinFirmas = informe("ESTADO_DOCUMENTO: ABIERTO");

        String texto = PdfPreflight.textoDeCierre(sinFirmas);

        assertTrue(texto.contains("nadie más podrá agregarle firmas"), texto);
        assertTrue(texto.contains("Proteger el contenido"), texto);
    }

    @Test
    void ningunTextoUsaJergaTecnica() {
        PdfAnalysisReport r = informe("ESTADO_DOCUMENTO: ABIERTO", "FIRMA: 1 | INVALIDA | Luis",
                "MENSAJE: La firma de Luis NO es válida.", "RECOMENDACION: Consulte.");
        String todo = PdfPreflight.textoDeProblemas(r) + PdfPreflight.textoDeCierre(r);
        for (String jerga : List.of("OCSP", "CRL", "PKCS", "DocMDP", "ByteRange", "Exception")) {
            assertFalse(todo.contains(jerga), jerga);
        }
    }
}
