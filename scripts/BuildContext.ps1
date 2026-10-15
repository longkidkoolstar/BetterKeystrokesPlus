$ErrorActionPreference = 'Stop'

function Get-StreamProofBuildContext {
    param([string]$Root, [string]$BaseJar, [string]$JavaHome)
    if (-not (Test-Path -LiteralPath $BaseJar -PathType Leaf)) { throw "Base JAR not found: $BaseJar" }
    $basePath = (Resolve-Path -LiteralPath $BaseJar).Path
    $expectedBase = 'c4a15b140901b8b13a467e0a502ea7a7198fc54d280f0f39968b7a9ea9bfa408'
    if ((Get-FileHash -LiteralPath $basePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedBase) {
        throw 'Unsupported base JAR hash. Use the original supported JAR, not a previous Stream Proof build. See NOTICE.md.'
    }
    if ($JavaHome) {
        $java = Join-Path $JavaHome 'bin/java.exe'
        $javac = Join-Path $JavaHome 'bin/javac.exe'
    } else {
        $java = (Get-Command java -ErrorAction Stop).Source
        $javac = (Get-Command javac -ErrorAction Stop).Source
    }
    $dependencyDirectory = Join-Path $Root '.build/deps'
    New-Item -ItemType Directory -Force -Path $dependencyDirectory | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $dependencies = @{}
    $manifest = Get-Content -LiteralPath (Join-Path $Root 'dependencies.json') -Raw | ConvertFrom-Json
    foreach ($dependency in $manifest) {
        $destination = Join-Path $dependencyDirectory $dependency.file
        if (-not (Test-Path -LiteralPath $destination)) {
            Write-Host "Downloading $($dependency.name)..."
            Invoke-WebRequest -Uri $dependency.url -OutFile $destination -UseBasicParsing
        }
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $dependency.sha256) {
            throw "Dependency hash mismatch: $($dependency.name). Remove the cached file and investigate before retrying."
        }
        $dependencies[$dependency.name] = $destination
    }
    $productionClasses = Join-Path $Root '.build/classes-prod'
    New-Item -ItemType Directory -Force -Path $productionClasses | Out-Null
    $compileClasspath = @($basePath, $dependencies['forge'], $dependencies['lwjgl'], $dependencies['asm'], $dependencies['asm-tree']) -join ';'
    return [PSCustomObject]@{
        Root=$Root; BaseJar=$basePath; BaseHash=$expectedBase; Java=$java; Javac=$javac
        Dependencies=$dependencies; Classes=$productionClasses; Classpath=$compileClasspath
    }
}
