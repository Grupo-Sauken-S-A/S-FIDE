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


package com.sauken.s_fide.integration_tests;

import com.itextpdf.signatures.ITSAClient;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

/**
 * Autoridad de sellado de tiempo local, solo para pruebas: arma un sello RFC 3161 real con
 * BouncyCastle y un certificado propio, sin ninguna conexión a Internet.
 */
final class TsaLocal implements ITSAClient {

    private final PrivateKey clave;
    private final X509Certificate certificado;

    TsaLocal(PrivateKey clave, X509Certificate certificado) {
        this.clave = clave;
        this.certificado = certificado;
    }

    @Override
    public int getTokenSizeEstimate() {
        return 8192;
    }

    @Override
    public MessageDigest getMessageDigest() throws java.security.GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256");
    }

    @Override
    public byte[] getTimeStampToken(byte[] imprint) throws Exception {
        SignerInfoGenerator firmante = new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC")
                .build("SHA256withRSA", clave, certificado);
        DigestCalculator calculador = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
                .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
        TimeStampTokenGenerator generador = new TimeStampTokenGenerator(firmante, calculador,
                new ASN1ObjectIdentifier("1.2.3.4.5"));
        generador.addCertificates(new JcaCertStore(List.of(certificado)));

        TimeStampRequestGenerator pedido = new TimeStampRequestGenerator();
        pedido.setCertReq(true);
        TimeStampRequest solicitud = pedido.generate(TSPAlgorithms.SHA256, imprint,
                BigInteger.valueOf(System.nanoTime()));
        return generador.generate(solicitud, BigInteger.ONE, new Date()).getEncoded();
    }
}
