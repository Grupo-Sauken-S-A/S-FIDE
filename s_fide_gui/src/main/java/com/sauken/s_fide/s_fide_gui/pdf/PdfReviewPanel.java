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

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Panel "Revisión del documento": el estado de cada firma de un PDF en palabras simples, tal como lo
 * informa el propio firmador. Lo comparten la ventana de ubicación de la firma y el visor de PDF.
 * <p>
 * Respeta la política de mensajes para quien no es técnico: solo una firma realmente inválida se presenta en
 * rojo; los datos de contexto (un certificado que venció después de firmar, por ejemplo) quedan en un
 * "Ver detalle" plegado, sin alarma.
 */
public final class PdfReviewPanel {

    static final String VERDE = "#2e7d32";
    static final String ROJO = "#c62828";
    static final String AZUL = "#1565c0";
    static final String GRIS = "#616161";

    private PdfReviewPanel() {
    }

    /**
     * @param informe             lo que informó el firmador; {@code null} si no se pudo analizar el documento
     * @param textoSiNoHayAnalisis qué decir en ese caso (depende de para qué se muestra el panel)
     */
    public static VBox crear(PdfAnalysisReport informe, String textoSiNoHayAnalisis) {
        VBox caja = new VBox(8);

        Label titulo = new Label("Revisión del documento");
        titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        caja.getChildren().add(titulo);

        if (informe == null) {
            caja.getChildren().add(texto(textoSiNoHayAnalisis, GRIS));
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

    public static Label texto(String contenido, String color) {
        Label etiqueta = new Label(contenido);
        etiqueta.setWrapText(true);
        etiqueta.setMaxWidth(300);
        etiqueta.setStyle("-fx-text-fill: " + color + ";");
        return etiqueta;
    }

    private static VBox filaDeFirma(PdfAnalysisReport.Firma firma) {
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
        Label mensaje = texto(firma.mensaje() != null ? firma.mensaje() : firma.firmante(),
                firma.esInvalida() ? ROJO : "#212121");
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
}
