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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfAnalysisReportTest {

    @Test
    void interpretaUnDocumentoAbiertoConUnaFirmaYUnaNota() {
        List<String> salida = List.of(
                "SLF4J(W): ruido de arranque que no es parte del informe",
                "ESTADO_DOCUMENTO: ABIERTO",
                "CANTIDAD_FIRMAS: 1",
                "FIRMA: 1 | VALIDA | Ana Pérez",
                "MENSAJE: La firma de Ana Pérez es válida.",
                "REVOCACION: No figura revocado (consulta OCSP)",
                "CAMPO: Signature_Ana_1",
                "NOTA: Su certificado venció, pero estaba vigente cuando firmó.",
                "PAGINAS: 3");

        PdfAnalysisReport r = PdfAnalysisReport.parse(salida);

        assertNotNull(r);
        assertFalse(r.cerrado());
        assertEquals(3, r.paginas());
        assertEquals(1, r.firmas().size());
        PdfAnalysisReport.Firma f = r.firmas().get(0);
        assertEquals("Ana Pérez", f.firmante());
        assertEquals("VALIDA", f.veredicto());
        assertEquals("Signature_Ana_1", f.campo());
        assertEquals(1, f.notas().size());
        assertEquals("No figura revocado (consulta OCSP)", f.revocacion());
        assertFalse(r.hayProblemas());
    }

    @Test
    void unDocumentoCerradoTraeSuMotivo() {
        PdfAnalysisReport r = PdfAnalysisReport.parse(List.of(
                "ESTADO_DOCUMENTO: CERRADO",
                "MOTIVO_CIERRE: El documento fue bloqueado por Ana y no admite más firmas.",
                "CANTIDAD_FIRMAS: 0"));

        assertTrue(r.cerrado());
        assertEquals("El documento fue bloqueado por Ana y no admite más firmas.", r.motivoCierre());
    }

    @Test
    void unaFirmaInvalidaYLaRecomendacionSonUnProblema() {
        PdfAnalysisReport r = PdfAnalysisReport.parse(List.of(
                "ESTADO_DOCUMENTO: ABIERTO",
                "FIRMA: 1 | INVALIDA | Luis",
                "MENSAJE: La firma de Luis NO es válida: el documento fue modificado después de que Luis lo firmara.",
                "RECOMENDACION: Le recomendamos consultar con quien le envió el documento antes de firmar."));

        assertTrue(r.hayProblemas());
        assertTrue(r.firmas().get(0).esInvalida());
    }

    @Test
    void losSellosDeTiempoNoCuentanComoFirmasDePersonas() {
        PdfAnalysisReport r = PdfAnalysisReport.parse(List.of(
                "ESTADO_DOCUMENTO: ABIERTO",
                "FIRMA: 1 | VALIDA | Ana",
                "MENSAJE: ok",
                "SELLO: 2 | VALIDA | sello de tiempo",
                "MENSAJE: El documento tiene un sello de tiempo."));

        assertEquals(2, r.firmas().size());
        assertEquals(1, r.cantidadDeFirmasDePersonas());
        assertTrue(r.firmas().get(1).esSello());
    }

    @Test
    void losCamposVaciosYLaProteccionDelContenidoSeLeen() {
        PdfAnalysisReport r = PdfAnalysisReport.parse(List.of(
                "ESTADO_DOCUMENTO: ABIERTO",
                "PAGINAS: 2",
                "CONTENIDO_PROTEGIDO: SI",
                "CAMPO_VACIO: FirmaExportador | 1",
                "CAMPO_VACIO: FirmaFuncionario | 2"));

        assertTrue(r.contenidoProtegido());
        assertEquals(2, r.camposVacios().size());
        assertEquals("FirmaFuncionario", r.camposVacios().get(1).nombre());
        assertEquals(2, r.camposVacios().get(1).pagina());
    }

    @Test
    void lasNotasYLosAvisosDelDocumentoSeDistinguen() {
        PdfAnalysisReport r = PdfAnalysisReport.parse(List.of(
                "ESTADO_DOCUMENTO: ABIERTO",
                "NOTA_DOCUMENTO: Después de la última firma se agregaron comentarios.",
                "AVISO_DOCUMENTO: No pudimos comprobar qué cambios se hicieron."));

        assertEquals("Después de la última firma se agregaron comentarios.", r.notaDocumento());
        assertEquals("No pudimos comprobar qué cambios se hicieron.", r.avisoDocumento());
        assertFalse(r.hayProblemas(), "Un aviso suave no es un problema");
    }

    @Test
    void unaSalidaQueNoEsUnInformeDevuelveNulo() {
        assertNull(PdfAnalysisReport.parse(List.of("Error: El documento no se pudo leer.")));
    }

    @Test
    void elNombreDelFirmanteConBarraVerticalNoRompeLaLectura() {
        PdfAnalysisReport r = PdfAnalysisReport.parse(List.of(
                "ESTADO_DOCUMENTO: ABIERTO",
                "FIRMA: 1 | VALIDA | Pérez | Gómez S.A."));

        assertEquals("Pérez | Gómez S.A.", r.firmas().get(0).firmante());
    }
}
