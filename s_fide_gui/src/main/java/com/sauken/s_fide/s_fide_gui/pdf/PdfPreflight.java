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
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

/**
 * Revisión previa a firmar un PDF. Antes de aplicar una firma se le pide al propio programa de firma que
 * analice el documento (sin certificado ni contraseña) y, según lo que informe:
 * <ul>
 *   <li>documento cerrado: no se firma, y se explica por qué;</li>
 *   <li>firmas anteriores con problemas reales: se informa y se pregunta si se quiere firmar igual
 *   (informar y seguir: la decisión es de la persona);</li>
 *   <li>cierre del documento: se confirma que será la firma final.</li>
 * </ul>
 * Lo demás (avisos suaves, notas) no interrumpe: está a la vista en la ventana de ubicación de la firma.
 * El análisis lo hace el firmador; acá solo se interpreta su informe y se conversa con la persona.
 */
public final class PdfPreflight {

    /** Qué se va a firmar: el programa que lo hará, el documento y si la firma cierra el documento. */
    public record Pedido(String jar, String pdf, boolean cerrar) {
    }

    private PdfPreflight() {
    }

    /**
     * Revisa el documento y llama a {@code seguir} si corresponde firmar, o a {@code cancelado} (con el
     * código 1) si no. Se puede llamar desde el hilo de la interfaz; no la bloquea.
     */
    public static void revisar(Window propietario, Pedido pedido, TextArea salida, Runnable seguir,
                               IntConsumer cancelado) {
        GUIUtils.beginBusy();
        Platform.runLater(() -> salida.appendText("Analizando el documento y sus firmas…\n"));

        CompletableFuture.runAsync(() -> {
            GUIUtils.SalidaDeProceso respuesta = GUIUtils.ejecutarYCapturar(pedido.jar(),
                    new String[]{"-analizar-documento", pedido.pdf()}, 180);
            PdfAnalysisReport informe = respuesta.codigo() == 0 ? PdfAnalysisReport.parse(respuesta.lineas()) : null;
            String error = informe == null ? mensajeDeError(respuesta) : null;
            Platform.runLater(() -> decidir(propietario, pedido, informe, error, salida, seguir, cancelado));
        });
    }

    private static void decidir(Window propietario, PdfPreflight.Pedido pedido, PdfAnalysisReport informe, String error,
                                TextArea salida, Runnable seguir, IntConsumer cancelado) {
        try {
            if (informe == null) {
                salida.appendText("Error: " + error + "\n");
                avisar(propietario, "No se puede firmar el documento", error);
                cancelado.accept(1);
                return;
            }
            if (informe.cerrado()) {
                salida.appendText("Error: " + informe.motivoCierre() + "\n");
                avisar(propietario, "El documento está cerrado", informe.motivoCierre());
                cancelado.accept(1);
                return;
            }

            if (informe.hayProblemas() && !confirmar(propietario, "Revise las firmas del documento",
                    "Este documento tiene firmas con problemas", textoDeProblemas(informe),
                    "Firmar de todas formas", true)) {
                cancelar(salida, cancelado);
                return;
            }

            if (pedido.cerrar() && !confirmar(propietario,
                    informe.cantidadDeFirmasDePersonas() > 0 ? "Firma final del documento" : "Bloquear el documento",
                    informe.cantidadDeFirmasDePersonas() > 0 ? "Esta será la firma final" : "Nadie más podrá firmar",
                    textoDeCierre(informe),
                    informe.cantidadDeFirmasDePersonas() > 0 ? "Firmar y cerrar" : "Firmar y bloquear",
                    false)) {
                cancelar(salida, cancelado);
                return;
            }

            seguir.run();
        } finally {
            // La operación real (si se aprobó) ya tomó su propia espera dentro de seguir.run().
            GUIUtils.endBusy();
        }
    }

    private static void cancelar(TextArea salida, IntConsumer cancelado) {
        salida.appendText("Firma cancelada. No se modificó ningún archivo.\n");
        cancelado.accept(1);
    }

    static String textoDeProblemas(PdfAnalysisReport informe) {
        StringBuilder texto = new StringBuilder();
        for (PdfAnalysisReport.Firma firma : informe.firmas()) {
            if (firma.esInvalida()) {
                texto.append("• ").append(firma.mensaje()).append("\n\n");
            }
        }
        if (informe.avisoDocumento() != null && informe.hayProblemas()) {
            texto.append("• ").append(informe.avisoDocumento()).append("\n\n");
        }
        if (informe.recomendacion() != null) {
            texto.append(informe.recomendacion());
        }
        return texto.toString().trim();
    }

    static String textoDeCierre(PdfAnalysisReport informe) {
        if (informe.cantidadDeFirmasDePersonas() > 0) {
            return "Al firmar, el documento quedará cerrado: no se podrán agregar más firmas y cualquier cambio "
                    + "posterior hará que las firmas dejen de ser válidas.\n\nSi otras personas todavía tienen que "
                    + "firmar, cancele y desmarque la opción de cerrar el documento.";
        }
        return "Al bloquear el documento, nadie más podrá agregarle firmas.\n\nSi otras personas tienen que firmar "
                + "después, cancele y desmarque esa opción. Para evitar que se modifique el contenido y permitir más "
                + "firmas, use «Proteger el contenido».";
    }

    private static String mensajeDeError(GUIUtils.SalidaDeProceso respuesta) {
        if (respuesta.agotoTiempo()) {
            return "El análisis del documento tardó demasiado y se canceló. Verifique su conexión a Internet e "
                    + "intente de nuevo.";
        }
        List<String> lineas = respuesta.lineas();
        for (String linea : lineas) {
            if (linea.startsWith("Error: ")) {
                return linea.substring("Error: ".length());
            }
        }
        return "No se pudo analizar el documento. Verifique que el archivo sea un PDF válido.";
    }

    private static void avisar(Window propietario, String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.initOwner(propietario);
        alerta.showAndWait();
    }

    /**
     * Pregunta con dos botones: el de aceptar, que lleva el nombre de la acción, y Cancelar.
     *
     * @param aceptarPorDefecto si es falso, la tecla Enter elige Cancelar: se usa para lo que no se puede
     *                          deshacer (cerrar un documento), así no se hace por un Enter distraído
     */
    private static boolean confirmar(Window propietario, String titulo, String encabezado, String texto,
                                     String textoAceptar, boolean aceptarPorDefecto) {
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alerta = new Alert(Alert.AlertType.WARNING, "", aceptar, cancelar);
        alerta.setTitle(titulo);
        alerta.setHeaderText(encabezado);
        alerta.initOwner(propietario);

        Label contenido = new Label(texto);
        contenido.setWrapText(true);
        contenido.setMaxWidth(500);
        ScrollPane desplazamiento = new ScrollPane(new VBox(contenido));
        desplazamiento.setFitToWidth(true);
        desplazamiento.setPrefViewportWidth(520);
        desplazamiento.setPrefViewportHeight(Math.min(320, 60 + texto.length() / 2.2));
        desplazamiento.setStyle("-fx-background-color: transparent;");
        alerta.getDialogPane().setContent(desplazamiento);
        ((javafx.scene.control.Button) alerta.getDialogPane().lookupButton(aceptar))
                .setDefaultButton(aceptarPorDefecto);
        ((javafx.scene.control.Button) alerta.getDialogPane().lookupButton(cancelar))
                .setDefaultButton(!aceptarPorDefecto);

        Optional<ButtonType> respuesta = alerta.showAndWait();
        return respuesta.isPresent() && respuesta.get() == aceptar;
    }
}
