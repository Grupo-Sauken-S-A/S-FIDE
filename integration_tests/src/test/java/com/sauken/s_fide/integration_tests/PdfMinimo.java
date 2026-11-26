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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Un PDF válido de dos páginas A4, armado a mano para las pruebas. */
final class PdfMinimo {

    private PdfMinimo() {
    }

    static String contenido() {
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
        return texto.toString();
    }
}
