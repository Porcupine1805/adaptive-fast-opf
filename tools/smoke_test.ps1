$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

& (Join-Path $RepoRoot "tools/build.ps1") -Clean
& (Join-Path $RepoRoot "scripts/validation/run_canonical_equivalence.ps1") `
    -InputDir "data/smoke" -OutputDir "results/smoke"

$Comparison = Join-Path $RepoRoot "results/smoke/comparisons/equivalence_summary.csv"
$Rows = @(Import-Csv -LiteralPath $Comparison)
$Allowed = @("SHA256_MATCH", "NORMALIZED_SHA256_MATCH", "CONTENT_MATCH_TOLERANCE")
$Failures = @($Rows | Where-Object { $_.status -notin $Allowed })

if ($Rows.Count -eq 0) { throw "Smoke test produced no comparison rows." }
if (($Rows.Configuration | Sort-Object -Unique).Count -ne 4) { throw "Smoke test did not cover all four FastOPF configurations." }
if ($Failures.Count -gt 0) { throw "Smoke test has $($Failures.Count) failed comparisons." }

Write-Host "Smoke test passed: $($Rows.Count) comparisons across HJ, BM, WB, and Full."
