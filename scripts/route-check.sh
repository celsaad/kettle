#!/usr/bin/env bash
# Steers the running shell through every route by navigate message and checks what each step leaves
# on screen, back included. A port of route-check.ps1, which explains the matrix and what it pins;
# the two matrices are kept identical, so a step added to one is added to both.
#
# Needs com.casco.kettle.dev from native-dev.sh, running and past its splash, or with --package the
# release-built com.casco.kettle.bench2 (`gradlew :app:installBenchmark`); on a phone in English
# whose install still holds the seed library. The runner's steps finish real sessions, which join the
# .dev history (#306). It never touches com.casco.kettle.
#
# A start opens the native runner, in every build since the flip release (#308), and is waited on
# past its count-in: its End only shows once the session runs. Every route is the same in debug
# and release, and routing is native's in both since #309.
#
# Usage: scripts/route-check.sh [--device <adb serial>] [--package com.casco.kettle.bench2]
set -euo pipefail

package='com.casco.kettle.dev'

target=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) target=(-s "$2"); shift ;;
    --package)
      case "$2" in
        com.casco.kettle.dev | com.casco.kettle.bench2) package="$2" ;;
        *) echo "--package is com.casco.kettle.dev or com.casco.kettle.bench2" >&2; exit 2 ;;
      esac
      shift
      ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done
a() { adb ${target[@]+"${target[@]}"} "$@"; }
receiver="$package/com.casco.kettle.shell.nav.NavigateReceiver"

# action|expect. A route: send it. 'back': press back. 'tap:X': tap the element whose text is X.
# 'front': bring the app to the front. Expect is an exact on-screen text, or 'tab:X' for the shell's
# tab X being the selected one; empty means the app is not in front.
steps=(
  # Tabs, out of order and repeated, and the four-tab shell's paths, each landing on the tab and section
  # that now holds its screen (#411). A section is checked as a tab is: its segment is the selected node.
  '/progress|tab:Progress' '/library|tab:Library' '/|tab:Today' '/history|tab:Sessions'
  '/programs|tab:Programs' '/analytics|tab:Overview' '/library?section=exercises|tab:Exercises'
  '/progress|tab:Progress'
  # The shell's own bar and back stack: Today first, then out of the app. A tab keeps its section.
  'tap:Library|tab:Exercises' 'tap:Programs|tab:Programs' 'tap:Progress|tab:Progress'
  'tap:Library|tab:Programs' 'back|tab:Today' 'back|' 'front|tab:Today' 'tap:Progress|tab:Progress'
  # Every modal from a tab, back to that tab after each.
  '/settings|Settings' 'back|tab:Progress' '/support|Support Kettle' 'back|tab:Progress'
  '/import|Import library' 'back|tab:Progress' '/exercise-editor|New exercise' 'back|tab:Progress'
  '/exercise-editor?id=pushups|Edit exercise' 'back|tab:Progress' '/workout-editor|New workout'
  'back|tab:Progress' '/workout-editor?id=foundations-push|Edit workout' 'back|tab:Progress'
  '/workout-preview?workoutId=foundations-push|NEXT UP' 'back|tab:Progress'
  '/workout-preview?programId=foundations&week=1&day=Day%201|NEXT UP · Week 1 · Day 1'
  'back|tab:Progress' '/exercise-progress?exerciseId=pushups|Push-ups' 'back|tab:Progress'
  '/program-detail?programId=foundations|Week 1 · Day 1' 'back|tab:Progress'
  '/program-editor|New program' 'back|tab:Progress' '/program-editor?id=foundations|Edit program'
  'back|tab:Progress' '/program-guide|Writing a program by hand' 'back|tab:Progress'
  '/session-editor|Edit session' 'back|tab:Progress'
  '/your-file|Have an assistant write your training' 'back|tab:Progress'
  '/library-yaml|Weights are in kilograms, as the file stores them.' 'back|tab:Progress'
  '/exercise?id=pushups|YAML' 'back|tab:Progress'
  # A section's row opens its native sheet over the tab (#197, #233), and back returns to it.
  'tap:Library|tab:Programs' 'tap:Foundations · 4 Weeks · No Equipment|Week 1 · Day 1'
  # Over that native sheet, the native editor and the native runner each come back to it (#155, #233).
  # Back over a running session asks first, End early's sheet (#417), and completion's Done leaves (#297, #306).
  'tap:Edit program|Edit program' 'back|Week 1 · Day 1' 'tap:Start Week 1 · Day 1|End'
  'back|End the session early?' 'tap:Save and end|Workout complete' 'tap:Done|Week 1 · Day 1'
  'back|tab:Programs'
  # Library's Exercises: a row opens its detail, and Edit the form over that; the header's + opens the
  # form directly. Back returns through each (#234, #413).
  'tap:Exercises|tab:Exercises' 'tap:Push-ups|YAML' 'tap:Edit|Edit exercise' 'back|YAML'
  'back|tab:Exercises'
  'tap:New exercise|New exercise' 'back|tab:Exercises'
  # The file's name under Library's title opens the file sheet; Open as YAML and Paste YAML open their own
  # sheets over it, and back returns through each (#413).
  'tap:exercises.yaml|Have an assistant write your training'
  'tap:Open as YAML|Weights are in kilograms, as the file stores them.'
  'back|Have an assistant write your training' 'tap:Paste YAML|Import library'
  'back|Have an assistant write your training' 'back|tab:Exercises'
  # Today's gear opens native Settings over it, and back returns (#235). All workouts opens Library's
  # Workouts (#412), whose New workout opens the native editor over it (#238).
  'tap:Today|tab:Today' 'tap:Settings|Settings' 'back|tab:Today' 'tap:All workouts|tab:Workouts'
  'tap:New workout|New workout' 'back|tab:Workouts'
  # From a modal: to a tab, and to another modal. Either way back reaches Today and then leaves.
  '/library|tab:Library' '/settings|Settings' '/progress|tab:Progress' 'back|tab:Today'
  '/library|tab:Library' '/settings|Settings' '/support|Support Kettle' 'back|tab:Library'
  'back|tab:Today' 'back|'
  # Sent while in the background, it lands, and shows once the app is in front again.
  '/progress|' 'front|tab:Progress'
  # Over the runner, a message is dropped with no question asked, and Done pops its slot (#302, #309).
  '/session?workoutId=foundations-push|End' '/library|End' 'back|End the session early?'
  'tap:Keep going|End' 'back|End the session early?' 'tap:Save and end|Workout complete'
  'tap:Done|tab:Progress' 'back|tab:Today' 'back|'
)

# A dump that fails (it gives up on a screen that isn't idle for a second) writes nothing, so without
# the rm the last step's file would be read as this one's, and pass on a text both screens share.
# A failed dump prints nothing instead, which shows nothing.
screen() {
  a exec-out 'f=/sdcard/route-check.xml; rm -f $f; uiautomator dump $f >/dev/null 2>&1; cat $f 2>/dev/null; true'
}

# XPath through xmllint rather than a regex over the dump, so entities and attribute order don't
# matter. No step's text holds a single quote, which is what would break the literal.
xpath() { xmllint --xpath "$1" - 2>/dev/null || true; }

# 'tab:X' is a selected node with the label X inside it: the dump lists Compose's nodes unmerged, so
# a tab's selected state is on the item and its label is a TextView under it. A section's segment
# (#411) is selectable without being a Tab, which Compose reports as checked rather than selected.
shows() {
  if [ "${1#tab:}" != "$1" ]; then
    [ "$(screen | xpath "boolean(//node[@selected='true' or @checked='true'][.//node[@text='${1#tab:}']])")" = true ]
  else
    [ "$(screen | xpath "boolean(//node[@text='$1'])")" = true ]
  fi
}

start_app() { a shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }

# Quoted for the device's shell, which would otherwise read the ? and & in a query string.
send_route() { a shell am broadcast -n "$receiver" --es url "'$1'" >/dev/null; }

# topResumedActivity from Android 10; below it, only mResumedActivity names the top (#364). From 10 on
# mResumedActivity is per task, so it's read only when there's no topResumedActivity.
in_front() {
  local dump top
  dump="$(a shell dumpsys activity activities)"
  top="$(grep -m1 topResumedActivity <<<"$dump" || grep -m1 mResumedActivity <<<"$dump" || true)"
  grep -qF "$package" <<<"$top"
}

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

# The runner asks for it on its first start, and the prompt would sit over every later step. Only from
# Android 13, which added it: below that the grant fails, and minSdk is 26 (#364).
if [ "$(a shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ] 2>/dev/null; then
  a shell pm grant "$package" android.permission.POST_NOTIFICATIONS
fi
start_app
# From wherever the last run or the user left it.
send_route '/'
wait_for 'tab:Today' || { echo "$package isn't showing Home. Start it first." >&2; exit 1; }

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
