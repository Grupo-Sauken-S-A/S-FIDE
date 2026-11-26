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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada jar es independiente, así que las clases comunes están copiadas en varios módulos. Esta prueba falla si
 * alguna copia quedó distinta de las demás (la única diferencia admitida es el nombre del paquete): es la forma
 * de que una corrección no quede aplicada a medias.
 */
class LoteCopiasTest {

    @Test
    void lasClasesDelLoteSonIdenticasEnTodosLosModulos() throws IOException {
        comprobar("Lote.java", 7);
        comprobar("LoteVerificacion.java", 3);
        comprobar("LotePdf.java", 2);
        comprobar("LoteXml.java", 2);
        comprobar("LoteToken.java", 2);
        comprobar("XmlBom.java", 5);
    }

    private void comprobar(String archivo, int minimoDeCopias) throws IOException {
        Path raiz = Path.of("").toAbsolutePath().getParent();
        List<Path> copias = new ArrayList<>();
        try (Stream<Path> modulos = Files.list(raiz)) {
            for (Path modulo : modulos.filter(Files::isDirectory).sorted().toList()) {
                Path copia = modulo.resolve("src/main/java/com/sauken/s_fide/" + modulo.getFileName() + "/" + archivo);
                if (Files.exists(copia)) {
                    copias.add(copia);
                }
            }
        }
        assertTrue(copias.size() >= minimoDeCopias, "Faltan copias de " + archivo + ": " + copias);
        String referencia = sinNombreDeModulo(copias.get(0));
        for (Path copia : copias) {
            assertEquals(referencia, sinNombreDeModulo(copia), archivo + " difiere en " + copia);
        }
    }

    private static String sinNombreDeModulo(Path copia) throws IOException {
        // .../<modulo>/src/main/java/com/sauken/s_fide/<modulo>/Archivo.java
        String modulo = copia.getParent().getFileName().toString();
        return Files.readString(copia, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("com.sauken.s_fide." + modulo, "com.sauken.s_fide.<modulo>");
    }
}
