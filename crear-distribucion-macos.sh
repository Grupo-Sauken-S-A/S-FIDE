#!/bin/sh
# ============================================================
#  crear-distribucion-macos.sh  (Linux / macOS / Git Bash en Windows)
#
#  Arma los paquetes completos de S-FiDE para macOS, uno por procesador
#  (Apple Silicon e Intel), como S-FiDE-<version>-macos-<procesador>.tar.gz
#  mas su .sha256. Es un .tar.gz y no un .zip porque tar conserva los
#  permisos de ejecucion que necesita el Java embebido.
#
#  Hace falta:
#   - la carpeta de distribucion que arma install.bat (jars, lanzadores, doc)
#   - la carpeta "macos" del vendor con los originales: OpenJDK de Oracle
#     (openjdk-23.0.1_macos-aarch64_bin.tar.gz y ..._macos-x64_bin.tar.gz) y
#     el SDK de JavaFX (openjfx-23.0.1_osx-aarch64_bin-sdk.zip y ..._osx-x64_...)
#
#  Uso: ./crear-distribucion-macos.sh <carpeta_distribucion> <carpeta_vendor> [carpeta_salida] [version]
# ============================================================
set -eu

REPO="$(cd "$(dirname "$0")" && pwd)"
DIST="${1:-}"
VENDOR="${2:-}"
SALIDA="${3:-$REPO/dist-macos}"
VERSION="${4:-}"

[ -n "$DIST" ] && [ -n "$VENDOR" ] || { echo "Uso: $0 <carpeta_distribucion> <carpeta_vendor> [carpeta_salida] [version]" >&2; exit 1; }
[ -d "$DIST" ] || { echo "No existe la carpeta de distribucion: $DIST" >&2; exit 1; }
MAC="$VENDOR/macos"
[ -d "$MAC" ] || { echo "No existe $MAC (originales de OpenJDK y JavaFX para macOS)." >&2; exit 1; }
if [ -z "$VERSION" ]; then
    VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$REPO/pom.xml" | head -1)"
fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVA_BIN" >/dev/null 2>&1 || [ -x "$JAVA_BIN" ] || { echo "Hace falta Java 21 o superior (JAVA_HOME o en el PATH)." >&2; exit 1; }

mkdir -p "$SALIDA"
for ARQ in aarch64 x64; do
    JDK="$MAC/openjdk-23.0.1_macos-${ARQ}_bin.tar.gz"
    FX="$MAC/openjfx-23.0.1_osx-${ARQ}_bin-sdk.zip"
    [ -f "$JDK" ] || { echo "Falta $JDK" >&2; exit 1; }
    [ -f "$FX" ] || { echo "Falta $FX" >&2; exit 1; }
    echo "== macos-$ARQ"
    "$JAVA_BIN" "$REPO/herramientas/CrearDistribucionMacOS.java" --dist "$DIST" --jdk "$JDK" --javafx "$FX" \
        --plataforma "macos-$ARQ" --version "$VERSION" --salida "$SALIDA"
done
echo
echo "Listo. Adjunte los .tar.gz y sus .sha256 al GitHub Release v$VERSION."
