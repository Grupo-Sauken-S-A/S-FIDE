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


package com.sauken.s_fide.s_fide_gui.pdf;

import com.sauken.s_fide.s_fide_gui.pdf.PdfCoordinates.Pagina;
import com.sauken.s_fide.s_fide_gui.pdf.PdfCoordinates.Rectangulo;
import com.sauken.s_fide.s_fide_gui.pdf.PdfDocumentView.CampoDeFirma;
import javafx.application.Platform;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Ventana para ubicar la firma sobre el propio documento, como en Acrobat: se ve la página, se arrastra
 * el recuadro de la firma (con el texto real que va a llevar) y se lo agranda o achica con el mouse. Al
 * aceptar se devuelven la página y las coordenadas que entienden los firmadores; la ventana nunca firma
 * ni modifica el documento.
 * <p>
 * También sirve para revisar el documento antes de firmar: muestra las firmas que ya tiene, con su estado
 * explicado en lenguaje simple, y los campos de firma que el autor dejó preparados.
 */
public final class PdfSignaturePlacementDialog {

    private static final double ANCHO_MINIMO = 60;
    private static final double ALTO_MINIMO = 30;
    /** Relleno aproximado entre el borde del recuadro y su texto, en puntos. */
    private static final double RELLENO_TEXTO = 3;
    private static final double TAMANO_MANEJADOR = 9;

    private static final String VERDE = "#2e7d32";
    private static final String ROJO = "#c62828";
    private static final String AZUL = "#1565c0";
    private static final String GRIS = "#616161";

    private enum Manejador {NO, N, S, E, O, NE, NO_, SE, SO}

    private final Path pdf;
    private final PdfAnalysisReport informe;
    private final String textoVistaPrevia;
    private final float tamanoLetra;
    private final boolean textoDeEjemplo;
    private final Stage ventana = new Stage();
    private final PdfDocumentView vista;
    private final ExecutorService dibujo = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "dibujo-pdf");
        hilo.setDaemon(true);
        return hilo;
    });
    private final double escalaDePantalla = Math.max(1.0, Screen.getPrimary().getOutputScaleX());

    // --- estado: lo que la persona va eligiendo ---
    private int paginaActual = 1;
    private double zoom = 1.0;
    private Rectangulo ubicacion;          // en puntos PDF, sobre la página actual
    private String campoElegido;           // nombre del campo si firma en uno existente
    private SignaturePlacement resultado;
    private long ordenDeDibujo;            // descarta dibujos viejos si la persona ya cambió de página o zoom

    // --- pantalla ---
    private final ImageView imagenPagina = new ImageView();
    private final Pane capa = new Pane();
    private final Pane hoja = new Pane(imagenPagina, capa);
    private final ScrollPane desplazamiento = new ScrollPane(new StackPane(hoja));
    private final Label etiquetaPagina = new Label();
    private final Label etiquetaZoom = new Label();
    private final Label mensajeEstado = new Label();
    private final Button botonAceptar = new Button("Usar esta ubicación");
    private final TextField campoX = new TextField();
    private final TextField campoY = new TextField();
    private final TextField campoAncho = new TextField();
    private final TextField campoAlto = new TextField();
    private final Label etiquetaPaginaPanel = new Label();
    private final ListView<Integer> miniaturas = new ListView<>();
    private final Map<Integer, Image> cacheMiniaturas = new HashMap<>();
    private boolean actualizandoCampos;
    private boolean cambiandoPaginaDesdeLista;

    private final Rectangle caja = new Rectangle();
    private final Text textoCaja = new Text();
    private final Rectangle[] manejadores = new Rectangle[8];
    private final Manejador[] tiposDeManejador = {Manejador.NO_, Manejador.N, Manejador.NE, Manejador.E,
            Manejador.SE, Manejador.S, Manejador.SO, Manejador.O};

    // arrastre en curso
    private Manejador arrastre = Manejador.NO;
    private boolean moviendoCaja;
    private double inicioMouseX;
    private double inicioMouseY;
    private Rectangulo inicioPantalla;

    private PdfSignaturePlacementDialog(Window propietario, Path pdf, PdfDocumentView vista,
                                        SignaturePlacement inicial, PdfAnalysisReport informe,
                                        String textoVistaPrevia, boolean textoDeEjemplo, float tamanoLetra) {
        this.pdf = pdf;
        this.vista = vista;
        this.informe = informe;
        this.textoVistaPrevia = textoVistaPrevia;
        this.textoDeEjemplo = textoDeEjemplo;
        this.tamanoLetra = tamanoLetra;

        SignaturePlacement punto = inicial != null ? inicial : SignaturePlacement.porDefecto();
        if (punto.esEnCampoExistente()) {
            campoElegido = punto.campo();
            paginaActual = paginaDelCampo(punto.campo());
            ubicacion = rectanguloDelCampo(punto.campo());
        } else {
            paginaActual = Math.min(Math.max(1, punto.pagina()), vista.cantidadDePaginas());
            ubicacion = new Rectangulo(punto.x(), punto.y(), punto.ancho(), punto.alto());
        }
        if (ubicacion == null) {
            campoElegido = null;
            paginaActual = 1;
            ubicacion = new Rectangulo(40, 55, 160, 70);
        }
        ubicacion = buscarLugarLibre(ajustarADentroDeLaPagina(ubicacion));

        ventana.initOwner(propietario);
        ventana.initModality(Modality.WINDOW_MODAL);
        ventana.setTitle("Ubicar la firma en el documento — " + pdf.getFileName());
        ventana.setScene(new Scene(construirPantalla(), 1240, 820));
        ventana.setMinWidth(900);
        ventana.setMinHeight(600);
        ventana.setOnHidden(e -> {
            dibujo.shutdownNow();
            vista.close();
        });
    }

    /**
     * Abre la ventana y espera a que la persona acepte o cancele.
     *
     * @param inicial          ubicación con la que arranca (null: la clásica, abajo a la izquierda)
     * @param informe          lo que informó el firmador sobre el documento (puede ser null si no se pudo analizar)
     * @param textoVistaPrevia texto que llevará la firma, tal como lo calcula el firmador; null si no se pudo
     *                         obtener, y entonces se muestra un ejemplo
     * @param tamanoLetra      tamaño de letra de la firma, en puntos
     * @param alNoPoderMostrar qué hacer si el documento no se puede mostrar (recibe el motivo ya redactado para la
     *                         persona); si es null se muestra un aviso
     * @return la ubicación elegida, o vacío si se canceló o el documento no se puede mostrar
     */
    public static Optional<SignaturePlacement> mostrar(Window propietario, Path pdf, SignaturePlacement inicial,
                                                       PdfAnalysisReport informe, String textoVistaPrevia,
                                                       float tamanoLetra, Consumer<String> alNoPoderMostrar) {
        PdfDocumentView vista;
        try {
            vista = PdfDocumentView.abrir(pdf);
        } catch (PdfDocumentView.VistaPreviaException e) {
            if (alNoPoderMostrar != null) {
                alNoPoderMostrar.accept(e.getMessage());
            } else {
                Alert alerta = new Alert(Alert.AlertType.WARNING);
                alerta.setTitle("Vista previa no disponible");
                alerta.setHeaderText(null);
                alerta.setContentText(e.getMessage());
                alerta.initOwner(propietario);
                alerta.showAndWait();
            }
            return Optional.empty();
        }

        boolean ejemplo = textoVistaPrevia == null || textoVistaPrevia.isBlank();
        PdfSignaturePlacementDialog dialogo = new PdfSignaturePlacementDialog(propietario, pdf, vista, inicial,
                informe, ejemplo ? textoDeEjemplo("") : textoVistaPrevia, ejemplo, tamanoLetra);
        dialogo.ventana.setOnShown(e -> dialogo.ajustarPaginaCompleta());
        dialogo.ventana.showAndWait();
        return Optional.ofNullable(dialogo.resultado);
    }

    /** Muestra la ventana sin esperar (para pruebas y capturas de pantalla). */
    public static PdfSignaturePlacementDialog abrirSinEsperar(Window propietario, Path pdf, SignaturePlacement inicial,
                                                              PdfAnalysisReport informe, String textoVistaPrevia,
                                                              float tamanoLetra) throws PdfDocumentView.VistaPreviaException {
        PdfDocumentView vista = PdfDocumentView.abrir(pdf);
        boolean ejemplo = textoVistaPrevia == null || textoVistaPrevia.isBlank();
        PdfSignaturePlacementDialog dialogo = new PdfSignaturePlacementDialog(propietario, pdf, vista, inicial,
                informe, ejemplo ? textoDeEjemplo("") : textoVistaPrevia, ejemplo, tamanoLetra);
        dialogo.ventana.setOnShown(e -> dialogo.ajustarPaginaCompleta());
        dialogo.ventana.show();
        return dialogo;
    }

    public Stage ventana() {
        return ventana;
    }

    /** Texto de ejemplo con el mismo formato que dibuja el firmador, para cuando no se pudo consultar el real. */
    public static String textoDeEjemplo(String textoPersonalizado) {
        StringBuilder texto = new StringBuilder();
        if (textoPersonalizado != null && !textoPersonalizado.isBlank()) {
            texto.append(textoPersonalizado).append("\n\n");
        }
        texto.append("Firmado digitalmente por:\n<su nombre, según su certificado>\nFecha: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        return texto.toString();
    }

    // =====================================================================================
    //  Armado de la pantalla
    // =====================================================================================

    private BorderPane construirPantalla() {
        BorderPane raiz = new BorderPane();
        raiz.setTop(construirBarra());
        raiz.setLeft(construirMiniaturas());
        raiz.setCenter(construirZonaDeLaPagina());
        raiz.setRight(construirPanelDerecho());
        raiz.setBottom(construirPie());

        raiz.setOnKeyPressed(e -> {
            if (e.getTarget() instanceof TextField) {
                return;
            }
            double paso = e.isShiftDown() ? 10 : 1;
            switch (e.getCode()) {
                case LEFT -> moverUbicacionEnPantalla(-paso, 0);
                case RIGHT -> moverUbicacionEnPantalla(paso, 0);
                case UP -> moverUbicacionEnPantalla(0, -paso);
                case DOWN -> moverUbicacionEnPantalla(0, paso);
                case ESCAPE -> ventana.close();
                default -> {
                    return;
                }
            }
            e.consume();
        });
        return raiz;
    }

    private ToolBar construirBarra() {
        Button anterior = new Button("◀ Anterior");
        anterior.setOnAction(e -> irAPagina(paginaActual - 1));
        Button siguiente = new Button("Siguiente ▶");
        siguiente.setOnAction(e -> irAPagina(paginaActual + 1));

        Button menos = new Button("−");
        menos.setTooltip(new javafx.scene.control.Tooltip("Achicar la página"));
        menos.setOnAction(e -> cambiarZoom(zoom / 1.2));
        Button mas = new Button("+");
        mas.setTooltip(new javafx.scene.control.Tooltip("Agrandar la página"));
        mas.setOnAction(e -> cambiarZoom(zoom * 1.2));
        Button ancho = new Button("Ajustar al ancho");
        ancho.setOnAction(e -> ajustarAlAncho());
        Button completa = new Button("Página completa");
        completa.setOnAction(e -> ajustarPaginaCompleta());

        MenuButton sugeridas = new MenuButton("Posiciones sugeridas");
        MenuItem exportador = new MenuItem("Firma del Exportador (abajo a la izquierda)");
        exportador.setOnAction(e -> irAPosicionSugerida(40));
        MenuItem funcionario = new MenuItem("Firma del Funcionario Habilitado (abajo, a la derecha)");
        funcionario.setOnAction(e -> irAPosicionSugerida(310));
        sugeridas.getItems().addAll(exportador, funcionario);
        sugeridas.setTooltip(new javafx.scene.control.Tooltip(
                "Posiciones habituales de los Certificados de Origen y las Declaraciones Juradas de Origen"));

        etiquetaPagina.setStyle("-fx-font-weight: bold;");
        etiquetaZoom.setMinWidth(50);
        etiquetaZoom.setAlignment(Pos.CENTER);

        return new ToolBar(anterior, etiquetaPagina, siguiente, new Separator(), menos, etiquetaZoom, mas, ancho,
                completa, new Separator(), sugeridas);
    }

    private ListView<Integer> construirMiniaturas() {
        for (int i = 1; i <= vista.cantidadDePaginas(); i++) {
            miniaturas.getItems().add(i);
        }
        miniaturas.setStyle("-fx-control-inner-background: #eceff1; -fx-control-inner-background-alt: #eceff1;");
        miniaturas.setPrefWidth(130);
        miniaturas.setMinWidth(130);
        miniaturas.setCellFactory(lista -> new ListCell<>() {
            private final ImageView imagen = new ImageView();
            private final Label numero = new Label();
            private final VBox contenido = new VBox(3, imagen, numero);

            {
                contenido.setAlignment(Pos.CENTER);
                imagen.setFitWidth(90);
                imagen.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(Integer pagina, boolean vacia) {
                super.updateItem(pagina, vacia);
                if (vacia || pagina == null) {
                    setGraphic(null);
                    return;
                }
                numero.setText(String.valueOf(pagina));
                imagen.setImage(cacheMiniaturas.get(pagina));
                if (cacheMiniaturas.get(pagina) == null) {
                    pedirMiniatura(pagina);
                }
                setGraphic(contenido);
            }
        });
        miniaturas.getSelectionModel().selectedItemProperty().addListener((obs, anterior, nueva) -> {
            if (nueva != null && !cambiandoPaginaDesdeLista && nueva != paginaActual) {
                irAPagina(nueva);
            }
        });
        return miniaturas;
    }

    private void pedirMiniatura(int pagina) {
        dibujo.submit(() -> {
            try {
                Image imagen = vista.renderizar(pagina, 0.2);
                Platform.runLater(() -> {
                    cacheMiniaturas.put(pagina, imagen);
                    miniaturas.refresh();
                });
            } catch (PdfDocumentView.VistaPreviaException ignorada) {
                // Sin miniatura el número de página alcanza.
            }
        });
    }

    private ScrollPane construirZonaDeLaPagina() {
        hoja.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 10, 0, 0, 2);");
        StackPane centro = (StackPane) desplazamiento.getContent();
        centro.setPadding(new Insets(18));
        centro.setStyle("-fx-background-color: #8a8f98;");
        desplazamiento.setStyle("-fx-background: #8a8f98;");
        desplazamiento.setPannable(false);
        desplazamiento.viewportBoundsProperty().addListener((obs, a, b) -> centro.setMinSize(
                Math.max(0, b.getWidth() - 2), Math.max(0, b.getHeight() - 2)));

        // Ctrl + rueda: acercar o alejar, como en los visores de PDF.
        desplazamiento.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                cambiarZoom(e.getDeltaY() > 0 ? zoom * 1.1 : zoom / 1.1);
                e.consume();
            }
        });

        // Un clic sobre la página coloca el recuadro en ese lugar; si estaba elegido un campo existente,
        // el clic fuera del campo vuelve a la ubicación libre.
        capa.setOnMouseClicked(e -> {
            if (!e.isStillSincePress() || e.getTarget() != capa) {
                return;
            }
            if (campoElegido != null) {
                volverAUbicacionLibre();
                return;
            }
            Pagina geo = vista.pagina(paginaActual);
            Rectangulo actual = rectanguloEnPantalla();
            colocarEnPantalla(e.getX() / zoom - actual.ancho() / 2, e.getY() / zoom - actual.alto() / 2,
                    actual.ancho(), actual.alto(), geo);
        });

        caja.setStroke(Color.web(AZUL));
        caja.setStrokeWidth(2);
        caja.setStrokeType(StrokeType.INSIDE);
        caja.setCursor(Cursor.MOVE);
        textoCaja.setFill(Color.web("#1a1a1a"));
        textoCaja.setMouseTransparent(true);

        caja.setOnMousePressed(e -> comenzarArrastre(e, Manejador.NO, true));
        caja.setOnMouseDragged(this::continuarArrastre);
        caja.setOnMouseReleased(e -> terminarArrastre());
        for (int i = 0; i < manejadores.length; i++) {
            Rectangle m = new Rectangle(TAMANO_MANEJADOR, TAMANO_MANEJADOR);
            m.setFill(Color.WHITE);
            m.setStroke(Color.web(AZUL));
            m.setStrokeWidth(1.5);
            final Manejador tipo = tiposDeManejador[i];
            m.setCursor(cursorDe(tipo));
            m.setOnMousePressed(e -> comenzarArrastre(e, tipo, false));
            m.setOnMouseDragged(this::continuarArrastre);
            m.setOnMouseReleased(e -> terminarArrastre());
            manejadores[i] = m;
        }
        return desplazamiento;
    }

    private VBox construirPanelDerecho() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(330);
        panel.setMinWidth(330);

        // Estado del documento (cerrado, con problemas, etc.).
        VBox firmas = construirResumenDeFirmas();

        Label tituloPosicion = new Label("Posición de la firma");
        tituloPosicion.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        etiquetaPaginaPanel.setStyle("-fx-text-fill: " + GRIS + ";");

        for (TextField campo : new TextField[]{campoX, campoY, campoAncho, campoAlto}) {
            campo.setPrefWidth(80);
            campo.setOnAction(e -> aplicarCampos());
            campo.focusedProperty().addListener((obs, antes, ahora) -> {
                if (!ahora) {
                    aplicarCampos();
                }
            });
        }
        javafx.scene.layout.GridPane cuadricula = new javafx.scene.layout.GridPane();
        cuadricula.setHgap(8);
        cuadricula.setVgap(6);
        cuadricula.addRow(0, new Label("X:"), campoX, new Label("Y:"), campoY);
        cuadricula.addRow(1, new Label("Ancho:"), campoAncho, new Label("Alto:"), campoAlto);
        Label ayuda = new Label("En puntos (1 punto = 1/72 de pulgada). Una hoja A4 mide 595 × 842. "
                + "Normalmente no hace falta tocar estos valores: arrastre el recuadro con el mouse.");
        ayuda.setWrapText(true);
        ayuda.setStyle("-fx-text-fill: " + GRIS + "; -fx-font-size: 11px;");

        Label tituloTexto = new Label("Texto de la firma");
        tituloTexto.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label ayudaTexto = new Label(textoDeEjemplo
                ? "Se muestra con un nombre de ejemplo: el nombre real de su certificado y la fecha se completan al firmar."
                : "Así quedará escrito: su nombre real y la fecha de ahora. La hora exacta se registra al firmar.");
        ayudaTexto.setWrapText(true);
        ayudaTexto.setStyle("-fx-text-fill: " + GRIS + "; -fx-font-size: 11px;");

        panel.getChildren().addAll(firmas, new Separator());
        VBox preparados = construirCamposPreparados();
        if (preparados != null) {
            panel.getChildren().addAll(preparados, new Separator());
        }
        panel.getChildren().addAll(tituloPosicion, etiquetaPaginaPanel, cuadricula, ayuda,
                new Separator(), tituloTexto, ayudaTexto);

        ScrollPane contenedor = new ScrollPane(panel);
        contenedor.setFitToWidth(true);
        contenedor.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox salida = new VBox(contenedor);
        VBox.setVgrow(contenedor, Priority.ALWAYS);
        salida.setPrefWidth(346);
        return salida;
    }

    /** Lista de los campos de firma que el autor dejó preparados y todavía están vacíos, con un botón para cada uno. */
    private VBox construirCamposPreparados() {
        java.util.List<CampoDeFirma> vacios = vista.camposDeFirma().stream().filter(c -> !c.firmado()).toList();
        if (vacios.isEmpty() || (informe != null && informe.cerrado())) {
            return null;
        }
        VBox caja = new VBox(6);
        Label titulo = new Label("Campos de firma preparados");
        titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        caja.getChildren().addAll(titulo, texto("Quien armó el documento dejó estos lugares marcados para firmar:", GRIS));
        for (CampoDeFirma campo : vacios) {
            Label nombre = new Label("«" + campo.nombre() + "» · página " + campo.pagina());
            nombre.setWrapText(true);
            nombre.setMaxWidth(190);
            Button firmarAqui = new Button("Firmar aquí");
            firmarAqui.setOnAction(e -> {
                campoElegido = campo.nombre();
                ubicacion = campo.rectangulo();
                if (paginaActual != campo.pagina()) {
                    paginaActual = campo.pagina();
                    cargarPagina();
                } else {
                    redibujarCapa();
                }
            });
            HBox fila = new HBox(8, nombre, firmarAqui);
            fila.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            caja.getChildren().add(fila);
        }
        return caja;
    }

    private VBox construirResumenDeFirmas() {
        VBox caja = new VBox(8);

        Label titulo = new Label("Revisión del documento");
        titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        caja.getChildren().add(titulo);

        if (informe == null) {
            caja.getChildren().add(texto("No se pudieron analizar las firmas de este documento. Igualmente puede "
                    + "ubicar su firma.", GRIS));
            return caja;
        }

        if (informe.cerrado()) {
            Label cerrado = texto("⛔ " + informe.motivoCierre(), ROJO);
            cerrado.setStyle(cerrado.getStyle() + "; -fx-font-weight: bold;");
            caja.getChildren().add(cerrado);
        }

        if (informe.firmas().isEmpty()) {
            caja.getChildren().add(texto("El documento todavía no tiene firmas.", GRIS));
        }
        for (PdfAnalysisReport.Firma firma : informe.firmas()) {
            caja.getChildren().add(filaDeFirma(firma));
        }
        if (informe.avisoDocumento() != null) {
            caja.getChildren().add(texto("ⓘ " + informe.avisoDocumento(), informe.hayProblemas() ? ROJO : GRIS));
        }
        if (informe.notaDocumento() != null) {
            caja.getChildren().add(texto("ⓘ " + informe.notaDocumento(), GRIS));
        }
        if (informe.hayProblemas() && informe.recomendacion() != null) {
            caja.getChildren().add(texto(informe.recomendacion(), ROJO));
        }
        if (informe.contenidoProtegido()) {
            caja.getChildren().add(texto("🔒 El contenido del documento está protegido contra cambios, pero admite "
                    + "más firmas.", GRIS));
        }
        return caja;
    }

    private Label texto(String contenido, String color) {
        Label etiqueta = new Label(contenido);
        etiqueta.setWrapText(true);
        etiqueta.setMaxWidth(300);
        etiqueta.setStyle("-fx-text-fill: " + color + ";");
        return etiqueta;
    }

    private VBox filaDeFirma(PdfAnalysisReport.Firma firma) {
        String icono;
        String color;
        if (firma.esInvalida()) {
            icono = "✖";
            color = ROJO;
        } else if (firma.esNoVerificable()) {
            icono = "✔";
            color = AZUL;
        } else {
            icono = "✔";
            color = VERDE;
        }
        Label marca = new Label(icono);
        marca.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label mensaje = texto(firma.mensaje() != null ? firma.mensaje() : firma.firmante(), firma.esInvalida() ? ROJO : "#212121");
        mensaje.setMaxWidth(270);
        HBox cabecera = new HBox(8, marca, mensaje);
        cabecera.setAlignment(Pos.TOP_LEFT);

        VBox fila = new VBox(2, cabecera);
        boolean hayDetalle = !firma.notas().isEmpty() || firma.revocacion() != null;
        if (hayDetalle) {
            VBox detalle = new VBox(4);
            for (String nota : firma.notas()) {
                detalle.getChildren().add(texto(nota, GRIS));
            }
            if (firma.revocacion() != null) {
                detalle.getChildren().add(texto("Estado del certificado: " + firma.revocacion(), GRIS));
            }
            TitledPane plegable = new TitledPane("Ver detalle", detalle);
            plegable.setExpanded(false);
            plegable.setStyle("-fx-font-size: 11px;");
            fila.getChildren().add(plegable);
        }
        return fila;
    }

    private HBox construirPie() {
        Button cancelar = new Button("Cancelar");
        cancelar.setCancelButton(true);
        cancelar.setOnAction(e -> ventana.close());

        botonAceptar.setDefaultButton(true);
        botonAceptar.setOnAction(e -> {
            resultado = ubicacionActual();
            ventana.close();
        });

        mensajeEstado.setWrapText(true);
        HBox.setHgrow(mensajeEstado, Priority.ALWAYS);
        mensajeEstado.setMaxWidth(Double.MAX_VALUE);

        HBox pie = new HBox(12, mensajeEstado, cancelar, botonAceptar);
        pie.setAlignment(Pos.CENTER_RIGHT);
        pie.setPadding(new Insets(10, 14, 10, 14));
        pie.setStyle("-fx-border-color: #cfd2d6; -fx-border-width: 1 0 0 0;");
        return pie;
    }

    // =====================================================================================
    //  Página, zoom y dibujo
    // =====================================================================================

    private void irAPagina(int numero) {
        if (numero < 1 || numero > vista.cantidadDePaginas() || numero == paginaActual) {
            return;
        }
        paginaActual = numero;
        if (campoElegido != null && paginaDelCampo(campoElegido) != paginaActual) {
            campoElegido = null;
        }
        ubicacion = ajustarADentroDeLaPagina(ubicacion);
        cargarPagina();
    }

    private void cambiarZoom(double nuevo) {
        zoom = Math.max(0.3, Math.min(4.0, nuevo));
        cargarPagina();
    }

    private void ajustarAlAncho() {
        double disponible = desplazamiento.getViewportBounds().getWidth() - 60;
        Pagina geo = vista.pagina(paginaActual);
        if (disponible > 100) {
            zoom = Math.max(0.3, Math.min(4.0, disponible / geo.anchoEnPantalla()));
        }
        cargarPagina();
    }

    private void ajustarPaginaCompleta() {
        Pagina geo = vista.pagina(paginaActual);
        double ancho = (desplazamiento.getViewportBounds().getWidth() - 60) / geo.anchoEnPantalla();
        double alto = (desplazamiento.getViewportBounds().getHeight() - 60) / geo.altoEnPantalla();
        zoom = Math.max(0.3, Math.min(4.0, Math.min(ancho, alto)));
        cargarPagina();
    }

    private void cargarPagina() {
        Pagina geo = vista.pagina(paginaActual);
        double anchoPantalla = geo.anchoEnPantalla() * zoom;
        double altoPantalla = geo.altoEnPantalla() * zoom;
        hoja.setPrefSize(anchoPantalla, altoPantalla);
        hoja.setMinSize(anchoPantalla, altoPantalla);
        hoja.setMaxSize(anchoPantalla, altoPantalla);
        capa.setPrefSize(anchoPantalla, altoPantalla);
        imagenPagina.setFitWidth(anchoPantalla);
        imagenPagina.setFitHeight(altoPantalla);

        etiquetaPagina.setText("Página " + paginaActual + " de " + vista.cantidadDePaginas());
        etiquetaZoom.setText(Math.round(zoom * 100) + " %");
        cambiandoPaginaDesdeLista = true;
        miniaturas.getSelectionModel().select(Integer.valueOf(paginaActual));
        miniaturas.scrollTo(Integer.valueOf(paginaActual));
        cambiandoPaginaDesdeLista = false;

        redibujarCapa();

        final long orden = ++ordenDeDibujo;
        final int pagina = paginaActual;
        final double escala = zoom * escalaDePantalla;
        dibujo.submit(() -> {
            try {
                Image imagen = vista.renderizar(pagina, escala);
                Platform.runLater(() -> {
                    if (orden == ordenDeDibujo) {
                        imagenPagina.setImage(imagen);
                    }
                });
            } catch (PdfDocumentView.VistaPreviaException e) {
                Platform.runLater(() -> mostrarAviso(e.getMessage()));
            }
        });
    }

    private void mostrarAviso(String mensaje) {
        mensajeEstado.setStyle("-fx-text-fill: " + ROJO + ";");
        mensajeEstado.setText(mensaje);
    }

    // =====================================================================================
    //  Capa de encima de la página: firmas existentes, campos vacíos y recuadro de la firma nueva
    // =====================================================================================

    private void redibujarCapa() {
        capa.getChildren().clear();
        Pagina geo = vista.pagina(paginaActual);

        for (CampoDeFirma campo : vista.camposDeFirma()) {
            if (campo.pagina() != paginaActual) {
                continue;
            }
            Rectangulo r = PdfCoordinates.rectanguloAPantalla(geo, campo.rectangulo());
            Rectangle marco = new Rectangle(r.x() * zoom, r.y() * zoom, r.ancho() * zoom, r.alto() * zoom);
            if (campo.firmado()) {
                marco.setFill(Color.web("#9e9e9e", 0.35));
                marco.setStroke(Color.web("#616161"));
                marco.getStrokeDashArray().setAll(4.0, 3.0);
                String quien = firmanteDelCampo(campo.nombre());
                Label etiqueta = new Label("Firmado" + (quien != null ? " por " + quien : ""));
                etiqueta.setStyle("-fx-font-size: 10px; -fx-text-fill: #424242; -fx-background-color: rgba(255,255,255,0.8);"
                        + " -fx-padding: 1 3 1 3;");
                etiqueta.setLayoutX(marco.getX() + 2);
                etiqueta.setLayoutY(marco.getY() + 2);
                etiqueta.setMouseTransparent(true);
                marco.setMouseTransparent(true);
                capa.getChildren().addAll(marco, etiqueta);
            } else {
                boolean elegido = campo.nombre().equals(campoElegido);
                marco.setFill(Color.web(AZUL, elegido ? 0.20 : 0.08));
                marco.setStroke(Color.web(AZUL));
                marco.getStrokeDashArray().setAll(6.0, 4.0);
                marco.setCursor(Cursor.HAND);
                Label etiqueta = new Label(elegido ? "Se firmará aquí" : "Campo de firma: clic para firmar aquí");
                etiqueta.setStyle("-fx-font-size: 10px; -fx-text-fill: " + AZUL + ";"
                        + " -fx-background-color: rgba(255,255,255,0.85); -fx-padding: 1 3 1 3;");
                etiqueta.setLayoutX(marco.getX() + 2);
                etiqueta.setLayoutY(marco.getY() + 2);
                etiqueta.setMouseTransparent(true);
                marco.setOnMouseClicked(e -> elegirCampo(campo));
                capa.getChildren().addAll(marco, etiqueta);
            }
        }

        if (campoElegido == null || !campoElegidoEnPaginaActual()) {
            capa.getChildren().add(caja);
            capa.getChildren().add(textoCaja);
            capa.getChildren().addAll(manejadores);
        } else {
            // Firma dentro de un campo existente: el campo ya está marcado arriba; se muestra el texto adentro.
            capa.getChildren().add(textoCaja);
        }
        refrescarCaja();
    }

    private boolean campoElegidoEnPaginaActual() {
        return campoElegido != null && paginaDelCampo(campoElegido) == paginaActual;
    }

    private String firmanteDelCampo(String nombreCampo) {
        if (informe == null) {
            return null;
        }
        return informe.firmas().stream()
                .filter(f -> nombreCampo.equals(f.campo()))
                .map(PdfAnalysisReport.Firma::firmante)
                .findFirst().orElse(null);
    }

    private void elegirCampo(CampoDeFirma campo) {
        campoElegido = campo.nombre();
        ubicacion = campo.rectangulo();
        redibujarCapa();
    }

    private void volverAUbicacionLibre() {
        if (campoElegido == null) {
            return;
        }
        campoElegido = null;
        ubicacion = ajustarADentroDeLaPagina(new Rectangulo(ubicacion.x(), ubicacion.y(),
                Math.max(ubicacion.ancho(), 160), Math.max(ubicacion.alto(), 70)));
        redibujarCapa();
    }

    private int paginaDelCampo(String nombre) {
        return vista.camposDeFirma().stream().filter(c -> c.nombre().equals(nombre) && !c.firmado())
                .map(CampoDeFirma::pagina).findFirst().orElse(1);
    }

    private Rectangulo rectanguloDelCampo(String nombre) {
        return vista.camposDeFirma().stream().filter(c -> c.nombre().equals(nombre) && !c.firmado())
                .map(CampoDeFirma::rectangulo).findFirst().orElse(null);
    }

    // =====================================================================================
    //  Recuadro de la firma nueva: dibujo, arrastre y cambio de tamaño
    // =====================================================================================

    /** El recuadro actual en puntos de pantalla (sin zoom), ya considerando la rotación de la página. */
    private Rectangulo rectanguloEnPantalla() {
        return PdfCoordinates.rectanguloAPantalla(vista.pagina(paginaActual), ubicacion);
    }

    private void refrescarCaja() {
        Rectangulo r = rectanguloEnPantalla();
        double x = r.x() * zoom;
        double y = r.y() * zoom;
        double ancho = r.ancho() * zoom;
        double alto = r.alto() * zoom;

        boolean libre = campoElegido == null;
        caja.setVisible(libre);
        textoCaja.setVisible(true);
        for (Rectangle m : manejadores) {
            m.setVisible(libre);
        }

        caja.setX(x);
        caja.setY(y);
        caja.setWidth(ancho);
        caja.setHeight(alto);

        double tamano = tamanoLetra * zoom;
        textoCaja.setFont(Font.font("Helvetica", FontWeight.NORMAL, tamano));
        textoCaja.setText(textoVistaPrevia);
        double relleno = RELLENO_TEXTO * zoom;
        textoCaja.setWrappingWidth(Math.max(10, ancho - 2 * relleno));
        textoCaja.setX(x + relleno);
        textoCaja.setY(y + relleno + tamano);
        double alturaTexto = textoCaja.getLayoutBounds().getHeight();
        boolean sobra = alturaTexto > alto - 2 * relleno + 1;
        textoCaja.setClip(new Rectangle(x + relleno, y, Math.max(0, ancho - 2 * relleno), alto));

        String conflicto = firmaTapada(ubicacion);
        boolean hayError = conflicto != null;
        caja.setFill(Color.web(hayError ? ROJO : AZUL, hayError ? 0.18 : 0.10));
        caja.setStroke(Color.web(hayError || sobra ? ROJO : AZUL));

        double[][] posiciones = {
                {x, y}, {x + ancho / 2, y}, {x + ancho, y}, {x + ancho, y + alto / 2},
                {x + ancho, y + alto}, {x + ancho / 2, y + alto}, {x, y + alto}, {x, y + alto / 2}};
        for (int i = 0; i < manejadores.length; i++) {
            manejadores[i].setX(posiciones[i][0] - TAMANO_MANEJADOR / 2);
            manejadores[i].setY(posiciones[i][1] - TAMANO_MANEJADOR / 2);
        }

        actualizarCamposYEstado(sobra, conflicto);
    }

    private void actualizarCamposYEstado(boolean sobraTexto, String conflicto) {
        actualizandoCampos = true;
        boolean libre = campoElegido == null;
        etiquetaPaginaPanel.setText(libre ? "Página " + paginaActual + " de " + vista.cantidadDePaginas()
                : "Se firmará en el campo \"" + campoElegido + "\"");
        campoX.setText(SignaturePlacement.numero(ubicacion.x()));
        campoY.setText(SignaturePlacement.numero(ubicacion.y()));
        campoAncho.setText(SignaturePlacement.numero(ubicacion.ancho()));
        campoAlto.setText(SignaturePlacement.numero(ubicacion.alto()));
        for (TextField campo : new TextField[]{campoX, campoY, campoAncho, campoAlto}) {
            campo.setDisable(!libre);
        }
        actualizandoCampos = false;

        boolean cerrado = informe != null && informe.cerrado();
        String mensaje;
        String color = GRIS;
        boolean aceptable = true;

        if (cerrado) {
            mensaje = "Este documento está cerrado y no admite más firmas.";
            color = ROJO;
            aceptable = false;
        } else if (conflicto != null) {
            mensaje = "El recuadro tapa " + conflicto + ". Muévalo a un lugar libre.";
            color = ROJO;
            aceptable = false;
        } else if (sobraTexto && libre) {
            mensaje = "El texto no entra en el recuadro: agrándelo o use un texto personalizado más corto.";
            color = ROJO;
        } else if (!libre) {
            mensaje = "La firma se dibujará dentro del campo preparado en el documento. "
                    + "Haga clic en la página, fuera del campo, para colocarla en otro lugar.";
        } else {
            mensaje = "Arrastre el recuadro para moverlo, o sus bordes para cambiar el tamaño. "
                    + "También puede hacer clic en la página para colocarlo ahí.";
        }
        mensajeEstado.setStyle("-fx-text-fill: " + color + ";");
        mensajeEstado.setText(mensaje);
        botonAceptar.setDisable(!aceptable);
    }


    /** Nombre de la firma que el recuadro estaría tapando, o null si no tapa ninguna. */
    private String firmaTapada(Rectangulo recuadro) {
        if (campoElegido != null) {
            return null;
        }
        for (CampoDeFirma campo : vista.camposDeFirma()) {
            if (campo.pagina() == paginaActual && campo.firmado() && seCruzan(recuadro, campo.rectangulo())) {
                String quien = firmanteDelCampo(campo.nombre());
                return quien != null ? "la firma de " + quien : "una firma ya existente";
            }
        }
        return null;
    }

    /**
     * Si el lugar elegido tapa una firma que ya está en la página (lo habitual cuando la posición de
     * siempre ya la ocupó otra persona), busca el primer lugar libre: primero las dos columnas
     * habituales de abajo y después hacia arriba. Si no encuentra ninguno deja el pedido tal cual.
     */
    private Rectangulo buscarLugarLibre(Rectangulo deseado) {
        if (campoElegido != null || firmaTapada(deseado) == null) {
            return deseado;
        }
        Pagina geo = vista.pagina(paginaActual);
        double separacion = 12;
        for (double y = geo.y() + 55; y + deseado.alto() <= geo.y() + geo.alto(); y += deseado.alto() + separacion) {
            for (double x = geo.x() + 40; x + deseado.ancho() <= geo.x() + geo.ancho();
                 x += deseado.ancho() + 40) {
                Rectangulo candidato = new Rectangulo(x, y, deseado.ancho(), deseado.alto());
                if (firmaTapada(candidato) == null) {
                    return candidato;
                }
            }
        }
        return deseado;
    }

    private static boolean seCruzan(Rectangulo a, Rectangulo b) {
        return a.x() < b.x() + b.ancho() && a.x() + a.ancho() > b.x()
                && a.y() < b.y() + b.alto() && a.y() + a.alto() > b.y();
    }

    private void comenzarArrastre(MouseEvent e, Manejador tipo, boolean mover) {
        moviendoCaja = mover;
        arrastre = tipo;
        inicioMouseX = e.getSceneX();
        inicioMouseY = e.getSceneY();
        inicioPantalla = rectanguloEnPantalla();
        e.consume();
    }

    private void continuarArrastre(MouseEvent e) {
        if (inicioPantalla == null) {
            return;
        }
        double dx = (e.getSceneX() - inicioMouseX) / zoom;
        double dy = (e.getSceneY() - inicioMouseY) / zoom;
        Pagina geo = vista.pagina(paginaActual);
        Rectangulo base = inicioPantalla;

        if (moviendoCaja) {
            colocarEnPantalla(base.x() + dx, base.y() + dy, base.ancho(), base.alto(), geo);
        } else {
            double x1 = base.x();
            double y1 = base.y();
            double x2 = base.x() + base.ancho();
            double y2 = base.y() + base.alto();
            switch (arrastre) {
                case NO_ -> {
                    x1 += dx;
                    y1 += dy;
                }
                case N -> y1 += dy;
                case NE -> {
                    x2 += dx;
                    y1 += dy;
                }
                case E -> x2 += dx;
                case SE -> {
                    x2 += dx;
                    y2 += dy;
                }
                case S -> y2 += dy;
                case SO -> {
                    x1 += dx;
                    y2 += dy;
                }
                case O -> x1 += dx;
                default -> {
                }
            }
            double anchoMax = geo.anchoEnPantalla();
            double altoMax = geo.altoEnPantalla();
            x1 = Math.max(0, Math.min(x1, x2 - ANCHO_MINIMO));
            y1 = Math.max(0, Math.min(y1, y2 - ALTO_MINIMO));
            x2 = Math.min(anchoMax, Math.max(x2, x1 + ANCHO_MINIMO));
            y2 = Math.min(altoMax, Math.max(y2, y1 + ALTO_MINIMO));
            colocarEnPantalla(x1, y1, x2 - x1, y2 - y1, geo);
        }
        e.consume();
    }

    private void terminarArrastre() {
        inicioPantalla = null;
        arrastre = Manejador.NO;
        moviendoCaja = false;
    }

    /** Coloca el recuadro, dado en puntos de pantalla, dentro de la página. */
    private void colocarEnPantalla(double x, double y, double ancho, double alto, Pagina geo) {
        double maxX = Math.max(0, geo.anchoEnPantalla() - ancho);
        double maxY = Math.max(0, geo.altoEnPantalla() - alto);
        double cx = Math.max(0, Math.min(x, maxX));
        double cy = Math.max(0, Math.min(y, maxY));
        ubicacion = PdfCoordinates.rectanguloAPdf(geo, new Rectangulo(cx, cy, ancho, alto));
        refrescarCaja();
    }

    private void moverUbicacionEnPantalla(double dx, double dy) {
        if (campoElegido != null) {
            return;
        }
        Rectangulo r = rectanguloEnPantalla();
        colocarEnPantalla(r.x() + dx, r.y() + dy, r.ancho(), r.alto(), vista.pagina(paginaActual));
    }

    private void irAPosicionSugerida(double x) {
        campoElegido = null;
        Pagina geo = vista.pagina(paginaActual);
        double ancho = Math.max(ANCHO_MINIMO, ubicacion.ancho());
        double alto = Math.max(ALTO_MINIMO, ubicacion.alto());
        ubicacion = ajustarADentroDeLaPagina(new Rectangulo(geo.x() + x, geo.y() + 55, ancho, alto));
        redibujarCapa();
    }

    private void aplicarCampos() {
        if (actualizandoCampos || campoElegido != null) {
            return;
        }
        try {
            double x = Double.parseDouble(campoX.getText().replace(',', '.'));
            double y = Double.parseDouble(campoY.getText().replace(',', '.'));
            double ancho = Double.parseDouble(campoAncho.getText().replace(',', '.'));
            double alto = Double.parseDouble(campoAlto.getText().replace(',', '.'));
            ubicacion = ajustarADentroDeLaPagina(new Rectangulo(x, y, Math.max(ANCHO_MINIMO, ancho),
                    Math.max(ALTO_MINIMO, alto)));
        } catch (NumberFormatException e) {
            mostrarAviso("Ingrese solo números en la posición (por ejemplo 40 o 55.5).");
        }
        refrescarCaja();
    }

    /** Mantiene el recuadro dentro del área visible de la página actual. */
    private Rectangulo ajustarADentroDeLaPagina(Rectangulo r) {
        Pagina geo = vista.pagina(paginaActual);
        double ancho = Math.min(r.ancho(), geo.ancho());
        double alto = Math.min(r.alto(), geo.alto());
        double x = Math.max(geo.x(), Math.min(r.x(), geo.x() + geo.ancho() - ancho));
        double y = Math.max(geo.y(), Math.min(r.y(), geo.y() + geo.alto() - alto));
        return new Rectangulo(x, y, ancho, alto);
    }

    private SignaturePlacement ubicacionActual() {
        if (campoElegido != null) {
            return new SignaturePlacement(paginaDelCampo(campoElegido), 0, 0, 0, 0, campoElegido);
        }
        return new SignaturePlacement(paginaActual, ubicacion.x(), ubicacion.y(), ubicacion.ancho(),
                ubicacion.alto(), null);
    }

    private static Cursor cursorDe(Manejador tipo) {
        return switch (tipo) {
            case N, S -> Cursor.N_RESIZE;
            case E, O -> Cursor.E_RESIZE;
            case NE, SO -> Cursor.NE_RESIZE;
            case NO_, SE -> Cursor.NW_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

}
