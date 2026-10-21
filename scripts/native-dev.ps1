<#
.SYNOPSIS
  The native shell's dev loop on Windows: the RN app as an AAR, the shell around it on a phone, and
  Metro feeding it with fast refresh (docs/native-migration-plan.md, M2).

.DESCRIPTION
  The expo-brownfield CLI can't run on Windows (it spawns ./gradlew, which cmd doesn't resolve), so
  this calls the Gradle task it wraps directly.

  The AAR only needs rebuilding when native code or a native dependency changes: ~30 min cold, a few
  on a warm cache. A JS change needs nothing from here but Metro, so pass -SkipAar.

  Installs com.casco.kettle.dev only, which sits beside the Play install. Nothing here can touch
  com.casco.kettle.

.PARAMETER SkipAar
  Reuse the AAR already in mavenLocal.

.PARAMETER Prebuild
  Regenerate android/ first. Needed after a change to app.json, a config plugin or a native
  dependency, and on a fresh checkout (it happens anyway when android/ is missing).

.PARAMETER Device
  The adb serial to use when more than one device is attached.

.EXAMPLE
  ./scripts/native-dev.ps1 -SkipAar
#>
param(
  [switch]$SkipAar,
  [switch]$Prebuild,
  [string]$Device
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
# arm64 alone: every phone this loop targets is one, and each extra ABI adds ~15 min to the AAR.
$abi = '-PreactNativeArchitectures=arm64-v8a'

function Invoke-Step([string]$name, [scriptblock]$step) {
  Write-Host "`n== $name" -ForegroundColor Cyan
  & $step
  if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

if ($Device) { $env:ANDROID_SERIAL = $Device }

if ($Prebuild -or -not (Test-Path "$root/android")) {
  Invoke-Step 'Prebuild android/' {
    $env:CI = '1'
    & "$root/node_modules/.bin/expo.cmd" prebuild --platform android --no-install
    Remove-Item Env:CI
  }
}

if (-not $SkipAar) {
  # All, not Debug: the two publications share one coordinate, so publishing either alone overwrites
  # the other's metadata and leaves the shell's other variant resolving nothing.
  Invoke-Step 'Publish the RN AAR to mavenLocal' {
    & "$root/android/gradlew.bat" -p "$root/android" publishBrownfieldAllPublicationToMavenLocal $abi
  }
}

Invoke-Step 'Install the shell (com.casco.kettle.dev)' {
  & "$root/native/android/gradlew.bat" -p "$root/native/android" :app:installDebug
}

Invoke-Step 'Point the phone at Metro' { adb reverse tcp:8081 tcp:8081 }
Invoke-Step 'Launch' { adb shell am start -n com.casco.kettle.dev/com.casco.kettle.MainActivity }

# Not with CI=1, which turns reloads off. Stopping this leaves node holding 8081 on Windows; free the
# port before the next run.
Set-Location $root
& "$root/node_modules/.bin/expo.cmd" start --port 8081
