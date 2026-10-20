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

package com.sauken.s_fide.xml_signer_pkcs12;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Enumeration;

/**
 * Vigencia (fechas de validez) de un certificado, comprobada ANTES de usarlo
 * para firmar. Es una comprobación distinta de la revocación: un certificado
 * puede no estar revocado y, aun así, estar vencido (o no haber empezado a
 * valer) y entonces no sirve para firmar. Se compara contra el reloj de esta
 * computadora, igual que hace el resto de las comprobaciones de la suite.
 * <p>
 * Esta clase se duplica, idéntica salvo la línea {@code package}, en los diez
 * módulos que muestran o usan certificados (cada jar debe seguir siendo
 * independiente): los seis firmadores, {@code TokenSlotsView},
 * {@code TokenCertificateExtractor}, {@code PKCS12CertificateExtractor} y
 * {@code WindowsCertificateStoreView}. Si se modifica una, hay que
 * actualizar las diez.
 */
final class CertificateValidity {

    enum Estado {
        VIGENTE, VENCIDO, AUN_NO_VIGENTE
    }

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private CertificateValidity() {
    }

    static Estado estado(X509Certificate cert) {
        return estado(cert, new Date());
    }

    static Estado estado(X509Certificate cert, Date ahora) {
        if (ahora.before(cert.getNotBefore())) {
            return Estado.AUN_NO_VIGENTE;
        }
        if (ahora.after(cert.getNotAfter())) {
            return Estado.VENCIDO;
        }
        return Estado.VIGENTE;
    }

    /**
     * Línea para los listados: "VIGENTE (vence el ...)", "VENCIDO (venció el
     * ...)" o "AÚN NO VIGENTE (empieza a valer el ...)".
     */
    static String describir(X509Certificate cert) {
        return switch (estado(cert)) {
            case VIGENTE -> "VIGENTE (vence el " + fecha(cert.getNotAfter()) + ")";
            case VENCIDO -> "VENCIDO - no sirve para firmar (venció el " + fecha(cert.getNotAfter()) + ")";
            case AUN_NO_VIGENTE -> "AÚN NO VIGENTE - no sirve para firmar (empieza a valer el "
                    + fecha(cert.getNotBefore()) + ")";
        };
    }

    /**
     * Corta la firma si el certificado no está vigente. No hay forma de
     * omitir esta comprobación: a diferencia de un certificado revocado, uno
     * vencido nunca produce una firma que se pueda dar por buena.
     *
     * @throws IllegalArgumentException con un mensaje listo para mostrar
     */
    static void exigirVigente(X509Certificate cert) {
        switch (estado(cert)) {
            case VENCIDO -> throw new IllegalArgumentException("El certificado de firma venció el "
                    + fecha(cert.getNotAfter()) + ". No se puede firmar con un certificado vencido: "
                    + "renueve el certificado o use otro que esté vigente.");
            case AUN_NO_VIGENTE -> throw new IllegalArgumentException("El certificado de firma todavía no es "
                    + "válido: empieza a valer el " + fecha(cert.getNotBefore()) + ". No se puede firmar "
                    + "con él hasta esa fecha; revise también que la fecha de esta computadora sea correcta.");
            case VIGENTE -> {
            }
        }
    }

    /**
     * Elige con qué entrada del almacén firmar: la primera que tenga clave
     * privada y un certificado vigente. Un token o un archivo que conserva el
     * certificado anterior (vencido) junto con el renovado no debe obligar a
     * firmar con el viejo. Si ninguna está vigente devuelve la primera entrada
     * con clave, para que {@link #exigirVigente} informe el motivo real.
     */
    static String elegirAlias(KeyStore keyStore) throws KeyStoreException {
        String primeraConClave = null;
        String primera = null;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (primera == null) {
                primera = alias;
            }
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            if (primeraConClave == null) {
                primeraConClave = alias;
            }
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate x509 && estado(x509) == Estado.VIGENTE) {
                return alias;
            }
        }
        if (primeraConClave != null) {
            return primeraConClave;
        }
        if (primera == null) {
            throw new KeyStoreException("El almacén no contiene ningún certificado.");
        }
        return primera;
    }

    private static String fecha(Date fecha) {
        return FORMATO.format(fecha.toInstant());
    }
}
