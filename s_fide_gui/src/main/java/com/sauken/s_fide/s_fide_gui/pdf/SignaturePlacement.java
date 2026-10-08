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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dónde y con qué tamaño se dibuja la firma visible, en los mismos términos que los firmadores: página
 * (desde 1) y recuadro en puntos PDF. Si {@code campo} no es nulo, se firma dentro de ese campo de firma
 * que el documento ya trae preparado, y la página y el recuadro los define el campo.
 */
public record SignaturePlacement(int pagina, double x, double y, double ancho, double alto, String campo) {

    /** Posición clásica de S-FiDE: abajo a la izquierda de la primera página, 160 x 70 puntos. */
    public static SignaturePlacement porDefecto() {
        return new SignaturePlacement(1, 40, 55, 160, 70, null);
    }

    public boolean esEnCampoExistente() {
        return campo != null && !campo.isBlank();
    }

    /** Argumentos de línea de comandos para el firmador. */
    public List<String> argumentos() {
        List<String> args = new ArrayList<>();
        if (esEnCampoExistente()) {
            args.add("-campo");
            args.add(campo);
            return args;
        }
        args.add("-x");
        args.add(numero(x));
        args.add("-y");
        args.add(numero(y));
        args.add("-pagina");
        args.add(String.valueOf(pagina));
        args.add("-ancho");
        args.add(numero(ancho));
        args.add("-alto");
        args.add(numero(alto));
        return args;
    }

    /** Descripción corta para mostrar en pantalla. */
    public String resumen() {
        if (esEnCampoExistente()) {
            return "En el campo de firma \"" + campo + "\" que ya trae el documento";
        }
        return "Página " + pagina + " · X " + numero(x) + " · Y " + numero(y) + " · "
                + numero(ancho) + " × " + numero(alto) + " puntos";
    }

    /** Un decimal como mucho, sin ceros de más y siempre con punto: es lo que entienden los firmadores. */
    static String numero(double valor) {
        double redondeado = Math.round(valor * 10.0) / 10.0;
        if (redondeado == Math.rint(redondeado)) {
            return String.valueOf((long) redondeado);
        }
        return String.format(Locale.ROOT, "%.1f", redondeado);
    }
}
