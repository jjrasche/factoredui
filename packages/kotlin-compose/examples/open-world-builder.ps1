param(
    [string]$Data,
    [string]$Spec,
    [int]$Width = 1500,
    [int]$Height = 900,
    [ValidateSet("light", "dark")][string]$Theme
)

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Data) { $Data = Join-Path $here "world-builder.data.json" }
if (-not $Spec) { $Spec = Join-Path $here "world-builder.spec.json" }

$repoRoot = (Resolve-Path (Join-Path $here "..\..\..")).Path
$specFile = (Resolve-Path $Spec).Path
$dataFile = (Resolve-Path $Data).Path
$themeArgs = if ($Theme) { " --theme $Theme" } else { "" }

Push-Location $repoRoot
try {
    & .\gradlew.bat :kotlin-compose:specWindow "--args=$specFile $Width $Height --data $dataFile$themeArgs"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
