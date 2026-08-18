/**
 * The cases that deliberately cross more than one selector module, which is why they aren't filed
 * under any single one: each asserts that the *same* logged session reads consistently through the
 * stat aggregation, the list views and the entry descriptor. Splitting them per module would lose the
 * only thing they check — that the paths agree.
 */
import { changeLanguage } from 'i18next';

import type { Library, SessionEntry } from '@/domain/types';
import { historyStats } from '@/state/selectors/history-stats';
import { exerciseHistory, historySessionsView } from '@/state/selectors/history-views';
import { sessionEntryResult } from '@/state/selectors/session-summary';
import { aSession } from '@/test-support/sessions';

/**
 * A session with no workout behind it — what an ad-hoc session writes. This layer has been ready for
 * it since `workoutNameFor` learned to return null, which is why #55 needed no changes here; these
 * pin that, so a later "simplification" of the null path fails loudly instead of crashing History.
 */
describe('a session with no workout', () => {
  const library: Library = { version: 1, exercises: [], workouts: [], programs: [] };
  const adHoc = aSession({
    startedAt: '2026-07-24T09:00:00.000Z',
    workout: null,
    endedAt: '2026-07-24T09:45:00.000Z',
    entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 60, restTakenSec: 120 }] }],
  });

  it('renders in History under the translated stand-in', () => {
    expect(historySessionsView([adHoc], library)[0].workoutName).toBe('Ad-hoc session');
  });

  it('counts towards the stats like any other session', () => {
    expect(historyStats([adHoc])).toEqual({ sessions: 1, hours: 0, minutes: 45, sets: 1 });
  });

  // The stand-in is a translated string, not a hardcoded English one — `formatSessionName` owns it.
  it('translates the stand-in', async () => {
    await changeLanguage('pt');
    expect(historySessionsView([adHoc], library)[0].workoutName).toBe('Sessão avulsa');
  });
});

/**
 * What the session editor (#56) is for, from the other end: a correction has to move every number
 * derived from the log, not just the row the user typed into. Each of these reads the *edited* entry
 * through a different derived path — the History summary, the volume chart, the stat tiles — because
 * an edit that only showed up in one of them would look like the correction hadn't taken.
 */
describe('a corrected entry, downstream', () => {
  const library: Library = {
    version: 1,
    exercises: [{ id: 'back-squat', name: 'Back Squat', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 90 } }],
    workouts: [{ id: 'w', name: 'Leg day', blocks: [] }],
    programs: [],
  };
  const logged = (sets: { reps: number; weightKg?: number }[]): SessionEntry => ({
    exercise: 'back-squat',
    type: 'reps',
    sets: sets.map((set) => ({ ...set, restTakenSec: 90 })),
  });

  const before = aSession({ startedAt: '2026-07-29T09:00:00.000Z', entries: [logged([{ reps: 8 }, { reps: 8 }])] });
  // The same session with set 2 corrected from 8 reps to 5, which is what `editEntry` writes.
  const after = { ...before, entries: [logged([{ reps: 8 }, { reps: 5 }])] };

  it('changes the summary History renders', () => {
    expect(historySessionsView([before], library)[0].entries[0].summary).toBe('8 · 8 reps');
    expect(historySessionsView([after], library)[0].entries[0].summary).toBe('8 · 5 reps');
  });

  it('changes the entry result the summary is built from', () => {
    expect(sessionEntryResult(after.entries[0])).toEqual({ kind: 'reps', reps: [8, 5] });
  });

  it('changes the volume the chart plots', () => {
    expect(exerciseHistory([before], 'back-squat')[0].volume).toBe(16);
    expect(exerciseHistory([after], 'back-squat')[0].volume).toBe(13);
  });

  // Removing a set has to move the set count too, or the tiles keep counting work that was taken out.
  it('changes the set count in the stat tiles when a set is removed', () => {
    const removed = { ...before, entries: [logged([{ reps: 8 }])] };
    expect(historyStats([before]).sets).toBe(2);
    expect(historyStats([removed]).sets).toBe(1);
  });

  // A loaded entry's volume is reps × weight rather than reps, so correcting the load moves it too.
  it('changes the volume when the load is what was wrong', () => {
    const light = aSession({ startedAt: '2026-07-29T09:00:00.000Z', entries: [logged([{ reps: 5, weightKg: 60 }])] });
    const heavy = { ...light, entries: [logged([{ reps: 5, weightKg: 100 }])] };

    expect(exerciseHistory([light], 'back-squat')[0].volume).toBe(300);
    expect(exerciseHistory([heavy], 'back-squat')[0].volume).toBe(500);
  });
});
