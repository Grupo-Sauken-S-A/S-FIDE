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


package com.sauken.s_fide.s_fide_gui.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que informan los firmadores PDF con {@code -analizar-documento}: si el documento todavía admite
 * firmas y cómo está cada firma que ya tiene. La interfaz solo interpreta ese informe; el análisis lo
 * hace el propio programa de firma, con el mismo criterio que usará al firmar.
 */
public record PdfAnalysisReport(
        boolean cerrado,
        String motivoCierre,
        List<Firma> firmas,
        int paginas,
        boolean contenidoProtegido,
        List<CampoVacio> camposVacios,
        String avisoDocumento,
        String notaDocumento,
        String recomendacion) {

    /** Una firma de persona (o un sello de tiempo) ya presente en el documento. */
    public record Firma(boolean esSello, int numero, String veredicto, String firmante, String campo,
                        String mensaje, List<String> notas, String revocacion) {

        public boolean esInvalida() {
            return "INVALIDA".equals(veredicto);
        }

        public boolean esNoVerificable() {
            return "NO_VERIFICABLE".equals(veredicto);
        }
    }

    public record CampoVacio(String nombre, int pagina) {
    }

    /** Cantidad de firmas de personas (sin contar sellos de tiempo). */
    public long cantidadDeFirmasDePersonas() {
        return firmas.stream().filter(f -> !f.esSello()).count();
    }

    /** Solo los problemas reales: lo demás (avisos suaves, notas) no se presenta como alerta. */
    public boolean hayProblemas() {
        return recomendacion != null;
    }

    /**
     * Interpreta la salida de {@code -analizar-documento}. Las líneas que no son parte del informe (avisos
     * del sistema, ruido de arranque) se ignoran. Devuelve {@code null} si no aparece el estado del
     * documento: en ese caso la salida no es un informe.
     */
    public static PdfAnalysisReport parse(Iterable<String> lineas) {
        Boolean cerrado = null;
        String motivo = null;
        int paginas = 0;
        boolean protegido = false;
        String aviso = null;
        String nota = null;
        String recomendacion = null;
        List<Firma> firmas = new ArrayList<>();
        List<CampoVacio> vacios = new ArrayList<>();

        // La firma que se está armando: sus líneas MENSAJE, CAMPO, REVOCACION y NOTA vienen a continuación.
        boolean esSello = false;
        int numero = 0;
        String veredicto = null;
        String firmante = null;
        String mensaje = null;
        String campo = null;
        String revocacion = null;
        List<String> notas = new ArrayList<>();
        boolean hayFirmaEnCurso = false;

        for (String linea : lineas) {
            if (linea == null) {
                continue;
            }
            String texto = linea.stripTrailing();
            String valor;

            if ((valor = despues(texto, "ESTADO_DOCUMENTO: ")) != null) {
                cerrado = "CERRADO".equalsIgnoreCase(valor.trim());
            } else if ((valor = despues(texto, "MOTIVO_CIERRE: ")) != null) {
                motivo = valor;
            } else if (texto.startsWith("FIRMA: ") || texto.startsWith("SELLO: ")) {
                if (hayFirmaEnCurso) {
                    firmas.add(new Firma(esSello, numero, veredicto, firmante, campo, mensaje, notas, revocacion));
                }
                esSello = texto.startsWith("SELLO: ");
                String[] partes = texto.substring(7).split(" \\| ", 3);
                numero = entero(partes[0]);
                veredicto = partes.length > 1 ? partes[1].trim() : "";
                firmante = partes.length > 2 ? partes[2].trim() : "";
                mensaje = null;
                campo = null;
                revocacion = null;
                notas = new ArrayList<>();
                hayFirmaEnCurso = true;
            } else if (hayFirmaEnCurso && (valor = despues(texto, "MENSAJE: ")) != null) {
                mensaje = valor;
            } else if (hayFirmaEnCurso && (valor = despues(texto, "CAMPO: ")) != null) {
                campo = valor;
            } else if (hayFirmaEnCurso && (valor = despues(texto, "REVOCACION: ")) != null) {
                revocacion = valor;
            } else if (hayFirmaEnCurso && (valor = despues(texto, "NOTA: ")) != null) {
                notas.add(valor);
            } else if ((valor = despues(texto, "PAGINAS: ")) != null) {
                paginas = entero(valor);
            } else if (texto.startsWith("CONTENIDO_PROTEGIDO: ")) {
                protegido = true;
            } else if ((valor = despues(texto, "CAMPO_VACIO: ")) != null) {
                String[] partes = valor.split(" \\| ", 2);
                vacios.add(new CampoVacio(partes[0].trim(), partes.length > 1 ? Math.max(1, entero(partes[1])) : 1));
            } else if ((valor = despues(texto, "AVISO_DOCUMENTO: ")) != null) {
                aviso = valor;
            } else if ((valor = despues(texto, "NOTA_DOCUMENTO: ")) != null) {
                nota = valor;
            } else if ((valor = despues(texto, "RECOMENDACION: ")) != null) {
                recomendacion = valor;
            }
        }

        if (hayFirmaEnCurso) {
            firmas.add(new Firma(esSello, numero, veredicto, firmante, campo, mensaje, notas, revocacion));
        }
        if (cerrado == null) {
            return null;
        }
        return new PdfAnalysisReport(cerrado, motivo, List.copyOf(firmas), paginas, protegido,
                List.copyOf(vacios), aviso, nota, recomendacion);
    }

    private static String despues(String texto, String prefijo) {
        return texto.startsWith(prefijo) ? texto.substring(prefijo.length()) : null;
    }

    private static int entero(String texto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
