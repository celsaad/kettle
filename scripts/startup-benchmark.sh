#!/usr/bin/env bash
# Cold start to a drawn home screen, the native shell against today's app, on a phone
# (native/android/benchmark). A port of startup-benchmark.ps1, which explains the setup; read
# timeToFullDisplayMs. Never the Play install: the benchmark force-stops its targets and resets their
# compiled code, and nothing here names com.casco.kettle.
#
# Usage: scripts/startup-benchmark.sh [--skip-build] [--device <adb serial>]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
native="$root/native/android"
abi='-PreactNativeArchitectures=arm64-v8a'
runner='com.casco.kettle.benchmark/androidx.test.runner.AndroidJUnitRunner'

skip_build=0
target=()
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build) skip_build=1 ;;
    --device) target=(-s "$2"); shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done
a() { adb ${target[@]+"${target[@]}"} "$@"; }
step() { printf '\n\033[36m== %s\033[0m\n' "$1"; }

# In install and first-launch order, each with the APK that must carry it.
packages=(com.casco.kettle.bench com.casco.kettle.bench2)
apks=(
  "$root/android/app/build/outputs/apk/release/app-release.apk"
  "$native/app/build/outputs/apk/benchmark/app-benchmark.apk"
)
benchmark_apk="$native/benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk"

if [ "$skip_build" = 0 ]; then
  step "Build today's app (com.casco.kettle.bench)"
  "$root/android/gradlew" -p "$root/android" --init-script "$native/benchmark/rn-app.init.gradle" \
    :app:assembleRelease "$abi"
  # All, not Release: the two publications share one coordinate (see native-dev.ps1).
  step 'Publish the RN AAR to mavenLocal'
  "$root/android/gradlew" -p "$root/android" publishBrownfieldAllPublicationToMavenLocal "$abi"
  step 'Build the shell (com.casco.kettle.bench2) and the benchmark'
  "$native/gradlew" -p "$native" :app:assembleBenchmark :benchmark:assembleBenchmark "$abi"
fi

# Checked, not assumed: with --skip-build, today's app's APK is whatever its last release build left,
# and one built without the init script is com.casco.kettle itself.
build_tools="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
for i in 0 1; do
  actual="$("$build_tools/aapt2" dump packagename "${apks[$i]}")"
  if [ "$actual" != "${packages[$i]}" ]; then
    echo "${apks[$i]} is $actual, not ${packages[$i]}; rebuild without --skip-build" >&2
    exit 1
  fi
done

# --user 0: a Secure Folder user makes plain pm calls fail. --no-streaming: see native-dev.ps1.
for i in 0 1; do
  step "Install ${packages[$i]}"
  a install --user 0 -r --no-streaming "${apks[$i]}"
done
step 'Install the benchmark'
a install --user 0 -r --no-streaming "$benchmark_apk"

# Each target launched once first, so the first-launch seed write isn't in iteration one.
for package in "${packages[@]}"; do
  # `am start -W` exits 0 on a failed launch too; only its Status line says.
  launch="$(a shell am start -W -n "$package/com.casco.kettle.MainActivity")"
  if ! grep -q 'Status: ok' <<<"$launch"; then
    printf 'First launch of %s failed:\n%s\n' "$package" "$launch" >&2
    exit 1
  fi
  sleep 5
  step "Stop $package"
  a shell am force-stop "$package"
done

results='/sdcard/Android/media/com.casco.kettle.benchmark/'
out="$native/benchmark/build/results"
# Both ends emptied first, so a run that dies before writing can't be read as the last one's results.
step 'Clear the last results on the phone'
a shell rm -rf "$results"
rm -rf "$out"
mkdir -p "$out"

# `am instrument -w` exits 0 when a test fails; only JUnit's summary line says, and it carries the
# count the results have to match.
step 'Run the benchmark'
instrument="$(a shell am instrument -w --user 0 "$runner")"
echo "$instrument"
expected="$(sed -nE 's/.*OK \(([0-9]+) tests?\).*/\1/p' <<<"$instrument" | head -1)"
[ -n "$expected" ] || { echo 'The benchmark failed (no "OK (n tests)" from am instrument)' >&2; exit 1; }

step 'Pull the results'
a pull "$results" "$out"

# A run whose marker never fired still passes: Macrobenchmark just leaves timeToFullDisplayMs out, or
# short of an iteration. Either way it isn't a result. Node because it's already a dependency here.
node - "$out" "$expected" <<'EOF'
const fs = require('fs');
const path = require('path');
const [out, expected] = process.argv.slice(2);
const files = fs.readdirSync(out, { recursive: true }).filter((f) => f.endsWith('benchmarkData.json'));
const runs = files.flatMap((f) => JSON.parse(fs.readFileSync(path.join(out, f), 'utf8')).benchmarks);
if (runs.length !== Number(expected)) {
  console.error(`${expected} tests passed but ${runs.length} results came back`);
  process.exit(1);
}
for (const run of runs) {
  const full = run.metrics.timeToFullDisplayMs;
  const count = full ? full.runs.length : 0;
  if (count !== run.repeatIterations) {
    console.error(`${run.className}.${run.name} has ${count} full-display times of ${run.repeatIterations}: the marker didn't fire every time`);
    process.exit(1);
  }
  const ms = (n) => Math.round(n).toLocaleString('en-US');
  console.log(`${run.name}: median ${ms(full.median)} ms, range ${ms(full.minimum)}-${ms(full.maximum)}`);
}
EOF
printf '\n\033[32mResults in %s\033[0m\n' "$out"
