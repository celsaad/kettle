#!/usr/bin/env bash
# The native app's dev loop on macOS/Linux: build the debug app, install it on a phone, launch it. A
# port of native-dev.ps1, step for step; the reasoning behind each step lives in that file's
# comments, so a change to one wants the same change in both.
#
# Installs com.casco.kettle.dev only; nothing here can touch com.casco.kettle.
#
# Usage: scripts/native-dev.sh [--device <adb serial or ip:port>]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

target=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) target=(-s "$2"); shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

step() { printf '\n\033[36m== %s\033[0m\n' "$1"; }

step 'Build the app'
"$root/native/android/gradlew" -p "$root/native/android" :app:assembleDebug

# --no-streaming, not Gradle's installDebug: see native-dev.ps1.
step 'Install the app (com.casco.kettle.dev)'
adb ${target[@]+"${target[@]}"} install -r --no-streaming \
  "$root/native/android/app/build/outputs/apk/debug/app-debug.apk"

step 'Launch'
adb ${target[@]+"${target[@]}"} shell am start -n com.casco.kettle.dev/com.casco.kettle.MainActivity
