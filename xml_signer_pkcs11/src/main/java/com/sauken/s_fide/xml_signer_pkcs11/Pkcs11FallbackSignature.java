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

import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.AlgorithmParameterSpec;

/**
 * SignatureSpi que prueba primero el mecanismo combinado CKM_SHA256_RSA_PKCS
 * (hash calculado por el token, delegando en el SunPKCS11 real) y, si el
 * token no lo soporta, recalcula el hash SHA-256 por software y firma el
 * DigestInfo ASN.1 resultante con el mecanismo puro CKM_RSA_PKCS. Necesario
 * para tokens como el ePass2003 en modo FIPS 140-2 Nivel 3, que solo exponen
 * CKM_RSA_PKCS.
 *
 * Se registra bajo el nombre "SHA256withRSA" en {@link Pkcs11FallbackProvider}
 * para que la implementación JSR-105 de firma XML (invocada con ese
 * {@link Provider} desde {@code XMLSignatureFactory.getInstance("DOM", provider)})
 * la use de forma transparente sin cambiar el resto del flujo de firma XML.
 */
public final class Pkcs11FallbackSignature extends SignatureSpi {

    static volatile Provider delegateProvider;
    static volatile boolean lastSignUsedExternalHash;

    private PrivateKey privateKey;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    protected void engineInitVerify(PublicKey publicKey) {
        throw new UnsupportedOperationException(
                "Pkcs11FallbackSignature solo se usa para firmar, no para verificar");
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) {
        this.privateKey = privateKey;
        buffer.reset();
    }

    @Override
    protected void engineUpdate(byte b) {
        buffer.write(b);
    }

    @Override
    protected void engineUpdate(byte[] b, int off, int len) {
        buffer.write(b, off, len);
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        byte[] message = buffer.toByteArray();
        Provider provider = delegateProvider;
        if (provider == null) {
            throw new SignatureException("Provider PKCS#11 delegado no configurado");
        }

        try {
            Signature real = Signature.getInstance("SHA256withRSA", provider);
            real.initSign(privateKey);
            real.update(message);
            byte[] result = real.sign();
            lastSignUsedExternalHash = false;
            return result;
        } catch (Exception combinedMechanismFailure) {
            return signWithExternalHash(message, provider);
        }
    }

    private byte[] signWithExternalHash(byte[] message, Provider provider) throws SignatureException {
        try {
            byte[] digestInfo = buildDigestInfo(message);
            Signature real = Signature.getInstance("NONEwithRSA", provider);
            real.initSign(privateKey);
            real.update(digestInfo);
            byte[] result = real.sign();
            lastSignUsedExternalHash = true;
            return result;
        } catch (Exception externalHashFailure) {
            throw new SignatureException(
                    "El token no admite ningún mecanismo de firma RSA-SHA256 compatible (ni interno ni externo)",
                    externalHashFailure);
        }
    }

    private static byte[] buildDigestInfo(byte[] message) throws SignatureException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(message);
            AlgorithmIdentifier algorithmIdentifier =
                    new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
            DigestInfo digestInfo = new DigestInfo(algorithmIdentifier, digest);
            return digestInfo.getEncoded();
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new SignatureException("Error armando la estructura DigestInfo ASN.1", e);
        }
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) {
        throw new UnsupportedOperationException(
                "Pkcs11FallbackSignature solo se usa para firmar, no para verificar");
    }

    @Override
    protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
        throw new InvalidParameterException("Parámetros no soportados");
    }

    @Override
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new InvalidParameterException("Parámetros no soportados");
    }
}
