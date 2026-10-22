<#
.SYNOPSIS
  Cold start to a drawn home screen, the native shell against today's app, on a phone
  (native/android/benchmark, #150 in docs/native-migration-plan.md).

.DESCRIPTION
  Builds both as side-by-side release builds, installs them with the Macrobenchmark APK, and runs it.
  Today's app is the prebuilt project's release build turned into com.casco.kettle.bench by an init
  script, so android/ is never edited; the shell is its own `benchmark` build type,
  com.casco.kettle.bench2. Both call reportFullyDrawn from the same JS once Home has laid out, so read
  timeToFullDisplayMs.

  Never the Play install: the benchmark force-stops its targets and resets their compiled code, and
  com.casco.kettle holds the phone owner's real library and history. Nothing here names it.

  Each target is launched once before the run, so the first-launch seed write isn't in iteration one.

.PARAMETER SkipBuild
  Reuse the APKs already built.

.PARAMETER Device
  The adb serial to use when more than one device is attached.

.EXAMPLE
  ./scripts/startup-benchmark.ps1 -Device RXCY204V03W
#>
param(
  [switch]$SkipBuild,
  [string]$Device
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$native = "$root/native/android"
$abi = '-PreactNativeArchitectures=arm64-v8a'
$targets = @('com.casco.kettle.bench', 'com.casco.kettle.bench2')
$runner = 'com.casco.kettle.benchmark/androidx.test.runner.AndroidJUnitRunner'

function Invoke-Step([string]$name, [scriptblock]$step) {
  Write-Host "`n== $name" -ForegroundColor Cyan
  & $step
  if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

$apks = @{
  'com.casco.kettle.bench' = "$root/android/app/build/outputs/apk/release/app-release.apk"
  'com.casco.kettle.bench2' = "$native/app/build/outputs/apk/benchmark/app-benchmark.apk"
}
$benchmarkApk = "$native/benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk"

if (-not $SkipBuild) {
  Invoke-Step "Build today's app (com.casco.kettle.bench)" {
    & "$root/android/gradlew.bat" -p "$root/android" --init-script "$native/benchmark/rn-app.init.gradle" `
      :app:assembleRelease $abi
  }
  # All, not Release: the two publications share one coordinate (see native-dev.ps1).
  Invoke-Step 'Publish the RN AAR to mavenLocal' {
    & "$root/android/gradlew.bat" -p "$root/android" publishBrownfieldAllPublicationToMavenLocal $abi
  }
  Invoke-Step 'Build the shell (com.casco.kettle.bench2) and the benchmark' {
    & "$native/gradlew.bat" -p $native :app:assembleBenchmark :benchmark:assembleBenchmark $abi
  }
}

# --user 0: a Secure Folder user makes plain pm calls fail. --no-streaming: streamed installs of a
# large APK have arrived corrupt (native-dev.ps1).
foreach ($package in $targets) {
  Invoke-Step "Install $package" { adb @target install --user 0 -r --no-streaming $apks[$package] }
}
Invoke-Step 'Install the benchmark' { adb @target install --user 0 -r --no-streaming $benchmarkApk }

foreach ($package in $targets) {
  Invoke-Step "First launch of $package" {
    adb @target shell am start -W -n "$package/com.casco.kettle.MainActivity"
  }
  Start-Sleep -Seconds 5
  Invoke-Step "Stop $package" { adb @target shell am force-stop $package }
}

Invoke-Step 'Run the benchmark' { adb @target shell am instrument -w --user 0 $runner }

$results = '/sdcard/Android/media/com.casco.kettle.benchmark/'
$out = "$native/benchmark/build/results"
New-Item -ItemType Directory -Force $out | Out-Null
Invoke-Step 'Pull the results' { adb @target pull $results $out }
Write-Host "`nResults in $out" -ForegroundColor Green
