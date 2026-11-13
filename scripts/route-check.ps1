<#
.SYNOPSIS
  Steers the running shell through every route by navigate message and checks what each step leaves
  on screen, back included (#152 in docs/native-migration-plan.md, M3).

.DESCRIPTION
  Each step sends a route to the shell, presses back or taps a button, then waits for an exact text
  to be on screen, read from a uiautomator dump. It stops at the first step that doesn't get there.
  The messages go through NavigateReceiver, which the debug and benchmark builds have, so this needs
  com.casco.kettle.dev from native-dev.ps1, running and already past its splash, or with -Package
  the release-built com.casco.kettle.bench2 (`gradlew :app:installBenchmark`).

  The expected texts are English UI strings plus names from the seed library, so the phone has to be
  in English and the .dev install has to still hold the seed's exercises, workouts and program.
  The runner's steps finish real sessions, which join the .dev history (#306). It never touches
  com.casco.kettle.

  What the matrix pins: tabs in any order, every modal and back from it, a message sent from a modal
  (which must not stack a second copy of the tabs, since back would then walk through stale screens),
  and a message over a running session, which the runner drops without asking (#302).

  The matrix describes today's routes, so it moves with the migration. When a screen goes native, its
  steps stop going through a navigate message and its expected text may change. Since the shell's tab
  bar (M3), tab steps check which tab it has selected, and back between tabs is the shell's. Update
  the matrix in the same PR, rather than deleting a step that starts failing.

  A start opens the native runner, in every build since the flip release (#308), and is waited on
  past its count-in: its End only shows once the session runs. Every route is the same in debug
  and release, and routing is native's in both since #309.

.PARAMETER Device
  The adb serial to use when more than one device is attached.

.PARAMETER Package
  The install to steer: com.casco.kettle.dev, or com.casco.kettle.bench2 for a release-built shell.

.EXAMPLE
  ./scripts/route-check.ps1 -Device RXCY204V03W
#>
param(
  [string]$Device,
  [ValidateSet('com.casco.kettle.dev', 'com.casco.kettle.bench2')]
  [string]$Package = 'com.casco.kettle.dev'
)

$ErrorActionPreference = 'Stop'
$receiver = "$package/com.casco.kettle.shell.nav.NavigateReceiver"

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

# A route: send it. 'back': press back. 'tap:X': tap the element whose text is X. 'front': bring the
# app to the front. Expect is an exact on-screen text, or 'tab:X' for the shell's tab X being the
# selected one (its label is on screen whichever tab is showing, so the text alone proves nothing);
# $null means the app is not in front.
$steps = @(
  # Tabs, out of order and repeated, and the four-tab shell's paths, each landing on the tab and section
  # that now holds its screen (#411). A section is checked as a tab is: its segment is the selected node.
  @('/progress', 'tab:Progress'), @('/library', 'tab:Library'), @('/', 'tab:Today'),
  @('/history', 'tab:Sessions'), @('/programs', 'tab:Programs'), @('/analytics', 'tab:Stats'),
  @('/library?section=exercises', 'tab:Exercises'), @('/progress', 'tab:Progress'),
  # The shell's own bar and back stack: Today first, then out of the app. A tab keeps its section.
  @('tap:Library', 'tab:Exercises'), @('tap:Programs', 'tab:Programs'),
  @('tap:Progress', 'tab:Progress'), @('tap:Library', 'tab:Programs'), @('back', 'tab:Today'),
  @('back', $null), @('front', 'tab:Today'), @('tap:Progress', 'tab:Progress'),
  # Every modal from a tab, back to that tab after each.
  @('/settings', 'Settings'), @('back', 'tab:Progress'), @('/support', 'Support Kettle'),
  @('back', 'tab:Progress'), @('/import', 'Import library'), @('back', 'tab:Progress'),
  @('/exercise-editor', 'New exercise'), @('back', 'tab:Progress'),
  @('/exercise-editor?id=pushups', 'Edit exercise'), @('back', 'tab:Progress'),
  @('/workout-editor', 'New workout'), @('back', 'tab:Progress'),
  @('/workout-editor?id=foundations-push', 'Edit workout'), @('back', 'tab:Progress'),
  @('/workout-preview?workoutId=foundations-push', 'NEXT UP'), @('back', 'tab:Progress'),
  @('/workout-preview?programId=foundations&week=1&day=Day%201', 'NEXT UP · Week 1 · Day 1'),
  @('back', 'tab:Progress'), @('/exercise-progress?exerciseId=pushups', 'Push-ups'),
  @('back', 'tab:Progress'), @('/program-detail?programId=foundations', 'Week 1 · Day 1'),
  @('back', 'tab:Progress'), @('/program-editor', 'New program'), @('back', 'tab:Progress'),
  @('/program-editor?id=foundations', 'Edit program'), @('back', 'tab:Progress'),
  @('/program-guide', 'Writing a program by hand'), @('back', 'tab:Progress'),
  @('/session-editor', 'Edit session'), @('back', 'tab:Progress'),
  # A section's row opens its native sheet over the tab (#197, #233), and back returns to it.
  @('tap:Library', 'tab:Programs'), @('tap:Foundations · 4 Weeks · No Equipment', 'Week 1 · Day 1'),
  # Over that native sheet, the native editor and the native runner each come back to it (#155, #233).
  # Back over a running session asks first, End early's sheet (#417), and completion's Done leaves (#297, #306).
  @('tap:Edit program', 'Edit program'), @('back', 'Week 1 · Day 1'),
  @('tap:Start Week 1 · Day 1', 'End'), @('back', 'End the session early?'),
  @('tap:Save and end', 'Workout complete'), @('tap:Done', 'Week 1 · Day 1'),
  @('back', 'tab:Programs'),
  # Library's Exercises: a row and Import open their native sheets over it, and back returns to it (#234).
  @('tap:Exercises', 'tab:Exercises'), @('tap:Push-ups', 'Edit exercise'),
  @('back', 'tab:Exercises'), @('tap:Import', 'Import library'), @('back', 'tab:Exercises'),
  # Today's gear and New workout open native Settings and the native editor over it, and back returns
  # (#235, #238).
  @('tap:Today', 'tab:Today'), @('tap:Settings', 'Settings'), @('back', 'tab:Today'),
  @('tap:New workout', 'New workout'), @('back', 'tab:Today'),
  # From a modal: to a tab, and to another modal. Either way back reaches Today and then leaves.
  @('/library', 'tab:Library'), @('/settings', 'Settings'), @('/progress', 'tab:Progress'),
  @('back', 'tab:Today'), @('/library', 'tab:Library'), @('/settings', 'Settings'),
  @('/support', 'Support Kettle'), @('back', 'tab:Library'), @('back', 'tab:Today'),
  @('back', $null),
  # Sent while in the background, it lands, and shows once the app is in front again.
  @('/progress', $null), @('front', 'tab:Progress'),
  # Over the runner, a message is dropped with no question asked, and Done pops its slot (#302, #309).
  @('/session?workoutId=foundations-push', 'End'), @('/library', 'End'),
  @('back', 'End the session early?'), @('tap:Keep going', 'End'),
  @('back', 'End the session early?'), @('tap:Save and end', 'Workout complete'),
  @('tap:Done', 'tab:Progress'), @('back', 'tab:Today'), @('back', $null)
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
  # A section's segment (#411) is selectable without being a Tab, which Compose reports as checked.
  $null -ne $dump.SelectSingleNode("//node[@selected='true' or @checked='true'][.//node[@text='$label']]")
}

# Brings the app to the front, launching it if it isn't running.
function Start-App {
  & adb @target shell monkey -p $package -c android.intent.category.LAUNCHER 1 *> $null
}

function Send-Route([string]$url) {
  # Quoted for the device's shell, which would otherwise read the ? and & in a query string.
  & adb @target shell am broadcast -n $receiver --es url "'$url'" | Out-Null
}

# topResumedActivity from Android 10; below it, only mResumedActivity names the top (#364). From 10 on
# mResumedActivity is per task, so it's read only when there's no topResumedActivity.
function Test-Foreground {
  $dump = & adb @target shell dumpsys activity activities
  $top = $dump | Select-String -Pattern 'topResumedActivity' | Select-Object -First 1
  if (-not $top) { $top = $dump | Select-String -Pattern 'mResumedActivity' | Select-Object -First 1 }
  $top -match [regex]::Escape($package)
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
  # The runner asks for it on its first start, and the prompt would sit over every later step. Only from
  # Android 13, which added it: below that the grant fails, and minSdk is 26 (#364).
  $sdk = 0
  [void][int]::TryParse("$(& adb @target shell getprop ro.build.version.sdk)".Trim(), [ref]$sdk)
  if ($sdk -ge 33) {
    & adb @target shell pm grant $package android.permission.POST_NOTIFICATIONS
  }
  Start-App
  # From wherever the last run or the user left it.
  Send-Route '/'
  if (-not (Wait-For 'tab:Today')) { throw "$package isn't showing Home. Start it first." }

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
