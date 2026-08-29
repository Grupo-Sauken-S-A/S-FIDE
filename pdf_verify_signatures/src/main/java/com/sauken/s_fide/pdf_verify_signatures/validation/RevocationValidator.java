package com.sauken.s_fide.pdf_verify_signatures.validation;

import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

public class RevocationValidator {
    private static final Logger LOGGER = Logger.getLogger(RevocationValidator.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public enum RevocationStatus {
        GOOD, REVOKED, UNKNOWN
    }

    public static RevocationStatus checkCertificateRevocation(X509Certificate cert, Date validationDate) {
        try {
            // Intentar OCSP primero
            RevocationStatus status = checkOCSP(cert, validationDate);
            if (status != RevocationStatus.UNKNOWN) {
                return status;
            }

            // Si OCSP falla, intentar CRL
            return checkCRL(cert, validationDate);

        } catch (Exception e) {
            LOGGER.warning("Error verificando revocación: " + e.getMessage());
            return RevocationStatus.UNKNOWN;
        }
    }

    private static RevocationStatus checkOCSP(X509Certificate cert, Date validationDate) {
        try {
            byte[] response = null;
            List<String> ocspUrls = com.sauken.s_fide.pdf_verify_signatures.utils.CertificateUtils.getOCSPUrls(cert);

            for (String url : ocspUrls) {
                try {
                    HttpResponse<byte[]> httpResponse = NetworkUtils.sendRequest(
                            url,
                            generateBasicOCSPRequest(cert),
                            "application/ocsp-request"
                    );

                    if (httpResponse.statusCode() == 200) {
                        response = httpResponse.body();
                        break;
                    }
                } catch (Exception e) {
                    LOGGER.fine("Error OCSP para " + url + ": " + e.getMessage());
                }
            }

            if (response != null) {
                OCSPResp ocspResp = new OCSPResp(response);
                if (ocspResp.getStatus() == OCSPRespBuilder.SUCCESSFUL) {
                    BasicOCSPResp basicResp = (BasicOCSPResp) ocspResp.getResponseObject();
                    SingleResp[] responses = basicResp.getResponses();

                    if (responses.length > 0) {
                        SingleResp resp = responses[0];
                        org.bouncycastle.cert.ocsp.CertificateStatus status = resp.getCertStatus();

                        if (status == null) return RevocationStatus.GOOD;
                        if (status instanceof RevokedStatus) return RevocationStatus.REVOKED;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error en verificación OCSP: " + e.getMessage());
        }
        return RevocationStatus.UNKNOWN;
    }

    private static RevocationStatus checkCRL(X509Certificate cert, Date validationDate) {
        try {
            List<String> crlUrls = com.sauken.s_fide.pdf_verify_signatures.utils.CertificateUtils.getCRLUrls(cert);
            for (String url : crlUrls) {
                try {
                    HttpResponse<byte[]> response = NetworkUtils.sendRequest(url, null, "application/x-pkcs7-crl");

                    if (response.statusCode() == 200) {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(response.body()));

                        X509CRLEntry entry = crl.getRevokedCertificate(cert.getSerialNumber());
                        if (entry != null && entry.getRevocationDate().before(validationDate)) {
                            return RevocationStatus.REVOKED;
                        } else if (entry == null) {
                            return RevocationStatus.GOOD;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.fine("Error CRL para " + url + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error en verificación CRL: " + e.getMessage());
        }
        return RevocationStatus.UNKNOWN;
    }

    private static byte[] generateBasicOCSPRequest(X509Certificate cert) throws Exception {
        OCSPReqBuilder builder = new OCSPReqBuilder();
        CertificateID certId = new CertificateID(
                null, // Sin DigestCalculatorProvider por simplicidad
                new JcaX509CertificateHolder(cert),
                cert.getSerialNumber()
        );
        builder.addRequest(certId);
        return builder.build().getEncoded();
    }
}