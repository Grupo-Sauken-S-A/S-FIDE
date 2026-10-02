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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Número de versión de S-FiDE ({@code mayor.menor.parche}, con un sufijo
 * opcional de pre-publicación como {@code -beta.1}), comparable según las
 * reglas de SemVer: una pre-publicación es anterior a la versión final del
 * mismo número ({@code 1.4.0-beta.1 < 1.4.0}).
 */
record VersionNumber(int major, int minor, int patch, String preRelease) implements Comparable<VersionNumber> {
    private static final Pattern FORMAT =
            Pattern.compile("^v?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    /** Interpreta "1.4.0", "v1.4.0" o "1.4.0-beta.1"; vacío si no tiene ese formato. */
    static Optional<VersionNumber> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(text.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new VersionNumber(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                m.group(4) == null ? "" : m.group(4)));
    }

    @Override
    public int compareTo(VersionNumber other) {
        int c = Integer.compare(major, other.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, other.minor);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(patch, other.patch);
        if (c != 0) {
            return c;
        }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return preRelease.isEmpty() ? (other.preRelease.isEmpty() ? 0 : 1) : -1;
        }
        return comparePreRelease(preRelease, other.preRelease);
    }

    private static int comparePreRelease(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            boolean na = pa[i].matches("\\d+");
            boolean nb = pb[i].matches("\\d+");
            int c;
            if (na && nb) {
                c = Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i]));
            } else if (na != nb) {
                c = na ? -1 : 1; // los numéricos van antes que los alfanuméricos
            } else {
                c = pa[i].compareTo(pb[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    boolean isNewerThan(VersionNumber other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (preRelease.isEmpty() ? "" : "-" + preRelease);
    }
}
