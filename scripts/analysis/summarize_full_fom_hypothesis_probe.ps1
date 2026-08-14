param(
    [string]$ResultRoot = "results/experiments/full_fom_hypothesis",
    [string]$OutputDir = "results/analysis/full_fom_hypothesis_summary"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ResultRoot = if ([IO.Path]::IsPathRooted($ResultRoot)) { $ResultRoot } else { Join-Path $RepoRoot $ResultRoot }
$OutputDir = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $RepoRoot $OutputDir }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Get-MeanSd {
    param([double[]]$Values)
    $mean = ($Values | Measure-Object -Average).Average
    $sd = 0.0
    if ($Values.Count -gt 1) {
        $sumSquares = ($Values | ForEach-Object { ($_ - $mean) * ($_ - $mean) } | Measure-Object -Sum).Sum
        $sd = [math]::Sqrt($sumSquares / ($Values.Count - 1))
    }
    return @($mean, $sd)
}

function Read-Runs {
    param([string]$Directory, [string]$Configuration)
    Get-ChildItem "$Directory/${Configuration}_run_*.csv" | ForEach-Object {
        Import-Csv $_.FullName | ForEach-Object {
            [pscustomobject]@{
                Configuration = $Configuration
                Dataset = $_.Dataset
                minsup = [double]$_.minsup
                Time_s = [double]$_.Time_s
                Memory_MB = [double]$_.MaxMem_MB
                FreqPatterns = [long]$_.FreqPatterns
                WSBPrunes = [long]$_.WSBPrunes
                WordScans = [long]$_.SparseWordsScanned
                ScalarComparisons = [long]$_.ScalarPositionComparisons
            }
        }
    }
}

$lowMinSupRows = @()
$oneClientDir = "$ResultRoot/full_fom_hypothesis_low_minsup"
foreach ($configuration in @("HashOnly", "HashSparseCompressed", "FullStatic")) {
    $lowMinSupRows += Read-Runs $oneClientDir $configuration
}
$fiveClientDir = "$ResultRoot/full_fom_hypothesis_low_minsup_5clients"
$lowMinSupRows += Read-Runs $fiveClientDir "HashOnly"
$lowMinSupRows += Read-Runs $fiveClientDir "FullStatic"
$lowMinSupRows += Read-Runs "$ResultRoot/full_fom_hypothesis_low_minsup_5clients_bm" "HashSparseCompressed"

$lowAgg = $lowMinSupRows | Group-Object Configuration, Dataset, minsup | ForEach-Object {
    $stats = Get-MeanSd ([double[]]$_.Group.Time_s)
    [pscustomobject]@{
        Configuration = $_.Group[0].Configuration
        Dataset = $_.Group[0].Dataset
        minsup = $_.Group[0].minsup
        Runs = $_.Count
        TimeMean_s = $stats[0]
        TimeSD_s = $stats[1]
        MemoryMean_MB = ($_.Group.Memory_MB | Measure-Object -Average).Average
        FreqPatterns = ($_.Group.FreqPatterns | Measure-Object -Average).Average
        WSBPrunes = ($_.Group.WSBPrunes | Measure-Object -Average).Average
        WordScans = ($_.Group.WordScans | Measure-Object -Average).Average
        ScalarComparisons = ($_.Group.ScalarComparisons | Measure-Object -Average).Average
    }
}
$hjTimes = @{}
$lowAgg | Where-Object Configuration -eq "HashOnly" | ForEach-Object {
    $hjTimes["$($_.Dataset)|$($_.minsup)"] = $_.TimeMean_s
}
$lowAgg | ForEach-Object {
    $_ | Add-Member -NotePropertyName HJ_over_Configuration -NotePropertyValue (
        $hjTimes["$($_.Dataset)|$($_.minsup)"] / $_.TimeMean_s) -PassThru
} | Sort-Object Dataset, minsup, Configuration |
    Export-Csv "$OutputDir/hypothesis_1_minsup.csv" -NoTypeInformation -Encoding UTF8

$syntheticDir = "$ResultRoot/full_fom_hypothesis_synthetic_1024_repeated"
$syntheticRows = @()
foreach ($configuration in @("HashOnly", "HashSparseCompressed", "FullStatic")) {
    $syntheticRows += Read-Runs $syntheticDir $configuration
}
$densityByDataset = @{}
Import-Csv "$syntheticDir/generations_HashOnly_run_01.csv" | Group-Object Dataset | ForEach-Object {
    $densityByDataset[$_.Name] = ($_.Group.OccurrenceDensity | Measure-Object -Average).Average
}
$syntheticAgg = $syntheticRows | Group-Object Configuration, Dataset | ForEach-Object {
    $stats = Get-MeanSd ([double[]]$_.Group.Time_s)
    [pscustomobject]@{
        Configuration = $_.Group[0].Configuration
        Dataset = $_.Group[0].Dataset
        Runs = $_.Count
        MeanOccurrenceDensity = $densityByDataset[$_.Group[0].Dataset]
        TimeMean_s = $stats[0]
        TimeSD_s = $stats[1]
        MemoryMean_MB = ($_.Group.Memory_MB | Measure-Object -Average).Average
        FreqPatterns = ($_.Group.FreqPatterns | Measure-Object -Average).Average
        WordScans = ($_.Group.WordScans | Measure-Object -Average).Average
        ScalarComparisons = ($_.Group.ScalarComparisons | Measure-Object -Average).Average
    }
}
$syntheticHj = @{}
$syntheticAgg | Where-Object Configuration -eq "HashOnly" | ForEach-Object {
    $syntheticHj[$_.Dataset] = $_.TimeMean_s
}
$syntheticAgg | ForEach-Object {
    $_ | Add-Member -NotePropertyName HJ_over_Configuration -NotePropertyValue (
        $syntheticHj[$_.Dataset] / $_.TimeMean_s) -PassThru
} | Sort-Object Dataset, Configuration |
    Export-Csv "$OutputDir/hypothesis_2_density.csv" -NoTypeInformation -Encoding UTF8

$generationRows = Get-ChildItem "$syntheticDir/generations_*_run_*.csv" | ForEach-Object {
    $configuration = $_.BaseName -replace "^generations_", "" -replace "_run_\d+$", ""
    $run = [int]([regex]::Match($_.BaseName, "_run_(\d+)$").Groups[1].Value)
    Import-Csv $_.FullName | ForEach-Object {
        $length = [int]$_.PatternLength
        $bin = if ($length -le 15) { "02-15" }
            elseif ($length -le 63) { "16-63" }
            elseif ($length -le 255) { "64-255" }
            elseif ($length -le 511) { "256-511" }
            else { "512+" }
        [pscustomobject]@{
            Configuration = $configuration
            Run = $run
            Dataset = $_.Dataset
            LengthBin = $bin
            KernelTime_s = [double]$_.KernelTime_s
            Density = [double]$_.OccurrenceDensity
            ScalarComparisons = [long]$_.ScalarPositionComparisons
            WordScans = [long]$_.SparseWordsScanned
        }
    }
}
$perRunBins = $generationRows | Group-Object Configuration, Run, Dataset, LengthBin | ForEach-Object {
    [pscustomobject]@{
        Configuration = $_.Group[0].Configuration
        Run = $_.Group[0].Run
        Dataset = $_.Group[0].Dataset
        LengthBin = $_.Group[0].LengthBin
        KernelTime_s = ($_.Group.KernelTime_s | Measure-Object -Sum).Sum
        Density = ($_.Group.Density | Measure-Object -Average).Average
        ScalarComparisons = ($_.Group.ScalarComparisons | Measure-Object -Sum).Sum
        WordScans = ($_.Group.WordScans | Measure-Object -Sum).Sum
    }
}
$binAgg = $perRunBins | Group-Object Configuration, Dataset, LengthBin | ForEach-Object {
    [pscustomobject]@{
        Configuration = $_.Group[0].Configuration
        Dataset = $_.Group[0].Dataset
        LengthBin = $_.Group[0].LengthBin
        Runs = $_.Count
        KernelTimeMean_s = ($_.Group.KernelTime_s | Measure-Object -Average).Average
        MeanDensity = ($_.Group.Density | Measure-Object -Average).Average
        ScalarComparisons = ($_.Group.ScalarComparisons | Measure-Object -Average).Average
        WordScans = ($_.Group.WordScans | Measure-Object -Average).Average
    }
}
$binHj = @{}
$binAgg | Where-Object Configuration -eq "HashOnly" | ForEach-Object {
    $binHj["$($_.Dataset)|$($_.LengthBin)"] = $_.KernelTimeMean_s
}
$binAgg | Where-Object Configuration -in @("HashOnly", "HashSparseCompressed") | ForEach-Object {
    $_ | Add-Member -NotePropertyName HJ_over_Configuration -NotePropertyValue (
        $binHj["$($_.Dataset)|$($_.LengthBin)"] / $_.KernelTimeMean_s) -PassThru
} | Sort-Object Dataset, LengthBin, Configuration |
    Export-Csv "$OutputDir/hypothesis_3_pattern_length.csv" -NoTypeInformation -Encoding UTF8

@(
    [pscustomobject]@{ Hypothesis = "Very low minSup makes Full faster than HJ"; Verdict = "Conditional, not sufficient" },
    [pscustomobject]@{ Hypothesis = "Dense occurrence matrices favor BM"; Verdict = "Supported for the tested dense synthetic workloads" },
    [pscustomobject]@{ Hypothesis = "Very long patterns let BM amortize overhead"; Verdict = "Conditional on density and bitmap reuse; length alone is insufficient" }
) | Export-Csv "$OutputDir/hypothesis_verdicts.csv" -NoTypeInformation -Encoding UTF8

Write-Host "Hypothesis summaries written to $OutputDir"
