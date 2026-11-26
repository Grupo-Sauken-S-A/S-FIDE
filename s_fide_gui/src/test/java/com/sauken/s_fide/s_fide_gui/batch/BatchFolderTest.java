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

package com.sauken.s_fide.s_fide_gui.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BatchFolderTest {

    @TempDir
    Path carpeta;

    private void crear(String... nombres) throws IOException {
        for (String nombre : nombres) {
            Files.writeString(carpeta.resolve(nombre), "x");
        }
    }

    private static List<String> nombres(List<Path> rutas) {
        return rutas.stream().map(p -> p.getFileName().toString()).toList();
    }

    private static List<String> incluidos(BatchFolder.Exploracion e) {
        return e.incluidos().stream().map(a -> a.ruta().getFileName() + (a.marcado() ? "" : " (desmarcado)")).toList();
    }

    @Test
    void soloLaExtensionPedidaEnOrdenYSinSubcarpetas() throws IOException {
        crear("b.xml", "A.XML", "c.pdf", "a.xml.log", "d.xml.123.tmp-sfide", "vacio.xml");
        Files.createDirectories(carpeta.resolve("sub.xml"));
        Files.createDirectories(carpeta.resolve("otra"));
        Files.writeString(carpeta.resolve("otra/e.xml"), "x");

        var e = BatchFolder.explorar(carpeta, "xml", BatchFolder.Filtro.TODOS);

        assertEquals(List.of("A.XML", "b.xml", "vacio.xml"), incluidos(e));
    }

    @Test
    void alFirmarQuedanFueraLosQueTerminanEnSigned() throws IOException {
        crear("a.pdf", "a-signed.pdf", "b-SIGNED.pdf", "signed.pdf");

        var e = BatchFolder.explorar(carpeta, "pdf", BatchFolder.Filtro.SIN_FIRMADOS);

        assertEquals(List.of("a.pdf", "signed.pdf"), incluidos(e));
        assertEquals(List.of("a-signed.pdf", "b-SIGNED.pdf"), nombres(e.excluidosPorNombre()));
    }

    @Test
    void paraAgregarUnaFirmaEntranSoloLosSignedYNoSeRepiteLaSegunda() throws IOException {
        crear("a.xml", "a-signed.xml", "a-signed-signed.xml", "b-signed.xml", "c-signed-signed.xml");

        var e = BatchFolder.explorar(carpeta, "xml", BatchFolder.Filtro.SOLO_FIRMADOS);

        // "c-signed-signed" llegó sin su antecesor (viene de otra persona): entra marcado.
        assertEquals(List.of("a-signed-signed.xml (desmarcado)", "a-signed.xml", "b-signed.xml",
                "c-signed-signed.xml"), incluidos(e));
        assertEquals(List.of("a.xml"), nombres(e.excluidosPorNombre()));
    }

    @Test
    void laHuellaCambiaSiCambiaLaCarpeta() throws IOException {
        crear("a.xml");
        String antes = BatchFolder.explorar(carpeta, "xml", BatchFolder.Filtro.TODOS).huella();
        assertEquals(antes, BatchFolder.explorar(carpeta, "xml", BatchFolder.Filtro.TODOS).huella());

        Files.writeString(carpeta.resolve("a.xml"), "contenido distinto");
        String modificado = BatchFolder.explorar(carpeta, "xml", BatchFolder.Filtro.TODOS).huella();
        assertNotEquals(antes, modificado);

        crear("b.xml");
        assertNotEquals(modificado, BatchFolder.explorar(carpeta, "xml", BatchFolder.Filtro.TODOS).huella());
    }
}
