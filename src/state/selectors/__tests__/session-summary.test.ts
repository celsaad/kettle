import type { EntryResult } from '@/domain/format';
import type { SessionEntry } from '@/domain/types';
import { sessionEntryResult } from '@/state/selectors/session-summary';

// Asserts the descriptor, not the sentence: rendering is format.test.ts's job, and assertions on
// prose are exactly what i18n would invalidate. hiit and amrap both collapse onto `rounds` — the
// renderer never needs to know which produced it.
describe('sessionEntryResult', () => {
  it('describes every entry type structurally', () => {
    const cases: [SessionEntry, EntryResult][] = [
      [
        {
          exercise: 'e',
          type: 'timed_hold',
          sets: [
            { holdSec: 20, restTakenSec: 60 },
            { holdSec: 15, restTakenSec: 0 },
          ],
        },
        { kind: 'holds', holdSecs: [20, 15] },
      ],
      [
        {
          exercise: 'e',
          type: 'reps',
          sets: [
            { reps: 10, restTakenSec: 60 },
            { reps: 8, restTakenSec: 0 },
          ],
        },
        { kind: 'reps', reps: [10, 8] },
      ],
      [
        { exercise: 'e', type: 'hiit', roundsCompleted: 4 },
        { kind: 'rounds', rounds: 4 },
      ],
      [
        { exercise: 'e', type: 'emom', minutes: [{ reps: 3 }, { reps: 2 }] },
        { kind: 'intervals', intervals: 2, totalReps: 5 },
      ],
      // No reps logged at all reports none, rather than a misleading zero.
      [
        { exercise: 'e', type: 'emom', minutes: [{}, {}] },
        { kind: 'intervals', intervals: 2, totalReps: undefined },
      ],
      [
        { exercise: 'e', type: 'amrap', roundsCompleted: 7, extraReps: 4 },
        { kind: 'rounds', rounds: 7, extraReps: 4 },
      ],
      [
        { exercise: 'e', type: 'amrap', roundsCompleted: 7 },
        { kind: 'rounds', rounds: 7, extraReps: undefined },
      ],
      [
        { exercise: 'e', type: 'cardio', durationSec: 480, distanceMeters: 2000 },
        { kind: 'cardio', durationSec: 480, distanceMeters: 2000 },
      ],
      [
        { exercise: 'e', type: 'rest', restTakenSec: 90 },
        { kind: 'rest', restTakenSec: 90 },
      ],
    ];
    for (const [entry, expected] of cases) expect(sessionEntryResult(entry)).toEqual(expected);
  });
});
