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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Un programa de S-FiDE ejecutando un lote. La salida estándar y la de error se leen por separado y a medida que
 * llegan. No hay un tiempo máximo total (un lote largo es normal): se corta solo si pasa mucho tiempo sin
 * ninguna novedad.
 */
public final class BatchProcess {

    /**
     * @param cancelado    lo detuvo la persona
     * @param sinNovedades se detuvo por pasar demasiado tiempo sin escribir nada
     */
    public record Fin(int codigo, boolean cancelado, boolean sinNovedades) {
    }

    private static final Set<BatchProcess> ACTIVOS = ConcurrentHashMap.newKeySet();

    private final Process proceso;
    private volatile long ultimaNovedad = System.nanoTime();
    private volatile boolean cancelado;

    private BatchProcess(Process proceso) {
        this.proceso = proceso;
    }

    /**
     * @param salida     recibe cada línea de la salida estándar (desde otro hilo)
     * @param error      recibe cada línea de la salida de error (desde otro hilo)
     * @param alTerminar se llama una sola vez, desde otro hilo, después de haber entregado todas las líneas
     */
    public static BatchProcess iniciar(List<String> comando, Consumer<String> salida, Consumer<String> error,
                                       long segundosSinNovedades, Consumer<Fin> alTerminar) throws IOException {
        ProcessBuilder constructor = new ProcessBuilder(comando);
        constructor.environment().put("LANG", "es_ES.UTF-8");
        constructor.environment().put("LC_ALL", "es_ES.UTF-8");
        BatchProcess lote = new BatchProcess(constructor.start());
        ACTIVOS.add(lote);
        Thread lectorSalida = lote.leer(lote.proceso.getInputStream(), salida);
        Thread lectorError = lote.leer(lote.proceso.getErrorStream(), error);
        Thread espera = new Thread(() -> {
            boolean vencido = false;
            try {
                while (!lote.proceso.waitFor(1, TimeUnit.SECONDS)) {
                    if (System.nanoTime() - lote.ultimaNovedad > TimeUnit.SECONDS.toNanos(segundosSinNovedades)) {
                        vencido = true;
                        lote.proceso.destroyForcibly();
                    }
                }
                lectorSalida.join(5000);
                lectorError.join(5000);
            } catch (InterruptedException e) {
                lote.proceso.destroyForcibly();
                Thread.currentThread().interrupt();
            } finally {
                ACTIVOS.remove(lote);
                int codigo = lote.proceso.isAlive() ? 1 : lote.proceso.exitValue();
                alTerminar.accept(new Fin(codigo, lote.cancelado, vencido));
            }
        }, "lote-espera");
        espera.setDaemon(true);
        espera.start();
        return lote;
    }

    private Thread leer(InputStream origen, Consumer<String> destino) {
        Thread hilo = new Thread(() -> {
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(origen, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    ultimaNovedad = System.nanoTime();
                    destino.accept(linea);
                }
            } catch (IOException ignorada) {
                // El proceso se destruyó: no hay más que leer.
            }
        }, "lote-lector");
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    /** Detiene el programa. Las salidas ya generadas quedan completas: ningún archivo queda a medias. */
    public void cancelar() {
        cancelado = true;
        proceso.destroyForcibly();
    }

    /** Al cerrar la aplicación no debe quedar ningún lote corriendo. */
    public static void cancelarTodos() {
        ACTIVOS.forEach(BatchProcess::cancelar);
    }
}
