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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UpdateServiceTest {

    private static final String RELEASE_CON_PAQUETE = """
            {
              "url": "https://api.github.com/repos/Grupo-Sauken-S-A/S-FIDE/releases/1",
              "html_url": "https://github.com/Grupo-Sauken-S-A/S-FIDE/releases/tag/v1.5.0",
              "tag_name": "v1.5.0",
              "name": "S-FiDE v1.5.0",
              "draft": false,
              "prerelease": false,
              "body": "## Novedades\\n- Cambio con acentos: \\u00f1and\\u00fa\\n- otro",
              "assets": [
                {"name": "S-FiDE-1.5.0-windows.zip", "size": 381000000,
                 "browser_download_url": "https://github.com/Grupo-Sauken-S-A/S-FIDE/releases/download/v1.5.0/S-FiDE-1.5.0-windows.zip"},
                {"name": "S-FiDE-1.5.0-actualizacion.zip", "size": 12345678,
                 "browser_download_url": "https://github.com/Grupo-Sauken-S-A/S-FIDE/releases/download/v1.5.0/S-FiDE-1.5.0-actualizacion.zip"},
                {"name": "S-FiDE-1.5.0-actualizacion.zip.sha256", "size": 100,
                 "browser_download_url": "https://github.com/Grupo-Sauken-S-A/S-FIDE/releases/download/v1.5.0/S-FiDE-1.5.0-actualizacion.zip.sha256"}
              ]
            }
            """;

    @Test
    void interpretaUnaPublicacionConPaqueteDeActualizacion() {
        UpdateService.ReleaseInfo r = UpdateService.parseRelease(RELEASE_CON_PAQUETE);

        assertEquals("1.5.0", r.versionText());
        assertTrue(r.hasUpdatePackage());
        assertEquals("S-FiDE-1.5.0-actualizacion.zip", r.updatePackage().name());
        assertEquals(12345678L, r.updatePackage().size());
        assertEquals("S-FiDE-1.5.0-actualizacion.zip.sha256", r.checksum().name());
        assertTrue(r.notes().contains("ñandú"), "los escapes \\u deben decodificarse");
        assertTrue(r.pageUrl().endsWith("/tag/v1.5.0"));
    }

    @Test
    void sinPaqueteDeActualizacionSeInformaPeroNoSeOfreceInstalar() {
        String json = """
                {"tag_name": "v1.5.0", "assets": [
                  {"name": "S-FiDE-1.5.0-windows.zip", "size": 1, "browser_download_url": "https://github.com/x"}]}
                """;
        UpdateService.ReleaseInfo r = UpdateService.parseRelease(json);
        assertFalse(r.hasUpdatePackage());
        assertNull(r.updatePackage());
        assertEquals("S-FiDE 1.5.0", r.name());
    }

    @Test
    void elPaqueteDeOtraVersionNoSeConfundeConElDeEstaPublicacion() {
        String json = """
                {"tag_name": "v1.5.0", "assets": [
                  {"name": "S-FiDE-1.4.0-actualizacion.zip", "size": 1, "browser_download_url": "https://github.com/a"},
                  {"name": "S-FiDE-1.4.0-actualizacion.zip.sha256", "size": 1, "browser_download_url": "https://github.com/b"}]}
                """;
        assertFalse(UpdateService.parseRelease(json).hasUpdatePackage());
    }

    @Test
    void unaEtiquetaQueNoEsUnaVersionSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> UpdateService.parseRelease("{\"tag_name\": \"nightly\"}"));
        assertThrows(IllegalArgumentException.class, () -> UpdateService.parseRelease("{}"));
        assertThrows(IllegalArgumentException.class, () -> UpdateService.parseRelease("no es json"));
    }

    @Test
    void soloSePermiteHttpsHaciaGitHub() {
        assertTrue(UpdateService.isAllowedUrl("https://api.github.com/repos/x/y/releases/latest"));
        assertTrue(UpdateService.isAllowedUrl("https://github.com/Grupo-Sauken-S-A/S-FIDE/releases/download/v1/x.zip"));
        assertTrue(UpdateService.isAllowedUrl("https://objects.githubusercontent.com/github-production-release-asset/x"));
        assertTrue(UpdateService.isAllowedUrl("https://release-assets.githubusercontent.com/x"));

        assertFalse(UpdateService.isAllowedUrl("http://github.com/x"), "sin cifrado");
        assertFalse(UpdateService.isAllowedUrl("https://evil.com/github.com"));
        assertFalse(UpdateService.isAllowedUrl("https://github.com.evil.com/x"));
        assertFalse(UpdateService.isAllowedUrl("https://notgithub.com/x"));
        assertFalse(UpdateService.isAllowedUrl("https://evilgithubusercontent.com/x"));
        assertFalse(UpdateService.isAllowedUrl("file:///C:/x"));
        assertFalse(UpdateService.isAllowedUrl("no es una url"));
    }

    @Test
    void leeElSha256DelArchivoPublicado() throws Exception {
        String hash = "a".repeat(64);
        assertEquals(hash, UpdateService.parseExpectedSha256(hash + "  S-FiDE-1.5.0-actualizacion.zip\n"));
        assertEquals(hash, UpdateService.parseExpectedSha256(hash.toUpperCase()));
        assertThrows(UpdateService.UpdateCheckException.class, () -> UpdateService.parseExpectedSha256("zzz"));
        assertThrows(UpdateService.UpdateCheckException.class, () -> UpdateService.parseExpectedSha256(""));
    }

    @Test
    void miniJsonLeeTiposYRechazaLoMalFormado() {
        Object v = MiniJson.parse("{\"a\": [1, 2.5, true, null, \"x\"], \"b\": {\"c\": \"d\"}}");
        Map<String, Object> o = MiniJson.asObject(v);
        List<Object> a = MiniJson.asArray(o.get("a"));
        assertEquals(5, a.size());
        assertEquals(2.5, a.get(1));
        assertEquals(Boolean.TRUE, a.get(2));
        assertNull(a.get(3));
        assertNotNull(MiniJson.asObject(o.get("b")));

        for (String malo : List.of("{", "[1,]", "{\"a\" 1}", "{\"a\": }", "\"sin cerrar", "{} x", "[01a]", "")) {
            assertThrows(IllegalArgumentException.class, () -> MiniJson.parse(malo), malo);
        }
    }

    @Test
    void comparaVersionesSegunSemVer() {
        VersionNumber v140 = VersionNumber.parse("1.4.0").orElseThrow();
        assertTrue(VersionNumber.parse("v1.4.1").orElseThrow().isNewerThan(v140));
        assertTrue(VersionNumber.parse("1.10.0").orElseThrow().isNewerThan(VersionNumber.parse("1.9.9").orElseThrow()),
                "1.10.0 es posterior a 1.9.9 (comparación numérica, no de texto)");
        assertTrue(VersionNumber.parse("2.0.0").orElseThrow().isNewerThan(v140));
        assertFalse(v140.isNewerThan(v140));
        assertTrue(v140.isNewerThan(VersionNumber.parse("1.4.0-beta.2").orElseThrow()),
                "la versión final es posterior a su pre-publicación");
        assertTrue(VersionNumber.parse("1.4.0-beta.10").orElseThrow().isNewerThan(VersionNumber.parse("1.4.0-beta.9").orElseThrow()));
        assertTrue(VersionNumber.parse("1.4.0-rc.1").orElseThrow().isNewerThan(VersionNumber.parse("1.4.0-beta.9").orElseThrow()));
        assertEquals("1.4.0-beta.1", VersionNumber.parse("v1.4.0-beta.1").orElseThrow().toString());
        assertTrue(VersionNumber.parse("latest").isEmpty());
        assertTrue(VersionNumber.parse("1.4").isEmpty());
        assertTrue(VersionNumber.parse(null).isEmpty());
    }
}
