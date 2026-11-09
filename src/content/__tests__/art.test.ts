import art from '@/content/art.json';
import en from '@/i18n/locales/en.json';
import ja from '@/i18n/locales/ja.json';
import pt from '@/i18n/locales/pt.json';
import { contentPacks } from '@/storage/content-packs';
import { seedLibrary } from '@/storage/seed-library';
import { contentFiles } from '@/test-support/content-files';

// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };

const { readFileSync } = require('node:fs');

const drawings: Record<string, string> = art.drawings;
const NO_ART: readonly string[] = art.noArt;

/**
 * The drawings' data, which native's `:art` turns into VectorDrawables and its exercise rows look up by
 * id. Whether a figure reads as a plank rather than a push-up is an eyeball question, answered in the
 * running app; what is checked here is everything around the drawings: that the set covers the content
 * it was drawn for, that every drawing can announce itself in every language, and that every file
 * sticks to the one colour a tint can change. These outlived RN's `ExerciseArt`, the runner's last
 * reader (#308), because they were never about that component.
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

  it('points every id at a drawing that exists, and leaves none unused', () => {
    // :art fails the Android build on the same two mismatches; this says which one in a line.
    const used = new Set(Object.values(drawings));
    expect([files.filter((file) => !used.has(file)), [...used].filter((name) => !files.includes(name))]).toEqual([[], []]);
  });

  it.each(files)("draws %s in the root group's one colour and stroke", (file) => {
    // The root <g>'s `currentColor` is the one colour a renderer can change, through Android's tint
    // and iOS's template mode. Any other stroke or fill is fixed, so it vanishes on one of the three
    // surfaces a drawing lands on: light, dark, or the always-dark runner.
    const markup = readFileSync(`${__dirname}/../art/${file}.svg`, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    const coloured = [...markup.matchAll(/\b(stroke|fill|color|style)=(["'])(.*?)\2/g)]
      .map(([, name, , value]) => `${name}="${value}"`)
      .filter((attribute) => attribute !== 'fill="none"');

    expect(coloured).toEqual(['stroke="currentColor"']);
    // The rest of the root group is the set's style rather than its colour: one weight and round ends
    // are most of what makes separately drawn figures look drawn by one hand. :art accepts any cap or
    // join Android can draw, so this is the only place that holds the set to these.
    expect(markup).toContain('<g stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">');
  });
});

describe('descriptions', () => {
  const ids = Object.keys(drawings);
  // Every shipped bundle: a drawing that announces itself in English to a Japanese reader is the same
  // defect as one that announces itself in English to a Portuguese one.
  const bundles = Object.entries({ en, pt, ja });

  it.each(ids)('describes %s in every bundle', (id) => {
    // Nothing fails when a key is missing from one bundle — the fallback renders it in English and the
    // screen looks fine — so parity is only ever caught here.
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
