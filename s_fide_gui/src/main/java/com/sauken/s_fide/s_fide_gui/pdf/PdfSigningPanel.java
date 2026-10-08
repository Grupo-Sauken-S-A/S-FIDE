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

import com.sauken.s_fide.s_fide_gui.utils.GUIUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Todo lo que la persona elige sobre la firma visible de un PDF, listo para usar en cualquiera de las tres
 * pestañas de firma: dónde se dibuja (elegido sobre el propio documento), si es invisible, y si esta firma
 * cierra el documento o protege su contenido. Los tres firmadores reciben exactamente los mismos argumentos.
 */
public final class PdfSigningPanel {

    /** Lo que hay que agregar a la línea de comandos del firmador, y las dos opciones de cierre. */
    public record Pedido(List<String> argumentosDeUbicacion, boolean cerrar, boolean proteger) {
    }

    private static final float TAMANO_LETRA_POR_DEFECTO = 8.0f;

    private final String jar;
    private final Supplier<String> rutaDelPdf;
    private final Supplier<String> textoPersonalizado;
    private final Supplier<List<String>> credencialParaVistaPrevia;
    private final Supplier<Window> propietario;

    private SignaturePlacement ubicacion = SignaturePlacement.porDefecto();

    private final Label resumen = new Label();
    private final Button elegir = new Button("Elegir en el documento…");
    private final CheckBox invisible = new CheckBox("Firma invisible (no se dibuja nada en el documento)");
    private final CheckBox cerrar = new CheckBox("Cerrar el documento con esta firma (firma final)");
    private final CheckBox proteger = new CheckBox("Proteger el contenido (permite que otras personas sigan firmando)");

    /**
     * @param jar                       programa de firma que analiza el documento y calcula el texto de la firma
     * @param rutaDelPdf                ruta del documento elegido en la pestaña
     * @param textoPersonalizado        texto personalizado escrito en la pestaña (puede estar vacío)
     * @param credencialParaVistaPrevia argumentos que identifican el certificado, para que el firmador calcule el
     *                                  texto real de la firma (por ejemplo "-c archivo -p clave"); null o vacío si
     *                                  todavía no se puede, y entonces se muestra un ejemplo
     * @param propietario               ventana principal
     */
    public PdfSigningPanel(String jar, Supplier<String> rutaDelPdf, Supplier<String> textoPersonalizado,
                           Supplier<List<String>> credencialParaVistaPrevia, Supplier<Window> propietario) {
        this.jar = jar;
        this.rutaDelPdf = rutaDelPdf;
        this.textoPersonalizado = textoPersonalizado;
        this.credencialParaVistaPrevia = credencialParaVistaPrevia;
        this.propietario = propietario;

        resumen.setWrapText(true);
        resumen.setMaxWidth(520);
        elegir.setTooltip(new Tooltip("Muestra el documento para arrastrar la firma al lugar deseado, como en Acrobat"));
        elegir.setOnAction(e -> abrirSelector());

        invisible.selectedProperty().addListener((obs, antes, ahora) -> actualizar());
        invisible.setTooltip(new Tooltip("La firma es igual de válida, pero no se ve en el documento"));

        cerrar.setTooltip(new Tooltip("Úselo solo si es la última persona que va a firmar: después no se podrán "
                + "agregar más firmas"));
        proteger.setTooltip(new Tooltip("Úselo en la primera firma si quiere evitar que cambien el contenido pero "
                + "otras personas deben firmar después"));
        cerrar.selectedProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                proteger.setSelected(false);
            }
        });
        proteger.selectedProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                cerrar.setSelected(false);
            }
        });
        actualizar();
    }

    /** Fila de "Ubicación de la firma": resumen, botón para elegir sobre el documento y opción de firma invisible. */
    public Node nodoUbicacion() {
        VBox fila = new VBox(6, resumen, new HBox(12, elegir, invisible));
        return fila;
    }

    /** Opciones de cierre del documento. */
    public Node nodoOpciones() {
        return new VBox(4, cerrar, proteger);
    }

    public Pedido pedido() {
        List<String> argumentos = new ArrayList<>();
        if (invisible.isSelected() || ubicacion == null) {
            argumentos.addAll(List.of("-x", "0", "-y", "0"));
        } else {
            argumentos.addAll(ubicacion.argumentos());
        }
        return new Pedido(argumentos, cerrar.isSelected(), proteger.isSelected());
    }

    /** Después de firmar: las opciones de cierre se desmarcan siempre, para no cerrar un documento sin querer. */
    public void despuesDeFirmar() {
        Platform.runLater(() -> {
            cerrar.setSelected(false);
            proteger.setSelected(false);
        });
    }

    /** Vuelve a la posición habitual y a la firma visible. */
    public void reiniciar() {
        Platform.runLater(() -> {
            ubicacion = SignaturePlacement.porDefecto();
            invisible.setSelected(false);
            cerrar.setSelected(false);
            proteger.setSelected(false);
            actualizar();
        });
    }

    private void actualizar() {
        boolean sinFirmaVisible = invisible.isSelected();
        elegir.setDisable(sinFirmaVisible);
        resumen.setText(sinFirmaVisible
                ? "La firma será válida pero no se dibujará nada en el documento."
                : ubicacion.resumen());
    }

    // =====================================================================================

    private void abrirSelector() {
        String ruta = rutaDelPdf.get();
        if (ruta == null || ruta.isBlank() || !Files.isRegularFile(Path.of(ruta.trim()))) {
            Alert alerta = new Alert(Alert.AlertType.INFORMATION);
            alerta.setTitle("Elija primero el documento");
            alerta.setHeaderText(null);
            alerta.setContentText("Para ubicar la firma sobre el documento, primero elija el archivo PDF en el campo "
                    + "\"Archivo PDF\".");
            alerta.initOwner(propietario.get());
            alerta.showAndWait();
            return;
        }
        final Path pdf = Path.of(ruta.trim());
        final String personalizado = textoPersonalizado.get();
        final List<String> credencial = credencialParaVistaPrevia == null ? null : credencialParaVistaPrevia.get();

        GUIUtils.beginBusy();
        elegir.setDisable(true);
        CompletableFuture.supplyAsync(() -> consultar(pdf, personalizado, credencial)).whenComplete((datos, error) ->
                Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            avisar("No se pudo preparar la ventana de ubicación de la firma.");
                            return;
                        }
                        mostrarSelector(pdf, datos);
                    } finally {
                        elegir.setDisable(invisible.isSelected());
                        GUIUtils.endBusy();
                    }
                }));
    }

    /** Lo que se le pregunta a los firmadores antes de mostrar la ventana (se hace fuera del hilo de la interfaz). */
    private record Datos(PdfAnalysisReport informe, String textoDeLaFirma, float tamanoDeLetra) {
    }

    private Datos consultar(Path pdf, String personalizado, List<String> credencial) {
        GUIUtils.SalidaDeProceso analisis = GUIUtils.ejecutarYCapturar(jar,
                new String[]{"-analizar-documento", pdf.toString()}, 180);
        PdfAnalysisReport informe = analisis.codigo() == 0 ? PdfAnalysisReport.parse(analisis.lineas()) : null;

        String texto = null;
        float letra = TAMANO_LETRA_POR_DEFECTO;
        if (credencial != null && !credencial.isEmpty()) {
            List<String> args = new ArrayList<>();
            args.add("-vista-previa-texto");
            args.addAll(credencial);
            if (personalizado != null && !personalizado.isBlank()) {
                args.add("-t");
                args.add(personalizado);
            }
            GUIUtils.SalidaDeProceso vista = GUIUtils.ejecutarYCapturar(jar, args.toArray(new String[0]), 60);
            if (vista.codigo() == 0) {
                StringBuilder bloque = new StringBuilder();
                boolean dentro = false;
                for (String linea : vista.lineas()) {
                    if (linea.equals("TEXTO_FIRMA_INICIO")) {
                        dentro = true;
                    } else if (linea.equals("TEXTO_FIRMA_FIN")) {
                        dentro = false;
                    } else if (dentro) {
                        bloque.append(linea).append('\n');
                    } else if (linea.startsWith("TAMANO_LETRA: ")) {
                        try {
                            letra = Float.parseFloat(linea.substring("TAMANO_LETRA: ".length()).trim());
                        } catch (NumberFormatException ignorada) {
                            // se mantiene el tamaño habitual
                        }
                    }
                }
                if (!bloque.isEmpty()) {
                    texto = bloque.toString().stripTrailing();
                }
            }
        }
        if (texto == null) {
            texto = PdfSignaturePlacementDialog.textoDeEjemplo(personalizado);
        }
        boolean esEjemplo = texto.contains("<su nombre");
        return new Datos(informe, esEjemplo ? null : texto, letra);
    }

    private void mostrarSelector(Path pdf, Datos datos) {
        SignaturePlacement inicial = invisible.isSelected() ? SignaturePlacement.porDefecto() : ubicacion;
        Optional<SignaturePlacement> elegida = PdfSignaturePlacementDialog.mostrar(propietario.get(), pdf, inicial,
                datos.informe(), datos.textoDeLaFirma(), datos.tamanoDeLetra(), this::pedirAMano);
        elegida.ifPresent(u -> {
            ubicacion = u;
            invisible.setSelected(false);
            actualizar();
        });
    }

    /**
     * Respaldo para documentos que no se pueden mostrar (por ejemplo, protegidos con contraseña): se escriben
     * página y coordenadas a mano, igual que antes de existir la ventana de ubicación.
     */
    private void pedirAMano(String motivo) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Ubicación de la firma");
        dialogo.setHeaderText(motivo);
        dialogo.initOwner(propietario.get());
        ButtonType aceptar = new ButtonType("Usar estos valores", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);

        SignaturePlacement base = ubicacion != null ? ubicacion : SignaturePlacement.porDefecto();
        TextField pagina = new TextField(String.valueOf(base.pagina()));
        TextField x = new TextField(SignaturePlacement.numero(base.x()));
        TextField y = new TextField(SignaturePlacement.numero(base.y()));
        TextField ancho = new TextField(SignaturePlacement.numero(base.ancho() > 0 ? base.ancho() : 160));
        TextField alto = new TextField(SignaturePlacement.numero(base.alto() > 0 ? base.alto() : 70));
        GridPane cuadricula = new GridPane();
        cuadricula.setHgap(10);
        cuadricula.setVgap(8);
        cuadricula.setPadding(new Insets(10));
        cuadricula.addRow(0, new Label("Página:"), pagina);
        cuadricula.addRow(1, new Label("X (desde la izquierda):"), x);
        cuadricula.addRow(2, new Label("Y (desde abajo):"), y);
        cuadricula.addRow(3, new Label("Ancho:"), ancho);
        cuadricula.addRow(4, new Label("Alto:"), alto);
        Label ayuda = new Label("Las medidas son en puntos (1 punto = 1/72 de pulgada). Una hoja A4 mide 595 × 842. "
                + "El origen está en la esquina inferior izquierda de la hoja.");
        ayuda.setWrapText(true);
        ayuda.setMaxWidth(380);
        cuadricula.add(ayuda, 0, 5, 2, 1);
        dialogo.getDialogPane().setContent(cuadricula);

        Optional<ButtonType> respuesta = dialogo.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == aceptar) {
            try {
                ubicacion = new SignaturePlacement(Integer.parseInt(pagina.getText().trim()),
                        numero(x), numero(y), numero(ancho), numero(alto), null);
                invisible.setSelected(false);
                actualizar();
            } catch (NumberFormatException e) {
                avisar("Ingrese solo números (por ejemplo 40 o 55.5). No se cambió la ubicación.");
            }
        }
    }

    private static double numero(TextField campo) {
        return Double.parseDouble(campo.getText().trim().replace(',', '.'));
    }

    private void avisar(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle("Ubicación de la firma");
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.initOwner(propietario.get());
        alerta.showAndWait();
    }
}
