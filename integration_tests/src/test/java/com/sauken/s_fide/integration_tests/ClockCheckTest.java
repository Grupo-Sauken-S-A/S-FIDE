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

package com.sauken.s_fide.integration_tests;

import com.sauken.s_fide.xml_signer_pkcs12.validation.ClockCheck;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La comparación del reloj con la hora real solo advierte cuando la diferencia es grande, y siempre
 * como texto: nunca impide firmar. La consulta HTTPS en sí no se prueba acá (depende de la red).
 */
class ClockCheckTest {

    private static final Instant REAL = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void unaDiferenciaChicaNoAvisa() {
        assertNull(ClockCheck.evaluar(REAL.plusSeconds(40), REAL));
        assertNull(ClockCheck.evaluar(REAL.minusSeconds(4 * 60), REAL));
    }

    @Test
    void unRelojAtrasadoAvisaConElSentidoYLaMagnitud() {
        String aviso = ClockCheck.evaluar(REAL.minus(Duration.ofDays(3)), REAL);
        assertNotNull(aviso);
        assertTrue(aviso.startsWith("ADVERTENCIA:"), aviso);
        assertTrue(aviso.contains("atrasado unos 3 días"), aviso);
        assertTrue(aviso.contains("Se continúa con la firma"), aviso);
    }

    @Test
    void unRelojAdelantadoAvisa() {
        String aviso = ClockCheck.evaluar(REAL.plus(Duration.ofMinutes(20)), REAL);
        assertNotNull(aviso);
        assertTrue(aviso.contains("adelantado unos 20 minutos"), aviso);
    }
}
