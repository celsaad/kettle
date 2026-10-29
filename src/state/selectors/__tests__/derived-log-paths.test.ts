/**
 * The cases that deliberately cross more than one selector module, which is why they aren't filed
 * under any single one: each asserts that the *same* logged session reads consistently through the
 * stat aggregation, the list views and the entry descriptor. Splitting them per module would lose the
 * only thing they check — that the paths agree.
 */
import type { SessionEntry } from '@/domain/types';
import { exerciseHistory } from '@/state/selectors/history-views';
import { sessionEntryResult, sessionSetCount } from '@/state/selectors/session-summary';
import { aSession } from '@/test-support/sessions';

/**
 * What the session editor (#56) is for, from the other end: a correction has to move every number
 * derived from the log, not just the row the user typed into. Each of these reads the *edited* entry
 * through a different derived path — the entry result, the volume chart, the set count — because
 * an edit that only showed up in one of them would look like the correction hadn't taken.
 */
describe('a corrected entry, downstream', () => {
  const logged = (sets: { reps: number; weightKg?: number }[]): SessionEntry => ({
    exercise: 'back-squat',
    type: 'reps',
    sets: sets.map((set) => ({ ...set, restTakenSec: 90 })),
  });

  const before = aSession({ startedAt: '2026-07-29T09:00:00.000Z', entries: [logged([{ reps: 8 }, { reps: 8 }])] });
  // The same session with set 2 corrected from 8 reps to 5, which is what `editEntry` writes.
  const after = { ...before, entries: [logged([{ reps: 8 }, { reps: 5 }])] };

  it('changes the entry result the summary is built from', () => {
    expect(sessionEntryResult(after.entries[0])).toEqual({ kind: 'reps', reps: [8, 5] });
  });

  it('changes the volume the chart plots', () => {
    expect(exerciseHistory([before], 'back-squat')[0].volume).toBe(16);
    expect(exerciseHistory([after], 'back-squat')[0].volume).toBe(13);
  });

  // Removing a set has to move the set count too, or the counts keep including work that was taken out.
  it('changes the set count when a set is removed', () => {
    const removed = { ...before, entries: [logged([{ reps: 8 }])] };
    expect(sessionSetCount(before)).toBe(2);
    expect(sessionSetCount(removed)).toBe(1);
  });

  // A loaded entry's volume is reps × weight rather than reps, so correcting the load moves it too.
  it('changes the volume when the load is what was wrong', () => {
    const light = aSession({ startedAt: '2026-07-29T09:00:00.000Z', entries: [logged([{ reps: 5, weightKg: 60 }])] });
    const heavy = { ...light, entries: [logged([{ reps: 5, weightKg: 100 }])] };

    expect(exerciseHistory([light], 'back-squat')[0].volume).toBe(300);
    expect(exerciseHistory([heavy], 'back-squat')[0].volume).toBe(500);
  });
});
