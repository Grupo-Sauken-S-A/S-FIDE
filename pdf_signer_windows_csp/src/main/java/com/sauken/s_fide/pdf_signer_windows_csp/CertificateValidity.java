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

package com.sauken.s_fide.pdf_signer_windows_csp;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
     * Comprobación completa previa a firmar: vigencia y, además, que el
     * certificado esté habilitado para firmar documentos. Es lo que llaman
     * los firmadores; {@link #exigirVigente} queda para quien solo necesite
     * la vigencia.
     *
     * @throws IllegalArgumentException con un mensaje listo para mostrar
     */
    static void exigirApto(X509Certificate cert) {
        exigirVigente(cert);
        exigirUsoDeFirma(cert);
    }

    /**
     * Un certificado emitido solo para cifrar, o el de una autoridad
     * certificante, no sirve para firmar un documento: el programa que lo
     * verifique lo va a rechazar. Si el certificado no declara su uso
     * (extensión ausente) se acepta, como hacen los verificadores.
     */
    static void exigirUsoDeFirma(X509Certificate cert) {
        if (cert.getBasicConstraints() >= 0) {
            throw new IllegalArgumentException("El certificado seleccionado es el de una autoridad certificante, "
                    + "no el de una persona u organización: no sirve para firmar documentos. Elija su certificado "
                    + "personal.");
        }
        boolean[] uso = cert.getKeyUsage();
        if (uso == null) {
            return;
        }
        boolean firma = uso.length > 0 && uso[0];
        boolean noRepudio = uso.length > 1 && uso[1];
        if (!firma && !noRepudio) {
            throw new IllegalArgumentException("El certificado seleccionado no está habilitado para firmar "
                    + "documentos (fue emitido para otro uso, por ejemplo cifrar). Elija otro certificado.");
        }
    }

    /**
     * Avisos que no impiden firmar: un certificado de la cadena (autoridad
     * certificante) vencido o aún no vigente, una clave RSA corta o un
     * algoritmo de firma débil. La cadena es la que informa el token, el
     * archivo o el almacén de Windows; si trae solo el certificado de la
     * persona, no hay nada más que comprobar acá.
     */
    static List<String> advertencias(Certificate[] cadena) {
        List<String> avisos = new ArrayList<>();
        if (cadena == null || cadena.length == 0 || !(cadena[0] instanceof X509Certificate propio)) {
            return avisos;
        }
        for (int i = 1; i < cadena.length; i++) {
            if (cadena[i] instanceof X509Certificate ac) {
                switch (estado(ac)) {
                    case VENCIDO -> avisos.add("el certificado de la autoridad certificante «" + nombre(ac)
                            + "» venció el " + fecha(ac.getNotAfter())
                            + "; las firmas hechas con esta cadena pueden no ser aceptadas");
                    case AUN_NO_VIGENTE -> avisos.add("el certificado de la autoridad certificante «" + nombre(ac)
                            + "» todavía no es válido (empieza el " + fecha(ac.getNotBefore()) + ")");
                    case VIGENTE -> {
                    }
                }
            }
        }
        if (propio.getPublicKey() instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() < 2048) {
            avisos.add("la clave del certificado es RSA de " + rsa.getModulus().bitLength()
                    + " bits, menor que los 2048 que hoy se consideran seguros");
        }
        String algoritmo = propio.getSigAlgName().toUpperCase(Locale.ROOT);
        if (algoritmo.startsWith("MD5") || algoritmo.startsWith("MD2")) {
            avisos.add("el certificado fue emitido con un algoritmo débil (" + propio.getSigAlgName() + ")");
        }
        return avisos;
    }

    /** Entrega cada aviso de {@link #advertencias} a quien firma, ya redactado como advertencia. */
    static void avisar(Certificate[] cadena, Consumer<String> salida) {
        for (String aviso : advertencias(cadena)) {
            salida.accept("ADVERTENCIA: " + aviso + ". Se continúa con la firma.");
        }
    }

    private static String nombre(X509Certificate cert) {
        String rfc = cert.getSubjectX500Principal().getName();
        Matcher m = Pattern.compile("(?:^|,)CN=((?:\\\\.|[^,\\\\])*)").matcher(rfc);
        return m.find() ? m.group(1).replace("\\", "") : rfc;
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
