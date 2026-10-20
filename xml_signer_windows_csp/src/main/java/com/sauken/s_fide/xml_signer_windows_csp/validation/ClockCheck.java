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

package com.sauken.s_fide.xml_signer_windows_csp.validation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compara el reloj de esta computadora con la hora real, tomada de la
 * cabecera {@code Date} de una respuesta HTTPS, y avisa si la diferencia es
 * grande. NUNCA impide firmar: es solo una advertencia.
 * <p>
 * POR QUÉ IMPORTA: la vigencia del certificado y la fecha de la firma se
 * calculan con el reloj de la computadora. Con el reloj atrasado se puede
 * firmar con un certificado ya vencido, o rechazar uno vigente; con el reloj
 * adelantado la firma queda con una fecha que no es la real.
 * <p>
 * Si no hay conexión (o ningún servidor responde con una fecha), no se dice
 * nada: la ausencia de Internet ya la informan las otras comprobaciones y no
 * es un problema del reloj. Esta clase se duplica, idéntica salvo la línea
 * {@code package}, en los seis módulos que firman (cada jar debe seguir
 * siendo independiente).
 */
public final class ClockCheck {

    /** A partir de esta diferencia se avisa. Las cabeceras Date tienen resolución de un segundo. */
    static final Duration UMBRAL = Duration.ofMinutes(5);

    private static final List<String> SERVIDORES = List.of(
            "https://www.google.com", "https://www.cloudflare.com");
    /** Sin conexión útil, la comprobación no debe demorar la firma más de unos pocos segundos (2 servidores x 2 s). */
    private static final Duration TIEMPO_DE_ESPERA = Duration.ofSeconds(2);
    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private ClockCheck() {
    }

    /** Consulta la hora real y, si el reloj está desfasado, entrega un aviso ya redactado. */
    public static void avisar(Consumer<String> salida) {
        String aviso = verificar();
        if (aviso != null) {
            salida.accept(aviso);
        }
    }

    /** @return el aviso, o {@code null} si el reloj está bien o no se pudo comprobar */
    static String verificar() {
        try {
            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(TIEMPO_DE_ESPERA)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            for (String servidor : SERVIDORES) {
                Instant antes = Instant.now();
                try {
                    HttpResponse<Void> respuesta = cliente.send(
                            HttpRequest.newBuilder(URI.create(servidor))
                                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                    .timeout(TIEMPO_DE_ESPERA)
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
                    Instant despues = Instant.now();
                    String fecha = respuesta.headers().firstValue("Date").orElse(null);
                    if (fecha == null) {
                        continue;
                    }
                    Instant referencia = ZonedDateTime.parse(fecha, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    // La hora local de referencia es el punto medio de la consulta, para no culpar al reloj por la demora de la red.
                    Instant local = antes.plus(Duration.between(antes, despues).dividedBy(2));
                    return evaluar(local, referencia);
                } catch (Exception e) {
                    // Este servidor no sirvió; se prueba el siguiente.
                }
            }
        } catch (Exception e) {
            // Sin conexión o sin cliente HTTP: no es un problema del reloj.
        }
        return null;
    }

    /** Decide si la diferencia amerita un aviso. Separado para poder probarlo sin red. */
    public static String evaluar(Instant local, Instant referencia) {
        Duration diferencia = Duration.between(referencia, local);
        if (diferencia.abs().compareTo(UMBRAL) < 0) {
            return null;
        }
        String sentido = diferencia.isNegative() ? "atrasado" : "adelantado";
        return "ADVERTENCIA: el reloj de esta computadora está " + sentido + " unos " + describir(diferencia.abs())
                + " respecto de la hora real de Internet (computadora: " + FORMATO.format(local)
                + "; hora real: " + FORMATO.format(referencia) + "). La vigencia del certificado y la fecha de la "
                + "firma se calculan con ese reloj: conviene corregirlo antes de firmar. Se continúa con la firma.";
    }

    private static String describir(Duration d) {
        long dias = d.toDays();
        if (dias >= 2) {
            return dias + " días";
        }
        long horas = d.toHours();
        if (horas >= 2) {
            return horas + " horas";
        }
        return d.toMinutes() + " minutos";
    }
}
