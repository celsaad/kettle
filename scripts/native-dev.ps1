<#
.SYNOPSIS
  The native app's dev loop on Windows: build the debug app, install it on a phone, launch it.

.DESCRIPTION
  Installs com.casco.kettle.dev only, which sits beside the Play install. Nothing here can touch
  com.casco.kettle.

.PARAMETER Device
  The adb serial to use when more than one device is attached. Wireless debugging works too, and
  outlasts a flaky cable: pair once (`adb pair`), `adb connect`, then pass its ip:port here.

.EXAMPLE
  ./scripts/native-dev.ps1
#>
param(
  [string]$Device
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Step([string]$name, [scriptblock]$step) {
  Write-Host "`n== $name" -ForegroundColor Cyan
  & $step
  if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

Invoke-Step 'Build the app' {
  & "$root/native/android/gradlew.bat" -p "$root/native/android" :app:assembleDebug
}

# Not Gradle's installDebug: it streams the APK into the installer, and on a Galaxy S21 (Android 15)
# the debug APK, which is large, arrived failing its v2 signature digest while a plain copy of the
# same file matched byte for byte. That USB link also kept dropping, so the streaming may not be the
# whole cause; --no-streaming, which copies first and then installs, worked every time.
Invoke-Step 'Install the app (com.casco.kettle.dev)' {
  adb @target install -r --no-streaming "$root/native/android/app/build/outputs/apk/debug/app-debug.apk"
}

Invoke-Step 'Launch' { adb @target shell am start -n com.casco.kettle.dev/com.casco.kettle.MainActivity }
