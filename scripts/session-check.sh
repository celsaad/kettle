#!/usr/bin/env bash
# Runs the native runner's repeatable device checks unattended against com.casco.kettle.dev (#315):
# leaving the count-in, a stale advance, cue cadence, the step-end alert's gating and content, the
# sounds preference, notifications denied, process death, and a rest's timing with the screen off,
# then the finish path. Each check prints PASS or FAIL on its own line. session-check.ps1 is its twin
# for Windows, step for step; the two change together, and the test library they plant is
# session-check-library.yaml.
#
# Needs the .dev APK built (scripts/native-dev.sh, or `gradlew :app:assembleDebug` once the RN AAR is
# in mavenLocal), and a target that's booted, unlocked and running no session. It installs the APK
# (after checking the cue sounds made it in: mergeDebugAssets can stay up to date and ship without
# them), and starts Metro if nothing answers on 8081.
#
# The evidence is what a person would read by hand: the session's state file
# (`no_backup/runner/<id>.json`, device wall-clock ms), its session file, `dumpsys audio`'s
# requestAudioFocus() and abandonAudioFocus() lines (a cue that played abandons its focus; one the
# system refused doesn't), the live "Notification List" of `dumpsys notification`
# (never its archive, which keeps cancelled ones), and `dumpsys activity services`. The session is
# driven through the debug-only RunnerCommandReceiver, so nothing taps the screen except the recovery
# prompt, which only ever shows on an unlocked launch.
#
# Caveat: the alert checks report the runner's visibility through the receiver, so they test the
# service's gating, not the app lifecycle that sets it in a real session. The Home check is the one
# that sends the app away for real and leaves the flag alone; keep it.
#
# On an emulator, doze is forced (`deviceidle force-idle`) and a PIN set for the timing check, each
# confirmed before the rest is timed, and a call is rung (never answered) to check a cue is skipped.
# On a phone the lock settings are left alone and the call check is skipped, saying so.
#
# Leaves the target as it found it, a failing or interrupted run included: the .dev data folder
# restored byte for byte (checked), POST_NOTIFICATIONS granted, the emulator's PIN and doze cleared,
# and Metro stopped if this started it (one already running is the caller's, and stays). Its temp
# folder, with the backup in it, is kept after a failing run and removed after a clean one. It never
# touches com.casco.kettle.
#
# What it can't check, by gesture, ear or time, is the manual list in .github/pull_request_template.md.
#
# The checks read English text (alert titles from en.json's session.notification keys, the recovery
# prompt's buttons), so the target's language has to be English. They also hardcode the alert's ids
# and channel; the Kotlin side points back here.
#
# Usage: scripts/session-check.sh [--device <adb serial>]
# Not -e: a check that fails says so and the run carries on to the next; setup steps exit themselves.
set -u

root="$(cd "$(dirname "$0")/.." && pwd)"
package=com.casco.kettle.dev
data="/data/data/$package"
receiver="$package/com.casco.kettle.shell.runner.RunnerCommandReceiver"
navigator="$package/com.casco.kettle.shell.nav.NavigateReceiver"
apk="$root/native/android/app/build/outputs/apk/debug/app-debug.apk"
pin=1234

target=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) target=(-s "$2"); shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

a() { adb ${target[@]+"${target[@]}"} "$@"; }
as_app() { a exec-out run-as "$package" "$@"; }

passed=0
failed=0
check() {
  # check <name> <0 for a pass> [what was seen instead]
  if [ "$2" = 0 ]; then
    passed=$((passed + 1)); printf '\033[32mPASS\033[0m %s\n' "$1"
  else
    failed=$((failed + 1)); printf '\033[31mFAIL\033[0m %s%s\n' "$1" "${3:+ ($3)}"
  fi
}
skip() { printf '\033[33mSKIP\033[0m %s\n' "$1"; }
# A section's header. Waits up to 30 s for the target first: an adb server restarting mid-run marks it
# offline for a few seconds. Polled, since stock macOS has no `timeout` to bound a wait-for-device.
step() {
  printf '\n\033[36m== %s\033[0m\n' "$1"
  wait_for 30 target_online || true
}
target_online() { [ "$(a get-state 2>/dev/null)" = device ]; }

# --- The receiver, the device's clock and files ---------------------------------------------------

# The receiver's answer: the broadcast's result data.
rc() { a shell am broadcast -n "$receiver" --es cmd "$@" | sed -n 's/.*data="\(.*\)"$/\1/p'; }
# Retried, being only a read: this adb's server can restart mid-run, and a read that lands while the
# device is offline comes back empty. Commands are never retried: a second advance would skip a step.
report() {
  local i out
  for i in 1 2 3; do
    out=$(rc report)
    case "$out" in '{'*) printf '%s' "$out"; return 0 ;; esac
    wait_for 10 target_online || true
  done
}
# A field of a one-line JSON object, string or number.
field() { printf '%s' "$1" | sed -n "s/.*\"$2\":\"\{0,1\}\([^\",}]*\).*/\1/p"; }
view() { field "$(report)" view; }

# Quoted for the device's shell, which would otherwise read the ? in a query string.
navigate() { a shell am broadcast -n "$navigator" --es url "'$1'" >/dev/null; }

now_ms() { local ns; ns=$(a shell date +%s%N | tr -d '\r'); echo "${ns%??????}"; }

# Polls [condition] every half second for up to [seconds].
wait_for() {
  local seconds=$1; shift
  local until=$((SECONDS + seconds))
  while [ $SECONDS -le $until ]; do
    if "$@"; then return 0; fi
    sleep 0.5
  done
  return 1
}
is_view() { [ "$(view)" = "$1" ]; }
at_step() { [ "$(step_index)" = "$1" ]; }
no_alert() { [ -z "$(alert)" ]; }
no_state_files() { [ -z "$(state_files)" ]; }
service_stopped() { ! service_running; }
recovery_asking() { [ "$(field "$(report)" recovery)" = Asking ]; }

# exec-out carries the device's stderr too, so a missing file is silenced there, not here.
in_app() { as_app sh -c "$1 2>/dev/null"; }
state_files() { in_app "ls $data/no_backup/runner" | tr -d '\r'; }
state_of() { in_app "cat $data/no_backup/runner/$1.json"; }
session_file() { in_app "cat $data/files/exercise-tracker/sessions/$1.yaml"; }
session_count() { in_app "ls $data/files/exercise-tracker/sessions" | wc -w | tr -d ' '; }
step_index() { field "$(report)" stepIndex; }

# The focus requests the app made from [from_ms] to [to_ms] (now, if not given), device wall clock, one
# millisecond time of day per line; abandons with [kind] abandon. A cue that played abandons its focus
# when its sound ends, so a request with no abandon after it is one the system refused. `dumpsys audio`
# stamps them in local time, so the bounds are shifted by the device's own offset; a window across
# midnight wraps. Its history is capped, so a window has to be recent: count within one, never lines
# overall.
focus_since() {
  local from=$1 to=${2:-$(now_ms)} kind=${3:-request} offset sign hh mm
  offset=$(a shell date +%z | tr -d '\r')
  sign=${offset:0:1}; hh=$((10#${offset:1:2})); mm=$((10#${offset:3:2}))
  local shift_ms=$(((hh * 3600 + mm * 60) * 1000))
  [ "$sign" = - ] && shift_ms=$((-shift_ms))
  local day=86400000
  local lo=$((((from + shift_ms) % day + day) % day)) hi=$((((to + shift_ms) % day + day) % day))
  a shell dumpsys audio | awk -v lo="$lo" -v hi="$hi" -v kind="$kind" -v pkg="callingPack=$package" '
    $3 == kind "AudioFocus()" && index($0, pkg) {
      split($2, t, ":"); ms = ((t[1] * 60 + t[2]) * 60 + t[3]) * 1000 + t[4]
      if (lo <= hi ? (ms >= lo && ms <= hi) : (ms >= lo || ms <= hi)) print ms
    }'
}

# The app's posts in the live list, one line each: id, channel, flags, title and text.
live_notifications() {
  a shell dumpsys notification --noredact | awk '
    /Notification List:/ { live = 1; next }
    /mArchive=|Snoozed notifications:/ { live = 0 }
    !live { next }
    /NotificationRecord\(/ { if (rec != "") print rec; rec = ""; mine = index($0, "pkg='"$package"'") > 0
      if (mine) { match($0, /id=[0-9]+/); id = substr($0, RSTART + 3, RLENGTH - 3)
        match($0, /channel=[^ ]+/); rec = "id=" id " " substr($0, RSTART, RLENGTH)
        match($0, /flags=[A-Z_|]+/); rec = rec " " substr($0, RSTART, RLENGTH) } }
    mine && /android\.title=String/ { sub(/.*String \(/, ""); sub(/\)$/, ""); rec = rec " title=" $0 }
    mine && /android\.text=String/ { sub(/.*String \(/, ""); sub(/\)$/, ""); rec = rec " text=" $0 }
    END { if (rec != "") print rec }'
}
# The step-end alert, if one is up: id 3052 (the ongoing one is 3051), and checked for its channel.
alert() { live_notifications | grep '^id=3052 '; }
cues_channel=expo_notifications_fallback_notification_channel

service_running() { a shell dumpsys activity services "$package" | grep -q 'RunnerService'; }

# --- Opening and closing sessions -----------------------------------------------------------------

# Opens [workout] by navigate message and waits for its count-in. A message sent before the shell's activity
# is up is lost, so it's sent again until one lands; one landing over the runner is dropped by design.
# Each try puts the Metro tunnel back first: an adb server that restarts mid-run drops it, and the app
# can't load its JS without it, so after two misses it's relaunched too.
open_session() {
  local i
  for i in 1 2 3 4 5; do
    a reverse tcp:8081 tcp:8081 >/dev/null 2>&1
    [ "$i" = 3 ] && launch
    navigate "/session?workoutId=$1"
    if wait_for 6 is_not_idle; then return 0; fi
  done
  echo "couldn't open $1" >&2
  return 1
}
is_not_idle() { [ "$(view)" != Idle ]; }
wait_running() { wait_for "${1:-8}" is_view Running; }

# Finishes a running session or leaves a count-in, presses Back on the completion (Done), and waits for
# Idle.
close_session() {
  case "$(view)" in
    Running) rc finish >/dev/null; wait_for 5 is_view Completed || true ;;
    CountingIn) a shell input keyevent KEYCODE_BACK ;;
  esac
  if [ "$(view)" = Completed ]; then a shell input keyevent KEYCODE_BACK; fi
  wait_for 6 is_view Idle
  sleep 1
}

launch() {
  a shell am force-stop "$package"
  a shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  wait_for 30 receiver_answers
}
receiver_answers() { case "$(report)" in '{'*) return 0 ;; *) return 1 ;; esac; }

write_preferences() {
  # The run's own: sounds as asked, and no backup folder, so a finish writes nothing outside the app.
  printf '{"unitSystem":"metric","themePreference":"system","restDayReminder":false,"sessionSounds":%s,"backupFolderUri":null}' "$1" > "$work/preferences.json"
  a push "$work/preferences.json" /data/local/tmp/session-check-preferences.json >/dev/null 2>&1
  a shell "run-as $package sh -c 'cat /data/local/tmp/session-check-preferences.json > $data/files/exercise-tracker/preferences.json'"
  a shell rm -f /data/local/tmp/session-check-preferences.json
}

# --- Setup and the way back -----------------------------------------------------------------------

work=$(mktemp -d)
emulator=0
metro_pid=
pin_set=0
backed_up=0

checksums() { as_app sh -c "cd $data/files/exercise-tracker && find . -type f -exec md5sum {} + | sort -k2"; }

restore() {
  local code=$?
  set +e
  step 'Restoring the target'
  [ "$emulator" = 1 ] && a emu gsm cancel 5550100 >/dev/null 2>&1
  if [ "$pin_set" = 1 ]; then a shell locksettings clear --old "$pin" >/dev/null; fi
  a shell dumpsys deviceidle unforce >/dev/null 2>&1
  a shell input keyevent KEYCODE_WAKEUP
  a shell wm dismiss-keyguard >/dev/null 2>&1
  a shell pm grant "$package" android.permission.POST_NOTIFICATIONS 2>/dev/null
  a shell am force-stop "$package"
  if [ "$backed_up" = 1 ]; then
    # The runner's state files aren't in the backup, and there were none at the start (checked). The
    # data folder is cleared only when the archive that replaces it is on the device. Synced before
    # anything else (#333): tar doesn't, the checksums below read the page cache, and an emulator killed
    # soon after (`emu kill`) came back with every restored file present and zero bytes long.
    a shell "run-as $package sh -c 'rm -rf $data/no_backup/runner; [ -s /data/local/tmp/session-check.tar ] && cd $data && rm -rf files/exercise-tracker && tar -xf /data/local/tmp/session-check.tar; sync'"
    a shell rm -f /data/local/tmp/session-check.tar
    if [ "$(checksums)" = "$(cat "$work/checksums")" ]; then
      echo "The .dev data folder is byte for byte as it was."
    else
      echo "The .dev data folder differs from its backup: it's in $work/data.tar." >&2
      code=1
    fi
  fi
  if [ -n "$metro_pid" ]; then kill "$metro_pid" 2>/dev/null; wait "$metro_pid" 2>/dev/null; fi
  printf '\n%d passed, %d failed\n' "$passed" "$failed"
  [ "$failed" -gt 0 ] && code=1
  if [ "$code" = 0 ]; then rm -rf "$work"; else echo "Kept $work for a look." >&2; fi
  exit "$code"
}
trap restore EXIT
trap 'exit 130' INT TERM

if ! curl -s --max-time 2 http://localhost:8081/status | grep -q 'packager-status:running'; then
  step 'Starting Metro'
  (cd "$root" && exec node_modules/.bin/expo start --port 8081 >"$work/metro.log" 2>&1) &
  metro_pid=$!
  ready_by=$((SECONDS + 120))
  until curl -s --max-time 2 http://localhost:8081/status | grep -q 'packager-status:running'; do
    kill -0 "$metro_pid" 2>/dev/null || { echo 'Metro exited before it was ready' >&2; exit 1; }
    [ $SECONDS -le $ready_by ] || { echo "Metro wasn't ready in 2 minutes: see $work/metro.log" >&2; exit 1; }
    sleep 1
  done
  # Expo starts adb's server itself as it comes up, and on this adb that can restart the one already
  # running, which drops the device for a few seconds. So Metro comes first, before any device step,
  # and settles before the target is waited for again.
  sleep 8
fi
a wait-for-device
# Read once the target is surely online: a property read while adb restarts comes back empty.
case "${target[1]:-$(a get-serialno)}" in emulator-*) emulator=1 ;; esac
[ "$(a shell getprop ro.boot.qemu | tr -d '\r')" = 1 ] || [ "$(a shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] && emulator=1

step 'Checking the APK and the target'
[ -f "$apk" ] || { echo "No APK at $apk: build the .dev app first." >&2; exit 1; }
for sound in tick milestone exercise-change; do
  unzip -l "$apk" | grep -q "assets/$sound.wav" ||
    { echo "The APK has no $sound.wav: run gradlew :app:mergeDebugAssets --rerun, then build again." >&2; exit 1; }
done
# Retried: installing is idempotent, and an adb server restarting under it leaves the device offline.
installed=0
for try in 1 2 3; do
  if a install -r --no-streaming "$apk" >/dev/null 2>&1; then installed=1; break; fi
  sleep 3; a wait-for-device
done
[ "$installed" = 1 ] || { echo "Couldn't install $apk." >&2; exit 1; }
[ -z "$(state_files)" ] || { echo 'A session is running or waiting to be recovered: finish it first.' >&2; exit 1; }

step 'Backing up .dev and planting the test library'
as_app tar -cf - files/exercise-tracker > "$work/data.tar"
checksums > "$work/checksums"
# Trusted only once there's something to put back: an empty baseline would make the restore's
# comparison pass whatever happened, and an archive missing on the device would restore nothing.
[ -s "$work/data.tar" ] && [ -s "$work/checksums" ] || { echo "Couldn't back up .dev's data folder." >&2; exit 1; }
a push "$work/data.tar" /data/local/tmp/session-check.tar >/dev/null 2>&1 &&
  [ "$(a shell stat -c %s /data/local/tmp/session-check.tar | tr -d '\r')" = "$(wc -c < "$work/data.tar" | tr -d ' ')" ] ||
  { echo "Couldn't copy the backup to the device." >&2; exit 1; }
backed_up=1
# The whole library is replaced rather than added to, so no check depends on what the user's holds.
a push "$root/scripts/session-check-library.yaml" /data/local/tmp/session-check-library.yaml >/dev/null 2>&1
a shell "run-as $package sh -c 'cat /data/local/tmp/session-check-library.yaml > $data/files/exercise-tracker/exercises.yaml'"
a shell rm -f /data/local/tmp/session-check-library.yaml
write_preferences true

a reverse tcp:8081 tcp:8081 >/dev/null
a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard >/dev/null 2>&1 || true
launch || { echo "$package didn't start." >&2; exit 1; }

# --- The checks -----------------------------------------------------------------------------------

step 'Leaving during the count-in'
before=$(session_count)
open_session zz-check-rest
[ "$(view)" = CountingIn ]; check 'the session opens on its count-in' $?
a shell input keyevent KEYCODE_BACK
wait_for 5 is_view Idle; check 'Back during the count-in leaves' $?
sleep 1
[ -z "$(state_files)" ]; check 'leaving the count-in leaves no state file' $?
[ "$(session_count)" = "$before" ]; check 'leaving the count-in leaves no session file' $?
sleep 1

step 'A stale advance'
open_session zz-check-rest && wait_running
first=$(report)
rc advance >/dev/null
wait_for 3 at_step 1 && moved=0 || moved=1
check 'an advance moves to the rest' "$moved"
# Delivered and then refused: a broadcast that never arrived would leave the step where it is too.
visit=$(field "$first" visit)
answer=$([ -n "$visit" ] && rc advance --el visit "$visit")
sleep 1
[ "$answer" = ok ] && [ "$(step_index)" = 1 ]
check 'an advance carrying the last visit is refused' $? "answer '$answer', on step $(step_index)"
close_session

step 'Cue cadence, alert gating and content, sounds on'
open_session zz-check-cues && wait_running
a shell dumpsys activity services "$package" | grep -q 'isForeground=true .*types=0x00000100'
check 'the service runs in the foreground, typed health' $?
id=$(field "$(report)" sessionId)
start=$(field "$(state_of "$id")" phaseStartedAt)
count_in=$(focus_since $((start - 3500)) $((start + 500)) | wc -l | tr -d ' ')
[ "$count_in" = 4 ]; check 'the count-in cues 3 ticks and the exercise change' $? "$count_in focus requests"
# The HIIT work is 10 s: its midpoint at 5, ticks at 7, 8 and 9, the change at 10. The runner is on
# screen as it ends, so no alert may be posted.
wait_for 14 at_step 1 || true
sleep 0.8
midpoint=$(focus_since $((start + 4500)) $((start + 5500)) | wc -l | tr -d ' ')
[ "$midpoint" = 1 ]; check 'HIIT work cues its midpoint' $? "$midpoint focus requests"
ending=$(focus_since $((start + 6500)) $((start + 10600)))
gaps=$(printf '%s\n' "$ending" | awk 'NR > 1 { d = $1 - last; if (d < 800 || d > 1200) bad = 1 } { last = $1 } END { print NR, bad + 0 }')
[ "$gaps" = '4 0' ]; check "a countdown's last 3 s and its end cue a second apart" $? "cues and stray gap: $gaps"
# What the call check reads: a cue that plays gives its focus back once its sound ends.
released=$(focus_since $((start + 4500)) $((start + 11400)) abandon | wc -l | tr -d ' ')
[ "$released" -gt 0 ]; check 'a cue that plays gives its focus back' $? "$released abandons"
[ -z "$(alert)" ]; check 'no alert is posted while the runner is on screen' $? "$(alert)"
rc visible --ez value false >/dev/null
# The hold ends at 4 s, handing over to the AMRAP.
wait_for 8 at_step 2 || true
sleep 0.8
hold=$(alert)
case "$hold" in *'title=Hold complete'*'text=Check AMRAP'*) ok=0 ;; *) ok=1 ;; esac
check 'a hold that ends off screen posts "Hold complete" and the next exercise' $ok "$hold"
case "$hold" in *"channel=$cues_channel "*) ok=0 ;; *) ok=1 ;; esac
check 'the alert is on the cues channel' $ok "$hold"
case "$hold" in *SILENT*) ok=0 ;; *) ok=1 ;; esac
check 'with sounds on, the alert is silent: the service played the cue' $ok "$hold"
rc visible --ez value true >/dev/null
wait_for 3 no_alert; check 'the alert is cleared once the runner is on screen' $? "$(alert)"
rc visible --ez value false >/dev/null
# The AMRAP's 5 s cap ends the workout. The service stops with it, so this last alert stays up after;
# whether it should clear is #327, a question for the app, not this check.
wait_for 9 is_view Completed || true
sleep 0.8
last=$(alert)
case "$last" in *"title=Time's up"*'text=Workout complete'*) ok=0 ;; *) ok=1 ;; esac
check "an AMRAP that ends the workout posts \"Time's up\" and \"Workout complete\"" $ok "$last"
rc visible --ez value true >/dev/null
backup=$(field "$(report)" backup)
[ "$backup" = NONE ]; check 'the completion reports the backup outcome' $? "backup $backup"
close_session

step 'The alert, with the app sent Home for real'
open_session zz-check-rest && wait_running
rc advance >/dev/null
wait_for 3 at_step 1 || true
a shell input keyevent KEYCODE_HOME
# The 6 s rest ends with the app in the background.
wait_for 9 at_step 2 || true
sleep 0.8
rest=$(alert)
case "$rest" in *'title=Rest complete'*'text=Check Reps'*) ok=0 ;; *) ok=1 ;; esac
check 'a rest that ends with the app away posts "Rest complete" and the next exercise' $ok "$rest"
a shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_for 5 no_alert; check 'coming back to the runner clears the alert' $? "$(alert)"
close_session

step 'Process death, mid-set and mid-rest'
kill_app() { a shell "run-as $package kill -9 \$(pidof $package)"; }
tap_text() {
  a shell uiautomator dump /sdcard/session-check.xml >/dev/null 2>&1
  local xy
  xy=$(a shell cat /sdcard/session-check.xml | tr '>' '\n' | grep "package=\"$package\"" | grep "text=\"$1\"" |
    sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -1)
  [ -n "$xy" ] || return 1
  set -- $xy
  a shell input tap $((($1 + $3) / 2)) $((($2 + $4) / 2))
}
# History's banner title (en.json's history.problemsTitle), or nothing with no unreadable file, and the
# session files that are empty (#333). The user's own sessions stay in the log through the run, so the
# checks are that a kill adds nothing to either. Read only with History's tab selected, as route-check
# reads it, so a dump that failed or a screen that never came can't pass as an empty banner.
history_banner() {
  local dump
  navigate /history
  sleep 2
  dump=$(a exec-out 'f=/sdcard/session-check.xml; rm -f $f; uiautomator dump $f >/dev/null 2>&1; cat $f 2>/dev/null; true')
  if [ "$(printf '%s' "$dump" | xmllint --xpath "boolean(//node[@selected='true'][.//node[@text='History']])" - 2>/dev/null)" != true ]; then
    echo 'History not on screen'
    return
  fi
  printf '%s' "$dump" | xmllint --xpath "string(//node[contains(@text, 'be read or written')]/@text)" - 2>/dev/null
}
# A find that failed says so, rather than reading as no empty file: in_app throws its errors away.
empty_session_files() {
  in_app "find $data/files/exercise-tracker/sessions -name '*.yaml' -empty || echo 'find failed'" | tr -d '\r'
}
banner_before=$(history_banner)
empty_before=$(empty_session_files)
open_session zz-check-rest && wait_running
id=$(field "$(report)" sessionId)
kill_app
sleep 1
[ -n "$(state_of "$id")" ]; check 'killed mid-set, the state file survives' $?
a shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_for 20 recovery_asking; check 'the next launch offers Resume or Finish' $?
tap_text Resume || true
wait_for 8 is_view Running; check 'Resume carries the session on' $? "$(view)"
rc advance >/dev/null
wait_for 3 at_step 1 || true
kill_app
sleep 1
[ -n "$(state_of "$id")" ]; check 'killed mid-rest, the state file survives' $?
a shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_for 20 recovery_asking; check 'the next launch offers the prompt again' $?
tap_text Finish || true
wait_for 8 no_state_files; check "the prompt's Finish deletes the state file" $?
case "$(session_file "$id")" in *ended_at:\ 20*) ok=0 ;; *) ok=1 ;; esac
check "the prompt's Finish stamps ended_at" $ok
empty=$(empty_session_files)
[ "$empty" = "$empty_before" ] && [ "${empty#*find failed}" = "$empty" ]; check 'two kills leave no new empty session file' $? "$empty"
banner=$(history_banner)
[ "$banner" = "$banner_before" ] && [ "$banner" != 'History not on screen' ]; check "History's banner lists nothing new after the relaunch" $? "'$banner', was '$banner_before'"
sleep 1

step 'Sounds off'
write_preferences false
launch
from=$(now_ms)
open_session zz-check-cues && wait_running
rc visible --ez value false >/dev/null
wait_for 14 at_step 1 || true
sleep 0.8
quiet=$(focus_since "$from" | wc -l | tr -d ' ')
[ "$quiet" = 0 ]; check 'with sounds off, no cue requests focus' $? "$quiet focus requests"
interval=$(alert)
case "$interval" in *'title=Interval complete'*'text=Check Hold'*) ok=0 ;; *) ok=1 ;; esac
check 'an interval that ends off screen posts "Interval complete" and the next exercise' $ok "$interval"
case "$interval" in '' | *SILENT*) ok=1 ;; *) ok=0 ;; esac
check 'with sounds off, the alert sounds itself' $ok "$interval"
rc visible --ez value true >/dev/null
close_session

step 'Notifications denied'
write_preferences true
# Revoking a runtime permission kills the app, so it's revoked before the launch.
a shell pm revoke "$package" android.permission.POST_NOTIFICATIONS
launch
from=$(now_ms)
open_session zz-check-cues && wait_running
rc visible --ez value false >/dev/null
wait_for 14 at_step 1; check 'with notifications denied, the session runs' $?
cues=$(focus_since "$from" | wc -l | tr -d ' ')
[ "$cues" -gt 0 ]; check 'with notifications denied, the cues still play' $? "$cues focus requests"
[ -z "$(live_notifications)" ]; check 'with notifications denied, nothing shows in the shade' $? "$(live_notifications)"
rc visible --ez value true >/dev/null
close_session
a shell pm grant "$package" android.permission.POST_NOTIFICATIONS
launch

step 'A cue during a call'
if [ "$emulator" = 1 ]; then
  open_session zz-check-cues && wait_running
  id=$(field "$(report)" sessionId)
  start=$(field "$(state_of "$id")" phaseStartedAt)
  # Ringing from 2.5 s into the HIIT work to past its end, so the midpoint, the ticks and the change all
  # fall inside the call. Rung, never answered: answering one crashed the emulator.
  sleep 2.5
  a emu gsm call 5550100 >/dev/null
  wait_for 12 at_step 1 || true
  sleep 1.5
  a emu gsm cancel 5550100 >/dev/null
  tried=$(focus_since $((start + 4500)) $((start + 10600)) | wc -l | tr -d ' ')
  played=$(focus_since $((start + 4500)) $((start + 11400)) abandon | wc -l | tr -d ' ')
  [ "$tried" -gt 0 ] && [ "$played" = 0 ]
  check 'a cue while a call rings is skipped' $? "$tried requests, $played played"
  close_session
else
  skip 'a cue while a call rings: needs an emulator (adb emu gsm call); check it by hand on a phone'
fi

step "A rest's timing with the screen off, then the finish path"
open_session zz-check-rest && wait_running
id=$(field "$(report)" sessionId)
rc advance >/dev/null
wait_for 3 at_step 1 || true
rest_start=$(field "$(state_of "$id")" phaseStartedAt)
if [ "$emulator" = 1 ]; then
  a shell locksettings set-pin "$pin" >/dev/null && pin_set=1
  a shell locksettings verify --old "$pin" >/dev/null 2>&1; check 'the PIN lock is set' $?
fi
a shell input keyevent KEYCODE_SLEEP
sleep 1
a shell dumpsys power | grep -qE 'mWakefulness=(Asleep|Dozing)'; check 'the screen is off' $?
if [ "$emulator" = 1 ]; then
  a shell dumpsys deviceidle force-idle >/dev/null
  [ "$(a shell dumpsys deviceidle get deep | tr -d '\r')" = IDLE ]; check 'the device is in deep doze' $?
fi
# Waited out, not polled: a broadcast to the app every half second could keep it awake and hide a late
# wake. The state file is read without waking the app.
sleep 10
rest_end=$(field "$(state_of "$id")" phaseStartedAt)
[ "$(field "$(state_of "$id")" stepIndex)" = 2 ]; check 'the rest ends by itself with the screen off' $?
took=$((rest_end - rest_start))
[ "$took" -ge 5000 ] && [ "$took" -le 7000 ]; check 'a 6 s rest ends at 6 s ± 1 s with the screen off' $? "${took} ms"
rc finish >/dev/null
wait_for 5 is_view Completed; check 'Finish completes the session, screen off' $?
wait_for 5 no_state_files; check 'the finish deletes the state file' $?
written=$(session_file "$id")
case "$written" in *ended_at:\ 20*) ok=0 ;; *) ok=1 ;; esac
check 'the finish stamps ended_at' $ok
taken=$(printf '%s\n' "$written" | sed -n 's/.*rest_taken_sec: \([0-9.]*\).*/\1/p' | head -1)
[ "$taken" = 6 ]; check 'the logged rest_taken_sec is 6' $? "rest_taken_sec $taken"
wait_for 5 service_stopped
check 'the finish stops the service' $?
