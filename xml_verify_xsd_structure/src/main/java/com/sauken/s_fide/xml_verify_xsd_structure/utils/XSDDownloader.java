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

  Autores: Juan Carlos Ríos y Juan Ignacio Ríos con la asistencia de Claude AI 3.5 Sonnet
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

  Authors: Juan Carlos Ríos y Juan Ignacio Ríos with support of Claude AI 3.5 Sonnet
  E-mail: mailto:jrios@sauken.com.ar,nrios@sauken.com.ar
  Company: Grupo Sauken S.A.
  WebSite: https://www.sauken.com.ar/
  Git: https://github.com/Grupo-Sauken-S-A/S-FIDE

 */

package com.sauken.s_fide.xml_verify_xsd_structure.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

public class XSDDownloader {
    private static final PrintStream errorStream;

    static {
        try {
            errorStream = new PrintStream(System.err, true, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error al configurar la codificación de errores");
        }
    }

    public static String extractXSDUrlFromXML(Document doc) {
        // Obtener el elemento raíz
        Element root = doc.getDocumentElement();

        // 1. Primero buscar en schemaLocation como atributo
        String schemaLocation = root.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation");
        if (schemaLocation != null && !schemaLocation.trim().isEmpty()) {
            String[] pairs = schemaLocation.trim().split("\\s+");
            if (pairs.length >= 2) {
                String xsdUrl = pairs[pairs.length - 1];
                return normalizeUrl(xsdUrl);
            }
        }

        // 2. Buscar en schemaLocation como elemento (backward compatibility)
        NodeList schemaLocations = doc.getElementsByTagNameNS(
                "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation");
        if (schemaLocations.getLength() > 0) {
            String schemaLocationValue = schemaLocations.item(0).getNodeValue();
            if (schemaLocationValue != null && !schemaLocationValue.trim().isEmpty()) {
                String[] parts = schemaLocationValue.split("\\s+");
                if (parts.length >= 2) {
                    return normalizeUrl(parts[parts.length - 1]);
                }
            }
        }

        // 3. Buscar en noNamespaceSchemaLocation como atributo
        String noNamespaceSchema = root.getAttributeNS(
                "http://www.w3.org/2001/XMLSchema-instance",
                "noNamespaceSchemaLocation"
        );
        if (noNamespaceSchema != null && !noNamespaceSchema.trim().isEmpty()) {
            return normalizeUrl(noNamespaceSchema.trim());
        }

        // 4. Buscar en noNamespaceSchemaLocation como elemento (backward compatibility)
        NodeList noNamespaceSchemas = doc.getElementsByTagNameNS(
                "http://www.w3.org/2001/XMLSchema-instance", "noNamespaceSchemaLocation");
        if (noNamespaceSchemas.getLength() > 0) {
            String value = noNamespaceSchemas.item(0).getNodeValue();
            if (value != null && !value.trim().isEmpty()) {
                return normalizeUrl(value.trim());
            }
        }

        return null;
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        url = url.trim();
        return url;
    }

    public static File downloadXSD(String xsdUrl) throws IOException {
        checkInternetConnection();

        String normalizedUrl = normalizeUrl(xsdUrl);
        if (normalizedUrl == null) {
            throw new IOException("URL del XSD no válida");
        }

        IOException lastException = null;

        // Lista de URLs a intentar
        String[] urlsToTry = new String[]{normalizedUrl};

        // Si es http://, agregar https:// como alternativa
        if (normalizedUrl.startsWith("http://")) {
            urlsToTry = new String[]{
                    normalizedUrl,
                    "https://" + normalizedUrl.substring(7)
            };
        }
        // Si es https://, agregar http:// como alternativa
        else if (normalizedUrl.startsWith("https://")) {
            urlsToTry = new String[]{
                    normalizedUrl,
                    "http://" + normalizedUrl.substring(8)
            };
        }

        // Intentar cada URL
        for (String urlStr : urlsToTry) {
            try {
                return downloadFromUrl(urlStr);
            } catch (IOException e) {
                lastException = e;
                errorStream.println("Advertencia: No se pudo descargar de " + urlStr + ": " + e.getMessage());
                // Continuar con la siguiente URL si hay alguna
            }
        }

        // Si llegamos aquí, ninguna URL funcionó
        throw new IOException("No se pudo descargar el archivo XSD de ninguna URL", lastException);
    }

    private static File downloadFromUrl(String xsdUrl) throws IOException {
        URL url = new URL(xsdUrl);
        String fileName = new File(url.getPath()).getName();
        File tempFile = Files.createTempFile("schema_", fileName).toFile();
        tempFile.deleteOnExit();

        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            // Limpiar el archivo temporal si algo sale mal
            tempFile.delete();
            throw e;
        }

        return tempFile;
    }

    private static void checkInternetConnection() throws IOException {
        if (!InternetConnectivityChecker.isInternetAvailable()) {
            errorStream.println("Error: No hay conexión a Internet disponible");
            throw new IOException("No hay conexión a Internet disponible");
        }
    }
}