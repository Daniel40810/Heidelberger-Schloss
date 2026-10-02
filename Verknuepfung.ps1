# Legt auf dem Desktop die Verknuepfung "Heidelberg 1619" mit dem Programmsymbol an.
# Aufruf ueber Verknuepfung.bat (Doppelklick).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar  = Join-Path $root 'dist\Heidelberg.jar'
$ico  = Join-Path $root 'icon\heidelberg.ico'
if (-not (Test-Path $jar)) { throw "dist\Heidelberg.jar fehlt - zuerst in NetBeans bauen (Clean and Build)." }
if (-not (Test-Path $ico)) { throw "icon\heidelberg.ico fehlt." }

$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) {
    $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
} else {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'Heidelberg 1619.lnk'
$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath       = $javaw
$s.Arguments        = '-Xmx4g -jar "' + $jar + '"'
$s.WorkingDirectory = $root
$s.IconLocation     = $ico
$s.Description      = 'Heidelberger Schloss um 1619'
$s.Save()
Write-Host "Verknuepfung angelegt: $lnk"
