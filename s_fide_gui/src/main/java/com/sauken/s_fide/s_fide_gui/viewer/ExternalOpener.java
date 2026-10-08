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


package com.sauken.s_fide.s_fide_gui.viewer;

import javafx.application.HostServices;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Abre cosas fuera de S-FiDE con lo que tenga configurado el sistema operativo (Windows, Linux o macOS): un
 * enlace en el navegador predeterminado, un documento con la aplicación predeterminada de su tipo, o una
 * carpeta en el explorador de archivos.
 * <p>
 * Usa {@link HostServices} de JavaFX y no {@code java.awt.Desktop}: S-FiDE activa el modo "headless" de AWT
 * para dibujar las páginas de un PDF, y en ese modo {@code Desktop} deja de funcionar
 * ({@code HeadlessException}). HostServices no depende de AWT y usa el mecanismo de cada sistema.
 * <p>
 * Las llamadas se hacen fuera del hilo de la interfaz (pueden demorar); si algo falla se informa en lenguaje
 * simple, de vuelta en el hilo de la interfaz.
 */
public final class ExternalOpener {

    private static volatile HostServices servicios;

    private ExternalOpener() {
    }

    /** Se llama una vez al iniciar la aplicación, con {@code getHostServices()}. */
    public static void inicializar(HostServices hostServices) {
        servicios = hostServices;
    }

    /** Abre {@code url} en el navegador predeterminado. */
    public static void abrirEnlace(String url, Consumer<String> alFallar) {
        mostrar(url, "No se pudo abrir el navegador. Puede abrir esta dirección a mano: " + url, alFallar);
    }

    /** Abre una carpeta en el explorador de archivos del sistema. */
    public static void abrirCarpeta(Path carpeta, Consumer<String> alFallar) {
        mostrar(carpeta.toUri().toString(), "No se pudo abrir la carpeta " + carpeta, alFallar);
    }

    /**
     * Abre el documento con la aplicación predeterminada de su tipo. Si el sistema no tiene ninguna asociada
     * (se puede comprobar en Windows), lo abre en el navegador predeterminado, que muestra los PDF por su
     * cuenta. En Linux y macOS decide el propio sistema.
     */
    public static void abrirDocumento(Path archivo, Consumer<String> alFallar) {
        String error = "No se pudo abrir el documento con una aplicación externa. Verifique que el equipo tenga "
                + "un programa o un navegador para abrir este tipo de archivo.";
        Thread hilo = new Thread(() -> {
            try {
                String destino = archivo.toUri().toString();
                if (esWindows() && !tieneAplicacionAsociada(archivo)) {
                    // Una página mínima que redirige al documento: los .html siempre tienen navegador.
                    Path puente = Files.createTempFile("sfide-abrir-", ".html");
                    puente.toFile().deleteOnExit();
                    Files.writeString(puente, "<!DOCTYPE html><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" "
                            + "content=\"0;url=" + destino + "\">", StandardCharsets.UTF_8);
                    destino = puente.toUri().toString();
                }
                pedirAlSistema(destino);
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> alFallar.accept(error));
            }
        }, "visor-abrir-externo");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Muestra un aviso simple encima de la ventana indicada. */
    public static void informar(javafx.stage.Window propietario, String mensaje) {
        javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alerta.initOwner(propietario);
        alerta.setTitle("No se pudo abrir");
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.show();
    }

    // ---------------------------------------------------------------------------------------------

    private static void mostrar(String destino, String mensajeDeError, Consumer<String> alFallar) {
        Thread hilo = new Thread(() -> {
            try {
                pedirAlSistema(destino);
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> alFallar.accept(mensajeDeError));
            }
        }, "visor-abrir-externo");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static void pedirAlSistema(String destino) throws IOException {
        HostServices hs = servicios;
        if (hs == null) {
            throw new IOException("Servicios del sistema no inicializados");
        }
        hs.showDocument(destino);
    }

    private static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Solo Windows: ¿hay un programa asociado a la extensión del archivo? Se consulta el registro con
     * {@code reg query} (argumentos separados, sin intérprete de comandos). Ante cualquier duda se asume que sí:
     * es mejor dejar decidir a Windows que desviar al navegador sin necesidad.
     */
    private static boolean tieneAplicacionAsociada(Path archivo) {
        String nombre = archivo.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        if (punto < 0) {
            return true;
        }
        String extension = nombre.substring(punto).toLowerCase(Locale.ROOT);
        try {
            Process proceso = new ProcessBuilder("reg", "query", "HKCR\\" + extension, "/ve")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                return true;
            }
            return proceso.exitValue() == 0;
        } catch (IOException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
