<#
.SYNOPSIS
  Manuscript experimental protocol runner for Adaptive Fast OPF (V0–V3).

.DESCRIPTION
  Aligns with paper variants:
    V0  OPF-Miner original          (OPF_Miner_Original)
    V1  HJ-only                     (mode=hash_only)
    V2  HJ + CPC (always / gate C)  (mode=adaptive, staged off or gate C, Gallop off)
    V3  Full Adaptive               (mode=adaptive, staged on, CPC+Gallop gated)

  Warm-up then measured runs; per-run CSV under results/manuscript/<stamp>/.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/benchmark/run_manuscript_protocol.ps1 `
    -Profile pilot -Runs 5 -WarmupRuns 3
#>
param(
    [ValidateSet("pilot", "db_full", "elec_scale", "custom")]
    [string]$Profile = "pilot",
    [string]$InputDir = "",
    [string]$FileRegex = "",
    [string]$MinSupList = "",
    [int]$Runs = 5,
    [int]$WarmupRuns = 3,
    [string]$InitialHeap = "2g",
    [string]$MaximumHeap = "8g",
    [string]$OutputDir = "",
    [string[]]$Variants = @("V0", "V1", "V2", "V3")
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ClassPath = Join-Path $RepoRoot "build/classes/benchmark"

switch ($Profile) {
    "pilot" {
        if (-not $InputDir) { $InputDir = "data/benchmark" }
        if (-not $FileRegex) { $FileRegex = "DB(4|5)\.txt" }
        if (-not $MinSupList) { $MinSupList = "2,4" }
    }
    "db_full" {
        if (-not $InputDir) { $InputDir = "data/benchmark" }
        if (-not $FileRegex) { $FileRegex = "DB[1-8]\.txt" }
        if (-not $MinSupList) { $MinSupList = "2,4,6,8,10,12" }
    }
    "elec_scale" {
        if (-not $InputDir) { $InputDir = "data/electricity_scale" }
        if (-not $FileRegex) { $FileRegex = "ELEC_.*\.txt" }
        if (-not $MinSupList) { $MinSupList = "2,4,8" }
        if ($MaximumHeap -eq "8g") { $MaximumHeap = "16g" }
    }
    "custom" {
        if (-not $InputDir -or -not $FileRegex -or -not $MinSupList) {
            throw "Profile custom requires -InputDir, -FileRegex, and -MinSupList."
        }
    }
}

$InputPath = if ([IO.Path]::IsPathRooted($InputDir)) { $InputDir } else { Join-Path $RepoRoot $InputDir }
$ResolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = "results/manuscript/$(Get-Date -Format 'yyyyMMdd_HHmmss')_$Profile"
}
$ResolvedOutput = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $RepoRoot $OutputDir }

if (-not (Test-Path -LiteralPath (Join-Path $ClassPath "FOMAblationFlags.class"))) {
    & (Join-Path $RepoRoot "tools/build.ps1")
}
if (-not (Test-Path -LiteralPath (Join-Path $ClassPath "OPF_Miner_Original.class"))) {
    & (Join-Path $RepoRoot "tools/build.ps1")
}

# Paper-aligned variants (bitmap never)
$AllVariants = @{
    "V0" = [pscustomobject]@{
        Label = "V0_OPF"
        Class = "OPF_Miner_Original"
        Mode = ""; Bitmap = ""; WSB = ""
        Extra = @()
    }
    "V1" = [pscustomobject]@{
        Label = "V1_HJ"
        Class = "FOMAblationFlags"
        Mode = "hash_only"; Bitmap = "never"; WSB = "never"
        Extra = @("-DadaptiveWsbCheapPrune=false", "-DadaptiveSmartIntersect=false")
    }
    "V2" = [pscustomobject]@{
        Label = "V2_HJ_CPC"
        Class = "FOMAblationFlags"
        Mode = "adaptive"; Bitmap = "never"; WSB = "cost"
        # CPC on (gate C = always when N,P floors pass); Gallop off
        Extra = @(
            "-DadaptiveStagedPolicy=true",
            "-DadaptiveCpcGate=C",
            "-DadaptiveWsbCheapPrune=true",
            "-DadaptiveSmartIntersect=false",
            "-DadaptiveGallopWithoutCpc=false"
        )
    }
    "V3" = [pscustomobject]@{
        Label = "V3_Adaptive"
        Class = "FOMAblationFlags"
        Mode = "adaptive"; Bitmap = "never"; WSB = "cost"
        Extra = @(
            "-DadaptiveStagedPolicy=true",
            "-DadaptiveCpcGate=B",
            "-DadaptiveWsbCheapPrune=true",
            "-DadaptiveSmartIntersect=true",
            "-DadaptiveGallopWithoutCpc=true"
        )
    }
}

$Configurations = @()
foreach ($v in $Variants) {
    if (-not $AllVariants.ContainsKey($v)) { throw "Unknown variant $v. Use V0 V1 V2 V3." }
    $Configurations += $AllVariants[$v]
}

New-Item -ItemType Directory -Force -Path $ResolvedOutput,
    (Join-Path $ResolvedOutput "raw"),
    (Join-Path $ResolvedOutput "warmup"),
    (Join-Path $ResolvedOutput "logs"),
    (Join-Path $ResolvedOutput "summaries") | Out-Null

if (Test-Path (Join-Path $RepoRoot "tools/capture_environment.ps1")) {
    & (Join-Path $RepoRoot "tools/capture_environment.ps1") `
        -OutputFile (Join-Path $ResolvedOutput "environment.txt") `
        -InitialHeap $InitialHeap -MaximumHeap $MaximumHeap
}

@"
profile=$Profile
input=$ResolvedInput
fileRegex=$FileRegex
minsupList=$MinSupList
runs=$Runs
warmup=$WarmupRuns
heap=-Xms$InitialHeap -Xmx$MaximumHeap
variants=$($Variants -join ',')
timestamp=$(Get-Date -Format o)
"@ | Set-Content -Encoding UTF8 (Join-Path $ResolvedOutput "protocol_meta.txt")

function Invoke-Configuration {
    param($Configuration, [string]$Csv, [string]$Log)
    $JavaArgs = @(
        "-Xms$InitialHeap", "-Xmx$MaximumHeap", "-cp", $ClassPath,
        "-Dinput=$ResolvedInput", "-DfileRegex=$FileRegex",
        "-DminsupList=$MinSupList", "-Doutput=$Csv",
        "-DjitWarmupRuns=0", "-DcaseWarmupRuns=0"
    )
    if ($Configuration.Class -eq "FOMAblationFlags") {
        $JavaArgs += "-Dmode=$($Configuration.Mode)"
        $JavaArgs += "-DbitmapPolicy=$($Configuration.Bitmap)"
        $JavaArgs += "-DwsbPolicy=$($Configuration.WSB)"
        $JavaArgs += "-Ddiagnostic=true"
        $JavaArgs += "-Dlog=$Log"
        foreach ($e in $Configuration.Extra) { $JavaArgs += $e }
    }
    $JavaArgs += $Configuration.Class
    Write-Host "  java $($Configuration.Label) -> $Csv"
    & java @JavaArgs
    if ($LASTEXITCODE -ne 0) { throw "Java failed for $($Configuration.Label)." }
    if (-not (Test-Path -LiteralPath $Csv)) { throw "Missing CSV for $($Configuration.Label)." }
}

for ($Warmup = 1; $Warmup -le $WarmupRuns; $Warmup++) {
    foreach ($Configuration in $Configurations) {
        $Stem = "warmup_{0}_{1:D2}" -f $Configuration.Label, $Warmup
        Invoke-Configuration $Configuration `
            (Join-Path $ResolvedOutput "warmup/$Stem.csv") `
            (Join-Path $ResolvedOutput "logs/$Stem.log")
    }
}

for ($Run = 1; $Run -le $Runs; $Run++) {
    $Rotation = ($Run - 1) % [Math]::Max(1, $Configurations.Count)
    $Order = @($Configurations[$Rotation..($Configurations.Count - 1)])
    if ($Rotation -gt 0) { $Order += @($Configurations[0..($Rotation - 1)]) }
    foreach ($Configuration in $Order) {
        $Stem = "{0}_run_{1:D2}" -f $Configuration.Label, $Run
        Write-Host "[$Run/$Runs] $($Configuration.Label)"
        Invoke-Configuration $Configuration `
            (Join-Path $ResolvedOutput "raw/$Stem.csv") `
            (Join-Path $ResolvedOutput "logs/$Stem.log")
    }
}

# Long table + median summary
$LongRows = foreach ($Configuration in $Configurations) {
    Get-ChildItem -LiteralPath (Join-Path $ResolvedOutput "raw") -Filter "$($Configuration.Label)_run_*.csv" -ErrorAction SilentlyContinue |
        Sort-Object Name | ForEach-Object {
            $RunNumber = [int]([regex]::Match($_.BaseName, '_run_(\d+)$').Groups[1].Value)
            Import-Csv -LiteralPath $_.FullName | ForEach-Object {
                $_ | Add-Member -NotePropertyName Variant -NotePropertyValue $Configuration.Label -PassThru |
                    Add-Member -NotePropertyName Run -NotePropertyValue $RunNumber -PassThru
            }
        }
}
$LongPath = Join-Path $ResolvedOutput "summaries/all_runs.csv"
$LongRows | Export-Csv $LongPath -NoTypeInformation -Encoding UTF8

function Get-Median([double[]]$Values) {
    if (-not $Values -or $Values.Count -eq 0) { return [double]::NaN }
    $Sorted = @($Values | Sort-Object)
    $Middle = [int][math]::Floor($Sorted.Count / 2)
    if ($Sorted.Count % 2) { return [double]$Sorted[$Middle] }
    return ([double]$Sorted[$Middle - 1] + [double]$Sorted[$Middle]) / 2.0
}

$Groups = $LongRows | Group-Object Variant, Dataset, minsup
$MedianRows = foreach ($g in $Groups) {
    $times = @($g.Group | ForEach-Object { [double]$_.Time_s })
    $mems = @($g.Group | ForEach-Object { [double]$_.MaxMem_MB })
    $freq = ($g.Group | Select-Object -First 1).FreqPatterns
    [pscustomobject]@{
        Variant = ($g.Name -split ',')[0].Trim()
        Dataset = ($g.Group | Select-Object -First 1).Dataset
        minsup = ($g.Group | Select-Object -First 1).minsup
        MedianTime_s = Get-Median $times
        MedianMem_MB = Get-Median $mems
        FreqPatterns = $freq
        NRuns = $g.Count
    }
}
$MedianPath = Join-Path $ResolvedOutput "summaries/median_by_variant.csv"
$MedianRows | Export-Csv $MedianPath -NoTypeInformation -Encoding UTF8

Write-Host "Done. Output: $ResolvedOutput"
Write-Host "  all_runs: $LongPath"
Write-Host "  medians:  $MedianPath"
