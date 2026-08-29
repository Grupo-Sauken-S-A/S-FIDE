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

package com.sauken.s_fide.s_fide_gui.validators;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModuleValidator {
    private static final Map<String, String> JAR_MAPPINGS = new HashMap<>();

    static {
        JAR_MAPPINGS.put("TokenSlotsView", "TokenSlotsView.jar");
        JAR_MAPPINGS.put("TokenCertificateExtractor", "TokenCertificateExtractor.jar");
        JAR_MAPPINGS.put("PKCS12CertificateExtractor", "PKCS12CertificateExtractor.jar");
        JAR_MAPPINGS.put("XMLSignerPKCS11", "XMLSignerPKCS11.jar");
        JAR_MAPPINGS.put("XMLSignerPKCS12", "XMLSignerPKCS12.jar");
        JAR_MAPPINGS.put("XMLVerifySignatures", "XMLVerifySignatures.jar");
        JAR_MAPPINGS.put("XMLVerifyXSDStructure", "XMLVerifyXSDStructure.jar");
        JAR_MAPPINGS.put("PDFSignerPKCS11", "PDFSignerPKCS11.jar");
        JAR_MAPPINGS.put("PDFSignerPKCS12", "PDFSignerPKCS12.jar");
        JAR_MAPPINGS.put("PDFVerifySignatures", "PDFVerifySignatures.jar");
    }

    public record ValidationResult(boolean valid, String errorMessage) {
        public ValidationResult {
            if (valid && errorMessage != null) {
                throw new IllegalArgumentException("Un resultado válido no puede tener mensaje de error");
            }
            if (!valid && (errorMessage == null || errorMessage.trim().isEmpty())) {
                throw new IllegalArgumentException("Un resultado inválido debe tener un mensaje de error");
            }
        }
    }

    public static ValidationResult validateJarFile(String moduleName) {
        String jarName = JAR_MAPPINGS.get(moduleName);
        if (jarName == null) {
            return new ValidationResult(false, "Módulo no reconocido: " + moduleName);
        }

        Path jarPath = Path.of(System.getProperty("user.dir"), jarName);
        if (!Files.exists(jarPath)) {
            return new ValidationResult(false,
                    "No se encuentra el archivo JAR requerido: " + jarName);
        }

        if (!Files.isReadable(jarPath)) {
            return new ValidationResult(false,
                    "No se puede leer el archivo JAR: " + jarName);
        }

        try {
            if (Files.size(jarPath) == 0) {
                return new ValidationResult(false,
                        "El archivo JAR está vacío: " + jarName);
            }
        } catch (IOException e) {
            return new ValidationResult(false,
                    "Error al verificar el archivo JAR " + jarName + ": " + e.getMessage());
        }

        return new ValidationResult(true, null);
    }

    public static void showValidationError(ValidationResult result) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showValidationError(result));
            return;
        }

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("Error de Validación");
        alert.setHeaderText("Error en la validación de parámetros");
        alert.setContentText(result.errorMessage());
        alert.showAndWait();
    }

    public static ValidationResult validatePKCS11(String libPath, String password, String slotNumber) {
        try {
            ValidationResult jarValidation = validateJarFile("TokenSlotsView");
            if (!jarValidation.valid()) {
                return jarValidation;
            }

            if (libPath == null || libPath.trim().isEmpty()) {
                return new ValidationResult(false, "La ruta de la biblioteca PKCS#11 es requerida");
            }

            File lib = new File(libPath);
            if (!lib.exists()) {
                return new ValidationResult(false, "La biblioteca PKCS#11 no existe: " + libPath);
            }
            if (!lib.isFile()) {
                return new ValidationResult(false, "La ruta especificada no es un archivo: " + libPath);
            }
            if (!lib.canRead()) {
                return new ValidationResult(false, "No se puede leer la biblioteca PKCS#11: " + libPath);
            }

            String extension = getFileExtension(libPath).toLowerCase();
            if (!(extension.equals("dll") || extension.equals("so"))) {
                return new ValidationResult(false,
                        "La biblioteca PKCS#11 debe tener extensión .dll o .so: " + libPath);
            }

            if (password == null || password.trim().isEmpty()) {
                return new ValidationResult(false, "La contraseña del token es requerida");
            }

            if (slotNumber != null && !slotNumber.trim().isEmpty()) {
                try {
                    int slot = Integer.parseInt(slotNumber);
                    if (slot < 0) {
                        return new ValidationResult(false,
                                "El número de slot debe ser un valor positivo: " + slot);
                    }
                } catch (NumberFormatException e) {
                    return new ValidationResult(false,
                            "El número de slot debe ser un valor numérico válido: " + slotNumber);
                }
            }

            return new ValidationResult(true, null);
        } catch (SecurityException e) {
            return new ValidationResult(false,
                    "Error de seguridad al acceder a la biblioteca PKCS#11: " + e.getMessage());
        } catch (Exception e) {
            return new ValidationResult(false,
                    "Error inesperado al validar parámetros PKCS#11: " + e.getMessage());
        }
    }

    public static ValidationResult validatePKCS12(String pkcs12Path, String password) {
        try {
            ValidationResult jarValidation = validateJarFile("PKCS12CertificateExtractor");
            if (!jarValidation.valid()) {
                return jarValidation;
            }

            if (pkcs12Path == null || pkcs12Path.trim().isEmpty()) {
                return new ValidationResult(false, "La ruta del archivo PKCS#12 es requerida");
            }

            File pkcs12 = new File(pkcs12Path);
            if (!pkcs12.exists()) {
                return new ValidationResult(false, "El archivo PKCS#12 no existe: " + pkcs12Path);
            }
            if (!pkcs12.isFile()) {
                return new ValidationResult(false, "La ruta especificada no es un archivo: " + pkcs12Path);
            }
            if (!pkcs12.canRead()) {
                return new ValidationResult(false, "No se puede leer el archivo PKCS#12: " + pkcs12Path);
            }

            String extension = getFileExtension(pkcs12Path).toLowerCase();
            if (!(extension.equals("p12") || extension.equals("pfx"))) {
                return new ValidationResult(false,
                        "El archivo PKCS#12 debe tener extensión .p12 o .pfx: " + pkcs12Path);
            }

            if (password == null || password.trim().isEmpty()) {
                return new ValidationResult(false, "La contraseña del archivo PKCS#12 es requerida");
            }

            return new ValidationResult(true, null);
        } catch (SecurityException e) {
            return new ValidationResult(false,
                    "Error de seguridad al acceder al archivo PKCS#12: " + e.getMessage());
        } catch (Exception e) {
            return new ValidationResult(false,
                    "Error inesperado al validar archivo PKCS#12: " + e.getMessage());
        }
    }

    public static ValidationResult validateXML(String xmlPath, String xsdPath) {
        try {
            if (xmlPath == null || xmlPath.trim().isEmpty()) {
                return new ValidationResult(false, "La ruta del archivo XML es requerida");
            }

            File xml = new File(xmlPath);
            if (!xml.exists()) {
                return new ValidationResult(false, "El archivo XML no existe: " + xmlPath);
            }
            if (!xml.isFile()) {
                return new ValidationResult(false, "La ruta especificada no es un archivo: " + xmlPath);
            }
            if (!xml.canRead()) {
                return new ValidationResult(false, "No se puede leer el archivo XML: " + xmlPath);
            }

            String xmlExtension = getFileExtension(xmlPath).toLowerCase();
            if (!xmlExtension.equals("xml")) {
                return new ValidationResult(false,
                        "El archivo debe tener extensión .xml: " + xmlPath);
            }

            if (xsdPath != null && !xsdPath.trim().isEmpty()) {
                File xsd = new File(xsdPath);
                if (!xsd.exists()) {
                    return new ValidationResult(false, "El archivo XSD no existe: " + xsdPath);
                }
                if (!xsd.isFile()) {
                    return new ValidationResult(false, "La ruta del XSD no es un archivo: " + xsdPath);
                }
                if (!xsd.canRead()) {
                    return new ValidationResult(false, "No se puede leer el archivo XSD: " + xsdPath);
                }

                String xsdExtension = getFileExtension(xsdPath).toLowerCase();
                if (!xsdExtension.equals("xsd")) {
                    return new ValidationResult(false,
                            "El archivo de esquema debe tener extensión .xsd: " + xsdPath);
                }
            }

            return new ValidationResult(true, null);
        } catch (SecurityException e) {
            return new ValidationResult(false,
                    "Error de seguridad al acceder a los archivos: " + e.getMessage());
        } catch (Exception e) {
            return new ValidationResult(false,
                    "Error inesperado al validar archivos XML/XSD: " + e.getMessage());
        }
    }

    public static ValidationResult validatePDF(String pdfPath, String xPos, String yPos) {
        try {
            if (pdfPath == null || pdfPath.trim().isEmpty()) {
                return new ValidationResult(false, "La ruta del archivo PDF es requerida");
            }

            File pdf = new File(pdfPath);
            if (!pdf.exists()) {
                return new ValidationResult(false, "El archivo PDF no existe: " + pdfPath);
            }
            if (!pdf.isFile()) {
                return new ValidationResult(false, "La ruta especificada no es un archivo: " + pdfPath);
            }
            if (!pdf.canRead()) {
                return new ValidationResult(false, "No se puede leer el archivo PDF: " + pdfPath);
            }

            String extension = getFileExtension(pdfPath).toLowerCase();
            if (!extension.equals("pdf")) {
                return new ValidationResult(false,
                        "El archivo debe tener extensión .pdf: " + pdfPath);
            }

            if (xPos != null && !xPos.trim().isEmpty()) {
                try {
                    float x = Float.parseFloat(xPos);
                    if (x < 0) {
                        return new ValidationResult(false,
                                "La posición X debe ser un valor positivo: " + x);
                    }
                } catch (NumberFormatException e) {
                    return new ValidationResult(false,
                            "La posición X debe ser un valor numérico válido: " + xPos);
                }
            }

            if (yPos != null && !yPos.trim().isEmpty()) {
                try {
                    float y = Float.parseFloat(yPos);
                    if (y < 0) {
                        return new ValidationResult(false,
                                "La posición Y debe ser un valor positivo: " + y);
                    }
                } catch (NumberFormatException e) {
                    return new ValidationResult(false,
                            "La posición Y debe ser un valor numérico válido: " + yPos);
                }
            }

            return new ValidationResult(true, null);
        } catch (SecurityException e) {
            return new ValidationResult(false,
                    "Error de seguridad al acceder al archivo PDF: " + e.getMessage());
        } catch (Exception e) {
            return new ValidationResult(false,
                    "Error inesperado al validar archivo PDF: " + e.getMessage());
        }
    }

    private static String getFileExtension(String filePath) {
        if (filePath == null || filePath.lastIndexOf('.') == -1) {
            return "";
        }
        return filePath.substring(filePath.lastIndexOf('.') + 1);
    }

    public static boolean confirmOverwrite(String filePath) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Este método debe ser llamado desde el hilo de JavaFX");
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("Confirmar Sobrescritura");
        alert.setHeaderText("El archivo ya existe");
        alert.setContentText("¿Desea sobrescribir el archivo?\n" + filePath);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}