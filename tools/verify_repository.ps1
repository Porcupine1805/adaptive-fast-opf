param([switch]$PublicationGate)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Required = @(
    "README.md", "CITATION.cff", "src/benchmark/java/FOMAblationFlags.java",
    "src/benchmark/java/OPF_Miner_Original.java", "tools/smoke_test.ps1",
    "scripts/benchmark/run_factorial_benchmark.ps1",
    "scripts/validation/run_canonical_equivalence.ps1",
    "data/manifests/datasets.csv", "data/manifests/checksums.sha256",
    "results/reference/current-probes/full_fom_optimization_probe_10clients_rotated/probe_summary.csv",
    "results/reference/historical/benchmark/OPF_Miner_Original_summary_avg.csv",
    "results/reference/rq6/rq6_full_results_final.csv"
)
foreach ($Relative in $Required) {
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $Relative))) { throw "Missing required file: $Relative" }
}

$Oversize = @(Get-ChildItem -LiteralPath $RepoRoot -Recurse -File | Where-Object { $_.Length -ge 95MB })
if ($Oversize.Count) { throw "Files >= 95 MB found: $($Oversize.FullName -join ', ')" }
$Forbidden = @(Get-ChildItem -LiteralPath $RepoRoot -Recurse -File | Where-Object {
    $_.FullName -notlike "*\build\*" -and
    ($_.FullName -notlike "*\results\*" -or $_.FullName -like "*\results\reference\*") -and
    ($_.Extension -in @('.class','.zip','.7z','.tar','.gz') -or $_.Name -like '*.log')
})
if ($Forbidden.Count) { throw "Forbidden publication artifacts found: $($Forbidden.FullName -join ', ')" }

& (Join-Path $RepoRoot "tools/smoke_test.ps1")
if ($PublicationGate) {
    if (Test-Path -LiteralPath (Join-Path $RepoRoot "LICENSE_PENDING.md")) { throw "Publication gate: project license is unresolved." }
    $ReviewRows = @(Import-Csv (Join-Path $RepoRoot "data/manifests/datasets.csv") | Where-Object redistribution_status -eq 'REVIEW_REQUIRED')
    if ($ReviewRows.Count) { throw "Publication gate: dataset redistribution review is unresolved." }
}
Write-Host "Repository verification passed."
