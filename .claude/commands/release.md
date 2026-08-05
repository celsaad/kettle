---
description: Tag a version that has already been bumped, publish the signed APK, and cut the GitHub Release
argument-hint: "[version, e.g. 0.4.0]"
allowed-tools: Read, Write, Bash(git:*), Bash(node:*), Bash(gh:*), Bash(sha256sum:*), Bash(apksigner:*), Bash(aapt:*)
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

## 2. The APK

**The public APK is the Play-signed universal APK, downloaded from the Console.** It is not built
anywhere — not by CI, not by EAS. **Why** is the signing entry in
[`docs/decisions.md`](../../docs/decisions.md); read it once, and don't re-argue it here. The short
version is that an APK signed with any other key cannot install over a Play install, and the only way
across deletes the user's entire training log.

Get it from **Play Console → Release → App bundle explorer → the versionCode for this release →
Downloads → "Signed, universal APK"**.

**This step is a human's, and this command must stop and ask for it rather than route around it.**
There is no API for that download, and two substitutes look exactly like the answer:
`gh workflow run android.yml -f variant=apk`, and `eas build --profile preview`. Both produce a real
APK, both are signed with the upload key, and neither is a fallback when the Console is inconvenient.

### Prove it before you attach it

The failure is silent and unrecoverable, so it gets a command rather than a warning. All three need
the SDK build-tools on PATH.

```sh
apksigner verify --print-certs <apk>          # signer certificate
sha256sum <apk>                               # goes in the notes
aapt dump badging <apk> | head -1             # versionCode
```

Three things have to hold, and **any one of them failing means you have the wrong file — go back to
the Console**:

- The signer's SHA-256 matches the **app signing certificate** recorded in
  [`docs/building-android.md`](../../docs/building-android.md). This is the check that catches every
  wrong-artefact path at once, including both substitutes above. It is the *app signing* certificate,
  not the upload certificate printed beside it in the Console — matching the upload one means you are
  holding exactly the file this must never publish.
- The **versionCode matches the tag**.
- The **sha256** goes into the release notes. It is the only way a reader can check that what they
  downloaded is what you published.

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

**Draft first.** §2 makes the APK a manual Console download, so "the binary isn't in hand yet" is the
normal case rather than the exception — 0.4.0 was cut exactly this way. Write the notes to a file,
create the draft, and publish only once the file is attached and verified:

```sh
gh release create v<version> --draft --title "Kettle <version>" --notes-file <notes>
gh release upload v<version> <apk>
gh release edit v<version> --draft=false
```

A published release promising a download that isn't attached is worse than no release, which is the
whole reason the draft comes first. Publishing is the last step.

## 4. Before you publish, and then

Two things to check on the draft, both of which are only visible before the release goes out:

- **No placeholder text is left.** The notes carry a `sha256` line; a draft published with it still
  reading "added when the file is attached" advertises a verification path that doesn't exist, which
  is worse than omitting it. Read the rendered draft, not the file you wrote.
- **The asset is actually attached**, and it is the file §2's three checks passed on.

Then say what you did, and specifically **name anything you couldn't do**. The APK is the whole point
of the release, so a release created without one is not finished, however complete the notes are —
leave it a draft and say so rather than publishing to look done.

Out of scope here, deliberately: submitting to IzzyOnDroid, F-Droid or Accrescent. This establishes
the artefact; where it gets distributed is a separate decision, and F-Droid in particular re-signs
with its *own* key, which is the same trap a third time.
