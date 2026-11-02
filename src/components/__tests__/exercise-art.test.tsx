import { screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips
// `import/no-named-as-default-member`, and that accepted-warning pile is meant to stop growing.
import { changeLanguage } from 'i18next';

import { ARTWORK, ExerciseArt } from '@/components/exercise-art';
import art from '@/content/art.json';
import en from '@/i18n/locales/en.json';
import ja from '@/i18n/locales/ja.json';
import pt from '@/i18n/locales/pt.json';
import { contentPacks } from '@/storage/content-packs';
import { seedLibrary } from '@/storage/seed-library';
import { contentFiles } from '@/test-support/content-files';
import { renderScreen } from '@/test-support/render';

const drawings: Record<string, string> = art.drawings;
const NO_ART: readonly string[] = art.noArt;

/**
 * The drawings themselves aren't testable — whether a figure reads as a plank rather than a push-up
 * is an eyeball question, answered in the running app. What is testable is everything around them:
 * that the set covers the content it was drawn for, that every drawing can announce itself in both
 * languages, that every file sticks to the one colour the renderer can change, and that an id with no
 * drawing degrades to nothing rather than to a crash.
 */

/**
 * Everything the app itself ships as content — the seed a first launch lands on, and the packs the
 * import screen offers. Both are ours, both render in the same lists, and an exercise with no drawing
 * beside neighbours that have one is the same defect whichever library it came out of.
 */
const shippedExercises = [...seedLibrary().exercises, ...contentPacks.flatMap((pack) => pack.library.exercises)];

describe('coverage of the shipped content', () => {
  it('draws every shipped exercise, or says out loud that it does not', () => {
    // The failure this pins is silent by nature: a new shipped exercise simply renders without art,
    // which looks like a deliberate omission from every angle except this one. Landing on NO_ART is
    // then a decision someone made, rather than a step someone forgot.
    const undrawn = shippedExercises
      .map((exercise) => exercise.id)
      .filter((id) => !(id in drawings) && !NO_ART.includes(id));

    expect(undrawn).toEqual([]);
  });

  it('keeps NO_ART honest', () => {
    // An id here that no longer exists, or that has since been drawn, would quietly buy the test
    // above a pass it hasn't earned.
    const shipped = new Set(shippedExercises.map((exercise) => exercise.id));
    expect(NO_ART.filter((id) => !shipped.has(id) || id in drawings)).toEqual([]);
  });

  it('draws nothing for an id nothing ships', () => {
    // The other direction, and the one a rename breaks: a drawing keyed to an id that no longer
    // exists is dead weight nobody would notice, since a map lookup that misses renders nothing.
    const shipped = new Set(shippedExercises.map((exercise) => exercise.id));
    expect(Object.keys(drawings).filter((id) => !shipped.has(id))).toEqual([]);
  });
});

describe('the files', () => {
  const files = contentFiles('art')
    .filter((file) => file.endsWith('.svg'))
    .map((file) => file.replace(/\.svg$/, ''));

  it('bundles every drawing on disk, and nothing that is not', () => {
    // RN reads only what a static import names, while native reads the directory. A file added
    // without its import draws on Android and nowhere else.
    expect(Object.keys(ARTWORK).toSorted()).toEqual(files);
  });

  it('points every id at a drawing that exists, and leaves none unused', () => {
    // :art fails the Android build on the same two mismatches; this is the same check on RN's side.
    const used = new Set(Object.values(drawings));
    expect([files.filter((file) => !used.has(file)), [...used].filter((name) => !files.includes(name))]).toEqual([[], []]);
  });

  it.each(files)('colours %s only through the root group', (file) => {
    // The root <g>'s `currentColor` is the one colour the renderer can change, through RN's `color`,
    // Android's tint and iOS's template mode. Any other stroke or fill is fixed, so it vanishes on
    // one of the three surfaces a drawing lands on: light, dark, or the always-dark runner.
    const markup = ARTWORK[file].replace(/<!--[\s\S]*?-->/g, '');
    const coloured = [...markup.matchAll(/\b(stroke|fill|color|style)="([^"]*)"/g)]
      .map(([attribute]) => attribute)
      .filter((attribute) => attribute !== 'fill="none"');

    expect(coloured).toEqual(['stroke="currentColor"']);
    expect(markup).toMatch(/<g stroke="currentColor"/);
  });
});

describe('descriptions', () => {
  const ids = Object.keys(drawings);
  // Every shipped bundle, not just the two the app started with: a drawing that announces itself in
  // English to a Japanese reader is the same defect as one that announces itself in English to a
  // Portuguese one, and adding a language is the moment that stops being hypothetical.
  const bundles = Object.entries({ en, pt, ja });

  it.each(ids)('describes %s in every bundle', (id) => {
    // Nothing fails when a key is missing from one bundle — i18next's fallbackLng renders it in
    // English and the screen looks fine — so parity is only ever caught here.
    for (const [language, bundle] of bundles) {
      expect([language, typeof (bundle.exerciseArt as Record<string, string>)[id]]).toEqual([language, 'string']);
    }
  });

  it('adds no description for a drawing that does not exist', () => {
    for (const [language, bundle] of bundles) {
      expect([language, Object.keys(bundle.exerciseArt).filter((id) => !(id in drawings))]).toEqual([language, []]);
    }
  });
});

describe('rendering', () => {
  it('announces the movement in the active language', async () => {
    // Driven in pt on purpose: an English assertion here cannot tell a translated string from a
    // hardcoded one, because `t('exerciseArt.plank')` and the literal it returns render identically.
    await changeLanguage('pt');
    await renderScreen(<ExerciseArt exerciseId="plank" />);

    expect(screen.getByLabelText(pt.exerciseArt.plank)).toBeTruthy();
  });

  it('draws the markup in the colour it is given', async () => {
    // The runner passes its own colour whatever the scheme; the drawing has to take it.
    await renderScreen(<ExerciseArt exerciseId="plank" color="#123456" />);

    expect(JSON.stringify(screen.toJSON())).toContain('#123456');
  });

  it('renders nothing for an exercise with no drawing', async () => {
    // The common case in a library the user built themselves, and the whole of the failure mode:
    // a map lookup returning undefined.
    await renderScreen(<ExerciseArt exerciseId="something-the-user-invented" />);

    expect(screen.toJSON()).toBeNull();
  });
});
