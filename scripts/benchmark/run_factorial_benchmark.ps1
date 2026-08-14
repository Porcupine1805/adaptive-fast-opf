param(
    [string]$InputDir = "data/benchmark",
    [string]$OutputDir = "",
    [string]$FileRegex = "DB[1-8]\.txt",
    [string]$MinSupList = "2,4,6,8,10,12",
    [int]$Runs = 10,
    [int]$WarmupRuns = 1,
    [string]$InitialHeap = "512m",
    [string]$MaximumHeap = "4g",
    [switch]$IncludeFullStatic
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ClassPath = Join-Path $RepoRoot "build/classes/benchmark"
$InputPath = if ([IO.Path]::IsPathRooted($InputDir)) { $InputDir } else { Join-Path $RepoRoot $InputDir }
$ResolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = "results/runs/factorial_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
}
$ResolvedOutput = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $RepoRoot $OutputDir }

if ($Runs -lt 1 -or $WarmupRuns -lt 0) { throw "Runs must be >= 1 and WarmupRuns must be >= 0." }
if (-not (Test-Path -LiteralPath (Join-Path $ClassPath "FOMAblationFlags.class"))) {
    & (Join-Path $RepoRoot "tools/build.ps1")
}

$Configurations = @(
    [pscustomobject]@{ Label="OPF"; Class="OPF_Miner_Original"; Mode=""; Bitmap=""; WSB="" },
    [pscustomobject]@{ Label="HJ"; Class="FOMAblationFlags"; Mode="hash_only"; Bitmap="never"; WSB="never" },
    [pscustomobject]@{ Label="BM"; Class="FOMAblationFlags"; Mode="hash_sparse"; Bitmap="always"; WSB="never" },
    [pscustomobject]@{ Label="WB"; Class="FOMAblationFlags"; Mode="hash_wsb"; Bitmap="never"; WSB="always" },
    [pscustomobject]@{ Label="FullAdaptive"; Class="FOMAblationFlags"; Mode="full"; Bitmap="adaptive"; WSB="adaptive" }
)
if ($IncludeFullStatic) {
    $Configurations += [pscustomobject]@{ Label="FullStatic"; Class="FOMAblationFlags"; Mode="full"; Bitmap="always"; WSB="always" }
}

New-Item -ItemType Directory -Force -Path $ResolvedOutput,(Join-Path $ResolvedOutput "logs"),(Join-Path $ResolvedOutput "warmup") | Out-Null
& (Join-Path $RepoRoot "tools/capture_environment.ps1") -OutputFile (Join-Path $ResolvedOutput "environment.txt") -InitialHeap $InitialHeap -MaximumHeap $MaximumHeap

function Invoke-Configuration {
    param($Configuration, [string]$Csv, [string]$Log)
    $JavaArgs = @(
        "-Xms$InitialHeap", "-Xmx$MaximumHeap", "-cp", $ClassPath,
        "-Dinput=$ResolvedInput", "-DfileRegex=$FileRegex",
        "-DminsupList=$MinSupList", "-Doutput=$Csv"
    )
    if ($Configuration.Class -eq "FOMAblationFlags") {
        $JavaArgs += "-Dmode=$($Configuration.Mode)"
        $JavaArgs += "-DbitmapPolicy=$($Configuration.Bitmap)"
        $JavaArgs += "-DwsbPolicy=$($Configuration.WSB)"
        $JavaArgs += "-Ddiagnostic=true"
        $JavaArgs += "-Dlog=$Log"
    }
    $JavaArgs += $Configuration.Class
    & java @JavaArgs
    if ($LASTEXITCODE -ne 0) { throw "Java failed for $($Configuration.Label)." }
    if (-not (Test-Path -LiteralPath $Csv) -or @(Import-Csv -LiteralPath $Csv).Count -eq 0) {
        throw "No benchmark rows produced for $($Configuration.Label)."
    }
}

for ($Warmup = 1; $Warmup -le $WarmupRuns; $Warmup++) {
    foreach ($Configuration in $Configurations) {
        $Stem = "warmup_{0}_{1:D2}" -f $Configuration.Label,$Warmup
        Invoke-Configuration $Configuration (Join-Path $ResolvedOutput "warmup/$Stem.csv") (Join-Path $ResolvedOutput "warmup/$Stem.log")
    }
}

for ($Run = 1; $Run -le $Runs; $Run++) {
    $Rotation = ($Run - 1) % $Configurations.Count
    $Order = @($Configurations[$Rotation..($Configurations.Count - 1)])
    if ($Rotation -gt 0) { $Order += @($Configurations[0..($Rotation - 1)]) }
    foreach ($Configuration in $Order) {
        $Stem = "{0}_run_{1:D2}" -f $Configuration.Label,$Run
        Write-Host "[$Run/$Runs] $($Configuration.Label)"
        Invoke-Configuration $Configuration (Join-Path $ResolvedOutput "$Stem.csv") (Join-Path $ResolvedOutput "logs/$Stem.log")
    }
}

$LongRows = foreach ($Configuration in $Configurations) {
    Get-ChildItem -LiteralPath $ResolvedOutput -Filter "$($Configuration.Label)_run_*.csv" | Sort-Object Name | ForEach-Object {
        $RunNumber = [int]([regex]::Match($_.BaseName, '_run_(\d+)$').Groups[1].Value)
        Import-Csv -LiteralPath $_.FullName | ForEach-Object {
            $_ | Add-Member -NotePropertyName Configuration -NotePropertyValue $Configuration.Label -PassThru |
                Add-Member -NotePropertyName Run -NotePropertyValue $RunNumber -PassThru
        }
    }
}
$LongRows | Select-Object Configuration,Run,Dataset,minsup,Time_s,MaxMem_MB,Candidates,Fusions,SupportOps,FreqPatterns,PairChecks,WSBChecks,WSBPrunes,SparseObjects,SparseWordsAllocated,SparseWordsScanned,ScalarPositionComparisons,BitmapFusionPairs,ScalarFusionPairs,RangeBoundPrunes |
    Export-Csv (Join-Path $ResolvedOutput "all_runs.csv") -NoTypeInformation -Encoding UTF8

function Get-Median([double[]]$Values) {
    $Sorted = @($Values | Sort-Object)
    $Middle = [int][math]::Floor($Sorted.Count / 2)
    if ($Sorted.Count % 2) { return $Sorted[$Middle] }
    return ($Sorted[$Middle - 1] + $Sorted[$Middle]) / 2.0
}

$Summary = $LongRows | Group-Object Configuration,Dataset,minsup | ForEach-Object {
    $Group = @($_.Group)
    $Times = [double[]]@($Group | ForEach-Object { [double]$_.Time_s })
    $Mean = ($Times | Measure-Object -Average).Average
    $Variance = if ($Times.Count -gt 1) { ($Times | ForEach-Object { [math]::Pow($_ - $Mean, 2) } | Measure-Object -Sum).Sum / ($Times.Count - 1) } else { 0 }
    [pscustomobject]@{
        Configuration = $Group[0].Configuration
        Dataset = $Group[0].Dataset
        MinSup = [double]$Group[0].minsup
        Runs = $Group.Count
        TimeMean_s = $Mean
        TimeStdDev_s = [math]::Sqrt($Variance)
        TimeMedian_s = Get-Median $Times
        TimeMin_s = ($Times | Measure-Object -Minimum).Minimum
        MaxMemMean_MB = ($Group | Measure-Object -Property MaxMem_MB -Average).Average
        FreqPatterns = ($Group | Measure-Object -Property FreqPatterns -Average).Average
    }
}
$Summary | Sort-Object Dataset,MinSup,Configuration | Export-Csv (Join-Path $ResolvedOutput "summary.csv") -NoTypeInformation -Encoding UTF8

Write-Host "Benchmark complete: $ResolvedOutput"
Write-Host "Canonical output was disabled; run the validation workflow separately."
