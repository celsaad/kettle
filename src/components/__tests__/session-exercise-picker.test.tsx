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
