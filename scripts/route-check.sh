#!/usr/bin/env bash
# Steers the running shell through every route by navigate message and checks what each step leaves
# on screen, back included. A port of route-check.ps1, which explains the matrix and what it pins;
# the two matrices are kept identical, so a step added to one is added to both.
#
# Needs com.casco.kettle.dev from native-dev.sh, running against Metro and past its splash, on a
# phone in English whose .dev install still holds the seed library. The runner steps finish a real
# session, which joins the .dev history. It never touches com.casco.kettle.
#
# Usage: scripts/route-check.sh [--device <adb serial>]
set -euo pipefail

package='com.casco.kettle.dev'
receiver="$package/com.casco.kettle.shell.rn.NavigateReceiver"

target=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) target=(-s "$2"); shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done
a() { adb ${target[@]+"${target[@]}"} "$@"; }

# action|expect. A route: send it. 'back': press back. 'tap:X': tap the element whose text is X.
# 'front': bring the app to the front. Expect is an exact on-screen text, or 'tab:X' for the shell's
# tab X being the selected one; empty means the app is not in front.
steps=(
  # Tabs, out of order and repeated. A message moves RN, and the shell's bar follows its report.
  '/history|tab:History' '/library|tab:Library' '/programs|tab:Programs'
  '/|tab:Workouts' '/programs|tab:Programs' '/history|tab:History'
  '/history|tab:History'
  # The shell's own bar and back stack: Workouts first, then out of the app.
  'tap:Library|tab:Library' 'tap:Programs|tab:Programs' 'back|tab:Workouts'
  'back|' 'front|tab:Workouts' 'tap:History|tab:History'
  # Every modal from a tab, back to that tab after each.
  '/settings|Settings' 'back|tab:History'
  '/analytics|Your numbers' 'back|tab:History'
  '/support|Support Kettle' 'back|tab:History'
  '/import|Import library' 'back|tab:History'
  '/exercise-editor|New exercise' 'back|tab:History'
  '/exercise-editor?id=pushups|Edit exercise' 'back|tab:History'
  '/workout-editor|New workout' 'back|tab:History'
  '/workout-editor?id=foundations-push|Edit workout' 'back|tab:History'
  '/exercise-progress?exerciseId=pushups|Push-ups' 'back|tab:History'
  '/program-detail?programId=foundations|Week 1 · Day 1' 'back|tab:History'
  '/program-editor|New program' 'back|tab:History'
  '/program-editor?id=foundations|Edit program' 'back|tab:History'
  '/program-guide|Writing a program by hand' 'back|tab:History'
  '/session-editor|Edit session' 'back|tab:History'
  # From a modal: to a tab, and to another modal. Either way back reaches Workouts and then leaves.
  '/library|tab:Library' '/settings|Settings' '/history|tab:History'
  'back|tab:Workouts'
  '/library|tab:Library' '/settings|Settings' '/support|Support Kettle'
  'back|tab:Library' 'back|tab:Workouts' 'back|'
  # Sent while in the background, it lands, and shows once the app is in front again.
  '/history|' 'front|tab:History'
  # Over a running session. Cancel must leave the runner on top, with nothing stacked over it.
  '/session?workoutId=foundations-push|Finish'
  '/library|Finish session?' 'tap:CANCEL|Finish'
  'back|Finish session?' 'tap:CANCEL|Finish'
  '/programs|Finish session?' 'tap:FINISH|Workout complete'
  'tap:Done|tab:History' 'back|tab:Workouts' 'back|'
)

screen() {
  a shell uiautomator dump /sdcard/route-check.xml >/dev/null
  a exec-out cat /sdcard/route-check.xml
}

# XPath through xmllint rather than a regex over the dump, so entities and attribute order don't
# matter. No step's text holds a single quote, which is what would break the literal.
xpath() { xmllint --xpath "$1" - 2>/dev/null || true; }

# 'tab:X' is a selected node with the label X inside it: the dump lists Compose's nodes unmerged, so
# a tab's selected state is on the item and its label is a TextView under it.
shows() {
  if [ "${1#tab:}" != "$1" ]; then
    [ "$(screen | xpath "boolean(//node[@selected='true'][.//node[@text='${1#tab:}']])")" = true ]
  else
    [ "$(screen | xpath "boolean(//node[@text='$1'])")" = true ]
  fi
}

start_app() { a shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }

# Quoted for the device's shell, which would otherwise read the ? and & in a query string.
send_route() { a shell am broadcast -n "$receiver" --es url "'$1'" >/dev/null; }

in_front() { a shell dumpsys activity activities | grep -m1 topResumedActivity | grep -qF "$package"; }

# Polls rather than sleeping a fixed time; 8 s covers a modal's animation plus the runner's first
# render with room to spare.
wait_for() {
  local deadline=$((SECONDS + 8))
  while [ "$SECONDS" -lt "$deadline" ]; do
    sleep 0.5
    if [ -z "$1" ]; then in_front || return 0
    elif shows "$1"; then return 0
    fi
  done
  return 1
}

# The runner asks for it on its first start, and the prompt would sit over every later step.
a shell pm grant "$package" android.permission.POST_NOTIFICATIONS
start_app
# From wherever the last run or the user left it.
send_route '/'
wait_for 'tab:Workouts' || { echo "$package isn't showing Home. Start it against Metro first." >&2; exit 1; }

n=0
for step in "${steps[@]}"; do
  n=$((n + 1))
  action="${step%%|*}"
  expect="${step#*|}"
  case "$action" in
    back) a shell input keyevent KEYCODE_BACK ;;
    front) start_app ;;
    tap:*)
      label="${action#tap:}"
      # By its text, or by its description: an unselected tab shows no label, and its icon names it.
      bounds="$(screen | xpath "string((//node[@text='$label' or @content-desc='$label'])[1]/@bounds)")"
      [ -n "$bounds" ] || { echo "Step $n: nothing on screen reads '$label'" >&2; exit 1; }
      # [x1,y1][x2,y2] -> the centre.
      read -r x1 y1 x2 y2 <<<"$(echo "$bounds" | tr '[],' '   ')"
      a shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
      ;;
    *) send_route "$action" ;;
  esac
  shown="${expect:-(left the app)}"
  wait_for "$expect" || { echo "Step $n: '$action' should show '$shown', and didn't" >&2; exit 1; }
  printf '%3d  %-42s %s\n' "$n" "$action" "$shown"
done
printf '\n\033[32mAll %d steps landed where they should.\033[0m\n' "$n"
