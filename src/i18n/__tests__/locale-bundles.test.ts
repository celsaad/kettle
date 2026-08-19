import { changeLanguage, t } from 'i18next';

import { resources } from '@/i18n';

/**
 * The parity guard the locale bundles never had. `en.json` and `pt.json` were kept level by hand, and
 * nothing failed when they weren't: a key missing from one bundle renders through `fallbackLng` in
 * English, which reads as untranslated copy rather than as a bug. Adding a third language made the
 * hand pass an unreasonable ask.
 *
 * It runs against `resources` rather than a list of its own, so a fourth language is covered the
 * moment it is registered — `adding-a-language.md` stays a six-place procedure, not a seven-place one.
 */

type Bundle = Record<string, unknown>;

function keyPaths(bundle: Bundle, prefix = ''): string[] {
  return Object.entries(bundle).flatMap(([key, value]) =>
    value !== null && typeof value === 'object' ? keyPaths(value as Bundle, `${prefix}${key}.`) : [`${prefix}${key}`],
  );
}

/**
 * The plural categories a language actually has, per CLDR. `en` and `pt` have two, `ja` has one — so
 * "the same keys as English" is the wrong bar for a plural key, and expanding English's bases against
 * each language's own categories is the right one.
 */
function pluralCategories(language: string): string[] {
  return new Intl.PluralRules(language).resolvedOptions().pluralCategories;
}

/**
 * English's key set, with every plural base re-expanded into `categories`.
 *
 * Two sets come out of this, because required and permitted differ. **Required** is the categories
 * English itself distinguishes, narrowed to the ones the language has — `one`/`other` for `pt`, just
 * `other` for `ja`. **Permitted** is everything CLDR gives the language: `pt` also has `many`, which
 * only applies from a million upward, and no Kettle count is a set or rep total that large. Demanding
 * `_many` would add fifteen keys for a number the app cannot produce; permitting it means a bundle
 * that wants the extra precision can have it without failing here.
 */
function expandPlurals(categories: string[]): Set<string> {
  return new Set(
    keyPaths(resources.en.translation).flatMap((path) => {
      const base = path.replace(/_(?:zero|one|two|few|many|other)$/, '');
      return base === path ? [path] : categories.map((category) => `${base}_${category}`);
    }),
  );
}

const languages = Object.keys(resources) as (keyof typeof resources)[];

describe.each(languages)('the %s bundle', (language) => {
  const categories = pluralCategories(language);
  const actual = new Set(keyPaths(resources[language].translation));
  const required = expandPlurals(pluralCategories('en').filter((category) => categories.includes(category)));
  const permitted = expandPlurals(categories);

  it('carries every key English does', () => {
    expect([...required].filter((key) => !actual.has(key))).toEqual([]);
  });

  it('carries nothing English does not', () => {
    // Two directions at once. A key dropped from `en.json` leaves translations behind that nothing
    // renders and everyone goes on maintaining; and a plural form the language has no rule for — a
    // `_one` in `ja` — is dead the day it is written, since `count` there always resolves to `other`.
    expect([...actual].filter((key) => !permitted.has(key))).toEqual([]);
  });
});

/**
 * The Japanese wrinkle, pinned rather than described. Japanese has a single plural category, so every
 * `count` key resolves to `_other` — a bundle written by analogy with `en`/`pt` carries `_one` keys
 * that never render, and nothing above catches that on its own once `_one` is simply absent.
 */
describe('Japanese pluralisation', () => {
  it('has one plural category, so one form covers every count', async () => {
    expect(pluralCategories('ja')).toEqual(['other']);

    await changeLanguage('ja');
    expect(t('format.set', { count: 1 })).toBe('1セット');
    expect(t('format.set', { count: 3 })).toBe('3セット');
  });
});
