#! /bin/sh

# Carpeta donde vive S-FiDE: se detecta automaticamente a partir de la
# ubicacion de este mismo archivo, para que funcione sin editar nada sin
# importar donde este montado (por ejemplo, al correr desde un pendrive).
SFIDE="$(cd "$(dirname "$0")" && pwd)"
cd "$SFIDE" || exit 1

case "$(uname -s)" in
    Darwin)
        PLATFORM=macos
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

"$JAVA_HOME/bin/java" --module-path "$JAVA_FX/lib" --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "$SFIDE/SFide-GUI.jar"
