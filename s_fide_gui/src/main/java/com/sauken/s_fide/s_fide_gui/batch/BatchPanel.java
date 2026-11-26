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

import com.sauken.s_fide.s_fide_gui.utils.ConfigurationManager;
import com.sauken.s_fide.s_fide_gui.utils.GUIUtils;
import com.sauken.s_fide.s_fide_gui.utils.UserDataDirectory;
import com.sauken.s_fide.s_fide_gui.viewer.ExternalOpener;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * La pestaña "Por lote": se elige una carpeta, se revisa y se ejecuta el programa correspondiente sobre todos
 * sus documentos, con la misma interfaz de línea de comandos que usaría un integrador ({@code -lote}).
 */
public final class BatchPanel {

    /** Sin ninguna novedad del programa durante este tiempo se lo considera colgado. */
    private static final long SEGUNDOS_SIN_NOVEDADES = 600;

    private static final ReadOnlyBooleanWrapper EN_CURSO = new ReadOnlyBooleanWrapper(false);

    /** Verdadero mientras hay un lote ejecutándose: la interfaz bloquea el menú y las pestañas. */
    public static ReadOnlyBooleanProperty enCursoProperty() {
        return EN_CURSO.getReadOnlyProperty();
    }

    /** Cómo terminó un lote, para lo que la pestaña tenga que hacer después. */
    public record Fin(int firmados, String corte) {
    }

    /** Paso previo a ejecutar (confirmaciones, consulta de revocación); si todo está bien llama a seguir. */
    public interface Confirmacion {
        void confirmar(int cantidad, Runnable seguir);
    }

    /**
     * Lo propio de cada opción del menú.
     *
     * @param programa         nombre del programa, sin extensión
     * @param extension        "xml" o "pdf"
     * @param firma            si firma (con revisión previa) o verifica
     * @param carpetaReciente  qué "última carpeta usada" corresponde
     * @param faltante         mensaje si falta completar algún dato, o {@code null}
     * @param argumentos       argumentos que van después de {@code -lote <lista>} al ejecutar
     * @param argumentosRevision los mismos para revisar sin firmar (sin la contraseña); {@code null} si no firma
     * @param antesDeEjecutar  paso previo, o {@code null}
     * @param alEjecutar       se consulta al empezar y devuelve qué hacer al terminar; o {@code null}
     * @param verDetalle       verifica un documento solo, para ver su detalle; {@code null} si firma
     */
    public record Modulo(String programa, String extension, boolean firma,
                         ConfigurationManager.DirectoryKind carpetaReciente, Supplier<String> faltante,
                         Supplier<List<String>> argumentos, Supplier<List<String>> argumentosRevision,
                         Confirmacion antesDeEjecutar, Supplier<Consumer<Fin>> alEjecutar,
                         Consumer<Path> verDetalle) {
    }

    /** Resultado de la última revisión, válido mientras no cambien la carpeta ni los datos. */
    private record Revision(String clave, List<BatchRow> filas, boolean mezclado) {
    }

    private final Modulo modulo;
    private final ConfigurationManager configuracion;
    private final Supplier<Window> propietario;
    private final Supplier<TextArea> salidaDelProceso;

    private final TextField carpeta = new TextField();
    private final Button examinar = new Button("Examinar...");
    private final CheckBox soloFirmados = new CheckBox("Incluir solo los archivos que terminan en -signed");
    private final CheckBox registro = new CheckBox("Guardar un registro por documento");
    private final Label resumen = new Label();
    private final Button revisar = new Button("Revisar");
    private final Button ejecutar = new Button();
    private final Button cancelar = new Button("Cancelar");

    private final ObservableList<BatchRow> filas = FXCollections.observableArrayList();
    private final FilteredList<BatchRow> visibles = new FilteredList<>(filas);
    private final TableView<BatchRow> tabla = new TableView<>(visibles);
    private final CheckBox soloProblemas = new CheckBox("Mostrar solo con problemas");
    private final Button detalle = new Button("Ver detalle");
    private final Button reintentar = new Button("Reintentar los que fallaron");
    private final Button abrirInforme = new Button("Abrir informe");
    private final Button abrirCarpeta = new Button("Abrir carpeta");

    private final BooleanProperty hayProceso = new SimpleBooleanProperty();
    private final BooleanProperty hayInforme = new SimpleBooleanProperty();
    private final BooleanProperty hayParaReintentar = new SimpleBooleanProperty();
    private BatchProcess proceso;
    private Revision revision;
    private Path informe;

    public BatchPanel(Modulo modulo, ConfigurationManager configuracion, Supplier<Window> propietario,
                      Supplier<TextArea> salidaDelProceso) {
        this.modulo = modulo;
        this.configuracion = configuracion;
        this.propietario = propietario;
        this.salidaDelProceso = salidaDelProceso;
        armarControles();
    }

    private void armarControles() {
        carpeta.setPromptText("Carpeta con los documentos ." + modulo.extension() + " (no incluye subcarpetas)");
        carpeta.setPrefWidth(420);
        carpeta.textProperty().addListener((o, antes, ahora) -> actualizarCantidad());
        soloFirmados.setTooltip(new Tooltip("Para agregar una firma a documentos ya firmados. Se mira el nombre del "
                + "archivo."));
        soloFirmados.selectedProperty().addListener((o, antes, ahora) -> actualizarCantidad());
        registro.setTooltip(new Tooltip("Guarda junto a cada documento un archivo .log con el detalle de su proceso"));
        resumen.setWrapText(true);
        resumen.setMaxWidth(640);

        examinar.setOnAction(e -> elegirCarpeta());
        revisar.setTooltip(new Tooltip("Muestra todo lo que se va a firmar, sin firmar nada"));
        revisar.setOnAction(e -> revisarCarpeta());
        ejecutar.getStyleClass().add("execute");
        ejecutar.setOnAction(e -> ejecutarCarpeta());
        revisar.disableProperty().bind(GUIUtils.busyProperty());
        ejecutar.disableProperty().bind(GUIUtils.busyProperty());
        cancelar.disableProperty().bind(hayProceso.not());
        cancelar.setOnAction(e -> {
            if (proceso != null) {
                proceso.cancelar();
            }
        });
        carpeta.disableProperty().bind(EN_CURSO);
        examinar.disableProperty().bind(EN_CURSO);
        soloFirmados.disableProperty().bind(EN_CURSO);
        registro.disableProperty().bind(EN_CURSO);

        tabla.setPrefHeight(260);
        tabla.setPrefWidth(860);
        // Los botones nunca se recortan, aunque la pantalla de la opción sea angosta.
        for (javafx.scene.layout.Region control : List.of(revisar, ejecutar, cancelar, soloProblemas, detalle,
                reintentar, abrirInforme, abrirCarpeta)) {
            control.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Los resultados aparecen acá, un documento por fila."));
        tabla.getColumns().add(columna("Documento", f -> Bindings.createStringBinding(f::nombre), 240));
        tabla.getColumns().add(columna("Estado", BatchRow::estadoProperty, 190));
        tabla.getColumns().add(columna("Detalle", BatchRow::mensajeProperty, 330));

        soloProblemas.selectedProperty().addListener((o, antes, ahora) -> filtrar());
        detalle.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull()
                .or(GUIUtils.busyProperty()));
        detalle.setOnAction(e -> verDetalle(tabla.getSelectionModel().getSelectedItem()));
        reintentar.disableProperty().bind(hayParaReintentar.not().or(GUIUtils.busyProperty()));
        reintentar.setOnAction(e -> reintentarFallidos());
        abrirInforme.disableProperty().bind(hayInforme.not().or(EN_CURSO));
        abrirInforme.setOnAction(e -> ExternalOpener.abrirDocumento(informe, this::avisar));
        abrirCarpeta.setOnAction(e -> carpetaElegida().ifPresentOrElse(
                c -> ExternalOpener.abrirCarpeta(c, this::avisar), () -> avisar("Elija primero una carpeta.")));
        actualizarCantidad();
    }

    /** Agrega los controles del lote a la grilla de la pestaña, a partir de la fila indicada. */
    public void agregarA(GridPane grilla, int fila) {
        grilla.add(new Label("Carpeta:"), 0, fila);
        grilla.add(carpeta, 1, fila);
        grilla.add(examinar, 2, fila++);
        VBox opciones = new VBox(4);
        if (modulo.firma()) {
            opciones.getChildren().add(soloFirmados);
        }
        opciones.getChildren().add(registro);
        grilla.add(opciones, 1, fila++, 2, 1);
        HBox botones = new HBox(10);
        if (modulo.firma()) {
            botones.getChildren().add(revisar);
        }
        botones.getChildren().addAll(ejecutar, cancelar);
        grilla.add(botones, 1, fila++, 2, 1);
        grilla.add(resumen, 1, fila++, 2, 1);
        grilla.add(tabla, 0, fila++, 3, 1);
        HBox acciones = new HBox(10, soloProblemas, detalle, reintentar, abrirInforme, abrirCarpeta);
        acciones.setStyle("-fx-alignment: center-left;");
        grilla.add(acciones, 0, fila, 3, 1);
    }

    /** El primer documento de la carpeta, para elegir sobre él la ubicación de la firma; vacío si no hay. */
    public String primerDocumento() {
        return explorar(false).filter(e -> !e.incluidos().isEmpty())
                .map(e -> e.incluidos().get(0).ruta().toString()).orElse("");
    }

    // ---------------------------------------------------------------------------------------------- carpeta

    private java.util.Optional<Path> carpetaElegida() {
        String texto = carpeta.getText() == null ? "" : carpeta.getText().trim();
        if (texto.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            Path ruta = Path.of(texto);
            return Files.isDirectory(ruta) ? java.util.Optional.of(ruta) : java.util.Optional.empty();
        } catch (InvalidPathException e) {
            return java.util.Optional.empty();
        }
    }

    private BatchFolder.Filtro filtro() {
        if (!modulo.firma()) {
            return BatchFolder.Filtro.TODOS;
        }
        return soloFirmados.isSelected() ? BatchFolder.Filtro.SOLO_FIRMADOS : BatchFolder.Filtro.SIN_FIRMADOS;
    }

    private java.util.Optional<BatchFolder.Exploracion> explorar(boolean informarError) {
        java.util.Optional<Path> elegida = carpetaElegida();
        if (elegida.isEmpty()) {
            if (informarError) {
                avisar("Elija una carpeta que exista.");
            }
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(BatchFolder.explorar(elegida.get(), modulo.extension(), filtro()));
        } catch (IOException e) {
            if (informarError) {
                avisar("No se pudo leer la carpeta " + elegida.get() + ". Verifique los permisos.");
            }
            return java.util.Optional.empty();
        }
    }

    private void elegirCarpeta() {
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle("Carpeta con los documentos del lote");
        File inicial = carpetaElegida().map(Path::toFile)
                .orElseGet(() -> configuracion.getLastDirectory(modulo.carpetaReciente()));
        if (inicial != null) {
            selector.setInitialDirectory(inicial);
        }
        File elegida = selector.showDialog(propietario.get());
        if (elegida != null) {
            configuracion.setLastDirectory(modulo.carpetaReciente(), elegida);
            carpeta.setText(elegida.getAbsolutePath());
        }
    }

    private void actualizarCantidad() {
        String verbo = modulo.firma() ? "Firmar" : "Verificar";
        var exploracion = explorar(false);
        if (exploracion.isEmpty()) {
            ejecutar.setText(verbo + " los documentos");
            resumen.setText("");
            return;
        }
        long marcados = exploracion.get().incluidos().stream().filter(BatchFolder.Archivo::marcado).count();
        int fuera = exploracion.get().excluidosPorNombre().size();
        ejecutar.setText(verbo + " " + marcados + (marcados == 1 ? " documento" : " documentos"));
        resumen.setText(marcados + " para procesar"
                + (fuera > 0 ? "; " + fuera + " fuera por el nombre" : "") + ".");
    }

    // ------------------------------------------------------------------------------------- revisar y ejecutar

    private boolean datosCompletos() {
        String falta = modulo.faltante().get();
        if (falta != null) {
            avisar(falta);
            return false;
        }
        return true;
    }

    private void revisarCarpeta() {
        if (!datosCompletos()) {
            return;
        }
        explorar(true).ifPresent(exploracion -> {
            if (sinDocumentos(exploracion)) {
                return;
            }
            pedirRevision(exploracion, nueva -> {
                boolean aceptada = BatchReviewDialog.mostrar(propietario.get(), nueva.filas(), avisoDe(nueva),
                        exploracion.excluidosPorNombre().size(), !nueva.mezclado());
                revision = aceptada ? nueva : null;
                if (aceptada) {
                    contarSeleccion(nueva);
                }
            });
        });
    }

    private void ejecutarCarpeta() {
        if (!datosCompletos()) {
            return;
        }
        explorar(true).ifPresent(exploracion -> {
            if (sinDocumentos(exploracion)) {
                return;
            }
            int fuera = exploracion.excluidosPorNombre().size();
            if (!modulo.firma()) {
                confirmarYLanzar(exploracion.incluidos().stream().map(BatchFolder.Archivo::ruta).toList(), fuera);
            } else if (revision != null && revision.clave().equals(clave(exploracion))) {
                firmarSeleccion(revision, exploracion);
            } else {
                if (revision != null) {
                    avisar("La carpeta o los datos cambiaron desde la última revisión: se vuelve a revisar.");
                }
                revisarYFirmar(exploracion);
            }
        });
    }

    private void revisarYFirmar(BatchFolder.Exploracion exploracion) {
        pedirRevision(exploracion, nueva -> {
            boolean hayBloqueados = nueva.filas().stream().anyMatch(f -> !f.seleccionable());
            // Nada que no se pueda firmar queda fuera sin que la persona lo vea.
            if (nueva.mezclado() || hayBloqueados) {
                boolean aceptada = BatchReviewDialog.mostrar(propietario.get(), nueva.filas(), avisoDe(nueva),
                        exploracion.excluidosPorNombre().size(), !nueva.mezclado());
                if (!aceptada) {
                    return;
                }
            }
            firmarSeleccion(nueva, exploracion);
        });
    }

    private void firmarSeleccion(Revision aprobada, BatchFolder.Exploracion exploracion) {
        List<Path> elegidos = aprobada.filas().stream().filter(f -> f.marcadoProperty().get()).map(BatchRow::ruta)
                .toList();
        int fuera = exploracion.excluidosPorNombre().size() + exploracion.incluidos().size() - elegidos.size();
        confirmarYLanzar(elegidos, fuera);
    }

    private boolean sinDocumentos(BatchFolder.Exploracion exploracion) {
        if (!exploracion.incluidos().isEmpty()) {
            return false;
        }
        int fuera = exploracion.excluidosPorNombre().size();
        avisar("No hay documentos ." + modulo.extension() + " para procesar en la carpeta."
                + (fuera > 0 ? " Por su nombre quedaron fuera " + fuera + "." : ""));
        return true;
    }

    private String clave(BatchFolder.Exploracion exploracion) {
        return filtro() + "\n" + exploracion.huella() + "\n" + String.join("\u0000", modulo.argumentosRevision().get());
    }

    private static String avisoDe(Revision revision) {
        return revision.mezclado() ? "El lote mezcla documentos de distinto tipo y no se puede firmar. Separe los que "
                + "no corresponden en otra carpeta, o elija el elemento correcto." : null;
    }

    private void contarSeleccion(Revision aprobada) {
        long marcados = aprobada.filas().stream().filter(f -> f.marcadoProperty().get()).count();
        ejecutar.setText("Firmar " + marcados + (marcados == 1 ? " documento" : " documentos"));
        resumen.setText(marcados + " para firmar; " + (aprobada.filas().size() - marcados) + " fuera del lote.");
    }

    /** Pide al programa que revise los documentos sin firmar y arma las filas con lo que informa. */
    private void pedirRevision(BatchFolder.Exploracion exploracion, Consumer<Revision> alRecibir) {
        List<BatchRow> revisadas = exploracion.incluidos().stream().map(a -> new BatchRow(a.ruta())).toList();
        String clave = clave(exploracion);
        Path lista;
        try {
            lista = Files.createTempFile("sfide-revision-", ".txt");
            escribirLista(lista, revisadas.stream().map(BatchRow::ruta).toList());
        } catch (IOException e) {
            avisar("No se pudo preparar la lista de documentos.");
            return;
        }
        List<String> argumentos = new ArrayList<>(List.of("-lote", lista.toString()));
        argumentos.addAll(modulo.argumentosRevision().get());
        argumentos.add("-revisar");
        resumen.setText("Revisando los documentos…");

        List<String> errores = new ArrayList<>();
        BatchProtocol.Resumen[] fin = new BatchProtocol.Resumen[1];
        lanzarProceso(argumentos, linea -> {
            BatchProtocol.documento(linea).ifPresent(d -> {
                if (d.numero() >= 1 && d.numero() <= revisadas.size()) {
                    revisadas.get(d.numero() - 1).informar(d);
                }
            });
            BatchProtocol.resumen(linea).ifPresent(r -> fin[0] = r);
        }, errores::add, resultado -> {
            borrar(lista);
            actualizarCantidad();
            if (resultado.cancelado()) {
                resumen.setText("Revisión cancelada.");
                return;
            }
            boolean mezclado = fin[0] != null && "LOTE_MEZCLADO".equals(fin[0].corte());
            if (fin[0] == null || (fin[0].corte() != null && !mezclado)) {
                avisar("No se pudo revisar los documentos.\n\n" + (errores.isEmpty()
                        ? "El programa no respondió." : String.join("\n", errores)));
                return;
            }
            for (int i = 0; i < revisadas.size(); i++) {
                BatchRow fila = revisadas.get(i);
                // En un lote mezclado, lo que sí corresponde llega como "no procesado".
                if ("LISTO".equals(fila.codigoDeEstado())) {
                    fila.marcadoProperty().set(exploracion.incluidos().get(i).marcado());
                } else {
                    fila.bloquear();
                }
            }
            alRecibir.accept(new Revision(clave, revisadas, mezclado));
        });
    }

    private void confirmarYLanzar(List<Path> documentos, int fuera) {
        if (documentos.isEmpty()) {
            avisar("No queda ningún documento marcado para procesar.");
            return;
        }
        Runnable seguir = () -> ejecutarLote(documentos, fuera);
        if (modulo.antesDeEjecutar() == null) {
            seguir.run();
        } else {
            modulo.antesDeEjecutar().confirmar(documentos.size(), seguir);
        }
    }

    private void ejecutarLote(List<Path> documentos, int fuera) {
        Path lista;
        Path nuevoInforme;
        try {
            Path lotes = Files.createDirectories(UserDataDirectory.resolve("lotes"));
            String base = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + "-"
                    + modulo.programa();
            lista = lotes.resolve(base + ".txt");
            nuevoInforme = lotes.resolve(base + ".csv");
            escribirLista(lista, documentos);
        } catch (IOException e) {
            avisar("No se pudo guardar la lista de documentos en la carpeta de S-FiDE del usuario.");
            return;
        }
        List<String> argumentos = new ArrayList<>(List.of("-lote", lista.toString()));
        argumentos.addAll(modulo.argumentos().get());
        argumentos.addAll(List.of("-informe", nuevoInforme.toString()));
        if (registro.isSelected()) {
            argumentos.add("-log");
        }

        filas.setAll(documentos.stream().map(BatchRow::new).toList());
        filas.forEach(f -> f.pendiente("En espera"));
        soloProblemas.setSelected(false);
        hayInforme.set(false);
        hayParaReintentar.set(false);
        salidaDelProceso.get().clear();
        resumen.setText("Procesando 0 de " + documentos.size() + "…");
        Consumer<Fin> despues = modulo.alEjecutar() == null ? null : modulo.alEjecutar().get();

        List<String> errores = new ArrayList<>();
        BatchProtocol.Resumen[] fin = new BatchProtocol.Resumen[1];
        boolean iniciado = lanzarProceso(argumentos, linea -> {
            var documento = BatchProtocol.documento(linea);
            if (documento.isPresent()) {
                int numero = documento.get().numero();
                if (numero >= 1 && numero <= filas.size()) {
                    filas.get(numero - 1).informar(documento.get());
                    resumen.setText("Procesando " + numero + " de " + documentos.size() + "…");
                }
                return;
            }
            BatchProtocol.resumen(linea).ifPresent(r -> fin[0] = r);
            if (!linea.startsWith("LOTE_")) {
                salidaDelProceso.get().appendText(linea + "\n");
            }
        }, linea -> {
            errores.add(linea);
            salidaDelProceso.get().appendText(linea + "\n");
        }, resultado -> {
            revision = null;
            String motivo = resultado.cancelado() ? "El lote se canceló."
                    : resultado.sinNovedades() ? "El lote se detuvo: el programa dejó de responder."
                    : "El lote se interrumpió antes de terminar.";
            if (fin[0] == null) {
                filas.forEach(f -> f.sinInforme(motivo));
            }
            long firmados = filas.stream().filter(f -> f.codigoDeEstado().startsWith("FIRMADO")).count();
            String texto = textoDeResumen(fin[0], fuera, motivo, errores);
            resumen.setText(texto);
            informe = nuevoInforme;
            hayInforme.set(Files.exists(nuevoInforme));
            hayParaReintentar.set(filas.stream()
                    .anyMatch(f -> BatchProtocol.sePuedeReintentar(f.codigoDeEstado())));
            filtrar();
            if (despues != null) {
                despues.accept(new Fin((int) firmados, fin[0] == null ? null : fin[0].corte()));
            }
            mostrarFinal(resultado.codigo() == 0, texto);
        });
        if (!iniciado) {
            filas.forEach(f -> f.sinInforme("No se pudo iniciar el programa."));
        }
    }

    private String textoDeResumen(BatchProtocol.Resumen fin, int fuera, String motivo, List<String> errores) {
        if (fin == null) {
            return motivo + " Revise la tabla y la salida del proceso.";
        }
        List<String> partes = new ArrayList<>();
        agregar(partes, modulo.firma() ? "Firmados" : "Válidos", fin.cantidad("correctos"));
        agregar(partes, modulo.firma() ? "Firmados con avisos" : "Válidos con observaciones",
                fin.cantidad("con_avisos"));
        agregar(partes, "No válidos", fin.cantidad("no_validos"));
        agregar(partes, "Sin firmas", fin.cantidad("sin_firmas"));
        agregar(partes, "Con error", fin.cantidad("con_error"));
        agregar(partes, "Omitidos", fin.cantidad("omitidos"));
        agregar(partes, "No procesados", fin.cantidad("no_procesados"));
        agregar(partes, "Fuera del lote", fuera);
        String texto = String.join(" · ", partes);
        if (fin.corte() != null) {
            texto += "\nLote detenido: " + errores.stream().filter(l -> l.startsWith("Error: "))
                    .map(l -> l.substring("Error: ".length())).findFirst().orElse(fin.corte());
        }
        return texto;
    }

    private static void agregar(List<String> partes, String nombre, int cantidad) {
        if (cantidad > 0) {
            partes.add(nombre + ": " + cantidad);
        }
    }

    /**
     * Ejecuta el programa marcando la interfaz como ocupada. Las líneas y el final llegan en el hilo de la
     * interfaz.
     */
    private boolean lanzarProceso(List<String> argumentos, Consumer<String> salida, Consumer<String> error,
                                  Consumer<BatchProcess.Fin> alTerminar) {
        List<String> comando = GUIUtils.comandoDeModulo(modulo.programa(), argumentos);
        if (comando == null) {
            avisar("No se encuentra el archivo " + modulo.programa() + ".jar en la carpeta de S-FiDE.");
            return false;
        }
        GUIUtils.beginBusy();
        EN_CURSO.set(true);
        hayProceso.set(true);
        try {
            proceso = BatchProcess.iniciar(comando,
                    linea -> Platform.runLater(() -> salida.accept(linea)),
                    linea -> Platform.runLater(() -> error.accept(linea)),
                    SEGUNDOS_SIN_NOVEDADES,
                    fin -> Platform.runLater(() -> {
                        liberar();
                        alTerminar.accept(fin);
                    }));
            return true;
        } catch (IOException e) {
            liberar();
            avisar("No se pudo iniciar " + modulo.programa() + ".jar.");
            return false;
        }
    }

    private void liberar() {
        proceso = null;
        hayProceso.set(false);
        EN_CURSO.set(false);
        GUIUtils.endBusy();
    }

    // ------------------------------------------------------------------------------------------- resultados

    private void filtrar() {
        visibles.setPredicate(soloProblemas.isSelected()
                ? f -> BatchProtocol.esProblema(f.codigoDeEstado()) : f -> true);
    }

    private void reintentarFallidos() {
        if (!datosCompletos()) {
            return;
        }
        Set<Path> fallidos = new LinkedHashSet<>(filas.stream()
                .filter(f -> BatchProtocol.sePuedeReintentar(f.codigoDeEstado())).map(BatchRow::ruta)
                .filter(Files::exists).toList());
        if (fallidos.isEmpty()) {
            avisar("Los documentos que fallaron ya no están en la carpeta.");
        } else if (modulo.firma()) {
            revisarYFirmar(new BatchFolder.Exploracion(
                    fallidos.stream().map(p -> new BatchFolder.Archivo(p, true)).toList(), List.of(), "reintento"));
        } else {
            confirmarYLanzar(List.copyOf(fallidos), 0);
        }
    }

    private void verDetalle(BatchRow fila) {
        if (modulo.verDetalle() != null) {
            modulo.verDetalle().accept(fila.ruta());
        } else if (!fila.registro().isEmpty()) {
            ExternalOpener.abrirDocumento(Path.of(fila.registro()), this::avisar);
        } else {
            avisar((fila.mensajeProperty().get().isEmpty() ? "" : fila.mensajeProperty().get() + "\n\n")
                    + "Para guardar el detalle de cada documento, marque «Guardar un registro por documento» antes de "
                    + "ejecutar.");
        }
    }

    private static TableColumn<BatchRow, String> columna(String titulo,
            java.util.function.Function<BatchRow, javafx.beans.value.ObservableValue<String>> valor, double ancho) {
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

    private static void escribirLista(Path lista, List<Path> documentos) throws IOException {
        Files.write(lista, documentos.stream().map(Path::toString).toList(), StandardCharsets.UTF_8);
    }

    private static void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignorada) {
            // Es un temporal del sistema: si no se puede borrar, lo limpiará el sistema.
        }
    }

    private void mostrarFinal(boolean correcto, String texto) {
        Alert alerta = new Alert(correcto ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alerta.initOwner(propietario.get());
        alerta.setTitle("Estado del lote");
        alerta.setHeaderText(correcto ? "El lote terminó correctamente" : "El lote terminó con problemas");
        alerta.setContentText(texto);
        alerta.show();
    }

    private void avisar(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.initOwner(propietario.get());
        alerta.setTitle("Lote");
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
