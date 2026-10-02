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

package com.sauken.s_fide.token_certificate_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProviderException;
import javax.security.auth.login.FailedLoginException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Pkcs11AccessTest {

    @TempDir
    Path tempDir;

    /** Arma el mínimo de un ejecutable PE de Windows: encabezado MZ, apuntador a "PE\0\0" y tipo de máquina. */
    private Path peFile(String name, int machine) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(0x100).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte) 'M').put(1, (byte) 'Z');
        buf.putInt(0x3C, 0x80);
        buf.put(0x80, (byte) 'P').put(0x81, (byte) 'E');
        buf.putShort(0x84, (short) machine);
        Path file = tempDir.resolve(name);
        Files.write(file, buf.array());
        return file;
    }

    private static boolean jvm64() {
        return !"32".equals(System.getProperty("sun.arch.data.model"));
    }

    @Test
    void bibliotecaDeLaOtraArquitecturaSeDetectaYSeExplica() throws IOException {
        Path otra = peFile(jvm64() ? "x86.dll" : "x64.dll", jvm64() ? 0x014C : 0x8664);
        String mensaje = Pkcs11Access.bitnessMismatch(otra);
        assertTrue(mensaje != null && mensaje.contains(jvm64() ? "32 bits" : "64 bits"), String.valueOf(mensaje));
    }

    @Test
    void bibliotecaDeLaMismaArquitecturaNoGeneraAviso() throws IOException {
        Path igual = peFile(jvm64() ? "x64.dll" : "x86.dll", jvm64() ? 0x8664 : 0x014C);
        assertNull(Pkcs11Access.bitnessMismatch(igual));
    }

    @Test
    void archivoDeFormatoDesconocidoNoGeneraAvisoFalso() throws IOException {
        Path raro = tempDir.resolve("raro.bin");
        Files.write(raro, new byte[128]);
        assertNull(Pkcs11Access.bitnessMismatch(raro));
    }

    @Test
    void archivoInexistenteSeInformaEnEspanol() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Pkcs11Access.resolve(tempDir.resolve("no-esta.dll").toString(), 0, "Prueba", false));
        assertTrue(e.getMessage().contains("no existe"));
    }

    @Test
    void contrasenaIncorrectaSeReconoceEnLaCadenaDeCausas() {
        Throwable error = new java.io.IOException("load failed",
                new FailedLoginException("CKR_PIN_INCORRECT"));
        assertEquals("Contraseña (PIN) incorrecta.", Pkcs11Access.describeFailure(error, "x.dll"));
    }

    @Test
    void tokenBloqueadoSeExplica() {
        Throwable error = new ProviderException("Initialization failed", new RuntimeException("CKR_PIN_LOCKED"));
        assertTrue(Pkcs11Access.describeFailure(error, "x.dll").contains("bloqueado"));
    }

    @Test
    void causaDesconocidaNuncaExponeUnaTrazaDeJava() {
        String mensaje = Pkcs11Access.describeFailure(new IllegalStateException("algo raro"), "x.dll");
        assertTrue(mensaje.startsWith("No se pudo acceder al token"));
        assertTrue(mensaje.contains("algo raro"));
        assertTrue(!mensaje.contains("\tat "));
    }
}
