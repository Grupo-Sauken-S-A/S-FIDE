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
import javafx.stage.Modality;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
        JAR_MAPPINGS.put("XMLSignerWindowsCSP", "XMLSignerWindowsCSP.jar");
        JAR_MAPPINGS.put("PDFSignerWindowsCSP", "PDFSignerWindowsCSP.jar");
        JAR_MAPPINGS.put("WindowsCertificateStoreView", "WindowsCertificateStoreView.jar");
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
        com.sauken.s_fide.s_fide_gui.utils.DialogOwner.conPropietario(alert).showAndWait();
    }
}
