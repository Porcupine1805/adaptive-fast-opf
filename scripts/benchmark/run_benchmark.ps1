param(
    [ValidateSet("opf", "hash_only", "full", "sparse_only", "wsb_only", "hash_sparse", "hash_wsb", "sparse_wsb")]
    [string]$Algorithm = "hash_only",
    [string]$InputDir = "data/smoke",
    [string]$OutputDir = "results/runs",
    [int]$Runs = 1,
    [string]$JavaHeap = "4g"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ClassPath = Join-Path $RepoRoot "build/classes/benchmark"
$ResolvedInput = (Resolve-Path (Join-Path $RepoRoot $InputDir)).Path
$ResolvedOutput = Join-Path $RepoRoot "$OutputDir/$Algorithm"

if (-not (Test-Path -LiteralPath (Join-Path $ClassPath "FOMAblationFlags.class"))) {
    & (Join-Path $RepoRoot "tools/build.ps1")
}

New-Item -ItemType Directory -Force -Path $ResolvedOutput | Out-Null

if ($Algorithm -eq "opf") {
    $ClassName = "OPF_Miner_Original"
    $Prefix = "OPF_Miner_Original"
} else {
    $ClassName = "FOMAblationFlags"
    $Prefix = switch ($Algorithm) {
        "hash_only" { "FOMAblationHashOnly" }
        "full" { "FOMAblationFull" }
        "sparse_only" { "FOMAblationSparseOnly" }
        "wsb_only" { "FOMAblationWSBOnly" }
        "hash_sparse" { "FOMAblationHashSparse" }
        "hash_wsb" { "FOMAblationHashWSB" }
        "sparse_wsb" { "FOMAblationSparseWSB" }
    }
}

for ($Index = 1; $Index -le $Runs; $Index++) {
    $RunLabel = "{0:D2}" -f $Index
    $Csv = Join-Path $ResolvedOutput "${Prefix}_run_${RunLabel}.csv"
    $Log = Join-Path $ResolvedOutput "${Prefix}_run_${RunLabel}.log"
    $JavaArgs = @(
        "-Xmx$JavaHeap",
        "-cp", $ClassPath,
        "-Dinput=$ResolvedInput",
        "-Doutput=$Csv"
    )
    if ($Algorithm -ne "opf") {
        $JavaArgs += "-Dmode=$Algorithm"
        $JavaArgs += "-Dlog=$Log"
    }
    $JavaArgs += $ClassName

    Write-Host "[$Index/$Runs] $Algorithm"
    & java @JavaArgs
    if ($LASTEXITCODE -ne 0) { throw "Java failed for $Algorithm run $RunLabel." }
}

& python (Join-Path $RepoRoot "scripts/benchmark/average.py") $ResolvedOutput $Prefix $Runs
if ($LASTEXITCODE -ne 0) { throw "Result aggregation failed." }

Write-Host "Results: $ResolvedOutput"
