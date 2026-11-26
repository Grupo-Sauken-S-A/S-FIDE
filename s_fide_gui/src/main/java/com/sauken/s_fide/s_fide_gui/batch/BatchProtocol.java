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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lee las líneas {@code LOTE_} que informan los programas de firma y verificación. La interfaz solo interpreta
 * ese informe: qué documento está bien o mal lo decide siempre el programa.
 */
public final class BatchProtocol {

    /** Una línea {@code LOTE_DOC}. */
    public record Documento(int numero, int total, String estado, String codigo, String entrada, String salida,
                            String log, String mensaje) {
    }

    /** La línea {@code LOTE_RESUMEN}. */
    public record Resumen(Map<String, String> valores) {
        public int cantidad(String nombre) {
            try {
                return Integer.parseInt(valores.getOrDefault(nombre, "0"));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** Causa por la que se detuvo el lote, o {@code null} si no se detuvo. */
        public String corte() {
            String causa = valores.get("corte");
            return causa == null || "NINGUNO".equals(causa) ? null : causa;
        }
    }

    private BatchProtocol() {
    }

    public static Optional<Documento> documento(String linea) {
        if (!linea.startsWith("LOTE_DOC;")) {
            return Optional.empty();
        }
        List<String> c = campos(linea.substring("LOTE_DOC;".length()));
        if (c.size() < 8) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Documento(Integer.parseInt(c.get(0)), Integer.parseInt(c.get(1)), c.get(2),
                    c.get(3), c.get(4), c.get(5), c.get(6), c.get(7)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<Resumen> resumen(String linea) {
        if (!linea.startsWith("LOTE_RESUMEN;")) {
            return Optional.empty();
        }
        Map<String, String> valores = new LinkedHashMap<>();
        for (String campo : campos(linea.substring("LOTE_RESUMEN;".length()))) {
            int igual = campo.indexOf('=');
            if (igual > 0) {
                valores.put(campo.substring(0, igual), campo.substring(igual + 1));
            }
        }
        return Optional.of(new Resumen(valores));
    }

    /** Campos separados por punto y coma y entre comillas; una comilla dentro de un campo viene duplicada. */
    static List<String> campos(String texto) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c != '"') {
                    campo.append(c);
                } else if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else {
                    entreComillas = false;
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == ';') {
                campos.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(c);
            }
        }
        campos.add(campo.toString());
        return campos;
    }

    /** El estado en palabras, con un signo para reconocerlo de un vistazo. */
    public static String estadoLegible(String estado) {
        return switch (estado) {
            case "LISTO" -> "● Listo";
            case "FIRMADO" -> "✔ Firmado";
            case "FIRMADO_CON_AVISOS" -> "⚠ Firmado con avisos";
            case "VALIDO" -> "✔ Válido";
            case "VALIDO_CON_OBSERVACIONES" -> "⚠ Válido con observaciones";
            case "NO_VALIDO" -> "✖ No válido";
            case "SIN_FIRMAS" -> "✖ Sin firmas";
            case "OMITIDO" -> "↷ Omitido";
            case "ERROR" -> "✖ Error";
            case "NO_PROCESADO" -> "— No procesado";
            default -> estado;
        };
    }

    /** Si el documento necesita que alguien lo mire. */
    public static boolean esProblema(String estado) {
        return !("LISTO".equals(estado) || "FIRMADO".equals(estado) || "VALIDO".equals(estado));
    }

    /** Si tiene sentido volver a intentarlo. */
    public static boolean sePuedeReintentar(String estado) {
        return "ERROR".equals(estado) || "NO_PROCESADO".equals(estado) || "NO_VALIDO".equals(estado)
                || "SIN_FIRMAS".equals(estado);
    }
}
