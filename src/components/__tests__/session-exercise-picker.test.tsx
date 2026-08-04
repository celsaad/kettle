import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import { SessionExercisePicker } from '@/components/session-exercise-picker';
import type { Exercise } from '@/domain/types';
import { renderScreen } from '@/test-support/render';

/**
 * The substitute picker. The runner decides *which* exercises are candidates (same type, minus the
 * current one) — this owns that they are reachable, named and dismissable.
 */
const candidates: Exercise[] = [
  { id: 'dips', name: 'Dips', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 60 } },
  { id: 'push-press', name: 'Push Press', type: 'reps', config: { sets: 3, targetRepsMin: 5, restSec: 120 } },
];

const props = { replacing: 'Bench Press', candidates, onCancel: jest.fn(), onSelect: jest.fn() };

it('lists every candidate by name', async () => {
  await renderScreen(<SessionExercisePicker {...props} />);

  expect(screen.getByText('Dips')).toBeTruthy();
  expect(screen.getByText('Push Press')).toBeTruthy();
});

// The user's own exercise name, interpolated into the line rather than translated.
it('names the exercise being replaced', async () => {
  await renderScreen(<SessionExercisePicker {...props} />);

  expect(screen.getByText('For the rest of Bench Press')).toBeTruthy();
});

it('reports the chosen exercise by id', async () => {
  const onSelect = jest.fn();
  await renderScreen(<SessionExercisePicker {...props} onSelect={onSelect} />);

  await fireEvent.press(screen.getByText('Push Press'));

  expect(onSelect).toHaveBeenCalledWith('push-press');
});

it('backs out through the backdrop and through Cancel', async () => {
  const onCancel = jest.fn();
  await renderScreen(<SessionExercisePicker {...props} onCancel={onCancel} />);

  await fireEvent.press(screen.getByLabelText('Keep the current exercise'));
  await fireEvent.press(screen.getByText('Cancel'));

  expect(onCancel).toHaveBeenCalledTimes(2);
});

/**
 * Driven in pt, since an English assertion cannot tell `t('session.swap.title')` from the literal it
 * returns — and the runner is where hardcoded strings have shipped before.
 */
it('renders its own copy in the active locale, and the exercise names verbatim', async () => {
  await changeLanguage('pt');

  await renderScreen(<SessionExercisePicker {...props} />);

  expect(screen.getByText('TROCAR EXERCÍCIO')).toBeTruthy();
  expect(screen.getByText('No lugar do que resta de Bench Press')).toBeTruthy();
  expect(screen.getByText('Dips')).toBeTruthy();
});

/**
 * Search. Only appears above a threshold: swapping filters to one exercise type and leaves a handful,
 * where a field would be noise; adding to an ad-hoc session offers the whole library, where scrolling
 * it mid-workout is worse than a keyboard.
 */
describe('searching a long list', () => {
  const many: Exercise[] = Array.from({ length: 12 }, (_, index) => ({
    id: `ex-${index}`,
    name: index === 0 ? 'Dumbbell Floor Press' : index === 1 ? 'Agachamento Búlgaro' : `Exercise ${index}`,
    type: 'reps' as const,
    config: { sets: 3, targetRepsMin: 8, restSec: 60 },
  }));

  it('stays out of the way for a short list', async () => {
    await renderScreen(<SessionExercisePicker {...props} />);

    expect(screen.queryByLabelText('Search exercises')).toBeNull();
  });

  it('appears once the list is long enough to be a problem', async () => {
    await renderScreen(<SessionExercisePicker {...props} candidates={many} />);

    expect(screen.getByLabelText('Search exercises')).toBeTruthy();
  });

  it('matches anywhere in the name, not just the start', async () => {
    await renderScreen(<SessionExercisePicker {...props} candidates={many} />);

    await fireEvent.changeText(screen.getByLabelText('Search exercises'), 'press');

    expect(screen.getByText('Dumbbell Floor Press')).toBeTruthy();
    expect(screen.queryByText('Exercise 5')).toBeNull();
  });

  // Names come from the user's own YAML, so "agach" has to find "Agachamento" without the accent.
  it('ignores case and accents', async () => {
    await renderScreen(<SessionExercisePicker {...props} candidates={many} />);

    await fireEvent.changeText(screen.getByLabelText('Search exercises'), 'bulgaro');

    expect(screen.getByText('Agachamento Búlgaro')).toBeTruthy();
  });

  it('says so when nothing matches, rather than showing an empty sheet', async () => {
    await renderScreen(<SessionExercisePicker {...props} candidates={many} />);

    await fireEvent.changeText(screen.getByLabelText('Search exercises'), 'zzz');

    expect(screen.getByText('No exercise matches "zzz"')).toBeTruthy();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    await renderScreen(<SessionExercisePicker {...props} candidates={many} />);

    expect(screen.getByLabelText('Buscar exercícios')).toBeTruthy();
  });
});
