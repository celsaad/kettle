import { changeLanguage } from 'i18next';

import type { Library } from '@/domain/types';
import { exerciseHistory, historySessionsView } from '@/state/selectors/history-views';
import { aSession } from '@/test-support/sessions';

/**
 * The two session-list views feed History and Today, and both used to assemble their labels here as
 * English template literals — untranslated on both screens, and pluralised by concatenation, so a
 * one-set session read "1 sets". They now go through `domain/format.ts`, which is what makes the `pt`
 * case below possible at all: against `en` a hardcoded literal and a `t()` call render identically.
 */
describe('session list labels', () => {
  const library: Library = {
    version: 1,
    exercises: [{ id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } }],
    workouts: [{ id: 'push-day', name: 'Push day', blocks: [] }],
    programs: [],
  };

  const oneSet = aSession({
    startedAt: '2026-07-24T09:00:00.000Z',
    endedAt: '2026-07-24T09:12:00.000Z',
    workout: 'push-day',
    entries: [{ exercise: 'lsit', type: 'timed_hold', sets: [{ holdSec: 20, restTakenSec: 0 }] }],
  });

  it('pluralises the set count instead of concatenating it', () => {
    const [single] = historySessionsView([oneSet], library);
    expect(single.setsLabel).toBe('1 set');
    expect(single.durationLabel).toBe('12 min');

    const twoSets = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      endedAt: '2026-07-24T09:12:00.000Z',
      entries: [
        {
          exercise: 'lsit',
          type: 'timed_hold',
          sets: [
            { holdSec: 20, restTakenSec: 0 },
            { holdSec: 18, restTakenSec: 0 },
          ],
        },
      ],
    });
    expect(historySessionsView([twoSets], library)[0].setsLabel).toBe('2 sets');
  });

  it('names a session after its workout, falling back to a label when it was started ad-hoc', () => {
    expect(historySessionsView([oneSet], library)[0].workoutName).toBe('Push day');
    const adHoc = aSession({ startedAt: '2026-07-24T09:00:00.000Z', workout: null });
    expect(historySessionsView([adHoc], library)[0].workoutName).toBe('Ad-hoc session');
  });

  // The workout is user data from their own YAML, so it stays as written whatever the locale is.
  it('translates every label it owns, and none of the user data', async () => {
    await changeLanguage('pt');
    const [session] = historySessionsView([oneSet], library);
    expect(session.setsLabel).toBe('1 série');
    expect(session.durationLabel).toBe('12 min');
    expect(session.workoutName).toBe('Push day');

    const adHoc = aSession({ startedAt: '2026-07-24T09:00:00.000Z', workout: null });
    expect(historySessionsView([adHoc], library)[0].workoutName).toBe('Sessão avulsa');
  });
});

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
