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

import com.sauken.s_fide.s_fide_gui.pdf.PdfCoordinates.Pagina;
import com.sauken.s_fide.s_fide_gui.pdf.PdfCoordinates.Rectangulo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfCoordinatesTest {

    private static final double EPS = 1e-9;

    private static void assertRect(Rectangulo esperado, Rectangulo real) {
        assertEquals(esperado.x(), real.x(), EPS, "x");
        assertEquals(esperado.y(), real.y(), EPS, "y");
        assertEquals(esperado.ancho(), real.ancho(), EPS, "ancho");
        assertEquals(esperado.alto(), real.alto(), EPS, "alto");
    }

    @Test
    void enUnaPaginaA4SinGirarElOrigenDelPdfEstaAbajoYLaPantallaArriba() {
        Pagina a4 = new Pagina(0, 0, 595, 842, 0);
        // Posición clásica de S-FiDE: X=40, Y=55, 160 x 70 -> en pantalla queda abajo a la izquierda.
        Rectangulo enPantalla = PdfCoordinates.rectanguloAPantalla(a4, new Rectangulo(40, 55, 160, 70));
        assertRect(new Rectangulo(40, 842 - 55 - 70, 160, 70), enPantalla);
    }

    @Test
    void ladoSuperiorDeLaPaginaEnPantallaEsYCeroYEnPdfEsElAlto() {
        Pagina a4 = new Pagina(0, 0, 595, 842, 0);
        assertEquals(842, PdfCoordinates.aPdf(a4, 0, 0)[1], EPS);
        assertEquals(0, PdfCoordinates.aPdf(a4, 0, 842)[1], EPS);
    }

    @Test
    void elAreaVisibleConOrigenDistintoDeCeroSeRespeta() {
        // Páginas con CropBox que no empieza en (0,0) son frecuentes en PDF generados por imprentas.
        Pagina recortada = new Pagina(10, 20, 500, 700, 0);
        double[] pdf = PdfCoordinates.aPdf(recortada, 0, 0);
        assertEquals(10, pdf[0], EPS);
        assertEquals(720, pdf[1], EPS);
        double[] pantalla = PdfCoordinates.aPantalla(recortada, 10, 20);
        assertEquals(0, pantalla[0], EPS);
        assertEquals(700, pantalla[1], EPS);
    }

    @Test
    void ida_y_vuelta_conservan_el_rectangulo_en_las_cuatro_rotaciones() {
        for (int rotacion : new int[]{0, 90, 180, 270}) {
            Pagina pagina = new Pagina(7, 13, 595, 842, rotacion);
            Rectangulo original = new Rectangulo(40, 55, 160, 70);
            Rectangulo enPantalla = PdfCoordinates.rectanguloAPantalla(pagina, original);
            assertRect(original, PdfCoordinates.rectanguloAPdf(pagina, enPantalla));
        }
    }

    @Test
    void conPaginaGiradaNoventaGradosElAnchoYElAltoSeIntercambianEnPantalla() {
        Pagina girada = new Pagina(0, 0, 595, 842, 90);
        assertEquals(842, girada.anchoEnPantalla(), EPS);
        assertEquals(595, girada.altoEnPantalla(), EPS);

        Rectangulo enPantalla = PdfCoordinates.rectanguloAPantalla(girada, new Rectangulo(40, 55, 160, 70));
        assertEquals(70, enPantalla.ancho(), EPS);
        assertEquals(160, enPantalla.alto(), EPS);
    }

    @Test
    void laEsquinaInferiorIzquierdaDelPdfSeVeArribaAIzquierdaConNoventaGrados() {
        Pagina girada = new Pagina(0, 0, 595, 842, 90);
        double[] p = PdfCoordinates.aPantalla(girada, 0, 0);
        assertEquals(0, p[0], EPS);
        assertEquals(0, p[1], EPS);
    }

    @Test
    void laRotacionSeNormaliza() {
        assertEquals(270, new Pagina(0, 0, 10, 10, -90).rotacion());
        assertEquals(90, new Pagina(0, 0, 10, 10, 450).rotacion());
    }
}
