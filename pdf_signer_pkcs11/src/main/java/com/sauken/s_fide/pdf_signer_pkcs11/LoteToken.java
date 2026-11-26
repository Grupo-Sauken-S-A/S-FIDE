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

package com.sauken.s_fide.pdf_signer_pkcs11;

/**
 * Clasifica las fallas de acceso a un token durante un lote: con qué causa se corta, y cuándo una falla al
 * firmar significa que se perdió el token y no tiene sentido seguir (ni arriesgar intentos de PIN).
 * <p>
 * Esta clase está duplicada a propósito en los firmadores de token (cada jar debe seguir siendo
 * independiente): si se modifica, hay que actualizar las dos copias (solo cambia la línea {@code package}).
 */
final class LoteToken {

    private LoteToken() {
    }

    /** El corte de lote que corresponde a una falla de acceso al token, con el mensaje ya redactado. */
    static Lote.Corte corte(Throwable error, String biblioteca, String mensaje) {
        String todo = cadenaDeCausas(error) + mensaje;
        String causa;
        if (contiene(todo, "CKR_PIN_LOCKED", "bloqueado por demasiados intentos")) {
            causa = "TOKEN_BLOQUEADO";
        } else if (contiene(todo, "CKR_PIN_INCORRECT", "FailedLoginException", "CKR_PIN_EXPIRED",
                "CKR_PIN_LEN_RANGE", "Contraseña (PIN) incorrecta")) {
            causa = "CONTRASENA";
        } else {
            causa = "TOKEN_NO_DISPONIBLE";
        }
        return new Lote.Corte(causa, mensaje);
    }

    /** Si la falla indica que el token ya no está disponible o que la sesión se perdió. */
    static boolean perdioElAcceso(Throwable error) {
        return contiene(cadenaDeCausas(error), "CKR_TOKEN_NOT_PRESENT", "CKR_DEVICE_REMOVED",
                "CKR_TOKEN_NOT_RECOGNIZED", "CKR_DEVICE_ERROR", "CKR_SESSION_HANDLE_INVALID", "CKR_SESSION_CLOSED",
                "CKR_USER_NOT_LOGGED_IN", "CKR_PIN_INCORRECT", "CKR_PIN_LOCKED", "CKR_PIN_EXPIRED",
                "CKR_FUNCTION_CANCELED", "CKR_CRYPTOKI_NOT_INITIALIZED");
    }

    private static String cadenaDeCausas(Throwable error) {
        StringBuilder todo = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            todo.append(t.getClass().getName()).append(' ').append(t.getMessage()).append('\n');
        }
        return todo.toString();
    }

    private static boolean contiene(String texto, String... marcas) {
        for (String marca : marcas) {
            if (texto.contains(marca)) {
                return true;
            }
        }
        return false;
    }
}
