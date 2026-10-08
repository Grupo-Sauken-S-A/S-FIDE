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


package com.sauken.s_fide.xml_verify_signatures;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Lectura de un archivo XML que tolera la marca BOM de UTF-8.
 * <p>
 * El BOM (Byte Order Mark) son tres bytes invisibles ({@code EF BB BF}) que algunos editores, como el Bloc de
 * notas de Windows, agregan al principio de un archivo al guardarlo como UTF-8. No forman parte del documento
 * XML, el estándar no los necesita en UTF-8 y varios sistemas receptores rechazan el archivo por su causa. El
 * lector de caracteres de Java no los descarta, y el analizador XML falla con "Content is not allowed in
 * prolog".
 * <p>
 * Quitarlos no altera ninguna firma: la canonicalización XML (C14N) que se firma trabaja sobre el contenido
 * del documento y no incluye la marca. Se quitan en memoria: el archivo original nunca se modifica.
 * <p>
 * Esta clase está duplicada a propósito en los módulos que leen XML (cada jar debe seguir siendo independiente):
 * si se modifica, hay que actualizar todas las copias (solo cambia la línea {@code package}).
 */
final class XmlBom {

    /** Para qué se lee el archivo: cambia lo que se le explica a la persona. */
    enum Uso {
        FIRMA, VERIFICACION
    }

    private static boolean yaAviso;

    private XmlBom() {
    }

    /**
     * Devuelve el contenido del archivo sin la marca BOM de UTF-8 (si la tenía) y, la primera vez que la
     * encuentra, lo informa por {@code salida}. Un XML en UTF-16 se rechaza con un mensaje claro.
     */
    static byte[] leer(String archivo, Uso uso, PrintStream salida) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(archivo));
        if (esUtf16(bytes)) {
            throw new IllegalArgumentException("El archivo XML está guardado en UTF-16, y S-FiDE trabaja con UTF-8. "
                    + "Vuelva a guardarlo como UTF-8 (en el Bloc de notas: Guardar como, y en Codificación elegir "
                    + "UTF-8) y reintente.");
        }
        if (!tieneBomUtf8(bytes)) {
            return bytes;
        }
        if (!yaAviso) {
            yaAviso = true;
            salida.println(aviso(uso));
        }
        return Arrays.copyOfRange(bytes, 3, bytes.length);
    }

    static boolean tieneBomUtf8(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF;
    }

    static boolean esUtf16(byte[] bytes) {
        return bytes.length >= 2 && (((bytes[0] & 0xff) == 0xFE && (bytes[1] & 0xff) == 0xFF)
                || ((bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xFE));
    }

    static String aviso(Uso uso) {
        String explicacion = "AVISO: el archivo XML comienza con una marca BOM (Byte Order Mark): tres bytes invisibles "
                + "que algunos editores, como el Bloc de notas de Windows, agregan al guardar en UTF-8. No forma parte "
                + "del contenido, el estándar XML no la necesita en UTF-8 y varios sistemas receptores rechazan el "
                + "archivo por su causa. ";
        if (uso == Uso.FIRMA) {
            return explicacion + "Se quitó automáticamente antes de firmar (su archivo original no se modificó) y el "
                    + "documento firmado se genera sin ella. Las firmas que el documento ya tuviera no se ven "
                    + "afectadas, porque la marca no integra lo que se firma.";
        }
        return explicacion + "Se ignoró para verificar (el archivo no se modificó) y no afecta el resultado de las "
                + "firmas. Conviene pedir a quien generó el archivo que lo guarde sin BOM.";
    }
}
