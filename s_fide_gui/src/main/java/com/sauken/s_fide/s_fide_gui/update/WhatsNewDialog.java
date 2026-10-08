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


package com.sauken.s_fide.s_fide_gui.update;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Cuadro de diálogo con el resumen de novedades: se muestra solo la primera vez que cada usuario abre una
 * versión nueva (con las novedades de todas las versiones que se saltó, si se saltó alguna) y también se
 * puede abrir a pedido desde el menú Ayuda, con todo el historial.
 */
public final class WhatsNewDialog {

    static final String PAGINA_DE_VERSIONES = "https://github.com/Grupo-Sauken-S-A/S-FIDE/releases";

    private WhatsNewDialog() {
    }

    /**
     * @param desde     versión con la que el usuario usaba S-FiDE antes; vacío si no se sabe o si es el historial
     * @param historial {@code true} cuando se abre a pedido desde el menú (otro título y un solo botón "Cerrar")
     */
    public static Dialog<ButtonType> crear(Window propietario, List<ReleaseNotes.Entrada> entradas,
                                           String versionActual, Optional<String> desde, boolean historial,
                                           Consumer<String> abrirEnNavegador) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(historial ? "Novedades de S-FiDE" : "S-FiDE se actualizó");
        dialogo.setHeaderText(historial ? "Historial de novedades de S-FiDE"
                : "Qué hay de nuevo en la versión " + versionActual);
        if (propietario != null) {
            dialogo.initOwner(propietario);
        }
        ButtonType cerrar = new ButtonType(historial ? "Cerrar" : "Entendido", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().add(cerrar);

        VBox contenido = new VBox(10);
        contenido.setPadding(new Insets(4, 10, 4, 4));

        String introduccion = introduccion(entradas, versionActual, desde, historial);
        if (introduccion != null) {
            contenido.getChildren().add(texto(introduccion, "-fx-text-fill: #424242;"));
        }

        for (int i = 0; i < entradas.size(); i++) {
            ReleaseNotes.Entrada entrada = entradas.get(i);
            if (i > 0) {
                contenido.getChildren().add(new Separator());
            }
            String titulo = "Versión " + entrada.version()
                    + (entrada.titulo().isBlank() ? "" : " — " + entrada.titulo());
            contenido.getChildren().add(texto(titulo, "-fx-font-weight: bold; -fx-font-size: 15px;"));
            if (!entrada.resumen().isBlank()) {
                contenido.getChildren().add(texto(entrada.resumen(), "-fx-text-fill: #424242;"));
            }
            for (ReleaseNotes.Grupo grupo : entrada.grupos()) {
                if (!grupo.titulo().isBlank()) {
                    contenido.getChildren().add(texto(grupo.titulo(), "-fx-font-weight: bold; -fx-padding: 4 0 0 0;"));
                }
                for (String punto : grupo.puntos()) {
                    Label viñeta = new Label("•");
                    viñeta.setMinWidth(14);
                    HBox fila = new HBox(6, viñeta, texto(punto, ""));
                    contenido.getChildren().add(fila);
                }
            }
        }

        Hyperlink web = new Hyperlink("Ver todas las versiones publicadas");
        web.setOnAction(e -> {
            if (abrirEnNavegador != null) {
                abrirEnNavegador.accept(PAGINA_DE_VERSIONES);
            }
        });
        contenido.getChildren().addAll(new Separator(), web);

        ScrollPane desplazamiento = new ScrollPane(contenido);
        desplazamiento.setFitToWidth(true);
        desplazamiento.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazamiento.setPrefViewportWidth(640);
        desplazamiento.setPrefViewportHeight(alturaSugerida(entradas, introduccion != null));
        desplazamiento.setStyle("-fx-background-color: transparent;");
        dialogo.getDialogPane().setContent(desplazamiento);
        dialogo.setResizable(true);
        return dialogo;
    }

    public static void mostrar(Window propietario, List<ReleaseNotes.Entrada> entradas, String versionActual,
                               Optional<String> desde, boolean historial, Consumer<String> abrirEnNavegador) {
        crear(propietario, entradas, versionActual, desde, historial, abrirEnNavegador).showAndWait();
    }

    /** Alto del área de texto según cuánto hay para leer, hasta un máximo (más allá se desplaza). */
    static double alturaSugerida(List<ReleaseNotes.Entrada> entradas, boolean conIntroduccion) {
        double lineas = conIntroduccion ? 2 : 0;
        for (ReleaseNotes.Entrada entrada : entradas) {
            lineas += 2 + Math.ceil(entrada.resumen().length() / 85.0);
            for (ReleaseNotes.Grupo grupo : entrada.grupos()) {
                lineas += 1.4;
                for (String punto : grupo.puntos()) {
                    lineas += Math.ceil(punto.length() / 85.0) + 0.4;
                }
            }
        }
        return Math.max(160, Math.min(480, 40 + 19 * lineas));
    }

    static String introduccion(List<ReleaseNotes.Entrada> entradas, String versionActual, Optional<String> desde,
                               boolean historial) {
        if (historial) {
            return "Estos son los cambios y agregados de cada versión, de la más nueva a la más vieja.";
        }
        if (desde.isPresent() && entradas.size() > 1) {
            return "Usted venía usando la versión " + desde.get() + ". Como se pasó directamente a la "
                    + versionActual + ", acá están las novedades de todas las versiones intermedias.";
        }
        if (desde.isPresent()) {
            return "Usted venía usando la versión " + desde.get() + ". Este es el resumen de lo que cambió:";
        }
        return "Este es el resumen de lo que trae S-FiDE:";
    }

    private static Label texto(String contenido, String estilo) {
        Label etiqueta = new Label(contenido);
        etiqueta.setWrapText(true);
        etiqueta.setMaxWidth(600);
        etiqueta.setStyle(estilo);
        return etiqueta;
    }
}
