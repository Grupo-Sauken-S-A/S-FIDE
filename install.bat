@echo off
cls

c:
cd c:\s-fide

del /q c:\s-fide\pkcs12_certificate_extractor-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\token_certificate_extractor-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\token_slots_view-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\xml_signer_pkcs11-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\xml_signer_pkcs12-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\xml_verify_signatures-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\pdf_verify_signatures-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\xml_verify_xsd_structure-1.0.0-jar-with-dependencies.jar
del /q c:\s-fide\pdf_signer_pkcs12-jar-with-dependencies.jar
del /q c:\s-fide\pdf_signer_pkcs11-jar-with-dependencies.jar
del /q c:\s-fide\s_fide_gui-1.0.0.jar
del /q c:\s-fide\lib\*.*

del /q c:\s-fide\PKCS12CertificateExtractor.jar
del /q c:\s-fide\TokenCertificateExtractor.jar
del /q c:\s-fide\TokenSlotsView.jar
del /q c:\s-fide\XMLSignerPKCS11.jar
del /q c:\s-fide\XMLSignerPKCS12.jar
del /q c:\s-fide\XMLVerifySignatures.jar
del /q c:\s-fide\PDFVerifySignatures.jar
del /q c:\s-fide\XMLVerifyXSDStructure.jar
del /q c:\s-fide\PDFSignerPKCS12.jar
del /q c:\s-fide\PDFSignerPKCS11.jar
del /q c:\s-fide\SFide-GUI.jar

copy C:\Users\juan_\IdeaProjects\S-FIDE\pkcs12_certificate_extractor\target\*jar-with-dependencies.jar c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\token_certificate_extractor\target\*jar-with-dependencies.jar  c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\token_slots_view\target\*jar-with-dependencies.jar             c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\xml_signer_pkcs11\target\*jar-with-dependencies.jar            c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\xml_signer_pkcs12\target\*jar-with-dependencies.jar            c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\xml_verify_signatures\target\*jar-with-dependencies.jar        c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\pdf_verify_signatures\target\*jar-with-dependencies.jar        c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\xml_verify_xsd_structure\target\*jar-with-dependencies.jar     c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\s_fide_gui\target\s_fide_gui-1.0.0.jar                         c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\pdf_signer_pkcs12\target\*jar-with-dependencies.jar            c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\pdf_signer_pkcs11\target\*jar-with-dependencies.jar            c:\s-fide\
copy C:\Users\juan_\IdeaProjects\S-FIDE\s_fide_gui\target\s_fide_gui-1.0.0-distribution\lib\*.*        c:\s-fide\lib\

ren pkcs12_certificate_extractor-1.0.0-jar-with-dependencies.jar PKCS12CertificateExtractor.jar
ren token_certificate_extractor-1.0.0-jar-with-dependencies.jar  TokenCertificateExtractor.jar
ren token_slots_view-1.0.0-jar-with-dependencies.jar             TokenSlotsView.jar
ren xml_signer_pkcs11-1.0.0-jar-with-dependencies.jar            XMLSignerPKCS11.jar
ren xml_signer_pkcs12-1.0.0-jar-with-dependencies.jar            XMLSignerPKCS12.jar
ren xml_verify_signatures-1.0.0-jar-with-dependencies.jar        XMLVerifySignatures.jar
ren pdf_verify_signatures-1.0.0-jar-with-dependencies.jar        PDFVerifySignatures.jar
ren xml_verify_xsd_structure-1.0.0-jar-with-dependencies.jar     XMLVerifyXSDStructure.jar
ren pdf_signer_pkcs12-1.0.0-jar-with-dependencies.jar            PDFSignerPKCS12.jar
ren pdf_signer_pkcs11-1.0.0-jar-with-dependencies.jar            PDFSignerPKCS11.jar
ren s_fide_gui-1.0.0.jar                                         SFide-GUI.jar