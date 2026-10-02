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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Carpeta de datos PERSONALES del usuario de S-FiDE: dónde viven su
 * configuración (sfide-defaults.properties), el candado de instancia única y
 * los certificados .pem que extraen los módulos de "Ver certificado".
 * <p>
 * Es {@code <carpeta personal del usuario>/S-FiDE} ({@code %USERPROFILE%\S-FiDE}
 * en Windows, {@code $HOME/S-FiDE} en Linux/macOS) — nunca la carpeta de
 * instalación. La instalación puede ser compartida por varios usuarios del
 * mismo equipo (a la vez o por turnos): cada uno debe tener sus propios
 * valores recordados, y nadie necesita permiso de escritura sobre la
 * carpeta de instalación para usar el programa.
 * <p>
 * La propiedad de sistema {@code sfide.data.dir} permite redirigirla (pruebas,
 * instalaciones portables en un pendrive). Los módulos de línea de comandos
 * que escriben en esta carpeta (los extractores de certificados) respetan la
 * misma propiedad y el mismo nombre de carpeta, para que la GUI y ellos
 * siempre coincidan — cada jar sigue siendo independiente, por eso esta
 * lógica mínima está repetida en cada módulo que la necesita.
 */
public final class UserDataDirectory {
    public static final String FOLDER_NAME = "S-FiDE";
    public static final String OVERRIDE_PROPERTY = "sfide.data.dir";

    private static Path resolved;
    private static boolean fallback;

    private UserDataDirectory() {
    }

    /**
     * Devuelve la carpeta de datos del usuario, creándola si hace falta. Si
     * no se puede crear ni escribir en ella (perfil de solo lectura, permisos),
     * se degrada al directorio de trabajo actual en lugar de impedir el uso
     * de la aplicación — ver {@link #isFallback()}.
     */
    public static synchronized Path get() {
        if (resolved == null) {
            resolved = resolve();
        }
        return resolved;
    }

    /** {@code true} si no se pudo usar la carpeta de usuario y se cayó al directorio de trabajo. */
    public static synchronized boolean isFallback() {
        get();
        return fallback;
    }

    public static Path resolve(String fileName) {
        return get().resolve(fileName);
    }

    private static Path resolve() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        Path candidate = (override != null && !override.isBlank())
                ? Paths.get(override.trim())
                : Paths.get(System.getProperty("user.home", "."), FOLDER_NAME);
        try {
            Files.createDirectories(candidate);
            if (Files.isWritable(candidate)) {
                return candidate;
            }
        } catch (IOException | RuntimeException e) {
            // Se informa abajo, junto con la degradación.
        }
        fallback = true;
        System.err.println("Aviso: no se pudo usar la carpeta personal de S-FiDE (" + candidate
                + "); se usará el directorio de trabajo actual.");
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
    }
}
