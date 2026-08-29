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

package com.sauken.s_fide.pdf_signer_pkcs11;

import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.ISignatureMechanismParams;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;

/**
 * Firma con un token PKCS#11, probando primero el mecanismo combinado
 * (CKM_SHA256_RSA_PKCS, hash calculado por el propio token) y, si el token no
 * lo soporta, recalculando el hash SHA-256 por software y armando el DigestInfo
 * ASN.1 para firmarlo con el mecanismo puro CKM_RSA_PKCS. Esto último es
 * necesario para tokens como el ePass2003 en modo FIPS 140-2 Nivel 3, que solo
 * exponen CKM_RSA_PKCS.
 */
public class Pkcs11ExternalSignature implements IExternalSignature {

    private final PrivateKey privateKey;
    private final Provider provider;
    private boolean lastSignUsedExternalHash;

    public Pkcs11ExternalSignature(PrivateKey privateKey, Provider provider) {
        this.privateKey = privateKey;
        this.provider = provider;
    }

    @Override
    public String getDigestAlgorithmName() {
        return "SHA256";
    }

    @Override
    public String getSignatureAlgorithmName() {
        return "RSA";
    }

    @Override
    public ISignatureMechanismParams getSignatureMechanismParameters() {
        return null;
    }

    /**
     * @return true si la última firma se realizó calculando el hash por
     * software (mecanismo externo, camino ePass2003); false si el token
     * aceptó el mecanismo combinado directamente (camino SafeNet).
     */
    public boolean lastSignUsedExternalHash() {
        return lastSignUsedExternalHash;
    }

    @Override
    public byte[] sign(byte[] message) throws GeneralSecurityException {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA", provider);
            signature.initSign(privateKey);
            signature.update(message);
            byte[] result = signature.sign();
            lastSignUsedExternalHash = false;
            return result;
        } catch (Exception combinedMechanismFailure) {
            return signWithExternalHash(message);
        }
    }

    private byte[] signWithExternalHash(byte[] message) throws GeneralSecurityException {
        try {
            byte[] digestInfo = buildDigestInfo(message);
            Signature signature = Signature.getInstance("NONEwithRSA", provider);
            signature.initSign(privateKey);
            signature.update(digestInfo);
            byte[] result = signature.sign();
            lastSignUsedExternalHash = true;
            return result;
        } catch (GeneralSecurityException externalHashFailure) {
            throw new GeneralSecurityException(
                    "El token no admite ningún mecanismo de firma RSA-SHA256 compatible (ni interno ni externo)",
                    externalHashFailure);
        }
    }

    private static byte[] buildDigestInfo(byte[] message) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(message);
        AlgorithmIdentifier algorithmIdentifier =
                new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
        DigestInfo digestInfo = new DigestInfo(algorithmIdentifier, digest);
        try {
            return digestInfo.getEncoded();
        } catch (IOException e) {
            throw new GeneralSecurityException("Error armando la estructura DigestInfo ASN.1", e);
        }
    }
}
