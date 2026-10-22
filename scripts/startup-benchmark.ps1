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
$runner = 'com.casco.kettle.benchmark/androidx.test.runner.AndroidJUnitRunner'

function Invoke-Step([string]$name, [scriptblock]$step) {
  Write-Host "`n== $name" -ForegroundColor Cyan
  & $step
  if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

# The packages each APK must carry. An ordered map, so installs and first launches run in this order.
$apks = [ordered]@{
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

# Checked, not assumed: with -SkipBuild, today's app's APK is whatever its last release build left, and
# one built without the init script is com.casco.kettle itself. Only the key mismatch would then stand
# between this script and the real install.
$buildTools = Get-ChildItem "$env:ANDROID_HOME/build-tools" | Sort-Object { [version]$_.Name } | Select-Object -Last 1
foreach ($package in $apks.Keys) {
  $actual = & "$($buildTools.FullName)/aapt2" dump packagename $apks[$package]
  if ($actual -ne $package) { throw "$($apks[$package]) is $actual, not $package; rebuild without -SkipBuild" }
}

# --user 0: a Secure Folder user makes plain pm calls fail. --no-streaming: streamed installs of a
# large APK have arrived corrupt (native-dev.ps1).
foreach ($package in $apks.Keys) {
  Invoke-Step "Install $package" { adb @target install --user 0 -r --no-streaming $apks[$package] }
}
Invoke-Step 'Install the benchmark' { adb @target install --user 0 -r --no-streaming $benchmarkApk }

foreach ($package in $apks.Keys) {
  # `am start -W` exits 0 on a failed launch too; only its Status line says.
  $launch = adb @target shell am start -W -n "$package/com.casco.kettle.MainActivity"
  if ($LASTEXITCODE -ne 0 -or -not ($launch -match 'Status: ok')) { throw "First launch of $package failed:`n$launch" }
  Start-Sleep -Seconds 5
  Invoke-Step "Stop $package" { adb @target shell am force-stop $package }
}

$results = '/sdcard/Android/media/com.casco.kettle.benchmark/'
$out = "$native/benchmark/build/results"
# Both ends emptied first, so a run that dies before writing can't be read as the last one's results.
Invoke-Step 'Clear the last results on the phone' { adb @target shell rm -rf $results }
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null

# `am instrument -w` exits 0 when a test fails; only JUnit's summary line says, and it carries the
# count the results have to match.
Write-Host "`n== Run the benchmark" -ForegroundColor Cyan
$instrument = adb @target shell am instrument -w --user 0 $runner
$instrument | Write-Host
$passed = ($instrument | Select-String 'OK \((\d+) tests?\)').Matches
if ($LASTEXITCODE -ne 0 -or -not $passed) { throw 'The benchmark failed (no "OK (n tests)" from am instrument)' }
$expected = [int]$passed[0].Groups[1].Value

Invoke-Step 'Pull the results' { adb @target pull $results $out }

# A run whose marker never fired still passes: Macrobenchmark just leaves timeToFullDisplayMs out, or
# short of an iteration whose marker landed after the measured block. Either way it isn't a result.
$runs = @(Get-ChildItem $out -Recurse -Filter '*benchmarkData.json' | ForEach-Object {
    (Get-Content $_.FullName -Raw | ConvertFrom-Json).benchmarks
  })
if ($runs.Count -ne $expected) { throw "$expected tests passed but $($runs.Count) results came back" }
foreach ($run in $runs) {
  $full = $run.metrics.timeToFullDisplayMs
  $label = "$($run.className).$($run.name)"
  if (-not $full -or $full.runs.Count -ne $run.repeatIterations) {
    throw "$label has $($full.runs.Count) full-display times of $($run.repeatIterations): the marker didn't fire every time"
  }
  Write-Host ('{0}: median {1:N0} ms, range {2:N0}-{3:N0}' -f $run.name, $full.median, $full.minimum, $full.maximum)
}
Write-Host "`nResults in $out" -ForegroundColor Green
