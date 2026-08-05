---
description: Tag a version that has already been bumped, publish the signed APK, and cut the GitHub Release
argument-hint: "[version, e.g. 0.4.0]"
allowed-tools: Read, Bash(git:*), Bash(node:*), Bash(gh:*), Bash(sha256sum:*), Bash(unzip:*)
---

Cut the public release for: **$ARGUMENTS** (if that's empty, use the version currently in `app.json`).

## Where this starts, and why it isn't `/bump`

`/bump` ends with "don't tag, don't run a build" on purpose: **deciding a version and publishing an
artefact are separate concerns**, and folding them together means a version bump you can't land
without also being ready to ship a binary. This command picks up exactly where that line falls.

So `/bump` has already run by the time you're here. `app.json` carries the version and versionCode,
`CHANGELOG.md` has a closed-out section for it, and the release commit is on `master`. If any of that
isn't true, stop and run `/bump` first — **this command never edits `app.json` or the changelog.**

Building the `.aab` that goes to Play is a third thing again, and also not this command's:
`.github/workflows/android.yml` does it on every merge to `master`, and `docs/building-android.md`
covers the keystore setup and the manual Play upload. What this command owns is the tag, and the
artefact that goes to people who don't get their copy from Play.

## 1. Tag

Tag the **`Release <version> (versionCode <n>)` commit itself**, not the merge commit above it: the
tag should name the commit that set the version, so `git describe` and a `git log <tag>..HEAD` read
the way people expect.

```
git tag -a v<version> <release commit> -m "Kettle <version> (versionCode <n>)"
git push origin v<version>
```

Annotated, not lightweight — a lightweight tag carries no date or author, and this is the record of
when a build went out. Tags are `v`-prefixed; the changelog headings are not. Don't retag a version
that's already pushed; a mistake gets a new version, not a moved tag.

## 2. The APK, and the one thing that is not recoverable

**Read this before uploading anything.** Getting it wrong cannot be fixed for anyone who has already
installed the wrong file.

The Play build is an **app-bundle**, which Play App Signing **re-signs with Google's key**. Android
identifies an installed app by its signature, so an APK signed with any *other* key is a different
app as far as the OS is concerned. It refuses to install over the Play version, and the only way
across is uninstall-and-reinstall — which deletes the user's entire training log.

**The decision, already taken (see `docs/decisions.md`): the public APK is the Play-signed universal
APK**, downloaded from the Console. It is not built anywhere — not by CI, not by EAS.

Get it from **Play Console → Release → App bundle explorer → the versionCode for this release →
Downloads → "Signed, universal APK"**. That file carries the app signing key, so it is byte-for-byte
the same identity Play installs: someone can sideload it and later move to Play, or the reverse,
keeping their data either way.

**This step is a human's, and this command must stop and ask for it rather than route around it.**
There is no API for that download, and there is now a substitute sitting right there in the repo that
looks exactly like the answer:

> ```
> gh workflow run android.yml -f variant=apk      # ← NOT the release artefact
> ```
>
> `docs/building-android.md` calls that variant "sideloadable", and it is — onto *your own phone*. It
> is wrong for a release twice over, and both are silent:
>
> - **It is signed with the upload key**, which Play replaces with the app signing key on every
>   upload. So it has the one signature this decision exists to avoid.
> - **It is `arm64-v8a` only**, built that way to save three quarters of the C++ compile. The
>   Console's universal APK carries every ABI. This one won't install on a 32-bit ARM or x86 device
>   and gives no useful reason why.
>
> The workflow's 14-day artifact retention says the same thing in another way — the doc's own note is
> "an upload key is not a distribution key, so these are only useful to whoever can already push to
> Play." A release asset that expires in a fortnight was never the plan.

`eas build --profile preview` is the same mistake by the older route. EAS no longer builds anything
here (see `docs/building-android.md`); `eas.json` is kept only for one-off credentials access.

Once you have the file, before attaching it:

- Confirm the versionCode matches the tag (`unzip -p app.apk AndroidManifest.xml` won't read cleanly;
  `aapt dump badging app.apk` is the readable one if the SDK build-tools are on PATH).
- Record its `sha256sum` and put it in the release notes. It costs one line and it is the only way
  anyone can check that what they downloaded is what you published.

## 3. The GitHub Release

Notes come from **the `<version>` section of `CHANGELOG.md`** — the "What changed" part. Do **not**
re-draft them: that section was written as the feature shipped, it is what the changelog is for, and a
second version of the same prose is a second thing to keep true. The Play release notes block in that
same section is *not* what goes here — it is 500-character store copy written for a different reader.

Every release's notes must also carry, in plain words near the top:

- **Which key the APK is signed with**, and what that means for moving between this download and Play.
  Assume the reader has no idea what app signing is.
- **The sha256** of the attached file.
- **Android only.** There is no iOS build (see the tip-jar entry in the decision log for why).

```
gh release create v<version> <apk> --title "Kettle <version>" --notes-file <notes>
```

Create it as a **draft** if the APK isn't in hand yet, so a published release never promises a
download that isn't attached. Publishing is the last step, not the first.

## 4. Then

Say what you did, and specifically **name anything you couldn't do** — an unattached APK is the whole
point of the release, so a release created without one is not finished, however complete the notes
are.

Out of scope here, deliberately: submitting to IzzyOnDroid, F-Droid or Accrescent. This establishes
the artefact; where it gets distributed is a separate decision, and F-Droid in particular re-signs
with its *own* key, which is the same trap a third time.
