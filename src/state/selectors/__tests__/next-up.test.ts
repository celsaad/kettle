import type { Library, Program } from '@/domain/types';
import { nextUpView, nextWeekAfter } from '@/state/selectors/next-up';
import { aSession } from '@/test-support/sessions';

describe('nextWeekAfter', () => {
  const program: Program = {
    id: 'prog',
    name: 'Prog',
    weeks: [
      { week: 1, workoutId: 'full' },
      { week: 2, workoutId: 'full' },
      { week: 3, workoutId: 'full' },
    ],
  };

  it('starts at the first week when there is no prior session', () => {
    expect(nextWeekAfter(program, []).week).toBe(1);
  });

  it('picks the next week after the most recent tracked session', () => {
    const sessions = [aSession({ startedAt: 't', program: 'prog', programWeek: 1, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(2);
  });

  it('wraps back to the first week after the last one', () => {
    const sessions = [aSession({ startedAt: 't', program: 'prog', programWeek: 3, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(1);
  });

  it('falls back to the first week when the referenced week no longer exists in the program', () => {
    const sessions = [aSession({ startedAt: 't', program: 'prog', programWeek: 99, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(1);
  });

  it('ignores a session belonging to a different program', () => {
    const sessions = [aSession({ startedAt: 't', program: 'other-prog', programWeek: 2, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(1);
  });

  // Unchanged by rest days on purpose: this returns the literal next slot, and it is `nextUpView`
  // one level up — the one that has a clock — that decides whether a rest slot is still owed.
  it('returns a rest slot as the literal next one, without skipping it', () => {
    const withRest: Program = {
      id: 'prog',
      name: 'Prog',
      weeks: [
        { week: 1, day: 'Day 1', workoutId: 'full' },
        { week: 1, day: 'Day 2', restDay: true },
      ],
    };
    const sessions = [aSession({ startedAt: 't', program: 'prog', programWeek: 1, programDay: 'Day 1' })];
    expect(nextWeekAfter(withRest, sessions).restDay).toBe(true);
  });
});

/**
 * How a rest day clears itself off the home screen, which is the one piece of this feature with no
 * anchor in the log — nothing about a rest day is ever recorded, so elapsed calendar days are what
 * move it along. The rule, with `R` consecutive rest slots:
 *
 *     restsServed = max(0, daysSince(lastSession) - 1)
 *
 * `now` is injected rather than faked with timers: the arithmetic is the thing under test, and a
 * parameter says so more clearly than a system clock does.
 */
describe('nextUpView and rest days', () => {
  const library: Library = {
    version: 1,
    exercises: [],
    workouts: [
      { id: 'push', name: 'Push', blocks: [] },
      { id: 'pull', name: 'Pull', blocks: [] },
    ],
    programs: [
      {
        id: 'prog',
        name: 'Prog',
        weeks: [
          { week: 1, day: 'Day 1', workoutId: 'push' },
          { week: 1, day: 'Day 2', restDay: true, notes: 'Walk.' },
          { week: 1, day: 'Day 3', workoutId: 'pull' },
        ],
      },
    ],
  };

  const trainedOn = (date: Date, day = 'Day 1') => [
    aSession({ startedAt: date.toISOString(), program: 'prog', programWeek: 1, programDay: day }),
  ];

  const monday = new Date(2026, 6, 27, 18, 0, 0);
  const dayAfter = (days: number) => new Date(2026, 6, 27 + days, 9, 0, 0);

  it('shows the rest day on the evening of the session before it', () => {
    const view = nextUpView(library, trainedOn(monday), monday);
    expect(view?.kind).toBe('rest');
    expect(view?.kind === 'rest' && view.weekDay).toBe('Day 2');
    expect(view?.kind === 'rest' && view.weekNotes).toBe('Walk.');
  });

  it('still shows it the next day, which is the day being rested', () => {
    const view = nextUpView(library, trainedOn(monday), dayAfter(1));
    expect(view?.kind).toBe('rest');
  });

  it('moves on to the next workout the day after that', () => {
    const view = nextUpView(library, trainedOn(monday), dayAfter(2));
    expect(view?.kind).toBe('workout');
    expect(view?.kind === 'workout' && view.workout.id).toBe('pull');
    expect(view?.kind === 'workout' && view.weekDay).toBe('Day 3');
  });

  it('stays moved on after a long layoff rather than re-owing the rest day', () => {
    const view = nextUpView(library, trainedOn(monday), dayAfter(30));
    expect(view?.kind).toBe('workout');
  });

  it('offers the next workout as an escape hatch while resting', () => {
    const view = nextUpView(library, trainedOn(monday), monday);
    expect(view?.kind === 'rest' && view.skipTo).toEqual({ programId: 'prog', week: '1', day: 'Day 3' });
  });

  it('spends two consecutive rest slots over two days', () => {
    const twoRests: Library = {
      ...library,
      programs: [
        {
          id: 'prog',
          name: 'Prog',
          weeks: [
            { week: 1, day: 'Day 1', workoutId: 'push' },
            { week: 1, day: 'Day 2', restDay: true },
            { week: 1, day: 'Day 3', restDay: true },
            { week: 1, day: 'Day 4', workoutId: 'pull' },
          ],
        },
      ],
    };
    const sessions = trainedOn(monday);
    expect(nextUpView(twoRests, sessions, dayAfter(1))).toMatchObject({ kind: 'rest', weekDay: 'Day 2' });
    expect(nextUpView(twoRests, sessions, dayAfter(2))).toMatchObject({ kind: 'rest', weekDay: 'Day 3' });
    expect(nextUpView(twoRests, sessions, dayAfter(3))).toMatchObject({ kind: 'workout' });
  });

  // No session has ever been tracked against this program, so there is no anchor to count days from.
  // It shows the rest day rather than guessing, and "train anyway" is the way out.
  it('shows a leading rest day when the program has never been run', () => {
    const leadingRest: Library = {
      ...library,
      programs: [
        {
          id: 'prog',
          name: 'Prog',
          weeks: [
            { week: 1, day: 'Day 1', restDay: true },
            { week: 1, day: 'Day 2', workoutId: 'push' },
          ],
        },
      ],
    };
    const view = nextUpView(leadingRest, [], monday);
    expect(view).toMatchObject({ kind: 'rest', weekDay: 'Day 1' });
    expect(view?.kind === 'rest' && view.skipTo).toEqual({ programId: 'prog', week: '1', day: 'Day 2' });
  });

  // The schema refuses this on import, but the in-app editor can still be mid-edit, and a card with a
  // link to nowhere is worse than a card with no link.
  it('leaves the escape hatch empty when nothing in the program runs anything', () => {
    const allRest: Library = {
      ...library,
      programs: [{ id: 'prog', name: 'Prog', weeks: [{ week: 1, restDay: true }] }],
    };
    expect(nextUpView(allRest, [], monday)).toMatchObject({ kind: 'rest', skipTo: null });
  });

  it('wraps a trailing rest day round to the next cycle', () => {
    const trailingRest: Library = {
      ...library,
      programs: [
        {
          id: 'prog',
          name: 'Prog',
          weeks: [
            { week: 1, day: 'Day 1', workoutId: 'push' },
            { week: 1, day: 'Day 2', restDay: true },
          ],
        },
      ],
    };
    const sessions = trainedOn(monday);
    expect(nextUpView(trailingRest, sessions, dayAfter(1))).toMatchObject({ kind: 'rest' });
    // Past the rest day the rotation comes back round to week 1 day 1, since finishing a program
    // restarts it.
    expect(nextUpView(trailingRest, sessions, dayAfter(2))).toMatchObject({ kind: 'workout', weekDay: 'Day 1' });
  });
});
