package com.sauken.ds_cod.tokenreader;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class TokenReader {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar token-reader.jar <PKCS#11 library path> <token password>");
            System.exit(1);
        }

        String pkcs11LibraryPath = args[0];
        String password = args[1];

        try {
            readToken(pkcs11LibraryPath, password);
        } catch (Exception e) {
            System.err.println("Error reading token: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void readToken(String pkcs11LibraryPath, String password)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        // Configure the PKCS#11 provider
        String config = "--name=CustomProvider\nlibrary=" + pkcs11LibraryPath;
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new RuntimeException("SunPKCS11 provider not available");
        }
        provider = provider.configure(config);
        Security.addProvider(provider);

        // Open the KeyStore from the token
        KeyStore keyStore = KeyStore.getInstance("PKCS11");
        keyStore.load(null, password.toCharArray());

        // Enumerate and display the contents of the slots
        int aliasNumber = 0;
        for (String alias : Collections.list(keyStore.aliases())) {
            System.out.println("Alias Number: " + aliasNumber);
            System.out.println("Alias: " + alias);

            if (keyStore.isKeyEntry(alias)) {
                System.out.println("  Type: Private Key");
            } else if (keyStore.isCertificateEntry(alias)) {
                System.out.println("  Type: Certificate");
                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    System.out.println("  Subject: " + x509Cert.getSubjectDN());
                } else {
                    System.out.println("  Certificate type: " + cert.getType());
                }
            }
            System.out.println();
            aliasNumber++;
        }
    }
}