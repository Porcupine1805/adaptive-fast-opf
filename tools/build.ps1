param(
    [switch]$Clean,
    [switch]$IncludeLegacy
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BuildRoot = Join-Path $RepoRoot "build/classes"
$BenchmarkOut = Join-Path $BuildRoot "benchmark"
$Rq6Out = Join-Path $BuildRoot "rq6"
$LegacyOut = Join-Path $BuildRoot "legacy"

if ($Clean -and (Test-Path -LiteralPath $BuildRoot)) {
    Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $BenchmarkOut,$Rq6Out | Out-Null

$BenchmarkNames = @(
    "OPF_Miner_Original.java",
    "FOMAblationFlags.java",
    "FOM.java",
    "GenerateFOMHypothesisDatasets.java"
)
$BenchmarkSources = @($BenchmarkNames | ForEach-Object { Join-Path $RepoRoot "src/benchmark/java/$_" })
$Rq6Sources = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "src/rq6/java") -Filter "*.java" | Select-Object -ExpandProperty FullName)
foreach ($Source in $BenchmarkSources) {
    if (-not (Test-Path -LiteralPath $Source)) { throw "Missing Java source: $Source" }
}
if ($Rq6Sources.Count -eq 0) { throw "RQ6 Java source discovery failed." }

& javac -encoding UTF-8 -d $BenchmarkOut $BenchmarkSources
if ($LASTEXITCODE -ne 0) { throw "Benchmark Java compilation failed." }
& javac -encoding UTF-8 -d $Rq6Out $Rq6Sources
if ($LASTEXITCODE -ne 0) { throw "RQ6 Java compilation failed." }

if ($IncludeLegacy) {
    New-Item -ItemType Directory -Force -Path $LegacyOut | Out-Null
    $LegacySources = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "src/benchmark/java/legacy") -Filter "*.java" | Select-Object -ExpandProperty FullName)
    & javac -encoding UTF-8 -d $LegacyOut $LegacySources
    if ($LASTEXITCODE -ne 0) { throw "Legacy Java compilation failed." }
}

Write-Host "Build complete"
Write-Host "  benchmark classes: $BenchmarkOut"
Write-Host "  RQ6 classes:       $Rq6Out"
if ($IncludeLegacy) { Write-Host "  legacy classes:    $LegacyOut" }
