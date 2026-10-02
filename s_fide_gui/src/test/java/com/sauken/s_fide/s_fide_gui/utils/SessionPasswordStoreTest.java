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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sauken.s_fide.s_fide_gui.utils.SessionPasswordStore.Kind;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionPasswordStoreTest {

    private static final String LIB = "C:\\Windows\\System32\\eTPKCS11.dll";

    @Test
    void recuerdaLaContrasenaParaLaMismaCredencial() {
        SessionPasswordStore store = new SessionPasswordStore();
        String key = SessionPasswordStore.tokenKey(LIB, "0");
        store.remember(Kind.PKCS11, key, "1234");

        assertEquals(Optional.of("1234"), store.recall(Kind.PKCS11, key));
        assertTrue(store.isRemembered(Kind.PKCS11, key, "1234"));
        assertFalse(store.isRemembered(Kind.PKCS11, key, "otra"));
    }

    @Test
    void cambiarDeTokenHaceQueNoAplique() {
        SessionPasswordStore store = new SessionPasswordStore();
        store.remember(Kind.PKCS11, SessionPasswordStore.tokenKey(LIB, "0"), "1234");

        // otra biblioteca (otra marca de token) y otro slot: no es el mismo token
        assertEquals(Optional.empty(), store.recall(Kind.PKCS11,
                SessionPasswordStore.tokenKey("C:\\Windows\\System32\\eps2003csp11.dll", "0")));
        assertEquals(Optional.empty(), store.recall(Kind.PKCS11, SessionPasswordStore.tokenKey(LIB, "3")));
    }

    @Test
    void laClaveNoDistingueMayusculasBarrasNiEspacios() {
        assertEquals(SessionPasswordStore.tokenKey(LIB, "0"),
                SessionPasswordStore.tokenKey("  c:/windows/system32/etpkcs11.dll ", " 0 "));
        // slot vacío equivale a 0, como en la interfaz
        assertEquals(SessionPasswordStore.tokenKey(LIB, "0"), SessionPasswordStore.tokenKey(LIB, ""));
    }

    @Test
    void unaContrasenaNuevaQueFuncionaReemplazaALaRecordada() {
        SessionPasswordStore store = new SessionPasswordStore();
        String key = SessionPasswordStore.fileKey("C:\\certs\\a.pfx");
        store.remember(Kind.PKCS12, key, "vieja");
        store.remember(Kind.PKCS12, key, "nueva");
        assertEquals(Optional.of("nueva"), store.recall(Kind.PKCS12, key));
    }

    @Test
    void lasContrasenasDeTokenYDeArchivoNoSeMezclan() {
        SessionPasswordStore store = new SessionPasswordStore();
        store.remember(Kind.PKCS11, SessionPasswordStore.tokenKey(LIB, "0"), "pin");
        assertFalse(store.hasAny(Kind.PKCS12));
        store.remember(Kind.PKCS12, SessionPasswordStore.fileKey("C:\\a.pfx"), "clave");
        assertEquals(Optional.of("pin"), store.recall(Kind.PKCS11, SessionPasswordStore.tokenKey(LIB, "0")));
    }

    @Test
    void olvidarYLimpiarSueltanTodo() {
        SessionPasswordStore store = new SessionPasswordStore();
        String k11 = SessionPasswordStore.tokenKey(LIB, "0");
        String k12 = SessionPasswordStore.fileKey("C:\\a.pfx");
        store.remember(Kind.PKCS11, k11, "pin");
        store.remember(Kind.PKCS12, k12, "clave");

        store.forget(Kind.PKCS11);
        assertFalse(store.hasAny(Kind.PKCS11));
        assertTrue(store.hasAny(Kind.PKCS12));

        store.clear();
        assertFalse(store.hasAny(Kind.PKCS12));
        assertEquals(Optional.empty(), store.peek(Kind.PKCS12));
    }

    @Test
    void noGuardaContrasenasVaciasNiClavesVacias() {
        SessionPasswordStore store = new SessionPasswordStore();
        store.remember(Kind.PKCS11, SessionPasswordStore.tokenKey(LIB, "0"), "");
        store.remember(Kind.PKCS11, "", "pin");
        store.remember(Kind.PKCS11, null, "pin");
        assertFalse(store.hasAny(Kind.PKCS11));
    }
}
