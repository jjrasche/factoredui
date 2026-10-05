param(
    [Parameter(Mandatory = $true)][string]$Graph,
    [int]$Width = 1800,
    [int]$Height = 1000,
    [ValidateSet("light", "dark")][string]$Theme
)

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $here "..\..\..")).Path
$spec = Join-Path $here "graph-explorer.spec.json"
$graphFile = (Resolve-Path $Graph).Path
$themeArgs = if ($Theme) { " --theme $Theme" } else { "" }

Push-Location $repoRoot
try {
    & .\gradlew.bat :kotlin-compose:specWindow "--args=$spec $Width $Height --data $graphFile$themeArgs"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
