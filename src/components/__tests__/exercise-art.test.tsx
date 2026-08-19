import { screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips
// `import/no-named-as-default-member`, and that accepted-warning pile is meant to stop growing.
import { changeLanguage } from 'i18next';

import { EXERCISE_ART, ExerciseArt, NO_ART } from '@/components/exercise-art';
import en from '@/i18n/locales/en.json';
import pt from '@/i18n/locales/pt.json';
import { seedLibrary } from '@/storage/seed-library';
import { renderScreen } from '@/test-support/render';

/**
 * The drawings themselves aren't testable — whether a figure reads as a plank rather than a push-up
 * is an eyeball question, answered in the running app. What is testable is everything around them:
 * that the set covers the content it was drawn for, that every drawing can announce itself in both
 * languages, and that an id with no drawing degrades to nothing rather than to a crash.
 */

describe('coverage of the seed', () => {
  it('draws every seeded exercise, or says out loud that it does not', () => {
    // The failure this pins is silent by nature: a new seeded exercise simply renders without art,
    // which looks like a deliberate omission from every angle except this one. Landing on NO_ART is
    // then a decision someone made, rather than a step someone forgot.
    const undrawn = seedLibrary.exercises
      .map((exercise) => exercise.id)
      .filter((id) => !(id in EXERCISE_ART) && !NO_ART.includes(id));

    expect(undrawn).toEqual([]);
  });

  it('keeps NO_ART honest', () => {
    // An id here that no longer exists, or that has since been drawn, would quietly buy the test
    // above a pass it hasn't earned.
    const seeded = new Set(seedLibrary.exercises.map((exercise) => exercise.id));
    expect(NO_ART.filter((id) => !seeded.has(id) || id in EXERCISE_ART)).toEqual([]);
  });
});

describe('descriptions', () => {
  const ids = Object.keys(EXERCISE_ART);

  it.each(ids)('describes %s in both bundles', (id) => {
    // Nothing fails when a key is missing from one bundle — i18next's fallbackLng renders it in
    // English and the screen looks fine — so parity is only ever caught here.
    expect(typeof (en.exerciseArt as Record<string, string>)[id]).toBe('string');
    expect(typeof (pt.exerciseArt as Record<string, string>)[id]).toBe('string');
  });

  it('adds no description for a drawing that does not exist', () => {
    expect(Object.keys(en.exerciseArt).filter((id) => !(id in EXERCISE_ART))).toEqual([]);
    expect(Object.keys(pt.exerciseArt).filter((id) => !(id in EXERCISE_ART))).toEqual([]);
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

  it('renders nothing for an exercise with no drawing', async () => {
    // The common case in a library the user built themselves, and the whole of the failure mode:
    // a map lookup returning undefined.
    await renderScreen(<ExerciseArt exerciseId="something-the-user-invented" />);

    expect(screen.toJSON()).toBeNull();
  });
});
