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

package com.sauken.s_fide.xml_signer_pkcs11;

import java.security.Provider;

/**
 * Provider mínimo que solo registra el algoritmo "SHA256withRSA" apuntando a
 * {@link Pkcs11FallbackSignature}. Se pasa a
 * {@code XMLSignatureFactory.getInstance("DOM", provider)} para que la
 * implementación JSR-105 use esta clase (con su lógica de mecanismo
 * combinado + fallback externo) al firmar, sin tener que reescribir el resto
 * del flujo de firma XML basado en {@code XMLSignature}/{@code DOMSignContext}.
 */
public final class Pkcs11FallbackProvider extends Provider {

    public Pkcs11FallbackProvider(Provider delegate) {
        super("SFideXMLDSigFallback", "1.0", "S-FIDE: firma PKCS#11 con fallback de hash externo");
        Pkcs11FallbackSignature.delegateProvider = delegate;
        putService(new Service(
                this,
                "Signature",
                "SHA256withRSA",
                Pkcs11FallbackSignature.class.getName(),
                null,
                null));
        putService(new Service(
                this,
                "MessageDigest",
                "SHA-256",
                Pkcs11FallbackDigest.class.getName(),
                null,
                null));
    }

    /**
     * @return true si la última firma realizada a través de este provider
     * usó el mecanismo de hash externo (camino ePass2003); false si el token
     * aceptó el mecanismo combinado directamente (camino SafeNet).
     */
    public boolean lastSignUsedExternalHash() {
        return Pkcs11FallbackSignature.lastSignUsedExternalHash;
    }
}
