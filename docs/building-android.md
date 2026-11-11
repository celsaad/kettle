# Building the Android release on GitHub Actions

> **Live.** `.github/workflows/android.yml` builds the Play bundle on a GitHub runner, on every merge
> to `master` and on demand: one Gradle build of `native/android`, the native app. Releases up to
> 0.11.0 were the prebuilt RN app, and until M7 (#357) the native shell hosted it as an AAR. EAS is
> not used at all. The keystore setup here is a prerequisite — without it the workflow fails on the
> first run, deliberately and early, which for a merge trigger means every merge until it's done.

## The pipeline

One job and one Gradle build. It sets up JDK 17, which Gradle runs on, and JDK 21, which the build
names as its test toolchain, restores the Gradle cache, then runs `native/android`'s
`:app:bundleRelease` (or `:app:assembleRelease` for an `.apk`), signed with the upload key from the
repo's secrets. No Node: the build reads the repo's plain files (the locale bundles, `theme.json`,
`src/content`, the sounds, the committed fonts) and nothing a JS install provides. The application
id is `com.casco.kettle` and the version is `native/android/gradle.properties`', so the bundle
installs over a Play copy in place.

## Why not EAS

The build is ordinary Gradle. What EAS added was a queue, and on the free tier the queue was most of
the wall clock. What was given up: EAS's credential store (replaced by four repository secrets) and
its build history (replaced by the Actions run list).

## One-time setup

### 1. The upload keystore

**Play App Signing means this is the *upload* key, not the app signing key.** Google holds the
signing key and re-signs every upload; the upload key only proves the upload is from you. Losing it
is recoverable (Play Console › Setup › App integrity resets it), and so is leaking it. It is still a
secret, just not an unrecoverable one.

It came out of EAS's credential store once (`eas credentials`, before EAS left the repo at M7), and
lives in the four secrets below. A `.jks` copy is kept off the repo.

### 2. Put it in four repository secrets

The keystore is binary and a secret is text, so it goes in base64. PowerShell, since that's what this
is run from — `gh secret set NAME < file` is the documented form and PowerShell has no `<` redirect,
so the encode and the upload are one expression instead and no `.b64` is left lying about:

```powershell
gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64 --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")))
```

The other three take no file and prompt, so they are the same everywhere: the keystore password, the
key alias and the key password.

```powershell
gh secret set ANDROID_UPLOAD_KEYSTORE_PASSWORD
gh secret set ANDROID_UPLOAD_KEY_ALIAS
gh secret set ANDROID_UPLOAD_KEY_PASSWORD
```

Confirm the alias and the keystore password against the file rather than against what you copied — a
wrong alias fails half an hour into a build. `keytool -list -v -keystore upload.jks` prompts for the
keystore password and prints `Alias name:`; `keytool -certreq -keystore upload.jks -alias <alias>` is
the non-destructive way to check the *key* password, which `-list` never touches.

Keep the `.jks` somewhere you'd still have it after a laptop dies.
`*.jks` is gitignored, which stops the obvious accident and nothing else.

**The repository being public doesn't expose these.** Secrets are never given to a workflow triggered
from a fork, and this one runs only on a push to `master` or on a manual dispatch — both of which
already require write access. Don't add a `pull_request` trigger to it: that is the single change
that would break the property.

### 3. Record the app signing certificate

**Play Console › Setup › App integrity** prints two SHA-256 fingerprints. Both belong here, and
confusing them is the whole reason they are written down together rather than looked up separately
each time:

| | SHA-256 | What it is |
| --- | --- | --- |
| **Upload certificate** | `48:0A:30:FE:53:E5:84:D0:AF:17:1F:1E:89:39:57:C2:86:12:E5:3E:90:29:44:38:36:5F:6E:E5:AA:0A:AA:54` | What this workflow signs with. Every `.aab` and `.apk` built here carries it. Play strips it. |
| **App signing certificate** | `_(fill in from the Console)_` | What Play re-signs with, and therefore what every installed copy actually carries. |

The upload row is the certificate on the 0.11.0 release bundle, read from the log of the `master`
run that built it, and is pinned in `android.yml` as `UPLOAD_CERT_SHA256`. Check it against the
Console once; if the two ever disagree, the Console wins and both copies here are wrong.

Neither is a secret — they are public certificates, which is why they can live in the repo. It is the
private keys they belong to that differ: Google holds the app signing one and will never release it.

They earn their place here twice:

- **The workflow's "Check the signing certificate" step** fails unless the build carries the *upload*
  row. That is the check for a build that came out unsigned or signed with some other key, which
  produces a perfectly valid-looking file that Play rejects half an hour later.
- **`/release` checks a candidate APK against the *app signing* row** before it is attached to a
  GitHub Release. An APK matching the upload row instead is the one artefact that must never be
  published — see the signing entry in [`decisions.md`](decisions.md).

## When it runs

**On every merge to `master`**, skipping prose-only changes (`docs/`, `site/`, `store/`, any `.md`).
It is the only signed release build CI runs (`ci.yml`'s `gradlew build` has no upload key), so it's
what catches a dependency bump breaking the signed bundle.

**Manually**, when you want an `.apk` or a build at a bumped `versionCode`:

```sh
gh workflow run android.yml -f variant=aab
gh run watch
```

A dispatch with `--ref <branch>` runs that branch's copy of the workflow, which is how a change to the
pipeline itself gets its first run before it merges.

- **`aab`** — the Play upload.
- **`apk`** — sideloadable, a universal APK: the app has no native code of its own to split by ABI.
  It's signed with the same upload key, which is fine for a phone and is *not* enough for in-app
  purchases: Play Billing only resolves products in a build installed from Play (see
  `store/README.md` on licence testing).

  **It is also not the APK that gets published**, and the reason is silent: the upload-key signature
  makes it a different app from a Play install, so nobody can move between the two without
  uninstalling and losing their log. The public artefact is *downloaded* from Play rather than built
  here — see the signing entry in [`decisions.md`](decisions.md), and `/release`.

Both variants are the same signed release build; the merge trigger produces an `.aab`. The artifact
is attached to the run for 14 days, named by run number rather than by version — only a release commit
bumps `versionCode`, so a week of merge builds are all `kettle 0.3.0 (6)` and nothing inside the file
distinguishes them.

Upload to Play by hand. Automating it needs a service account JSON, which is a fifth secret with
write access to the listing, and hasn't been worth it for a release every few weeks.

**`versionCode` comes from `native/android/gradle.properties`** (`kettle.versionCode`), which
`app/build.gradle.kts` reads, and Play rejects a re-used one. It continues the prebuilt app's line,
since the native app replaced that app in place. `/bump` is what increments it; the workflow doesn't.

## What it costs

A Kotlin and resources build, cached by `gradle/actions/setup-gradle` plus `--build-cache` (the
dependency and wrapper downloads, and the Kotlin and Java compilation). Until M7 most of the wall
clock was the RN AAR's C++, behind a ccache with knobs of its own; that went with React Native, and
so did the job's 90-minute timeout, now 30.

## How signing works, and the trap it's built around

The shell's `app/build.gradle.kts` gives the release build type an `upload` signing config **only when
`KETTLE_UPLOAD_KEYSTORE` is set**, reading the path and the three passwords from the environment at
Gradle time. Nothing is written into a file, so a failed build's tree is safe to attach to a run.
That holds because `android.yml` passes `--no-configuration-cache`: `gradle.properties` turns the
configuration cache on, and with it a configuration that read the passwords is written under
`native/android/.gradle/`. A signed build run by hand wants the same flag.
Everywhere else — a local `:app:build`, `ci.yml`'s `gradlew build` — release builds come out
unsigned, which is harmless: an unsigned file installs nowhere and Play refuses it.

The debug build type signs with Expo's public template debug key. It signs nothing that leaves a
developer's machine.

**The trap is a build that looks signed and isn't, or is signed with the wrong key.** Both produce a
valid-looking `.aab` that Play rejects on upload, half an hour after the mistake; the prebuilt app's
template had one built in, signing release with the debug keystore on purpose. So the workflow doesn't
trust the build: its "Check the signing certificate" step reads the certificate off the output
(`keytool` for an `.aab`, `apksigner` for an `.apk`) and **fails the run** unless it is
`UPLOAD_CERT_SHA256`. That step is the backstop, not the first line: a missing keystore secret fails
earlier, at "Restore the upload keystore", and a wrong password or alias fails in Gradle's own
signing. What reaches the check is the quiet case, a build that came out unsigned because
`KETTLE_UPLOAD_KEYSTORE` didn't reach Gradle, or signed with the wrong key. Every case fails the run.

That pinned value changes only if the upload key itself is reset in the Console. Changing which key
signs what is the one mistake in this repo that can't be fixed for people who already installed — read
the signing entry in [`decisions.md`](decisions.md) before touching it.
