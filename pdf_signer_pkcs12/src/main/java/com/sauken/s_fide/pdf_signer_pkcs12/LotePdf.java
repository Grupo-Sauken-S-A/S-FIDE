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

package com.sauken.s_fide.pdf_signer_pkcs12;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.sauken.s_fide.pdf_signer_pkcs12.validation.PdfDocumentAnalyzer;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.ProviderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo propio de la firma de PDF por lotes: el control estricto de los argumentos, la revisión de cada documento
 * antes de acceder al certificado, y la escritura del archivo firmado sin reemplazar ninguno.
 * <p>
 * Esta clase está duplicada a propósito en los dos firmadores PDF que admiten lote (cada jar debe seguir
 * siendo independiente): si se modifica, hay que actualizar las dos copias (solo cambia el nombre del módulo
 * en el {@code package} y los {@code import}).
 */
final class LotePdf {

    /** Qué tipo de valor lleva cada opción. */
    enum Tipo {
        TEXTO, ENTERO, NUMERO, VERDADERO_O_FALSO
    }

    /** Una opción del firmador: sus formas de escribirla y si es obligatoria. */
    record Opcion(String nombre, Tipo tipo, boolean obligatoria, List<String> formas) {
        static Opcion de(Tipo tipo, String... formas) {
            return new Opcion(formas[0], tipo, false, List.of(formas));
        }

        static Opcion obligatoria(Tipo tipo, String... formas) {
            return new Opcion(formas[0], tipo, true, List.of(formas));
        }
    }

    /**
     * Pedido de firma por lotes.
     *
     * @param argumentos las opciones de firma tal como las entiende el modo de un documento, sin las propias
     *                   del lote y sin {@code -i}
     * @param valores    valor de cada opción presente, por su nombre principal
     */
    record Pedido(Lote.Opciones opciones, List<String> argumentos, Map<String, String> valores) {
    }

    /** Reglas que cada firmador ya tiene para el modo de un documento. */
    interface Reglas {
        /** Por qué un documento cifrado no admite firmas, redactado para la persona. */
        String motivoDelCifrado(Path documento);

        /** @throws IllegalArgumentException si la página, la posición o la protección no sirven para el documento */
        void validarPosicionYProteccion(Path documento, PdfDocumentAnalyzer.Analisis analisis);
    }

    /** Firma un documento dejando el resultado, ya controlado, en {@code destino}. */
    interface Firma {
        void firmar(Path original, PdfDocumentAnalyzer.Analisis analisis, Path destino) throws Exception;
    }

    private LotePdf() {
    }

    /** Si entre las opciones (nunca como valor de otra) está {@code -lote}. */
    static boolean pideLote(String[] args, List<Opcion> opciones) {
        return contieneBandera(args, opciones, true);
    }

    /** Si se pidió revisar sin lote: en ese caso hay que rechazar todo, nunca firmar. */
    static boolean pideRevisar(String[] args, List<Opcion> opciones) {
        return contieneBandera(args, opciones, false);
    }

    private static boolean contieneBandera(String[] args, List<Opcion> opciones, boolean lote) {
        if (args.length > 0 && args[0].matches("(?i)--?(vista-previa-texto|verificar-revocacion|analizar-documento)")) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if (lote ? Lote.esOpcionDeLote(args[i]) : Lote.esOpcionRevisar(args[i])) {
                return true;
            }
            if (buscar(opciones, args[i]) != null || Lote.esOpcionInforme(args[i])) {
                i++;
            }
        }
        return false;
    }

    private static Opcion buscar(List<Opcion> opciones, String forma) {
        for (Opcion opcion : opciones) {
            if (opcion.formas().contains(forma)) {
                return opcion;
            }
        }
        return null;
    }

    /**
     * Control estricto: en lote, un argumento desconocido, repetido, mal escrito o no admitido corta todo,
     * porque afectaría a todos los documentos.
     *
     * @throws IllegalArgumentException con el motivo, en español
     */
    static Pedido analizarArgumentos(String[] args, List<Opcion> opciones) {
        String lista = null;
        String informe = null;
        boolean registro = false;
        boolean revisar = false;
        List<String> argumentos = new ArrayList<>();
        Map<String, String> valores = new java.util.HashMap<>();
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String forma = args[i];
            Opcion opcion = buscar(opciones, forma);
            if (Lote.esOpcionDeLote(forma)) {
                lista = valorDe(args, i++, forma, vistas.add("-lote"));
            } else if (Lote.esOpcionInforme(forma)) {
                informe = valorDe(args, i++, forma, vistas.add("-informe"));
            } else if (Lote.esOpcionRegistro(forma) && vistas.add("-log")) {
                registro = true;
            } else if (Lote.esOpcionRevisar(forma) && vistas.add("-revisar")) {
                revisar = true;
            } else if (opcion != null) {
                String valor = valorDe(args, i++, forma, vistas.add(opcion.nombre()));
                comprobar(opcion, forma, valor);
                valores.put(opcion.nombre(), valor);
                argumentos.add(forma);
                argumentos.add(valor);
            } else if ("-i".equals(forma) || "--input".equals(forma)) {
                throw new IllegalArgumentException("Con -lote no se usa -i: los documentos van en la lista.");
            } else if (forma.matches("(?i)--?campo")) {
                throw new IllegalArgumentException("-campo no se admite por lotes: cada documento puede tener el campo "
                        + "en otro lugar. Fírmelos de a uno.");
            } else {
                throw new IllegalArgumentException("Argumento no reconocido o repetido: " + forma);
            }
        }
        if (lista == null) {
            throw new IllegalArgumentException("Falta el archivo de lista después de -lote.");
        }
        for (Opcion opcion : opciones) {
            if (opcion.obligatoria() && !valores.containsKey(opcion.nombre())) {
                throw new IllegalArgumentException("Falta el argumento obligatorio " + opcion.nombre() + ".");
            }
        }
        return new Pedido(new Lote.Opciones(lista, informe, registro, revisar), argumentos, valores);
    }

    private static String valorDe(String[] args, int posicion, String forma, boolean primeraVez) {
        if (!primeraVez) {
            throw new IllegalArgumentException("Argumento repetido: " + forma);
        }
        if (posicion + 1 >= args.length) {
            throw new IllegalArgumentException("Falta el valor del argumento " + forma + ".");
        }
        return args[posicion + 1];
    }

    private static void comprobar(Opcion opcion, String forma, String valor) {
        boolean correcto = switch (opcion.tipo()) {
            case TEXTO -> true;
            case ENTERO -> valor.matches("\\d{1,9}");
            case NUMERO -> valor.matches("-?\\d+(\\.\\d+)?");
            case VERDADERO_O_FALSO -> "true".equalsIgnoreCase(valor) || "false".equalsIgnoreCase(valor);
        };
        if (!correcto) {
            throw new IllegalArgumentException("El valor del argumento " + forma + " no es válido: " + valor);
        }
    }

    /** Revisión previa, sin certificado: resuelve los documentos que no se pueden firmar. */
    static void revisar(List<Lote.Documento> pendientes, Reglas reglas) {
        for (Lote.Documento documento : pendientes) {
            Lote.enRegistro(documento, () -> {
                revisarUno(documento, reglas);
                return null;
            });
        }
    }

    private static void revisarUno(Lote.Documento documento, Reglas reglas) {
        Path destino = salidaDe(documento.ruta());
        if (Files.exists(destino)) {
            documento.resolver(yaExiste(destino));
            return;
        }
        PdfDocumentAnalyzer.Analisis analisis;
        try {
            boolean cifrado;
            try (PdfReader lector = new PdfReader(documento.ruta().toFile());
                 PdfDocument pdf = new PdfDocument(lector)) {
                cifrado = lector.isEncrypted();
            }
            analisis = PdfDocumentAnalyzer.analizar(documento.ruta(), false);
            if (analisis.estado() == PdfDocumentAnalyzer.Estado.CERRADO) {
                documento.resolver(Lote.Resultado.error("DOCUMENTO_CERRADO", analisis.motivoCierre()));
                return;
            }
            if (cifrado) {
                documento.resolver(Lote.Resultado.error("DOCUMENTO_RESTRINGIDO",
                        reglas.motivoDelCifrado(documento.ruta())));
                return;
            }
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            documento.resolver(Lote.Resultado.error("DOCUMENTO_MAL_FORMADO", e.getMessage()));
            return;
        } catch (IOException | RuntimeException e) {
            documento.resolver(ilegibleOCifrado(documento.ruta(), reglas));
            return;
        }
        try {
            reglas.validarPosicionYProteccion(documento.ruta(), analisis);
        } catch (IllegalArgumentException e) {
            documento.resolver(Lote.Resultado.error(codigoDePosicion(e.getMessage()), e.getMessage()));
        }
    }

    /** Un documento que no se abre puede estar bloqueado (y el análisis sabe decirlo) o simplemente dañado. */
    private static Lote.Resultado ilegibleOCifrado(Path documento, Reglas reglas) {
        try {
            PdfDocumentAnalyzer.Analisis analisis = PdfDocumentAnalyzer.analizar(documento, false);
            return analisis.estado() == PdfDocumentAnalyzer.Estado.CERRADO
                    ? Lote.Resultado.error("DOCUMENTO_CERRADO", analisis.motivoCierre())
                    : Lote.Resultado.error("DOCUMENTO_RESTRINGIDO", reglas.motivoDelCifrado(documento));
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            return Lote.Resultado.error("DOCUMENTO_MAL_FORMADO", e.getMessage());
        } catch (RuntimeException e) {
            return Lote.Resultado.error("DOCUMENTO_MAL_FORMADO",
                    "No se pudo leer el documento PDF. Verifique que el archivo no esté dañado.");
        }
    }

    private static String codigoDePosicion(String mensaje) {
        if (mensaje.contains("protec")) {
            return "PROTECCION_NO_APLICABLE";
        }
        return mensaje.contains("no existe") ? "PAGINA_INEXISTENTE" : "FIRMA_NO_ENTRA";
    }

    /**
     * Firma un documento ya revisado: escribe en un temporal, y solo si el resultado es correcto lo deja con su
     * nombre definitivo, sin reemplazar ningún archivo.
     */
    static Lote.Resultado firmar(Lote.Documento documento, Firma firma) {
        Path original = documento.ruta();
        Path destino = salidaDe(original);
        if (Files.exists(destino)) {
            return yaExiste(destino);
        }
        Path temporal = Lote.temporalPara(destino);
        try {
            PdfDocumentAnalyzer.Analisis analisis = PdfDocumentAnalyzer.analizar(original, false);
            // El informe de las firmas anteriores (consulta su revocación) queda en el registro del documento.
            PdfDocumentAnalyzer.Analisis previas = null;
            if (!analisis.firmas().isEmpty()) {
                previas = PdfDocumentAnalyzer.analizar(original, true);
                PdfDocumentAnalyzer.imprimirInforme(previas, System.out);
            }
            firma.firmar(original, analisis, temporal);
            if (!Lote.publicar(temporal, destino)) {
                return yaExiste(destino);
            }
            System.out.println("Documento firmado guardado como: " + destino);
            return resultadoDeLaFirma(destino, previas);
        } catch (Lote.Corte corte) {
            throw corte;
        } catch (PdfDocumentAnalyzer.DocumentoIlegibleException e) {
            return Lote.Resultado.error("DOCUMENTO_MAL_FORMADO", e.getMessage());
        } catch (FileSystemException e) {
            return Lote.Resultado.error("SALIDA_NO_ESCRIBIBLE", "No se pudo guardar el archivo firmado " + destino
                    + ". Verifique los permisos y el espacio de la carpeta.");
        } catch (Exception e) {
            // Solo los mensajes propios llegan a la persona; el detalle técnico de otras fallas, no.
            String detalle = e instanceof IllegalArgumentException || e instanceof IllegalStateException
                    ? ": " + e.getMessage() : ".";
            return Lote.Resultado.error(esFallaDeLaClave(e) ? "ERROR_AL_FIRMAR" : "ERROR_INESPERADO",
                    "No se pudo firmar el documento" + detalle);
        } finally {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException e) {
                System.out.println("No se pudo borrar el archivo temporal " + temporal);
            }
        }
    }

    /**
     * Una firma anterior inválida no impide firmar (la decisión es de quien firma), pero en un lote nadie mira
     * cada documento: queda señalado en su resultado.
     */
    private static Lote.Resultado resultadoDeLaFirma(Path destino, PdfDocumentAnalyzer.Analisis previas) {
        if (previas != null && previas.hayProblemas()) {
            for (PdfDocumentAnalyzer.FirmaPrevia anterior : previas.firmas()) {
                if (anterior.veredicto() == PdfDocumentAnalyzer.Veredicto.INVALIDA) {
                    return new Lote.Resultado(Lote.Estado.FIRMADO_CON_AVISOS, "FIRMA_PREVIA_INVALIDA", destino,
                            "Se firmó, pero hay una firma anterior que no es válida. " + anterior.mensaje());
                }
            }
            return new Lote.Resultado(Lote.Estado.FIRMADO_CON_AVISOS, "MODIFICADO_DESPUES_DE_FIRMAR", destino,
                    "Se firmó, pero atención: " + previas.cambios().mensaje());
        }
        boolean pendientes = previas != null && previas.firmas().stream()
                .anyMatch(f -> f.veredicto() == PdfDocumentAnalyzer.Veredicto.NO_VERIFICABLE);
        return new Lote.Resultado(Lote.Estado.FIRMADO, "OK", destino, pendientes
                ? "No se pudieron completar todas las comprobaciones de las firmas anteriores." : "");
    }

    /** Mismo nombre con el sufijo "-signed" antes de la extensión, en la misma carpeta. */
    static Path salidaDe(Path documento) {
        String nombre = documento.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return documento.resolveSibling(punto < 0
                ? nombre + "-signed" : nombre.substring(0, punto) + "-signed" + nombre.substring(punto));
    }

    private static Lote.Resultado yaExiste(Path destino) {
        return new Lote.Resultado(Lote.Estado.OMITIDO, "SALIDA_YA_EXISTE", destino,
                "Ya existe el archivo firmado: no se vuelve a firmar ni se reemplaza.");
    }

    private static boolean esFallaDeLaClave(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof GeneralSecurityException || causa instanceof ProviderException) {
                return true;
            }
        }
        return false;
    }
}
