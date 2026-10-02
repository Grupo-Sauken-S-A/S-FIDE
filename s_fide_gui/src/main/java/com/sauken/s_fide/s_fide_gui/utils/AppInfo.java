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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Datos de la propia aplicación: versión instalada y carpeta de instalación.
 * <p>
 * La versión sale de {@code s-fide-version.properties}, un recurso que Maven
 * filtra con {@code ${project.version}} al compilar: es la misma versión del
 * pom, sin una constante a mano que haya que acordarse de cambiar en cada
 * publicación. Importa porque el buscador de actualizaciones compara contra
 * ella — una constante olvidada ofrecería actualizar a la versión que ya se
 * tiene (o ignoraría una nueva).
 */
public final class AppInfo {
    private static final String VERSION_RESOURCE = "/s-fide-version.properties";
    private static final String UNKNOWN = "desconocida";
    private static String version;

    private AppInfo() {
    }

    /** Versión de S-FiDE instalada (por ejemplo, "1.4.0"), o "desconocida" si no se puede leer. */
    public static synchronized String version() {
        if (version == null) {
            version = readVersion();
        }
        return version;
    }

    private static String readVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("version", "").trim();
                // Sin filtrar (corrida desde el IDE sin pasar por Maven) queda el marcador literal.
                if (!v.isEmpty() && !v.startsWith("${")) {
                    return v;
                }
            }
        } catch (IOException ignored) {
            // Se cae a "desconocida".
        }
        return UNKNOWN;
    }

    /**
     * Carpeta de instalación: la que contiene el jar en ejecución (donde
     * viven también los jars de los módulos). {@code null} si no se puede
     * determinar.
     */
    public static Path installDir() {
        try {
            return Paths.get(AppInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (Exception e) {
            return null;
        }
    }
}
