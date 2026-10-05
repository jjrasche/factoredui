param(
    [Parameter(Mandatory = $true)][string]$World,
    [string]$Spec,
    [string]$Presentation,
    [ValidateSet("light", "dark")][string]$Theme = "light",
    [int]$Width = 1500,
    [int]$Height = 900
)

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Spec) { $Spec = Join-Path $here "world-builder-engine.spec.json" }
if (-not $Presentation) { $Presentation = Join-Path $here "parcel.presentation.json" }

$repoRoot = (Resolve-Path (Join-Path $here "..\..\..")).Path
$worldFile = (Resolve-Path $World).Path
$specFile = (Resolve-Path $Spec).Path
$presentationFile = (Resolve-Path $Presentation).Path

Push-Location $repoRoot
try {
    & .\gradlew.bat :kotlin-world-builder:worldBuilder "--args=--world $worldFile --spec $specFile --presentation $presentationFile --theme $Theme --width $Width --height $Height"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
