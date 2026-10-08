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

import com.sauken.s_fide.s_fide_gui.utils.ConfigurationManager.NovedadesPendientes;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cuándo se le avisa a un usuario que su versión de S-FiDE cambió. */
class NovedadesPolicyTest {

    @Test
    void unUsuarioNuevoNoRecibeAvisoDeActualizacion() {
        NovedadesPendientes n = ConfigurationManager.decidirNovedades(true, null, null, "1.5.0");

        assertFalse(n.avisar(), "No se actualizó nada: es su primer uso");
    }

    @Test
    void quienActualizaDeLa140ALa150LoVeEnSuPrimerArranque() {
        // La 1.4.0 ya anotaba "app.version"; las novedades todavía no se le mostraron nunca.
        NovedadesPendientes n = ConfigurationManager.decidirNovedades(false, null, "1.4.0", "1.5.0");

        assertTrue(n.avisar());
        assertEquals(Optional.of("1.4.0"), n.desde());
    }

    @Test
    void quienYaVioLasNovedadesDeEstaVersionNoLasVeDeNuevo() {
        NovedadesPendientes n = ConfigurationManager.decidirNovedades(false, "1.5.0", "1.5.0", "1.5.0");

        assertFalse(n.avisar());
    }

    @Test
    void quienSaltaDeLa140ALa160PartePorLaUltimaVersionVista() {
        NovedadesPendientes n = ConfigurationManager.decidirNovedades(false, "1.4.0", "1.4.0", "1.6.0");

        assertTrue(n.avisar());
        assertEquals(Optional.of("1.4.0"), n.desde(), "Con ReleaseNotes se muestran 1.5.0 y 1.6.0");
    }

    @Test
    void laVersionVistaTienePrioridadSobreLaDeLaUltimaVez() {
        // Vio las novedades de 1.5.0, pero la última vez que abrió S-FiDE (sin alcanzar a ver nada) era 1.5.0 también.
        NovedadesPendientes n = ConfigurationManager.decidirNovedades(false, "1.5.0", "1.5.0", "1.6.0");

        assertEquals(Optional.of("1.5.0"), n.desde());
        assertTrue(n.avisar());
    }

    @Test
    void unaConfiguracionDeUnaVersionAnteriorALa140MuestraTodoElHistorial() {
        // Existía configuración pero sin "app.version": viene de antes de la 1.4.0.
        NovedadesPendientes n = ConfigurationManager.decidirNovedades(false, null, null, "1.5.0");

        assertTrue(n.avisar());
        assertEquals(Optional.empty(), n.desde(), "Sin versión conocida se muestra desde el principio");
    }

    @Test
    void corridoDesdeElIdeSinVersionRealNoAvisaNada() {
        assertFalse(ConfigurationManager.decidirNovedades(false, null, "1.4.0", "desconocida").avisar());
        assertFalse(ConfigurationManager.decidirNovedades(false, null, "1.4.0", null).avisar());
    }
}
