// Points the release build type at a real upload key.
//
// `expo prebuild` writes `android/` from the bare template, which deliberately signs release builds
// with the *debug* keystore ("Caution! In production, you need to generate your own keystore file").
// Play refuses an upload signed that way, so CI has to repoint it before `bundleRelease` runs.
//
// This lives in CI rather than as an app.json config plugin on purpose: it's a release concern, and
// a plugin would run on every local `expo prebuild` too, where there is no keystore and no reason to
// diverge from the template. `android/` is gitignored and regenerated on every run, so patching it
// here leaves nothing behind.
//
// Secrets are read from the environment at Gradle time rather than written into the file, so a
// failed build that uploads or logs the generated `android/` tree can't leak them.

import { readFileSync, writeFileSync } from 'node:fs';

const BUILD_GRADLE = 'android/app/build.gradle';

const RELEASE_SIGNING_CONFIG = `
        release {
            storeFile file(System.getenv("KETTLE_UPLOAD_KEYSTORE"))
            storePassword System.getenv("KETTLE_UPLOAD_KEYSTORE_PASSWORD")
            keyAlias System.getenv("KETTLE_UPLOAD_KEY_ALIAS")
            keyPassword System.getenv("KETTLE_UPLOAD_KEY_PASSWORD")
        }`;

// A silent no-op here produces a debug-signed .aab that looks like a build succeeded, so every
// missing anchor is fatal and names what has to be re-read.
function fail(message) {
  console.error(`inject-release-signing: ${message}`);
  console.error(`The bare template's ${BUILD_GRADLE} has changed shape — re-read it and update this script.`);
  process.exit(1);
}

let gradle = readFileSync(BUILD_GRADLE, 'utf8');

// Order matters. The buildTypes edit runs first, while `release {` unambiguously means the build
// type; inserting the signing config first would give the regex below a second `release {` to find.
const buildTypesIndex = gradle.indexOf('buildTypes {');
if (buildTypesIndex === -1) fail('no `buildTypes {` block found');

// `[^}]*?` rather than `[\s\S]*?` so the match can't run past the end of the release block and pick
// up the assignment belonging to the debug build type. The `=` is optional because the template
// switched from Groovy's setter syntax to a plain assignment and either spelling still works.
const releaseSigning = /(release\s*\{[^}]*?signingConfig\s*=?\s*signingConfigs\.)debug/;
const buildTypes = gradle.slice(buildTypesIndex);
if (!releaseSigning.test(buildTypes)) fail('the release build type does not use `signingConfigs.debug`');

gradle = gradle.slice(0, buildTypesIndex) + buildTypes.replace(releaseSigning, '$1release');

if (!gradle.includes('signingConfigs {')) fail('no `signingConfigs {` block found');
gradle = gradle.replace('signingConfigs {', `signingConfigs {${RELEASE_SIGNING_CONFIG}`);

writeFileSync(BUILD_GRADLE, gradle);
console.log('inject-release-signing: the release build type now signs with signingConfigs.release');
