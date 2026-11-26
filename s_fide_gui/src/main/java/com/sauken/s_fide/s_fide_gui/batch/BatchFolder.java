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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Elige, <b>por su nombre</b>, qué archivos de una carpeta entran en un lote. No mira su contenido ni decide si
 * sirven: eso lo informa el programa que los procesa. No recorre subcarpetas.
 */
public final class BatchFolder {

    /** Qué nombres entran según el sufijo "-signed". */
    public enum Filtro {
        /** Verificación y validación: todos. */
        TODOS,
        /** Firma: solo los que no terminan en "-signed". */
        SIN_FIRMADOS,
        /** Agregar otra firma: solo los que terminan en "-signed". */
        SOLO_FIRMADOS
    }

    /**
     * @param marcado si entra marcado; no lo hace el que es resultado de una segunda firma hecha en esta misma
     *                carpeta, para no agregar una tercera sin querer al repetir un lote
     */
    public record Archivo(Path ruta, boolean marcado) {
    }

    /**
     * @param huella cambia si se agrega, quita o modifica algún documento de la carpeta
     */
    public record Exploracion(List<Archivo> incluidos, List<Path> excluidosPorNombre, String huella) {
    }

    private static final String SUFIJO = "-signed";

    private BatchFolder() {
    }

    public static Exploracion explorar(Path carpeta, String extension, Filtro filtro) throws IOException {
        List<Path> documentos;
        try (Stream<Path> contenido = Files.list(carpeta)) {
            documentos = contenido
                    .filter(Files::isRegularFile)
                    .filter(p -> nombre(p).toLowerCase(Locale.ROOT).endsWith("." + extension))
                    .sorted(Comparator.comparing((Path p) -> nombre(p).toLowerCase(Locale.ROOT))
                            .thenComparing(BatchFolder::nombre))
                    .toList();
        }
        Set<String> nombres = documentos.stream().map(p -> sinExtension(p).toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Archivo> incluidos = new ArrayList<>();
        List<Path> excluidos = new ArrayList<>();
        StringBuilder huella = new StringBuilder();
        for (Path documento : documentos) {
            huella.append(nombre(documento)).append('|').append(Files.size(documento)).append('|')
                    .append(Files.getLastModifiedTime(documento).toMillis()).append('\n');
            String base = sinExtension(documento).toLowerCase(Locale.ROOT);
            boolean firmado = base.endsWith(SUFIJO);
            if ((filtro == Filtro.SIN_FIRMADOS && firmado) || (filtro == Filtro.SOLO_FIRMADOS && !firmado)) {
                excluidos.add(documento);
                continue;
            }
            boolean yaRefirmado = filtro == Filtro.SOLO_FIRMADOS && base.endsWith(SUFIJO + SUFIJO)
                    && nombres.contains(base.substring(0, base.length() - SUFIJO.length()));
            incluidos.add(new Archivo(documento.toAbsolutePath(), !yaRefirmado));
        }
        return new Exploracion(List.copyOf(incluidos), List.copyOf(excluidos), huella.toString());
    }

    private static String nombre(Path ruta) {
        return ruta.getFileName().toString();
    }

    private static String sinExtension(Path ruta) {
        String nombre = nombre(ruta);
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? nombre : nombre.substring(0, punto);
    }
}
