import { exerciseHistory } from '@/state/selectors/history-views';
import { aSession } from '@/test-support/sessions';

describe('exerciseHistory volume', () => {
  it('weights reps volume by load only when a set actually carries weight', () => {
    const withWeight = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'bench',
          type: 'reps',
          sets: [
            { reps: 5, weightKg: 60, restTakenSec: 90 },
            { reps: 5, weightKg: 60, restTakenSec: 90 },
          ],
        },
      ],
    });
    const bodyweight = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'pushups',
          type: 'reps',
          sets: [
            { reps: 10, restTakenSec: 45 },
            { reps: 8, restTakenSec: 0 },
          ],
        },
      ],
    });
    expect(exerciseHistory([withWeight], 'bench')[0].volume).toBe(600);
    expect(exerciseHistory([bodyweight], 'pushups')[0].volume).toBe(18);
  });

  it('uses distance over duration for cardio, falling back to duration when distance is absent', () => {
    const withDistance = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'row', type: 'cardio', durationSec: 480, distanceMeters: 2000 }],
    });
    const durationOnly = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'row', type: 'cardio', durationSec: 480 }],
    });
    expect(exerciseHistory([withDistance], 'row')[0].volume).toBe(2000);
    expect(exerciseHistory([durationOnly], 'row')[0].volume).toBe(480);
  });
});
