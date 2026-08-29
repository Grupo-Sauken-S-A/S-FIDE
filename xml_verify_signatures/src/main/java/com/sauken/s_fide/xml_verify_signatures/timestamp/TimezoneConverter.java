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

import java.time.*;
import java.util.*;

public class TimezoneConverter {
    private static final Map<String, String> COUNTRY_TIMEZONES = new HashMap<>();

    static {
        // Inicializamos el mapa con los países y sus zonas horarias principales
        COUNTRY_TIMEZONES.put("AR", "America/Buenos_Aires");   // Argentina
        COUNTRY_TIMEZONES.put("BO", "America/La_Paz");         // Bolivia
        COUNTRY_TIMEZONES.put("BR", "America/Sao_Paulo");      // Brasil
        COUNTRY_TIMEZONES.put("CL", "America/Santiago");       // Chile
        COUNTRY_TIMEZONES.put("CO", "America/Bogota");         // Colombia
        COUNTRY_TIMEZONES.put("CU", "America/Havana");         // Cuba
        COUNTRY_TIMEZONES.put("EC", "America/Guayaquil");      // Ecuador
        COUNTRY_TIMEZONES.put("MX", "America/Mexico_City");    // México
        COUNTRY_TIMEZONES.put("PA", "America/Panama");         // Panamá
        COUNTRY_TIMEZONES.put("PY", "America/Asuncion");       // Paraguay
        COUNTRY_TIMEZONES.put("PE", "America/Lima");           // Perú
        COUNTRY_TIMEZONES.put("UY", "America/Montevideo");     // Uruguay
        COUNTRY_TIMEZONES.put("VE", "America/Caracas");        // Venezuela
    }

    /**
     * Convierte una fecha local a UTC basada en el país exportador
     * @param localDateTime La fecha y hora en formato local
     * @param countryCode El código de país ISO 3166-1 alfa-2
     * @return Date en UTC
     */
    public static Date convertToUTC(Date localDateTime, String countryCode) {
        if (localDateTime == null || countryCode == null) {
            throw new IllegalArgumentException("La fecha y el código de país no pueden ser nulos");
        }

        String zoneId = COUNTRY_TIMEZONES.get(countryCode.toUpperCase());
        if (zoneId == null) {
            throw new IllegalArgumentException("Código de país no soportado: " + countryCode);
        }

        // Convertir Date a LocalDateTime
        LocalDateTime localDT = LocalDateTime.ofInstant(
                localDateTime.toInstant(),
                ZoneId.of(zoneId)
        );

        // Crear ZonedDateTime con la zona horaria del país
        ZonedDateTime zonedDT = localDT.atZone(ZoneId.of(zoneId));

        // Convertir a UTC
        ZonedDateTime utcDT = zonedDT.withZoneSameInstant(ZoneOffset.UTC);

        // Convertir de vuelta a Date
        return Date.from(utcDT.toInstant());
    }

    /**
     * Convierte un nombre de país a su código ISO 3166-1 alfa-2
     * @param countryName Nombre del país en español
     * @return Código ISO 3166-1 alfa-2
     */
    public static String getCountryCode(String countryName) {
        if (countryName == null) {
            throw new IllegalArgumentException("El nombre del país no puede ser nulo");
        }

        Map<String, String> countryNameToCode = new HashMap<>();
        countryNameToCode.put("ARGENTINA", "AR");
        countryNameToCode.put("BOLIVIA", "BO");
        countryNameToCode.put("BRASIL", "BR");
        countryNameToCode.put("CHILE", "CL");
        countryNameToCode.put("COLOMBIA", "CO");
        countryNameToCode.put("CUBA", "CU");
        countryNameToCode.put("ECUADOR", "EC");
        countryNameToCode.put("MÉXICO", "MX");
        countryNameToCode.put("MEXICO", "MX");
        countryNameToCode.put("PANAMÁ", "PA");
        countryNameToCode.put("PANAMA", "PA");
        countryNameToCode.put("PARAGUAY", "PY");
        countryNameToCode.put("PERÚ", "PE");
        countryNameToCode.put("PERU", "PE");
        countryNameToCode.put("URUGUAY", "UY");
        countryNameToCode.put("VENEZUELA", "VE");

        String code = countryNameToCode.get(countryName.toUpperCase());
        if (code == null) {
            throw new IllegalArgumentException("País no soportado: " + countryName);
        }
        return code;
    }
}