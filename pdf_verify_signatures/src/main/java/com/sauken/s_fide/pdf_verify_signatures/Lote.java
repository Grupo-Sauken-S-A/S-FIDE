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

package com.sauken.s_fide.pdf_verify_signatures;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ejecución por lotes: la misma operación sobre todos los documentos de una lista, con un único acceso al
 * certificado, una línea de resultado por documento y un resumen final.
 * <p>
 * Se ocupa de todo lo que es igual en cualquier módulo (leer la lista, las líneas {@code LOTE_}, el informe
 * CSV, el registro por documento, los cortes y el código de salida). Lo propio de cada módulo lo aporta su
 * {@link Tarea}.
 * <p>
 * Esta clase está duplicada a propósito en los módulos que admiten lote (cada jar debe seguir siendo
 * independiente): si se modifica, hay que actualizar todas las copias (solo cambia la línea {@code package}).
 * La prueba {@code LoteCopiasTest} comprueba que sigan idénticas.
 */
final class Lote {

    /** Versión del formato de las líneas {@code LOTE_}. */
    private static final String FORMATO = "1";
    /** Errores seguidos de acceso a la clave a partir de los cuales no tiene sentido seguir. */
    private static final int MAXIMO_DE_FALLAS_DE_CLAVE = 3;

    /** Cómo terminó un documento. */
    enum Estado {
        LISTO, FIRMADO, FIRMADO_CON_AVISOS, VALIDO, VALIDO_CON_OBSERVACIONES, NO_VALIDO, SIN_FIRMAS, OMITIDO,
        ERROR, NO_PROCESADO
    }

    /** Resultado de un documento; {@code salida} es el archivo generado (o el que ya existía), si lo hay. */
    record Resultado(Estado estado, String codigo, Path salida, String mensaje) {
        static Resultado de(Estado estado, String codigo, String mensaje) {
            return new Resultado(estado, codigo, null, mensaje);
        }

        static Resultado error(String codigo, String mensaje) {
            return new Resultado(Estado.ERROR, codigo, null, mensaje);
        }
    }

    /** Situación que impide seguir con el lote: lo que falta queda sin procesar. */
    static final class Corte extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String causa;

        Corte(String causa, String mensaje) {
            super(mensaje);
            this.causa = causa;
        }

        String causa() {
            return causa;
        }
    }

    /** Un documento de la lista. */
    static final class Documento {
        private final int numero;
        private final Path ruta;
        private final String texto;
        private final ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        private final PrintStream registro = new PrintStream(memoria, true, StandardCharsets.UTF_8);
        private Resultado resultado;

        private Documento(int numero, Path ruta, String texto) {
            this.numero = numero;
            this.ruta = ruta;
            this.texto = texto;
        }

        Path ruta() {
            return ruta;
        }

        /** Donde se escribe el detalle de este documento; solo se ve en su archivo {@code .log}. */
        PrintStream registro() {
            return registro;
        }

        boolean pendiente() {
            return resultado == null;
        }

        /** Deja el documento resuelto sin procesarlo (error u omisión). */
        void resolver(Resultado nuevo) {
            resultado = nuevo;
        }
    }

    /** Lo propio de cada módulo. */
    interface Tarea {
        /**
         * Revisa los documentos sin tocar ningún certificado y resuelve los que no se pueden procesar. Puede
         * cortar el lote (por ejemplo, si mezcla documentos de distinto tipo).
         */
        default void revisar(List<Documento> pendientes) {
        }

        /** Accede una única vez a lo que se necesita para procesar; solo se llama si queda algo por hacer. */
        default void acceder() {
        }

        /** Procesa un documento. Lo que escriba en la salida estándar va a su registro. */
        Resultado procesar(Documento documento);

        /** Libera lo tomado en {@link #acceder()}; se llama siempre. */
        default void cerrar() {
        }
    }

    /** Opciones comunes a todos los módulos. */
    record Opciones(String lista, String informe, boolean registroPorDocumento, boolean soloRevisar) {
    }

    private final String programa;
    private final String version;
    private final PrintStream salida;
    private final PrintStream error;
    private BufferedWriter informe;
    private boolean informeIncompleto;

    private Lote(String programa, String version, PrintStream salida, PrintStream error) {
        this.programa = programa;
        this.version = version;
        this.salida = salida;
        this.error = error;
    }

    // ---------------------------------------------------------------------------------------------- argumentos

    static boolean esOpcionDeLote(String argumento) {
        return coincide(argumento, "lote");
    }

    static boolean esOpcionRevisar(String argumento) {
        return coincide(argumento, "revisar");
    }

    static boolean esOpcionInforme(String argumento) {
        return coincide(argumento, "informe");
    }

    static boolean esOpcionRegistro(String argumento) {
        return coincide(argumento, "log");
    }

    private static boolean coincide(String argumento, String nombre) {
        return ("-" + nombre).equalsIgnoreCase(argumento) || ("--" + nombre).equalsIgnoreCase(argumento);
    }

    /** Argumentos mal formados: se informa y no se hace nada. Devuelve el código de salida. */
    static int rechazarArgumentos(String programa, String version, PrintStream salida, PrintStream error,
                                  String mensaje) {
        Lote lote = new Lote(programa, version, salida, error);
        return lote.cortarAntesDeEmpezar("ARGUMENTOS", mensaje);
    }

    // ----------------------------------------------------------------------------------------------- ejecución

    /**
     * Ejecuta el lote completo y devuelve el código de salida: 0 solo si no hubo corte y todos los documentos
     * de la lista terminaron bien.
     *
     * @param extension extensión que deben tener los documentos, sin el punto ("xml" o "pdf")
     */
    static int ejecutar(String programa, String version, String extension, Opciones opciones, PrintStream salida,
                        PrintStream error, Tarea tarea) {
        Lote lote = new Lote(programa, version, salida, error);
        List<Documento> documentos;
        try {
            documentos = leerLista(opciones.lista(), extension);
        } catch (Corte corte) {
            return lote.cortarAntesDeEmpezar(corte.causa(), corte.getMessage());
        }
        try {
            lote.crearInforme(opciones);
        } catch (Corte corte) {
            lote.escribirInicio(documentos.size());
            error.println("Error: " + corte.getMessage());
            for (Documento documento : documentos) {
                if (documento.pendiente()) {
                    documento.resolver(Resultado.de(Estado.NO_PROCESADO, corte.causa(), corte.getMessage()));
                }
                lote.informar(documento, documentos.size(), false);
            }
            lote.escribirResumen(documentos, corte.causa());
            return 1;
        }
        lote.escribirInicio(documentos.size());
        return lote.procesar(documentos, opciones, tarea);
    }

    private int procesar(List<Documento> documentos, Opciones opciones, Tarea tarea) {
        boolean registrar = opciones.registroPorDocumento() && !opciones.soloRevisar();
        Corte corte = null;
        int informados = 0;
        try {
            List<Documento> pendientes = documentos.stream().filter(Documento::pendiente).toList();
            if (!pendientes.isEmpty()) {
                tarea.revisar(pendientes);
            }
            if (opciones.soloRevisar()) {
                for (Documento documento : documentos) {
                    if (documento.pendiente()) {
                        documento.resolver(Resultado.de(Estado.LISTO, "OK", "Listo para procesar."));
                    }
                }
            } else if (documentos.stream().anyMatch(Documento::pendiente)) {
                tarea.acceder();
            }
            int fallasDeClave = 0;
            for (Documento documento : documentos) {
                if (documento.pendiente()) {
                    documento.resolver(procesarUno(documento, tarea));
                    fallasDeClave = "ERROR_AL_FIRMAR".equals(documento.resultado.codigo()) ? fallasDeClave + 1 : 0;
                }
                informar(documento, documentos.size(), registrar);
                informados++;
                if (fallasDeClave >= MAXIMO_DE_FALLAS_DE_CLAVE) {
                    throw new Corte("FALLAS_CONSECUTIVAS", "Se detuvo el lote: " + MAXIMO_DE_FALLAS_DE_CLAVE
                            + " documentos seguidos no se pudieron firmar. Verifique el certificado o el token.");
                }
            }
        } catch (Corte c) {
            corte = c;
        } catch (RuntimeException e) {
            corte = new Corte("ERROR_INESPERADO", "El lote se detuvo por un error inesperado.");
        } finally {
            tarea.cerrar();
        }
        if (corte != null) {
            error.println("Error: " + corte.getMessage());
            for (int i = informados; i < documentos.size(); i++) {
                Documento documento = documentos.get(i);
                if (documento.pendiente()) {
                    documento.resolver(Resultado.de(Estado.NO_PROCESADO, corte.causa(), corte.getMessage()));
                }
                informar(documento, documentos.size(), registrar);
            }
        }
        escribirResumen(documentos, corte == null ? null : corte.causa());
        boolean todoBien = corte == null && !informeIncompleto
                && documentos.stream().allMatch(d -> terminoBien(d.resultado));
        return todoBien ? 0 : 1;
    }

    private static Resultado procesarUno(Documento documento, Tarea tarea) {
        try {
            return enRegistro(documento, () -> tarea.procesar(documento));
        } catch (Corte corte) {
            throw corte;
        } catch (OutOfMemoryError e) {
            return Resultado.error("ERROR_INESPERADO", "El documento es demasiado grande para procesarlo.");
        } catch (RuntimeException e) {
            return Resultado.error("ERROR_INESPERADO", "No se pudo procesar el documento.");
        }
    }

    /** Ejecuta una acción haciendo que lo que escriba en la salida estándar o de error vaya al registro. */
    static <T> T enRegistro(Documento documento, Supplier<T> accion) {
        PrintStream salidaAnterior = System.out;
        PrintStream errorAnterior = System.err;
        System.setOut(documento.registro);
        System.setErr(documento.registro);
        try {
            return accion.get();
        } finally {
            System.setOut(salidaAnterior);
            System.setErr(errorAnterior);
        }
    }

    private static boolean terminoBien(Resultado resultado) {
        return switch (resultado.estado()) {
            case LISTO, FIRMADO, FIRMADO_CON_AVISOS, VALIDO, VALIDO_CON_OBSERVACIONES -> true;
            case OMITIDO -> "DUPLICADO".equals(resultado.codigo());
            case NO_VALIDO, SIN_FIRMAS, ERROR, NO_PROCESADO -> false;
        };
    }

    // --------------------------------------------------------------------------------------------------- lista

    private static List<Documento> leerLista(String lista, String extension) {
        Path archivo;
        List<String> lineas;
        try {
            archivo = Path.of(lista).toAbsolutePath().normalize();
            if (!Files.isRegularFile(archivo)) {
                throw new Corte("LISTA_ILEGIBLE", "El archivo de lista no existe: " + lista);
            }
            lineas = decodificar(Files.readAllBytes(archivo)).lines().toList();
        } catch (CharacterCodingException e) {
            throw new Corte("LISTA_ILEGIBLE", "El archivo de lista no está en UTF-8. Vuelva a guardarlo como UTF-8: "
                    + lista);
        } catch (IOException | InvalidPathException e) {
            throw new Corte("LISTA_ILEGIBLE", "No se pudo leer el archivo de lista: " + lista);
        }

        List<Documento> documentos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (String linea : lineas) {
            String texto = linea.strip();
            if (texto.length() > 1 && texto.startsWith("\"") && texto.endsWith("\"")) {
                texto = texto.substring(1, texto.length() - 1).strip();
            }
            if (texto.isEmpty()) {
                continue;
            }
            int numero = documentos.size() + 1;
            Path ruta = null;
            if (texto.indexOf('\t') < 0) {
                try {
                    ruta = archivo.getParent().resolve(texto).toAbsolutePath().normalize();
                } catch (InvalidPathException e) {
                    ruta = null;
                }
            }
            Documento documento = new Documento(numero, ruta, texto);
            documentos.add(documento);
            if (ruta == null) {
                documento.resolver(Resultado.error("LINEA_NO_VALIDA",
                        "La línea de la lista no es una ruta de archivo válida."));
            } else if (!vistos.add(claveDe(ruta))) {
                documento.resolver(Resultado.de(Estado.OMITIDO, "DUPLICADO", "El documento está repetido en la lista."));
            } else if (!Files.exists(ruta)) {
                documento.resolver(Resultado.error("ARCHIVO_NO_ENCONTRADO", "El archivo no existe."));
            } else if (!ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
                documento.resolver(Resultado.error("EXTENSION_NO_CORRESPONDE",
                        "El archivo no tiene la extensión ." + extension + "."));
            } else if (!Files.isRegularFile(ruta) || !Files.isReadable(ruta)) {
                documento.resolver(Resultado.error("ARCHIVO_ILEGIBLE", "No se puede leer el archivo."));
            }
        }
        if (documentos.isEmpty()) {
            throw new Corte("LISTA_VACIA", "El archivo de lista no contiene ningún documento: " + lista);
        }
        return documentos;
    }

    private static String decodificar(byte[] bytes) throws CharacterCodingException {
        int inicio = bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF && (bytes[1] & 0xff) == 0xBB
                && (bytes[2] & 0xff) == 0xBF ? 3 : 0;
        String texto = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, inicio, bytes.length - inicio))
                .toString();
        if (texto.indexOf('\0') >= 0) {
            // Un archivo en UTF-16 sin marca puede pasar como UTF-8 válido: lo delatan los bytes en cero.
            throw new CharacterCodingException();
        }
        return texto;
    }

    private static String claveDe(Path ruta) {
        String texto = ruta.toString();
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                ? texto.toLowerCase(Locale.ROOT) : texto;
    }

    // ------------------------------------------------------------------------------------------------- salidas

    private int cortarAntesDeEmpezar(String causa, String mensaje) {
        escribirInicio(0);
        error.println("Error: " + mensaje);
        escribirResumen(List.of(), causa);
        return 1;
    }

    private void escribirInicio(int total) {
        salida.println("LOTE_INICIO;" + campos(FORMATO, programa, version, String.valueOf(total)));
    }

    private void escribirResumen(List<Documento> documentos, String causa) {
        Map<Estado, Integer> cuenta = new EnumMap<>(Estado.class);
        for (Documento documento : documentos) {
            cuenta.merge(documento.resultado.estado(), 1, Integer::sum);
        }
        salida.println("LOTE_RESUMEN;" + campos(
                "total=" + documentos.size(),
                "correctos=" + (cuenta.getOrDefault(Estado.LISTO, 0) + cuenta.getOrDefault(Estado.FIRMADO, 0)
                        + cuenta.getOrDefault(Estado.VALIDO, 0)),
                "con_avisos=" + (cuenta.getOrDefault(Estado.FIRMADO_CON_AVISOS, 0)
                        + cuenta.getOrDefault(Estado.VALIDO_CON_OBSERVACIONES, 0)),
                "no_validos=" + cuenta.getOrDefault(Estado.NO_VALIDO, 0),
                "sin_firmas=" + cuenta.getOrDefault(Estado.SIN_FIRMAS, 0),
                "con_error=" + cuenta.getOrDefault(Estado.ERROR, 0),
                "omitidos=" + cuenta.getOrDefault(Estado.OMITIDO, 0),
                "no_procesados=" + cuenta.getOrDefault(Estado.NO_PROCESADO, 0),
                "corte=" + (causa == null ? "NINGUNO" : causa)));
        salida.flush();
        if (informe != null) {
            try {
                informe.close();
            } catch (IOException e) {
                avisarInformeIncompleto();
            }
        }
    }

    private void crearInforme(Opciones opciones) {
        if (opciones.informe() == null) {
            return;
        }
        String nombre = opciones.informe().toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".xml") || nombre.endsWith(".pdf")) {
            throw new Corte("INFORME_NO_CREADO", "El informe no puede ser un archivo .xml ni .pdf: "
                    + opciones.informe());
        }
        try {
            Path archivo = Path.of(opciones.informe()).toAbsolutePath().normalize();
            Path lista = Path.of(opciones.lista()).toAbsolutePath().normalize();
            if (claveDe(archivo).equals(claveDe(lista)) || (Files.exists(archivo) && Files.isSameFile(archivo, lista))) {
                throw new Corte("INFORME_NO_CREADO", "El informe no puede ser el mismo archivo que la lista.");
            }
            informe = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8);
            informe.write(campos("n", "total", "estado", "codigo", "entrada", "salida", "log", "mensaje"));
            informe.newLine();
            informe.flush();
        } catch (IOException | InvalidPathException e) {
            throw new Corte("INFORME_NO_CREADO", "No se pudo crear el informe: " + opciones.informe());
        }
    }

    private void informar(Documento documento, int total, boolean registrar) {
        Resultado r = documento.resultado;
        String entrada = documento.ruta == null ? documento.texto : documento.ruta.toString();
        String log = "";
        if (registrar && r.estado() != Estado.NO_PROCESADO && documento.ruta != null
                && Files.isRegularFile(documento.ruta)) {
            log = escribirRegistro(documento);
        }
        String linea = campos(String.valueOf(documento.numero), String.valueOf(total), r.estado().name(), r.codigo(),
                entrada, r.salida() == null ? "" : r.salida().toString(), log, r.mensaje());
        salida.println("LOTE_DOC;" + linea);
        salida.flush();
        if (informe != null && !informeIncompleto) {
            try {
                informe.write(linea);
                informe.newLine();
                informe.flush();
            } catch (IOException e) {
                avisarInformeIncompleto();
            }
        }
        if (r.estado() != Estado.NO_PROCESADO && !terminoBien(r)) {
            error.println(sinSaltos(entrada) + ": " + sinSaltos(r.mensaje()));
        }
    }

    private void avisarInformeIncompleto() {
        informeIncompleto = true;
        error.println("Error: no se pudo seguir escribiendo el informe; está incompleto.");
    }

    /** Agrega el detalle del documento a su archivo .log; devuelve la ruta, o vacío si no se pudo. */
    private String escribirRegistro(Documento documento) {
        Path archivo = documento.ruta.resolveSibling(documento.ruta.getFileName() + ".log");
        String fin = System.lineSeparator();
        Resultado r = documento.resultado;
        String contenido = "===== " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                + " - " + programa + " " + version + " =====" + fin
                + documento.memoria.toString(StandardCharsets.UTF_8)
                + "Resultado: " + r.estado() + " (" + r.codigo() + ") " + sinSaltos(r.mensaje()) + fin + fin;
        try {
            Files.writeString(archivo, contenido, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            return archivo.toString();
        } catch (IOException e) {
            error.println("Advertencia: no se pudo escribir el registro " + archivo);
            return "";
        }
    }

    private static String campos(String... valores) {
        StringBuilder linea = new StringBuilder();
        for (String valor : valores) {
            if (!linea.isEmpty()) {
                linea.append(';');
            }
            linea.append('"').append(sinSaltos(valor).replace("\"", "\"\"")).append('"');
        }
        return linea.toString();
    }

    private static String sinSaltos(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\r\\n\\u0085\\u2028\\u2029]+", " ").strip();
    }

    // ------------------------------------------------------------------------------------- escritura sin pisar

    /** Nombre temporal, en la misma carpeta que el destino, que no se confunde con un documento. */
    static Path temporalPara(Path destino) {
        return destino.resolveSibling(destino.getFileName() + "." + Long.toHexString(System.nanoTime())
                + "." + ProcessHandle.current().pid() + ".tmp-sfide");
    }

    /**
     * Deja el archivo temporal con su nombre definitivo, solo si ese nombre está libre: nunca reemplaza un
     * archivo existente, ni siquiera si otro proceso lo crea en el mismo momento. Borra siempre el temporal.
     *
     * @return {@code false} si el destino ya existía
     */
    static boolean publicar(Path temporal, Path destino) throws IOException {
        try {
            try {
                // Un enlace falla si el destino existe, de forma indivisible en todos los sistemas.
                Files.createLink(destino, temporal);
            } catch (FileAlreadyExistsException e) {
                return false;
            } catch (IOException | UnsupportedOperationException e) {
                // Discos que no admiten enlaces (por ejemplo, un pendrive FAT32).
                try {
                    Files.move(temporal, destino);
                } catch (FileAlreadyExistsException existe) {
                    return false;
                }
            }
            return true;
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    /** Escribe un archivo nuevo completo o no escribe nada; ver {@link #publicar(Path, Path)}. */
    static boolean guardarSinPisar(Path destino, byte[] contenido) throws IOException {
        Path temporal = temporalPara(destino);
        try {
            Files.write(temporal, contenido);
        } catch (IOException e) {
            Files.deleteIfExists(temporal);
            throw e;
        }
        return publicar(temporal, destino);
    }
}
