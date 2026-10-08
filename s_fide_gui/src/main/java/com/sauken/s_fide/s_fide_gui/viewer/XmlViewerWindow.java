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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Visor interno de documentos XML: el árbol de elementos con colores y nodos que se pliegan y despliegan, como
 * en un navegador, más el marcado de las firmas digitales que contiene.
 * <p>
 * Es solo para mirar. Las marcas de firma salen de los datos de la propia firma y no dicen si es válida: para
 * eso está el botón "Verificar firmas", que usa el mismo programa de verificación de siempre.
 */
public final class XmlViewerWindow {

    private static final Color COLOR_SIGNO = Color.web("#6b6b6b");
    private static final Color COLOR_ETIQUETA = Color.web("#881280");
    private static final Color COLOR_ATRIBUTO = Color.web("#994500");
    private static final Color COLOR_VALOR = Color.web("#1a1aa6");
    private static final Color COLOR_TEXTO = Color.web("#202020");
    private static final Color COLOR_COMENTARIO = Color.web("#236e25");
    private static final Color COLOR_FIRMADO = Color.web("#2e7d32");
    private static final Color COLOR_FIRMA = Color.web("#1565c0");
    private static final int LARGO_MAXIMO_DE_TEXTO = 300;
    private static final int NIVELES_AL_ABRIR = 2;
    private static final int LIMITE_PARA_EXPANDIR_TODO = 5000;

    private final Stage ventana = new Stage();
    private final XmlDocumentModel modelo;
    private final TreeView<Node> arbol = new TreeView<>();
    private final TextField busqueda = new TextField();
    private final Label estado = new Label();

    private List<Node> coincidencias = List.of();
    private int coincidenciaActual = -1;

    private XmlViewerWindow(Window propietario, Path archivo, XmlDocumentModel modelo, Consumer<Path> verificar) {
        this.modelo = modelo;
        ventana.initOwner(propietario);
        ventana.setTitle("Visor de XML — " + archivo.getFileName());

        arbol.setShowRoot(true);
        arbol.setRoot(new ItemXml(modelo.documento().getDocumentElement()));
        arbol.setCellFactory(v -> new CeldaXml());
        arbol.getRoot().setExpanded(true);
        expandirHasta(arbol.getRoot(), NIVELES_AL_ABRIR);
        arbol.setContextMenu(menuContextual());

        Button verificarBoton = new Button("Verificar firmas");
        verificarBoton.setDisable(modelo.firmas().isEmpty());
        verificarBoton.setOnAction(e -> verificar.accept(archivo));

        Button expandir = new Button("Expandir todo");
        expandir.setOnAction(e -> expandirTodo());
        Button colapsar = new Button("Colapsar todo");
        colapsar.setOnAction(e -> colapsarTodo());

        busqueda.setPromptText("Buscar texto o nombre de etiqueta…");
        busqueda.setPrefColumnCount(26);
        busqueda.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                buscar(!e.isShiftDown());
            }
        });
        Button siguiente = new Button("Siguiente");
        siguiente.setOnAction(e -> buscar(true));
        Button anterior = new Button("Anterior");
        anterior.setOnAction(e -> buscar(false));

        Region relleno = new Region();
        HBox.setHgrow(relleno, Priority.ALWAYS);
        HBox barra = new HBox(8, busqueda, anterior, siguiente, expandir, colapsar, relleno, verificarBoton);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8));

        estado.setStyle("-fx-text-fill: #616161;");
        estado.setPadding(new Insets(4, 10, 6, 10));
        estado.setText(resumen());

        BorderPane raiz = new BorderPane(arbol);
        raiz.setTop(barra);
        raiz.setBottom(estado);
        ventana.setScene(new Scene(raiz, 980, 680));
    }

    /**
     * Abre el archivo en una ventana nueva. Si el XML no se puede mostrar, devuelve el motivo en lenguaje simple
     * por la excepción y no abre nada.
     *
     * @param verificar qué hacer cuando la persona pide verificar las firmas del archivo
     */
    public static Stage abrir(Window propietario, Path archivo, Consumer<Path> verificar)
            throws XmlDocumentModel.XmlViewException {
        XmlViewerWindow visor = new XmlViewerWindow(propietario, archivo, XmlDocumentModel.abrir(archivo), verificar);
        visor.ventana.show();
        return visor.ventana;
    }

    private String resumen() {
        int firmas = modelo.firmas().size();
        String texto = modelo.cantidadDeElementos() + " elementos";
        if (firmas == 0) {
            return texto + " · sin firmas digitales";
        }
        List<String> quienes = new ArrayList<>();
        for (XmlDocumentModel.FirmaXml f : modelo.firmas()) {
            quienes.add(f.firmante() == null ? XmlDocumentModel.FIRMANTE_DESCONOCIDO : f.firmante());
        }
        return texto + " · " + firmas + (firmas == 1 ? " firma digital" : " firmas digitales") + " (" + String.join(", ", quienes)
                + "). Las marcas son informativas: use «Verificar firmas» para comprobar su validez.";
    }

    // ---------------------------------------------------------------------------------------------
    //  Árbol
    // ---------------------------------------------------------------------------------------------

    /** Lo que se ve de un nodo: un elemento con un único texto adentro se muestra en una sola línea. */
    private static String textoUnico(Element e) {
        Node hijo = e.getFirstChild();
        if (hijo != null && hijo.getNextSibling() == null
                && (hijo.getNodeType() == Node.TEXT_NODE || hijo.getNodeType() == Node.CDATA_SECTION_NODE)) {
            return hijo.getNodeValue();
        }
        return null;
    }

    private static boolean esVisible(Node n) {
        return switch (n.getNodeType()) {
            case Node.ELEMENT_NODE, Node.COMMENT_NODE -> true;
            case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> !n.getNodeValue().isBlank();
            default -> false;
        };
    }

    /** Un nodo del árbol cuyos hijos se arman recién cuando se despliega. */
    private static final class ItemXml extends TreeItem<Node> {
        private boolean cargado;

        ItemXml(Node nodo) {
            super(nodo);
        }

        @Override
        public boolean isLeaf() {
            if (!(getValue() instanceof Element e)) {
                return true;
            }
            if (textoUnico(e) != null) {
                return true;
            }
            for (Node h = e.getFirstChild(); h != null; h = h.getNextSibling()) {
                if (esVisible(h)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public javafx.collections.ObservableList<TreeItem<Node>> getChildren() {
            if (!cargado) {
                cargado = true;
                if (getValue() instanceof Element e && textoUnico(e) == null) {
                    List<TreeItem<Node>> hijos = new ArrayList<>();
                    for (Node h = e.getFirstChild(); h != null; h = h.getNextSibling()) {
                        if (esVisible(h)) {
                            hijos.add(new ItemXml(h));
                        }
                    }
                    super.getChildren().setAll(hijos);
                }
            }
            return super.getChildren();
        }
    }

    private final class CeldaXml extends TreeCell<Node> {
        @Override
        protected void updateItem(Node nodo, boolean vacio) {
            super.updateItem(nodo, vacio);
            if (vacio || nodo == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setText(null);
            setGraphic(dibujar(nodo, getTreeItem().isExpanded()));
        }
    }

    private TextFlow dibujar(Node nodo, boolean desplegado) {
        TextFlow flujo = new TextFlow();
        switch (nodo.getNodeType()) {
            case Node.COMMENT_NODE -> agregar(flujo, "<!-- " + recortar(nodo.getNodeValue().strip()) + " -->",
                    COLOR_COMENTARIO, false);
            case Node.CDATA_SECTION_NODE -> agregar(flujo, "<![CDATA[" + recortar(nodo.getNodeValue().strip()) + "]]>",
                    COLOR_TEXTO, false);
            case Node.TEXT_NODE -> agregar(flujo, recortar(nodo.getNodeValue().strip()), COLOR_TEXTO, false);
            default -> dibujarElemento(flujo, (Element) nodo, desplegado);
        }
        return flujo;
    }

    private void dibujarElemento(TextFlow flujo, Element e, boolean desplegado) {
        agregar(flujo, "<", COLOR_SIGNO, false);
        agregar(flujo, e.getTagName(), COLOR_ETIQUETA, false);
        NamedNodeMap atributos = e.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Node a = atributos.item(i);
            agregar(flujo, " " + a.getNodeName(), COLOR_ATRIBUTO, false);
            agregar(flujo, "=", COLOR_SIGNO, false);
            agregar(flujo, "\"" + recortar(a.getNodeValue()) + "\"", COLOR_VALOR, false);
        }
        String unico = textoUnico(e);
        boolean sinHijos = unico == null && esHoja(e);
        if (sinHijos) {
            agregar(flujo, "/>", COLOR_SIGNO, false);
        } else if (unico != null) {
            agregar(flujo, ">", COLOR_SIGNO, false);
            agregar(flujo, recortar(unico.strip()), COLOR_TEXTO, false);
            agregar(flujo, "</", COLOR_SIGNO, false);
            agregar(flujo, e.getTagName(), COLOR_ETIQUETA, false);
            agregar(flujo, ">", COLOR_SIGNO, false);
        } else if (desplegado) {
            agregar(flujo, ">", COLOR_SIGNO, false);
        } else {
            agregar(flujo, "> … </", COLOR_SIGNO, false);
            agregar(flujo, e.getTagName(), COLOR_ETIQUETA, false);
            agregar(flujo, ">", COLOR_SIGNO, false);
        }

        XmlDocumentModel.FirmaXml firma = modelo.firmaDe(e);
        if (firma != null) {
            String quien = firma.firmante() == null ? XmlDocumentModel.FIRMANTE_DESCONOCIDO : firma.firmante();
            agregar(flujo, "   ✎ Firma de " + quien, COLOR_FIRMA, true);
        }
        List<String> firmantes = modelo.firmantesDe(e);
        if (!firmantes.isEmpty()) {
            agregar(flujo, "   ✔ Firmado por " + String.join(", ", firmantes), COLOR_FIRMADO, true);
        }
    }

    private static boolean esHoja(Element e) {
        for (Node h = e.getFirstChild(); h != null; h = h.getNextSibling()) {
            if (esVisible(h)) {
                return false;
            }
        }
        return true;
    }

    private static void agregar(TextFlow flujo, String contenido, Color color, boolean destacado) {
        Text texto = new Text(contenido);
        texto.setFill(color);
        texto.setFont(destacado ? Font.font("System", FontWeight.BOLD, 12) : Font.font("Consolas", 13));
        flujo.getChildren().add(texto);
    }

    private static String recortar(String texto) {
        String limpio = texto.replaceAll("\\s+", " ");
        return limpio.length() <= LARGO_MAXIMO_DE_TEXTO ? limpio
                : limpio.substring(0, LARGO_MAXIMO_DE_TEXTO) + "… (" + limpio.length() + " caracteres)";
    }

    // ---------------------------------------------------------------------------------------------
    //  Expandir, colapsar, copiar
    // ---------------------------------------------------------------------------------------------

    private static void expandirHasta(TreeItem<Node> item, int niveles) {
        if (niveles <= 0 || item.isLeaf()) {
            return;
        }
        item.setExpanded(true);
        for (TreeItem<Node> hijo : item.getChildren()) {
            expandirHasta(hijo, niveles - 1);
        }
    }

    private void expandirTodo() {
        // En un documento enorme abrirlo todo congelaría la ventana: se abren solo los primeros niveles.
        expandirHasta(arbol.getRoot(), modelo.cantidadDeElementos() <= LIMITE_PARA_EXPANDIR_TODO ? 1000 : 4);
        if (modelo.cantidadDeElementos() > LIMITE_PARA_EXPANDIR_TODO) {
            estado.setText("El documento es muy grande: se abrieron los primeros niveles. Despliegue el resto a mano o use la búsqueda.");
        }
    }

    private void colapsarTodo() {
        colapsar(arbol.getRoot());
        arbol.getRoot().setExpanded(true);
    }

    private static void colapsar(TreeItem<Node> item) {
        item.setExpanded(false);
        for (TreeItem<Node> hijo : item.getChildren()) {
            colapsar(hijo);
        }
    }

    private ContextMenu menuContextual() {
        MenuItem copiarNodo = new MenuItem("Copiar este elemento");
        copiarNodo.setOnAction(e -> copiar(nodoElegido() == null ? null : XmlDocumentModel.serializar(nodoElegido())));
        MenuItem copiarRuta = new MenuItem("Copiar la ruta");
        copiarRuta.setOnAction(e -> copiar(nodoElegido() == null ? null : XmlDocumentModel.ruta(nodoElegido())));
        return new ContextMenu(copiarNodo, copiarRuta);
    }

    private Node nodoElegido() {
        TreeItem<Node> elegido = arbol.getSelectionModel().getSelectedItem();
        return elegido == null ? null : elegido.getValue();
    }

    private static void copiar(String texto) {
        if (texto == null) {
            return;
        }
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(texto);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    // ---------------------------------------------------------------------------------------------
    //  Búsqueda
    // ---------------------------------------------------------------------------------------------

    private void buscar(boolean haciaAdelante) {
        String consulta = busqueda.getText() == null ? "" : busqueda.getText().strip().toLowerCase();
        if (consulta.isEmpty()) {
            return;
        }
        if (!consulta.equals(consultaAnterior)) {
            consultaAnterior = consulta;
            coincidencias = reunirCoincidencias(consulta);
            coincidenciaActual = -1;
        }
        if (coincidencias.isEmpty()) {
            estado.setText("No se encontró «" + busqueda.getText().strip() + "».");
            return;
        }
        coincidenciaActual = Math.floorMod(coincidenciaActual + (haciaAdelante ? 1 : -1), coincidencias.size());
        mostrar(coincidencias.get(coincidenciaActual));
        estado.setText("Coincidencia " + (coincidenciaActual + 1) + " de " + coincidencias.size()
                + " para «" + busqueda.getText().strip() + "».");
    }

    private String consultaAnterior = "";

    private List<Node> reunirCoincidencias(String consulta) {
        List<Node> encontrados = new ArrayList<>();
        recorrer(modelo.documento().getDocumentElement(), consulta, encontrados);
        return encontrados;
    }

    private static void recorrer(Node nodo, String consulta, List<Node> encontrados) {
        boolean coincide = switch (nodo.getNodeType()) {
            case Node.ELEMENT_NODE -> {
                Element e = (Element) nodo;
                boolean enAtributos = false;
                NamedNodeMap atributos = e.getAttributes();
                for (int i = 0; i < atributos.getLength() && !enAtributos; i++) {
                    enAtributos = atributos.item(i).getNodeName().toLowerCase().contains(consulta)
                            || atributos.item(i).getNodeValue().toLowerCase().contains(consulta);
                }
                String unico = textoUnico(e);
                yield e.getTagName().toLowerCase().contains(consulta) || enAtributos
                        || (unico != null && unico.toLowerCase().contains(consulta));
            }
            case Node.COMMENT_NODE, Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                    esVisible(nodo) && nodo.getNodeValue().toLowerCase().contains(consulta);
            default -> false;
        };
        if (coincide) {
            encontrados.add(nodo);
        }
        Element e = nodo instanceof Element el ? el : null;
        if (e != null && textoUnico(e) == null) {
            for (Node h = e.getFirstChild(); h != null; h = h.getNextSibling()) {
                recorrer(h, consulta, encontrados);
            }
        }
    }

    /** Despliega el camino hasta el nodo y lo deja elegido a la vista. */
    private void mostrar(Node nodo) {
        List<Node> camino = new ArrayList<>();
        for (Node n = nodo; n != null && n.getNodeType() != Node.DOCUMENT_NODE; n = n.getParentNode()) {
            camino.add(0, n);
        }
        TreeItem<Node> actual = arbol.getRoot();
        for (int i = 1; i < camino.size() && actual != null; i++) {
            actual.setExpanded(true);
            TreeItem<Node> siguiente = null;
            for (TreeItem<Node> hijo : actual.getChildren()) {
                if (hijo.getValue() == camino.get(i)) {
                    siguiente = hijo;
                    break;
                }
            }
            actual = siguiente;
        }
        if (actual != null) {
            arbol.getSelectionModel().select(actual);
            int fila = arbol.getRow(actual);
            Platform.runLater(() -> arbol.scrollTo(Math.max(0, fila - 3)));
        }
    }
}
