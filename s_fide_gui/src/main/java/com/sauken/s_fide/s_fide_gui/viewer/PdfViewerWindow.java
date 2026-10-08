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

import com.sauken.s_fide.s_fide_gui.pdf.PdfAnalysisReport;
import com.sauken.s_fide.s_fide_gui.pdf.PdfDocumentView;
import com.sauken.s_fide.s_fide_gui.pdf.PdfReviewPanel;
import com.sauken.s_fide.s_fide_gui.utils.GUIUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Visor interno de documentos PDF: muestra las páginas con el mismo dibujo que la ventana de ubicación de la
 * firma y, al costado, la revisión de las firmas que informa el propio firmador ({@code -analizar-documento}).
 * <p>
 * Es solo para mirar: no firma ni modifica el documento. Para comprobar la validez de las firmas está el
 * botón "Verificar firmas", que usa el programa de verificación de siempre.
 */
public final class PdfViewerWindow {

    private static final String TEXTO_SIN_ANALISIS = "No se pudieron analizar las firmas de este documento.";

    private final Stage ventana = new Stage();
    private final PdfDocumentView vista;
    private final double escalaDePantalla = Math.max(1.0, Screen.getPrimary().getOutputScaleX());
    private final ExecutorService dibujo = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "visor-pdf");
        hilo.setDaemon(true);
        return hilo;
    });

    private final ImageView imagen = new ImageView();
    private final StackPane hoja = new StackPane(imagen);
    private final ScrollPane desplazamiento = new ScrollPane(new StackPane(hoja));
    private final Label etiquetaPagina = new Label();
    private final Label etiquetaZoom = new Label();
    private final BorderPane raiz = new BorderPane();
    private final VBox panelDerecho = new VBox(10);

    private int paginaActual = 1;
    private double zoom = 1.0;
    private long ordenDeDibujo;

    private PdfViewerWindow(Window propietario, Path archivo, PdfDocumentView vista, Consumer<Path> verificar) {
        this.vista = vista;
        ventana.initOwner(propietario);
        ventana.setTitle("Visor de PDF — " + archivo.getFileName());
        ventana.setOnHidden(e -> {
            dibujo.shutdownNow();
            vista.close();
        });

        Button anterior = new Button("◀");
        anterior.setOnAction(e -> irAPagina(paginaActual - 1));
        Button siguiente = new Button("▶");
        siguiente.setOnAction(e -> irAPagina(paginaActual + 1));
        Button menos = new Button("−");
        menos.setOnAction(e -> cambiarZoom(zoom / 1.2));
        Button mas = new Button("+");
        mas.setOnAction(e -> cambiarZoom(zoom * 1.2));
        Button alAncho = new Button("Ajustar al ancho");
        alAncho.setOnAction(e -> ajustarAlAncho());
        Button verificarBoton = new Button("Verificar firmas");
        verificarBoton.setOnAction(e -> verificar.accept(archivo));

        Region relleno = new Region();
        HBox.setHgrow(relleno, Priority.ALWAYS);
        HBox barra = new HBox(8, anterior, etiquetaPagina, siguiente, menos, etiquetaZoom, mas, alAncho, relleno,
                verificarBoton);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8));

        hoja.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 8, 0, 0, 2);");
        desplazamiento.setPannable(true);
        desplazamiento.setStyle("-fx-background: #e0e0e0;");
        desplazamiento.getContent().setStyle("-fx-padding: 20;");
        desplazamiento.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                cambiarZoom(e.getDeltaY() > 0 ? zoom * 1.1 : zoom / 1.1);
                e.consume();
            }
        });

        panelDerecho.setPadding(new Insets(10));
        panelDerecho.getChildren().add(PdfReviewPanel.crear(null, "Analizando las firmas del documento…"));
        ScrollPane lateral = new ScrollPane(panelDerecho);
        lateral.setFitToWidth(true);
        lateral.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lateral.setPrefWidth(340);
        lateral.setMinWidth(300);

        raiz.setTop(barra);
        raiz.setCenter(desplazamiento);
        raiz.setRight(lateral);
        ventana.setScene(new Scene(raiz, 1100, 760));
        ventana.setOnShown(e -> ajustarAlAncho());
    }

    /**
     * Abre el PDF en una ventana nueva. Si no se puede mostrar, informa el motivo en lenguaje simple por la
     * excepción y no abre nada.
     *
     * @param verificar qué hacer cuando la persona pide verificar las firmas del archivo
     */
    public static Stage abrir(Window propietario, Path archivo, Consumer<Path> verificar)
            throws PdfDocumentView.VistaPreviaException {
        PdfViewerWindow visor = new PdfViewerWindow(propietario, archivo, PdfDocumentView.abrir(archivo), verificar);
        visor.ventana.show();
        visor.analizar(archivo);
        return visor.ventana;
    }

    private void analizar(Path archivo) {
        Thread hilo = new Thread(() -> {
            GUIUtils.SalidaDeProceso respuesta = GUIUtils.ejecutarYCapturar("PDFSignerPKCS12",
                    new String[]{"-analizar-documento", archivo.toString()}, 180);
            PdfAnalysisReport informe = respuesta.codigo() == 0 ? PdfAnalysisReport.parse(respuesta.lineas()) : null;
            Platform.runLater(() -> {
                panelDerecho.getChildren().setAll(PdfReviewPanel.crear(informe, TEXTO_SIN_ANALISIS));
            });
        }, "visor-pdf-analisis");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void irAPagina(int numero) {
        if (numero < 1 || numero > vista.cantidadDePaginas() || numero == paginaActual) {
            return;
        }
        paginaActual = numero;
        cargarPagina();
    }

    private void cambiarZoom(double nuevo) {
        zoom = Math.max(0.3, Math.min(4.0, nuevo));
        cargarPagina();
    }

    private void ajustarAlAncho() {
        double disponible = desplazamiento.getViewportBounds().getWidth() - 60;
        if (disponible > 100) {
            zoom = Math.max(0.3, Math.min(4.0, disponible / vista.pagina(paginaActual).anchoEnPantalla()));
        }
        cargarPagina();
    }

    private void cargarPagina() {
        double ancho = vista.pagina(paginaActual).anchoEnPantalla() * zoom;
        double alto = vista.pagina(paginaActual).altoEnPantalla() * zoom;
        hoja.setPrefSize(ancho, alto);
        hoja.setMinSize(ancho, alto);
        hoja.setMaxSize(ancho, alto);
        imagen.setFitWidth(ancho);
        imagen.setFitHeight(alto);
        etiquetaPagina.setText("Página " + paginaActual + " de " + vista.cantidadDePaginas());
        etiquetaZoom.setText(Math.round(zoom * 100) + " %");

        final long orden = ++ordenDeDibujo;
        final int pagina = paginaActual;
        final double escala = zoom * escalaDePantalla;
        dibujo.submit(() -> {
            try {
                javafx.scene.image.Image dibujada = vista.renderizar(pagina, escala);
                Platform.runLater(() -> {
                    if (orden == ordenDeDibujo) {
                        imagen.setImage(dibujada);
                    }
                });
            } catch (PdfDocumentView.VistaPreviaException e) {
                Platform.runLater(() -> panelDerecho.getChildren().add(PdfReviewPanel.texto(e.getMessage(), "#c62828")));
            }
        });
    }
}
