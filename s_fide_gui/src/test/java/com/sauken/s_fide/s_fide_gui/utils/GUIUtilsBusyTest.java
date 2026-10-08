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

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Una sola operación a la vez: mientras haya algo en curso, los botones "Ejecutar" se deshabilitan.
 * Algunas operaciones encadenan varias ejecuciones, así que la espera se cuenta y dura hasta la última.
 */
class GUIUtilsBusyTest {

    @BeforeAll
    static void iniciarJavaFx() throws Exception {
        try {
            CountDownLatch listo = new CountDownLatch(1);
            Platform.startup(listo::countDown);
            assumeTrue(listo.await(15, TimeUnit.SECONDS), "JavaFX no arrancó");
        } catch (IllegalStateException yaIniciado) {
            // Otra prueba ya lo inició: sirve igual.
        } catch (Throwable sinEntornoGrafico) {
            assumeTrue(false, "Sin entorno gráfico para JavaFX: " + sinEntornoGrafico);
        }
    }

    private static void esperarAlHiloDeLaInterfaz() throws InterruptedException {
        CountDownLatch pasado = new CountDownLatch(1);
        Platform.runLater(pasado::countDown);
        assertTrue(pasado.await(10, TimeUnit.SECONDS));
    }

    @Test
    void lasOperacionesEncadenadasMantienenOcupadoHastaLaUltima() throws Exception {
        esperarAlHiloDeLaInterfaz();
        assertFalse(GUIUtils.busyProperty().get());

        GUIUtils.beginBusy();
        GUIUtils.beginBusy();
        esperarAlHiloDeLaInterfaz();
        assertTrue(GUIUtils.busyProperty().get());

        GUIUtils.endBusy();
        esperarAlHiloDeLaInterfaz();
        assertTrue(GUIUtils.busyProperty().get(), "Todavía queda una operación en curso");

        GUIUtils.endBusy();
        esperarAlHiloDeLaInterfaz();
        assertFalse(GUIUtils.busyProperty().get());
    }

    @Test
    void unFinDeMasNoDejaLaCuentaEnNegativo() throws Exception {
        esperarAlHiloDeLaInterfaz();
        GUIUtils.endBusy();
        esperarAlHiloDeLaInterfaz();
        assertFalse(GUIUtils.busyProperty().get());

        GUIUtils.beginBusy();
        esperarAlHiloDeLaInterfaz();
        assertTrue(GUIUtils.busyProperty().get());
        GUIUtils.endBusy();
        esperarAlHiloDeLaInterfaz();
        assertFalse(GUIUtils.busyProperty().get());
    }
}
