param(
    [string]$OutputFile = "results/environment.txt",
    [string]$InitialHeap = "512m",
    [string]$MaximumHeap = "4g"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ResolvedOutput = if ([IO.Path]::IsPathRooted($OutputFile)) { $OutputFile } else { Join-Path $RepoRoot $OutputFile }
New-Item -ItemType Directory -Force -Path (Split-Path $ResolvedOutput) | Out-Null

$CpuName = $env:PROCESSOR_IDENTIFIER
$LogicalProcessors = $env:NUMBER_OF_PROCESSORS
$RamGB = "UNAVAILABLE"
$OsDescription = [Environment]::OSVersion.VersionString
try {
    $Cpu = Get-CimInstance Win32_Processor -ErrorAction Stop | Select-Object -First 1
    $CpuName = $Cpu.Name
    $LogicalProcessors = $Cpu.NumberOfLogicalProcessors
} catch {}
try {
    $Os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
    $OsDescription = "$($Os.Caption) $($Os.Version) build $($Os.BuildNumber)"
} catch {}
try {
    $Computer = Get-CimInstance Win32_ComputerSystem -ErrorAction Stop
    $RamGB = [math]::Round($Computer.TotalPhysicalMemory / 1GB, 2)
} catch {}
$PreviousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$JavaVersion = (& java -version 2>&1) -join [Environment]::NewLine
$VmSettings = (& java "-Xms$InitialHeap" "-Xmx$MaximumHeap" -XshowSettings:vm -version 2>&1) -join [Environment]::NewLine
$PowerPlan = try { (& powercfg /getactivescheme 2>&1) -join [Environment]::NewLine } catch { "UNAVAILABLE: $($_.Exception.Message)" }
$ErrorActionPreference = $PreviousErrorAction

@"
CapturedAt=$(Get-Date -Format o)
Machine=$env:COMPUTERNAME
CPU=$CpuName
LogicalProcessors=$LogicalProcessors
RAM_GB=$RamGB
OS=$OsDescription
JVM_FLAGS=-Xms$InitialHeap -Xmx$MaximumHeap
PowerPolicy=$PowerPlan

[java -version]
$JavaVersion

[VM settings]
$VmSettings
"@ | Set-Content -LiteralPath $ResolvedOutput -Encoding UTF8

Write-Host "Environment: $ResolvedOutput"
