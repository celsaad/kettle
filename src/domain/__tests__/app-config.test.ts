/**
 * Two invariants `app.json` states and nothing else enforces.
 *
 * Both are the same shape of failure: a field whose *correctness* lives in another file, where a
 * plausible-looking value ships and the cost lands on the store rather than on anyone editing it.
 * Same bargain `store-copy.test.ts` strikes with the listing's character counts.
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };

import { resources } from '@/i18n';

const { readFileSync } = require('node:fs');

const config = JSON.parse(readFileSync(`${__dirname}/../../../app.json`, 'utf8')).expo as {
  version: string;
  ios: { buildNumber: string };
  android: { versionCode: number };
  plugins: (string | [string, Record<string, unknown>])[];
};

describe('the two build numbers', () => {
  /**
   * They are kept numerically identical so one integer serves both stores and the changelog heading
   * — which names only the versionCode — stays readable. Nothing in either store requires that; what
   * each requires is that its own number never repeats, and `/bump` moves the Android one. This is
   * what notices when it moves alone.
   *
   * If a rejected submission ever needs a second build of the same version, raise *both*: an Android
   * versionCode that skips an integer costs nothing, and a divergence here costs the invariant.
   */
  it('are the same number, in each store’s own type', () => {
    expect(config.ios.buildNumber).toBe(String(config.android.versionCode));
  });

  // A number here is accepted by the schema and produces a CFBundleVersion Xcode then argues with.
  it('keeps the iOS one a string', () => {
    expect(typeof config.ios.buildNumber).toBe('string');
  });
});

/**
 * The seventh place in `docs/adding-a-language.md`, made machine-checked.
 *
 * `expo-localization`'s `supportedLocales` is the only thing that writes `CFBundleLocalizations`, and
 * iOS filters `Locale.preferredLanguages` against it — so a language registered in `resources` but
 * missing here is unreachable on every iPhone while every other test stays green. That silence is
 * the entire failure mode, and it is why this is asserted rather than documented.
 */
describe('the iOS locale declaration', () => {
  const plugin = config.plugins.find(
    (entry): entry is [string, { supportedLocales: { ios: string[] } }] =>
      Array.isArray(entry) && entry[0] === 'expo-localization',
  );

  it('declares every language the app actually ships', () => {
    expect(plugin?.[1].supportedLocales.ios.toSorted()).toEqual(Object.keys(resources).toSorted());
  });
});
