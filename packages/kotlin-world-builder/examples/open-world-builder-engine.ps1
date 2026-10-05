param(
    [Parameter(Mandatory = $true)][string]$World,
    [string]$Spec = (Join-Path $PSScriptRoot "world-builder-engine.spec.json"),
    [string]$Presentation = (Join-Path $PSScriptRoot "parcel.presentation.json"),
    [ValidateSet("light", "dark")][string]$Theme = "light",
    [int]$Width = 1500,
    [int]$Height = 900
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
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
