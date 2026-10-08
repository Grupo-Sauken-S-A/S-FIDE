#! /bin/sh

# Carpeta donde vive S-FiDE: se detecta automaticamente a partir de la
# ubicacion de este mismo archivo, para que funcione sin editar nada sin
# importar donde este montado (por ejemplo, al correr desde un pendrive).
SFIDE="$(cd "$(dirname "$0")" && pwd)"
cd "$SFIDE" || exit 1

# Opciones de Java propias de cada sistema (vacio salvo en macOS).
SISTEMA_OPCIONES=""

case "$(uname -s)" in
    Darwin)
        # En macOS hay un runtime por procesador: Apple Silicon (arm64) o Intel (x86_64).
        # Un Mac con Apple Silicon que abre la terminal con Rosetta informa x86_64 y usa
        # el runtime de Intel, que tambien funciona.
        case "$(uname -m)" in
            arm64|aarch64) PLATFORM=macos-aarch64 ;;
            *)             PLATFORM=macos-x64 ;;
        esac
        # Al descargar el ZIP con el navegador, macOS marca todo lo que contiene como "de
        # Internet" (cuarentena) y Gatekeeper bloquea el Java embebido la primera vez
        # ("no se puede abrir porque no se puede verificar al desarrollador"). Se quita la
        # marca solo de esta carpeta: es la de S-FiDE, no se toca nada fuera de ella.
        xattr -dr com.apple.quarantine "$SFIDE" 2>/dev/null || true
        # Nombre de la aplicacion en la barra de menu y en el Dock.
        SISTEMA_OPCIONES="-Xdock:name=S-FiDE"
        ;;
    *)
        PLATFORM=linux-x64
        ;;
esac
JAVA_HOME="$SFIDE/openjdk-23.0.1/$PLATFORM"
JAVA_FX="$SFIDE/javafx-sdk-23.0.1/$PLATFORM"

PATH="$JAVA_HOME/bin:$PATH"
export PATH

if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "No se encontro Java en: $JAVA_HOME"
    echo "La carpeta de S-FiDE parece incompleta o movida."
    exit 1
fi

if [ ! -d "$JAVA_FX/lib" ]; then
    echo "No se encontro JavaFX en: $JAVA_FX"
    echo "La carpeta de S-FiDE parece incompleta o movida."
    exit 1
fi

if [ ! -f "$SFIDE/SFide-GUI.jar" ]; then
    echo "No se encontro SFide-GUI.jar en $SFIDE"
    exit 1
fi

# SISTEMA_OPCIONES va sin comillas a proposito: puede estar vacio o llevar mas de una opcion.
# shellcheck disable=SC2086
"$JAVA_HOME/bin/java" $SISTEMA_OPCIONES --module-path "$JAVA_FX/lib" --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "$SFIDE/SFide-GUI.jar"
