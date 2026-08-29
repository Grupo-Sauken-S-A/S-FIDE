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

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;

/**
 * Delega el cálculo del digest SHA-256 (usado por JSR-105 para el hash de las
 * referencias XML, siempre en software, nunca en el token) a la
 * implementación estándar del JDK. Existe solo para que
 * {@link Pkcs11FallbackProvider} pueda publicar el servicio "MessageDigest"
 * que la implementación JSR-105 de firma XML pide al provider indicado en
 * {@code XMLSignatureFactory.getInstance("DOM", provider)}, además del
 * servicio "Signature".
 */
public final class Pkcs11FallbackDigest extends MessageDigestSpi {

    private final MessageDigest delegate;

    public Pkcs11FallbackDigest() {
        // Pide el SHA-256 explícitamente al provider "SUN" del JDK, nunca sin indicar
        // provider: como este provider se registra con prioridad 1, MessageDigest.getInstance
        // ("SHA-256") sin provider se resolvería contra sí mismo y entraría en recursión
        // infinita (StackOverflowError) al construir cada nueva instancia.
        try {
            delegate = MessageDigest.getInstance("SHA-256", "SUN");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible en el provider SUN del JDK", e);
        }
    }

    @Override
    protected void engineUpdate(byte input) {
        delegate.update(input);
    }

    @Override
    protected void engineUpdate(byte[] input, int offset, int len) {
        delegate.update(input, offset, len);
    }

    @Override
    protected byte[] engineDigest() {
        return delegate.digest();
    }

    @Override
    protected void engineReset() {
        delegate.reset();
    }

    @Override
    protected int engineGetDigestLength() {
        return delegate.getDigestLength();
    }
}
