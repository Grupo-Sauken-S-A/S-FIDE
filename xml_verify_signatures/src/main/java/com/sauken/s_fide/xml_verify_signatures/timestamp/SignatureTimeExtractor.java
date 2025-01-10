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

package com.sauken.s_fide.xml_verify_signatures.timestamp;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SignatureTimeExtractor {
    public static class SigningTimeResult {
        private final Date date;
        private final String errorMessage;
        private final boolean useCurrentTime;

        public SigningTimeResult(Date date, String errorMessage, boolean useCurrentTime) {
            this.date = date;
            this.errorMessage = errorMessage;
            this.useCurrentTime = useCurrentTime;
        }

        public Date getDate() {
            return date;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isUsingCurrentTime() {
            return useCurrentTime;
        }
    }

    public static SigningTimeResult extractSigningTime(Element signatureElement) {
        try {
            // Get the Reference element to determine what is being signed
            NodeList referenceNodes = signatureElement.getElementsByTagNameNS(
                    "http://www.w3.org/2000/09/xmldsig#", "Reference");
            if (referenceNodes.getLength() == 0) {
                return new SigningTimeResult(
                        new Date(),
                        "No se encontró elemento Reference en la firma. Se utilizará la fecha actual.",
                        true
                );
            }

            Element referenceElement = (Element) referenceNodes.item(0);
            String uri = referenceElement.getAttribute("URI");
            if (uri.isEmpty()) {
                return new SigningTimeResult(
                        new Date(),
                        "URI de Reference vacío en la firma. Se utilizará la fecha actual.",
                        true
                );
            }

            if (uri.startsWith("#")) {
                uri = uri.substring(1); // Remove the # character
            }

            // Find the root element of the document
            Element rootElement = signatureElement.getOwnerDocument().getDocumentElement();

            // Handle different types of signed content
            switch (uri) {
                case "COD":
                    return extractDateFromCOD(rootElement);
                case "CODEH":
                    return extractDateFromCODEH(rootElement);
                default:
                    return new SigningTimeResult(
                            new Date(),
                            "URI de Reference '" + uri + "' no reconocido. Se utilizará la fecha actual.",
                            true
                    );
            }
        } catch (Exception e) {
            return new SigningTimeResult(
                    new Date(),
                    "Error al extraer tiempo de firma: " + e.getMessage() + ". Se utilizará la fecha actual.",
                    true
            );
        }
    }

    private static SigningTimeResult extractDateFromCOD(Element rootElement) {
        try {
            NodeList declarationDates = rootElement.getElementsByTagName("DeclarationDate");
            if (declarationDates.getLength() == 0) {
                return new SigningTimeResult(
                        new Date(),
                        "No se encontró el elemento DeclarationDate en COD. Se utilizará la fecha actual.",
                        true
                );
            }

            String timeStr = declarationDates.item(0).getTextContent().trim();
            if (timeStr.isEmpty()) {
                return new SigningTimeResult(
                        new Date(),
                        "DeclarationDate en COD está vacío. Se utilizará la fecha actual.",
                        true
                );
            }

            // Obtener el país exportador
            NodeList exporterCountries = rootElement.getElementsByTagName("ExporterCountry");
            if (exporterCountries.getLength() == 0) {
                return new SigningTimeResult(
                        new Date(),
                        "No se encontró el elemento ExporterCountry. Se utilizará la fecha actual.",
                        true
                );
            }

            String countryCode = exporterCountries.item(0).getTextContent().trim();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            Date localDate = sdf.parse(timeStr);

            // Convertir a UTC usando el país exportador
            Date utcDate = TimezoneConverter.convertToUTC(localDate, countryCode);
            return new SigningTimeResult(utcDate, null, false);

        } catch (Exception e) {
            return new SigningTimeResult(
                    new Date(),
                    "Error al procesar DeclarationDate en COD: " + e.getMessage() + ". Se utilizará la fecha actual.",
                    true
            );
        }
    }

    private static SigningTimeResult extractDateFromCODEH(Element rootElement) {
        try {
            NodeList certificateDates = rootElement.getElementsByTagName("CertificateDate");
            if (certificateDates.getLength() == 0) {
                return new SigningTimeResult(
                        new Date(),
                        "No se encontró el elemento CertificateDate en CODEH. Se utilizará la fecha actual.",
                        true
                );
            }

            String timeStr = certificateDates.item(0).getTextContent().trim();
            if (timeStr.isEmpty()) {
                return new SigningTimeResult(
                        new Date(),
                        "CertificateDate en CODEH está vacío. Se utilizará la fecha actual.",
                        true
                );
            }

            // Obtener el país exportador
            NodeList exporterCountries = rootElement.getElementsByTagName("ExporterCountry");
            if (exporterCountries.getLength() == 0) {
                return new SigningTimeResult(
                        new Date(),
                        "No se encontró el elemento ExporterCountry. Se utilizará la fecha actual.",
                        true
                );
            }

            String countryCode = exporterCountries.item(0).getTextContent().trim();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            Date localDate = sdf.parse(timeStr);

            // Convertir a UTC usando el país exportador
            Date utcDate = TimezoneConverter.convertToUTC(localDate, countryCode);
            return new SigningTimeResult(utcDate, null, false);

        } catch (Exception e) {
            return new SigningTimeResult(
                    new Date(),
                    "Error al procesar CertificateDate en CODEH: " + e.getMessage() + ". Se utilizará la fecha actual.",
                    true
            );
        }
    }
}