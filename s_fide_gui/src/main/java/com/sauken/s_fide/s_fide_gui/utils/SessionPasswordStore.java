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

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Contraseñas usadas CON ÉXITO durante la sesión abierta de la GUI, para que
 * el usuario no tenga que volver a escribirlas al pasar de una pestaña a otra.
 * <p>
 * Reglas (pedidas por el dueño del proyecto):
 * <ul>
 *   <li>Solo en memoria, mientras dure la sesión: nunca se escribe en
 *   ningún archivo (ni en sfide-defaults.properties) ni se envía a ningún
 *   lado. Al cerrar S-FiDE desaparece.</li>
 *   <li>Se recuerda UNA contraseña por tipo de credencial ({@link Kind}):
 *   las de token PKCS#11 comparten una, las de archivo PKCS#12 otra.</li>
 *   <li>Está atada a la credencial para la que funcionó (la "clave": la
 *   biblioteca y el slot del token, o el archivo PKCS#12). Si el usuario
 *   cambia de token o de archivo, la contraseña recordada deja de aplicar
 *   y se olvida.</li>
 *   <li>Si el usuario escribe una contraseña nueva y esa funciona, reemplaza
 *   a la recordada.</li>
 * </ul>
 * Es lógica pura, sin dependencias de la interfaz, para poder probarla sola.
 * Aclaración honesta de alcance: en Java una contraseña es un {@code String}
 * inmutable que no se puede borrar de la memoria de forma determinística;
 * "olvidar" acá significa soltar toda referencia, no sobrescribir los bytes.
 */
public final class SessionPasswordStore {

    /** Tipos de credencial que comparten contraseña entre pestañas. */
    public enum Kind {
        /** Token criptográfico (biblioteca PKCS#11). */
        PKCS11,
        /** Archivo de certificado PKCS#12 (.p12/.pfx). */
        PKCS12
    }

    private final Map<Kind, String> keys = new EnumMap<>(Kind.class);
    private final Map<Kind, String> passwords = new EnumMap<>(Kind.class);

    /** Clave de una credencial de token: biblioteca + slot (el slot vacío equivale a 0, como en la GUI). */
    public static String tokenKey(String libraryPath, String slotNumber) {
        String slot = (slotNumber == null || slotNumber.isBlank()) ? "0" : slotNumber.trim();
        return normalizePath(libraryPath) + "|" + slot;
    }

    /** Clave de una credencial de archivo PKCS#12: su ruta. */
    public static String fileKey(String pkcs12Path) {
        return normalizePath(pkcs12Path);
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/').toLowerCase();
    }

    /** Guarda la contraseña que acaba de funcionar para esa credencial, reemplazando la anterior. */
    public synchronized void remember(Kind kind, String key, String password) {
        if (password == null || password.isEmpty() || key == null || key.isBlank()) {
            return;
        }
        keys.put(kind, key);
        passwords.put(kind, password);
    }

    /** La contraseña recordada, solo si fue para esta misma credencial. */
    public synchronized Optional<String> recall(Kind kind, String currentKey) {
        if (passwords.containsKey(kind) && keys.get(kind).equals(currentKey)) {
            return Optional.of(passwords.get(kind));
        }
        return Optional.empty();
    }

    /** {@code true} si {@code password} es justamente la recordada para esta credencial. */
    public synchronized boolean isRemembered(Kind kind, String currentKey, String password) {
        return password != null && recall(kind, currentKey).filter(password::equals).isPresent();
    }

    /** {@code true} si hay algo recordado de ese tipo, sea para la credencial que sea. */
    public synchronized boolean hasAny(Kind kind) {
        return passwords.containsKey(kind);
    }

    /** {@code true} si lo recordado de ese tipo corresponde a la credencial actual. */
    public synchronized boolean isForCurrentCredential(Kind kind, String currentKey) {
        return passwords.containsKey(kind) && keys.get(kind).equals(currentKey);
    }

    /** La contraseña recordada de ese tipo, sin importar para qué credencial (para poder limpiar los campos que la muestran). */
    public synchronized Optional<String> peek(Kind kind) {
        return Optional.ofNullable(passwords.get(kind));
    }

    public synchronized void forget(Kind kind) {
        keys.remove(kind);
        passwords.remove(kind);
    }

    public synchronized void clear() {
        keys.clear();
        passwords.clear();
    }
}
