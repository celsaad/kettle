import { screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import { SessionComplete } from '@/components/session-complete';
import type { SessionRecord } from '@/state/selectors/records';
import { usePreferencesStore } from '@/state/preferences-store';
import { renderScreen } from '@/test-support/render';

/**
 * What this screen adds to the checkmark: the records the finished session set, and the estimated
 * 1RM under a loaded one. `selectors.test.ts` owns *which* sets count as records; this owns how they
 * read, which is where the unit conversion and the translation live.
 */
const heaviest: SessionRecord = {
  kind: 'heaviestSet',
  exerciseId: 'bench',
  exerciseName: 'Bench Press',
  weightKg: 100,
  reps: 5,
  oneRepMaxKg: 116.66666,
};

it('renders exactly the old screen when nothing was beaten', async () => {
  await renderScreen(<SessionComplete workoutName="Push Day" onDone={jest.fn()} />);

  expect(screen.getByText('Workout complete')).toBeTruthy();
  expect(screen.getByText('Push Day')).toBeTruthy();
  expect(screen.queryByText('PR')).toBeNull();
});

it('names the record and the exercise that set it', async () => {
  await renderScreen(<SessionComplete workoutName="Push Day" records={[heaviest]} onDone={jest.fn()} />);

  expect(screen.getByText('PR')).toBeTruthy();
  // The user's own exercise name, rendered verbatim.
  expect(screen.getByText('Bench Press')).toBeTruthy();
  expect(screen.getByText('Heaviest ever · 100 kg × 5 reps')).toBeTruthy();
});

/**
 * The formula is named on screen — an unattributed 1RM is a number people argue with — but as a note
 * under the list rather than in parentheses after the value, where it competed with the number and
 * told a lifter mid-celebration nothing they wanted at that moment.
 */
it('shows the estimate plainly and attributes it once, below the records', async () => {
  await renderScreen(<SessionComplete workoutName="Push Day" records={[heaviest]} onDone={jest.fn()} />);

  expect(screen.getByText('Est. 1RM 116.67 kg')).toBeTruthy();
  expect(screen.getByText('1RM estimated from your best set with the Epley formula.')).toBeTruthy();
});

// Two loaded records, one note. Repeating the attribution per card is what made it noise.
it('attributes the estimate once however many records there are', async () => {
  const second: SessionRecord = { ...heaviest, exerciseId: 'squat', exerciseName: 'Back Squat', weightKg: 140 };
  await renderScreen(<SessionComplete workoutName="Push Day" records={[heaviest, second]} onDone={jest.fn()} />);

  expect(screen.getAllByText(/Est\. 1RM/)).toHaveLength(2);
  expect(screen.getAllByText(/Epley/)).toHaveLength(1);
});

// Weights are stored in kilograms and converted only at the render boundary, so this screen has to
// do the conversion itself — a pound user seeing 100 kg would be the tell that it didn't.
it('converts both weights to the display unit', async () => {
  usePreferencesStore.setState((state) => ({
    preferences: { ...state.preferences, unitSystem: 'imperial' },
  }));

  await renderScreen(<SessionComplete workoutName="Push Day" records={[heaviest]} onDone={jest.fn()} />);

  expect(screen.getByText('Heaviest ever · 220.5 lb × 5 reps')).toBeTruthy();
  expect(screen.getByText('Est. 1RM 257.2 lb')).toBeTruthy();
});

// Including the note, which would otherwise be explaining a number that isn't there.
it('has no 1RM line or attribution for a bodyweight record', async () => {
  const record: SessionRecord = { kind: 'mostReps', exerciseId: 'pullups', exerciseName: 'Pull-ups', reps: 14 };
  await renderScreen(<SessionComplete workoutName="Pull Day" records={[record]} onDone={jest.fn()} />);

  expect(screen.getByText('Most reps ever · 14 reps')).toBeTruthy();
  expect(screen.queryByText(/1RM/)).toBeNull();
  expect(screen.queryByText(/Epley/)).toBeNull();
});

/**
 * Driven in pt, since an English-locale assertion cannot tell `t('format.record.heaviestSet')` from
 * the hardcoded literal it returns — only a locale switch distinguishes them, and three screens have
 * shipped with hardcoded English for exactly that reason.
 */
it('renders the record lines in the active locale', async () => {
  await changeLanguage('pt');

  await renderScreen(<SessionComplete workoutName="Push Day" records={[heaviest]} onDone={jest.fn()} />);

  expect(screen.getByText('Treino concluído')).toBeTruthy();
  expect(screen.getByText('RECORDE')).toBeTruthy();
  expect(screen.getByText('Mais pesado até hoje · 100 kg × 5 repetições')).toBeTruthy();
  expect(screen.getByText('1RM estimado 116.67 kg')).toBeTruthy();
  expect(screen.getByText('1RM estimado a partir da sua melhor série, pela fórmula de Epley.')).toBeTruthy();
  // Never translated, in any locale.
  expect(screen.getByText('Bench Press')).toBeTruthy();
});
