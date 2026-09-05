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

package com.sauken.s_fide.s_fide_gui.utils;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Evita que se ejecuten dos instancias de S-FiDE GUI en simultáneo desde la
 * misma carpeta de instalación (dos instalaciones distintas en carpetas
 * separadas pueden correr en paralelo sin problema, cada una con su propio
 * lock — mismo criterio que sfide-defaults.properties).
 * <p>
 * Usa un {@link FileLock} exclusivo sobre un archivo dedicado
 * ("sfide-gui.lock", junto al jar en ejecución), no un flag persistido en
 * disco: la diferencia importa porque un flag es un dato que puede quedar
 * "marcado" para siempre si el proceso termina de mala manera (caída,
 * kill, corte de luz), dejando el sistema inoperable hasta borrarlo a mano.
 * Un FileLock, en cambio, está atado al handle de archivo abierto del
 * proceso — el sistema operativo lo libera automáticamente en el instante
 * en que el proceso termina, sea cual sea el motivo, sin dejar ningún
 * estado que haya que limpiar. Es además nativamente multiplataforma:
 * {@code java.nio.channels.FileLock} es parte estándar del JDK y la JVM lo
 * traduce a la primitiva de lock de cada sistema operativo (Windows,
 * Linux, macOS) con esta misma semántica en los tres.
 */
public final class SingleInstanceGuard {
    private static final String LOCK_FILE_NAME = "sfide-gui.lock";

    // Deliberadamente nunca se cierran: cerrarlos liberaría el lock antes de
    // tiempo. El propio sistema operativo los cierra (liberando el lock) al
    // terminar el proceso, sin importar cómo termine.
    private static FileChannel lockChannel;
    private static FileLock lock;

    private SingleInstanceGuard() {
    }

    /**
     * Intenta tomar el lock exclusivo. Devuelve {@code true} si esta es la
     * única instancia corriendo desde esta carpeta de instalación (el lock
     * se mantiene tomado por el resto de la ejecución), o {@code false} si
     * ya hay otra instancia viva con el lock tomado.
     * <p>
     * Si el archivo de lock no se puede ni crear ni abrir por algún motivo
     * ajeno a "otra instancia lo tiene" (permisos, medio de solo lectura,
     * etc.), se degrada a permitir el arranque igual — esta verificación es
     * una comodidad, no debe poder impedir usar la aplicación por una causa
     * distinta a la que realmente le compete.
     */
    public static boolean tryAcquire() {
        try {
            Path installDir = resolveInstallDir();
            Path lockPath = (installDir != null ? installDir : Paths.get("."))
                    .resolve(LOCK_FILE_NAME);

            lockChannel = FileChannel.open(
                    lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
            lock = lockChannel.tryLock();
            return lock != null;
        } catch (OverlappingFileLockException e) {
            // Esta misma JVM ya lo tiene tomado (no debería darse en el uso
            // real, tryAcquire() se llama una sola vez en main()) — se trata
            // igual que "no se pudo adquirir".
            return false;
        } catch (IOException e) {
            System.err.println("No se pudo verificar instancia única, se permite continuar: " + e.getMessage());
            return true;
        }
    }

    private static Path resolveInstallDir() {
        try {
            return Paths.get(
                    SingleInstanceGuard.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mensaje mostrado cuando {@link #tryAcquire()} devuelve {@code false}.
     * Se muestra con Swing ({@code JOptionPane}), no con un Alert de JavaFX,
     * a propósito: esto ocurre antes de {@code Application.launch()}, sin el
     * toolkit de JavaFX todavía inicializado.
     */
    public static void showAlreadyRunningMessage() {
        JOptionPane.showMessageDialog(
                null,
                "S-FiDE ya está en ejecución.\nEsta nueva ventana se cerrará.",
                "S-FIDE - Sistema de Firma Digital Extendido",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}
