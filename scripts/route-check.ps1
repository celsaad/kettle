<#
.SYNOPSIS
  Steers the running shell through every route by navigate message and checks what each step leaves
  on screen, back included (#152 in docs/native-migration-plan.md, M3).

.DESCRIPTION
  Each step sends a route to the shell, presses back or taps a button, then waits for an exact text
  to be on screen, read from a uiautomator dump. It stops at the first step that doesn't get there.
  The messages go through NavigateReceiver, which only the debug build has, so this needs
  com.casco.kettle.dev from native-dev.ps1, running against Metro and already past its splash.

  The expected texts are English UI strings plus names from the seed library, so the phone has to be
  in English and the .dev install has to still hold the seed's exercises, workouts and program.
  The runner steps finish a real session, which joins the .dev history. It never touches
  com.casco.kettle.

  What the matrix pins: tabs in any order, every modal and back from it, a message sent from a modal
  (which must not stack a second copy of the tabs, since back would then walk through stale screens),
  and a message over a running session, which must wait on "Finish session?".

  The matrix describes today's routes, so it moves with the migration. When a screen goes native, its
  steps stop going through a navigate message and its expected text may change. When native owns
  navigation, back is native's job, not JS's. Update the matrix in the same PR, rather than deleting a
  step that starts failing.

.PARAMETER Device
  The adb serial to use when more than one device is attached.

.EXAMPLE
  ./scripts/route-check.ps1 -Device RXCY204V03W
#>
param(
  [string]$Device
)

$ErrorActionPreference = 'Stop'
$package = 'com.casco.kettle.dev'
$receiver = "$package/com.casco.kettle.shell.rn.NavigateReceiver"

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

# A route: send it. 'back': press back. 'tap:X': tap the element whose text is X. 'front': bring the
# app to the front. Expect is an exact on-screen text; $null means the app is not in front.
$steps = @(
  # Tabs, out of order and repeated.
  @('/history', 'History'), @('/library', 'Library'), @('/programs', 'Programs'), @('/', 'Workouts'),
  @('/programs', 'Programs'), @('/history', 'History'), @('/history', 'History'),
  # Every modal from a tab, back to that tab after each.
  @('/settings', 'Settings'), @('back', 'History'),
  @('/analytics', 'Your numbers'), @('back', 'History'),
  @('/support', 'Support Kettle'), @('back', 'History'),
  @('/import', 'Import library'), @('back', 'History'),
  @('/exercise-editor', 'New exercise'), @('back', 'History'),
  @('/exercise-editor?id=pushups', 'Edit exercise'), @('back', 'History'),
  @('/workout-editor', 'New workout'), @('back', 'History'),
  @('/workout-editor?id=foundations-push', 'Edit workout'), @('back', 'History'),
  @('/exercise-progress?exerciseId=pushups', 'Push-ups'), @('back', 'History'),
  @('/program-detail?programId=foundations', 'Week 1 · Day 1'), @('back', 'History'),
  @('/program-editor', 'New program'), @('back', 'History'),
  @('/program-editor?id=foundations', 'Edit program'), @('back', 'History'),
  @('/program-guide', 'Writing a program by hand'), @('back', 'History'),
  @('/session-editor', 'Edit session'), @('back', 'History'),
  # From a modal: to a tab, and to another modal. Either way back reaches Workouts and then leaves.
  @('/library', 'Library'), @('/settings', 'Settings'), @('/history', 'History'), @('back', 'Workouts'),
  @('/library', 'Library'), @('/settings', 'Settings'), @('/support', 'Support Kettle'),
  @('back', 'Library'), @('back', 'Workouts'), @('back', $null),
  # Sent while in the background, it lands, and shows once the app is in front again.
  @('/history', $null), @('front', 'History'),
  # Over a running session. Cancel must leave the runner on top, with nothing stacked over it.
  @('/session?workoutId=foundations-push', 'Finish'),
  @('/library', 'Finish session?'), @('tap:CANCEL', 'Finish'),
  @('back', 'Finish session?'), @('tap:CANCEL', 'Finish'),
  @('/programs', 'Finish session?'), @('tap:FINISH', 'Workout complete'),
  @('tap:Done', 'History'), @('back', 'Workouts'), @('back', $null)
)

function Get-Screen {
  & adb @target shell uiautomator dump /sdcard/route-check.xml | Out-Null
  (& adb @target exec-out cat /sdcard/route-check.xml) -join ''
}

# Matches a dump attribute whose text is exactly $text.
function Get-TextPattern([string]$text) {
  "text=`"$([regex]::Escape([System.Security.SecurityElement]::Escape($text)))`""
}

# Brings the app to the front, launching it if it isn't running.
function Start-App {
  & adb @target shell monkey -p $package -c android.intent.category.LAUNCHER 1 *> $null
}

function Send-Route([string]$url) {
  # Quoted for the device's shell, which would otherwise read the ? and & in a query string.
  & adb @target shell am broadcast -n $receiver --es url "'$url'" | Out-Null
}

function Test-Foreground {
  (& adb @target shell dumpsys activity activities | Select-String -Pattern 'topResumedActivity' |
    Select-Object -First 1) -match [regex]::Escape($package)
}

# Polls rather than sleeping a fixed time. A dump takes about a second on the S25+, which sets the
# pace, and 8 s covers a modal's animation plus the runner's first render with room to spare.
function Wait-For([string]$text) {
  $deadline = (Get-Date).AddSeconds(8)
  do {
    Start-Sleep -Milliseconds 500
    if ($null -eq $text) { if (-not (Test-Foreground)) { return $true } }
    elseif ((Get-Screen) -match (Get-TextPattern $text)) { return $true }
  } while ((Get-Date) -lt $deadline)
  $false
}

# The texts carry "·", and adb's output is decoded with the console's encoding.
$previousEncoding = [Console]::OutputEncoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try {
  # The runner asks for it on its first start, and the prompt would sit over every later step.
  & adb @target shell pm grant $package android.permission.POST_NOTIFICATIONS
  Start-App
  # From wherever the last run or the user left it.
  Send-Route '/'
  if (-not (Wait-For 'Workouts')) { throw "$package isn't showing Home. Start it against Metro first." }

  $n = 0
  foreach ($step in $steps) {
    $n++
    $action, $expect = $step
    if ($action -eq 'back') {
      & adb @target shell input keyevent KEYCODE_BACK
    } elseif ($action -eq 'front') {
      Start-App
    } elseif ($action.StartsWith('tap:')) {
      $label = $action.Substring(4)
      $match = [regex]::Match((Get-Screen), "$(Get-TextPattern $label)[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
      if (-not $match.Success) { throw "Step ${n}: nothing on screen reads '$label'" }
      $x = ([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2
      $y = ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
      & adb @target shell input tap $x $y
    } else {
      Send-Route $action
    }
    $shown = if ($null -eq $expect) { '(left the app)' } else { $expect }
    if (-not (Wait-For $expect)) { throw "Step ${n}: '$action' should show '$shown', and didn't" }
    Write-Host ('{0,3}  {1,-42} {2}' -f $n, $action, $shown)
  }
  Write-Host "`nAll $n steps landed where they should." -ForegroundColor Green
} finally {
  [Console]::OutputEncoding = $previousEncoding
}
