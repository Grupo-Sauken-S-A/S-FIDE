# ============================================================
#  crear-distribucion-macos.ps1  (Windows)
#
#  Arma los paquetes completos de S-FiDE para macOS, uno por procesador
#  (Apple Silicon e Intel): S-FiDE-<version>-macos-<procesador>.tar.gz mas su
#  .sha256. Es un .tar.gz y no un .zip porque tar conserva los permisos de
#  ejecucion que necesita el Java embebido.
#
#  Hace falta la carpeta de distribucion que arma install.bat y la carpeta
#  "macos" del vendor con los originales de OpenJDK (Oracle) y JavaFX para macOS.
#  Un JDK completo (JAVA_HOME) corre la herramienta.
#
#  Uso: .\crear-distribucion-macos.ps1 -Distribucion <carpeta> -Vendor <carpeta> [-Salida <carpeta>] [-Version x.y.z]
#  (solo ASCII a proposito: Windows PowerShell 5.1 lee un .ps1 sin BOM como ANSI)
# ============================================================
param(
    [Parameter(Mandatory = $true)][string]$Distribucion,
    [Parameter(Mandatory = $true)][string]$Vendor,
    [string]$Salida,
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Salida) { $Salida = Join-Path $repo 'dist-macos' }
if (-not $Version) {
    [xml]$pom = Get-Content (Join-Path $repo 'pom.xml')
    $Version = $pom.project.version
}
if (-not (Test-Path $Distribucion)) { throw "No existe la carpeta de distribucion: $Distribucion" }
$mac = Join-Path $Vendor 'macos'
if (-not (Test-Path $mac)) { throw "No existe $mac (originales de OpenJDK y JavaFX para macOS)." }

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
New-Item -ItemType Directory -Force $Salida | Out-Null

foreach ($arq in 'aarch64', 'x64') {
    $jdk = Join-Path $mac "openjdk-23.0.1_macos-${arq}_bin.tar.gz"
    $fx = Join-Path $mac "openjfx-23.0.1_osx-${arq}_bin-sdk.zip"
    if (-not (Test-Path $jdk)) { throw "Falta $jdk" }
    if (-not (Test-Path $fx)) { throw "Falta $fx" }
    Write-Host "== macos-$arq"
    & $java (Join-Path $repo 'herramientas\CrearDistribucionMacOS.java') --dist $Distribucion --jdk $jdk --javafx $fx `
        --plataforma "macos-$arq" --version $Version --salida $Salida
    if ($LASTEXITCODE -ne 0) { throw "La herramienta fallo para macos-$arq." }
}
Write-Host ""
Write-Host "Listo. Adjunte los .tar.gz y sus .sha256 al GitHub Release v$Version."
