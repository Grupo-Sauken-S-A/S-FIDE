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

package com.sauken.s_fide.xml_verify_signatures.utils;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class CertificateUtils {
    private static String extractString(GeneralName gn) {
        try {
            ASN1Primitive primitive = gn.toASN1Primitive();
            if (primitive instanceof ASN1OctetString) {
                return new String(((ASN1OctetString) primitive).getOctets());
            }
            return primitive.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error extracting string from GeneralName: " + e.getMessage());
        }
    }

    public static List<String> getOCSPUrls(X509Certificate cert) throws IOException {
        List<String> ocspUrls = new ArrayList<>();
        byte[] authInfoAccess = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");

        if (authInfoAccess != null) {
            try (ASN1InputStream ais = new ASN1InputStream(authInfoAccess)) {
                ASN1OctetString aios = (ASN1OctetString) ais.readObject();
                try (ASN1InputStream aos = new ASN1InputStream(aios.getOctets())) {
                    AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(aos.readObject());

                    for (AccessDescription ad : aia.getAccessDescriptions()) {
                        if (ad.getAccessMethod().equals(AccessDescription.id_ad_ocsp)) {
                            GeneralName gn = ad.getAccessLocation();
                            if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                                ocspUrls.add(extractString(gn));
                            }
                        }
                    }
                }
            }
        }
        return ocspUrls;
    }

    public static List<String> getCRLUrls(X509Certificate cert) throws IOException {
        List<String> crlUrls = new ArrayList<>();
        byte[] crlDist = cert.getExtensionValue("2.5.29.31");

        if (crlDist != null) {
            try (ASN1InputStream ais = new ASN1InputStream(crlDist)) {
                ASN1OctetString octs = (ASN1OctetString) ais.readObject();
                try (ASN1InputStream dis = new ASN1InputStream(octs.getOctets())) {
                    CRLDistPoint distPoint = CRLDistPoint.getInstance(dis.readObject());

                    for (DistributionPoint dp : distPoint.getDistributionPoints()) {
                        DistributionPointName dpn = dp.getDistributionPoint();
                        if (dpn != null && dpn.getType() == DistributionPointName.FULL_NAME) {
                            GeneralNames gns = (GeneralNames) dpn.getName();
                            for (GeneralName gn : gns.getNames()) {
                                if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                                    crlUrls.add(extractString(gn));
                                }
                            }
                        }
                    }
                }
            }
        }
        return crlUrls;
    }

    public static X509Certificate getIssuerCertificate(X509Certificate cert, String trustStorePath, String trustStorePassword) {
        try {
            java.security.KeyStore trustStore = java.security.KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(trustStorePath)) {
                trustStore.load(fis, trustStorePassword.toCharArray());

                String issuerDN = cert.getIssuerX500Principal().getName();
                System.out.println("Buscando emisor con DN: " + issuerDN);

                Enumeration<String> aliases = trustStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    X509Certificate storeCert = (X509Certificate) trustStore.getCertificate(alias);
                    String subjectDN = storeCert.getSubjectX500Principal().getName();
                    // System.out.println("Verificando certificado con Subject DN: " + subjectDN);
                    if (subjectDN.equals(issuerDN)) {
                        return storeCert;
                    }
                }
                System.out.println("No se encontró el certificado emisor en el truststore (java/lib/security/cacerts)");
            }
        } catch (Exception e) {
            System.err.println("Error al obtener el certificado emisor: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}