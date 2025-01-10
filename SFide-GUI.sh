#! /bin/sh

cd /opt/S-FiDE

JAVA_HOME=/opt/S-FiDE/openjdk-23.0.1/linux-x64
JAVA_FX=/opt/S-FiDE/javafx-sdk-23.0.1/linux-x64

PATH=$JAVA_HOME/bin:$PATH

$JAVA_HOME/bin/java --module-path $JAVA_FX/lib --add-modules javafx.controls,javafx.fxml -jar SFide-GUI.jar