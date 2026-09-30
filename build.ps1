param([string]$Task = 'build', [switch]$Offline, [switch]$JoinTestServer)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$javaCandidate = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon'
if (-not $env:JAVA_HOME -and (Test-Path (Join-Path $javaCandidate 'bin\javac.exe'))) { $env:JAVA_HOME = $javaCandidate }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home' }
$gradleArgs = @($Task, '--no-daemon')
if ($Offline) { $gradleArgs += '--offline' }
if ($JoinTestServer) { $gradleArgs += '-PhifiJoin=true' }
& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
