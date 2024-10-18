package com.sauken.ds_cod.tokencertextractor;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class TokenCertificateExtractor {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java -jar token-cert-extractor.jar <ruta del driver PKCS#11> <contraseña del token> <número de slot> <alias del certificado>");
            System.exit(1);
        }

        String pkcs11LibraryPath = args[0];
        String password = args[1];
        int slotNumber = Integer.parseInt(args[2]);
        String certificateAlias = args[3];

        try {
            extractCertificate(pkcs11LibraryPath, password, slotNumber, certificateAlias);
        } catch (Exception e) {
            System.err.println("Error al leer el token: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void extractCertificate(String pkcs11LibraryPath, String password, int slotNumber, String certificateAlias)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        // Configurar el proveedor PKCS#11
        String config = String.format(
                "--name=CustomProvider\nlibrary=%s\nslot=%d",
                pkcs11LibraryPath,
                slotNumber
        );
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new RuntimeException("Proveedor SunPKCS11 no disponible");
        }
        provider = provider.configure(config);
        Security.addProvider(provider);

        // Abrir el KeyStore desde el token
        KeyStore keyStore = KeyStore.getInstance("PKCS11");
        keyStore.load(null, password.toCharArray());

        // Extraer el certificado especificado
        if (keyStore.containsAlias(certificateAlias)) {
            Certificate cert = keyStore.getCertificate(certificateAlias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) cert;
                System.out.println("Información del Certificado:");
                System.out.println("Sujeto: " + x509Cert.getSubjectX500Principal());
                System.out.println("Emisor: " + x509Cert.getIssuerX500Principal());
                System.out.println("Número de Serie: " + x509Cert.getSerialNumber());
                System.out.println("Válido desde: " + x509Cert.getNotBefore());
                System.out.println("Válido hasta: " + x509Cert.getNotAfter());
                System.out.println("Algoritmo de Firma: " + x509Cert.getSigAlgName());
            } else {
                System.out.println("El certificado no es de tipo X.509");
            }
        } else {
            System.out.println("No se encontró un certificado con el alias especificado: " + certificateAlias);
        }
    }
}