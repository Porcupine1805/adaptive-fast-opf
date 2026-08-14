param(
    [string]$InputDir = "data/smoke",
    [string]$OutputDir = "results/canonical_check",
    [string]$FileRegex = ".*\.txt",
    [string]$MinSupList = "2,4,6,8,10,12",
    [string]$JavaHeap = "4g",
    [int]$RoundDecimals = 6,
    [double]$Tolerance = 1e-6
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ClassPath = Join-Path $RepoRoot "build/classes/benchmark"
$InputPath = if ([IO.Path]::IsPathRooted($InputDir)) { $InputDir } else { Join-Path $RepoRoot $InputDir }
$ResolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$ResolvedOutput = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $RepoRoot $OutputDir }
$OpfCanonical = Join-Path $ResolvedOutput "canonical/opf"

if (-not (Test-Path -LiteralPath (Join-Path $ClassPath "FOMAblationFlags.class"))) {
    & (Join-Path $RepoRoot "tools/build.ps1")
}

$Configurations = @(
    [pscustomobject]@{ Label="HJ"; Mode="hash_only"; Bitmap="never"; WSB="never" },
    [pscustomobject]@{ Label="BM"; Mode="hash_sparse"; Bitmap="always"; WSB="never" },
    [pscustomobject]@{ Label="WB"; Mode="hash_wsb"; Bitmap="never"; WSB="always" },
    [pscustomobject]@{ Label="FullAdaptive"; Mode="full"; Bitmap="adaptive"; WSB="adaptive" }
)

New-Item -ItemType Directory -Force -Path $OpfCanonical,(Join-Path $ResolvedOutput "comparisons") | Out-Null
& java "-Xmx$JavaHeap" -cp $ClassPath `
    "-Dinput=$ResolvedInput" "-DfileRegex=$FileRegex" "-DminsupList=$MinSupList" `
    "-Doutput=$(Join-Path $ResolvedOutput 'opf_summary.csv')" "-Dcanonical=$OpfCanonical" `
    OPF_Miner_Original
if ($LASTEXITCODE -ne 0) { throw "OPF canonical run failed." }

$Combined = @()
foreach ($Configuration in $Configurations) {
    $Canonical = Join-Path $ResolvedOutput "canonical/$($Configuration.Label)"
    $Comparison = Join-Path $ResolvedOutput "comparisons/opf_vs_$($Configuration.Label).csv"
    New-Item -ItemType Directory -Force -Path $Canonical | Out-Null
    & java "-Xmx$JavaHeap" -cp $ClassPath `
        "-Dinput=$ResolvedInput" "-DfileRegex=$FileRegex" "-DminsupList=$MinSupList" `
        "-Doutput=$(Join-Path $ResolvedOutput "$($Configuration.Label)_summary.csv")" `
        "-Dlog=$(Join-Path $ResolvedOutput "$($Configuration.Label).log")" `
        "-Dcanonical=$Canonical" "-Dmode=$($Configuration.Mode)" `
        "-DbitmapPolicy=$($Configuration.Bitmap)" "-DwsbPolicy=$($Configuration.WSB)" `
        FOMAblationFlags
    if ($LASTEXITCODE -ne 0) { throw "$($Configuration.Label) canonical run failed." }

    & python (Join-Path $RepoRoot "scripts/validation/verify_sha256_equivalence.py") `
        --opf $OpfCanonical --fom $Canonical --out $Comparison `
        --round-decimals $RoundDecimals --tolerance $Tolerance
    if ($LASTEXITCODE -ne 0) { throw "$($Configuration.Label) equivalence failed." }
    $Combined += Import-Csv -LiteralPath $Comparison | ForEach-Object {
        $_ | Add-Member -NotePropertyName Configuration -NotePropertyValue $Configuration.Label -PassThru
    }
}
$Combined | Select-Object Configuration,file,status,detail,max_diff,round_decimals,opf_raw_sha256,fom_raw_sha256,opf_normalized_sha256,fom_normalized_sha256 |
    Export-Csv (Join-Path $ResolvedOutput "comparisons/equivalence_summary.csv") -NoTypeInformation -Encoding UTF8
Write-Host "Canonical equivalence passed: $($Configurations.Count) FastOPF configurations."
