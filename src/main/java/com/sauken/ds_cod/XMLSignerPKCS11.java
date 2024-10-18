package com.sauken.ds_cod.xmlsignerpkcs11;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import javax.xml.crypto.*;
import javax.xml.crypto.dom.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;

public class XMLSignerPKCS11 {

    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Uso: java XMLSignerPKCS11 <ruta_driver_pkcs11> <contraseña_token> <numero_slot> <archivo_xml> <uri>");
            System.exit(1);
        }

        String pkcs11LibraryPath = args[0];
        String password = args[1];
        int slotNumber = Integer.parseInt(args[2]);
        String xmlFile = args[3];
        String uri = args[4];

        try {
            boolean result = signXML(pkcs11LibraryPath, password, slotNumber, xmlFile, uri);
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("Error al firmar el XML: " + e.getMessage());
            e.printStackTrace();
            System.out.println(false);
        }
    }

    private static boolean signXML(String pkcs11LibraryPath, String password, int slotNumber, String xmlFile, String uri)
            throws Exception {
        // Configurar el proveedor PKCS#11
        String config = String.format(
                "--name=XMLSignerProvider\nlibrary=%s\nslot=%d",
                pkcs11LibraryPath,
                slotNumber
        );
        Provider provider = Security.getProvider("SunPKCS11");
        if (provider == null) {
            throw new RuntimeException("Proveedor SunPKCS11 no disponible");
        }
        provider = provider.configure(config);
        Security.addProvider(provider);

        // Cargar el KeyStore desde el token
        KeyStore keyStore = KeyStore.getInstance("PKCS11");
        keyStore.load(null, password.toCharArray());

        // Obtener la clave privada y el certificado
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

        // Cargar el documento XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new File(xmlFile));

        // Crear el objeto XMLSignatureFactory
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        // Crear la referencia solo para el URI especificado
        Reference ref = fac.newReference(
                uri,
                fac.newDigestMethod(DigestMethod.SHA256, null),
                null,  // Sin transformaciones
                null,
                null);

        // Crear el SignedInfo
        SignedInfo si = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                Collections.singletonList(ref));

        // Crear el KeyInfo
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        X509Data x509Data = kif.newX509Data(Collections.singletonList(cert));
        KeyInfo ki = kif.newKeyInfo(Collections.singletonList(x509Data));

        // Crear el objeto XMLSignature
        XMLSignature signature = fac.newXMLSignature(si, ki);

        // Firmar el documento
        DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
        signature.sign(dsc);

        // Guardar el documento firmado
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer();
        trans.transform(new DOMSource(doc), new StreamResult(new File(xmlFile)));

        return true;
    }
}