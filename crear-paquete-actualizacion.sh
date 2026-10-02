#!/bin/sh
# ============================================================
#  crear-paquete-actualizacion.sh  (Linux / macOS)
#
#  Equivalente de crear-paquete-actualizacion.ps1: arma el paquete que usa
#  "Ayuda > Buscar actualizaciones" de S-FiDE:
#    S-FiDE-<version>-actualizacion.zip         (jars, lanzadores, documentacion; SIN runtime de Java/JavaFX)
#    S-FiDE-<version>-actualizacion.zip.sha256  (hash SHA-256 del zip, formato sha256sum)
#  Ambos archivos deben adjuntarse al GitHub Release de esa version.
#
#  Requisitos: haber corrido "./mvnw clean install" en la raiz del proyecto;
#  JAVA_HOME apuntando a un JDK completo (herramienta "jar") o el comando "zip".
#  Permisos: el lanzador SFide-GUI.sh se arma con permiso de ejecucion (755) y
#  el resto con 644; si se usa "zip" esos permisos viajan en el paquete. Aun asi,
#  el actualizador (SFideUpdater) vuelve a garantizar el permiso de ejecucion de
#  todo .sh al instalarlo, porque no todos los programas de zip conservan permisos.
#
#  Uso:  ./crear-paquete-actualizacion.sh [version] [carpeta_salida]
#  Sin version toma la del pom.xml raiz. Sin carpeta de salida usa ./dist-update
# ============================================================
set -eu

REPO="$(cd "$(dirname "$0")" && pwd)"
VERSION="${1:-}"
SALIDA="${2:-$REPO/dist-update}"
RUNTIME_JAVA="${RUNTIME_JAVA:-openjdk-23.0.1}"
RUNTIME_JAVAFX="${RUNTIME_JAVAFX:-javafx-sdk-23.0.1}"

if [ -z "$VERSION" ]; then
    VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$REPO/pom.xml" | head -1)"
fi
[ -n "$VERSION" ] || { echo "No se pudo determinar la version (indiquela como primer argumento)." >&2; exit 1; }

if command -v sha256sum >/dev/null 2>&1; then
    sha256() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
    sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
    echo "Hace falta sha256sum o shasum." >&2; exit 1
fi

mkdir -p "$SALIDA"
SALIDA="$(cd "$SALIDA" && pwd)"
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/sfide-update-XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT

echo "Armando la distribucion de $VERSION en $STAGE ..."

# Mismo mapeo modulo -> nombre de jar "amigable" (sin version) que install.bat
copiar_jar() {
    modulo="$1"; destino="$2"
    origen="$(ls "$REPO/$modulo/target/$modulo"-*-jar-with-dependencies.jar 2>/dev/null | head -1 || true)"
    [ -n "$origen" ] || { echo "Falta el jar de $modulo: corrio './mvnw clean install'?" >&2; exit 1; }
    cp "$origen" "$STAGE/$destino"
}
copiar_jar pkcs12_certificate_extractor PKCS12CertificateExtractor.jar
copiar_jar token_certificate_extractor TokenCertificateExtractor.jar
copiar_jar token_slots_view TokenSlotsView.jar
copiar_jar xml_signer_pkcs11 XMLSignerPKCS11.jar
copiar_jar xml_signer_pkcs12 XMLSignerPKCS12.jar
copiar_jar xml_verify_signatures XMLVerifySignatures.jar
copiar_jar xml_verify_xsd_structure XMLVerifyXSDStructure.jar
copiar_jar pdf_signer_pkcs11 PDFSignerPKCS11.jar
copiar_jar pdf_signer_pkcs12 PDFSignerPKCS12.jar
copiar_jar pdf_verify_signatures PDFVerifySignatures.jar
copiar_jar xml_signer_windows_csp XMLSignerWindowsCSP.jar
copiar_jar pdf_signer_windows_csp PDFSignerWindowsCSP.jar
copiar_jar windows_certificate_store_view WindowsCertificateStoreView.jar
copiar_jar s_fide_updater SFideUpdater.jar

# La GUI: el artefacto con dependencias se llama s_fide_gui-<version>.jar
GUI="$REPO/s_fide_gui/target/s_fide_gui-$VERSION.jar"
[ -f "$GUI" ] || GUI="$(ls "$REPO"/s_fide_gui/target/s_fide_gui-*.jar | grep -v original | head -1)"
cp "$GUI" "$STAGE/SFide-GUI.jar"

cp "$REPO/SFide-GUI.bat" "$REPO/SFide-GUI.sh" "$REPO/sfide-defaults.demo.properties" \
   "$REPO/Leeme.txt" "$REPO/LICENSE" "$STAGE/"
cp "$REPO/s_fide_gui/src/main/resources/images/sfide-icon.ico" "$STAGE/S-FiDE.ico"
cp -R "$REPO/doc" "$STAGE/doc"

# Permisos: lanzador ejecutable, todo lo demas de solo lectura/escritura del dueno
find "$STAGE" -type d -exec chmod 755 {} +
find "$STAGE" -type f -exec chmod 644 {} +
chmod 755 "$STAGE/SFide-GUI.sh"

JARS="$(ls "$STAGE"/*.jar | wc -l | tr -d ' ')"
[ "$JARS" -ge 15 ] || { echo "Se esperaban al menos 15 jars (14 modulos + SFideUpdater) y hay $JARS." >&2; exit 1; }

# Manifiesto: version y que runtimes embebidos exige esta version
printf 'version=%s\nrequires.java=%s\nrequires.javafx=%s\n' "$VERSION" "$RUNTIME_JAVA" "$RUNTIME_JAVAFX" \
    > "$STAGE/update-manifest.properties"

# SHA-256 de cada archivo instalable (formato sha256sum, rutas con "/")
: > "$STAGE/update-files.sha256"
( cd "$STAGE" && find . -type f ! -name update-manifest.properties ! -name update-files.sha256 | sed 's#^\./##' | LC_ALL=C sort ) |
while IFS= read -r rel; do
    printf '%s  %s\n' "$(sha256 "$STAGE/$rel")" "$rel" >> "$STAGE/update-files.sha256"
done
chmod 644 "$STAGE/update-manifest.properties" "$STAGE/update-files.sha256"

ZIP="$SALIDA/S-FiDE-$VERSION-actualizacion.zip"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
    # -X: sin atributos extra de fecha/uid; conserva los permisos Unix (755 del .sh)
    ( cd "$STAGE" && zip -q -X -r "$ZIP" . )
else
    JAR="jar"
    [ -n "${JAVA_HOME:-}" ] && JAR="$JAVA_HOME/bin/jar"
    command -v "$JAR" >/dev/null 2>&1 || [ -x "$JAR" ] || { echo "Hace falta el comando zip o un JDK (jar)." >&2; exit 1; }
    "$JAR" --create --file "$ZIP" --no-manifest -C "$STAGE" .
    echo "Aviso: se uso 'jar' (no conserva permisos Unix); SFideUpdater garantiza el permiso de ejecucion de los .sh al instalar."
fi

printf '%s  S-FiDE-%s-actualizacion.zip\n' "$(sha256 "$ZIP")" "$VERSION" > "$ZIP.sha256"

TAM="$(du -h "$ZIP" | cut -f1)"
echo ""
echo "Listo:"
echo "  $ZIP  ($TAM)"
echo "  $ZIP.sha256"
echo "Adjunte AMBOS archivos al GitHub Release v$VERSION."
