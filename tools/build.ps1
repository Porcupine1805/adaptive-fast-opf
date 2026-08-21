param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BuildRoot = Join-Path $RepoRoot "build/classes"
$BenchmarkOut = Join-Path $BuildRoot "benchmark"

if ($Clean -and (Test-Path -LiteralPath $BuildRoot)) {
    Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $BenchmarkOut | Out-Null

$BenchmarkSources = @(
    (Join-Path $RepoRoot "src/benchmark/java/OPF_Miner_Original.java"),
    (Join-Path $RepoRoot "src/benchmark/java/FOMAblationFlags.java"),
    (Join-Path $RepoRoot "src/benchmark/java/HJOPF.java")
)
foreach ($Source in $BenchmarkSources) {
    if (-not (Test-Path -LiteralPath $Source)) { throw "Missing Java source: $Source" }
}

& javac -encoding UTF-8 -d $BenchmarkOut $BenchmarkSources
if ($LASTEXITCODE -ne 0) { throw "Benchmark Java compilation failed." }

Write-Host "Build complete"
Write-Host "  benchmark classes: $BenchmarkOut"
Write-Host "  Entry points: OPF_Miner_Original | HJOPF (recommended) | FOMAblationFlags"
