import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips
// `import/no-named-as-default-member`, and that accepted-warning pile is meant to stop growing.
import { changeLanguage } from 'i18next';
import { Alert } from 'react-native';

import SessionEditorScreen from '@/app/session-editor';
import type { Session, SessionEntry } from '@/domain/types';
import type { UnitSystem } from '@/domain/units';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { pressAlertButton, renderScreen } from '@/test-support/render';

/**
 * The editor's wiring, not its arithmetic — `buildEntry` and `validateEntryForm` have their own unit
 * tests in `domain/__tests__/session-entry-form.test.ts` and aren't re-tested here. What's covered is
 * what only exists once the screen is mounted: that a correction reaches the store at the right index,
 * that removing a set is confirmed and reversible, and the two states where editing is refused.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const backSquat = anExercise({ id: 'back-squat', name: 'Back Squat' });
const plank = anExercise({ id: 'plank', name: 'Plank', type: 'timed_hold' });

const squatEntry: SessionEntry = {
  exercise: 'back-squat',
  type: 'reps',
  sets: [
    { reps: 8, weightKg: 60, rpe: 8, restTakenSec: 90 },
    { reps: 6, weightKg: 60, rpe: 9, restTakenSec: 120 },
  ],
};
const restEntry: SessionEntry = { exercise: 'rest', type: 'rest', restTakenSec: 120 };
const plankEntry: SessionEntry = { exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 45, restTakenSec: 60 }] };

function aSession(overrides: Partial<Session> = {}): Session {
  return {
    version: 1,
    id: 'session-1',
    workout: 'leg-day',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt: '2026-07-29T09:00:00.000Z',
    endedAt: '2026-07-29T10:00:00.000Z',
    entries: [squatEntry, restEntry, plankEntry],
    ...overrides,
  };
}

function setUnitSystem(unitSystem: UnitSystem) {
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, unitSystem }, status: 'ready' }));
}

const editEntry = jest.fn();
const removeEntry = jest.fn();

beforeEach(() => {
  setSearchParams({ id: 'session-1' });
  setUnitSystem('metric');
  useLibraryStore.setState({
    library: aLibrary({ exercises: [backSquat, plank], workouts: [aWorkout({ id: 'leg-day', name: 'Leg day' })] }),
    status: 'ready',
  });
  useSessionHistoryStore.setState({ status: 'ready', sessions: [aSession()], errors: [], editEntry, removeEntry });
  editEntry.mockReset();
  removeEntry.mockReset();
});

/** Reps/load/RPE render in `ENTRY_FIELDS.reps` order, so they're addressed by their visible label. */
function fieldFor(setNumber: number, label: string) {
  return screen.getByLabelText(`Set ${setNumber} · ${label}`);
}

describe('correcting a logged set', () => {
  it('writes the edited value back to the entry it came from', async () => {
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(fieldFor(2, 'Reps'), '7');
    await fireEvent.press(screen.getByText('Save'));

    expect(editEntry).toHaveBeenCalledWith('session-1', 0, {
      exercise: 'back-squat',
      type: 'reps',
      sets: [
        { reps: 8, weightKg: 60, rpe: 8, restTakenSec: 90 },
        { reps: 7, weightKg: 60, rpe: 9, restTakenSec: 120 },
      ],
    });
  });

  /**
   * The index the editor writes with is into `session.entries`, where the plank sits at 2 — History
   * renders it at 1, because `historySessionsView` filters the rest entry out. Building from the
   * filtered list instead would send this correction to the rest entry.
   */
  it('uses the raw entry index, not the rest-filtered position History shows', async () => {
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(screen.getByLabelText('Set 1 · Hold (sec)'), '60');
    await fireEvent.press(screen.getByText('Save'));

    expect(editEntry).toHaveBeenCalledWith('session-1', 2, {
      exercise: 'plank',
      type: 'timed_hold',
      sets: [{ holdSec: 60, restTakenSec: 60 }],
    });
  });

  it('leaves untouched entries alone rather than rewriting them all', async () => {
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(fieldFor(1, 'Reps'), '10');
    await fireEvent.press(screen.getByText('Save'));

    // Both entries are still written — the screen doesn't diff — but with their own values, and the
    // plank's write must not carry the squat's edit.
    const plankWrite = editEntry.mock.calls.find((call) => call[1] === 2);
    expect(plankWrite?.[2]).toEqual(plankEntry);
  });

  it('blocks the write and says why when a value is invalid', async () => {
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(fieldFor(1, 'RPE'), '88');
    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('RPE can be at most 10.')).toBeTruthy();
    expect(editEntry).not.toHaveBeenCalled();
    expect(router.back).not.toHaveBeenCalled();
  });

  it('converts a load typed in pounds back to kilograms', async () => {
    setUnitSystem('imperial');
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(fieldFor(1, 'Load (lb)'), '135');
    await fireEvent.press(screen.getByText('Save'));

    const written = editEntry.mock.calls.find((call) => call[1] === 0)?.[2];
    expect(written.sets[0].weightKg).toBe(61.23);
    // The set the user didn't touch keeps its stored kilograms exactly, rather than the 60.01 a
    // display round trip through pounds would produce.
    expect(written.sets[1].weightKg).toBe(60);
  });

  it('closes without writing anything when cancelled', async () => {
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(fieldFor(1, 'Reps'), '99');
    await fireEvent.press(screen.getByText('Cancel'));

    expect(editEntry).not.toHaveBeenCalled();
    expect(router.back).toHaveBeenCalled();
  });
});

describe('removing', () => {
  it('drops one set without confirming, since the entry survives it', async () => {
    await renderScreen(<SessionEditorScreen />);
    const alert = jest.spyOn(Alert, 'alert');

    await fireEvent.press(screen.getByLabelText('Remove set · Set 1'));

    expect(alert).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByText('Save'));
    expect(editEntry).toHaveBeenCalledWith('session-1', 0, {
      exercise: 'back-squat',
      type: 'reps',
      sets: [{ reps: 6, weightKg: 60, rpe: 9, restTakenSec: 120 }],
    });
  });

  // Losing the last set takes the exercise out of the session, which is a bigger thing than dropping a
  // set and the one step the user can't infer from the button they pressed.
  it('confirms before the last set takes the exercise with it', async () => {
    await renderScreen(<SessionEditorScreen />);
    const alert = jest.spyOn(Alert, 'alert');

    await fireEvent.press(screen.getByLabelText('Remove exercise'));
    expect(alert).toHaveBeenCalled();
    await pressAlertButton(alert, 'destructive');
    await fireEvent.press(screen.getByText('Save'));

    expect(removeEntry).toHaveBeenCalledWith('session-1', 2);
  });

  // Dismissing the alert rather than pressing its destructive button: the cancel button carries no
  // handler (nothing to undo), so *not* running one is exactly what cancelling does.
  it('leaves the entry alone unless the confirm is accepted', async () => {
    await renderScreen(<SessionEditorScreen />);
    const alert = jest.spyOn(Alert, 'alert');

    await fireEvent.press(screen.getByLabelText('Remove exercise'));
    await fireEvent.press(screen.getByText('Save'));

    expect(alert).toHaveBeenCalled();
    expect(removeEntry).not.toHaveBeenCalled();
    expect(editEntry).toHaveBeenCalledWith('session-1', 2, plankEntry);
  });
});

describe('sessions that can’t be edited', () => {
  /**
   * The runner owns an in-flight session's file and writes through its own copy, so an edit made here
   * would be overwritten by the next set logged. The store refuses it too — this is what stops the
   * screen from taking input it would then silently drop.
   */
  it('refuses one that is still running, and offers no fields', async () => {
    useSessionHistoryStore.setState({ sessions: [aSession({ endedAt: null })] });

    await renderScreen(<SessionEditorScreen />);

    expect(screen.getByText('This session is still running')).toBeTruthy();
    expect(screen.queryByText('Save')).toBeNull();
  });

  it('says so when the session is gone rather than rendering an empty form', async () => {
    useSessionHistoryStore.setState({ sessions: [] });

    await renderScreen(<SessionEditorScreen />);

    expect(screen.getByText('Session not found')).toBeTruthy();
    expect(screen.queryByText('Save')).toBeNull();
  });

  it('says so when nothing in the session has editable fields', async () => {
    useSessionHistoryStore.setState({
      sessions: [aSession({ entries: [restEntry, { exercise: 'clean', type: 'emom', minutes: [{ reps: 3 }] }] })],
    });

    await renderScreen(<SessionEditorScreen />);

    expect(screen.getByText('Nothing to edit')).toBeTruthy();
  });
});

/**
 * An EMOM's per-minute rep list is out of scope, but dropping it from the editor would read as if the
 * log had lost it — so it is listed, with its result, and said to be uneditable.
 */
describe('an entry the editor can’t rewrite', () => {
  it('is shown read-only rather than hidden', async () => {
    useSessionHistoryStore.setState({
      sessions: [
        aSession({ entries: [squatEntry, { exercise: 'clean', type: 'emom', minutes: [{ reps: 3 }, { reps: 3 }] }] }),
      ],
    });

    await renderScreen(<SessionEditorScreen />);

    expect(screen.getByText("Minute-by-minute logs aren't editable here.")).toBeTruthy();
    // Still saveable: the squat above it is editable, and the EMOM is simply not written.
    expect(screen.getByText('Save')).toBeTruthy();
  });
});

/**
 * An English-locale assertion cannot catch a hardcoded English string — `t('x.y')` and the literal it
 * returns render identically. Three screens have shipped with hardcoded strings for exactly that
 * reason, so the editor gets driven in Portuguese.
 */
describe('in Portuguese', () => {
  // No reset afterwards: `jest.setup-after-env.js` does it in `beforeEach` on purpose, because doing
  // it in an afterEach re-renders a tree RNTL hasn't unmounted yet and reports it as an unwrapped
  // update. Switching back here would reintroduce exactly that.
  it('translates its chrome and keeps the user’s own exercise names verbatim', async () => {
    await changeLanguage('pt');

    await renderScreen(<SessionEditorScreen />);

    expect(screen.getByText('Editar sessão')).toBeTruthy();
    expect(screen.getByText('Salvar')).toBeTruthy();
    expect(screen.getByLabelText('Série 1 · Reps')).toBeTruthy();
    // User data is never translated.
    expect(screen.getByText('Back Squat')).toBeTruthy();
  });

  it('translates a validation failure', async () => {
    await changeLanguage('pt');
    await renderScreen(<SessionEditorScreen />);

    await fireEvent.changeText(screen.getByLabelText('Série 1 · RPE'), '88');
    await fireEvent.press(screen.getByText('Salvar'));

    expect(screen.getByText('RPE pode ser no máximo 10.')).toBeTruthy();
  });
});
