param(
    [string]$Data = (Join-Path $PSScriptRoot "world-builder.data.json"),
    [string]$Spec = (Join-Path $PSScriptRoot "world-builder.spec.json"),
    [int]$Width = 1500,
    [int]$Height = 900
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$specFile = (Resolve-Path $Spec).Path
$dataFile = (Resolve-Path $Data).Path

Push-Location $repoRoot
try {
    & .\gradlew.bat :kotlin-compose:specWindow "--args=$specFile $Width $Height --data $dataFile"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
