param(
    [Parameter(Mandatory = $true)][string]$Graph,
    [int]$Width = 1800,
    [int]$Height = 1000
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$spec = Join-Path $PSScriptRoot "graph-explorer.spec.json"
$graphFile = (Resolve-Path $Graph).Path

Push-Location $repoRoot
try {
    & .\gradlew.bat :kotlin-compose:specWindow "--args=$spec $Width $Height --data $graphFile"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
