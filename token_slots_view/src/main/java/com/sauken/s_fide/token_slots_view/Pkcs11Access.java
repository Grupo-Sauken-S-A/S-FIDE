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

package com.sauken.s_fide.token_slots_view;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a un token PKCS#11 a través del proveedor SunPKCS11, resolviendo el
 * slot y traduciendo los errores a mensajes que una persona pueda entender.
 * <p>
 * POR QUÉ EXISTE: {@code slotListIndex=N} de SunPKCS11 es un índice sobre
 * TODOS los slots que informa la biblioteca (haya o no un token en cada uno),
 * no sobre los slots con token. Bibliotecas como la de SafeNet (eTPKCS11.dll)
 * exponen una cantidad fija de slots (8) y el token puede ocupar cualquiera
 * de ellos según el puerto USB, otros tokens conectados o lectores
 * instalados. Si el slot indicado está vacío, SunPKCS11 igual arranca pero
 * sin registrar el almacén de claves PKCS11, y la lectura falla con un
 * error genérico — aunque el token funcione perfecto en otro programa (por
 * ejemplo uno que recorre los slots con token). Acá se prueba primero el
 * slot pedido y, si está vacío, se busca el token en los demás.
 * <p>
 * Esta clase se duplica en cada módulo que accede a un token PKCS#11
 * (misma política de independencia de jars del resto del proyecto).
 */
final class Pkcs11Access {
    /** Tope de slots a recorrer: ninguna biblioteca real expone tantos. */
    private static final int MAX_SLOTS_TO_SCAN = 64;

    private Pkcs11Access() {
    }

    /** Un proveedor ya configurado sobre un slot que tiene un token presente. */
    record TokenSlot(int slotIndex, Provider provider) {
    }

    /** Resultado de buscar el token: el slot elegido y los demás slots con token. */
    record Resolution(TokenSlot selected, List<Integer> otherSlotsWithToken, boolean differsFromRequested) {
    }

    /**
     * Resuelve el slot a usar.
     *
     * @param requestedSlot slot pedido por el usuario, o -1 para "el primero con token"
     * @param providerName  nombre del proveedor SunPKCS11 a configurar
     * @param allowChoosing si es {@code false} y hay más de un token candidato, falla pidiendo
     *                      que se indique el slot (no se intenta ingresar el PIN en ninguno);
     *                      si es {@code true}, se devuelve el primero y se informa de los demás
     * @throws IllegalArgumentException con un mensaje en español, listo para mostrar
     */
    static Resolution resolve(String libraryPath, int requestedSlot, String providerName, boolean allowChoosing) {
        validateLibraryFile(libraryPath);

        List<TokenSlot> withToken = new ArrayList<>();
        int total = 0;
        for (int index = 0; index < MAX_SLOTS_TO_SCAN; index++) {
            Provider candidate;
            try {
                candidate = configure(libraryPath, index, providerName);
            } catch (RuntimeException e) {
                if (isSlotIndexOutOfRange(e)) {
                    break;
                }
                throw new IllegalArgumentException(describeFailure(e, libraryPath));
            }
            total = index + 1;
            if (hasToken(candidate)) {
                withToken.add(new TokenSlot(index, candidate));
                // Con un slot pedido que sí tiene token no hace falta mirar más: es exactamente el
                // comportamiento de siempre, sin consultas extra a la biblioteca.
                if (requestedSlot >= 0 && index == requestedSlot) {
                    return new Resolution(withToken.get(withToken.size() - 1), List.of(), false);
                }
            }

        }

        if (withToken.isEmpty()) {
            throw new IllegalArgumentException(noTokenMessage(libraryPath, total));
        }

        if (requestedSlot >= 0) {
            // El slot pedido no tiene token (si lo tuviera ya habría salido arriba).
            if (withToken.size() == 1) {
                TokenSlot only = withToken.get(0);
                return new Resolution(only, List.of(), true);
            }
            throw new IllegalArgumentException("No hay un token en el slot " + requestedSlot
                    + ", pero se detectaron tokens en los slots " + joinSlots(withToken)
                    + ". Indique cuál usar con el número de slot, para no probar la contraseña en un token equivocado.");
        }

        TokenSlot first = withToken.get(0);
        List<Integer> others = new ArrayList<>();
        for (int i = 1; i < withToken.size(); i++) {
            others.add(withToken.get(i).slotIndex());
        }
        if (!others.isEmpty() && !allowChoosing) {
            throw new IllegalArgumentException("Se detectaron tokens en los slots " + joinSlots(withToken)
                    + ". Indique cuál usar con el número de slot, para no probar la contraseña en un token equivocado.");
        }
        return new Resolution(first, others, false);
    }

    /** Inventario de la biblioteca: cuántos slots informa y en cuáles hay un token presente. */
    record Inventory(int totalSlots, List<TokenSlot> withToken) {
    }

    /**
     * Recorre TODOS los slots que informa la biblioteca y devuelve cuántos son
     * y en cuáles hay un token. Saber si hay un token no requiere contraseña.
     */
    static Inventory inventory(String libraryPath, String providerName) {
        validateLibraryFile(libraryPath);
        List<TokenSlot> withToken = new ArrayList<>();
        int total = 0;
        for (int index = 0; index < MAX_SLOTS_TO_SCAN; index++) {
            Provider candidate;
            try {
                candidate = configure(libraryPath, index, providerName);
            } catch (RuntimeException e) {
                if (isSlotIndexOutOfRange(e)) {
                    break;
                }
                throw new IllegalArgumentException(describeFailure(e, libraryPath));
            }
            total = index + 1;
            if (hasToken(candidate)) {
                withToken.add(new TokenSlot(index, candidate));
            }
        }
        return new Inventory(total, withToken);
    }

    private static String joinSlots(List<TokenSlot> slots) {
        StringBuilder sb = new StringBuilder();
        for (TokenSlot s : slots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s.slotIndex());
        }
        return sb.toString();
    }

    private static String noTokenMessage(String libraryPath, int totalSlots) {
        return "No se detectó ningún token conectado para la biblioteca " + libraryPath
                + " (la biblioteca informa " + totalSlots + " slot" + (totalSlots == 1 ? "" : "s") + ", todos sin token). "
                + "Verifique que el token esté insertado, que la biblioteca corresponda a la marca/modelo de ese token "
                + "y que no lo esté usando otro programa en forma exclusiva.";
    }

    private static Provider configure(String libraryPath, int slotIndex, String providerName) {
        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException("El proveedor SunPKCS11 no está disponible en este Java.");
        }
        String config = "--name=" + providerName + "\nlibrary=" + sanitizeLibraryPath(libraryPath)
                + "\nslotListIndex=" + slotIndex;
        return base.configure(config);
    }

    /**
     * El parser de configuración de SunPKCS11 trata la barra invertida como
     * carácter de escape, por lo que una ruta de Windows sin convertir (aun
     * entre comillas) falla al configurar el proveedor. Se reemplaza "\" por
     * "/" (aceptado igual por el cargador nativo de la biblioteca) y se
     * encierra el valor entre comillas para tolerar espacios en el path.
     */
    private static String sanitizeLibraryPath(String path) {
        return "\"" + path.replace('\\', '/') + "\"";
    }

    /**
     * Un slot sin token deja al proveedor sin servicios registrados: es la
     * única forma, con la API pública del JDK, de saber si hay un token.
     */
    private static boolean hasToken(Provider provider) {
        return provider.getService("KeyStore", "PKCS11") != null;
    }

    private static boolean isSlotIndexOutOfRange(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("slotListIndex is")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Validaciones previas de la biblioteca
    // ---------------------------------------------------------------------

    static void validateLibraryFile(String libraryPath) {
        Path path = Paths.get(libraryPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("El archivo de la biblioteca PKCS#11 no existe: " + libraryPath);
        }
        String mismatch = bitnessMismatch(path);
        if (mismatch != null) {
            throw new IllegalArgumentException(mismatch);
        }
    }

    /**
     * Detecta una biblioteca de 32 bits usada desde un Java de 64 bits (o a la
     * inversa) leyendo el encabezado del propio archivo (PE en Windows, ELF
     * en Linux). Es una causa clásica de "no se puede acceder al token" con
     * programas antiguos que sí funcionan: un Java 8 de 32 bits usa la DLL de
     * 32 bits (SysWOW64) y el Java de S-FiDE necesita la de 64 bits (System32).
     *
     * @return un mensaje de error, o {@code null} si coincide o no se puede determinar
     */
    static String bitnessMismatch(Path library) {
        int libraryBits;
        try {
            libraryBits = libraryBits(library);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (libraryBits == 0) {
            return null;
        }
        int jvmBits = "32".equals(System.getProperty("sun.arch.data.model")) ? 32 : 64;
        if (libraryBits == jvmBits) {
            return null;
        }
        return "La biblioteca PKCS#11 " + library + " es de " + libraryBits + " bits, pero S-FiDE se ejecuta con un Java de "
                + jvmBits + " bits y no puede cargarla. Use la versión de " + jvmBits + " bits del driver del token"
                + (jvmBits == 64 && System.getProperty("os.name", "").toLowerCase().contains("win")
                ? " (en Windows, normalmente la de C:\\Windows\\System32; la de C:\\Windows\\SysWOW64 es la de 32 bits)." : ".");
    }

    /** 32, 64, o 0 si no es un formato reconocido o no se puede determinar. */
    private static int libraryBits(Path library) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(library.toFile(), "r")) {
            byte[] head = new byte[64];
            file.readFully(head);
            if (head[0] == 'M' && head[1] == 'Z') {
                int peOffset = ByteBuffer.wrap(head, 0x3C, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                file.seek(peOffset);
                byte[] pe = new byte[6];
                file.readFully(pe);
                if (pe[0] != 'P' || pe[1] != 'E') {
                    return 0;
                }
                int machine = ByteBuffer.wrap(pe, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                return switch (machine) {
                    case 0x014C, 0x01C0, 0x01C4 -> 32;      // x86, ARM, ARMNT
                    case 0x8664, 0xAA64, 0x0200 -> 64;      // x64, ARM64, IA-64
                    default -> 0;
                };
            }
            if (head[0] == 0x7F && head[1] == 'E' && head[2] == 'L' && head[3] == 'F') {
                return head[4] == 1 ? 32 : (head[4] == 2 ? 64 : 0);
            }
            return 0;
        }
    }

    // ---------------------------------------------------------------------
    // Traducción de errores
    // ---------------------------------------------------------------------

    /**
     * Mensaje en español para un error al acceder al token, a partir de TODA
     * la cadena de causas (el error que importa casi nunca es el de arriba:
     * SunPKCS11 lo envuelve varias veces). Nunca expone una traza de Java; si
     * no reconoce la causa, agrega el detalle técnico en una sola línea para
     * que quien da soporte pueda diagnosticarlo.
     */
    static String describeFailure(Throwable error, String libraryPath) {
        StringBuilder all = new StringBuilder();
        Throwable root = error;
        for (Throwable t = error; t != null; t = t.getCause()) {
            all.append(t.getClass().getName()).append(' ').append(String.valueOf(t.getMessage())).append('\n');
            root = t;
        }
        String text = all.toString();

        if (text.contains("CKR_PIN_INCORRECT") || text.contains("FailedLoginException")) {
            return "Contraseña (PIN) incorrecta.";
        }
        if (text.contains("CKR_PIN_LOCKED")) {
            return "El token está bloqueado por demasiados intentos fallidos de contraseña (PIN). "
                    + "Debe desbloquearse con la herramienta del fabricante.";
        }
        if (text.contains("CKR_PIN_EXPIRED") || text.contains("CKR_PIN_LEN_RANGE")) {
            return "El token rechazó la contraseña (PIN): está vencida o su longitud no es válida.";
        }
        if (text.contains("CKR_TOKEN_NOT_PRESENT") || text.contains("CKR_DEVICE_REMOVED")
                || text.contains("CKR_TOKEN_NOT_RECOGNIZED")) {
            return "El token no está presente, fue retirado o no es reconocido por la biblioteca indicada.";
        }
        if (text.contains("CKR_DEVICE_ERROR") || text.contains("CKR_DEVICE_MEMORY")) {
            return "El token informó un error del dispositivo. Retírelo, vuelva a insertarlo e intente de nuevo.";
        }
        if (text.contains("is not a valid Win32 application") || text.contains("no es una aplicaci")
                || text.contains("wrong ELF class")) {
            String mismatch = bitnessMismatch(Paths.get(libraryPath));
            return mismatch != null ? mismatch
                    : "La biblioteca PKCS#11 no es compatible con este Java (probablemente es de otra arquitectura: 32 bits contra 64 bits).";
        }
        if (text.contains("UnsatisfiedLinkError") || text.contains("Can't find dependent libraries")
               ) {
            return "No se pudo cargar la biblioteca PKCS#11 " + libraryPath + ": o bien falta alguna otra biblioteca "
                    + "que necesita (instale por completo el driver del fabricante) o no es compatible con este Java.";
        }
        if (text.contains("C_GetFunctionList") || text.contains("The specified procedure could not be found")
                || text.contains("No se encontró el procedimiento") || text.contains("No se encontró el proceso especificado")) {
            return "El archivo indicado no parece ser una biblioteca PKCS#11 de un token (no exporta las funciones esperadas): "
                    + libraryPath;
        }
        if (text.contains("CKR_CRYPTOKI_ALREADY_INITIALIZED")) {
            return "La biblioteca PKCS#11 informó que ya estaba inicializada por otro componente de este proceso.";
        }
        return "No se pudo acceder al token (" + root.getClass().getSimpleName()
                + (root.getMessage() != null ? ": " + root.getMessage().trim() : "") + ").";
    }
}
