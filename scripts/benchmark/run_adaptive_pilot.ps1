param(
    [string]$InputDir = "data/benchmark",
    [string]$OutputDir = "",
    [string]$FileRegex = "DB(1|5|8)\.txt",
    [string]$ConfigurationRegex = ".*",
    [string]$MinSupList = "2,4",
    [int]$Runs = 3,
    [int]$CacheWarmupRuns = 0,
    [int]$JitWarmupRuns = 2,
    [int]$CaseWarmupRuns = 0,
    [string]$InitialHeap = "512m",
    [string]$MaximumHeap = "4g",
    [long]$AdaptiveBitmapMaxConversionUnits = 32768,
    [long]$AdaptiveWsbMinPairs = 128,
    [double]$AdaptiveWsbMinGain = 1.25,
    [double]$AdaptiveWsbMinSupportRatio = 0.001,
    [int]$WsbSampleSize = 8
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ClassPath = Join-Path $RepoRoot "build/classes/benchmark"
$InputPath = if ([IO.Path]::IsPathRooted($InputDir)) { $InputDir } else { Join-Path $RepoRoot $InputDir }
$ResolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = "results/runs/adaptive_pilot_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
}
$ResolvedOutput = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $RepoRoot $OutputDir }

if ($Runs -lt 1 -or $CacheWarmupRuns -lt 0) { throw "Runs must be >= 1 and CacheWarmupRuns >= 0." }
if (Test-Path -LiteralPath $ResolvedOutput) {
    if (@(Get-ChildItem -LiteralPath $ResolvedOutput -Force).Count -gt 0) {
        throw "Refusing to mix a pilot with non-empty output: $ResolvedOutput"
    }
} else {
    New-Item -ItemType Directory -Path $ResolvedOutput | Out-Null
}
New-Item -ItemType Directory -Force -Path (Join-Path $ResolvedOutput "logs"),
    (Join-Path $ResolvedOutput "warmup"),(Join-Path $ResolvedOutput "diagnostic") | Out-Null

& (Join-Path $RepoRoot "tools/build.ps1") -Clean
& (Join-Path $RepoRoot "tools/capture_environment.ps1") `
    -OutputFile (Join-Path $ResolvedOutput "environment.txt") `
    -InitialHeap $InitialHeap -MaximumHeap $MaximumHeap

$Configurations = @(
    [pscustomobject]@{ Label="HJOnly"; Mode="hash_only"; Bitmap="never"; WSB="never" },
    [pscustomobject]@{ Label="BitmapOnly"; Mode="sparse_only"; Bitmap="always"; WSB="never" },
    [pscustomobject]@{ Label="WSBOnly"; Mode="wsb_only"; Bitmap="never"; WSB="always" },
    [pscustomobject]@{ Label="HashBitmap"; Mode="hash_sparse"; Bitmap="always"; WSB="never" },
    [pscustomobject]@{ Label="HashWSB"; Mode="hash_wsb"; Bitmap="never"; WSB="always" },
    [pscustomobject]@{ Label="FullStatic"; Mode="full"; Bitmap="always"; WSB="always" },
    [pscustomobject]@{ Label="Adaptive"; Mode="adaptive"; Bitmap="cost"; WSB="cost" }
) | Where-Object Label -Match $ConfigurationRegex
if (@($Configurations).Count -eq 0 -or -not ($Configurations.Label -contains "Adaptive")) {
    throw "ConfigurationRegex must select Adaptive and at least one configuration."
}

$GitCommit = "UNCOMMITTED"
if (Test-Path -LiteralPath (Join-Path $RepoRoot ".git")) {
    $PreviousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $GitCommitCandidate = & git -C $RepoRoot rev-parse HEAD 2>$null
    $GitExitCode = $LASTEXITCODE
    $ErrorActionPreference = $PreviousErrorPreference
    if ($GitExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($GitCommitCandidate)) {
        $GitCommit = $GitCommitCandidate
    }
}

$Manifest = [ordered]@{
    created_at = (Get-Date -Format o)
    input = $ResolvedInput
    file_regex = $FileRegex
    configuration_regex = $ConfigurationRegex
    minsup_list = $MinSupList
    runs = $Runs
    cache_warmup_runs = $CacheWarmupRuns
    jit_warmup_runs = $JitWarmupRuns
    case_warmup_runs = $CaseWarmupRuns
    initial_heap = $InitialHeap
    maximum_heap = $MaximumHeap
    source_sha256 = (Get-FileHash (Join-Path $RepoRoot "src/benchmark/java/FOMAblationFlags.java") -Algorithm SHA256).Hash
    git_commit = $GitCommit
    adaptive = [ordered]@{
        bitmap_max_conversion_units = $AdaptiveBitmapMaxConversionUnits
        wsb_min_pairs = $AdaptiveWsbMinPairs
        wsb_min_gain = $AdaptiveWsbMinGain
        wsb_min_support_ratio = $AdaptiveWsbMinSupportRatio
        wsb_sample_size = $WsbSampleSize
    }
    configurations = @($Configurations)
}
$Manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $ResolvedOutput "manifest.json") -Encoding UTF8

function Invoke-PilotConfiguration {
    param($Configuration, [string]$Csv, [string]$Log, [string]$GenerationOutput = "")
    $JavaArgs = @(
        "-Xms$InitialHeap", "-Xmx$MaximumHeap", "-cp", $ClassPath,
        "-Dinput=$ResolvedInput", "-DfileRegex=$FileRegex", "-DminsupList=$MinSupList",
        "-Dmode=$($Configuration.Mode)", "-DbitmapPolicy=$($Configuration.Bitmap)",
        "-DwsbPolicy=$($Configuration.WSB)", "-Ddiagnostic=false",
        "-DadaptiveBitmapMaxConversionUnits=$AdaptiveBitmapMaxConversionUnits",
        "-DadaptiveWsbMinPairs=$AdaptiveWsbMinPairs", "-DadaptiveWsbMinGain=$AdaptiveWsbMinGain",
        "-DadaptiveWsbMinSupportRatio=$AdaptiveWsbMinSupportRatio",
        "-DwsbSampleSize=$WsbSampleSize", "-Doutput=$Csv", "-Dlog=$Log"
        "-DjitWarmupRuns=$JitWarmupRuns"
        "-DcaseWarmupRuns=$CaseWarmupRuns"
    )
    if (-not [string]::IsNullOrWhiteSpace($GenerationOutput)) {
        $JavaArgs += "-DgenerationOutput=$GenerationOutput"
    }
    $JavaArgs += "FOMAblationFlags"
    & java @JavaArgs
    if ($LASTEXITCODE -ne 0) { throw "Java failed for $($Configuration.Label)." }
    if (-not (Test-Path -LiteralPath $Csv) -or @(Import-Csv -LiteralPath $Csv).Count -eq 0) {
        throw "No rows produced for $($Configuration.Label)."
    }
}

for ($Warmup = 1; $Warmup -le $CacheWarmupRuns; $Warmup++) {
    foreach ($Configuration in $Configurations) {
        $Stem = "warmup_{0}_{1:D2}" -f $Configuration.Label,$Warmup
        Invoke-PilotConfiguration $Configuration `
            (Join-Path $ResolvedOutput "warmup/$Stem.csv") `
            (Join-Path $ResolvedOutput "warmup/$Stem.log")
    }
}

for ($Run = 1; $Run -le $Runs; $Run++) {
    $Rotation = ($Run - 1) % $Configurations.Count
    $Order = @($Configurations[$Rotation..($Configurations.Count - 1)])
    if ($Rotation -gt 0) { $Order += @($Configurations[0..($Rotation - 1)]) }
    foreach ($Configuration in $Order) {
        $Stem = "{0}_run_{1:D2}" -f $Configuration.Label,$Run
        Write-Host "[$Run/$Runs] $($Configuration.Label)"
        Invoke-PilotConfiguration $Configuration `
            (Join-Path $ResolvedOutput "$Stem.csv") `
            (Join-Path $ResolvedOutput "logs/$Stem.log")
    }
}

$LongRows = foreach ($Configuration in $Configurations) {
    Get-ChildItem -LiteralPath $ResolvedOutput -Filter "$($Configuration.Label)_run_*.csv" |
        Sort-Object Name | ForEach-Object {
            $RunNumber = [int]([regex]::Match($_.BaseName, '_run_(\d+)$').Groups[1].Value)
            Import-Csv -LiteralPath $_.FullName | ForEach-Object {
                $_ | Add-Member -NotePropertyName Configuration -NotePropertyValue $Configuration.Label -PassThru |
                    Add-Member -NotePropertyName Run -NotePropertyValue $RunNumber -PassThru
            }
        }
}
$Columns = @("Configuration","Run","Dataset","minsup","Time_s","MaxMem_MB","PairChecks","Candidates",
    "Fusions","SupportOps","FreqPatterns","WSBChecks","WSBPrunes","SparseObjects","SparseWordsAllocated",
    "SparseWordsScanned","ScalarPositionComparisons","BitmapFusionPairs","ScalarFusionPairs","RangeBoundPrunes",
    "AdaptiveDecision_ms","AdaptiveBitmapDecision_ms","AdaptiveWsbDecision_ms",
    "AdaptiveBitmapLevels","AdaptiveScalarLevels","AdaptiveWsbSampledLevels",
    "AdaptiveWsbEnabledLevels")
$LongRows | Select-Object $Columns | Export-Csv (Join-Path $ResolvedOutput "all_runs.csv") -NoTypeInformation -Encoding UTF8

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
    $Variance = if ($Times.Count -gt 1) {
        ($Times | ForEach-Object { [math]::Pow($_ - $Mean, 2) } | Measure-Object -Sum).Sum / ($Times.Count - 1)
    } else { 0.0 }
    [pscustomobject]@{
        Configuration = $Group[0].Configuration
        Dataset = $Group[0].Dataset
        MinSup = [double]$Group[0].minsup
        Runs = $Group.Count
        TimeMean_s = $Mean
        TimeMedian_s = Get-Median $Times
        TimeStdDev_s = [math]::Sqrt($Variance)
        TimeMin_s = ($Times | Measure-Object -Minimum).Minimum
        MaxMemMean_MB = ($Group | Measure-Object -Property MaxMem_MB -Average).Average
        FreqPatterns = ($Group | Measure-Object -Property FreqPatterns -Average).Average
        AdaptiveDecisionMean_ms = ($Group | Measure-Object -Property AdaptiveDecision_ms -Average).Average
        AdaptiveBitmapLevelsMean = ($Group | Measure-Object -Property AdaptiveBitmapLevels -Average).Average
        AdaptiveScalarLevelsMean = ($Group | Measure-Object -Property AdaptiveScalarLevels -Average).Average
        AdaptiveWsbEnabledLevelsMean = ($Group | Measure-Object -Property AdaptiveWsbEnabledLevels -Average).Average
    }
}
$Summary | Sort-Object Dataset,MinSup,Configuration | Export-Csv (Join-Path $ResolvedOutput "summary.csv") -NoTypeInformation -Encoding UTF8

$Comparison = foreach ($Group in ($Summary | Group-Object Dataset,MinSup)) {
    $Adaptive = $Group.Group | Where-Object Configuration -eq "Adaptive"
    $Best = $Group.Group | Sort-Object TimeMedian_s | Select-Object -First 1
    foreach ($Row in $Group.Group) {
        [pscustomobject]@{
            Dataset = $Row.Dataset
            MinSup = $Row.MinSup
            Configuration = $Row.Configuration
            TimeMedian_s = $Row.TimeMedian_s
            AdaptiveMedian_s = $Adaptive.TimeMedian_s
            Adaptive_over_Configuration = $Adaptive.TimeMedian_s / $Row.TimeMedian_s
            Winner = $Best.Configuration
        }
    }
}
$Comparison | Export-Csv (Join-Path $ResolvedOutput "adaptive_comparison.csv") -NoTypeInformation -Encoding UTF8

$AdaptiveConfiguration = $Configurations | Where-Object Label -eq "Adaptive"
Invoke-PilotConfiguration $AdaptiveConfiguration `
    (Join-Path $ResolvedOutput "diagnostic/adaptive_summary.csv") `
    (Join-Path $ResolvedOutput "diagnostic/adaptive.log") `
    (Join-Path $ResolvedOutput "diagnostic/adaptive_generation_decisions.csv")

Write-Host "Adaptive pilot complete: $ResolvedOutput"
