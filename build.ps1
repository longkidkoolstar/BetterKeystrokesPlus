param(
    [Parameter(Mandatory=$true)][string]$BaseJar,
    [string]$JavaHome,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = $PSScriptRoot
. (Join-Path $repositoryRoot 'scripts/BuildContext.ps1')
$context = Get-StreamProofBuildContext -Root $repositoryRoot -BaseJar $BaseJar -JavaHome $JavaHome
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repositoryRoot 'dist' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$version = (Get-Content -LiteralPath (Join-Path $repositoryRoot 'VERSION') -Raw).Trim()
$output = Join-Path $OutputDirectory "BetterKeystrokesPlus-1.8.9-v$version.jar"
if ([IO.Path]::GetFullPath($output) -eq [IO.Path]::GetFullPath($context.BaseJar)) { throw 'Output must not overwrite the base JAR.' }
$sources = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'src') -Recurse -Filter '*.java' -File | ForEach-Object FullName)
# Compile a fresh production class set; never package obsolete anonymous/inner classes.
$classRoot = [IO.Path]::GetFullPath($context.Classes).TrimEnd('\') + '\'
$expectedRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot '.build/classes-prod')).TrimEnd('\') + '\'
if ($classRoot -ne $expectedRoot) { throw 'Unexpected production-class directory.' }
Get-ChildItem -LiteralPath $context.Classes -Recurse -Filter '*.class' -File | ForEach-Object {
    if (-not ([IO.Path]::GetFullPath($_.FullName).StartsWith($classRoot, [StringComparison]::OrdinalIgnoreCase))) { throw 'Class file escaped build directory.' }
    Remove-Item -LiteralPath $_.FullName
}
& $context.Javac -source 8 -target 8 -encoding UTF-8 -classpath $context.Classpath -d $context.Classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
& $context.Java -classpath "$($context.Classes);$($context.Classpath)" PatchJar $context.BaseJar $output $context.Classes
if ($LASTEXITCODE -ne 0) { throw 'Patching failed.' }
$metadata = [ordered]@{
    version=$version
    baseSha256=$context.BaseHash
    outputSha256=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
    builtAtUtc=[DateTime]::UtcNow.ToString('o')
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'build-info.json') -Encoding UTF8
Write-Host "Built: $output"
