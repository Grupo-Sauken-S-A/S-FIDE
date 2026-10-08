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


package com.sauken.s_fide.s_fide_gui.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseNotesTest {

    private static final String TEXTO = """
            # Novedades de S-FiDE

            ## 1.4.0 | Actualizador
            Ahora S-FiDE se actualiza solo.
            ### Nuevo
            - Buscar actualizaciones desde el menú Ayuda.
            - Datos personales en la carpeta del usuario.

            ## 1.5.0 | Firma de PDF más fácil
            ### Nuevo
            - Elegir la ubicación de la firma sobre el documento
              como en Acrobat.
            ### Mejoras
            - Mensajes más claros.

            ## 1.6.0 | Futura
            ### Nuevo
            - Algo de la 1.6.0.

            ## versión-rara | Se ignora
            - no entra
            """;

    private static List<ReleaseNotes.Entrada> todas() {
        return ReleaseNotes.parse(TEXTO);
    }

    private static List<String> versiones(List<ReleaseNotes.Entrada> entradas) {
        return entradas.stream().map(ReleaseNotes.Entrada::version).toList();
    }

    @Test
    void interpretaVersionesTitulosResumenYGrupos() {
        List<ReleaseNotes.Entrada> entradas = todas();

        assertEquals(List.of("1.4.0", "1.5.0", "1.6.0"), versiones(entradas), "La sección de versión inválida se ignora");
        ReleaseNotes.Entrada v140 = entradas.get(0);
        assertEquals("Actualizador", v140.titulo());
        assertEquals("Ahora S-FiDE se actualiza solo.", v140.resumen());
        assertEquals(2, v140.grupos().get(0).puntos().size());

        ReleaseNotes.Entrada v150 = entradas.get(1);
        assertEquals(2, v150.grupos().size());
        assertEquals("Nuevo", v150.grupos().get(0).titulo());
        assertEquals("Elegir la ubicación de la firma sobre el documento como en Acrobat.",
                v150.grupos().get(0).puntos().get(0), "Una novedad escrita en dos líneas se une");
    }

    @Test
    void quienActualizaUnaVersionVeSoloLasNovedadesDeEsaVersion() {
        List<ReleaseNotes.Entrada> mostrar = ReleaseNotes.desde(todas(), Optional.of("1.4.0"), "1.5.0");

        assertEquals(List.of("1.5.0"), versiones(mostrar));
    }

    @Test
    void quienSaltaVersionesVeLasNovedadesDeTodasLasIntermediasDeLaMasNuevaALaMasVieja() {
        List<ReleaseNotes.Entrada> mostrar = ReleaseNotes.desde(todas(), Optional.of("1.4.0"), "1.6.0");

        assertEquals(List.of("1.6.0", "1.5.0"), versiones(mostrar),
                "De 1.4.0 directo a 1.6.0 hay que mostrar también lo de 1.5.0");
    }

    @Test
    void sinSaberLaVersionAnteriorSeMuestraTodoElHistorialHastaLaActual() {
        List<ReleaseNotes.Entrada> mostrar = ReleaseNotes.desde(todas(), Optional.empty(), "1.5.0");

        assertEquals(List.of("1.5.0", "1.4.0"), versiones(mostrar), "No se muestran versiones posteriores a la instalada");
    }

    @Test
    void sinCambioDeVersionOConVersionMasViejaNoSeMuestraNada() {
        assertTrue(ReleaseNotes.desde(todas(), Optional.of("1.5.0"), "1.5.0").isEmpty());
        assertTrue(ReleaseNotes.desde(todas(), Optional.of("1.6.0"), "1.5.0").isEmpty(), "Volver a una versión vieja no avisa");
    }

    @Test
    void laComparacionEsNumericaYNoDeTexto() {
        String texto = "## 1.9.0 | a\n### Nuevo\n- a\n## 1.10.0 | b\n### Nuevo\n- b\n";
        List<ReleaseNotes.Entrada> mostrar = ReleaseNotes.desde(ReleaseNotes.parse(texto), Optional.of("1.9.0"), "1.10.0");

        assertEquals(List.of("1.10.0"), versiones(mostrar), "1.10.0 es posterior a 1.9.0");
    }

    @Test
    void unaVersionActualDesconocidaNoMuestraNada() {
        assertTrue(ReleaseNotes.desde(todas(), Optional.of("1.4.0"), "desconocida").isEmpty(),
                "Corrida desde el IDE, sin versión real");
    }

    @Test
    void unaVersionAnteriorQueNoSeEntiendeSeTrataComoDesconocida() {
        List<ReleaseNotes.Entrada> mostrar = ReleaseNotes.desde(todas(), Optional.of("rara"), "1.5.0");

        assertEquals(List.of("1.5.0", "1.4.0"), versiones(mostrar));
    }

    @Test
    void elArchivoRealDeNovedadesSeLeeYTieneLaVersionActual() throws IOException {
        try (InputStream in = ReleaseNotesTest.class.getResourceAsStream("/text/NOVEDADES.txt")) {
            assertTrue(in != null, "Falta text/NOVEDADES.txt");
            List<ReleaseNotes.Entrada> entradas = ReleaseNotes.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));

            assertFalse(entradas.isEmpty());
            assertTrue(versiones(entradas).contains("1.5.0"), "Las novedades de la 1.5.0 tienen que estar");
            for (ReleaseNotes.Entrada entrada : entradas) {
                assertFalse(entrada.grupos().isEmpty(), "La versión " + entrada.version() + " no tiene novedades");
            }
        }
    }

    @Test
    void elTextoDeLasNovedadesNoUsaJergaTecnicaNiMarcadoresDeMaven() throws IOException {
        try (InputStream in = ReleaseNotesTest.class.getResourceAsStream("/text/NOVEDADES.txt")) {
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(texto.contains("${"), "Maven filtra los recursos de texto: un ${...} se reemplazaría");
            for (String jerga : List.of("OCSP", "CRL", "PKCS#7", "DocMDP", "ByteRange", "stack trace")) {
                assertFalse(texto.contains(jerga), "Texto para personas no técnicas: " + jerga);
            }
        }
    }
}
