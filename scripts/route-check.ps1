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
  steps stop going through a navigate message and its expected text may change. Since the shell's tab
  bar (M3), tab steps check which tab it has selected, and back between tabs is the shell's. Update
  the matrix in the same PR, rather than deleting a step that starts failing.

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
# app to the front. Expect is an exact on-screen text, or 'tab:X' for the shell's tab X being the
# selected one (its label is on screen whichever tab is showing, so the text alone proves nothing);
# $null means the app is not in front.
$steps = @(
  # Tabs, out of order and repeated. A message moves RN, and the shell's bar follows its report.
  @('/history', 'tab:History'), @('/library', 'tab:Library'), @('/programs', 'tab:Programs'),
  @('/', 'tab:Workouts'), @('/programs', 'tab:Programs'), @('/history', 'tab:History'),
  @('/history', 'tab:History'),
  # The shell's own bar and back stack: Workouts first, then out of the app.
  @('tap:Library', 'tab:Library'), @('tap:Programs', 'tab:Programs'), @('back', 'tab:Workouts'),
  @('back', $null), @('front', 'tab:Workouts'), @('tap:History', 'tab:History'),
  # Every modal from a tab, back to that tab after each.
  @('/settings', 'Settings'), @('back', 'tab:History'),
  @('/analytics', 'Your numbers'), @('back', 'tab:History'),
  @('/support', 'Support Kettle'), @('back', 'tab:History'),
  @('/import', 'Import library'), @('back', 'tab:History'),
  @('/exercise-editor', 'New exercise'), @('back', 'tab:History'),
  @('/exercise-editor?id=pushups', 'Edit exercise'), @('back', 'tab:History'),
  @('/workout-editor', 'New workout'), @('back', 'tab:History'),
  @('/workout-editor?id=foundations-push', 'Edit workout'), @('back', 'tab:History'),
  @('/exercise-progress?exerciseId=pushups', 'Push-ups'), @('back', 'tab:History'),
  @('/program-detail?programId=foundations', 'Week 1 · Day 1'), @('back', 'tab:History'),
  @('/program-editor', 'New program'), @('back', 'tab:History'),
  @('/program-editor?id=foundations', 'Edit program'), @('back', 'tab:History'),
  @('/program-guide', 'Writing a program by hand'), @('back', 'tab:History'),
  @('/session-editor', 'Edit session'), @('back', 'tab:History'),
  # From a modal: to a tab, and to another modal. Either way back reaches Workouts and then leaves.
  @('/library', 'tab:Library'), @('/settings', 'Settings'), @('/history', 'tab:History'),
  @('back', 'tab:Workouts'),
  @('/library', 'tab:Library'), @('/settings', 'Settings'), @('/support', 'Support Kettle'),
  @('back', 'tab:Library'), @('back', 'tab:Workouts'), @('back', $null),
  # Sent while in the background, it lands, and shows once the app is in front again.
  @('/history', $null), @('front', 'tab:History'),
  # Over a running session. Cancel must leave the runner on top, with nothing stacked over it.
  @('/session?workoutId=foundations-push', 'Finish'),
  @('/library', 'Finish session?'), @('tap:CANCEL', 'Finish'),
  @('back', 'Finish session?'), @('tap:CANCEL', 'Finish'),
  @('/programs', 'Finish session?'), @('tap:FINISH', 'Workout complete'),
  @('tap:Done', 'tab:History'), @('back', 'tab:Workouts'), @('back', $null)
)

# A dump that fails (it gives up on a screen that isn't idle for a second) writes nothing, so without
# the rm the last step's file would be read as this one's, and pass on a text both screens share.
# A failed dump returns an empty string instead, which shows nothing.
function Get-Screen {
  $read = 'f=/sdcard/route-check.xml; rm -f $f; uiautomator dump $f >/dev/null 2>&1; cat $f 2>/dev/null; true'
  (& adb @target exec-out $read) -join ''
}

# Matches a dump attribute whose text is exactly $text.
function Get-TextPattern([string]$text) {
  "text=`"$([regex]::Escape([System.Security.SecurityElement]::Escape($text)))`""
}

# Whether a dump shows what a step expects. 'tab:X' is a selected node with the label X inside it:
# the dump lists Compose's nodes unmerged, so a tab's selected state is on the item and its label is
# a TextView under it.
function Test-Screen([string]$screen, [string]$expect) {
  if (-not $screen) { return $false }
  if (-not $expect.StartsWith('tab:')) { return $screen -match (Get-TextPattern $expect) }
  $label = $expect.Substring(4)
  $dump = [xml]$screen
  $null -ne $dump.SelectSingleNode("//node[@selected='true'][.//node[@text='$label']]")
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
    elseif (Test-Screen (Get-Screen) $text) { return $true }
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
  if (-not (Wait-For 'tab:Workouts')) { throw "$package isn't showing Home. Start it against Metro first." }

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
      # By its text, or by its description: an unselected tab shows no label, and its icon names it.
      $name = (Get-TextPattern $label) -replace '^text=', '(?:text|content-desc)='
      $match = [regex]::Match((Get-Screen), "$name[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
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
