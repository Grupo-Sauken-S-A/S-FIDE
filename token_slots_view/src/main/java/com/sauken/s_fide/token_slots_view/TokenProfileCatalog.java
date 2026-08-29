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

  Autores: Juan Carlos Ríos y Juan Ignacio Ríos con la asistencia de Claude AI 3.5 Sonnet
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

  Authors: Juan Carlos Ríos y Juan Ignacio Ríos with support of Claude AI 3.5 Sonnet
  E-mail: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Company: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

 */

package com.sauken.s_fide.token_slots_view;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Catálogo de perfiles de tokens PKCS#11 conocidos en el ecosistema de firma
 * digital argentino (AC-ONTI), con la ruta típica de su librería PKCS#11 por
 * sistema operativo y la estrategia de hash conocida/recomendada. Se usa
 * como ayuda de UX para autodetección y selección de driver — nunca como
 * única fuente de verdad para decidir el mecanismo de firma real (el código
 * de firma siempre reintenta en tiempo de ejecución, sin confiar ciegamente
 * en este catálogo).
 *
 * Los datos se leen de "token-profiles.txt" (recurso empaquetado en el jar),
 * cuyo origen único es el archivo shared-resources/token-profiles.txt en la
 * raíz del repositorio.
 */
public final class TokenProfileCatalog {

    public record TokenProfile(
            String marca,
            String modelo,
            String estrategiaHash,
            String estado,
            String rutaWindows,
            String rutaLinux,
            String rutaMacOS,
            List<String> nombresArchivo) {

        public String descripcion() {
            return marca + " - " + modelo;
        }

        public String rutaParaSistemaActual() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                return rutaWindows;
            }
            if (os.contains("mac") || os.contains("darwin")) {
                return rutaMacOS;
            }
            return rutaLinux;
        }
    }

    private static final String RESOURCE_NAME = "token-profiles.txt";
    private static List<TokenProfile> profiles;

    private TokenProfileCatalog() {
    }

    public static synchronized List<TokenProfile> getProfiles() {
        if (profiles == null) {
            profiles = loadProfiles();
        }
        return profiles;
    }

    /**
     * Detecta un perfil conocido comparando el nombre de archivo (sin ruta)
     * de la librería PKCS#11 indicada, ignorando mayúsculas/minúsculas.
     */
    public static TokenProfile detectByLibraryPath(String libraryPath) {
        if (libraryPath == null || libraryPath.isBlank()) {
            return null;
        }
        String fileName = libraryPath.replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        String fileNameLower = fileName.toLowerCase(Locale.ROOT);
        for (TokenProfile profile : getProfiles()) {
            for (String candidate : profile.nombresArchivo()) {
                if (candidate.toLowerCase(Locale.ROOT).equals(fileNameLower)) {
                    return profile;
                }
            }
        }
        return null;
    }

    /** Tabla formateada para stdout, con la ruta del sistema operativo actual y si existe en disco. */
    public static String formatTableForCurrentOs() {
        StringBuilder sb = new StringBuilder();
        sb.append("Drivers PKCS#11 conocidos (sistema: ")
                .append(System.getProperty("os.name", "desconocido")).append("):\n\n");

        for (TokenProfile p : getProfiles()) {
            String path = p.rutaParaSistemaActual();
            sb.append("- ").append(p.descripcion()).append('\n');
            sb.append("    Estado: ").append(p.estado()).append('\n');
            sb.append("    Estrategia de hash: ").append(p.estrategiaHash()).append('\n');
            if (path != null && !path.isBlank()) {
                sb.append("    Ruta típica: ").append(path).append('\n');
                boolean exists;
                try {
                    exists = Files.exists(Path.of(path));
                } catch (Exception e) {
                    exists = false;
                }
                sb.append("    ¿Existe en este equipo?: ").append(exists ? "Sí" : "No").append('\n');
            } else {
                sb.append("    Ruta típica: no aplica en este sistema operativo\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static List<TokenProfile> loadProfiles() {
        List<TokenProfile> result = new ArrayList<>();
        try (InputStream is = TokenProfileCatalog.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (is == null) {
                return result;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split("\\|", -1);
                    if (fields.length != 8) {
                        continue;
                    }
                    List<String> nombres = new ArrayList<>();
                    for (String n : fields[7].split(",")) {
                        String trimmed = n.strip();
                        if (!trimmed.isEmpty()) {
                            nombres.add(trimmed);
                        }
                    }
                    result.add(new TokenProfile(
                            fields[0].strip(), fields[1].strip(), fields[2].strip(), fields[3].strip(),
                            fields[4].strip(), fields[5].strip(), fields[6].strip(), nombres));
                }
            }
        } catch (IOException e) {
            // El catálogo es solo un hint de UX; si falla la carga, seguimos sin autodetección.
        }
        return result;
    }
}
