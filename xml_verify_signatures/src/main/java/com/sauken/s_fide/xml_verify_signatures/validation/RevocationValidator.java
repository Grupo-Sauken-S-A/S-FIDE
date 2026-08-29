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

package com.sauken.s_fide.xml_verify_signatures.validation;

import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import com.sauken.s_fide.xml_verify_signatures.timestamp.SignatureTimeExtractor.SigningTimeResult;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.*;
import java.time.Duration;
import java.util.Date;

public class RevocationValidator {
    public enum RevocationStatus {
        GOOD, REVOKED, UNKNOWN
    }

    public static class RevocationResult {
        private final RevocationStatus status;
        private final String method;
        private final String errorMessage;
        private final boolean usedCurrentTime;
        private final Date revocationDate;
        private final Date validationDate;

        public RevocationResult(RevocationStatus status, String method, String errorMessage,
                                boolean usedCurrentTime, Date revocationDate, Date validationDate) {
            this.status = status;
            this.method = method;
            this.errorMessage = errorMessage;
            this.usedCurrentTime = usedCurrentTime;
            this.revocationDate = revocationDate;
            this.validationDate = validationDate;
        }

        public RevocationStatus getStatus() {
            return status;
        }

        public String getMethod() {
            return method;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isUsedCurrentTime() {
            return usedCurrentTime;
        }

        public Date getRevocationDate() {
            return revocationDate;
        }

        public Date getValidationDate() {
            return validationDate;
        }
    }

    private static String cleanUrl(String url) {
        return url.replaceAll("\\[CONTEXT \\d+\\]", "");
    }

    public static RevocationResult checkOCSPStatus(X509Certificate cert, String ocspUrl,
                                                   X509Certificate issuerCert, SigningTimeResult signingTimeResult) throws Exception {

        if (issuerCert == null) {
            return new RevocationResult(RevocationStatus.UNKNOWN, "OCSP",
                    "No se pudo obtener el certificado emisor", false, null, null);
        }

        String cleanUrl = cleanUrl(ocspUrl);

        try {
            OCSPReqBuilder ocspBuilder = new OCSPReqBuilder();
            var digCalcBuilder = new JcaDigestCalculatorProviderBuilder();
            var certId = new CertificateID(
                    digCalcBuilder.build().get(CertificateID.HASH_SHA1),
                    new JcaX509CertificateHolder(issuerCert),
                    cert.getSerialNumber());

            ocspBuilder.addRequest(certId);
            OCSPReq ocspReq = ocspBuilder.build();

            byte[] ocspRespData = sendOCSPRequest(cleanUrl, ocspReq.getEncoded());
            OCSPResp ocspResp = new OCSPResp(ocspRespData);

            if (ocspResp.getStatus() != OCSPRespBuilder.SUCCESSFUL) {
                return new RevocationResult(RevocationStatus.UNKNOWN, "OCSP",
                        "Respuesta OCSP no exitosa", false, null, signingTimeResult.getDate());
            }

            BasicOCSPResp basicResp = (BasicOCSPResp) ocspResp.getResponseObject();
            SingleResp[] responses = basicResp.getResponses();

            if (responses.length == 0) {
                return new RevocationResult(RevocationStatus.UNKNOWN, "OCSP",
                        "Sin respuestas OCSP", false, null, signingTimeResult.getDate());
            }

            SingleResp resp = responses[0];
            org.bouncycastle.cert.ocsp.CertificateStatus certStatus = resp.getCertStatus();

            Date signingTime = signingTimeResult.getDate();

            if (certStatus == null) {
                // Certificado no revocado
                return new RevocationResult(RevocationStatus.GOOD, "OCSP", null,
                        signingTimeResult.isUsingCurrentTime(), null, signingTime);
            } else if (certStatus instanceof RevokedStatus revokedStatus) {
                Date revocationTime = revokedStatus.getRevocationTime();

                // Si la firma se realizó antes de la revocación, es válida
                if (signingTime.before(revocationTime)) {
                    return new RevocationResult(RevocationStatus.GOOD, "OCSP",
                            "Certificado revocado posteriormente a la firma",
                            signingTimeResult.isUsingCurrentTime(),
                            revocationTime, signingTime);
                }

                // La firma se realizó en o después de la revocación
                String message = signingTimeResult.isUsingCurrentTime() ?
                        "Validación realizada con fecha actual debido a: " + signingTimeResult.getErrorMessage() :
                        "La firma se realizó cuando el certificado ya estaba revocado";

                return new RevocationResult(RevocationStatus.REVOKED, "OCSP", message,
                        signingTimeResult.isUsingCurrentTime(), revocationTime, signingTime);
            }

            return new RevocationResult(RevocationStatus.UNKNOWN, "OCSP", null,
                    signingTimeResult.isUsingCurrentTime(), null, signingTime);

        } catch (Exception e) {
            return new RevocationResult(RevocationStatus.UNKNOWN, "OCSP",
                    "Error en consulta OCSP: " + e.getMessage(), false, null, signingTimeResult.getDate());
        }
    }

    private static byte[] sendOCSPRequest(String ocspUrl, byte[] ocspReqData) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ocspUrl))
                .header("Content-Type", "application/ocsp-request")
                .header("Accept", "application/ocsp-response")
                .POST(HttpRequest.BodyPublishers.ofByteArray(ocspReqData))
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }

    public static RevocationResult checkCRLStatus(X509Certificate cert, String crlUrl,
                                                  SigningTimeResult signingTimeResult) throws Exception {

        String cleanUrl = cleanUrl(crlUrl);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream in = response.body()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509CRL crl = (X509CRL) cf.generateCRL(in);

                X509CRLEntry revokedCertEntry = crl.getRevokedCertificate(cert.getSerialNumber());
                Date signingTime = signingTimeResult.getDate();

                if (revokedCertEntry != null) {
                    Date revocationDate = revokedCertEntry.getRevocationDate();

                    // Si la firma se realizó antes de la revocación, es válida
                    if (signingTime.before(revocationDate)) {
                        return new RevocationResult(RevocationStatus.GOOD, "CRL",
                                "Certificado revocado posteriormente a la firma",
                                signingTimeResult.isUsingCurrentTime(),
                                revocationDate, signingTime);
                    }

                    // La firma se realizó en o después de la revocación
                    String message = signingTimeResult.isUsingCurrentTime() ?
                            "Validación realizada con fecha actual debido a: " + signingTimeResult.getErrorMessage() :
                            "La firma se realizó cuando el certificado ya estaba revocado";

                    return new RevocationResult(RevocationStatus.REVOKED, "CRL", message,
                            signingTimeResult.isUsingCurrentTime(), revocationDate, signingTime);
                }

                // Certificado no revocado
                return new RevocationResult(RevocationStatus.GOOD, "CRL", null,
                        signingTimeResult.isUsingCurrentTime(), null, signingTime);

            } catch (Exception e) {
                return new RevocationResult(RevocationStatus.UNKNOWN, "CRL",
                        "Error procesando CRL: " + e.getMessage(), false, null, signingTimeResult.getDate());
            }
        } catch (Exception e) {
            return new RevocationResult(RevocationStatus.UNKNOWN, "CRL",
                    "Error descargando CRL: " + e.getMessage(), false, null, signingTimeResult.getDate());
        }
    }
}