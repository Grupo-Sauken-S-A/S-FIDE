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


package com.sauken.s_fide.s_fide_gui.pdf;

import com.sauken.s_fide.s_fide_gui.pdf.PdfCoordinates.Pagina;
import com.sauken.s_fide.s_fide_gui.pdf.PdfCoordinates.Rectangulo;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lectura de un PDF solo para mostrarlo: dibuja sus páginas y ubica los campos de firma que ya tiene.
 * No firma ni modifica nada; la firma la hace siempre el programa de firma correspondiente.
 * <p>
 * No es seguro usarlo desde varios hilos a la vez: el diálogo lo usa desde un único hilo de dibujo.
 */
public final class PdfDocumentView implements AutoCloseable {

    static {
        // Evita que Java intente iniciar un entorno gráfico propio (AWT) junto al de JavaFX: solo hace
        // falta para dibujar la página en memoria.
        System.setProperty("java.awt.headless", "true");
    }

    /** El documento no se puede mostrar; el mensaje ya está en lenguaje simple. */
    public static final class VistaPreviaException extends Exception {
        public VistaPreviaException(String mensaje) {
            super(mensaje);
        }
    }

    /** Campo de firma que ya existe en el documento, firmado o vacío. Rectángulo en puntos PDF. */
    public record CampoDeFirma(String nombre, int pagina, Rectangulo rectangulo, boolean firmado) {
    }

    private final PDDocument documento;
    private final PDFRenderer renderizador;
    private final List<Pagina> paginas = new ArrayList<>();
    private final List<CampoDeFirma> campos = new ArrayList<>();

    private PdfDocumentView(PDDocument documento) {
        this.documento = documento;
        this.renderizador = new PDFRenderer(documento);
        for (PDPage pagina : documento.getPages()) {
            PDRectangle area = pagina.getCropBox();
            paginas.add(new Pagina(area.getLowerLeftX(), area.getLowerLeftY(), area.getWidth(), area.getHeight(),
                    pagina.getRotation()));
        }
        leerCamposDeFirma();
    }

    public static PdfDocumentView abrir(Path pdf) throws VistaPreviaException {
        try {
            PDDocument documento = Loader.loadPDF(pdf.toFile());
            if (documento.getNumberOfPages() == 0) {
                documento.close();
                throw new VistaPreviaException("El documento no tiene páginas para mostrar.");
            }
            return new PdfDocumentView(documento);
        } catch (InvalidPasswordException e) {
            throw new VistaPreviaException("El documento está protegido con una contraseña, así que no se puede "
                    + "mostrar. Puede ingresar la posición de la firma a mano.");
        } catch (IOException | RuntimeException e) {
            throw new VistaPreviaException("No se pudo abrir el documento para mostrarlo. Verifique que el archivo "
                    + "no esté dañado. Puede ingresar la posición de la firma a mano.");
        }
    }

    public int cantidadDePaginas() {
        return paginas.size();
    }

    /** Geometría de una página; el número empieza en 1. */
    public Pagina pagina(int numero) {
        return paginas.get(numero - 1);
    }

    public List<CampoDeFirma> camposDeFirma() {
        return campos;
    }

    /** Dibuja una página (número desde 1) con {@code escala} píxeles por punto PDF. */
    public synchronized WritableImage renderizar(int numero, double escala) throws VistaPreviaException {
        try {
            BufferedImage imagen = renderizador.renderImage(numero - 1, (float) escala, ImageType.RGB);
            int ancho = imagen.getWidth();
            int alto = imagen.getHeight();
            int[] pixeles = imagen.getRGB(0, 0, ancho, alto, null, 0, ancho);
            WritableImage salida = new WritableImage(ancho, alto);
            salida.getPixelWriter().setPixels(0, 0, ancho, alto, PixelFormat.getIntArgbInstance(), pixeles, 0, ancho);
            return salida;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            throw new VistaPreviaException("No se pudo dibujar la página " + numero + " del documento.");
        }
    }

    private void leerCamposDeFirma() {
        try {
            PDAcroForm formulario = documento.getDocumentCatalog().getAcroForm();
            if (formulario == null) {
                return;
            }
            for (PDField campo : formulario.getFieldTree()) {
                if (!(campo instanceof PDSignatureField firma)) {
                    continue;
                }
                for (PDAnnotationWidget widget : firma.getWidgets()) {
                    int indice = indiceDePagina(widget);
                    PDRectangle rect = widget.getRectangle();
                    if (indice < 0 || rect == null || rect.getWidth() <= 0 || rect.getHeight() <= 0) {
                        continue; // campo invisible o sin lugar en ninguna página
                    }
                    campos.add(new CampoDeFirma(campo.getFullyQualifiedName(), indice + 1,
                            new Rectangulo(rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight()),
                            firma.getSignature() != null));
                }
            }
        } catch (RuntimeException | IOException e) {
            // Sin los campos existentes la vista previa sigue siendo útil: solo no se señalan en pantalla.
            campos.clear();
        }
    }

    private int indiceDePagina(PDAnnotationWidget widget) throws IOException {
        PDPage pagina = widget.getPage();
        if (pagina != null) {
            return documento.getPages().indexOf(pagina);
        }
        // Algunos documentos no indican la página en el campo: se la busca entre las anotaciones.
        int indice = 0;
        for (PDPage candidata : documento.getPages()) {
            for (PDAnnotation anotacion : candidata.getAnnotations()) {
                if (anotacion.getCOSObject() == widget.getCOSObject()) {
                    return indice;
                }
            }
            indice++;
        }
        return -1;
    }

    @Override
    public void close() {
        try {
            documento.close();
        } catch (IOException ignorada) {
            // Ya no se usa; no hay nada útil que informar.
        }
    }
}
