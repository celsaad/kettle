> **Research, not a plan.** Answers #224 (child of the M5 map #219): *how* the seed library and the
> content packs become data files that RN and native both read. The *that* is settled in the decision
> log's single-source entry (#147). Written 2026-10-28 against `master` at 94edf09. This branch is
> never merged; the implementing PR carries what it needs from here into code and the decision log.

# Seed and packs as data files both sides read

## Answer

1. **Files live in `src/content/`**, as library YAML plus one translation JSON per language:

   ```
   src/content/
     seed/library.yaml   seed/pt.json   seed/ja.json
     packs.json                          # [{ "id", "idPrefix" }, ...] in import-screen order
     packs/<pack-id>/library.yaml   packs/<pack-id>/pt.json   packs/<pack-id>/ja.json
   ```

   `src/` because that is where the two existing shared data files already sit and are read in place
   by Gradle (`src/i18n/locales/*.json`, `src/constants/theme.json`). The YAML is the user's own file
   format, so the authoring notes at the top of today's `seed-library.ts` can stay beside the data as
   YAML comments. JSON can't hold those, which is why `theme.json` needs a TS wrapper for its notes.
   The translation JSON keeps the `LibraryTranslation` shape exactly as it is today
   (`days`, `exercises`, `workouts`, `programs.weekNotes` keyed `"week|day"`).

2. **RN reads the YAML as a string inlined by Metro**, through a ~6-line local Babel-transformer
   wrapper and a matching ~3-line Jest transform, and then runs it through the ordinary
   `parseLibraryYaml`. No new dependency: the wrapper uses `@expo/metro-config`, which is already
   installed. The translation JSON needs nothing, because Metro and Jest already import `.json`.

3. **Android packages the same directory as APK assets** with one line in `:app`'s
   `androidComponents.onVariants` (`variant.sources.assets?.addStaticSourceDirectory(...)`). It reads
   them with `AssetManager` and parses with `:shared`'s existing `parseLibraryYaml`. Nothing is
   generated and nothing is committed twice. `localizeLibrary` is behaviour, so it is **ported** to
   `:shared` and held by a new conformance suite. It is not data.

4. **iOS (M8)** adds the same directory to the app bundle as a folder reference and reads it with
   `Bundle.main`. It is the same split as Android: files in the bundle, parse in `:shared`.

5. **Tests:** the existing invariants (id prefix, no collisions, reachability, translation coverage
   and parity, merge into seed) carry over unchanged in substance and read the files instead of the
   TS objects. Conformance gains **four `source:` cases** in the `library` suite, the same mechanism
   that already carries `site/examples/*.yaml`, plus a new **`localize` suite**. A small test holds
   `packs.json`, the TS `require` map and the directory listing to the same set of ids.

The rest of this file is the evidence for each step and the alternatives that were weighed.

## What exists today (repo, verified)

- `src/storage/seed-library.ts` (460 lines): one `Library` literal plus `seedLibraryFor(language)`.
  `seed-translations.ts` (255) holds `pt` and `ja` tables. `content-packs.ts` (540) holds three
  libraries, built with helpers (`restDay`, `threeDayWeek`, `fourSets`), plus the
  `{ id, idPrefix, library }` list. `content-pack-translations.ts` (388) holds their tables.
  `library-translation.ts` (90) holds the table type, `localizeLibrary`, `libraryWeekKey` and
  `translationFor`.
- Consumers outside tests: `storage/library-file.ts` (`seedLibraryFor(i18next.language)`, computed
  **on every `loadLibrary` call**, even when the file exists) and `app/import.tsx`
  (`contentPacks`, `contentPackLibrary`, `contentPackCounts`). `components/exercise-art/map.ts` only
  mentions the seed and packs in comments, but its test imports both.
- Tests that import the objects: `seed-library.test.ts`, `content-packs.test.ts`,
  `site-examples.test.ts` (merges each example into `seedLibrary`), `files-changed.test.ts`,
  `exercise-art.test.tsx`, `import.test.tsx`, `library-file.test.ts`.
- There is **no `metro.config.js`**, so the app runs on `expo/metro-config` defaults.
- Native generation precedent (`native/android/app/build.gradle.kts`): `GenerateStrings` and
  `GenerateTheme` are `JavaExec` tasks. They read `../../src/i18n/locales` and
  `../../src/constants/theme.json` in place, write into a build directory, and are wired with
  `variant.sources.res/java?.addGeneratedSourceDirectory`. The same files reach the tests through
  `CommandLineArgumentProvider`s. `:shared` reads `conformance/` in place the same way
  (`ConformanceCorpus`). It has no resources and is JVM-only for now.
- `:shared` already has `parseLibraryYaml`, `serializeLibraryYaml`, `mergeLibraries` and
  `repairLibraryBounds` (`LibraryYaml.kt`, `Merge.kt`, `Repair.kt`). It has no `localizeLibrary`.
- Both loaders accept YAML anchors and aliases, capped at 1000 (`yaml-mapping.ts` `LOAD_OPTIONS`;
  `YamlLoad.kt` `MAX_ALIASES`).
- `conformance/library.yaml` already has `source:` cases that point at
  `site/examples/*.yaml`, read in place. The README documents this ("`parseLibraryYaml(input)`, or
  the file at `source`").

## RN: getting a `.yaml` file into the bundle

### What Metro and Jest do with `.yaml` today (node_modules, verified)

- **Metro treats `.yaml` as an asset, not source.** `metro-config@0.84.5`
  `src/defaults/defaults.js` lists `yaml` and `yml` in `assetExts`. Its `sourceExts` are
  `js, jsx, json, ts, tsx`. `@expo/metro-config@57.0.12` `build/ExpoMetroConfig.js` keeps Metro's
  `assetExts`, adds `heic, avif, db`, and builds its own `sourceExts`. So `require('x.yaml')` today
  returns an **asset reference** (a number on native), not the text. The Expo SDK 57 Metro page
  agrees: "When assets are imported, a virtual module is created… On native, it is a numeric ID."
- **jest-expo does the same.** `jest-expo/jest-preset.js` lists `yaml`/`yml` in its asset
  extensions and maps them to `src/preset/assetFileTransformer.js`, which returns
  `module.exports = 1;`. Confirmed by running it: a `require` of a `.yaml` file in a test returned
  `number`.

### Recommended: a local transformer that inlines the text

The Expo SDK 57 Metro page ("Extending the Babel transformer") documents the pattern. Wrap
`@expo/metro-config/babel-transformer`, change the source for the file types you care about, and
pass everything on to the upstream transformer. Its own example does this for `.svg`.

```js
// metro.transformer.js
const upstream = require('@expo/metro-config/babel-transformer');
module.exports.transform = ({ src, filename, options }) =>
  upstream.transform({
    src: /\.ya?ml$/.test(filename) ? `module.exports = ${JSON.stringify(src)};` : src,
    filename,
    options,
  });
module.exports.getCacheKey = upstream.getCacheKey;

// metro.config.js
const { getDefaultConfig } = require('expo/metro-config');
const config = getDefaultConfig(__dirname);
config.transformer.babelTransformerPath = require.resolve('./metro.transformer.js');
config.resolver.assetExts = config.resolver.assetExts.filter((e) => e !== 'yaml' && e !== 'yml');
config.resolver.sourceExts = [...config.resolver.sourceExts, 'yaml'];
module.exports = config;
```

Jest needs the matching transform. A project `transform` key wins over the preset's, which was
verified in `jest-config/build/normalize.js` `mergeOptionWithPreset` (project keys are spread first)
and `@jest/transform` `_getTransformPatternAndPath` (first matching pattern wins):

```jsonc
"transform": { "\\.ya?ml$": "<rootDir>/jest.yaml-transform.js" }
// jest.yaml-transform.js: module.exports = { process: (src) => ({ code: `module.exports = ${JSON.stringify(src)};` }) };
```

Plus `declare module '*.yaml' { const text: string; export default text; }` for `tsc`.

**This was prototyped in this worktree and then reverted.** The steps:

- The seed and the kettlebell pack were emitted to `src/content/` with `serializeLibraryYaml`.
  The seed came to 562 lines of YAML (13.9 KB) and the kettlebell pack to 258 lines. The tables came
  to about 5 KB of JSON per language.
- **Jest:** `import seedYaml from '@/content/seed/library.yaml'` and
  `import pt from '@/content/seed/pt.json'` both work. `parseLibraryYaml(seedYaml)` `toEqual`s
  today's `seedLibrary`, and `localizeLibrary(parsed, pt)` `toEqual`s
  `localizeSeed(seedLibrary, 'pt')`. A full parse of the seed took about 0.6 ms per run under Jest
  on this Mac.
- **`tsc --noEmit`**: clean. The JSON import is typed through `expo/tsconfig.base`'s
  `resolveJsonModule`.
- **Metro, Android:** `expo export --platform android` (with `EXPO_OFFLINE=1`). The Hermes bundle
  contains the YAML text (`target_reps_min: 12`, `exercises:\n  - id: rest`), stored UTF-16 because
  the content has non-ASCII characters. No `.yaml` file appears among the exported assets.
- **Metro, web:** `expo export --platform web`. The YAML text is in the entry JS. This matters
  because the seed is the web build's entire library.
- The brownfield AAR's bundle comes from `expo export:embed`, which loads the project's Metro config
  (`@expo/cli/build/src/export/embed/exportEmbedAsync.js`, `loadMetroConfigAsync`). So the shell
  gets the same treatment. This step was not built end to end here.
- **`knip`** reported one finding: `@expo/metro-config/babel-transformer` is an **unlisted
  dependency** (it is a dependency of `expo` and is hoisted, but `package.json` doesn't list it).
  `expo` doesn't re-export it. The fix is to list `@expo/metro-config` at the version `expo` pins
  (`~57.0.12`, already in the lockfile, so nothing new is downloaded). The other fix is a `knip.jsonc`
  exception, but listing it is the house rule.
- **`oxfmt --check`** formats the `.yaml` files (they passed as serialized) and the `.json` files
  (it wanted to reformat them). So the new files fall under `pnpm run format` with no config change.

Metro's transform cache key hashes the file at `babelTransformerPath`
(`metro-transform-worker/src/index.js` `getCacheKey`). Editing the wrapper therefore invalidates the
cache, and delegating `getCacheKey` keeps upstream's part of the key.

### Alternatives weighed for RN

| Option | Why not |
|---|---|
| **Keep `.yaml` as an asset and read it at runtime** (`expo-asset` → `downloadAsync` → `File.text()`, or `fetch` on web) | It makes seeding async and turns a bundled constant into an I/O path that can fail: a release Android build copies the asset out of the APK first, and web fetches it. Jest gets `1`. More failure surface for content that is fixed at build time. |
| **Commit a generated `.json`/`.ts` form of the YAML** | It breaks the precedent that nothing generated is committed. A generated file can go stale and would need a freshness check. |
| **Author the library as snake_case JSON** (zero Metro config: JSON is YAML 1.2, so the same parser takes it) | The runner-up. Against it: no comments, so the seed's authoring constraints lose their home; it is not the user's format; 500 lines of program weeks are worse to hand-edit as JSON; and the decision log already says "library YAML". |
| **YAML inside a TS template literal** | Native would have to extract it from TS, which is exactly the "parse `theme.ts` from Kotlin" option the decision log rejects. |
| `require.context` over `src/content/packs` to avoid a hand-kept `require` map | Expo's Metro supports it (expo-router relies on it), but Jest doesn't without extra plumbing. Three static requires plus a test that checks them against `packs.json` cost less. |

### Consequences for the RN code (not done here)

- `seed-library.ts` becomes about a dozen lines: import the YAML and the tables, parse lazily, cache
  the result, localize. `content-packs.ts` becomes the `packs.json` list plus a static
  `{ id → require(yaml), tables }` map. The helpers (`restDay`, `threeDayWeek`, `fourSets`) disappear
  in favour of longhand YAML, which is also the shape users see in their exported file. YAML anchors
  would compress the repeated overrides, and both loaders accept them. But `site/format.html` doesn't
  teach anchors, so longhand is the conservative default.
- **Parse lazily.** `loadLibrary` calls `seedLibraryFor` on every launch today. With YAML that call
  becomes a parse, so it should run only on the seed and reseed paths.
- A shipped file that fails to parse is a build defect, and the tests stop it from shipping. At
  runtime the loader should throw rather than degrade, because there is nothing honest to degrade to.
- `library-translation.ts` keeps `localizeLibrary`. The `LibraryTranslation` type stays, and the
  JSON is checked against it at compile time through the typed JSON import.

## Native: packaging and reading

- **`:app` assets, in place.** AGP 8.12.0's `com.android.build.api.variant.SourceDirectories` has
  `addStaticSourceDirectory(String)`, and `Sources.getAssets()` returns a `SourceDirectories.Layered`
  (checked with `javap` against `gradle-api-8.12.0.jar` from the Gradle cache). So:

  ```kotlin
  onVariants { variant ->
      variant.sources.assets?.addStaticSourceDirectory(contentDir.asFile.path) // ../../src/content
  }
  ```

  Nothing is generated, because there is nothing to generate: the files are already in their final
  form. The precedent this follows is "read in place, never copy", not "generate". Assets land at
  the asset root (`seed/library.yaml`, `packs.json`, `packs/<id>/…`), which sits beside the RN
  bundle the AAR contributes. If a namespace is wanted, nest a `content/` folder inside the source
  directory.
- **Reading:** `context.assets.open("seed/library.yaml").reader().readText()` →
  `parseLibraryYaml` (`:shared`). Translation JSON can go through `:shared`'s existing YAML loader,
  since snakeyaml-engine is YAML 1.2 and JSON is valid YAML 1.2. That avoids adding
  kotlinx-serialization to `commonMain`. It is in the version catalog and used by `:strings`, so
  adding it would also be acceptable. Neither touches the network.
- **Port `localizeLibrary`** (plus `translationFor`'s base-subtag rule) into `:shared`. It is
  behaviour, and the decision log says behaviour is ported and held by conformance.
- **`:shared` resources were weighed and rejected.** Java resources would work on Android through
  the classloader, but KMP has no resource story for iOS without Compose Multiplatform resources,
  and the domain module shouldn't own app content. Generating Kotlin string constants (the `:theme`
  pattern) was also weighed. It works on every KMP target, but it is generation with nothing to
  generate, and the decision log says Swift reads the files, not a Kotlin copy.
- **iOS (M8):** add `src/content` to the Xcode target as a folder reference (Copy Bundle Resources)
  and read it with `Bundle.main.url(forResource:withExtension:subdirectory:)`. Parsing goes through
  `:shared`'s iOS framework, which needs `:shared`'s iOS targets. Whether snakeyaml-engine-kmp
  covers them is an M8 check. **Unverified: no Mac.**

## Tests and conformance

- **Carry over, reading files:** `seed-library.test.ts` and `content-packs.test.ts` keep every
  invariant (prefix declared and held both ways, no collisions with the seed or between packs, own
  rest exercise, reachability, all seven types in the seed, `foundations` first, weeks enumerated
  and in order, translation coverage, no stale keys, "changes nothing but the strings", regional
  fallback). Their subject becomes the parsed file. The "round-trips through the YAML layer" test
  stays meaningful as parse → serialize → parse.
- **New, small:** the `packs.json` ids, the TS `require` map's keys and the directory names under
  `src/content/packs/` are one set. On the native side, a Robolectric test in `:app` opens each
  asset and parses it, which proves the packaging line works.
- **Conformance: yes, cheaply.**
  - Add `source: src/content/seed/library.yaml` and one `source:` per pack to the `library` suite,
    the same as the four site examples. That holds the Kotlin parser to the TS one on the real
    shipped content, with no copy. Cost: the goldens grow (`library.expected.json` is 63 KB today
    with four examples), and every content edit moves them. That diff is the reviewable record of
    what changed.
  - Add a `localize` suite for the ported `localizeLibrary`: a `source` library plus a
    `translation:` JSON path → the localized library. It also needs inline cases for the edge
    behaviour: a missing `notes` falls back to English, an untranslated `day` keeps its English
    label, a rest day's `day`/`notes`, and a `pt-BR` tag reads as `pt`.
- The **content invariants stay TS-only.** The content is one set of files, so its rules need
  checking once. What native has to agree on is behaviour, and conformance covers that.
- Callers to update: `site-examples.test.ts`, `files-changed.test.ts`, `exercise-art.test.tsx`,
  `import.test.tsx` and `library-file.test.ts` switch from the objects to the loaders.

## Docs the implementing PR touches

`AGENTS.md` ("Where things live", storage bullet). The decision log's seed and packs entries, which
cite `seed-library.ts` and `content-packs.ts` by name; the single-source entry's "Next instance"
bullet becomes a landed note. `docs/adding-a-language.md`, step 4 (`seed-translations.ts` becomes a
`<lang>.json` per seed and per pack). `exercise-art/map.ts`'s header comment.

## Bearing on other M5 tickets

- **#222 (one flip or per file):** the data move can land **before** the flip, in its own PR. The RN
  side is a pure refactor with identical behaviour, and the native side reads files nothing routes
  to yet. It doesn't have to ride the flip release.
- **#220 / #221 (what RN writes, and when it starts, after the flip):** `loadLibrary` *writes* the
  library in three places today: the first-launch seed, the clamp-and-save repair, and the reseed
  after quarantine. If native is the only writer of the library file, those three moves go native,
  and native has to seed before RN ever reads. The seed data being shared is what makes that
  possible; the ordering is those tickets' to settle.
- **#225 (drawings):** the same transformer wrapper can inline `.svg` files as text for
  `SvgXml`. Expo's own example of the pattern is for `.svg`. `exercise-art.test.tsx` checks drawing
  ids against the seed and packs, so it should read the data files once they exist.

## Open risks

- **The AAR end to end** (`scripts/native-dev.sh` → shell on a phone) was not run. The claim rests on
  `export:embed` loading the project Metro config, which was read in source, not observed in a
  build.
- **Hermes parse cost** was not measured. The 0.6 ms figure is from Node under Jest. The seed is
  parsed once per seed or reseed (once lazy parsing is in), and each pack once when the import screen
  counts it, so even a 10× slowdown is not felt.
- **iOS** is unverified (no Mac). See above.
