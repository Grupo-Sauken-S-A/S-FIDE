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


package com.sauken.s_fide.s_fide_gui.update;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Historial de novedades de S-FiDE, pensado para mostrarle a una persona no técnica qué cambió cuando su
 * versión se actualiza. Se lee de {@code text/NOVEDADES.txt}, que lleva una sección por versión:
 * <pre>
 * ## 1.5.0 | Título corto de la versión
 * Una o más líneas de resumen (opcional).
 * ### Nuevo
 * - Cada novedad en una línea.
 * ### Mejoras
 * - ...
 * </pre>
 * Cuando alguien salta varias versiones (por ejemplo de la 1.4.0 directamente a una futura 1.6.0) se
 * muestran las novedades de <b>todas</b> las versiones intermedias, de la más nueva a la más vieja, no solo
 * las de la última: por eso el archivo conserva el historial completo.
 */
public final class ReleaseNotes {

    /** Un grupo de novedades dentro de una versión (por ejemplo "Nuevo" o "Mejoras"). */
    public record Grupo(String titulo, List<String> puntos) {
    }

    /** Las novedades de una versión. */
    public record Entrada(String version, String titulo, String resumen, List<Grupo> grupos) {
    }

    private ReleaseNotes() {
    }

    /** Interpreta el texto del archivo de novedades. Las secciones cuya versión no se entiende se ignoran. */
    public static List<Entrada> parse(String texto) {
        List<Entrada> entradas = new ArrayList<>();
        if (texto == null) {
            return entradas;
        }

        String version = null;
        String titulo = "";
        StringBuilder resumen = new StringBuilder();
        List<Grupo> grupos = new ArrayList<>();
        String grupoTitulo = null;
        List<String> puntos = new ArrayList<>();

        for (String linea : texto.replace("\r\n", "\n").split("\n")) {
            String limpia = linea.strip();
            if (limpia.startsWith("## ")) {
                cerrarGrupo(grupos, grupoTitulo, puntos);
                agregar(entradas, version, titulo, resumen, grupos);

                String[] partes = limpia.substring(3).split("\\|", 2);
                version = partes[0].strip();
                titulo = partes.length > 1 ? partes[1].strip() : "";
                resumen = new StringBuilder();
                grupos = new ArrayList<>();
                grupoTitulo = null;
                puntos = new ArrayList<>();
            } else if (version == null) {
                continue; // texto antes de la primera versión: encabezado del archivo
            } else if (limpia.startsWith("### ")) {
                cerrarGrupo(grupos, grupoTitulo, puntos);
                grupoTitulo = limpia.substring(4).strip();
                puntos = new ArrayList<>();
            } else if (limpia.startsWith("- ")) {
                puntos.add(limpia.substring(2).strip());
            } else if (!limpia.isEmpty() && !limpia.startsWith("#")) {
                if (grupoTitulo == null) {
                    if (!resumen.isEmpty()) {
                        resumen.append(' ');
                    }
                    resumen.append(limpia);
                } else if (!puntos.isEmpty()) {
                    // continuación de la novedad anterior, escrita en otra línea
                    puntos.set(puntos.size() - 1, puntos.get(puntos.size() - 1) + " " + limpia);
                }
            }
        }
        cerrarGrupo(grupos, grupoTitulo, puntos);
        agregar(entradas, version, titulo, resumen, grupos);
        return entradas;
    }

    private static void cerrarGrupo(List<Grupo> grupos, String titulo, List<String> puntos) {
        if (!puntos.isEmpty()) {
            grupos.add(new Grupo(titulo == null ? "" : titulo, List.copyOf(puntos)));
        }
    }

    private static void agregar(List<Entrada> entradas, String version, String titulo, StringBuilder resumen,
                                List<Grupo> grupos) {
        if (version != null && VersionNumber.parse(version).isPresent()) {
            entradas.add(new Entrada(version, titulo, resumen.toString(), List.copyOf(grupos)));
        }
    }

    /**
     * Las novedades que le corresponden a quien pasó de {@code anterior} a {@code actual}: las versiones
     * posteriores a la anterior y hasta la actual inclusive, de la más nueva a la más vieja.
     *
     * @param anterior versión que tenía antes; vacío si no se sabe (se muestra todo el historial hasta la actual)
     * @param actual   versión instalada ahora
     */
    public static List<Entrada> desde(List<Entrada> todas, Optional<String> anterior, String actual) {
        Optional<VersionNumber> hasta = VersionNumber.parse(actual);
        if (hasta.isEmpty()) {
            return List.of();
        }
        Optional<VersionNumber> desde = anterior.flatMap(VersionNumber::parse);
        if (desde.isPresent() && desde.get().compareTo(hasta.get()) >= 0) {
            return List.of();
        }
        return todas.stream()
                .filter(e -> {
                    VersionNumber v = VersionNumber.parse(e.version()).orElseThrow();
                    return v.compareTo(hasta.get()) <= 0 && (desde.isEmpty() || v.compareTo(desde.get()) > 0);
                })
                .sorted(Comparator.comparing((Entrada e) -> VersionNumber.parse(e.version()).orElseThrow()).reversed())
                .toList();
    }
}
