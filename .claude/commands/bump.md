---
description: Bump the app version and each shipping platform's build number, close out the changelog, and commit the release
argument-hint: "[patch|minor|major|<explicit version>]"
allowed-tools: Read, Edit, Bash(git:*), Bash(node:*), Bash(pnpm run typecheck), Bash(pnpm run lint), Bash(pnpm test), Bash(gh pr:*)
---

Cut a release by bumping the version. The requested bump is: **$ARGUMENTS** (if that's empty, work out
whether it should be patch or minor from the commits since the last release, and say which you chose
and why before editing anything).

## Where the version lives

**Each platform keeps its version in one file of its own** (`CHANGELOG.md` also changes, but it
records the release rather than defining it, see below). Find the platforms by looking for those
files, not by assuming:

- **Android:** `native/android/gradle.properties`, as two properties that `app/build.gradle.kts`
  reads:
  - `kettle.versionName`, the human version, e.g. `0.15.0`;
  - `kettle.versionCode`, an integer, **always +1**. Play rejects an upload whose versionCode isn't
    higher than the last one it accepted, and it never resets when the human version changes.
- **iOS:** none yet. M8 adds one, e.g. `native/ios/Version.xcconfig` with `CFBundleVersion` beside
  the shared version name. Until a file like that exists, there is nothing to bump.

So, before editing:

- **One version file found:** say "iOS has no config until M8, bumping Android only" and go ahead
  without asking.
- **More than one found:** ask "Android, iOS, or both?" and bump only the platforms chosen.

The rule across platforms: **each platform has its own build number** (`versionCode`,
`CFBundleVersion`), which goes up only when that platform ships. **The version name is shared**,
taken from `CHANGELOG.md`'s heading. A platform that skips a release skips that version name; it
doesn't reuse it later.

If a rejected submission ever needs a *second* build of the same version, raise that platform's build
number again. A build number that skips an integer costs nothing.

One file that looks like it should change and must not: **`package.json`'s `"version": "1.0.0"`**,
which every release this project has cut has left alone. Nothing reads it as the app's version.

## Choosing the number

Read `git log --oneline <last release tag or commit>..HEAD` and judge by what a user would notice:

- **patch** — fixes and corrections only; nothing new to find in the UI.
- **minor** — any new user-facing behaviour, however small. This is the usual one here.
- **major** — reserved for a break in the YAML format or the on-disk layout, since that's the promise
  the app actually makes to the files people hand-edit and share. A redesign is not a major.

A refactor with no behaviour change doesn't drive the decision on its own — note it in the message and
let the user-facing work set the level.

## The changelog

`CHANGELOG.md` at the repo root is the other half of a release, and the bump is the moment it gets
closed out. The whole design of that file is that `## Unreleased` is written up *as features ship*, so
by now the writing should already be done and this is a promotion, not a drafting job.

Turn the `## Unreleased` section into the release:

1. **Rename the heading** to `## <version> — versionCode <n>, <date>`, matching `0.3.0`'s
   (`## 0.3.0 — versionCode 6, 2 August 2026`). The date is the day you cut it, written out — `2 August
   2026`, not `2026-08-02`. Use the same version and versionCode you just put in
   `gradle.properties`.
2. **Drop the "not in any build yet" paragraph** under the old heading. It's true only of Unreleased,
   and left behind it says the opposite of what the section now means.
3. **Open a fresh `## Unreleased` above it**, holding one line — `Nothing since <version>.` — so the
   next feature has an obvious place to land and nobody has to reconstruct the section's shape.

Then check the release notes that go up with it:

- **They must exist.** If `## Unreleased` has a `### Play release notes` block, it's the copy for this
  release and you're done. If it doesn't — or if the `### What changed` list has grown past what the
  notes describe — that's a writing job, and it's the user's call how the release reads. Say what's
  missing and stop before committing rather than drafting Play copy on your own.
- **Recount if you touched them at all.** 500 characters per language block, and over-limit is a
  *rejected upload*, not a truncated one. Count programmatically — `node -e` reading the file — and
  update the "Counted at N (en-US) and N (pt-BR)" line to the real numbers. The numbers written beside
  a block have been wrong before, which is why `store-copy.test.ts` asserts the limit; nothing asserts
  the prose.
- **Only include a `<pt-BR>` block if the listing carries that language.** An unsupported tag is
  rejected on upload.

Don't rewrite history: released sections above are the record of what testers actually read, so
correcting an old one is a separate change with its own reason, not something a bump does in passing.

## Which branch

Check what's in flight before deciding, and say which you're doing:

- **If there's an open PR whose work belongs in this release**, carry the bump as a commit on that
  branch, so merging it is what cuts the release. This is what 0.2.1 and 0.3.0 both did.
- **Otherwise** branch off `master` as `chore/version-<version>` and open its own PR. Nothing lands
  straight on `master` (see AGENTS.md), and `master` is the default branch here, not `main`.

## Then

1. Edit each chosen platform's version file: for Android, both properties in
   `native/android/gradle.properties`. Show the edit you're making before you make it.
2. Close out `CHANGELOG.md` as above.
3. Run `pnpm test`, `pnpm run typecheck` and `pnpm run lint`. Typecheck and lint should be untouched by
   a version bump; if they aren't clean, something else is wrong and the bump isn't the thing to fix it
   with. `pnpm test` earns its place here because `store-copy.test.ts` reads `CHANGELOG.md` and
   fails on a release-note block over 500 characters. Nothing checks the version file: read the diff.
4. Commit as **`Release <version> (versionCode <n>)`**, matching every previous release. The body says
   what's in it — the user-facing changes, by PR number where there is one — and why the level was
   patch/minor/major if that isn't obvious. Note anything deliberately excluded.
5. Push. Open a PR only if you made a new branch; if you added the commit to an existing PR's branch,
   say so rather than opening a second one.

Don't tag, don't run a build, and don't touch the Play listing. Deciding a version and publishing an
artefact are separate concerns, and folding them together means a bump you can't land without also
being ready to ship a binary — so tagging and the signed APK are `/release`'s, not this command's.
