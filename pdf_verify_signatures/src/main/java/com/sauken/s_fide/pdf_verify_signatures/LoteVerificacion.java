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

package com.sauken.s_fide.pdf_verify_signatures;

/**
 * Argumentos de un lote de verificación o de validación:
 * {@code -lote <lista> [<esquema>] [-simple] [-informe <csv>] [-log]}. Se controlan estrictamente.
 * <p>
 * Esta clase está duplicada a propósito en los programas de verificación que admiten lote (cada jar debe
 * seguir siendo independiente): si se modifica, hay que actualizar todas las copias (solo cambia la línea
 * {@code package}).
 */
final class LoteVerificacion {

    /** @param esquema archivo XSD indicado para todo el lote, o {@code null} */
    record Pedido(Lote.Opciones opciones, boolean simple, String esquema) {
    }

    private LoteVerificacion() {
    }

    /** @throws IllegalArgumentException con el motivo, en español */
    static Pedido analizarArgumentos(String[] args, boolean admiteSimple, boolean admiteEsquema) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Falta el archivo de lista después de -lote.");
        }
        String informe = null;
        String esquema = null;
        boolean registro = false;
        boolean simple = false;
        int i = 2;
        if (admiteEsquema && args.length > 2 && !args[2].startsWith("-")) {
            esquema = args[2];
            i = 3;
        }
        for (; i < args.length; i++) {
            String opcion = args[i];
            if (Lote.esOpcionRegistro(opcion) && !registro) {
                registro = true;
            } else if (Lote.esOpcionInforme(opcion) && informe == null && i + 1 < args.length) {
                informe = args[++i];
            } else if (admiteSimple && "-simple".equalsIgnoreCase(opcion) && !simple) {
                simple = true;
            } else {
                throw new IllegalArgumentException("Argumento no reconocido, repetido o incompleto: " + opcion);
            }
        }
        return new Pedido(new Lote.Opciones(args[1], informe, registro, false), simple, esquema);
    }
}
