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


package com.sauken.s_fide.s_fide_gui.utils;

import javafx.scene.control.Dialog;
import javafx.stage.Window;

/**
 * Garantiza que ningún cuadro de diálogo quede huérfano: sin ventana propietaria, un diálogo es una ventana
 * suelta que puede quedar escondida detrás de otra, o seguir abierta cuando la ventana desde la que se lo
 * llamó ya se cerró. Con propietario, el sistema lo mantiene delante de esa ventana y lo cierra con ella.
 */
public final class DialogOwner {

    private DialogOwner() {
    }

    /**
     * Si el diálogo todavía no tiene propietario, le asigna la ventana que tiene el foco (o, si ninguna lo
     * tiene, la última ventana visible). Un diálogo que ya tiene propietario no se toca. Hay que llamarlo
     * antes de mostrarlo.
     */
    public static <D extends Dialog<?>> D conPropietario(D dialogo) {
        if (dialogo.getOwner() == null && !dialogo.isShowing()) {
            Window elegida = null;
            for (Window ventana : Window.getWindows()) {
                if (ventana.isShowing()) {
                    if (ventana.isFocused()) {
                        elegida = ventana;
                        break;
                    }
                    elegida = ventana;
                }
            }
            if (elegida != null) {
                dialogo.initOwner(elegida);
            }
        }
        return dialogo;
    }
}
