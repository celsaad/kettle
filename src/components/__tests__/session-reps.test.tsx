import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import { SessionReps } from '@/components/session-reps';
import { usePreferencesStore } from '@/state/preferences-store';
import type { PreviousSet } from '@/state/selectors/records';
import { renderScreen } from '@/test-support/render';

/**
 * The "last time" line and the adopt control on it. `use-session-runner.test.tsx` owns *what* the
 * previous set is and when the marker fires; this owns how the row reads, which is where the unit
 * conversion and the translation live.
 */
const props = {
  exerciseName: 'Bench Press',
  setIndex: 1,
  setTotal: 3,
  targetReps: 5,
  reps: 5,
  onChangeReps: jest.fn(),
  rpe: 8,
  onChangeRpe: jest.fn(),
  weightKg: 60,
  onChangeWeightKg: jest.fn(),
  next: null,
  restFollows: true,
  onPrev: jest.fn(),
  onLogSet: jest.fn(),
};

const loaded: PreviousSet = { kind: 'reps', reps: 8, weightKg: 60 };
const bodyweight: PreviousSet = { kind: 'reps', reps: 12 };

it('says nothing about last time on a first-ever session', async () => {
  await renderScreen(<SessionReps {...props} />);

  expect(screen.queryByText(/Last time/)).toBeNull();
});

it('shows the load and reps from last time', async () => {
  await renderScreen(<SessionReps {...props} previousSet={loaded} />);

  expect(screen.getByText('Last time · 60 kg × 8 reps')).toBeTruthy();
});

// Bodyweight sets log no weight at all, so the line carries reps and nothing else — never "0 kg".
it('shows a bodyweight set without inventing a load', async () => {
  await renderScreen(<SessionReps {...props} previousSet={bodyweight} />);

  expect(screen.getByText('Last time · 12 reps')).toBeTruthy();
});

it('converts the previous load to the display unit', async () => {
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, unitSystem: 'imperial' } }));

  await renderScreen(<SessionReps {...props} previousSet={{ kind: 'reps', reps: 8, weightKg: 100 }} />);

  expect(screen.getByText('Last time · 220.5 lb × 8 reps')).toBeTruthy();
});

it('adopts the previous load when the line is pressed', async () => {
  const onAdoptPrevious = jest.fn();
  await renderScreen(<SessionReps {...props} previousSet={loaded} onAdoptPrevious={onAdoptPrevious} />);

  await fireEvent.press(screen.getByLabelText(/Use last time/));

  expect(onAdoptPrevious).toHaveBeenCalledTimes(1);
});

// A bodyweight set has no load to take, so the line stops being a control rather than becoming one
// that does nothing when pressed.
it('offers no adopt control for a bodyweight set', async () => {
  await renderScreen(<SessionReps {...props} previousSet={bodyweight} />);

  expect(screen.getByText('Last time · 12 reps')).toBeTruthy();
  expect(screen.queryByLabelText(/Use last time/)).toBeNull();
});

it('marks a set that beats the log', async () => {
  await renderScreen(<SessionReps {...props} previousSet={loaded} beatsPersonalBest />);

  expect(screen.getByText('PR')).toBeTruthy();
});

it('leaves the row unmarked when the set beats nothing', async () => {
  await renderScreen(<SessionReps {...props} previousSet={loaded} />);

  expect(screen.queryByText('PR')).toBeNull();
});

/**
 * Driven in pt: an English-locale assertion cannot tell `t('format.previous.loaded')` from the
 * hardcoded literal it returns, and the runner is where that has bitten before.
 */
it('renders the line and the adopt label in the active locale', async () => {
  await changeLanguage('pt');

  await renderScreen(<SessionReps {...props} previousSet={loaded} />);

  expect(screen.getByText('Da última vez · 60 kg × 8 repetições')).toBeTruthy();
  expect(screen.getByLabelText(/Usar a carga da última vez/)).toBeTruthy();
  // The user's own exercise name, never translated.
  expect(screen.getByText('Bench Press')).toBeTruthy();
});

/**
 * The set-count controls. `use-session-runner.test.tsx` owns when they are *allowed*; this owns that
 * they are reachable, named, and disabled rather than missing at the floor.
 */
describe('changing the set count', () => {
  it('offers nothing when the runner says the count is fixed', async () => {
    await renderScreen(<SessionReps {...props} />);

    expect(screen.queryByLabelText(/sets/i)).toBeNull();
  });

  it('adds and drops through the controls', async () => {
    const onAddSet = jest.fn();
    const onDropSet = jest.fn();
    await renderScreen(<SessionReps {...props} canAddSet canDropSet onAddSet={onAddSet} onDropSet={onDropSet} />);

    await fireEvent.press(screen.getByLabelText('Increase sets'));
    await fireEvent.press(screen.getByLabelText('Decrease sets'));

    expect(onAddSet).toHaveBeenCalledTimes(1);
    expect(onDropSet).toHaveBeenCalledTimes(1);
  });

  // Disabled rather than gone: a control that vanishes reflows the header right where the user is
  // reading their set number.
  it('keeps the drop control in place but disabled at the floor', async () => {
    await renderScreen(<SessionReps {...props} canAddSet canDropSet={false} onDropSet={jest.fn()} />);

    const drop = screen.getByLabelText('Decrease sets');
    expect(drop).toBeTruthy();
    expect(drop.props.accessibilityState).toMatchObject({ disabled: true });
  });

  it('names the controls in the active locale', async () => {
    await changeLanguage('pt');
    await renderScreen(<SessionReps {...props} canAddSet canDropSet />);

    expect(screen.getByLabelText('Aumentar séries')).toBeTruthy();
    expect(screen.getByLabelText('Diminuir séries')).toBeTruthy();
  });
});
