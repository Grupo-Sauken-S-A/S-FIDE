/*
  Derechos Reservados © 2024 Juan Carlos Ríos y Juan Ignacio Ríos, Grupo Sauken S.A.

  Este es un Software Libre; como tal redistribuirlo y/o modificarlo está
  permitido, siempre y cuando se haga bajo los términos y condiciones de la
  Licencia Pública General GNU publicada por la Free Software Foundation,
  ya sea en su versión 2 ó cualquier otra de las posteriores a la misma.

  Este "Programa" se distribuye con la intención de que sea útil, sin
  embargo carece de garantía, ni siquiera tiene la garantía implícita de
  tipo comercial o inherente al propósito del mismo "Programa". Ver la
  Licencia Pública General GNU para más detalles.

  Se debe haber recibido una copia de la Licencia Pública General GNU con
  este "Programa", si este no fue el caso, favor de escribir a la Free
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Comprueba que los programas de línea de comandos se comporten igual que en una versión anterior. Ejecuta la
 * misma batería de casos con dos carpetas de jars (la publicada y la nueva) y compara, caso por caso, la salida
 * estándar, la salida de error, el código de salida y los archivos generados. Las partes que cambian en cada
 * ejecución (fechas, horas, carpeta de trabajo, número de versión) se normalizan antes de comparar.
 * <p>
 * No usa token ni el almacén de Windows: los casos que firman usan certificados PKCS#12 de prueba que se generan
 * en el momento con keytool. De los módulos de token se cubren las ayudas y los errores de argumentos.
 * <p>
 * Uso: java herramientas/CompatibilidadCli.java &lt;jarsAnteriores&gt; &lt;jarsNuevos&gt; &lt;carpetaDeTrabajo&gt;
 * <p>
 * Cada carpeta de jars debe tener los nombres de distribución (XMLSignerPKCS12.jar, etc.). La carpeta de
 * trabajo se crea; no puede existir con contenido. Termina con código 0 si no hay diferencias y 1 si las hay.
 */
public final class CompatibilidadCli {

    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    private static final String CLAVE = "clave-de-prueba-123";

    private record Caso(String nombre, String modulo, List<String> argumentos) {
    }

    private record Resultado(int codigo, String salida, String error, TreeMap<String, String> archivos) {
    }

    private CompatibilidadCli() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length != 3) {
            System.err.println("Uso: java CompatibilidadCli.java <jarsAnteriores> <jarsNuevos> <carpetaDeTrabajo>");
            System.exit(1);
        }
        Path anteriores = Path.of(args[0]).toAbsolutePath();
        Path nuevos = Path.of(args[1]).toAbsolutePath();
        Path trabajo = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(trabajo);
        try (Stream<Path> contenido = Files.list(trabajo)) {
            if (contenido.findAny().isPresent()) {
                System.err.println("La carpeta de trabajo no está vacía: " + trabajo);
                System.exit(1);
            }
        }

        Path insumos = Files.createDirectories(trabajo.resolve("insumos"));
        prepararInsumos(anteriores, insumos);

        List<Caso> casos = casos();
        int diferencias = 0;
        int numero = 0;
        for (Caso caso : casos) {
            numero++;
            String etiqueta = String.format("%03d-%s", numero, caso.nombre());
            Resultado antes = ejecutarCaso(anteriores, insumos, trabajo.resolve("anterior").resolve(etiqueta), caso);
            Resultado despues = ejecutarCaso(nuevos, insumos, trabajo.resolve("nuevo").resolve(etiqueta), caso);
            if (!antes.equals(despues)) {
                diferencias++;
                System.out.println("==================== DIFERENCIA: " + etiqueta + " (" + caso.modulo() + " "
                        + String.join(" ", caso.argumentos()) + ")");
                informar("código", String.valueOf(antes.codigo()), String.valueOf(despues.codigo()));
                informar("salida estándar", antes.salida(), despues.salida());
                informar("salida de error", antes.error(), despues.error());
                informar("archivos", antes.archivos().toString(), despues.archivos().toString());
            }
        }
        System.out.println("Casos ejecutados: " + casos.size() + ". Con diferencias: " + diferencias + ".");
        System.exit(diferencias == 0 ? 0 : 1);
    }

    private static void informar(String que, String antes, String despues) {
        if (!antes.equals(despues)) {
            System.out.println("--- " + que + " (anterior)");
            System.out.println(recortar(antes));
            System.out.println("+++ " + que + " (nuevo)");
            System.out.println(recortar(despues));
        }
    }

    private static String recortar(String texto) {
        return texto.length() <= 900 ? texto : texto.substring(0, 900) + " [...]";
    }

    // ---------------------------------------------------------------------------------------------- insumos

    private static void prepararInsumos(Path jars, Path dir) throws Exception {
        generarP12(dir, "ok.p12", List.of("-validity", "30", "-ext", "KeyUsage=digitalSignature,nonRepudiation"));
        generarP12(dir, "vencido.p12", List.of("-startdate", "-30d", "-validity", "2"));

        escribir(dir, "simple.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <doc>
                  <item Id="item">Declaración de ñandú</item>
                </doc>
                """);
        escribir(dir, "cod.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <CertificadoOrigen>
                  <Exportador><COD Id="COD"><n>1</n></COD></Exportador>
                  <Entidad><CODEH Id="CODEH"><n>2</n></CODEH></Entidad>
                </CertificadoOrigen>
                """);
        escribir(dir, "djo.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Declaracion>
                  <Exportador><DJO Id="DJO"><n>1</n></DJO></Exportador>
                  <Entidad><DJOEH Id="DJOEH"><n>2</n></DJOEH></Entidad>
                </Declaracion>
                """);
        escribir(dir, "malformado.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <doc><sin-cerrar></doc>
                """);
        ByteArrayOutputStream bom = new ByteArrayOutputStream();
        bom.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        bom.write(Files.readAllBytes(dir.resolve("simple.xml")));
        Files.write(dir.resolve("bom.xml"), bom.toByteArray());
        escribir(dir, "esquema.xsd", """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="doc">
                    <xs:complexType><xs:sequence>
                      <xs:element name="item"><xs:complexType><xs:simpleContent><xs:extension base="xs:string">
                        <xs:attribute name="Id" type="xs:string"/></xs:extension></xs:simpleContent></xs:complexType>
                      </xs:element>
                      <xs:any namespace="##other" processContents="skip" minOccurs="0" maxOccurs="unbounded"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        escribir(dir, "otro.xsd", """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="otra" type="xs:string"/>
                </xs:schema>
                """);
        escribir(dir, "roto.xsd", "esto no es un esquema\n");
        Files.write(dir.resolve("doc.pdf"), pdfMinimo());
        escribir(dir, "noespdf.pdf", "esto no es un PDF\n");

        // Documentos ya firmados, generados una sola vez con los jars anteriores y compartidos por ambas corridas.
        preparar(jars, dir, "XMLSignerPKCS12", "ok.p12", CLAVE, "simple.xml", "");
        preparar(jars, dir, "XMLSignerPKCS12", "ok.p12", CLAVE, "cod.xml", "COD");
        preparar(jars, dir, "XMLSignerPKCS12", "ok.p12", CLAVE, "djo.xml", "DJO");
        Files.move(dir.resolve("simple-signed.xml"), dir.resolve("firmado.xml"));
        Files.move(dir.resolve("cod-signed.xml"), dir.resolve("codfirmado.xml"));
        Files.move(dir.resolve("djo-signed.xml"), dir.resolve("djofirmado.xml"));
        escribir(dir, "alterado.xml", Files.readString(dir.resolve("firmado.xml")).replace("ñandú", "otra cosa"));
        preparar(jars, dir, "PDFSignerPKCS12", "-i", "doc.pdf", "-c", "ok.p12", "-p", CLAVE);
        Files.move(dir.resolve("doc-signed.pdf"), dir.resolve("firmado.pdf"));
        preparar(jars, dir, "PDFSignerPKCS12", "-i", "doc.pdf", "-c", "ok.p12", "-p", CLAVE, "-l", "true");
        Files.move(dir.resolve("doc-signed.pdf"), dir.resolve("cerrado.pdf"));
        borrarCarpeta(dir.resolve("logs"));
    }

    private static void preparar(Path jars, Path dir, String modulo, String... argumentos) throws Exception {
        Resultado r = ejecutar(comando(jars, modulo, List.of(argumentos)), dir);
        if (r.codigo() != 0) {
            throw new IllegalStateException("No se pudo preparar un insumo con " + modulo + ": " + r.error() + r.salida());
        }
    }

    private static void generarP12(Path dir, String nombre, List<String> extra) throws Exception {
        List<String> comando = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias",
                "prueba", "-keyalg", "RSA", "-keysize", "2048", "-keystore", nombre, "-storetype", "PKCS12",
                "-storepass", CLAVE, "-keypass", CLAVE, "-dname", "CN=Prueba de Compatibilidad, O=S-FiDE, C=AR"));
        comando.addAll(extra);
        Resultado r = ejecutar(comando, dir);
        if (r.codigo() != 0) {
            throw new IllegalStateException("keytool falló: " + r.error() + r.salida());
        }
    }

    private static void escribir(Path dir, String nombre, String contenido) throws IOException {
        Files.writeString(dir.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    /** Un PDF válido de dos páginas A4, armado a mano para no depender de ningún archivo del repositorio. */
    private static byte[] pdfMinimo() {
        List<String> objetos = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
        List<Integer> posiciones = new ArrayList<>();
        for (int i = 0; i < objetos.size(); i++) {
            posiciones.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objetos.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objetos.size() + 1).append("\n0000000000 65535 f \n");
        for (int posicion : posiciones) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", posicion));
        }
        pdf.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    // ----------------------------------------------------------------------------------------------- casos

    private static List<Caso> casos() {
        List<Caso> casos = new ArrayList<>();
        for (String modulo : List.of("XMLSignerPKCS11", "XMLSignerPKCS12", "XMLVerifySignatures",
                "XMLVerifyXSDStructure", "PDFSignerPKCS11", "PDFSignerPKCS12", "PDFVerifySignatures")) {
            String m = modulo.toLowerCase(Locale.ROOT);
            for (String especial : List.of("-version", "-v", "--version", "-ayuda", "-h", "--help", "-licencia",
                    "--license", "-listar-drivers", "-inexistente")) {
                agregar(casos, m + "-especial" + especial, modulo, especial);
            }
            agregar(casos, m + "-sin-argumentos", modulo);
        }

        String x = "XMLSignerPKCS12";
        agregar(casos, "x12-completo", x, "ok.p12", CLAVE, "simple.xml", "");
        agregar(casos, "x12-elemento", x, "ok.p12", CLAVE, "simple.xml", "item");
        agregar(casos, "x12-cod", x, "ok.p12", CLAVE, "cod.xml", "COD");
        agregar(casos, "x12-codeh", x, "ok.p12", CLAVE, "codfirmado.xml", "CODEH");
        agregar(casos, "x12-codeh-sin-cod", x, "ok.p12", CLAVE, "cod.xml", "CODEH");
        agregar(casos, "x12-cod-doble", x, "ok.p12", CLAVE, "codfirmado.xml", "COD");
        agregar(casos, "x12-comex-completo", x, "ok.p12", CLAVE, "cod.xml", "");
        agregar(casos, "x12-djo", x, "ok.p12", CLAVE, "djo.xml", "DJO");
        agregar(casos, "x12-djoeh", x, "ok.p12", CLAVE, "djofirmado.xml", "DJOEH");
        agregar(casos, "x12-elemento-inexistente", x, "ok.p12", CLAVE, "simple.xml", "NoEsta");
        agregar(casos, "x12-bom", x, "ok.p12", CLAVE, "bom.xml", "");
        agregar(casos, "x12-malformado", x, "ok.p12", CLAVE, "malformado.xml", "");
        agregar(casos, "x12-clave-mala", x, "ok.p12", "mala", "simple.xml", "");
        agregar(casos, "x12-sin-xml", x, "ok.p12", CLAVE, "noesta.xml", "");
        agregar(casos, "x12-sin-p12", x, "noesta.p12", CLAVE, "simple.xml", "");
        agregar(casos, "x12-vencido", x, "vencido.p12", CLAVE, "simple.xml", "");
        agregar(casos, "x12-pocos", x, "ok.p12", CLAVE, "simple.xml");
        agregar(casos, "x12-omitir", x, "ok.p12", CLAVE, "simple.xml", "", "-omitir-revocacion", "true");
        agregar(casos, "x12-quinto-malo", x, "ok.p12", CLAVE, "simple.xml", "", "-otra", "true");
        agregar(casos, "x12-revocacion", x, "-verificar-revocacion", "ok.p12", CLAVE);
        agregar(casos, "x12-revocacion-vencido", x, "-verificar-revocacion", "vencido.p12", CLAVE);

        String t = "XMLSignerPKCS11";
        agregar(casos, "x11-sin-biblioteca", t, "noesta.dll", "1234", "0", "simple.xml", "");
        agregar(casos, "x11-slot-malo", t, "noesta.dll", "1234", "x", "simple.xml", "");
        agregar(casos, "x11-no-es-biblioteca", t, "simple.xml", "1234", "0", "simple.xml", "");
        agregar(casos, "x11-pocos", t, "noesta.dll", "1234");
        agregar(casos, "x11-revocacion", t, "-verificar-revocacion", "noesta.dll", "1234", "0");

        String v = "XMLVerifySignatures";
        for (String doc : List.of("firmado.xml", "codfirmado.xml", "alterado.xml", "simple.xml", "malformado.xml",
                "noesta.xml", "bom.xml")) {
            agregar(casos, "xv-" + doc, v, doc);
            agregar(casos, "xv-simple-" + doc, v, doc, "-simple");
        }
        agregar(casos, "xv-segundo-malo", v, "firmado.xml", "-otro");
        agregar(casos, "xv-muchos", v, "firmado.xml", "-simple", "-otro");

        String s = "XMLVerifyXSDStructure";
        agregar(casos, "xsd-valido", s, "firmado.xml", "esquema.xsd");
        agregar(casos, "xsd-sin-firmas", s, "simple.xml", "esquema.xsd");
        agregar(casos, "xsd-alterado", s, "alterado.xml", "esquema.xsd");
        agregar(casos, "xsd-otro-esquema", s, "firmado.xml", "otro.xsd");
        agregar(casos, "xsd-esquema-roto", s, "firmado.xml", "roto.xsd");
        agregar(casos, "xsd-sin-referencia", s, "firmado.xml");
        agregar(casos, "xsd-sin-xml", s, "noesta.xml", "esquema.xsd");
        agregar(casos, "xsd-sin-xsd", s, "firmado.xml", "noesta.xsd");
        agregar(casos, "xsd-malformado", s, "malformado.xml", "esquema.xsd");
        agregar(casos, "xsd-muchos", s, "firmado.xml", "esquema.xsd", "-otro");

        String p = "PDFSignerPKCS12";
        pdf(casos, "p12-invisible", p, "-i", "doc.pdf");
        pdf(casos, "p12-visible", p, "-i", "doc.pdf", "-x", "40", "-y", "55", "-t", "Texto");
        pdf(casos, "p12-pagina2", p, "-i", "doc.pdf", "-x", "40", "-y", "55", "-pagina", "2");
        pdf(casos, "p12-bloqueado", p, "-i", "doc.pdf", "-l", "true");
        pdf(casos, "p12-protegido", p, "-i", "doc.pdf", "-proteger-contenido", "true");
        pdf(casos, "p12-segunda", p, "-i", "firmado.pdf");
        pdf(casos, "p12-cierre", p, "-i", "firmado.pdf", "-l", "true");
        pdf(casos, "p12-cerrado", p, "-i", "cerrado.pdf");
        pdf(casos, "p12-pagina-mala", p, "-i", "doc.pdf", "-x", "40", "-y", "55", "-pagina", "9");
        pdf(casos, "p12-no-entra", p, "-i", "doc.pdf", "-x", "590", "-y", "55");
        pdf(casos, "p12-campo-malo", p, "-i", "doc.pdf", "-campo", "NoEsta");
        pdf(casos, "p12-lock-y-proteger", p, "-i", "doc.pdf", "-l", "true", "-proteger-contenido", "true");
        pdf(casos, "p12-numero-malo", p, "-i", "doc.pdf", "-x", "abc");
        pdf(casos, "p12-desconocido", p, "-i", "doc.pdf", "-desconocido", "algo");
        pdf(casos, "p12-log-suelto", p, "-i", "doc.pdf", "-log", "-informe", "x.csv");
        pdf(casos, "p12-texto-lote", p, "-i", "doc.pdf", "-x", "40", "-y", "55", "-t", "-lote");
        pdf(casos, "p12-sin-pdf", p, "-i", "noesta.pdf");
        pdf(casos, "p12-no-es-pdf", p, "-i", "noespdf.pdf");
        agregar(casos, "p12-clave-mala", p, "-i", "doc.pdf", "-c", "ok.p12", "-p", "mala");
        agregar(casos, "p12-sin-p12", p, "-i", "doc.pdf", "-c", "noesta.p12", "-p", CLAVE);
        agregar(casos, "p12-vencido", p, "-i", "doc.pdf", "-c", "vencido.p12", "-p", CLAVE);
        agregar(casos, "p12-faltan", p, "-i", "doc.pdf", "-c", "ok.p12", "-x", "1");
        agregar(casos, "p12-pocos", p, "-i", "doc.pdf");
        agregar(casos, "p12-analizar", p, "-analizar-documento", "firmado.pdf");
        agregar(casos, "p12-analizar-malo", p, "-analizar-documento", "noespdf.pdf");
        agregar(casos, "p12-revocacion", p, "-verificar-revocacion", "ok.p12", CLAVE);
        agregar(casos, "p12-vista", p, "-vista-previa-texto", "-c", "ok.p12", "-p", CLAVE, "-t", "Hola");

        String k = "PDFSignerPKCS11";
        agregar(casos, "p11-sin-pdf", k, "-i", "noesta.pdf", "-l", "noesta.dll", "-p", "1", "-s", "0");
        agregar(casos, "p11-sin-biblioteca", k, "-i", "doc.pdf", "-l", "noesta.dll", "-p", "1", "-s", "0");
        agregar(casos, "p11-no-es-biblioteca", k, "-i", "doc.pdf", "-l", "simple.xml", "-p", "1", "-s", "0");
        agregar(casos, "p11-cerrado", k, "-i", "cerrado.pdf", "-l", "simple.xml", "-p", "1", "-s", "0");
        agregar(casos, "p11-pocos", k, "-i", "doc.pdf");
        agregar(casos, "p11-numero-malo", k, "-i", "doc.pdf", "-l", "noesta.dll", "-p", "1", "-s", "x");
        agregar(casos, "p11-faltan", k, "-i", "doc.pdf", "-l", "noesta.dll", "-p", "1", "-x", "1", "-y", "1");
        agregar(casos, "p11-analizar", k, "-analizar-documento", "firmado.pdf");

        String pv = "PDFVerifySignatures";
        for (String doc : List.of("firmado.pdf", "cerrado.pdf", "doc.pdf", "noespdf.pdf", "noesta.pdf")) {
            agregar(casos, "pv-" + doc, pv, doc);
            agregar(casos, "pv-simple-" + doc, pv, doc, "-simple");
        }
        agregar(casos, "pv-extra", pv, "firmado.pdf", "-otro", "-mas");
        return casos;
    }

    private static void agregar(List<Caso> casos, String nombre, String modulo, String... argumentos) {
        casos.add(new Caso(nombre, modulo, List.of(argumentos)));
    }

    /** Caso de firma de PDF con la credencial de prueba correcta agregada al final. */
    private static void pdf(List<Caso> casos, String nombre, String modulo, String... resto) {
        List<String> argumentos = new ArrayList<>(List.of(resto));
        argumentos.addAll(List.of("-c", "ok.p12", "-p", CLAVE));
        casos.add(new Caso(nombre, modulo, argumentos));
    }

    // ------------------------------------------------------------------------------------------- ejecución

    private static List<String> comando(Path jars, String modulo, List<String> argumentos) {
        List<String> comando = new ArrayList<>(List.of(JAVA, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8", "-Djava.awt.headless=true", "-jar",
                jars.resolve(modulo + ".jar").toString()));
        comando.addAll(argumentos);
        return comando;
    }

    private static Resultado ejecutarCaso(Path jars, Path insumos, Path carpeta, Caso caso) throws Exception {
        Files.createDirectories(carpeta);
        TreeMap<String, String> originales = new TreeMap<>();
        try (Stream<Path> lista = Files.list(insumos)) {
            for (Path origen : lista.filter(Files::isRegularFile).toList()) {
                Path destino = carpeta.resolve(origen.getFileName().toString());
                Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
                originales.put(origen.getFileName().toString(), huella(destino));
            }
        }
        Resultado r = ejecutar(comando(jars, caso.modulo(), caso.argumentos()), carpeta);

        TreeMap<String, String> archivos = new TreeMap<>();
        try (Stream<Path> lista = Files.list(carpeta)) {
            for (Path archivo : lista.filter(Files::isRegularFile).toList()) {
                String nombre = archivo.getFileName().toString();
                String huella = huella(archivo);
                if (!huella.equals(originales.get(nombre))) {
                    // Un XML firmado con la misma clave RSA sale idéntico byte a byte; un PDF lleva fecha y hora.
                    archivos.put(nombre, nombre.endsWith(".xml") ? huella : "(generado)");
                }
            }
        }
        for (String nombre : originales.keySet()) {
            if (!Files.exists(carpeta.resolve(nombre))) {
                archivos.put(nombre, "(borrado)");
            }
        }
        String salida = normalizar(r.salida(), carpeta);
        String error = normalizar(r.error(), carpeta);
        Files.writeString(carpeta.resolve("_resultado.txt"), "codigo=" + r.codigo() + "\n--- stdout\n" + salida
                + "\n--- stderr\n" + error + "\n--- archivos\n" + archivos + "\n", StandardCharsets.UTF_8);
        return new Resultado(r.codigo(), salida, error, archivos);
    }

    private static String normalizar(String texto, Path carpeta) {
        return texto
                .replace("\r", "")
                .replace(carpeta.toString(), "<DIR>")
                .replace(carpeta.toString().replace('\\', '/'), "<DIR>")
                .replaceAll("v\\d+\\.\\d+\\.\\d+", "v<VERSION>")
                .replaceAll("\\d{2}/\\d{2}/\\d{4}( \\d{2}:\\d{2}(:\\d{2})?)?", "<FECHA>")
                .replaceAll("(?mU)^\\p{L}{3,4}\\.? \\d{1,2}, \\d{4} \\d{1,2}:\\d{2}:\\d{2}\\s[AP]\\.\\s?M\\.",
                        "<FECHAHORA>")
                .replaceAll("(?m)^\\d{2}:\\d{2}:\\d{2}[.,]\\d{3} ", "<HORA> ")
                .replaceAll("Signature_[A-Za-z0-9_]+_\\d{10,}", "<CAMPO>")
                .replaceAll("enc\\d+\\.tmp", "enc<N>.tmp");
    }

    private static Resultado ejecutar(List<String> comando, Path carpeta) throws Exception {
        Process proceso = new ProcessBuilder(comando).directory(carpeta.toFile()).start();
        proceso.getOutputStream().close();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        Thread lectorSalida = copiar(proceso.getInputStream(), salida);
        Thread lectorError = copiar(proceso.getErrorStream(), error);
        if (!proceso.waitFor(120, TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
            throw new IllegalStateException("El proceso no terminó a tiempo: " + comando);
        }
        lectorSalida.join(5000);
        lectorError.join(5000);
        return new Resultado(proceso.exitValue(), salida.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8), new TreeMap<>());
    }

    private static Thread copiar(InputStream origen, ByteArrayOutputStream destino) {
        Thread hilo = new Thread(() -> {
            try {
                origen.transferTo(destino);
            } catch (IOException ignorada) {
                // El proceso se destruyó: se usa lo leído hasta ese momento.
            }
        });
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    private static String huella(Path archivo) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivo)))
                .substring(0, 16);
    }

    private static void borrarCarpeta(Path carpeta) throws IOException {
        if (!Files.exists(carpeta)) {
            return;
        }
        try (Stream<Path> arbol = Files.walk(carpeta)) {
            for (Path p : arbol.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
