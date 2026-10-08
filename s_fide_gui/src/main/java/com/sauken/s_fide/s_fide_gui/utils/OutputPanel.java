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

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * La "Salida del Proceso": lo que imprimen los programas de S-FiDE mientras trabajan.
 * <p>
 * Funciona así:
 * <ul>
 *   <li>Arranca <b>colapsada</b>, y se puede colapsar y expandir desde su barra de título. Al colapsarla, la
 *   pantalla principal <b>recupera el espacio</b> que ocupaba; al expandirla, vuelve a la altura que la
 *   persona le dio (que se ajusta <b>arrastrando</b> el divisor que la separa de la pantalla principal).</li>
 *   <li>Se <b>abre sola cada vez que termina una operación</b> o una opción de menú que dejó salida —aunque
 *   la persona la hubiera colapsado mientras corría—, y no antes: durante la operación solo avisa
 *   "En curso…" y, si ya hay salida, "● hay salida nueva".</li>
 *   <li><b>Cambiar de opción de menú la colapsa</b> (para dejar lugar a la pantalla nueva) pero <b>nunca borra</b>
 *   la salida anterior.</li>
 *   <li>La salida anterior se borra <b>solo</b> cuando una operación nueva genera su primera línea de salida, o
 *   cuando la persona presiona <b>Limpiar</b> (que además la colapsa: no hay nada más para mostrar).</li>
 *   <li>La barra de título tiene botones para <b>copiar</b>, <b>guardar</b> en un archivo de texto y <b>limpiar</b>.</li>
 *   <li>Siempre muestra lo último que se imprimió.</li>
 * </ul>
 */
public final class OutputPanel {

    private static final double ALTURA_MINIMA = 90;
    private static final double FRACCION_MAXIMA = 0.75;

    private final SalidaTextArea area = new SalidaTextArea();
    private final TitledPane panel = new TitledPane();
    private final SplitPane divisor = new SplitPane();
    private final Label aviso = new Label();
    private final ProgressIndicator progreso = new ProgressIndicator();
    private final Supplier<Window> propietario;

    /**
     * Área de texto que borra lo anterior cuando llega la primera salida de una operación nueva. Todo el
     * programa escribe en ella con {@code appendText}, así que el borrado ocurre en un único lugar.
     */
    private static final class SalidaTextArea extends TextArea {
        private boolean borrarAlLlegarTexto;

        void borrarAlLlegarTexto() {
            borrarAlLlegarTexto = true;
        }

        void noBorrarAlLlegarTexto() {
            borrarAlLlegarTexto = false;
        }

        @Override
        public void appendText(String texto) {
            if (borrarAlLlegarTexto && texto != null && !texto.isEmpty()) {
                borrarAlLlegarTexto = false;
                clear();
            }
            super.appendText(texto);
        }
    }

    private double alturaExpandida;
    private boolean ajustandoDivisor;
    private boolean haySalidaNueva;
    private String mensajePasajero;
    private PauseTransition olvidarMensaje;

    /**
     * @param propietario     ventana principal (para los cuadros de guardado y de error)
     * @param pantallaChica   verdadero en pantallas de baja resolución: se usa una altura inicial menor
     */
    public OutputPanel(Supplier<Window> propietario, boolean pantallaChica) {
        this.propietario = propietario;
        // Alto inicial: lo que antes, más dos líneas de texto.
        this.alturaExpandida = pantallaChica ? 184 : 244;

        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(pantallaChica ? 8 : 10);
        area.setStyle("-fx-font-family: 'Consolas', monospace; -fx-control-inner-background: white;");

        panel.setContent(area);
        panel.setCollapsible(true);
        panel.setExpanded(false);
        // Sin animación: el divisor se mueve de una, y animar solo el contenido dejaría un hueco intermedio.
        panel.setAnimated(false);
        panel.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        panel.setGraphic(construirBarraDeTitulo());

        divisor.setOrientation(javafx.geometry.Orientation.VERTICAL);
        divisor.getStyleClass().add("output-split");

        area.textProperty().addListener((obs, antes, ahora) -> alCambiarElTexto(antes, ahora));
        panel.expandedProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                haySalidaNueva = false;
            }
            ajustarDivisor(ahora);
            actualizarAviso();
        });
        GUIUtils.busyProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                // Empieza una operación nueva: lo anterior se borra cuando llegue su primera línea.
                area.borrarAlLlegarTexto();
            } else {
                // Terminó: se muestra el resultado. Lo que llegue después (otra opción de menú) borra esto.
                mostrarSiHaySalida();
                area.borrarAlLlegarTexto();
            }
            actualizarAviso();
        });
    }

    /** Abre (descolapsa) la salida si hay algo para mostrar. Se usa al terminar cada operación. */
    public void mostrarSiHaySalida() {
        if (!area.getText().isEmpty() && !panel.isExpanded()) {
            panel.setExpanded(true);
        }
        haySalidaNueva = false;
        actualizarAviso();
    }

    /** La colapsa sin tocar su contenido: se usa al cambiar de opción de menú. */
    public void colapsar() {
        panel.setExpanded(false);
    }

    /** El área de texto donde los programas escriben. */
    public TextArea area() {
        return area;
    }

    public boolean estaExpandida() {
        return panel.isExpanded();
    }

    /**
     * Arma el contenedor con la pantalla principal arriba y la salida abajo, separadas por un divisor que se
     * puede arrastrar. Solo la pantalla principal crece o se achica con la ventana; la salida conserva su altura.
     */
    public SplitPane montarDebajoDe(Node pantallaPrincipal) {
        divisor.getItems().setAll(pantallaPrincipal, panel);
        SplitPane.setResizableWithParent(panel, false);

        divisor.heightProperty().addListener((obs, antes, ahora) -> ajustarDivisor(panel.isExpanded()));
        // Si la persona arrastra el divisor con la salida abierta, esa pasa a ser la altura que prefiere.
        divisor.getDividers().get(0).positionProperty().addListener((obs, antes, ahora) -> {
            if (!ajustandoDivisor && panel.isExpanded() && divisor.getHeight() > 0) {
                double altura = divisor.getHeight() * (1.0 - ahora.doubleValue());
                alturaExpandida = Math.max(ALTURA_MINIMA, Math.min(altura, divisor.getHeight() * FRACCION_MAXIMA));
            }
        });
        Platform.runLater(() -> ajustarDivisor(panel.isExpanded()));
        return divisor;
    }

    /** Vacía la salida y la colapsa: ya no hay nada para mostrar. */
    public void limpiar() {
        area.clear();
        area.noBorrarAlLlegarTexto();
        haySalidaNueva = false;
        panel.setExpanded(false);
        actualizarAviso();
    }

    // =====================================================================================

    private Node construirBarraDeTitulo() {
        Label titulo = new Label("Salida del Proceso");
        titulo.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        progreso.setPrefSize(16, 16);
        progreso.setMaxSize(16, 16);
        progreso.visibleProperty().bind(GUIUtils.busyProperty());
        progreso.managedProperty().bind(GUIUtils.busyProperty());

        aviso.setStyle("-fx-text-fill: #cfe8e5; -fx-font-size: 11px;");

        Button copiar = boton("Copiar", "Copia toda la salida al portapapeles", this::copiar);
        Button guardar = boton("Guardar…", "Guarda la salida en un archivo de texto", this::guardar);
        Button limpiar = boton("Limpiar", "Vacía la salida y la colapsa", this::limpiar);
        for (Button b : new Button[]{copiar, guardar, limpiar}) {
            b.disableProperty().bind(Bindings.isEmpty(area.textProperty()));
        }

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        HBox barra = new HBox(8, titulo, progreso, aviso, espacio, copiar, guardar, limpiar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 4, 0, 0));
        // La barra ocupa todo el ancho del título menos la flecha de expandir/colapsar.
        barra.prefWidthProperty().bind(panel.widthProperty().subtract(46));
        return barra;
    }

    private Button boton(String texto, String ayuda, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("output-header-button");
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(ayuda));
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void alCambiarElTexto(String antes, String ahora) {
        if (ahora != null && !ahora.isEmpty()) {
            if (!panel.isExpanded()) {
                if (GUIUtils.busyProperty().get()) {
                    // Hay una operación en curso: se abre cuando termine, no línea por línea.
                    haySalidaNueva = true;
                } else {
                    // Una opción de menú que escribe sin lanzar un proceso: se muestra enseguida.
                    panel.setExpanded(true);
                }
            }
            // Siempre se ve lo último que se imprimió.
            Platform.runLater(() -> area.setScrollTop(Double.MAX_VALUE));
        } else {
            haySalidaNueva = false;
        }
        actualizarAviso();
    }

    private void actualizarAviso() {
        String texto;
        if (mensajePasajero != null) {
            texto = mensajePasajero;
        } else if (GUIUtils.busyProperty().get()) {
            texto = "En curso…";
        } else if (!panel.isExpanded() && haySalidaNueva) {
            texto = "● hay salida nueva";
        } else {
            texto = "";
        }
        aviso.setText(texto);
    }

    /** Un mensaje breve en la barra ("Copiado", "Guardado") que se borra solo. */
    private void avisarUnRato(String mensaje) {
        mensajePasajero = mensaje;
        actualizarAviso();
        if (olvidarMensaje != null) {
            olvidarMensaje.stop();
        }
        olvidarMensaje = new PauseTransition(Duration.seconds(2.5));
        olvidarMensaje.setOnFinished(e -> {
            mensajePasajero = null;
            actualizarAviso();
        });
        olvidarMensaje.play();
    }

    private void copiar() {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(area.getText());
        Clipboard.getSystemClipboard().setContent(contenido);
        avisarUnRato("Copiado al portapapeles");
    }

    private void guardar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar la salida del proceso");
        selector.setInitialFileName("salida-sfide-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo de texto (*.txt)", "*.txt"));
        File documentos = new File(System.getProperty("user.home"), "Documents");
        File inicial = documentos.isDirectory() ? documentos : new File(System.getProperty("user.home"));
        selector.setInitialDirectory(inicial);

        File destino = selector.showSaveDialog(propietario.get());
        if (destino == null) {
            return;
        }
        try {
            Files.writeString(destino.toPath(), area.getText(), StandardCharsets.UTF_8);
            avisarUnRato("Guardado");
        } catch (IOException e) {
            Alert alerta = new Alert(Alert.AlertType.ERROR,
                    "No se pudo guardar el archivo. Verifique que la carpeta exista y que tenga permiso para escribir en ella.");
            alerta.setHeaderText(null);
            alerta.initOwner(propietario.get());
            alerta.showAndWait();
        }
    }

    /**
     * Pone el divisor donde corresponde: con la salida expandida, a la altura elegida; colapsada, justo para
     * que se vea solo su barra de título, de modo que la pantalla principal aproveche todo el resto.
     */
    private void ajustarDivisor(boolean expandida) {
        Platform.runLater(() -> {
            double total = divisor.getHeight();
            if (total <= 0 || divisor.getDividers().isEmpty()) {
                return;
            }
            double altura = expandida
                    ? Math.max(ALTURA_MINIMA, Math.min(alturaExpandida, total * FRACCION_MAXIMA))
                    : alturaDeLaBarra();
            ajustandoDivisor = true;
            try {
                divisor.setDividerPositions(Math.max(0.05, 1.0 - altura / total));
            } finally {
                ajustandoDivisor = false;
            }
        });
    }

    private double alturaDeLaBarra() {
        Node titulo = panel.lookup(".title");
        double altura = titulo == null ? 0 : titulo.getLayoutBounds().getHeight();
        return altura > 10 ? altura + 2 : 34;
    }
}
