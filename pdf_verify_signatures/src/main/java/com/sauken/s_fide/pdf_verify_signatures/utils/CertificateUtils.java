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

package com.sauken.s_fide.pdf_verify_signatures.utils;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.logging.*;

public class CertificateUtils {
    private static final Logger LOGGER = Logger.getLogger(CertificateUtils.class.getName());

    public static List<String> getOCSPUrls(X509Certificate cert) throws IOException {
        List<String> ocspUrls = new ArrayList<>();
        try {
            byte[] authInfoAccess = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (authInfoAccess != null) {
                ASN1Sequence asn1Seq = getASN1Sequence(authInfoAccess);
                if (asn1Seq != null) {
                    for (ASN1Encodable obj : asn1Seq) {
                        ASN1Sequence accessDesc = (ASN1Sequence) obj;
                        ASN1ObjectIdentifier accessMethod = (ASN1ObjectIdentifier) accessDesc.getObjectAt(0);

                        if (accessMethod.getId().equals("1.3.6.1.5.5.7.48.1")) {
                            GeneralName gn = GeneralName.getInstance(accessDesc.getObjectAt(1));
                            if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                                String url = extractString(gn);
                                if (url != null && url.startsWith("http")) {
                                    ocspUrls.add(url);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extrayendo URLs OCSP: " + e.getMessage());
        }
        return ocspUrls;
    }

    public static List<String> getCRLUrls(X509Certificate cert) throws IOException {
        List<String> crlUrls = new ArrayList<>();
        try {
            byte[] crlDist = cert.getExtensionValue("2.5.29.31");
            if (crlDist != null) {
                ASN1Sequence asn1Seq = getASN1Sequence(crlDist);
                if (asn1Seq != null) {
                    for (ASN1Encodable obj : asn1Seq) {
                        DistributionPoint dp = DistributionPoint.getInstance(obj);
                        DistributionPointName dpn = dp.getDistributionPoint();

                        if (dpn != null && dpn.getType() == DistributionPointName.FULL_NAME) {
                            GeneralNames gns = (GeneralNames) dpn.getName();
                            for (GeneralName gn : gns.getNames()) {
                                if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                                    String url = extractString(gn);
                                    if (url != null && url.startsWith("http")) {
                                        crlUrls.add(url);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extrayendo URLs CRL: " + e.getMessage());
        }
        return crlUrls;
    }

    private static ASN1Sequence getASN1Sequence(byte[] data) {
        try (ASN1InputStream ais = new ASN1InputStream(data)) {
            ASN1OctetString oct = (ASN1OctetString) ais.readObject();
            try (ASN1InputStream ais2 = new ASN1InputStream(oct.getOctets())) {
                return (ASN1Sequence) ais2.readObject();
            }
        } catch (Exception e) {
            LOGGER.warning("Error parseando secuencia ASN1: " + e.getMessage());
            return null;
        }
    }

    private static String extractString(GeneralName gn) {
        try {
            ASN1Encodable name = gn.getName();
            if (name instanceof DERIA5String) {
                return ((DERIA5String) name).getString();
            } else if (name instanceof ASN1OctetString) {
                return new String(((ASN1OctetString) name).getOctets());
            }
            return name.toString();
        } catch (Exception e) {
            LOGGER.warning("Error extrayendo string de GeneralName: " + e.getMessage());
            return null;
        }
    }
}