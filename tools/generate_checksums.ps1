$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DataRoot = Join-Path $RepoRoot "data"
$Output = Join-Path $DataRoot "manifests/checksums.sha256"
$Files = Get-ChildItem -LiteralPath $DataRoot -Recurse -File | Where-Object {
    $_.FullName -notlike "*\manifests\checksums.sha256"
} | Sort-Object FullName

$Lines = foreach ($File in $Files) {
    $Relative = $File.FullName.Substring($RepoRoot.Length + 1).Replace('\','/')
    "{0}  {1}" -f (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant(),$Relative
}
$Lines | Set-Content -LiteralPath $Output -Encoding ASCII
Write-Host "Checksums: $Output ($($Files.Count) files)"
