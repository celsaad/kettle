<#
.SYNOPSIS
  The native shell's dev loop on Windows: the RN app as an AAR, the shell around it on a phone, and
  Metro feeding it with fast refresh (docs/native-migration-plan.md, M2).

.DESCRIPTION
  The expo-brownfield CLI can't run on Windows (it spawns ./gradlew, which cmd doesn't resolve), so
  this calls the Gradle task it wraps directly.

  The AAR only needs rebuilding when native code or a native dependency changes, and a cold build is
  the prebuilt project's whole native build. A JS change needs nothing from here but Metro, so pass
  -SkipAar.

  Installs com.casco.kettle.dev only, which sits beside the Play install. Nothing here can touch
  com.casco.kettle.

.PARAMETER SkipAar
  Reuse the AAR already in mavenLocal.

.PARAMETER Prebuild
  Regenerate android/ first. Needed after a change to app.json, a config plugin or a native
  dependency, and on a fresh checkout (it happens anyway when android/ is missing).

.PARAMETER Device
  The adb serial to use when more than one device is attached. Wireless debugging works too, and
  outlasts a flaky cable: pair once (`adb pair`), `adb connect`, then pass its ip:port here.

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
# arm64 alone: every phone this loop targets is one, and each extra ABI is another full C++ build.
$abi = '-PreactNativeArchitectures=arm64-v8a'

function Invoke-Step([string]$name, [scriptblock]$step) {
  Write-Host "`n== $name" -ForegroundColor Cyan
  & $step
  if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

# A clean checkout has no node_modules, and both builds read versions out of it.
if (-not (Test-Path "$root/node_modules")) {
  Invoke-Step 'Install dependencies' { pnpm -C $root install --frozen-lockfile }
}

if ($Prebuild -or -not (Test-Path "$root/android")) {
  Invoke-Step 'Prebuild android/' {
    # Cleared even on a failure, which would otherwise leave CI=1 in the caller's terminal.
    $env:CI = '1'
    try { & "$root/node_modules/.bin/expo.cmd" prebuild --platform android --no-install }
    finally { Remove-Item Env:CI }
  }
}

if (-not $SkipAar) {
  # All, not Debug: the two publications share one coordinate, so publishing either alone overwrites
  # the other's metadata and leaves the shell's other variant resolving nothing.
  Invoke-Step 'Publish the RN AAR to mavenLocal' {
    & "$root/android/gradlew.bat" -p "$root/android" publishBrownfieldAllPublicationToMavenLocal $abi
  }
}

Invoke-Step 'Build the shell' {
  & "$root/native/android/gradlew.bat" -p "$root/native/android" :app:assembleDebug
}

# Not Gradle's installDebug: it streams the APK into the installer, and on a Galaxy S21 (Android 15)
# the debug APK, which is large, arrived failing its v2 signature digest while a plain copy of the
# same file matched byte for byte. That USB link also kept dropping, so the streaming may not be the
# whole cause; --no-streaming, which copies first and then installs, worked every time.
Invoke-Step 'Install the shell (com.casco.kettle.dev)' {
  adb @target install -r --no-streaming "$root/native/android/app/build/outputs/apk/debug/app-debug.apk"
}

Invoke-Step 'Point the phone at Metro' { adb @target reverse tcp:8081 tcp:8081 }

# Metro first: an app launched before it answers finds nothing at 8081 and stays on its splash. Not
# with CI=1, which turns reloads off. Its output stays in this window; stopping the script can leave
# node holding 8081 on Windows, so free the port before the next run.
$metro = Start-Process "$root/node_modules/.bin/expo.cmd" -ArgumentList 'start', '--port', '8081' `
  -WorkingDirectory $root -NoNewWindow -PassThru
Write-Host "`n== Waiting for Metro" -ForegroundColor Cyan
do {
  Start-Sleep -Seconds 1
  if ($metro.HasExited) { throw 'Metro exited before it was ready' }
  # RawContent, not Content: Metro sends no text content type, so Content arrives as bytes.
  $status = try { (Invoke-WebRequest -Uri 'http://localhost:8081/status' -UseBasicParsing -TimeoutSec 2).RawContent } catch { '' }
} until ($status -match 'packager-status:running')

Invoke-Step 'Launch' { adb @target shell am start -n com.casco.kettle.dev/com.casco.kettle.MainActivity }
$metro.WaitForExit()
