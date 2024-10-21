package com.sauken.s_fide;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class TokenCertificateExtractor {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Uso: java -jar token-cert-extractor.jar <ruta del driver PKCS#11> <contraseña del token> <número de slot>");
            System.exit(1);
        }

        String pkcs11LibraryPath = args[0];
        String password = args[1];
        int slotNumber = Integer.parseInt(args[2]);

        try {
            extractCertificate(pkcs11LibraryPath, password, slotNumber);
        } catch (Exception e) {
            System.err.println("Error al leer el token: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void extractCertificate(String pkcs11LibraryPath, String password, int slotNumber)
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

        // Buscar el certificado en el slot
        Enumeration<String> aliases = keyStore.aliases();
        if (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) cert;
                System.out.println("Información del Certificado en el slot " + slotNumber + ":");
                System.out.println("Sujeto: " + x509Cert.getSubjectX500Principal());
                System.out.println("Emisor: " + x509Cert.getIssuerX500Principal());
                System.out.println("Número de Serie: " + x509Cert.getSerialNumber());
                System.out.println("Válido desde: " + x509Cert.getNotBefore());
                System.out.println("Válido hasta: " + x509Cert.getNotAfter());
                System.out.println("Algoritmo de Firma: " + x509Cert.getSigAlgName());
            } else {
                System.out.println("El certificado en el slot " + slotNumber + " no es de tipo X.509");
            }
        } else {
            System.out.println("No se encontró ningún certificado en el slot " + slotNumber);
        }
    }
}