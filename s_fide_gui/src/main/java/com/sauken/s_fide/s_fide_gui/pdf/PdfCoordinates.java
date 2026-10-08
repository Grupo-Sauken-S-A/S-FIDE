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

/**
 * Conversión entre lo que la persona ve en pantalla y las coordenadas que entienden los firmadores.
 * <p>
 * Los firmadores reciben X, Y, ancho y alto en <b>puntos PDF</b> (1/72 de pulgada) con el origen en la
 * esquina inferior izquierda del área visible de la página, sobre la página <i>sin girar</i>. En pantalla
 * la página se ve ya girada (si el PDF trae una rotación), con el origen arriba a la izquierda y el eje
 * vertical hacia abajo. Esta clase hace esa traducción en ambos sentidos, sin depender de ninguna
 * biblioteca de PDF ni de JavaFX, para poder probarla sola.
 */
public final class PdfCoordinates {

    private PdfCoordinates() {
    }

    /** Área visible de una página: posición y tamaño en puntos PDF, y rotación (0, 90, 180 o 270). */
    public record Pagina(double x, double y, double ancho, double alto, int rotacion) {

        public Pagina {
            rotacion = ((rotacion % 360) + 360) % 360;
        }

        /** Ancho de la página tal como se ve en pantalla (ya girada). */
        public double anchoEnPantalla() {
            return rotacion == 90 || rotacion == 270 ? alto : ancho;
        }

        /** Alto de la página tal como se ve en pantalla (ya girada). */
        public double altoEnPantalla() {
            return rotacion == 90 || rotacion == 270 ? ancho : alto;
        }
    }

    /** Rectángulo con esquina (x, y) y tamaño (ancho, alto); el sistema de ejes depende de quién lo use. */
    public record Rectangulo(double x, double y, double ancho, double alto) {
    }

    /** De un punto del PDF (puntos, origen abajo a la izquierda) a pantalla (puntos, origen arriba a la izquierda). */
    public static double[] aPantalla(Pagina p, double ux, double uy) {
        double rx = ux - p.x();
        double ry = uy - p.y();
        return switch (p.rotacion()) {
            case 90 -> new double[]{ry, rx};
            case 180 -> new double[]{p.ancho() - rx, ry};
            case 270 -> new double[]{p.alto() - ry, p.ancho() - rx};
            default -> new double[]{rx, p.alto() - ry};
        };
    }

    /** De un punto de pantalla (puntos, origen arriba a la izquierda) al PDF (puntos, origen abajo a la izquierda). */
    public static double[] aPdf(Pagina p, double dx, double dy) {
        double rx;
        double ry;
        switch (p.rotacion()) {
            case 90 -> {
                rx = dy;
                ry = dx;
            }
            case 180 -> {
                rx = p.ancho() - dx;
                ry = dy;
            }
            case 270 -> {
                rx = p.ancho() - dy;
                ry = p.alto() - dx;
            }
            default -> {
                rx = dx;
                ry = p.alto() - dy;
            }
        }
        return new double[]{p.x() + rx, p.y() + ry};
    }

    /** Rectángulo del PDF a rectángulo en pantalla (el más chico que lo contiene si la página está girada). */
    public static Rectangulo rectanguloAPantalla(Pagina p, Rectangulo pdf) {
        double[] a = aPantalla(p, pdf.x(), pdf.y());
        double[] b = aPantalla(p, pdf.x() + pdf.ancho(), pdf.y() + pdf.alto());
        return normalizar(a, b);
    }

    /** Rectángulo de pantalla a rectángulo del PDF. */
    public static Rectangulo rectanguloAPdf(Pagina p, Rectangulo pantalla) {
        double[] a = aPdf(p, pantalla.x(), pantalla.y());
        double[] b = aPdf(p, pantalla.x() + pantalla.ancho(), pantalla.y() + pantalla.alto());
        return normalizar(a, b);
    }

    private static Rectangulo normalizar(double[] a, double[] b) {
        return new Rectangulo(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
    }
}
