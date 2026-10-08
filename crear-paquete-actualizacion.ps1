# ============================================================
#  crear-paquete-actualizacion.ps1
#
#  Arma el paquete que usa "Ayuda > Buscar actualizaciones" de S-FiDE:
#    S-FiDE-<version>-actualizacion.zip         (jars, lanzadores, documentacion; SIN runtime de Java/JavaFX)
#    S-FiDE-<version>-actualizacion.zip.sha256  (hash SHA-256 del zip, formato sha256sum)
#  Ambos archivos deben adjuntarse al GitHub Release de esa version
#  (junto con las distribuciones completas por plataforma).
#
#  Requisitos: correr antes "mvnw clean install" en la raiz del proyecto,
#  y tener JAVA_HOME apuntando a un JDK (se usa su herramienta "jar" para
#  armar el zip: genera rutas con "/" como exigen los programas de
#  descompresion de todas las plataformas, a diferencia de Compress-Archive
#  de Windows PowerShell 5.1, que usa "\").
#
#  Uso:  powershell -ExecutionPolicy Bypass -File crear-paquete-actualizacion.ps1 [-Version 1.5.0] [-Salida C:\carpeta]
#  Sin -Version toma la version del pom.xml raiz. Sin -Salida deja los
#  archivos en la carpeta "dist-update" del repositorio.
# ============================================================
param(
    [string]$Version,
    [string]$Salida,
    [string]$RuntimeJava = "openjdk-23.0.1",
    [string]$RuntimeJavaFx = "javafx-sdk-23.0.1"
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $Version) {
    [xml]$pom = Get-Content (Join-Path $repo 'pom.xml')
    $Version = $pom.project.version
}
if (-not $Salida) { $Salida = Join-Path $repo 'dist-update' }
New-Item -ItemType Directory -Force $Salida | Out-Null

$jar = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jar.exe' } else { 'jar' }
if (($env:JAVA_HOME) -and -not (Test-Path $jar)) { throw "No se encuentra jar.exe en JAVA_HOME ($env:JAVA_HOME). Se necesita un JDK completo." }

$stage = Join-Path ([IO.Path]::GetTempPath()) ("sfide-update-" + $Version)
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force $stage | Out-Null

Write-Host "Armando la distribucion de $Version en $stage ..."
$env:SFIDE_NOPAUSE = '1'
& cmd.exe /c "`"$repo\install.bat`" `"$stage`"" | Out-Null
Remove-Item Env:SFIDE_NOPAUSE

# Lo que NO va en una actualizacion: runtimes y ejemplos (si install.bat los dejo ahi).
foreach ($carpeta in $RuntimeJava, $RuntimeJavaFx, 'test', 'xsd') {
    $p = Join-Path $stage $carpeta
    if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}

$jars = @(Get-ChildItem $stage -Filter *.jar)
if ($jars.Count -lt 15) { throw "Se esperaban al menos 15 jars (14 modulos + SFideUpdater) y hay $($jars.Count). Corrio 'mvnw clean install'?" }

# Manifiesto: version y que runtimes embebidos exige esta version.
$utf8 = New-Object System.Text.UTF8Encoding($false)
$manifest = "version=$Version`nrequires.java=$RuntimeJava`nrequires.javafx=$RuntimeJavaFx`n"
[IO.File]::WriteAllText((Join-Path $stage 'update-manifest.properties'), $manifest, $utf8)

# Hash SHA-256 de cada archivo instalable (formato sha256sum, rutas con "/").
$lineas = New-Object System.Collections.Generic.List[string]
$base = (Resolve-Path $stage).Path.TrimEnd('\')
foreach ($f in Get-ChildItem $stage -Recurse -File) {
    $rel = $f.FullName.Substring($base.Length + 1).Replace('\', '/')
    if ($rel -eq 'update-manifest.properties') { continue }
    $hash = (Get-FileHash -Algorithm SHA256 $f.FullName).Hash.ToLower()
    $lineas.Add("$hash  $rel")
}
[IO.File]::WriteAllText((Join-Path $stage 'update-files.sha256'), (($lineas -join "`n") + "`n"), $utf8)

$zip = Join-Path $Salida "S-FiDE-$Version-actualizacion.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
& $jar --create --file $zip --no-manifest -C $stage .
if ($LASTEXITCODE -ne 0) { throw "jar.exe fallo al armar el zip." }

$zipHash = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
[IO.File]::WriteAllText("$zip.sha256", "$zipHash  S-FiDE-$Version-actualizacion.zip`n", $utf8)

Remove-Item -Recurse -Force $stage
$mb = [Math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ""
Write-Host "Listo:"
Write-Host "  $zip  ($mb MB, $($lineas.Count) archivos)"
Write-Host "  $zip.sha256  ($zipHash)"
Write-Host "Adjunte AMBOS archivos al GitHub Release v$Version."
