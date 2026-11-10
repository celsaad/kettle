<#
.SYNOPSIS
  Runs the native runner's repeatable device checks unattended against com.casco.kettle.dev (#315).

.DESCRIPTION
  Leaving the count-in, a stale advance, cue cadence, the step-end alert's gating and content, the
  sounds preference, notifications denied, process death, and a rest's timing with the screen off,
  then the finish path. Each check prints PASS or FAIL on its own line.

  The twin of session-check.sh, step for step: the two change together, and that file's header is
  the reference for what this needs, what it reads as evidence, its caveats, and how it leaves the
  target. The test library they plant is session-check-library.yaml. What neither can check is the
  manual list in .github/pull_request_template.md.

.PARAMETER Device
  The adb serial to use when more than one device is attached.

.EXAMPLE
  ./scripts/session-check.ps1 -Device emulator-5554
#>
param(
  [string]$Device
)

# Not Stop: a check that fails says so and the run carries on to the next; setup steps throw themselves.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$package = 'com.casco.kettle.dev'
$data = "/data/data/$package"
$receiver = "$package/com.casco.kettle.shell.runner.RunnerCommandReceiver"
$navigator = "$package/com.casco.kettle.shell.nav.NavigateReceiver"
$apk = Join-Path $root 'native/android/app/build/outputs/apk/debug/app-debug.apk'
$pin = '1234'

# Per call, not $env:ANDROID_SERIAL, which would outlive the script in the caller's terminal.
$target = if ($Device) { @('-s', $Device) } else { @() }

function A { & adb @target @args }
function AsApp { & adb @target exec-out run-as $package @args }

$script:passed = 0
$script:failed = 0
function Check([string]$Name, [bool]$Ok, [string]$Seen = '') {
  if ($Ok) {
    $script:passed++; Write-Host 'PASS ' -ForegroundColor Green -NoNewline; Write-Host $Name
  } else {
    $script:failed++; Write-Host 'FAIL ' -ForegroundColor Red -NoNewline
    Write-Host ($(if ($Seen) { "$Name ($Seen)" } else { $Name }))
  }
}
function Skip([string]$Name) { Write-Host 'SKIP ' -ForegroundColor Yellow -NoNewline; Write-Host $Name }
# A section's header. Waits for the target first: an adb server restarting mid-run marks it offline.
function Step([string]$Name) {
  Write-Host "`n== $Name" -ForegroundColor Cyan
  $job = Start-Job { param($t) & adb @t wait-for-device } -ArgumentList (, $target)
  Wait-Job $job -Timeout 30 | Out-Null; Remove-Job $job -Force
}

# --- The receiver, the device's clock and files ---------------------------------------------------

# The receiver's answer: the broadcast's result data.
function Rc { (A shell am broadcast -n $receiver --es cmd @args) -join "`n" -replace '(?s).*data="(.*)"\s*$', '$1' }
# Retried, being only a read: this adb's server can restart mid-run, and a read that lands while the
# device is offline comes back empty. Commands are never retried: a second advance would skip a step.
function Report {
  foreach ($i in 1..3) {
    $r = Rc report
    if ($r -match '^\{') { return $r | ConvertFrom-Json }
    WaitFor 10 { ((A get-state 2>$null) -join '') -eq 'device' } | Out-Null
  }
  return $null
}
function Field([string]$Name) { $r = Report; if ($r) { $r.$Name } else { $null } }
function View { Field view }

# Quoted for the device's shell, which would otherwise read the ? in a query string.
function Navigate([string]$Url) { A shell am broadcast -n $navigator --es url "'$Url'" | Out-Null }

function NowMs { [long]((A shell date +%s%N) -replace '\D', '').Substring(0, 13) }

# Polls [condition] every half second for up to [seconds].
function WaitFor([int]$Seconds, [scriptblock]$Condition) {
  $until = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -le $until) {
    if (& $Condition) { return $true }
    Start-Sleep -Milliseconds 500
  }
  return $false
}

# exec-out carries the device's stderr too, so a missing file is silenced there, not here.
function InApp([string]$Command) { AsApp sh -c "$Command 2>/dev/null" }
function StateFiles { (InApp "ls $data/no_backup/runner") -join '' }
function StateOf([string]$Id) { $j = (InApp "cat $data/no_backup/runner/$Id.json") -join ''; if ($j) { $j | ConvertFrom-Json } else { $null } }
function SessionFile([string]$Id) { (InApp "cat $data/files/exercise-tracker/sessions/$Id.yaml") -join "`n" }
function SessionCount { @((InApp "ls $data/files/exercise-tracker/sessions") -split '\s+' | Where-Object { $_ }).Count }
function StepIndex { Field stepIndex }

# The focus requests (or, with -Kind abandon, abandons) the app made from [from] to [to] (now, if not
# given), device wall clock, as milliseconds of the local day. dumpsys audio stamps them in local time,
# so the bounds are shifted by the device's own offset; a window across midnight wraps. Its history is
# capped, so a window has to be recent: count within one, never lines overall.
function FocusSince([long]$From, [long]$To = 0, [string]$Kind = 'request') {
  if (-not $To) { $To = NowMs }
  $offset = (A shell date +%z) -join ''
  $shift = ([int]$offset.Substring(1, 2) * 3600 + [int]$offset.Substring(3, 2) * 60) * 1000
  if ($offset.StartsWith('-')) { $shift = -$shift }
  $day = 86400000
  $lo = ((($From + $shift) % $day) + $day) % $day
  $hi = ((($To + $shift) % $day) + $day) % $day
  A shell dumpsys audio | ForEach-Object {
    $f = $_ -split '\s+' | Where-Object { $_ }
    if ($f.Count -ge 3 -and $f[2] -eq "${Kind}AudioFocus()" -and $_.Contains("callingPack=$package")) {
      $t = $f[1] -split ':'
      $ms = (([int]$t[0] * 60 + [int]$t[1]) * 60 + [int]$t[2]) * 1000 + [int]$t[3]
      if ($(if ($lo -le $hi) { $ms -ge $lo -and $ms -le $hi } else { $ms -ge $lo -or $ms -le $hi })) { $ms }
    }
  }
}
# @() of nothing passed as an argument is one $null, so nulls are dropped before counting.
function Count($Items) { @($Items | Where-Object { $null -ne $_ }).Count }

# The app's posts in the live list, one line each: id, channel, flags, title and text.
function LiveNotifications {
  $live = $false; $mine = $false; $rec = $null
  foreach ($line in (A shell dumpsys notification --noredact)) {
    if ($line -match 'Notification List:') { $live = $true; continue }
    if ($line -match 'mArchive=|Snoozed notifications:') { $live = $false }
    if (-not $live) { continue }
    if ($line -match 'NotificationRecord\(') {
      if ($rec) { $rec }
      $rec = $null
      $mine = $line.Contains("pkg=$package")
      if ($mine -and $line -match ' id=(\d+)') {
        $rec = "id=$($Matches[1])"
        if ($line -match '(channel=\S+)') { $rec += " $($Matches[1])" }
        if ($line -match '(flags=[A-Z_|]+)') { $rec += " $($Matches[1])" }
      }
    } elseif ($mine -and $line -match 'android\.title=String \((.*)\)$') {
      $rec += " title=$($Matches[1])"
    } elseif ($mine -and $line -match 'android\.text=String \((.*)\)$') {
      $rec += " text=$($Matches[1])"
    }
  }
  if ($rec) { $rec }
}
# The step-end alert, if one is up: id 3052 (the ongoing one is 3051), and checked for its channel.
function Alert { (LiveNotifications | Where-Object { $_ -like 'id=3052 *' }) -join '' }
$cuesChannel = 'expo_notifications_fallback_notification_channel'
function RecoveryAsking { (Field recovery) -eq 'Asking' }

function ServiceRunning { (A shell dumpsys activity services $package) -match 'RunnerService' }

# --- Opening and closing sessions -----------------------------------------------------------------

# Opens [workout] by navigate message and waits for its count-in. A message sent before the shell's activity
# is up is lost, so it's sent again until one lands; one landing over the runner is dropped by design.
# Each try puts the Metro tunnel back first: an adb server that restarts mid-run drops it, and the app
# can't load its JS without it, so after two misses it's relaunched too.
function OpenSession([string]$Workout) {
  foreach ($i in 1..5) {
    A reverse tcp:8081 tcp:8081 | Out-Null
    if ($i -eq 3) { Launch | Out-Null }
    Navigate "/session?workoutId=$Workout"
    if (WaitFor 6 { (View) -ne 'Idle' }) { return $true }
  }
  Write-Host "couldn't open $Workout"
  return $false
}
function WaitRunning([int]$Seconds = 8) { WaitFor $Seconds { (View) -eq 'Running' } }

# Finishes a running session or leaves a count-in, presses Back on the completion (Done), and waits for
# Idle.
function CloseSession {
  switch (View) {
    'Running' { Rc finish | Out-Null; WaitFor 5 { (View) -eq 'Completed' } | Out-Null }
    'CountingIn' { A shell input keyevent KEYCODE_BACK }
  }
  if ((View) -eq 'Completed') { A shell input keyevent KEYCODE_BACK }
  WaitFor 6 { (View) -eq 'Idle' } | Out-Null
  Start-Sleep -Seconds 1
}

function Launch {
  A shell am force-stop $package
  A shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
  WaitFor 30 { [bool](Report) }
}

function WritePreferences([string]$Sounds) {
  # The run's own: sounds as asked, and no backup folder, so a finish writes nothing outside the app.
  $file = Join-Path $work 'preferences.json'
  [IO.File]::WriteAllText($file, "{`"unitSystem`":`"metric`",`"themePreference`":`"system`",`"restDayReminder`":false,`"sessionSounds`":$Sounds,`"backupFolderUri`":null}")
  A push $file /data/local/tmp/session-check-preferences.json 2>&1 | Out-Null
  A shell "run-as $package sh -c 'cat /data/local/tmp/session-check-preferences.json > $data/files/exercise-tracker/preferences.json'"
  A shell rm -f /data/local/tmp/session-check-preferences.json
}

function Checksums { (AsApp sh -c "cd $data/files/exercise-tracker && find . -type f -exec md5sum {} + | sort -k2") -join "`n" }

# --- Setup and the way back -----------------------------------------------------------------------

$work = Join-Path ([IO.Path]::GetTempPath()) "session-check-$PID"
New-Item -ItemType Directory -Path $work -Force | Out-Null
$emulator = $false
$metro = $null
$pinSet = $false
$backedUp = $false
$checksumsBefore = ''
$code = 0

try {
  $status = try { (Invoke-WebRequest -Uri 'http://localhost:8081/status' -UseBasicParsing -TimeoutSec 2).RawContent } catch { '' }
  if ($status -notmatch 'packager-status:running') {
    Step 'Starting Metro'
    $expo = Join-Path $root "node_modules/.bin/$(if ($IsWindows) { 'expo.cmd' } else { 'expo' })"
    $metro = Start-Process $expo -ArgumentList 'start', '--port', '8081' -WorkingDirectory $root -PassThru `
      -RedirectStandardOutput (Join-Path $work 'metro.log') -RedirectStandardError (Join-Path $work 'metro.err')
    $readyBy = (Get-Date).AddMinutes(2)
    do {
      Start-Sleep -Seconds 1
      if ($metro.HasExited) { throw 'Metro exited before it was ready' }
      if ((Get-Date) -gt $readyBy) { throw "Metro wasn't ready in 2 minutes: see $work/metro.log" }
      # RawContent, not Content: Metro sends no text content type, so Content arrives as bytes.
      $status = try { (Invoke-WebRequest -Uri 'http://localhost:8081/status' -UseBasicParsing -TimeoutSec 2).RawContent } catch { '' }
    } until ($status -match 'packager-status:running')
    # Expo starts adb's server itself as it comes up, and on some adb builds that restarts the one
    # already running, dropping the device for a few seconds. So Metro comes first and settles.
    Start-Sleep -Seconds 8
  }
  A wait-for-device
  # Read once the target is surely online: a property read while adb restarts comes back empty.
  $serial = if ($Device) { $Device } else { (A get-serialno) -join '' }
  $emulator = $serial -like 'emulator-*' -or ((A shell getprop ro.boot.qemu) -join '') -eq '1' -or
    ((A shell getprop ro.kernel.qemu) -join '') -eq '1'

  Step 'Checking the APK and the target'
  if (-not (Test-Path $apk)) { throw "No APK at ${apk}: build the .dev app first." }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [IO.Compression.ZipFile]::OpenRead($apk)
  try {
    foreach ($sound in 'tick', 'milestone', 'exercise-change') {
      if (-not ($zip.Entries | Where-Object FullName -eq "assets/$sound.wav")) {
        throw "The APK has no $sound.wav: run gradlew :app:mergeDebugAssets --rerun, then build again."
      }
    }
  } finally { $zip.Dispose() }
  # Retried: installing is idempotent, and an adb server restarting under it leaves the device offline.
  $installed = $false
  foreach ($try in 1..3) {
    A install -r --no-streaming $apk 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { $installed = $true; break }
    Start-Sleep -Seconds 3; A wait-for-device
  }
  if (-not $installed) { throw "Couldn't install $apk." }
  if (StateFiles) { throw 'A session is running or waiting to be recovered: finish it first.' }

  Step 'Backing up .dev and planting the test library'
  $tar = Join-Path $work 'data.tar'
  # Start-Process, so the archive's bytes reach the file untouched by PowerShell's text pipeline.
  Start-Process adb -ArgumentList ($target + @('exec-out', 'run-as', $package, 'tar', '-cf', '-', 'files/exercise-tracker')) `
    -RedirectStandardOutput $tar -NoNewWindow -Wait
  $checksumsBefore = Checksums
  # Trusted only once there's something to put back: an empty baseline would make the restore's
  # comparison pass whatever happened, and an archive missing on the device would restore nothing.
  if (-not (Test-Path $tar) -or (Get-Item $tar).Length -eq 0 -or -not $checksumsBefore) { throw "Couldn't back up .dev's data folder." }
  A push $tar /data/local/tmp/session-check.tar 2>&1 | Out-Null
  if (((A shell stat -c %s /data/local/tmp/session-check.tar) -join '') -ne "$((Get-Item $tar).Length)") {
    throw "Couldn't copy the backup to the device."
  }
  $backedUp = $true
  # The whole library is replaced rather than added to, so no check depends on what the user's holds.
  $library = Join-Path $root 'scripts/session-check-library.yaml'
  A push $library /data/local/tmp/session-check-library.yaml 2>&1 | Out-Null
  A shell "run-as $package sh -c 'cat /data/local/tmp/session-check-library.yaml > $data/files/exercise-tracker/exercises.yaml'"
  A shell rm -f /data/local/tmp/session-check-library.yaml
  WritePreferences 'true'

  A reverse tcp:8081 tcp:8081 | Out-Null
  A shell input keyevent KEYCODE_WAKEUP
  A shell wm dismiss-keyguard 2>&1 | Out-Null
  if (-not (Launch)) { throw "$package didn't start." }

  # --- The checks ---------------------------------------------------------------------------------

  Step 'Leaving during the count-in'
  $before = SessionCount
  OpenSession 'zz-check-rest' | Out-Null
  Check 'the session opens on its count-in' ((View) -eq 'CountingIn')
  A shell input keyevent KEYCODE_BACK
  Check 'Back during the count-in leaves' (WaitFor 5 { (View) -eq 'Idle' })
  Start-Sleep -Seconds 1
  Check 'leaving the count-in leaves no state file' (-not (StateFiles))
  Check 'leaving the count-in leaves no session file' ((SessionCount) -eq $before)
  Start-Sleep -Seconds 1

  Step 'A stale advance'
  if (OpenSession 'zz-check-rest') { WaitRunning | Out-Null }
  $first = Report
  Rc advance | Out-Null
  Check 'an advance moves to the rest' (WaitFor 3 { (StepIndex) -eq 1 })
  # Delivered and then refused: a broadcast that never arrived would leave the step where it is too.
  $answer = if ($first) { Rc advance --el visit $first.visit } else { '' }
  Start-Sleep -Seconds 1
  $at = StepIndex
  Check 'an advance carrying the last visit is refused' ($answer -eq 'ok' -and $at -eq 1) "answer '$answer', on step $at"
  CloseSession

  Step 'Cue cadence, alert gating and content, sounds on'
  if (OpenSession 'zz-check-cues') { WaitRunning | Out-Null }
  Check 'the service runs in the foreground, typed health' ([bool]((A shell dumpsys activity services $package) -match 'isForeground=true .*types=0x00000100'))
  $id = Field sessionId
  $start = [long](StateOf $id).phaseStartedAt
  $countIn = Count (FocusSince ($start - 3500) ($start + 500))
  Check 'the count-in cues 3 ticks and the exercise change' ($countIn -eq 4) "$countIn focus requests"
  # The HIIT work is 10 s: its midpoint at 5, ticks at 7, 8 and 9, the change at 10. The runner is on
  # screen as it ends, so no alert may be posted.
  WaitFor 14 { (StepIndex) -eq 1 } | Out-Null
  Start-Sleep -Milliseconds 800
  $midpoint = Count (FocusSince ($start + 4500) ($start + 5500))
  Check 'HIIT work cues its midpoint' ($midpoint -eq 1) "$midpoint focus requests"
  $ending = @(FocusSince ($start + 6500) ($start + 10600))
  $stray = $false
  for ($i = 1; $i -lt $ending.Count; $i++) {
    $gap = $ending[$i] - $ending[$i - 1]
    if ($gap -lt 800 -or $gap -gt 1200) { $stray = $true }
  }
  Check "a countdown's last 3 s and its end cue a second apart" ($ending.Count -eq 4 -and -not $stray) "cues and stray gap: $($ending.Count) $([int]$stray)"
  # What the call check reads: a cue that plays gives its focus back once its sound ends.
  $released = Count (FocusSince ($start + 4500) ($start + 11400) 'abandon')
  Check 'a cue that plays gives its focus back' ($released -gt 0) "$released abandons"
  $seen = Alert
  Check 'no alert is posted while the runner is on screen' (-not $seen) $seen
  Rc visible --ez value false | Out-Null
  # The hold ends at 4 s, handing over to the AMRAP.
  WaitFor 8 { (StepIndex) -eq 2 } | Out-Null
  Start-Sleep -Milliseconds 800
  $hold = Alert
  Check 'a hold that ends off screen posts "Hold complete" and the next exercise' ($hold -like '*title=Hold complete*text=Check AMRAP*') $hold
  Check 'the alert is on the cues channel' ($hold -like "*channel=$cuesChannel *") $hold
  Check 'with sounds on, the alert is silent: the service played the cue' ($hold -like '*SILENT*') $hold
  Rc visible --ez value true | Out-Null
  Check 'the alert is cleared once the runner is on screen' (WaitFor 3 { -not (Alert) }) (Alert)
  Rc visible --ez value false | Out-Null
  # The AMRAP's 5 s cap ends the workout. The service stops with it, so this last alert stays up after;
  # whether it should clear is #327, a question for the app, not this check.
  WaitFor 9 { (View) -eq 'Completed' } | Out-Null
  Start-Sleep -Milliseconds 800
  $last = Alert
  Check "an AMRAP that ends the workout posts `"Time's up`" and `"Workout complete`"" ($last -like "*title=Time's up*text=Workout complete*") $last
  Rc visible --ez value true | Out-Null
  $backup = Field backup
  Check 'the completion reports the backup outcome' ($backup -eq 'NONE') "backup $backup"
  CloseSession

  Step 'The alert, with the app sent Home for real'
  if (OpenSession 'zz-check-rest') { WaitRunning | Out-Null }
  Rc advance | Out-Null
  WaitFor 3 { (StepIndex) -eq 1 } | Out-Null
  A shell input keyevent KEYCODE_HOME
  # The 6 s rest ends with the app in the background.
  WaitFor 9 { (StepIndex) -eq 2 } | Out-Null
  Start-Sleep -Milliseconds 800
  $rest = Alert
  Check 'a rest that ends with the app away posts "Rest complete" and the next exercise' ($rest -like '*title=Rest complete*text=Check Reps*') $rest
  A shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
  Check 'coming back to the runner clears the alert' (WaitFor 5 { -not (Alert) }) (Alert)
  CloseSession

  Step 'Process death, mid-set and mid-rest'
  function KillApp { A shell "run-as $package kill -9 `$(pidof $package)" }
  function TapText([string]$Text) {
    A shell uiautomator dump /sdcard/session-check.xml 2>&1 | Out-Null
    $xml = (A shell cat /sdcard/session-check.xml) -join ''
    $node = ($xml -split '>') | Where-Object { $_ -match "package=`"$package`"" -and $_ -match "text=`"$([regex]::Escape($Text))`"" } | Select-Object -First 1
    if ($node -notmatch 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') { return $false }
    A shell input tap ((([int]$Matches[1] + [int]$Matches[3]) / 2) -as [int]) ((([int]$Matches[2] + [int]$Matches[4]) / 2) -as [int])
    return $true
  }
  # History's banner title (en.json's history.problemsTitle), or nothing with no unreadable file, and the
  # session files that are empty (#333). The user's own sessions stay in the log through the run, so the
  # checks are that a kill adds nothing to either. Read only with History's tab selected, as route-check
  # reads it, so a dump that failed or a screen that never came can't pass as an empty banner.
  function HistoryBanner {
    Navigate '/history'
    Start-Sleep -Seconds 2
    $xml = (A exec-out 'f=/sdcard/session-check.xml; rm -f $f; uiautomator dump $f >/dev/null 2>&1; cat $f 2>/dev/null; true') -join ''
    try { $dump = [xml]$xml } catch { return 'History not on screen' }
    if ($null -eq $dump.SelectSingleNode("//node[@selected='true'][.//node[@text='History']]")) { return 'History not on screen' }
    $node = $dump.SelectSingleNode("//node[contains(@text, 'be read or written')]")
    if ($node) { $node.GetAttribute('text') } else { '' }
  }
  function EmptySessionFiles { (InApp "find $data/files/exercise-tracker/sessions -name '*.yaml' -empty") -join ' ' }
  $bannerBefore = HistoryBanner
  $emptyBefore = EmptySessionFiles
  if (OpenSession 'zz-check-rest') { WaitRunning | Out-Null }
  $id = Field sessionId
  KillApp
  Start-Sleep -Seconds 1
  Check 'killed mid-set, the state file survives' ([bool](StateOf $id))
  A shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
  Check 'the next launch offers Resume or Finish' (WaitFor 20 { RecoveryAsking })
  TapText 'Resume' | Out-Null
  Check 'Resume carries the session on' (WaitFor 8 { (View) -eq 'Running' }) (View)
  Rc advance | Out-Null
  WaitFor 3 { (StepIndex) -eq 1 } | Out-Null
  KillApp
  Start-Sleep -Seconds 1
  Check 'killed mid-rest, the state file survives' ([bool](StateOf $id))
  A shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
  Check 'the next launch offers the prompt again' (WaitFor 20 { RecoveryAsking })
  TapText 'Finish' | Out-Null
  Check "the prompt's Finish deletes the state file" (WaitFor 8 { -not (StateFiles) })
  Check "the prompt's Finish stamps ended_at" ((SessionFile $id) -match 'ended_at: 20')
  $empty = EmptySessionFiles
  Check 'two kills leave no new empty session file' ($empty -eq $emptyBefore) $empty
  $banner = HistoryBanner
  Check "History's banner lists nothing new after the relaunch" ($banner -eq $bannerBefore -and $banner -ne 'History not on screen') "'$banner', was '$bannerBefore'"
  Start-Sleep -Seconds 1

  Step 'Sounds off'
  WritePreferences 'false'
  Launch | Out-Null
  $from = NowMs
  if (OpenSession 'zz-check-cues') { WaitRunning | Out-Null }
  Rc visible --ez value false | Out-Null
  WaitFor 14 { (StepIndex) -eq 1 } | Out-Null
  Start-Sleep -Milliseconds 800
  $quiet = Count (FocusSince $from)
  Check 'with sounds off, no cue requests focus' ($quiet -eq 0) "$quiet focus requests"
  $interval = Alert
  Check 'an interval that ends off screen posts "Interval complete" and the next exercise' ($interval -like '*title=Interval complete*text=Check Hold*') $interval
  Check 'with sounds off, the alert sounds itself' ($interval -and $interval -notlike '*SILENT*') $interval
  Rc visible --ez value true | Out-Null
  CloseSession

  Step 'Notifications denied'
  WritePreferences 'true'
  # Revoking a runtime permission kills the app, so it's revoked before the launch.
  A shell pm revoke $package android.permission.POST_NOTIFICATIONS
  Launch | Out-Null
  $from = NowMs
  if (OpenSession 'zz-check-cues') { WaitRunning | Out-Null }
  Rc visible --ez value false | Out-Null
  Check 'with notifications denied, the session runs' (WaitFor 14 { (StepIndex) -eq 1 })
  $cues = Count (FocusSince $from)
  Check 'with notifications denied, the cues still play' ($cues -gt 0) "$cues focus requests"
  $shown = (LiveNotifications) -join '; '
  Check 'with notifications denied, nothing shows in the shade' (-not $shown) $shown
  Rc visible --ez value true | Out-Null
  CloseSession
  A shell pm grant $package android.permission.POST_NOTIFICATIONS
  Launch | Out-Null

  Step 'A cue during a call'
  if ($emulator) {
    if (OpenSession 'zz-check-cues') { WaitRunning | Out-Null }
    $id = Field sessionId
    $start = [long](StateOf $id).phaseStartedAt
    # Ringing from 2.5 s into the HIIT work to past its end, so the midpoint, the ticks and the change
    # all fall inside the call. Rung, never answered: answering one crashed the emulator.
    Start-Sleep -Milliseconds 2500
    A emu gsm call 5550100 | Out-Null
    WaitFor 12 { (StepIndex) -eq 1 } | Out-Null
    Start-Sleep -Milliseconds 1500
    A emu gsm cancel 5550100 | Out-Null
    $tried = Count (FocusSince ($start + 4500) ($start + 10600))
    $played = Count (FocusSince ($start + 4500) ($start + 11400) 'abandon')
    Check 'a cue while a call rings is skipped' ($tried -gt 0 -and $played -eq 0) "$tried requests, $played played"
    CloseSession
  } else {
    Skip 'a cue while a call rings: needs an emulator (adb emu gsm call); check it by hand on a phone'
  }

  Step "A rest's timing with the screen off, then the finish path"
  if (OpenSession 'zz-check-rest') { WaitRunning | Out-Null }
  $id = Field sessionId
  Rc advance | Out-Null
  WaitFor 3 { (StepIndex) -eq 1 } | Out-Null
  $restStart = [long](StateOf $id).phaseStartedAt
  if ($emulator) {
    A shell locksettings set-pin $pin | Out-Null
    $pinSet = $LASTEXITCODE -eq 0
    A shell locksettings verify --old $pin 2>&1 | Out-Null
    Check 'the PIN lock is set' ($LASTEXITCODE -eq 0)
  }
  A shell input keyevent KEYCODE_SLEEP
  Start-Sleep -Seconds 1
  Check 'the screen is off' ([bool]((A shell dumpsys power) -match 'mWakefulness=(Asleep|Dozing)'))
  if ($emulator) {
    A shell dumpsys deviceidle force-idle | Out-Null
    Check 'the device is in deep doze' (((A shell dumpsys deviceidle get deep) -join '') -eq 'IDLE')
  }
  # Waited out, not polled: a broadcast to the app every half second could keep it awake and hide a
  # late wake. The state file is read without waking the app.
  Start-Sleep -Seconds 10
  $after = StateOf $id
  $took = [long]$after.phaseStartedAt - $restStart
  Check 'the rest ends by itself with the screen off' ($after.stepIndex -eq 2)
  Check 'a 6 s rest ends at 6 s ± 1 s with the screen off' ($took -ge 5000 -and $took -le 7000) "$took ms"
  Rc finish | Out-Null
  Check 'Finish completes the session, screen off' (WaitFor 5 { (View) -eq 'Completed' })
  Check 'the finish deletes the state file' (WaitFor 5 { -not (StateFiles) })
  $written = SessionFile $id
  Check 'the finish stamps ended_at' ($written -match 'ended_at: 20')
  $taken = if ($written -match 'rest_taken_sec: ([0-9.]+)') { $Matches[1] } else { '' }
  Check 'the logged rest_taken_sec is 6' ($taken -eq '6') "rest_taken_sec $taken"
  Check 'the finish stops the service' (WaitFor 5 { -not (ServiceRunning) })
} catch {
  Write-Host $_ -ForegroundColor Red
  $code = 1
} finally {
  Step 'Restoring the target'
  if ($emulator) { A emu gsm cancel 5550100 2>&1 | Out-Null }
  if ($pinSet) { A shell locksettings clear --old $pin | Out-Null }
  A shell dumpsys deviceidle unforce 2>&1 | Out-Null
  A shell input keyevent KEYCODE_WAKEUP
  A shell wm dismiss-keyguard 2>&1 | Out-Null
  A shell pm grant $package android.permission.POST_NOTIFICATIONS 2>&1 | Out-Null
  A shell am force-stop $package
  if ($backedUp) {
    # The runner's state files aren't in the backup, and there were none at the start (checked). The
    # data folder is cleared only when the archive that replaces it is on the device. Synced before
    # anything else (#333): tar doesn't, the checksums below read the page cache, and an emulator killed
    # soon after (`emu kill`) came back with every restored file present and zero bytes long.
    A shell "run-as $package sh -c 'rm -rf $data/no_backup/runner; [ -s /data/local/tmp/session-check.tar ] && cd $data && rm -rf files/exercise-tracker && tar -xf /data/local/tmp/session-check.tar; sync'"
    A shell rm -f /data/local/tmp/session-check.tar
    if ((Checksums) -eq $checksumsBefore) {
      Write-Host 'The .dev data folder is byte for byte as it was.'
    } else {
      Write-Host "The .dev data folder differs from its backup: it's in $work/data.tar." -ForegroundColor Red
      $code = 1
    }
  }
  if ($metro -and -not $metro.HasExited) {
    # On Windows expo.cmd is a cmd.exe wrapper, and stopping it alone leaves node holding 8081: the
    # whole tree goes. Elsewhere the process is node itself.
    if ($IsWindows) { taskkill /PID $metro.Id /T /F | Out-Null } else { Stop-Process -Id $metro.Id -Force -ErrorAction SilentlyContinue }
  }
  Write-Host "`n$script:passed passed, $script:failed failed"
  if ($script:failed -gt 0) { $code = 1 }
  if ($code -eq 0) { Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue } else { Write-Host "Kept $work for a look." }
}
exit $code
