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

package com.sauken.s_fide.xml_signer_pkcs11;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyException;
import java.security.ProviderException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.crypto.dsig.XMLSignatureException;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;

/**
 * Lo propio de la firma de XML por lotes: los argumentos, la regla de no mezclar tipos de documento, la
 * revisión de cada documento antes de acceder al certificado y la escritura del archivo firmado.
 * <p>
 * Esta clase está duplicada a propósito en los dos firmadores XML que admiten lote (cada jar debe seguir
 * siendo independiente): si se modifica, hay que actualizar las dos copias (solo cambia la línea
 * {@code package}).
 */
final class LoteXml {

    /** Lectura del documento y reglas de firma, que cada firmador ya tiene para el modo de un documento. */
    interface Xml {
        Document leer(String ruta, PrintStream salida) throws Exception;

        /** "COD", "DJO" o {@code null} si no es un documento de comercio exterior. */
        String tipoComex(Document doc);

        boolean existeElemento(Document doc, String elemento) throws Exception;

        boolean tieneFirma(Document doc, String elemento);
    }

    /** Firma un documento y devuelve el contenido del archivo firmado. */
    interface Firma {
        byte[] firmar(String ruta) throws Exception;
    }

    record Pedido(Lote.Opciones opciones, List<String> credencial, String elemento, boolean omitirRevocacion) {
    }

    private static final String OTRO = "OTRO";

    private LoteXml() {
    }

    /**
     * {@code -lote <lista> <credencial...> <elemento> [-omitir-revocacion true|false] [-informe <csv>] [-log]
     * [-revisar]}. Los primeros se toman por posición (el elemento puede ser vacío); las opciones que siguen
     * se controlan estrictamente: un argumento desconocido o repetido corta todo.
     *
     * @throws IllegalArgumentException con el motivo, en español
     */
    static Pedido analizarArgumentos(String[] args, int cantidadDeCredencial) {
        int fijos = 2 + cantidadDeCredencial + 1;
        if (args.length < fijos) {
            throw new IllegalArgumentException("Faltan argumentos para firmar por lotes. Use -ayuda para ver la forma "
                    + "de invocación.");
        }
        List<String> credencial = new ArrayList<>(List.of(args).subList(2, 2 + cantidadDeCredencial));
        String elemento = args[2 + cantidadDeCredencial];
        // Ningún nombre de elemento XML empieza con "-": ahí hay una opción porque falta un argumento anterior.
        if (elemento.startsWith("-")) {
            throw new IllegalArgumentException("Falta un argumento: en el lugar del elemento a firmar está "
                    + elemento + ". Escriba todos los argumentos en orden; para firmar el documento completo, el "
                    + "elemento es \"\".");
        }

        String informe = null;
        Boolean omitir = null;
        boolean registro = false;
        boolean revisar = false;
        for (int i = fijos; i < args.length; i++) {
            String opcion = args[i];
            if (Lote.esOpcionRegistro(opcion) && !registro) {
                registro = true;
            } else if (Lote.esOpcionRevisar(opcion) && !revisar) {
                revisar = true;
            } else if (Lote.esOpcionInforme(opcion) && informe == null && i + 1 < args.length) {
                informe = args[++i];
            } else if (("-omitir-revocacion".equalsIgnoreCase(opcion) || "--omitir-revocacion".equalsIgnoreCase(opcion))
                    && omitir == null && i + 1 < args.length
                    && ("true".equalsIgnoreCase(args[i + 1]) || "false".equalsIgnoreCase(args[i + 1]))) {
                omitir = Boolean.parseBoolean(args[++i]);
            } else {
                throw new IllegalArgumentException("Argumento no reconocido, repetido o incompleto: " + opcion);
            }
        }
        return new Pedido(new Lote.Opciones(args[1], informe, registro, revisar), credencial, elemento,
                omitir != null && omitir);
    }

    /**
     * Revisión previa, sin certificado: lee cada documento, comprueba que el lote no mezcle tipos y resuelve
     * los documentos que no se pueden firmar.
     *
     * @throws Lote.Corte si el lote mezcla documentos de distinto tipo
     */
    static void revisar(List<Lote.Documento> pendientes, String elemento, Xml xml) {
        List<Lote.Documento> legibles = new ArrayList<>();
        List<Document> contenidos = new ArrayList<>();
        List<String> tipos = new ArrayList<>();
        for (Lote.Documento documento : pendientes) {
            XmlBom.reiniciarAviso();
            Document contenido = Lote.enRegistro(documento, () -> {
                try {
                    return xml.leer(documento.ruta().toString(), documento.registro());
                } catch (IOException e) {
                    documento.resolver(Lote.Resultado.error("ARCHIVO_ILEGIBLE", "No se puede leer el archivo."));
                } catch (SAXParseException e) {
                    documento.resolver(Lote.Resultado.error("DOCUMENTO_MAL_FORMADO", "El archivo XML está mal formado "
                            + "(línea " + e.getLineNumber() + ", columna " + e.getColumnNumber() + ")."));
                } catch (IllegalArgumentException e) {
                    // Mensajes propios, ya redactados para la persona (por ejemplo, un XML en UTF-16).
                    documento.resolver(Lote.Resultado.error("DOCUMENTO_MAL_FORMADO", e.getMessage()));
                } catch (Exception e) {
                    documento.resolver(Lote.Resultado.error("DOCUMENTO_MAL_FORMADO",
                            "No se pudo procesar el documento XML. Verifique que sea un XML válido."));
                }
                return null;
            });
            if (contenido != null) {
                String tipo = xml.tipoComex(contenido);
                legibles.add(documento);
                contenidos.add(contenido);
                tipos.add(tipo == null ? OTRO : tipo);
            }
        }

        String nombre = elemento.startsWith("#") ? elemento.substring(1) : elemento;
        String esperado = switch (nombre) {
            case "COD", "CODEH" -> "COD";
            case "DJO", "DJOEH" -> "DJO";
            case "" -> OTRO;
            default -> tipos.isEmpty() ? OTRO : tipos.get(0);
        };
        boolean mezcla = false;
        for (int i = 0; i < legibles.size(); i++) {
            if (!tipos.get(i).equals(esperado)) {
                mezcla = true;
                legibles.get(i).resolver(Lote.Resultado.error("TIPO_NO_CORRESPONDE", "El documento es "
                        + descripcion(tipos.get(i)) + " y este lote es para " + descripcion(esperado) + "."));
            }
        }
        if (mezcla) {
            throw new Lote.Corte("LOTE_MEZCLADO", "No se firmó ningún documento: el lote incluye documentos que no "
                    + "corresponden a " + (elemento.isEmpty() ? "la firma del documento completo"
                    : "la firma del elemento " + elemento) + ". Sepárelos y vuelva a ejecutar.");
        }

        for (int i = 0; i < legibles.size(); i++) {
            revisarUno(legibles.get(i), contenidos.get(i), elemento, xml);
        }
    }

    private static void revisarUno(Lote.Documento documento, Document contenido, String elemento, Xml xml) {
        Path destino = salidaDe(documento.ruta());
        if (Files.exists(destino)) {
            documento.resolver(yaExiste(destino));
            return;
        }
        boolean existe;
        try {
            existe = elemento.isEmpty() || xml.existeElemento(contenido, elemento);
        } catch (Exception e) {
            existe = false;
        }
        if (!existe) {
            documento.resolver(Lote.Resultado.error("ELEMENTO_NO_ENCONTRADO",
                    "El elemento o párrafo XML especificado no existe en el documento XML."));
        } else if (xml.tieneFirma(contenido, elemento)) {
            documento.resolver(Lote.Resultado.error("ELEMENTO_YA_FIRMADO", elemento.isEmpty()
                    ? "El documento ya tiene una firma digital aplicada sobre todo su contenido. No se puede firmar "
                    + "el mismo elemento dos veces."
                    : "El elemento '" + elemento + "' ya tiene una firma digital aplicada. No se puede firmar el mismo "
                    + "elemento dos veces."));
        } else if ("CODEH".equals(elemento) && !xml.tieneFirma(contenido, "COD")) {
            documento.resolver(Lote.Resultado.error("FALTA_FIRMA_PREVIA",
                    "No se puede firmar el elemento CODEH: no existe una firma digital previa sobre el elemento COD."));
        } else if ("DJOEH".equals(elemento) && !xml.tieneFirma(contenido, "DJO")) {
            documento.resolver(Lote.Resultado.error("FALTA_FIRMA_PREVIA",
                    "No se puede firmar el elemento DJOEH: no existe una firma digital previa sobre el elemento DJO."));
        }
    }

    /** Firma un documento ya revisado y guarda el resultado sin reemplazar ningún archivo. */
    static Lote.Resultado firmar(Lote.Documento documento, Firma firma) {
        XmlBom.reiniciarAviso();
        byte[] firmado;
        try {
            firmado = firma.firmar(documento.ruta().toString());
        } catch (Lote.Corte corte) {
            throw corte;
        } catch (Exception e) {
            // Solo los mensajes propios llegan a la persona; el detalle técnico de otras fallas, no.
            String detalle = e instanceof IllegalArgumentException ? ": " + e.getMessage() : ".";
            return Lote.Resultado.error(esFallaDeLaClave(e) ? "ERROR_AL_FIRMAR" : "ERROR_INESPERADO",
                    "No se pudo firmar el documento" + detalle);
        }
        Path destino = salidaDe(documento.ruta());
        try {
            if (!Lote.guardarSinPisar(destino, firmado)) {
                return yaExiste(destino);
            }
        } catch (IOException e) {
            return Lote.Resultado.error("SALIDA_NO_ESCRIBIBLE", "No se pudo guardar el archivo firmado " + destino
                    + ". Verifique los permisos y el espacio de la carpeta.");
        }
        documento.registro().println("Documento XML firmado guardado como: " + destino);
        return new Lote.Resultado(Lote.Estado.FIRMADO, "OK", destino, "");
    }

    /** Mismo nombre con el sufijo "-signed" antes de la extensión, en la misma carpeta. */
    static Path salidaDe(Path documento) {
        String nombre = documento.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return documento.resolveSibling(punto > 0
                ? nombre.substring(0, punto) + "-signed" + nombre.substring(punto) : nombre + "-signed");
    }

    private static Lote.Resultado yaExiste(Path destino) {
        return new Lote.Resultado(Lote.Estado.OMITIDO, "SALIDA_YA_EXISTE", destino,
                "Ya existe el archivo firmado: no se vuelve a firmar ni se reemplaza.");
    }

    private static boolean esFallaDeLaClave(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof XMLSignatureException || causa instanceof SignatureException
                    || causa instanceof KeyException || causa instanceof ProviderException) {
                return true;
            }
        }
        return false;
    }

    private static String descripcion(String tipo) {
        return switch (tipo) {
            case "COD" -> "un Certificado de Origen Digital";
            case "DJO" -> "una Declaración Jurada de Origen";
            default -> "un XML que no es de comercio exterior";
        };
    }
}
