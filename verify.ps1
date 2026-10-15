param(
    [Parameter(Mandatory=$true)][string]$BaseJar,
    [string]$JavaHome,
    [string]$MinecraftJar,
    [string]$CompatibilityJava
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts/BuildContext.ps1')
$context = Get-StreamProofBuildContext -Root $PSScriptRoot -BaseJar $BaseJar -JavaHome $JavaHome
$version = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'VERSION') -Raw).Trim()
$jar = Join-Path $PSScriptRoot "dist/BetterKeystrokesPlus-1.8.9-v$version.jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Run build.ps1 first.' }
$testClasses = Join-Path $PSScriptRoot '.build/classes-test'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$deps = $context.Dependencies
$classpath = @($jar, $testClasses, $context.Classes, $deps['forge'], $deps['lwjgl'], $deps['asm'], $deps['asm-tree'], $deps['asm-analysis']) -join ';'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Filter '*.java' -File | ForEach-Object FullName)
& $context.Javac -source 8 -target 8 -encoding UTF-8 -classpath $classpath -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& $context.Java -Xverify:all -classpath $classpath VerifyBuild $jar
if ($LASTEXITCODE -ne 0) { throw 'Bytecode checks failed.' }
& $context.Java -Xverify:all -classpath $classpath streamproof.HealthBowProbe
if ($LASTEXITCODE -ne 0) { throw 'Health/bow checks failed.' }
& $context.Java -Xverify:all -classpath $classpath streamproof.MotionPredictionProbe
if ($LASTEXITCODE -ne 0) { throw 'Adaptive motion prediction checks failed.' }
& $context.Java -Xverify:all -classpath $classpath streamproof.RodAimProbe
if ($LASTEXITCODE -ne 0) { throw 'Rod activation/trajectory checks failed.' }
$natives = Join-Path $PSScriptRoot '.build/natives'
New-Item -ItemType Directory -Force -Path $natives | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$nativeZip = [IO.Compression.ZipFile]::OpenRead($deps['lwjgl-natives'])
try {
    foreach ($entry in $nativeZip.Entries) {
        if ($entry.Name -like '*.dll') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $natives $entry.Name), $true)
        }
    }
} finally { $nativeZip.Dispose() }
& $context.Java "-Dorg.lwjgl.librarypath=$natives" -Xverify:all -classpath $classpath OverlayProbe
if ($LASTEXITCODE -ne 0) { throw 'Desktop overlay probe failed.' }
if (-not $CompatibilityJava) { $CompatibilityJava = $context.Java }
& $CompatibilityJava -Xverify:all -classpath $classpath streamproof.HealthBowProbe
if ($LASTEXITCODE -ne 0) { throw 'Legacy Java health/bow checks failed.' }
& $CompatibilityJava -Xverify:all -classpath $classpath streamproof.MotionPredictionProbe
if ($LASTEXITCODE -ne 0) { throw 'Legacy Java adaptive prediction checks failed.' }
& $CompatibilityJava -Xverify:all -classpath $classpath streamproof.RodAimProbe
if ($LASTEXITCODE -ne 0) { throw 'Legacy Java rod checks failed.' }
$legacyClasspath = "$($deps['jna-legacy']);$classpath"
& $CompatibilityJava "-Dorg.lwjgl.librarypath=$natives" -Xverify:all -classpath $legacyClasspath OverlayProbe
if ($LASTEXITCODE -ne 0) { throw 'Legacy JNA overlay probe failed.' }
& $CompatibilityJava -Xverify:all -classpath $legacyClasspath streamproof.WindowModeProbe
if ($LASTEXITCODE -ne 0) { throw 'Borderless restore probe failed.' }
& $CompatibilityJava "-Dorg.lwjgl.librarypath=$natives" -Xverify:all -classpath $legacyClasspath streamproof.NativeCaptureProbe
if ($LASTEXITCODE -ne 0) { throw 'Native OBS copy-sequence probe failed.' }
if ($MinecraftJar) {
    & $context.Java -Xverify:all -classpath $classpath DisplayTransformerProbe $MinecraftJar
    if ($LASTEXITCODE -ne 0) { throw 'Presentation transformer probe failed.' }
    & $CompatibilityJava -Xverify:all -classpath "$($deps['asm5']);$classpath" DisplayTransformerProbe $MinecraftJar
    if ($LASTEXITCODE -ne 0) { throw 'ASM 5 compatibility probe failed.' }
}
