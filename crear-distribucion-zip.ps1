# ============================================================
#  crear-distribucion-zip.ps1  (Windows)
#
#  Arma los ZIP completos de S-FiDE para Windows y para Linux a partir de la
#  carpeta de distribucion que genera install.bat con los runtimes embebidos
#  (VENDOR): S-FiDE-<version>-windows.zip y S-FiDE-<version>-linux.zip. Cada
#  uno lleva solo el runtime de su plataforma, y del JDK se omiten jmods,
#  include y lib\src.zip (solo sirven para compilar, no para ejecutar). Se
#  conserva la carpeta "legal" con los avisos de licencia.
#  Los paquetes de macOS se arman con crear-distribucion-macos.ps1.
#
#  Uso: .\crear-distribucion-zip.ps1 -Distribucion <carpeta> [-Salida <carpeta>] [-Version x.y.z]
#  Hace falta un JDK completo en JAVA_HOME (usa su herramienta "jar" para armar el zip).
#  (solo ASCII a proposito: Windows PowerShell 5.1 lee un .ps1 sin BOM como ANSI)
# ============================================================
param(
    [Parameter(Mandatory = $true)][string]$Distribucion,
    [string]$Salida,
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Salida) { $Salida = Join-Path $repo 'dist-release' }
if (-not $Version) {
    [xml]$pom = Get-Content (Join-Path $repo 'pom.xml')
    $Version = $pom.project.version
}
if (-not (Test-Path $Distribucion)) { throw "No existe la carpeta de distribucion: $Distribucion" }
$jar = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jar.exe' } else { 'jar' }
if (($env:JAVA_HOME) -and -not (Test-Path $jar)) { throw "No se encuentra jar.exe en JAVA_HOME ($env:JAVA_HOME). Se necesita un JDK completo." }
New-Item -ItemType Directory -Force $Salida | Out-Null

foreach ($p in @(@{ n = 'windows'; otra = 'linux-x64' }, @{ n = 'linux'; otra = 'windows-x64' })) {
    $stage = Join-Path ([IO.Path]::GetTempPath()) ('sfide-zip-' + $p.n)
    if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    $propio = if ($p.n -eq 'windows') { 'windows-x64' } else { 'linux-x64' }
    $xd = @(
        (Join-Path $Distribucion ('openjdk-23.0.1\' + $p.otra)),
        (Join-Path $Distribucion ('javafx-sdk-23.0.1\' + $p.otra)),
        (Join-Path $Distribucion 'logs'),
        (Join-Path $Distribucion ('openjdk-23.0.1\' + $propio + '\jmods')),
        (Join-Path $Distribucion ('openjdk-23.0.1\' + $propio + '\include'))
    )
    robocopy $Distribucion $stage /E /XD $xd /XF sfide-defaults.properties '*.lock' 'hs_err*' src.zip /NFL /NDL /NJH /NJS /NP | Out-Null
    # src.zip de javafx (si lo hubiera) tampoco hace falta
    $zip = Join-Path $Salida ('S-FiDE-' + $Version + '-' + $p.n + '.zip')
    if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
    & $jar --create --file $zip --no-manifest -C $stage .
    if ($LASTEXITCODE -ne 0) { throw ('jar.exe fallo al armar el zip de ' + $p.n) }
    Remove-Item -LiteralPath $stage -Recurse -Force
    $hash = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
    [IO.File]::WriteAllText("$zip.sha256", "$hash  S-FiDE-$Version-$($p.n).zip`n", (New-Object System.Text.UTF8Encoding($false)))
    '{0}: {1} MB  SHA-256 {2}' -f $zip, [math]::Round((Get-Item $zip).Length / 1MB, 1), $hash
}
Write-Host ''
Write-Host "Listo. Adjunte los .zip y sus .sha256 al GitHub Release v$Version."
