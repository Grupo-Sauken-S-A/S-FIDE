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

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Function;

/**
 * "Revisar": todo lo que se va a firmar, con el resultado de la revisión que hizo el programa de firma. La
 * persona puede desmarcar lo que no quiera; lo que el programa no puede firmar no se puede marcar.
 */
final class BatchReviewDialog {

    private BatchReviewDialog() {
    }

    /**
     * @param aviso       texto destacado (por ejemplo, que el lote mezcla tipos), o {@code null}
     * @param excluidos   cuántos archivos de la carpeta quedaron fuera por su nombre
     * @param sePuedeUsar si es falso solo se puede cerrar la ventana
     * @return si la persona aceptó la selección (queda guardada en cada fila)
     */
    static boolean mostrar(Window propietario, List<BatchRow> filas, String aviso, int excluidos,
                           boolean sePuedeUsar) {
        TableView<BatchRow> tabla = new TableView<>(FXCollections.observableArrayList(filas));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPrefSize(900, 420);

        TableColumn<BatchRow, BatchRow> marca = new TableColumn<>("Incluir");
        marca.setCellValueFactory(c -> Bindings.createObjectBinding(c::getValue));
        marca.setCellFactory(c -> new TableCell<>() {
            private final CheckBox casilla = new CheckBox();
            private BatchRow vinculada;

            @Override
            protected void updateItem(BatchRow fila, boolean vacia) {
                super.updateItem(fila, vacia);
                if (vinculada != null) {
                    casilla.selectedProperty().unbindBidirectional(vinculada.marcadoProperty());
                    vinculada = null;
                }
                if (vacia || fila == null) {
                    setGraphic(null);
                    return;
                }
                vinculada = fila;
                casilla.selectedProperty().bindBidirectional(fila.marcadoProperty());
                casilla.setDisable(!fila.seleccionable() || !sePuedeUsar);
                setGraphic(casilla);
            }
        });
        marca.setMinWidth(60);
        marca.setMaxWidth(70);
        tabla.getColumns().add(marca);
        tabla.getColumns().add(columna("Documento", f -> Bindings.createStringBinding(f::nombre), 320));
        tabla.getColumns().add(columna("Estado", BatchRow::estadoProperty, 130));
        tabla.getColumns().add(columna("Detalle", BatchRow::mensajeProperty, 360));

        VBox contenido = new VBox(8);
        contenido.setPadding(new Insets(6));
        if (aviso != null) {
            Label destacado = new Label("⛔ " + aviso);
            destacado.setWrapText(true);
            destacado.setMaxWidth(880);
            destacado.setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
            contenido.getChildren().add(destacado);
        }
        Label nota = new Label("El sufijo -signed se mira en el nombre del archivo: un documento firmado por otro "
                + "programa sin ese sufijo cuenta como sin sufijo."
                + (excluidos > 0 ? " Por su nombre quedaron fuera " + excluidos + " archivos de la carpeta." : ""));
        nota.setWrapText(true);
        nota.setMaxWidth(880);
        nota.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        contenido.getChildren().addAll(tabla, nota);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Revisar los documentos del lote");
        dialogo.setHeaderText(null);
        dialogo.setResizable(true);
        dialogo.initOwner(propietario);
        dialogo.getDialogPane().setContent(contenido);
        ButtonType aceptar = new ButtonType("Usar esta selección", ButtonBar.ButtonData.OK_DONE);
        if (sePuedeUsar) {
            dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);
        } else {
            dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        }
        return dialogo.showAndWait().filter(boton -> boton == aceptar).isPresent();
    }

    private static TableColumn<BatchRow, String> columna(String titulo,
            Function<BatchRow, javafx.beans.value.ObservableValue<String>> valor, double ancho) {
        TableColumn<BatchRow, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> valor.apply(c.getValue()));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacia) {
                super.updateItem(texto, vacia);
                setText(vacia ? null : texto);
                setTooltip(vacia || texto == null || texto.isEmpty() ? null : new Tooltip(texto));
            }
        });
        columna.setPrefWidth(ancho);
        return columna;
    }
}
