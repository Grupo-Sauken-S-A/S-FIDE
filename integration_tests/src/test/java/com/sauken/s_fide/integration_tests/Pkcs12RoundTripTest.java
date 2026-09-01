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

package com.sauken.s_fide.integration_tests;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueba de extremo a extremo: firma y luego verifica un XML y un PDF reales,
 * invocando XMLSignerPKCS12/XMLVerifySignatures/PDFSignerPKCS12/PDFVerifySignatures
 * como procesos separados — exactamente como lo haría un integrador real, no
 * llamando a sus clases internas directamente.
 * <p>
 * No usa ningún token físico ni ningún archivo de certificado guardado en el
 * repositorio: el certificado PKCS#12 se genera al vuelo con "keytool" (ya
 * incluido en cualquier JDK), autofirmado, con una contraseña aleatoria que
 * nunca se persiste ni se imprime — se descarta junto con el resto de la
 * carpeta temporal de la prueba al terminar. Esto es justamente lo que hace
 * posible automatizar esta prueba en un runner de CI sin hardware ni
 * secretos guardados, a diferencia de los módulos PKCS#11/Windows CSP-KSP,
 * que siguen necesitando QA manual con hardware real.
 */
class Pkcs12RoundTripTest {

    private static final int TIMEOUT_SECONDS = 60;

    @TempDir
    Path tempDir;

    private Path keystorePath;
    private String keystorePassword;

    @BeforeEach
    void generarCertificadoDePrueba() throws Exception {
        keystorePath = tempDir.resolve("test.p12");
        keystorePassword = generarPasswordAleatoria();

        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        List<String> comando = List.of(
                keytool.toString(),
                "-genkeypair",
                "-alias", "sfide-integration-test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-keystore", keystorePath.toString(),
                "-storetype", "PKCS12",
                "-storepass", keystorePassword,
                "-keypass", keystorePassword,
                "-dname", "CN=S-FiDE Integration Test, O=Grupo Sauken S.A., C=AR"
        );

        int exitCode = ejecutar(comando, tempDir);
        assertEquals(0, exitCode, "keytool no pudo generar el certificado de prueba");
        assertTrue(Files.exists(keystorePath), "keytool no dejó el archivo .p12 esperado");
    }

    @Test
    void firmaYVerificaXml() throws Exception {
        Path xmlOriginal = tempDir.resolve("documento.xml");
        Files.writeString(xmlOriginal,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<documento><dato>Prueba de integration_tests</dato></documento>\n",
                StandardCharsets.UTF_8);

        int exitFirma = ejecutarModulo(
                "com.sauken.s_fide.xml_signer_pkcs12.XMLSignerPKCS12",
                keystorePath.toString(), keystorePassword, xmlOriginal.toString(), ""
        );
        assertEquals(0, exitFirma, "XMLSignerPKCS12 no terminó con éxito");

        Path xmlFirmado = tempDir.resolve("documento-signed.xml");
        assertTrue(Files.exists(xmlFirmado), "No se generó el XML firmado esperado");

        int exitVerificacion = ejecutarModulo(
                "com.sauken.s_fide.xml_verify_signatures.XMLVerifySignatures",
                xmlFirmado.toString()
        );
        assertEquals(0, exitVerificacion, "XMLVerifySignatures no reportó el XML como válido");
    }

    @Test
    void firmaYVerificaPdf() throws Exception {
        Path pdfOriginal = tempDir.resolve("documento.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfOriginal.toString()))) {
            Document document = new Document(pdfDoc);
            document.add(new Paragraph("Documento de prueba para integration_tests"));
        }

        int exitFirma = ejecutarModulo(
                "com.sauken.s_fide.pdf_signer_pkcs12.PDFSignerPKCS12",
                "-i", pdfOriginal.toString(),
                "-c", keystorePath.toString(),
                "-p", keystorePassword
        );
        assertEquals(0, exitFirma, "PDFSignerPKCS12 no terminó con éxito");

        Path pdfFirmado = tempDir.resolve("documento-signed.pdf");
        assertTrue(Files.exists(pdfFirmado), "No se generó el PDF firmado esperado");

        int exitVerificacion = ejecutarModulo(
                "com.sauken.s_fide.pdf_verify_signatures.PDFVerifySignatures",
                pdfFirmado.toString()
        );
        assertEquals(0, exitVerificacion, "PDFVerifySignatures no reportó el PDF como válido");
    }

    /**
     * Corre la clase indicada como un proceso Java nuevo (nunca invocando su
     * main() directamente en este mismo proceso: main() termina llamando a
     * System.exit(), que mataría al proceso de test entero antes de que
     * JUnit pueda reportar nada). Usa el classpath del propio proceso de
     * test — Surefire ya lo arma con las clases compiladas de este módulo
     * más todas las dependencias declaradas (incluidos los cuatro módulos de
     * producción agregados como dependencia de test más arriba) — así que no
     * hace falta que exista ningún .jar empaquetado todavía.
     */
    private int ejecutarModulo(String claseFqcn, String... argumentos) throws IOException, InterruptedException {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> comando = new java.util.ArrayList<>(List.of(
                javaBin, "-cp", classpath,
                "-Dfile.encoding=UTF-8",
                // Sin esto, el proceso puede quedarse colgado intentando
                // conectar a una pantalla — iText toca clases AWT (métricas
                // de fuente) incluso para generar/firmar un PDF sin ninguna
                // interfaz gráfica de por medio. Confirmado empíricamente:
                // sin esta propiedad, firmaYVerificaPdf() se colgaba hasta
                // el timeout; con ella, termina en segundos.
                "-Djava.awt.headless=true",
                claseFqcn
        ));
        comando.addAll(List.of(argumentos));

        return ejecutar(comando, tempDir);
    }

    private int ejecutar(List<String> comando, Path directorioDeTrabajo) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.directory(directorioDeTrabajo.toFile());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        // Drenar la salida en un hilo aparte, EN SIMULTÁNEO con la espera —
        // no después. Confirmado empíricamente con un thread dump real: el
        // classpath combinado de las cuatro dependencias de este módulo hace
        // que Logback encuentre varios "logback.xml" duplicados y tire bastante
        // diagnóstico por stdout al arrancar cada subproceso — más de lo que
        // entra en el buffer del pipe del SO. Si nadie lee mientras tanto, el
        // hijo queda bloqueado escribiendo y este método bloqueado en
        // waitFor(): un interbloqueo clásico de ProcessBuilder. Que la salida
        // sea "poca" nunca hay que asumirlo — hay que drenarla siempre.
        StringBuilder salidaCapturada = new StringBuilder();
        Thread lector = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    salidaCapturada.append(linea).append('\n');
                }
            } catch (IOException ignored) {
                // Pasa si el proceso se destruye a la fuerza más abajo por timeout.
            }
        }, "lector-salida-proceso");
        lector.setDaemon(true);
        lector.start();

        boolean terminoATiempo = proceso.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!terminoATiempo) {
            proceso.destroyForcibly();
            lector.join(2000);
            fail("El proceso no terminó dentro de " + TIMEOUT_SECONDS + " segundos: " + comando
                    + "\nSalida hasta el momento:\n" + salidaCapturada);
        }

        lector.join(5000);

        if (proceso.exitValue() != 0) {
            System.out.println("--- salida del proceso (código " + proceso.exitValue() + ") ---");
            System.out.println(salidaCapturada);
        }

        return proceso.exitValue();
    }

    private static String generarPasswordAleatoria() {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(alfabeto.charAt(random.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }
}
