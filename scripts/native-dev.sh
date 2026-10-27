#!/usr/bin/env bash
# The native shell's dev loop on macOS/Linux: the RN app as an AAR, the shell around it on a phone,
# and Metro feeding it with fast refresh. A port of native-dev.ps1, step for step; the reasoning
# behind each step lives in that file's comments, so a change to one wants the same change in both.
#
# The AAR only needs rebuilding when native code or a native dependency changes, so pass --skip-aar
# for a JS-only change. Installs com.casco.kettle.dev only; nothing here can touch com.casco.kettle.
#
# Usage: scripts/native-dev.sh [--skip-aar] [--prebuild] [--device <adb serial or ip:port>]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
# arm64 alone: every phone this loop targets is one, and each extra ABI is another full C++ build.
abi='-PreactNativeArchitectures=arm64-v8a'

skip_aar=0
prebuild=0
target=()
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-aar) skip_aar=1 ;;
    --prebuild) prebuild=1 ;;
    --device) target=(-s "$2"); shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

step() { printf '\n\033[36m== %s\033[0m\n' "$1"; }

if [ ! -d "$root/node_modules" ]; then
  step 'Install dependencies'
  pnpm -C "$root" install --frozen-lockfile
fi

if [ "$prebuild" = 1 ] || [ ! -d "$root/android" ]; then
  step 'Prebuild android/'
  (cd "$root" && CI=1 node_modules/.bin/expo prebuild --platform android --no-install)
fi

if [ "$skip_aar" = 0 ]; then
  # All, not Debug: see native-dev.ps1.
  step 'Publish the RN AAR to mavenLocal'
  "$root/android/gradlew" -p "$root/android" publishBrownfieldAllPublicationToMavenLocal "$abi"
fi

step 'Build the shell'
"$root/native/android/gradlew" -p "$root/native/android" :app:assembleDebug

# --no-streaming, not Gradle's installDebug: see native-dev.ps1.
step 'Install the shell (com.casco.kettle.dev)'
adb ${target[@]+"${target[@]}"} install -r --no-streaming \
  "$root/native/android/app/build/outputs/apk/debug/app-debug.apk"

step 'Point the phone at Metro'
adb ${target[@]+"${target[@]}"} reverse tcp:8081 tcp:8081

# Metro first: an app launched before it answers stays on its splash. Not with CI=1, which turns
# reloads off.
(cd "$root" && exec node_modules/.bin/expo start --port 8081) &
metro=$!
trap 'kill "$metro" 2>/dev/null || true' EXIT INT TERM
step 'Waiting for Metro'
until curl -s --max-time 2 http://localhost:8081/status | grep -q 'packager-status:running'; do
  kill -0 "$metro" 2>/dev/null || { echo 'Metro exited before it was ready' >&2; exit 1; }
  sleep 1
done

step 'Launch'
adb ${target[@]+"${target[@]}"} shell am start -n com.casco.kettle.dev/com.casco.kettle.MainActivity
wait "$metro"
