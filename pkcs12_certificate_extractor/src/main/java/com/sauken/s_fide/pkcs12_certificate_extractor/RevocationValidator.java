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

package com.sauken.s_fide.pkcs12_certificate_extractor;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Consulta el estado de revocación de un certificado (OCSP primero, CRL como
 * respaldo si OCSP no da una respuesta concluyente) — el mismo mecanismo y
 * el mismo orden que usan los firmadores (ANTES de firmar) y los
 * verificadores (sobre una firma ya existente). Acá se usa solo para
 * INFORMAR el estado del certificado que se está extrayendo: el resultado
 * nunca cambia el código de salida del programa.
 * <p>
 * Esta clase se duplica en cada módulo que la necesita (misma política de
 * independencia de jars del resto del proyecto); a diferencia de la de los
 * firmadores, además de la lista de certificados confiables de Java, busca
 * el certificado emisor entre los que se le pasan ({@code posiblesEmisores}),
 * necesario para armar la consulta OCSP con autoridades certificantes que
 * Java no trae de fábrica.
 */
public class RevocationValidator {

    public enum RevocationStatus {
        GOOD, REVOKED, UNKNOWN
    }

    public static class Resultado {
        private final RevocationStatus estado;
        private final String metodo;
        private final String detalle;

        public Resultado(RevocationStatus estado, String metodo, String detalle) {
            this.estado = estado;
            this.metodo = metodo;
            this.detalle = detalle;
        }

        public RevocationStatus getEstado() {
            return estado;
        }

        public String getMetodo() {
            return metodo;
        }

        public String getDetalle() {
            return detalle;
        }
    }

    private static final String TRUST_STORE_PATH = System.getProperty("java.home")
            + File.separator + "lib" + File.separator + "security" + File.separator + "cacerts";
    private static final String TRUST_STORE_PASSWORD = "changeit";

    public static Resultado validar(X509Certificate cert, List<X509Certificate> posiblesEmisores) {
        try {
            if (!isInternetAvailable()) {
                return new Resultado(RevocationStatus.UNKNOWN, null, "sin conexión a Internet");
            }

            List<String> ocspUrls = getOCSPUrls(cert);
            List<String> crlUrls = getCRLUrls(cert);

            if (ocspUrls.isEmpty() && crlUrls.isEmpty()) {
                return new Resultado(RevocationStatus.UNKNOWN, null,
                        "el certificado no publica una URL de OCSP ni de CRL");
            }

            X509Certificate issuerCert = ocspUrls.isEmpty() ? null : getIssuerCertificate(cert, posiblesEmisores);

            for (String ocspUrl : ocspUrls) {
                Resultado r = checkOCSP(cert, issuerCert, ocspUrl);
                if (r.getEstado() != RevocationStatus.UNKNOWN) {
                    return r;
                }
            }

            for (String crlUrl : crlUrls) {
                Resultado r = checkCRL(cert, crlUrl);
                if (r.getEstado() != RevocationStatus.UNKNOWN) {
                    return r;
                }
            }

            return new Resultado(RevocationStatus.UNKNOWN, null,
                    "no se pudo obtener una respuesta concluyente de OCSP ni de CRL");
        } catch (Exception e) {
            return new Resultado(RevocationStatus.UNKNOWN, null, "error al validar: " + e.getMessage());
        }
    }

    private static boolean isInternetAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String extractString(GeneralName gn) {
        ASN1Primitive primitive = gn.toASN1Primitive();
        if (primitive instanceof ASN1OctetString octetString) {
            return new String(octetString.getOctets());
        }
        return primitive.toString();
    }

    private static List<String> getOCSPUrls(X509Certificate cert) throws IOException {
        List<String> urls = new ArrayList<>();
        byte[] authInfoAccess = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
        if (authInfoAccess == null) {
            return urls;
        }
        try (ASN1InputStream ais = new ASN1InputStream(authInfoAccess)) {
            ASN1OctetString aios = (ASN1OctetString) ais.readObject();
            try (ASN1InputStream aos = new ASN1InputStream(aios.getOctets())) {
                AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(aos.readObject());
                for (AccessDescription ad : aia.getAccessDescriptions()) {
                    if (ad.getAccessMethod().equals(AccessDescription.id_ad_ocsp)) {
                        GeneralName gn = ad.getAccessLocation();
                        if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                            urls.add(extractString(gn));
                        }
                    }
                }
            }
        }
        return urls;
    }

    private static List<String> getCRLUrls(X509Certificate cert) throws IOException {
        List<String> urls = new ArrayList<>();
        byte[] crlDist = cert.getExtensionValue("2.5.29.31");
        if (crlDist == null) {
            return urls;
        }
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
                                urls.add(extractString(gn));
                            }
                        }
                    }
                }
            }
        }
        return urls;
    }

    private static X509Certificate getIssuerCertificate(X509Certificate cert, List<X509Certificate> posiblesEmisores) {
        // Primero entre los certificados que ya se tienen a mano (los demás del mismo token/archivo
        // PKCS#12, donde suele estar la propia AC emisora): la firma del certificado debe verificar
        // con la clave pública candidata, no alcanza con que coincida el nombre.
        if (posiblesEmisores != null) {
            for (X509Certificate candidato : posiblesEmisores) {
                if (candidato == null || candidato.equals(cert)
                        || !candidato.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                    continue;
                }
                try {
                    cert.verify(candidato.getPublicKey());
                    return candidato;
                } catch (Exception e) {
                    // No es el emisor real (mismo nombre, otra clave): se sigue buscando.
                }
            }
        }
        try {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(TRUST_STORE_PATH)) {
                trustStore.load(fis, TRUST_STORE_PASSWORD.toCharArray());
            }
            String issuerDN = cert.getIssuerX500Principal().getName();
            Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                X509Certificate storeCert = (X509Certificate) trustStore.getCertificate(aliases.nextElement());
                if (storeCert != null && storeCert.getSubjectX500Principal().getName().equals(issuerDN)) {
                    return storeCert;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // Algunos certificados reales (confirmado con AC-ONTI/ePass2004 contra
    // hardware real) traen la URL de OCSP/CRL con un prefijo "[CONTEXT N]"
    // que queda pegado al extraer el GeneralName vía ASN1OctetString (una
    // particularidad de cómo BouncyCastle expone ciertas codificaciones
    // ASN.1 con tag implícito) — sin este saneo, URI.create()/HttpRequest
    // fallan con "Illegal character in scheme name" y la consulta cae
    // siempre a UNKNOWN, aunque el servidor de OCSP/CRL esté perfectamente
    // operativo. XMLVerifySignatures/PDFVerifySignatures ya lo hacían así;
    // se había perdido al portar esta clase para la validación antes de
    // firmar.
    private static String cleanUrl(String url) {
        return url.replaceAll("\\[CONTEXT \\d+\\]", "");
    }

    private static Resultado checkOCSP(X509Certificate cert, X509Certificate issuerCert, String ocspUrl) {
        if (issuerCert == null) {
            return new Resultado(RevocationStatus.UNKNOWN, "OCSP", "no se pudo obtener el certificado emisor");
        }
        try {
            OCSPReqBuilder ocspBuilder = new OCSPReqBuilder();
            var digCalcBuilder = new JcaDigestCalculatorProviderBuilder();
            var certId = new CertificateID(
                    digCalcBuilder.build().get(CertificateID.HASH_SHA1),
                    new JcaX509CertificateHolder(issuerCert),
                    cert.getSerialNumber());
            ocspBuilder.addRequest(certId);
            OCSPReq ocspReq = ocspBuilder.build();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl(ocspUrl)))
                    .header("Content-Type", "application/ocsp-request")
                    .header("Accept", "application/ocsp-response")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(ocspReq.getEncoded()))
                    .build();
            HttpResponse<byte[]> httpResp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            OCSPResp ocspResp = new OCSPResp(httpResp.body());
            if (ocspResp.getStatus() != OCSPRespBuilder.SUCCESSFUL) {
                return new Resultado(RevocationStatus.UNKNOWN, "OCSP", "respuesta OCSP no exitosa");
            }

            BasicOCSPResp basicResp = (BasicOCSPResp) ocspResp.getResponseObject();
            SingleResp[] responses = basicResp.getResponses();
            if (responses.length == 0) {
                return new Resultado(RevocationStatus.UNKNOWN, "OCSP", "sin respuestas OCSP");
            }

            CertificateStatus certStatus = responses[0].getCertStatus();
            if (certStatus == null) {
                return new Resultado(RevocationStatus.GOOD, "OCSP", null);
            } else if (certStatus instanceof RevokedStatus) {
                return new Resultado(RevocationStatus.REVOKED, "OCSP", "el certificado está revocado");
            }
            return new Resultado(RevocationStatus.UNKNOWN, "OCSP", null);
        } catch (Exception e) {
            return new Resultado(RevocationStatus.UNKNOWN, "OCSP", "error en la consulta OCSP: " + e.getMessage());
        }
    }

    private static Resultado checkCRL(X509Certificate cert, String crlUrl) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(cleanUrl(crlUrl))).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream in = response.body()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509CRL crl = (X509CRL) cf.generateCRL(in);
                X509CRLEntry entry = crl.getRevokedCertificate(cert.getSerialNumber());
                if (entry != null) {
                    return new Resultado(RevocationStatus.REVOKED, "CRL", "el certificado está revocado");
                }
                return new Resultado(RevocationStatus.GOOD, "CRL", null);
            } catch (Exception e) {
                return new Resultado(RevocationStatus.UNKNOWN, "CRL", "error al procesar la CRL: " + e.getMessage());
            }
        } catch (Exception e) {
            return new Resultado(RevocationStatus.UNKNOWN, "CRL", "error al descargar la CRL: " + e.getMessage());
        }
    }
}
