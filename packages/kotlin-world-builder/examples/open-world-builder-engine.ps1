param(
    [Parameter(Mandatory = $true)][string]$World,
    [string]$Spec,
    [string]$Presentation,
    [string]$Plan,
    [ValidateSet("light", "dark")][string]$Theme,
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
$themeArgs = if ($Theme) { " --theme $Theme" } else { "" }
$planArgs = if ($Plan) { " --plan " + (Resolve-Path $Plan).Path } else { "" }

Push-Location $repoRoot
try {
    & .\gradlew.bat :kotlin-world-builder:worldBuilder "--args=--world $worldFile --spec $specFile --presentation $presentationFile --width $Width --height $Height$themeArgs$planArgs"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
