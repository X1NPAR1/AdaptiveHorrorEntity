<#
.SYNOPSIS
  Builds the Forge and Fabric jars for Adaptive Horror Entity.

.DESCRIPTION
  The Architectury Loom plugin must run on JDK 17+, while Forge 1.16.5 compiles against JDK 8. This
  script runs Gradle on JDK 17 and lets the Gradle Java toolchain pick up JDK 8 for compilation.

  Requires: JDK 17 and JDK 8 installed (Eclipse Adoptium by default), and Gradle 7.6.x. If `gradle`
  is not on PATH, set $env:GRADLE_BIN to your gradle.bat.
#>

$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot

function Find-Jdk([string]$majorPrefix) {
    $base = "C:\Program Files\Eclipse Adoptium"
    if (Test-Path $base) {
        $hit = Get-ChildItem $base -Directory | Where-Object { $_.Name -like "jdk-$majorPrefix*" } | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

$jdk17 = Find-Jdk "17"
if (-not $jdk17) { throw "JDK 17 not found under Eclipse Adoptium. Install Temurin 17 (winget install EclipseAdoptium.Temurin.17.JDK)." }
if (-not (Find-Jdk "8")) { Write-Warning "JDK 8 not detected - Forge 1.16.5 compilation may fail. Install Temurin 8." }

$env:JAVA_HOME = $jdk17
Write-Host "JAVA_HOME (Gradle runtime) = $jdk17"

$gradle = if ($env:GRADLE_BIN) { $env:GRADLE_BIN } elseif (Get-Command gradle -ErrorAction SilentlyContinue) { "gradle" } else { "C:\gradle-dist\gradle-7.6.4\bin\gradle.bat" }
if ($gradle -ne "gradle" -and -not (Test-Path $gradle)) { throw "Gradle not found. Set `$env:GRADLE_BIN to your gradle.bat (Gradle 7.6.x)." }

& $gradle -p $projectDir --no-daemon --console=plain clean build
if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)." }

Write-Host ""
Write-Host "Build OK. Jars:" -ForegroundColor Green
Get-ChildItem "$projectDir\forge\build\libs","$projectDir\fabric\build\libs" -Filter "*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '(-sources|-dev)' } |
    ForEach-Object { Write-Host "  $($_.FullName)" }
